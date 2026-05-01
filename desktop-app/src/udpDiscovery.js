const dgram = require('dgram');
const { DISCOVERY_INTERVAL_MS, TCP_PORT, UDP_DISCOVERY_PORT } = require('./constants');
const { getBroadcastAddresses, getLocalIPv4 } = require('./network');

class UdpDiscovery {
  constructor({ deviceId, deviceName, onDevice, logger }) {
    this.deviceId = deviceId;
    this.deviceName = deviceName;
    this.onDevice = onDevice;
    this.logger = logger;
    this.socket = null;
    this.interval = null;
  }

  start() {
    if (this.socket) {
      return Promise.resolve();
    }

    this.socket = dgram.createSocket({ type: 'udp4', reuseAddr: true });
    this.socket.on('message', (buffer, rinfo) => this.handleMessage(buffer, rinfo));
    this.socket.on('error', (error) => this.logger?.warn(`UDP discovery error: ${error.message}`));

    return new Promise((resolve, reject) => {
      this.socket.once('error', reject);
      this.socket.bind(UDP_DISCOVERY_PORT, () => {
        this.socket.off('error', reject);
        this.socket.setBroadcast(true);
        this.announce();
        this.interval = setInterval(() => this.announce(), DISCOVERY_INTERVAL_MS);
        resolve();
      });
    });
  }

  stop() {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }

    if (!this.socket) {
      return Promise.resolve();
    }

    return new Promise((resolve) => {
      this.socket.close(() => resolve());
      this.socket = null;
    });
  }

  announce() {
    if (!this.socket) {
      return;
    }

    const message = Buffer.from(JSON.stringify({
      type: 'discovery_announce',
      deviceId: this.deviceId,
      deviceName: this.deviceName,
      ipAddress: getLocalIPv4(),
      port: TCP_PORT,
      platform: 'desktop',
      timestamp: Date.now()
    }));

    for (const address of getBroadcastAddresses()) {
      this.socket.send(message, 0, message.length, UDP_DISCOVERY_PORT, address);
    }
  }

  handleMessage(buffer, rinfo) {
    try {
      const message = JSON.parse(buffer.toString('utf8'));
      if (message.type !== 'discovery_announce' || message.deviceId === this.deviceId) {
        return;
      }

      this.onDevice({
        deviceId: message.deviceId,
        deviceName: message.deviceName || 'Nearby Device',
        ipAddress: message.ipAddress || rinfo.address,
        port: Number(message.port || TCP_PORT),
        platform: message.platform || 'unknown',
        timestamp: message.timestamp || Date.now(),
        lastSeen: Date.now()
      });
    } catch (error) {
      this.logger?.warn(`Invalid UDP discovery packet: ${error.message}`);
    }
  }
}

module.exports = UdpDiscovery;
