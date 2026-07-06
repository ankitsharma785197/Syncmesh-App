// Wire protocol for the file-transfer channel (TCP 8991). Mirrors the Android
// app's TransferProtocol.java exactly so both peers interoperate.
//
// Framing: one UTF-8 JSON object per '\n'-terminated line for control messages,
// and exactly `size` raw bytes immediately after each `file_header` line.
//
// sender → receiver : transfer_offer   {transferId, fromDeviceId, fromDeviceName,
//                                       fileCount, totalSize, files:[{name,size}], timestamp}
// receiver → sender : transfer_response{transferId, accepted, message}
// per file:
// sender → receiver : file_header      {transferId, index, name, size}
// sender → receiver : <size raw bytes>
// receiver → sender : file_ack         {transferId, index, ok}
// finally:
// sender → receiver : transfer_complete{transferId}
// receiver → sender : transfer_result  {transferId, ok}
//
// Cancel from either side = close the socket (senders best-effort write a
// transfer_cancel line first).

const TRANSFER_PORT = 8991;

const TYPE_OFFER = 'transfer_offer';
const TYPE_RESPONSE = 'transfer_response';
const TYPE_FILE_HEADER = 'file_header';
const TYPE_FILE_ACK = 'file_ack';
const TYPE_COMPLETE = 'transfer_complete';
const TYPE_RESULT = 'transfer_result';
const TYPE_CANCEL = 'transfer_cancel';

const MAX_FILE_SIZE = 2 * 1024 * 1024 * 1024; // 2 GB
const MAX_FILES_PER_TRANSFER = 500;
const CHUNK_SIZE = 64 * 1024;

const ACCEPT_TIMEOUT_MS = 90000;
const DATA_TIMEOUT_MS = 300000;
const CONNECT_TIMEOUT_MS = 5000;

function buildOffer(transferId, deviceId, deviceName, files) {
  let totalSize = 0;
  const fileArray = files.map((file) => {
    totalSize += file.size;
    return { name: file.name, size: file.size };
  });
  return {
    type: TYPE_OFFER,
    transferId,
    fromDeviceId: deviceId,
    fromDeviceName: deviceName,
    fileCount: files.length,
    totalSize,
    files: fileArray,
    timestamp: Date.now()
  };
}

function buildResponse(transferId, accepted, message) {
  return {
    type: TYPE_RESPONSE,
    transferId,
    accepted,
    message: message == null ? '' : message
  };
}

function buildFileHeader(transferId, index, name, size) {
  return { type: TYPE_FILE_HEADER, transferId, index, name, size };
}

function buildFileAck(transferId, index, ok) {
  return { type: TYPE_FILE_ACK, transferId, index, ok };
}

function buildSimple(type, transferId) {
  return { type, transferId };
}

// Parses the `files` array of an offer. Returns null if malformed.
function parseOfferFiles(offer) {
  if (!Array.isArray(offer?.files)) {
    return null;
  }
  return offer.files.map((item, index) => ({
    index,
    name: sanitizeFileName(item?.name),
    size: Number.isFinite(Number(item?.size)) ? Number(item.size) : -1,
    completed: false
  }));
}

// Validates offer metadata. Returns a human-readable rejection reason, or
// null when the offer is acceptable. Security gate for the receiver.
function validateOffer(offer, files) {
  const fileCount = Number(offer?.fileCount ?? -1);
  const totalSize = Number(offer?.totalSize ?? -1);
  if (!files || !files.length) {
    return 'No files in transfer offer';
  }
  if (fileCount !== files.length) {
    return 'File count mismatch';
  }
  if (files.length > MAX_FILES_PER_TRANSFER) {
    return 'Too many files in one transfer';
  }
  let computedTotal = 0;
  for (const file of files) {
    if (!file.name) {
      return 'Illegal file name';
    }
    if (file.size < 0 || file.size > MAX_FILE_SIZE) {
      return 'File exceeds the 2 GB limit';
    }
    computedTotal += file.size;
  }
  if (totalSize !== computedTotal) {
    return 'Total size mismatch';
  }
  return null;
}

// Sanitizes a remote-supplied file name: strips path components (defeats
// "../../x" traversal), control characters and characters illegal on common
// filesystems, and bounds the length. Never returns an empty string.
function sanitizeFileName(rawName) {
  let name = rawName == null ? '' : String(rawName);
  const slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
  if (slash >= 0) {
    name = name.slice(slash + 1);
  }
  let cleaned = '';
  for (const c of name) {
    const code = c.codePointAt(0);
    if (code < 0x20 || code === 0x7f || '/\\:*?"<>|\0'.includes(c)) {
      continue;
    }
    cleaned += c;
  }
  name = cleaned.trim();
  if (!name || /^\.+$/.test(name)) {
    name = `file_${Date.now()}`;
  }
  if (name.length > 200) {
    const extension = extensionOf(name);
    const base = name.slice(0, 200 - Math.min(extension.length + 1, 50));
    name = extension ? `${base}.${extension}` : base;
  }
  return name;
}

function extensionOf(name) {
  if (!name) {
    return '';
  }
  const dot = name.lastIndexOf('.');
  if (dot <= 0 || dot === name.length - 1) {
    return '';
  }
  return name.slice(dot + 1).toLowerCase();
}

module.exports = {
  TRANSFER_PORT,
  TYPE_OFFER,
  TYPE_RESPONSE,
  TYPE_FILE_HEADER,
  TYPE_FILE_ACK,
  TYPE_COMPLETE,
  TYPE_RESULT,
  TYPE_CANCEL,
  MAX_FILE_SIZE,
  MAX_FILES_PER_TRANSFER,
  CHUNK_SIZE,
  ACCEPT_TIMEOUT_MS,
  DATA_TIMEOUT_MS,
  CONNECT_TIMEOUT_MS,
  buildOffer,
  buildResponse,
  buildFileHeader,
  buildFileAck,
  buildSimple,
  parseOfferFiles,
  validateOffer,
  sanitizeFileName,
  extensionOf
};
