const os = require('os');
const { v4: uuidv4 } = require('uuid');

function getOrCreateDeviceId(database) {
  let deviceId = database.getSetting('deviceId');
  if (!deviceId) {
    deviceId = uuidv4();
    database.setSetting('deviceId', deviceId);
  }
  return deviceId;
}

function getDeviceName(database) {
  const configured = database.getSetting('deviceName');
  if (configured) {
    return configured;
  }
  return os.hostname() || (process.platform === 'darwin' ? 'MacBook' : 'Windows PC');
}

function getPairingCode(database) {
  let code = database.getSetting('pairingCode');
  if (!code) {
    code = String(Math.floor(100000 + Math.random() * 900000));
    database.setSetting('pairingCode', code);
  }
  return code;
}

module.exports = {
  getOrCreateDeviceId,
  getDeviceName,
  getPairingCode
};
