// Conflict-safe destinations for received files. Files land in
// <Downloads>/SyncMesh/ with "photo.jpg" → "photo (1).jpg" numbering, written
// to a hidden .part temp file and renamed into place on commit so a failed
// transfer never leaves a half-written file behind.

const fs = require('fs');
const path = require('path');
const { sanitizeFileName, extensionOf, CHUNK_SIZE } = require('./transferProtocol');

class TransferStorage {
  constructor(saveDir) {
    this.saveDir = saveDir;
  }

  // Reserves a unique destination for `rawName` and opens a temp write stream.
  open(rawName) {
    fs.mkdirSync(this.saveDir, { recursive: true });
    const name = sanitizeFileName(rawName);
    const finalPath = this.uniquePath(name);
    const tempPath = path.join(
      this.saveDir,
      `.${path.basename(finalPath)}.${process.pid}.${Date.now()}.part`
    );
    const stream = fs.createWriteStream(tempPath, { highWaterMark: CHUNK_SIZE });
    return new Destination(tempPath, finalPath, stream);
  }

  uniquePath(name) {
    const extension = extensionOf(name);
    const base = extension ? name.slice(0, name.length - extension.length - 1) : name;
    let candidate = path.join(this.saveDir, name);
    let counter = 1;
    while (fs.existsSync(candidate)) {
      const numbered = extension
        ? `${base} (${counter}).${extension}`
        : `${base} (${counter})`;
      candidate = path.join(this.saveDir, numbered);
      counter += 1;
    }
    return candidate;
  }
}

class Destination {
  constructor(tempPath, finalPath, stream) {
    this.tempPath = tempPath;
    this.finalPath = finalPath;
    this.stream = stream;
    this.fileName = path.basename(finalPath);
  }

  async write(chunk) {
    if (!this.stream.write(chunk)) {
      await new Promise((resolve, reject) => {
        const onDrain = () => {
          this.stream.off('error', onError);
          resolve();
        };
        const onError = (error) => {
          this.stream.off('drain', onDrain);
          reject(error);
        };
        this.stream.once('drain', onDrain);
        this.stream.once('error', onError);
      });
    }
  }

  async commit() {
    await new Promise((resolve, reject) => {
      this.stream.end((error) => (error ? reject(error) : resolve()));
    });
    fs.renameSync(this.tempPath, this.finalPath);
  }

  discard() {
    try {
      this.stream.destroy();
    } catch (_) {}
    try {
      fs.unlinkSync(this.tempPath);
    } catch (_) {}
  }
}

module.exports = TransferStorage;
