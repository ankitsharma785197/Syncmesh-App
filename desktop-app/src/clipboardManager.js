const { clipboard } = require('electron');
const { v4: uuidv4 } = require('uuid');
const { CLIPBOARD_POLL_MS } = require('./constants');

class ClipboardManager {
  constructor({ deviceId, deviceName, onLocalClipboard, onHistory, logger }) {
    this.deviceId = deviceId;
    this.deviceName = deviceName;
    this.onLocalClipboard = onLocalClipboard;
    this.onHistory = onHistory;
    this.logger = logger;
    this.interval = null;
    this.lastText = '';
    this.suppressText = null;
  }

  start() {
    if (this.interval) {
      return;
    }

    this.lastText = clipboard.readText() || '';
    this.interval = setInterval(() => this.checkClipboard(), CLIPBOARD_POLL_MS);
  }

  stop() {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
  }

  checkClipboard() {
    const text = clipboard.readText() || '';
    if (!text || text === this.lastText) {
      return;
    }

    this.lastText = text;
    if (this.suppressText === text) {
      this.suppressText = null;
      return;
    }

    const eventId = uuidv4();
    const event = {
      type: 'clipboard_update',
      eventId,
      fromDeviceId: this.deviceId,
      fromDeviceName: this.deviceName,
      text,
      timestamp: Date.now()
    };

    this.onHistory?.({
      eventId,
      text,
      sourceDeviceId: this.deviceId,
      sourceDeviceName: this.deviceName,
      direction: 'outgoing',
      createdAt: event.timestamp
    });
    this.onLocalClipboard?.(event);
  }

  writeRemoteClipboard(text) {
    this.suppressText = text;
    this.lastText = text;
    clipboard.writeText(text);
  }

  readCurrentText() {
    return clipboard.readText() || '';
  }
}

module.exports = ClipboardManager;
