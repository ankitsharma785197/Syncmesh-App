const { v4: uuidv4 } = require('uuid');
const QRCode = require('qrcode');

const ClipboardManager = require('./clipboardManager');
const { TCP_PORT } = require('./constants');
const { getOrCreateDeviceId, getDeviceName, getPairingCode } = require('./deviceIdentity');
const { getLocalIPv4, isIPv4Address } = require('./network');
const RecentEvents = require('./recentEvents');
const { sendJsonLine } = require('./tcpClient');
const TcpServer = require('./tcpServer');
const UdpDiscovery = require('./udpDiscovery');
const { safeNumber, safeString } = require('./safe');

class SyncController {
  constructor({ database, log, emit }) {
    this.database = database;
    this.log = log;
    this.emit = emit;
    this.running = false;
    this.logs = [];
    this.nearbyDevices = new Map();
    this.recentEvents = new RecentEvents();
    this.pendingPairRequests = new Map();
    this.onStatusChange = null;
    this.lastQrPayloadJson = '';

    this.deviceId = getOrCreateDeviceId(database);
    this.deviceName = getDeviceName(database);
    this.pairingCode = getPairingCode(database);

    this.tcpServer = new TcpServer({
      onMessage: (message, remote) => this.handleTcpMessage(message, remote),
      logger: log
    });

    this.discovery = new UdpDiscovery({
      deviceId: this.deviceId,
      deviceName: this.deviceName,
      onDevice: (device) => this.handleNearbyDevice(device),
      logger: log
    });

    this.clipboard = new ClipboardManager({
      deviceId: this.deviceId,
      deviceName: this.deviceName,
      onLocalClipboard: (event) => this.broadcastClipboard(event),
      onHistory: (item) => this.saveHistory(item),
      logger: log
    });
  }

  async start() {
    if (this.running) {
      return this.getState();
    }

    await this.tcpServer.start();
    await this.discovery.start();
    this.clipboard.start();
    this.running = true;
    this.addLog('info', 'Sync started on TCP 8989 and UDP discovery 8990');
    this.emitState();
    this.onStatusChange?.();
    return this.getState();
  }

  async stop() {
    if (!this.running) {
      return this.getState();
    }

    this.clipboard.stop();
    await this.discovery.stop();
    await this.tcpServer.stop();
    this.running = false;
    this.addLog('info', 'Sync stopped');
    this.emitState();
    this.onStatusChange?.();
    return this.getState();
  }

  getStatus() {
    return { running: this.running };
  }

  async getState() {
    return {
      running: this.running,
      deviceId: this.deviceId,
      deviceName: this.deviceName,
      ipAddress: getLocalIPv4(),
      port: TCP_PORT,
      pairingCode: this.pairingCode,
      settings: this.database.getAllSettings(),
      devices: this.database.listDevices(),
      nearbyDevices: this.getNearbyDevices(),
      history: this.database.listClipboardHistory(100),
      logs: this.logs,
      qrDataUrl: await this.getQrDataUrl()
    };
  }

  async getQrDataUrl() {
    const payload = {
      type: 'syncmesh_pair_qr',
      deviceId: safeString(this.deviceId),
      deviceName: safeString(this.deviceName),
      ipAddress: getLocalIPv4(),
      port: TCP_PORT,
      pairingCode: safeString(this.pairingCode)
    };
    const payloadJson = JSON.stringify(payload);
    if (payloadJson !== this.lastQrPayloadJson) {
      this.lastQrPayloadJson = payloadJson;
      this.addLog('info', `QR_PAYLOAD_GENERATED ${payloadJson}`);
    }
    return QRCode.toDataURL(payloadJson, { margin: 1, width: 220 });
  }

  updateSettings(settings) {
    if (settings.deviceName) {
      this.deviceName = settings.deviceName;
      this.database.setSetting('deviceName', settings.deviceName);
    }
    if (settings.regeneratePairingCode) {
      this.pairingCode = String(Math.floor(100000 + Math.random() * 900000));
      this.database.setSetting('pairingCode', this.pairingCode);
    }
    this.emitState();
    return this.getState();
  }

  getNearbyDevices() {
    return [...this.nearbyDevices.values()]
      .sort((a, b) => b.lastSeen - a.lastSeen)
      .slice(0, 50);
  }

  getLogs() {
    return this.logs;
  }

  handleNearbyDevice(device) {
    this.nearbyDevices.set(device.deviceId, device);
    const paired = this.database.getDevice(device.deviceId);
    if (paired) {
      this.database.markDeviceSeen(device.deviceId, device.ipAddress, device.port);
    }
    this.emit('nearby:update', this.getNearbyDevices());
  }

  async handleTcpMessage(message, remote) {
    if (!message || !message.type) {
      throw new Error('Missing message type');
    }

    switch (message.type) {
      case 'pair_request':
        return this.handlePairRequest(message, remote);
      case 'pair_response':
        return this.handlePairResponse(message, remote);
      case 'clipboard_update':
        return this.handleClipboardUpdate(message, remote);
      case 'ping':
        return {
          type: 'pong',
          deviceId: this.deviceId,
          deviceName: this.deviceName,
          timestamp: Date.now()
        };
      default:
        throw new Error(`Unsupported message type: ${message.type}`);
    }
  }

  handlePairRequest(message, remote) {
    const requestId = safeString(message.requestId, uuidv4());
    const expectedCode = safeString(this.pairingCode).trim();
    const receivedCode = safeString(message.pairingCode || message.code).trim();
    const localIp = getLocalIPv4();

    this.addLog('info', `PAIR_REQUEST_RECEIVED ${JSON.stringify(redactPairingPayload(message))}`);
    this.addLog('info', `PAIR_CODE_EXPECTED ${maskCode(expectedCode)}`);
    this.addLog('info', `PAIR_CODE_RECEIVED ${maskCode(receivedCode)}`);

    if (receivedCode !== expectedCode) {
      const rejectedResponse = {
        type: 'pair_response',
        requestId,
        fromDeviceId: safeString(this.deviceId),
        fromDeviceName: safeString(this.deviceName),
        ipAddress: localIp,
        port: TCP_PORT,
        accepted: false,
        message: 'Invalid pairing code',
        timestamp: Date.now()
      };
      this.addLog('warn', `PAIR_RESPONSE_SENT ${JSON.stringify(rejectedResponse)}`);
      return rejectedResponse;
    }

    const request = {
      deviceId: safeString(message.fromDeviceId || message.deviceId),
      deviceName: safeString(message.fromDeviceName || message.deviceName, 'Android Device'),
      ipAddress: safeString(message.ipAddress || remote.remoteAddress),
      port: safeNumber(message.port, TCP_PORT),
      platform: 'android',
      lastSeen: Date.now(),
      requestedAt: Date.now()
    };
    this.pendingPairRequests.set(request.deviceId, request);
    this.saveDevice(request);
    this.emit('pair:request', request);
    this.addLog('info', `Pair request accepted from ${request.deviceName}`);
    this.logPairedDevices();
    this.emitState();

    const acceptedResponse = {
      type: 'pair_response',
      requestId,
      fromDeviceId: safeString(this.deviceId),
      fromDeviceName: safeString(this.deviceName),
      ipAddress: localIp,
      port: TCP_PORT,
      accepted: true,
      message: 'Pairing successful',
      timestamp: Date.now()
    };
    this.addLog('info', `PAIR_RESPONSE_SENT ${JSON.stringify(acceptedResponse)}`);
    return acceptedResponse;
  }

  handlePairResponse(message, remote) {
    this.addLog('info', `PAIR_RESPONSE_RECEIVED ${JSON.stringify(message)}`);
    if (!message.accepted) {
      this.addLog('warn', `Pairing rejected by ${remote.remoteAddress}: ${message.message || message.reason || 'unknown reason'}`);
      return { type: 'ack', accepted: false };
    }

    this.saveDevice({
      deviceId: safeString(message.fromDeviceId || message.deviceId),
      deviceName: safeString(message.fromDeviceName || message.deviceName, 'Android Device'),
      ipAddress: safeString(message.ipAddress || remote.remoteAddress),
      port: safeNumber(message.port, TCP_PORT),
      platform: 'android',
      lastSeen: Date.now()
    });
    this.addLog('info', `Paired with ${message.fromDeviceName || message.deviceName || message.fromDeviceId || message.deviceId}`);
    this.logPairedDevices();
    this.emitState();
    return { type: 'ack', accepted: true };
  }

  handleClipboardUpdate(message, remote) {
    const fromDeviceId = safeString(message.fromDeviceId).trim();
    if (!fromDeviceId) {
      this.addLog('warn', 'CLIPBOARD_REJECTED_MISSING_FROM_DEVICE_ID');
      return { type: 'clipboard_ack', accepted: false, reason: 'missing_from_device_id' };
    }

    const paired = this.database.getDevice(fromDeviceId);
    if (!paired) {
      this.addLog('warn', `CLIPBOARD_REJECTED_UNPAIRED_DEVICE_ID ${fromDeviceId}`);
      this.addLog('warn', `CLIPBOARD_KNOWN_PAIRED_DEVICE_IDS ${JSON.stringify(this.database.listKnownDeviceIds())}`);
      return { type: 'clipboard_ack', accepted: false, reason: 'unpaired_device' };
    }

    if (!message.eventId || this.recentEvents.has(message.eventId)) {
      return { type: 'clipboard_ack', accepted: true, duplicate: true };
    }

    this.recentEvents.add(message.eventId);
    this.database.markDeviceSeen(fromDeviceId, message.ipAddress || remote.remoteAddress, message.port);
    this.clipboard.writeRemoteClipboard(safeString(message.text));
    this.saveHistory({
      eventId: message.eventId,
      text: safeString(message.text),
      sourceDeviceId: fromDeviceId,
      sourceDeviceName: safeString(message.fromDeviceName, paired.deviceName),
      direction: 'incoming',
      createdAt: message.timestamp || Date.now()
    });
    this.addLog('info', `CLIPBOARD_APPLIED ${fromDeviceId}`);
    return { type: 'clipboard_ack', accepted: true, eventId: message.eventId };
  }

  async pairManual(payload = {}) {
    const ipAddress = safeString(payload.ipAddress).trim();
    const port = safeNumber(payload.port, TCP_PORT);
    const pairingCode = safeString(payload.pairingCode).trim();
    if (!ipAddress) {
      throw new Error('Android IP address is required');
    }
    if (!pairingCode) {
      throw new Error('Pairing code is required');
    }

    this.addLog('info', `IPC_PAIR_MANUAL_PAYLOAD ${JSON.stringify({ ipAddress, port, pairingCode: maskCode(pairingCode) })}`);

    const requestId = uuidv4();
    const localIp = getLocalIPv4();
    const message = {
      type: 'pair_request',
      requestId,
      fromDeviceId: safeString(this.deviceId),
      fromDeviceName: safeString(this.deviceName),
      ipAddress: localIp,
      port: TCP_PORT,
      pairingCode,
      timestamp: Date.now()
    };
    this.addLog('info', `PAIR_REQUEST_SENT ${JSON.stringify(redactPairingPayload(message))}`);

    const response = await sendJsonLine({
      host: ipAddress,
      port,
      message
    });

    this.addLog('info', `PAIR_RESPONSE_RECEIVED ${JSON.stringify(response)}`);
    if (!response || response.type !== 'pair_response' || !response.accepted) {
      throw new Error(response?.message || response?.reason || 'Pairing failed');
    }

    this.saveDevice({
      deviceId: safeString(response.fromDeviceId || response.deviceId),
      deviceName: safeString(response.fromDeviceName || response.deviceName, 'Android Device'),
      ipAddress: safeString(response.ipAddress || ipAddress),
      port: safeNumber(response.port || port, TCP_PORT),
      platform: 'android',
      lastSeen: Date.now()
    });
    this.addLog('info', `Manual pairing completed with ${response.fromDeviceName || response.deviceName || response.fromDeviceId || response.deviceId}`);
    this.logPairedDevices();
    this.emitState();
    return this.getState();
  }

  acceptPairing({ deviceId }) {
    const request = this.pendingPairRequests.get(deviceId);
    if (!request) {
      throw new Error('Pair request not found');
    }
    this.saveDevice(request);
    this.pendingPairRequests.delete(deviceId);
    this.addLog('info', `Accepted pair request from ${request.deviceName}`);
    this.logPairedDevices();
    this.emitState();
    return this.getState();
  }

  rejectPairing({ deviceId }) {
    this.pendingPairRequests.delete(deviceId);
    this.addLog('info', `Rejected pair request from ${deviceId}`);
    this.emitState();
    return this.getState();
  }

  async broadcastClipboard(event) {
    this.recentEvents.add(event.eventId);
    const devices = this.database.listDevices().filter((device) => !isIPv4Address(device.deviceId));
    await Promise.all(devices.map(async (device) => {
      this.addLog('info', `CLIPBOARD_SEND_TARGET ${device.deviceId}/${device.ipAddress}/${device.port}`);
      try {
        await sendJsonLine({
          host: device.ipAddress,
          port: device.port,
          message: event
        });
        this.database.markDeviceSeen(device.deviceId, device.ipAddress, device.port);
        this.addLog('info', `CLIPBOARD_SEND_SUCCESS ${device.deviceId}`);
      } catch (error) {
        this.database.markDeviceError(device.deviceId, error.message);
        this.addLog('warn', `CLIPBOARD_SEND_FAILED ${device.deviceId} ${error.message}`);
      }
    }));
    this.emitState();
  }

  async sendCurrentClipboard() {
    const text = this.clipboard.readCurrentText();
    if (!text) {
      throw new Error('Clipboard is empty');
    }

    const event = {
      type: 'clipboard_update',
      eventId: uuidv4(),
      fromDeviceId: this.deviceId,
      fromDeviceName: this.deviceName,
      text,
      timestamp: Date.now()
    };
    this.saveHistory({
      eventId: event.eventId,
      text,
      sourceDeviceId: this.deviceId,
      sourceDeviceName: this.deviceName,
      direction: 'outgoing',
      createdAt: event.timestamp
    });
    await this.broadcastClipboard(event);
    return this.getState();
  }

  saveHistory(item) {
    this.database.addClipboardHistory(item);
    this.emit('history:new', item);
  }

  saveDevice(device) {
    const payload = {
      deviceId: safeString(device?.deviceId),
      deviceName: safeString(device?.deviceName, 'Android Device'),
      ipAddress: safeString(device?.ipAddress),
      port: safeNumber(device?.port, TCP_PORT),
      platform: safeString(device?.platform, 'android'),
      pairedAt: device?.pairedAt,
      lastSeen: device?.lastSeen,
      lastError: device?.lastError
    };
    if (!payload.deviceId || isIPv4Address(payload.deviceId)) {
      this.addLog('error', `DEVICE_SAVE_FAILED invalid device_id ${payload.deviceId || '(empty)'}`);
      throw new Error('Cannot save paired device without a valid device id');
    }
    this.addLog('info', `DEVICE_SAVE_PAYLOAD ${JSON.stringify(payload)}`);
    try {
      this.database.upsertDevice(payload);
    } catch (error) {
      this.addLog('error', `DEVICE_SAVE_FAILED ${error.message}`);
      throw error;
    }
  }

  logPairedDevices() {
    const devices = this.database.listDevices().map((device) => ({
      device_id: device.deviceId,
      name: device.deviceName,
      ip: device.ipAddress,
      port: device.port,
      platform: device.platform
    }));
    this.addLog('info', `PAIRED_DEVICE_LIST ${JSON.stringify(devices)}`);
  }

  addLog(level, message) {
    const item = { level, message, createdAt: Date.now() };
    this.logs.unshift(item);
    this.logs = this.logs.slice(0, 300);
    this.log?.[level]?.(message);
    this.emit('log:new', item);
  }

  async emitState() {
    this.emit('state:update', await this.getState());
  }
}

module.exports = SyncController;

function maskCode(code) {
  const value = safeString(code);
  if (!value) {
    return '';
  }
  return `${'*'.repeat(Math.max(value.length - 2, 0))}${value.slice(-2)}`;
}

function redactPairingPayload(payload) {
  return {
    ...payload,
    pairingCode: maskCode(payload?.pairingCode)
  };
}
