// File-transfer orchestrator for the desktop app. Owns the TCP 8991 server for
// incoming transfers, drives outgoing transfers, and publishes a single active
// TransferState snapshot to the renderer. Completely independent of the
// clipboard/pairing channel on 8989 — a transfer failure can never break sync.

const fs = require('fs');
const net = require('net');
const path = require('path');
const { v4: uuidv4 } = require('uuid');

const protocol = require('./transferProtocol');
const SocketReader = require('./socketReader');
const TransferStorage = require('./transferStorage');

const STATUS_COMPLETED = 'completed';
const STATUS_FAILED = 'failed';
const STATUS_CANCELLED = 'cancelled';
const STATUS_REJECTED = 'rejected';

class TransferManager {
  constructor({ database, saveDir, getIdentity, isPairedDevice, emit, addLog }) {
    this.database = database;
    this.saveDir = saveDir;
    this.getIdentity = getIdentity;
    this.isPairedDevice = isPairedDevice;
    this.emit = emit;
    this.addLog = addLog;

    this.server = null;
    this.state = null; // single active transfer slot
    this.pauseRequested = false;
    this.cancelRequested = false;
    this.activeSocket = null;
    this.pendingDecision = null; // { transferId, resolve, timer }
    this.lastOutgoing = null; // { device, filePaths } for retry
    this.storage = new TransferStorage(saveDir);
  }

  // Points received files at a new directory (user-chosen save location).
  setSaveDir(dir) {
    this.saveDir = dir;
    this.storage = new TransferStorage(dir);
  }

  // ── server lifecycle (called from SyncController start/stop) ──────────────

  startServer() {
    if (this.server) {
      return;
    }
    this.server = net.createServer((socket) => {
      this.handleIncoming(socket).catch((error) => {
        this.addLog('warn', `TRANSFER_INCOMING_ERROR ${error.message}`);
      });
    });
    this.server.on('error', (error) => {
      this.addLog('error', `TRANSFER_SERVER_ERROR ${error.code || ''} ${error.message}`);
    });
    this.server.listen(protocol.TRANSFER_PORT, '0.0.0.0', () => {
      this.addLog('info', `TRANSFER_SERVER_STARTED_ON_0_0_0_0_${protocol.TRANSFER_PORT}`);
    });
  }

  stopServer() {
    if (!this.server) {
      return;
    }
    try {
      this.server.close();
    } catch (_) {}
    this.server = null;
    this.cancel();
  }

  isServerRunning() {
    return Boolean(this.server && this.server.listening);
  }

  // ── state / controls ──────────────────────────────────────────────────────

  getState() {
    return this.state;
  }

  publish() {
    this.emit('transfer:state', this.state);
  }

  pause() {
    if (this.state && this.state.phase === 'transferring') {
      this.pauseRequested = true;
    }
  }

  resume() {
    this.pauseRequested = false;
  }

  cancel() {
    if (!this.state || this.isTerminal(this.state.phase)) {
      return;
    }
    this.cancelRequested = true;
    this.pauseRequested = false;
    if (this.pendingDecision) {
      this.respondToIncoming(this.pendingDecision.transferId, false);
    }
  }

  dismiss() {
    if (this.state && this.isTerminal(this.state.phase)) {
      this.state = null;
      this.publish();
    }
  }

  isTerminal(phase) {
    return phase === 'completed' || phase === 'failed' || phase === 'cancelled';
  }

  respondToIncoming(transferId, accepted) {
    const pending = this.pendingDecision;
    if (!pending || pending.transferId !== transferId) {
      return;
    }
    this.pendingDecision = null;
    clearTimeout(pending.timer);
    pending.resolve(Boolean(accepted));
  }

  listHistory(limit = 100) {
    return this.database.listTransferHistory(limit);
  }

  // ── outgoing ──────────────────────────────────────────────────────────────

  async sendFiles(device, filePaths) {
    if (this.state && !this.isTerminal(this.state.phase)) {
      throw new Error('A transfer is already in progress');
    }
    const files = prepareLocalFiles(filePaths);
    this.lastOutgoing = { device, filePaths: files.map((file) => file.path) };
    this.runOutgoing(device, files).catch((error) => {
      this.addLog('error', `TRANSFER_OUTGOING_ERROR ${error.message}`);
    });
    return { transferId: this.state?.transferId };
  }

  async retryLast() {
    if (!this.lastOutgoing) {
      throw new Error('Nothing to retry');
    }
    return this.sendFiles(this.lastOutgoing.device, this.lastOutgoing.filePaths);
  }

  async runOutgoing(device, files) {
    const transferId = uuidv4();
    const identity = this.getIdentity();
    const totalSize = files.reduce((sum, file) => sum + file.size, 0);

    this.beginSession({
      transferId,
      direction: 'outgoing',
      peerId: device.deviceId,
      peerName: device.deviceName,
      files,
      totalSize,
      phase: 'connecting'
    });

    let socket = null;
    let finalStatus = STATUS_FAILED;
    try {
      socket = await connect(device.ipAddress, protocol.TRANSFER_PORT);
      this.activeSocket = socket;
      socket.setTimeout(protocol.ACCEPT_TIMEOUT_MS, () => socket.destroy(new Error('Timed out waiting for the receiver')));
      const reader = new SocketReader(socket);

      writeLine(socket, protocol.buildOffer(transferId, identity.deviceId, identity.deviceName, files));
      this.state.phase = 'waiting_for_accept';
      this.publish();
      this.addLog('info', `TRANSFER_OFFER_SENT ${device.deviceName} (${files.length} files)`);

      const responseLine = await reader.readLine();
      if (responseLine == null) {
        throw new Error('Connection closed before response');
      }
      const response = JSON.parse(responseLine);
      if (!response.accepted) {
        this.state.phase = 'failed';
        this.state.message = response.message || 'Transfer was declined by the receiver';
        finalStatus = STATUS_REJECTED;
        this.addLog('info', `TRANSFER_REJECTED_BY ${device.deviceName}`);
        return;
      }

      socket.setTimeout(protocol.DATA_TIMEOUT_MS, () => socket.destroy(new Error('Connection timed out')));
      this.state.phase = 'transferring';
      this.state.startedAt = Date.now();
      this.publish();

      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        this.state.currentIndex = i;
        this.state.currentFileName = file.name;
        this.state.currentFileSize = file.size;
        this.state.currentFileBytes = 0;
        this.publish();

        writeLine(socket, protocol.buildFileHeader(transferId, i, file.name, file.size));
        await this.streamFileOut(socket, file);

        const ackLine = await reader.readLine();
        if (ackLine == null) {
          throw new Error('Connection closed while waiting for file ack');
        }
        const ack = JSON.parse(ackLine);
        if (!ack.ok) {
          throw new Error(`Receiver failed to store ${file.name}`);
        }
        file.completed = true;
        this.state.completedFiles = i + 1;
        this.publish();
      }

      writeLine(socket, protocol.buildSimple(protocol.TYPE_COMPLETE, transferId));

      this.state.phase = 'completed';
      this.state.etaSeconds = 0;
      this.state.message = `Sent ${files.length} file(s) to ${device.deviceName}`;
      finalStatus = STATUS_COMPLETED;
      this.addLog('info', `TRANSFER_COMPLETED_TO ${device.deviceName} (${totalSize} bytes)`);
    } catch (error) {
      if (error instanceof CancelledError || this.cancelRequested) {
        this.state.phase = 'cancelled';
        this.state.message = 'Transfer cancelled';
        finalStatus = STATUS_CANCELLED;
        if (socket && !socket.destroyed) {
          try {
            writeLine(socket, protocol.buildSimple(protocol.TYPE_CANCEL, transferId));
          } catch (_) {}
        }
      } else {
        this.state.phase = 'failed';
        this.state.message = toUserFacingError(error, device);
        finalStatus = STATUS_FAILED;
        this.addLog('warn', `TRANSFER_FAILED_TO ${device.deviceName} ${error.message}`);
      }
    } finally {
      destroyQuietly(socket);
      this.activeSocket = null;
      this.finishSession(finalStatus);
    }
  }

  async streamFileOut(socket, file) {
    const stream = fs.createReadStream(file.path, { highWaterMark: protocol.CHUNK_SIZE });
    let lastTick = Date.now();
    let lastTickBytes = this.state.transferredBytes;
    try {
      for await (const chunk of stream) {
        await this.waitWhilePaused();
        if (this.cancelRequested) {
          throw new CancelledError();
        }
        if (!socket.write(chunk)) {
          await waitForDrain(socket);
        }
        this.state.currentFileBytes += chunk.length;
        this.state.transferredBytes += chunk.length;

        const now = Date.now();
        if (now - lastTick >= 400) {
          this.updateSpeed(now - lastTick, this.state.transferredBytes - lastTickBytes);
          lastTick = now;
          lastTickBytes = this.state.transferredBytes;
          this.publish();
        }
      }
    } finally {
      stream.destroy();
    }
    if (this.state.currentFileBytes !== file.size) {
      throw new Error(`File changed while sending: ${file.name}`);
    }
  }

  // ── incoming ──────────────────────────────────────────────────────────────

  async handleIncoming(socket) {
    const remoteAddress = (socket.remoteAddress || 'unknown').replace(/^::ffff:/, '');
    socket.setTimeout(protocol.ACCEPT_TIMEOUT_MS, () => socket.destroy(new Error('Timed out')));
    const reader = new SocketReader(socket);

    let claimed = false;
    let finalStatus = STATUS_FAILED;
    let currentDestination = null;
    const savedFiles = [];
    try {
      const offerLine = await reader.readLine();
      if (offerLine == null) {
        return;
      }
      const offer = JSON.parse(offerLine);
      if (offer.type !== protocol.TYPE_OFFER) {
        this.addLog('warn', `TRANSFER_UNEXPECTED_MESSAGE ${remoteAddress}`);
        return;
      }
      const transferId = String(offer.transferId || '');
      const senderId = String(offer.fromDeviceId || '');
      const senderName = String(offer.fromDeviceName || 'Unknown device');

      // Security gates: sender must be paired, metadata must be sane.
      const files = protocol.parseOfferFiles(offer);
      let rejection = null;
      if (!this.isPairedDevice(senderId)) {
        rejection = 'Sender is not paired with this desktop';
        this.addLog('warn', `TRANSFER_REJECTED_UNPAIRED ${senderId}`);
      } else {
        rejection = protocol.validateOffer(offer, files);
        if (!rejection && this.state && !this.isTerminal(this.state.phase)) {
          rejection = 'Receiver is busy with another transfer';
        }
      }
      if (rejection) {
        writeLine(socket, protocol.buildResponse(transferId, false, rejection));
        return;
      }

      claimed = true;
      this.beginSession({
        transferId,
        direction: 'incoming',
        peerId: senderId,
        peerName: senderName,
        files,
        totalSize: Number(offer.totalSize || 0),
        phase: 'incoming_pending'
      });

      const accepted = await this.awaitUserDecision(transferId, senderName, files);
      if (!accepted) {
        writeLine(socket, protocol.buildResponse(transferId, false, 'Transfer was declined by the receiver'));
        this.state.phase = 'cancelled';
        this.state.message = 'You declined the transfer';
        finalStatus = STATUS_REJECTED;
        return;
      }

      writeLine(socket, protocol.buildResponse(transferId, true, 'accepted'));
      socket.setTimeout(protocol.DATA_TIMEOUT_MS, () => socket.destroy(new Error('Connection timed out')));
      this.activeSocket = socket;
      this.state.phase = 'transferring';
      this.state.startedAt = Date.now();
      this.publish();
      this.addLog('info', `TRANSFER_RECEIVING ${files.length} file(s) from ${senderName}`);

      for (let i = 0; i < files.length; i++) {
        const headerLine = await reader.readLine();
        if (headerLine == null) {
          throw new Error(`Connection closed before file ${i + 1}`);
        }
        const header = JSON.parse(headerLine);
        if (header.type === protocol.TYPE_CANCEL) {
          throw new CancelledError();
        }
        if (header.type !== protocol.TYPE_FILE_HEADER) {
          throw new Error('Protocol error: expected file header');
        }
        // Re-validate every header against the accepted offer.
        const name = protocol.sanitizeFileName(header.name);
        const size = Number(header.size ?? -1);
        const expected = files[i];
        if (size < 0 || size > protocol.MAX_FILE_SIZE || size !== expected.size) {
          throw new Error(`File size mismatch for ${name}`);
        }

        this.state.currentIndex = i;
        this.state.currentFileName = name;
        this.state.currentFileSize = size;
        this.state.currentFileBytes = 0;
        this.publish();

        currentDestination = this.storage.open(name);
        await this.receiveFile(reader, currentDestination, size);
        await currentDestination.commit();
        savedFiles.push(currentDestination.finalPath);
        currentDestination = null;

        expected.completed = true;
        this.state.completedFiles = i + 1;
        this.publish();
        writeLine(socket, protocol.buildFileAck(transferId, i, true));
      }

      const completeLine = await reader.readLine();
      if (completeLine != null) {
        writeLine(socket, protocol.buildSimple(protocol.TYPE_RESULT, transferId));
      }

      this.state.phase = 'completed';
      this.state.etaSeconds = 0;
      this.state.message = `Saved ${files.length} file(s) to ${this.saveDir}`;
      this.state.savedFiles = savedFiles;
      finalStatus = STATUS_COMPLETED;
      this.addLog('info', `TRANSFER_RECEIVED ${files.length} file(s) from ${senderName}`);
    } catch (error) {
      if (!claimed) {
        this.addLog('warn', `TRANSFER_INCOMING_ABORTED ${remoteAddress} ${error.message}`);
        return;
      }
      if (error instanceof CancelledError || this.cancelRequested) {
        this.state.phase = 'cancelled';
        this.state.message = 'Transfer cancelled';
        finalStatus = STATUS_CANCELLED;
      } else {
        this.state.phase = 'failed';
        this.state.message = 'Transfer failed — connection was interrupted';
        finalStatus = STATUS_FAILED;
        this.addLog('warn', `TRANSFER_INCOMING_FAILED ${remoteAddress} ${error.message}`);
      }
    } finally {
      if (currentDestination) {
        currentDestination.discard();
      }
      destroyQuietly(socket);
      if (this.activeSocket === socket) {
        this.activeSocket = null;
      }
      if (claimed) {
        this.finishSession(finalStatus);
      }
    }
  }

  async receiveFile(reader, destination, size) {
    let lastTick = Date.now();
    let lastTickBytes = this.state.transferredBytes;
    await reader.readBytes(size, async (chunk) => {
      if (this.cancelRequested) {
        throw new CancelledError();
      }
      await destination.write(chunk);
      this.state.currentFileBytes += chunk.length;
      this.state.transferredBytes += chunk.length;

      const now = Date.now();
      if (now - lastTick >= 400) {
        this.updateSpeed(now - lastTick, this.state.transferredBytes - lastTickBytes);
        lastTick = now;
        lastTickBytes = this.state.transferredBytes;
        this.publish();
      }
    });
  }

  awaitUserDecision(transferId, senderName, files) {
    this.publish();
    this.emit('transfer:incoming', {
      transferId,
      senderName,
      fileCount: files.length,
      totalSize: files.reduce((sum, file) => sum + file.size, 0),
      files: files.map((file) => ({ name: file.name, size: file.size }))
    });
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        if (this.pendingDecision && this.pendingDecision.transferId === transferId) {
          this.pendingDecision = null;
          resolve(false);
        }
      }, protocol.ACCEPT_TIMEOUT_MS - 5000);
      this.pendingDecision = { transferId, resolve, timer };
    });
  }

  // ── session bookkeeping ───────────────────────────────────────────────────

  beginSession({ transferId, direction, peerId, peerName, files, totalSize, phase }) {
    this.pauseRequested = false;
    this.cancelRequested = false;
    this.state = {
      transferId,
      direction,
      peerId,
      peerName,
      phase,
      files: files.map((file) => ({ name: file.name, size: file.size, completed: false })),
      fileCount: files.length,
      totalSize,
      currentIndex: -1,
      currentFileName: '',
      currentFileSize: 0,
      currentFileBytes: 0,
      transferredBytes: 0,
      completedFiles: 0,
      speedBps: 0,
      etaSeconds: -1,
      message: '',
      startedAt: 0,
      savedFiles: []
    };
    // Keep the per-file completed flags in sync with the live file list.
    this._liveFiles = files;
    this.publish();
    return this.state;
  }

  finishSession(status) {
    const state = this.state;
    if (!state) {
      return;
    }
    for (let i = 0; i < state.files.length; i++) {
      state.files[i].completed = Boolean(this._liveFiles?.[i]?.completed);
    }
    state.speedBps = 0;
    this.pauseRequested = false;
    this.cancelRequested = false;
    this.pendingDecision = null;
    try {
      this.database.addTransferHistory({
        transferId: state.transferId,
        direction: state.direction,
        deviceId: state.peerId,
        deviceName: state.peerName,
        fileCount: state.fileCount,
        totalSize: state.totalSize,
        transferredBytes: state.transferredBytes,
        status,
        message: state.message,
        filesJson: JSON.stringify(state.files),
        createdAt: Date.now()
      });
    } catch (error) {
      this.addLog('warn', `TRANSFER_HISTORY_SAVE_FAILED ${error.message}`);
    }
    this.publish();
    this.emit('transfer:finished', {
      transferId: state.transferId,
      status,
      direction: state.direction,
      peerName: state.peerName,
      fileCount: state.fileCount,
      totalSize: state.totalSize
    });
  }

  updateSpeed(elapsedMs, bytes) {
    const state = this.state;
    if (!state || elapsedMs <= 0) {
      return;
    }
    state.speedBps = Math.round((bytes * 1000) / elapsedMs);
    const remaining = state.totalSize - state.transferredBytes;
    state.etaSeconds = state.speedBps > 0 ? Math.ceil(remaining / state.speedBps) : -1;
  }

  async waitWhilePaused() {
    let wasPaused = false;
    while (this.pauseRequested && !this.cancelRequested) {
      if (!wasPaused) {
        wasPaused = true;
        this.state.phase = 'paused';
        this.state.speedBps = 0;
        this.state.etaSeconds = -1;
        this.publish();
      }
      await sleep(150);
    }
    if (this.cancelRequested) {
      throw new CancelledError();
    }
    if (wasPaused) {
      this.state.phase = 'transferring';
      this.publish();
    }
  }
}

class CancelledError extends Error {
  constructor() {
    super('cancelled');
  }
}

function prepareLocalFiles(filePaths) {
  if (!Array.isArray(filePaths) || !filePaths.length) {
    throw new Error('No files selected');
  }
  if (filePaths.length > protocol.MAX_FILES_PER_TRANSFER) {
    throw new Error(`At most ${protocol.MAX_FILES_PER_TRANSFER} files per transfer`);
  }
  return filePaths.map((filePath) => {
    const stats = fs.statSync(filePath);
    if (!stats.isFile()) {
      throw new Error(`Not a file: ${path.basename(filePath)}`);
    }
    if (stats.size > protocol.MAX_FILE_SIZE) {
      throw new Error(`${path.basename(filePath)} exceeds the 2 GB limit`);
    }
    return {
      path: filePath,
      name: protocol.sanitizeFileName(path.basename(filePath)),
      size: stats.size,
      completed: false
    };
  });
}

function connect(host, port) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host, port });
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error('Could not connect — is SyncMesh running on the other device?'));
    }, protocol.CONNECT_TIMEOUT_MS);
    socket.once('connect', () => {
      clearTimeout(timer);
      socket.setNoDelay(true);
      resolve(socket);
    });
    socket.once('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

function writeLine(socket, message) {
  socket.write(`${JSON.stringify(message)}\n`);
}

function waitForDrain(socket) {
  return new Promise((resolve, reject) => {
    const onDrain = () => {
      socket.off('error', onError);
      socket.off('close', onClose);
      resolve();
    };
    const onError = (error) => {
      socket.off('drain', onDrain);
      socket.off('close', onClose);
      reject(error);
    };
    const onClose = () => {
      socket.off('drain', onDrain);
      socket.off('error', onError);
      reject(new Error('Connection closed'));
    };
    socket.once('drain', onDrain);
    socket.once('error', onError);
    socket.once('close', onClose);
  });
}

function destroyQuietly(socket) {
  if (socket && !socket.destroyed) {
    try {
      socket.end();
      socket.destroy();
    } catch (_) {}
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function toUserFacingError(error, device) {
  const code = error?.code || '';
  if (code === 'ECONNREFUSED') {
    return `${device.deviceName} is not accepting transfers — make sure sync is running there`;
  }
  if (code === 'EHOSTUNREACH' || code === 'ENETUNREACH' || code === 'ETIMEDOUT') {
    return `${device.deviceName} is unreachable — check that both devices are on the same WiFi`;
  }
  return error?.message || 'Transfer failed';
}

module.exports = TransferManager;
