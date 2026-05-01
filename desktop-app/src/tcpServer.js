const net = require('net');
const { TCP_PORT } = require('./constants');

class TcpServer {
  constructor({ port = TCP_PORT, onMessage, logger }) {
    this.port = port;
    this.onMessage = onMessage;
    this.logger = logger;
    this.server = null;
    this.sockets = new Set();
  }

  start() {
    if (this.server) {
      return Promise.resolve();
    }

    this.server = net.createServer((socket) => this.handleConnection(socket));

    this.server.on('error', (err) => {
      this.logger?.error?.(`TCP_SERVER_ERROR ${err.code || ''} ${err.message}`);
    });

    this.server.on('close', () => {
      this.logger?.info?.('TCP_SERVER_CLOSED');
    });

    return new Promise((resolve, reject) => {
      const onStartError = (err) => {
        this.server = null;
        reject(err);
      };

      this.server.once('error', onStartError);

      this.server.listen(this.port, '0.0.0.0', () => {
        this.server.off('error', onStartError);
        this.logger?.info?.(`TCP_SERVER_STARTED_ON_0_0_0_0_${this.port}`);
        resolve();
      });
    });
  }

  stop() {
    if (!this.server) {
      return Promise.resolve();
    }

    for (const socket of this.sockets) {
      try {
        socket.destroy();
      } catch (_) {}
    }

    this.sockets.clear();

    return new Promise((resolve) => {
      const server = this.server;
      this.server = null;

      try {
        server.close(() => resolve());
      } catch (_) {
        resolve();
      }
    });
  }

  handleConnection(socket) {
    this.sockets.add(socket);

    socket.setEncoding('utf8');
    socket.setNoDelay(true);

    let buffer = '';
    let closed = false;

    const remoteAddress = normalizeAddress(socket.remoteAddress);
    const remotePort = socket.remotePort;

    this.logger?.info?.(`TCP_CLIENT_CONNECTED ${remoteAddress}:${remotePort}`);

    const safeWrite = (payload) => {
      if (closed || socket.destroyed || !socket.writable) {
        return;
      }

      try {
        socket.write(`${JSON.stringify(payload)}\n`);
      } catch (error) {
        this.logger?.warn?.(`TCP_SOCKET_WRITE_FAILED ${error.code || ''} ${error.message}`);
      }
    };

    socket.on('data', async (chunk) => {
      buffer += chunk;
      let newlineIndex = buffer.indexOf('\n');

      while (newlineIndex >= 0) {
        const line = buffer.slice(0, newlineIndex).trim();
        buffer = buffer.slice(newlineIndex + 1);
        newlineIndex = buffer.indexOf('\n');

        if (!line) {
          continue;
        }

        try {
          this.logger?.info?.(`TCP_RAW_MESSAGE_RECEIVED ${remoteAddress}:${remotePort}`);

          const message = JSON.parse(line);
          const response = await this.onMessage(message, {
            remoteAddress,
            remotePort
          });

          if (response) {
            safeWrite(response);
          }
        } catch (error) {
          this.logger?.warn?.(`TCP_MESSAGE_REJECTED ${error.message}`);
          safeWrite({ type: 'error', message: error.message });
        }
      }
    });

    socket.on('error', (err) => {
      if (err.code === 'ECONNRESET') {
        this.logger?.info?.(`TCP_SOCKET_RESET_BY_PEER ${remoteAddress}:${remotePort}`);
        return;
      }

      this.logger?.error?.(`TCP_SOCKET_ERROR ${err.code || ''} ${err.message}`);
    });

    socket.on('close', () => {
      closed = true;
      this.sockets.delete(socket);
      this.logger?.info?.(`TCP_SOCKET_CLOSED ${remoteAddress}:${remotePort}`);
    });

    socket.on('end', () => {
      closed = true;
      this.logger?.info?.(`TCP_SOCKET_ENDED ${remoteAddress}:${remotePort}`);
    });
  }
}

function normalizeAddress(address) {
  if (!address) {
    return '';
  }

  return address.replace(/^::ffff:/, '');
}

module.exports = TcpServer;