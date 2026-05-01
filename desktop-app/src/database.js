const path = require('path');
const fs = require('fs');
const initSqlJs = require('sql.js');
const { safeNumber, safeString } = require('./safe');
const { isIPv4Address } = require('./network');

class SyncMeshDatabase {
  constructor(userDataPath) {
    this.userDataPath = userDataPath;
    this.dbPath = path.join(userDataPath, 'syncmesh-desktop.sqlite');
    this.db = null;
  }

  async initialize() {
    fs.mkdirSync(this.userDataPath, { recursive: true });

    const SQL = await initSqlJs({
      locateFile: (file) => resolveSqlJsFile(file)
    });

    if (fs.existsSync(this.dbPath)) {
      this.db = new SQL.Database(fs.readFileSync(this.dbPath));
    } else {
      this.db = new SQL.Database();
    }

    this.db.run(`
      CREATE TABLE IF NOT EXISTS devices (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_id TEXT NOT NULL UNIQUE,
        device_name TEXT NOT NULL,
        ip_address TEXT NOT NULL,
        port INTEGER NOT NULL DEFAULT 8989,
        platform TEXT NOT NULL,
        paired_at INTEGER NOT NULL,
        last_seen INTEGER,
        last_error TEXT
      );

      CREATE TABLE IF NOT EXISTS clipboard_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        event_id TEXT NOT NULL UNIQUE,
        text TEXT NOT NULL,
        source_device_id TEXT,
        source_device_name TEXT,
        direction TEXT NOT NULL,
        created_at INTEGER NOT NULL
      );

      CREATE TABLE IF NOT EXISTS settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      );
    `);
    this.persist();
  }

  getSetting(key, fallback = null) {
    const row = this.get('SELECT value FROM settings WHERE key = ?', [key]);
    return row ? row.value : fallback;
  }

  setSetting(key, value) {
    this.run(`
      INSERT INTO settings (key, value)
      VALUES (?, ?)
      ON CONFLICT(key) DO UPDATE SET value = excluded.value
    `, [safeString(key), safeString(value)]);
  }

  getAllSettings() {
    const rows = this.all('SELECT key, value FROM settings');
    return rows.reduce((settings, row) => {
      settings[row.key] = row.value;
      return settings;
    }, {});
  }

  upsertDevice(device) {
    const now = Date.now();
    const payload = {
      deviceId: safeString(device?.deviceId),
      deviceName: safeString(device?.deviceName, 'Android Device'),
      ipAddress: safeString(device?.ipAddress),
      port: safeNumber(device?.port, 8989),
      platform: safeString(device?.platform, 'android'),
      pairedAt: safeNumber(device?.pairedAt, now),
      lastSeen: safeNumber(device?.lastSeen, now),
      lastError: device?.lastError === undefined ? null : device.lastError
    };

    this.run(`
      INSERT INTO devices (
        device_id, device_name, ip_address, port, platform, paired_at, last_seen, last_error
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(device_id) DO UPDATE SET
        device_name = excluded.device_name,
        ip_address = excluded.ip_address,
        port = excluded.port,
        platform = excluded.platform,
        last_seen = excluded.last_seen,
        last_error = excluded.last_error
    `, [
      payload.deviceId,
      payload.deviceName,
      payload.ipAddress,
      payload.port,
      payload.platform,
      payload.pairedAt,
      payload.lastSeen,
      payload.lastError
    ]);
  }

  listDevices() {
    return this.all(`
      SELECT
        id,
        device_id AS deviceId,
        device_name AS deviceName,
        ip_address AS ipAddress,
        port,
        platform,
        paired_at AS pairedAt,
        last_seen AS lastSeen,
        last_error AS lastError
      FROM devices
      ORDER BY last_seen DESC, paired_at DESC
    `).filter((device) => !isIPv4Address(device.deviceId));
  }

  getDevice(deviceId) {
    const safeDeviceId = safeString(deviceId);
    if (!safeDeviceId || isIPv4Address(safeDeviceId)) {
      return null;
    }

    return this.get(`
      SELECT
        id,
        device_id AS deviceId,
        device_name AS deviceName,
        ip_address AS ipAddress,
        port,
        platform,
        paired_at AS pairedAt,
        last_seen AS lastSeen,
        last_error AS lastError
      FROM devices
      WHERE device_id = ?
    `, [safeDeviceId]);
  }

  listKnownDeviceIds() {
    return this.listDevices().map((device) => device.deviceId);
  }

  removeDevice(deviceId) {
    this.run('DELETE FROM devices WHERE device_id = ?', [safeString(deviceId)]);
  }

  markDeviceSeen(deviceId, ipAddress, port) {
    this.run(`
      UPDATE devices
      SET last_seen = ?, ip_address = COALESCE(?, ip_address), port = COALESCE(?, port), last_error = NULL
      WHERE device_id = ?
    `, [
      Date.now(),
      ipAddress === undefined || ipAddress === null ? null : safeString(ipAddress),
      port === undefined || port === null ? null : safeNumber(port, null),
      safeString(deviceId)
    ]);
  }

  markDeviceError(deviceId, error) {
    this.run('UPDATE devices SET last_error = ? WHERE device_id = ?', [safeString(error), safeString(deviceId)]);
  }

  addClipboardHistory(item) {
    this.run(`
      INSERT OR IGNORE INTO clipboard_history (
        event_id, text, source_device_id, source_device_name, direction, created_at
      )
      VALUES (?, ?, ?, ?, ?, ?)
    `, [
      safeString(item?.eventId),
      safeString(item?.text),
      item?.sourceDeviceId === undefined ? null : safeString(item.sourceDeviceId),
      item?.sourceDeviceName === undefined ? null : safeString(item.sourceDeviceName),
      safeString(item?.direction, 'unknown'),
      safeNumber(item?.createdAt, Date.now())
    ]);
  }

  listClipboardHistory(limit = 100) {
    return this.all(`
      SELECT
        id,
        event_id AS eventId,
        text,
        source_device_id AS sourceDeviceId,
        source_device_name AS sourceDeviceName,
        direction,
        created_at AS createdAt
      FROM clipboard_history
      ORDER BY created_at DESC
      LIMIT ?
    `, [Math.min(safeNumber(limit, 100), 500)]);
  }

  run(sql, params = []) {
    const stmt = this.db.prepare(sql);
    try {
      stmt.bind(sanitizeParams(params));
      stmt.step();
    } finally {
      stmt.free();
    }
    this.persist();
  }

  get(sql, params = []) {
    const rows = this.all(sql, params);
    return rows[0] || null;
  }

  all(sql, params = []) {
    const stmt = this.db.prepare(sql);
    const rows = [];
    try {
      stmt.bind(sanitizeParams(params));
      while (stmt.step()) {
        rows.push(stmt.getAsObject());
      }
    } finally {
      stmt.free();
    }
    return rows;
  }

  persist() {
    fs.writeFileSync(this.dbPath, Buffer.from(this.db.export()));
  }

  close() {
    if (this.db) {
      this.persist();
      this.db.close();
      this.db = null;
    }
  }
}

module.exports = SyncMeshDatabase;

function sanitizeParams(params) {
  return params.map((value) => (value === undefined ? null : value));
}

function resolveSqlJsFile(file) {
  const devPath = path.join(__dirname, '..', 'node_modules', 'sql.js', 'dist', file);
  if (fs.existsSync(devPath)) {
    return devPath;
  }

  const unpackedPath = path.join(
    __dirname.replace('app.asar', 'app.asar.unpacked'),
    '..',
    'node_modules',
    'sql.js',
    'dist',
    file
  );
  if (fs.existsSync(unpackedPath)) {
    return unpackedPath;
  }

  return devPath;
}
