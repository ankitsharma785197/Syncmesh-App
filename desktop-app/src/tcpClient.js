const net = require('net');
const { TCP_TIMEOUT_MS } = require('./constants');

function sendJsonLine({ host, port, message, timeout = TCP_TIMEOUT_MS }) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host, port }, () => {
      socket.write(`${JSON.stringify(message)}\n`);
    });

    let buffer = '';
    let settled = false;

    const done = (error, response) => {
      if (settled) {
        return;
      }
      settled = true;
      socket.destroy();
      if (error) {
        reject(error);
      } else {
        resolve(response);
      }
    };

    socket.setTimeout(timeout);

    socket.on('data', (data) => {
      buffer += data.toString('utf8');
      const newlineIndex = buffer.indexOf('\n');
      if (newlineIndex >= 0) {
        const line = buffer.slice(0, newlineIndex).trim();
        if (!line) {
          done(null, null);
          return;
        }
        try {
          done(null, JSON.parse(line));
        } catch (error) {
          done(error);
        }
      }
    });

    socket.on('timeout', () => done(new Error('TCP request timed out')));
    socket.on('error', done);
    socket.on('close', () => {
      if (!settled) {
        resolve(null);
      }
    });
  });
}

module.exports = {
  sendJsonLine
};
