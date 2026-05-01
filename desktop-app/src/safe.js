function safeString(value, fallback = '') {
  return value === undefined || value === null ? fallback : String(value);
}

function safeNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

module.exports = {
  safeString,
  safeNumber
};
