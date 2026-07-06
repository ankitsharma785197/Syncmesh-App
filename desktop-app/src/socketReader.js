// Buffered reader over a net.Socket that supports the transfer channel's mixed
// framing: '\n'-terminated UTF-8 JSON control lines interleaved with raw byte
// payloads. A plain readline interface would swallow the start of the payload
// that follows a file_header line, so all reads go through one byte queue.

const MAX_LINE_BYTES = 512 * 1024;
const HIGH_WATER_BYTES = 4 * 1024 * 1024;

class SocketReader {
  constructor(socket) {
    this.socket = socket;
    this.chunks = [];
    this.buffered = 0;
    this.waiter = null;
    this.ended = false;
    this.error = null;

    socket.on('data', (chunk) => {
      this.chunks.push(chunk);
      this.buffered += chunk.length;
      if (this.buffered >= HIGH_WATER_BYTES) {
        socket.pause();
      }
      this.wake();
    });
    socket.on('end', () => {
      this.ended = true;
      this.wake();
    });
    socket.on('close', () => {
      this.ended = true;
      this.wake();
    });
    socket.on('error', (error) => {
      this.error = error;
      this.wake();
    });
  }

  wake() {
    const waiter = this.waiter;
    if (waiter) {
      this.waiter = null;
      waiter();
    }
  }

  waitForData() {
    return new Promise((resolve) => {
      this.waiter = resolve;
    });
  }

  take(maxBytes) {
    if (!this.chunks.length) {
      return null;
    }
    let chunk = this.chunks[0];
    if (chunk.length <= maxBytes) {
      this.chunks.shift();
    } else {
      this.chunks[0] = chunk.subarray(maxBytes);
      chunk = chunk.subarray(0, maxBytes);
    }
    this.buffered -= chunk.length;
    if (this.buffered < HIGH_WATER_BYTES / 2 && this.socket.isPaused()) {
      this.socket.resume();
    }
    return chunk;
  }

  // Reads one '\n'-terminated UTF-8 line. Returns null on clean end-of-stream.
  async readLine() {
    const parts = [];
    let partsLength = 0;
    for (;;) {
      while (this.chunks.length) {
        const chunk = this.chunks[0];
        const newline = chunk.indexOf(0x0a);
        if (newline >= 0) {
          parts.push(this.take(newline));
          this.take(1); // consume the '\n'
          return Buffer.concat(parts).toString('utf8');
        }
        parts.push(this.take(chunk.length));
        partsLength += chunk.length;
        if (partsLength > MAX_LINE_BYTES) {
          throw new Error('Control line too long');
        }
      }
      if (this.error) {
        throw this.error;
      }
      if (this.ended) {
        return partsLength ? Buffer.concat(parts).toString('utf8') : null;
      }
      await this.waitForData();
    }
  }

  // Reads exactly `size` raw bytes, delivering each chunk to the async
  // `onChunk(buffer)` callback (which may itself await, e.g. on disk drain).
  async readBytes(size, onChunk) {
    let remaining = size;
    while (remaining > 0) {
      const chunk = this.take(remaining);
      if (chunk) {
        remaining -= chunk.length;
        await onChunk(chunk);
        continue;
      }
      if (this.error) {
        throw this.error;
      }
      if (this.ended) {
        throw new Error('Stream ended mid-file');
      }
      await this.waitForData();
    }
  }
}

module.exports = SocketReader;
