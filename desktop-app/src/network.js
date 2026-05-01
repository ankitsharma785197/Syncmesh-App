const os = require('os');

function getLocalIPv4() {
  const interfaces = os.networkInterfaces();
  const privateAddresses = [];
  const otherAddresses = [];

  for (const records of Object.values(interfaces)) {
    for (const record of records || []) {
      if (record.family !== 'IPv4' || record.internal || record.address === '127.0.0.1') {
        continue;
      }

      if (isPrivateIPv4(record.address)) {
        privateAddresses.push(record.address);
      } else {
        otherAddresses.push(record.address);
      }
    }
  }

  privateAddresses.sort((a, b) => privateIPv4Priority(a) - privateIPv4Priority(b));
  return privateAddresses[0] || otherAddresses[0] || '';
}

function isPrivateIPv4(address) {
  if (!address || typeof address !== 'string') {
    return false;
  }

  const parts = address.split('.').map(Number);
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) {
    return false;
  }

  return (
    parts[0] === 10 ||
    (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) ||
    (parts[0] === 192 && parts[1] === 168)
  );
}

function isIPv4Address(address) {
  if (!address || typeof address !== 'string') {
    return false;
  }

  const parts = address.split('.').map(Number);
  return parts.length === 4 && parts.every((part) => Number.isInteger(part) && part >= 0 && part <= 255);
}

function privateIPv4Priority(address) {
  const parts = address.split('.').map(Number);
  if (parts[0] === 192 && parts[1] === 168) {
    return 1;
  }
  if (parts[0] === 10) {
    return 2;
  }
  if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) {
    return 3;
  }
  return 99;
}

function getBroadcastAddresses() {
  const addresses = new Set(['255.255.255.255']);
  const interfaces = os.networkInterfaces();

  for (const records of Object.values(interfaces)) {
    for (const record of records || []) {
      if (record.family !== 'IPv4' || record.internal || !record.netmask) {
        continue;
      }

      const ipParts = record.address.split('.').map(Number);
      const maskParts = record.netmask.split('.').map(Number);
      if (ipParts.length !== 4 || maskParts.length !== 4) {
        continue;
      }

      const broadcast = ipParts.map((part, index) => (part & maskParts[index]) | (~maskParts[index] & 255));
      addresses.add(broadcast.join('.'));
    }
  }

  return [...addresses];
}

module.exports = {
  getLocalIPv4,
  getBroadcastAddresses,
  isIPv4Address,
  isPrivateIPv4
};
