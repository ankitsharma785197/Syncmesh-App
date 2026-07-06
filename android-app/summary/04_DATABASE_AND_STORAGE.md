# 04 — Database & Storage

## 1. Overview of storage mechanisms

| Mechanism | Backing | Owner | Contents |
|-----------|---------|-------|----------|
| SQLite | `syncmesh.db` (v8) | `SyncDatabaseHelper` | devices, clipboard_history, sync_logs, app_settings |
| SharedPreferences | `syncmesh_prefs` | `AppPreferences` | device id/name, pairing code, keyboard flags |
| SharedPreferences | `syncmesh_keyboard_bridge` | `SyncMeshBridge` (keyboard) | auto‑send debounce state |
| In‑memory | `ConcurrentHashMap` | `AppRepository.nearbyDevices` | discovered nearby devices (volatile) |
| Keyboard prefs/files | HeliBoard's own datastore | keyboard module | keyboard settings, dictionaries, pinned clips (separate subsystem) |

There is **no Room, no DataStore (in the app module), no encrypted storage, no
EncryptedSharedPreferences, no Keystore usage**. All app data is plaintext.

## 2. SQLite database

- **File:** `syncmesh.db` — `SyncDatabaseHelper.DATABASE_NAME` (default app DB dir).
- **Version:** `DATABASE_VERSION = 8`.
- **Migration strategy:** unusual — `onCreate`, `onUpgrade`, `onDowngrade`, and `onOpen` all
  call `ensureSchema(db)`, which `CREATE TABLE IF NOT EXISTS` + additive `ALTER TABLE ADD
  COLUMN` guarded by a `PRAGMA table_info` existence check (`ensureColumn`/`hasColumn`).
  `onDowngrade` is deliberately overridden to avoid the default downgrade exception. This makes
  the schema **idempotent and forgiving** but means version bumps do no real migration work —
  columns are only ever added, never transformed or dropped.

### Tables

**`devices`** (paired devices):
```
id INTEGER PK AUTOINCREMENT
device_id TEXT UNIQUE          -- remote UUID
device_name TEXT
ip_address TEXT
port INTEGER
transport TEXT DEFAULT 'wifi'
bluetooth_address TEXT          -- never populated (Bluetooth not implemented)
paired_at INTEGER
last_seen INTEGER
last_error TEXT
```
Upsert via `insertWithOnConflict(..., CONFLICT_REPLACE)` on `device_id`. Read ordered by `paired_at DESC`.

**`clipboard_history`**:
```
id INTEGER PK AUTOINCREMENT
event_id TEXT UNIQUE            -- UUID; dedup key (CONFLICT_IGNORE on insert)
text TEXT                       -- full clipboard text, plaintext, unbounded length
source_device_id TEXT
source_device_name TEXT
direction TEXT DEFAULT 'local'  -- 'local' | 'remote'
created_at INTEGER
is_pinned INTEGER DEFAULT 0
```
Read ordered by `is_pinned DESC, created_at DESC`. `getLatestRemoteClipboardText()` returns the
newest `direction='remote'` text. **No row cap** — history grows unbounded until cleared.

**`sync_logs`**:
```
id INTEGER PK AUTOINCREMENT
level TEXT      -- DEBUG|INFO|WARN|ERROR
tag TEXT
message TEXT
created_at INTEGER
```
Capped at 250 rows: after each insert, `DELETE FROM sync_logs WHERE id NOT IN (SELECT id ...
ORDER BY id DESC LIMIT 250)`. Read ordered by `created_at DESC`.

**`app_settings`** (`key TEXT PK, value TEXT`): key/value store with `getSetting`/`setSetting`.
**Defined but effectively unused** — no production code calls `getSetting`/`setSetting`
(candidate dead code; verify before removal).

### Concurrency & correctness

- `AppRepository` serializes DB writes/reads via a private `databaseLock` monitor **and**
  `SyncDatabaseHelper`'s methods are `synchronized`. Two distinct lock scopes exist (the
  repository's `databaseLock` vs. the helper instance monitor) — writes done directly through
  `SyncDatabaseHelper` (e.g. `insertClipboardHistory`) are guarded by the helper monitor, while
  writes done via raw `getWritableDatabase()` in `AppRepository` are guarded by `databaseLock`.
  SQLite itself serializes, so this is safe but the double locking is redundant/inconsistent.
- Cursors are closed in `finally` blocks throughout. Good.

## 3. SharedPreferences

**`syncmesh_prefs`** (`AppPreferences`, `MODE_PRIVATE`):
| Key | Type | Notes |
|-----|------|------|
| `device_id` | String | random UUID, lazily created |
| `pairing_code` | String | 6‑digit, `SecureRandom` |
| `device_name` | String | manufacturer+model default |
| `auto_send_keyboard` | boolean | current keyboard auto‑send flag (default true) |
| `keyboard_auto_send_enabled` | boolean | legacy mirror key, written in tandem for back‑compat |
| `last_keyboard_sent_text` | String | (setter exists; not read in app flow) |
| `last_keyboard_sent_at` | long | (setter exists; not read in app flow) |
| `keyboard_language` | String | default `"en"` (getter/setter exist; unused by app UI) |

**`syncmesh_keyboard_bridge`** (`SyncMeshBridge`, keyboard side, `MODE_PRIVATE`):
| Key | Type | Notes |
|-----|------|------|
| `last_auto_sent_text` | String | debounce: last text auto‑sent |
| `last_auto_sent_at` | long | debounce: timestamp (`AUTO_SEND_DEBOUNCE_MS = 3000`) |

The keyboard bridge auto‑send flag is read from the **app's** repository via reflection
(`isKeyboardAutoSendEnabled`), not from the bridge prefs — the bridge prefs only hold debounce state.

## 4. Tokens / sessions / secrets

- **No auth tokens or sessions** exist. The only credential is the 6‑digit **pairing code**,
  stored in plaintext SharedPreferences and sent in plaintext over TCP.
- **No encryption keys** are generated or stored by the app.
- The **release signing keystore** `syncmesh-release.jks` and its passwords are committed to
  the repo (`app/build.gradle.kts:11-13`, `gradle.properties`). This is a build secret, not
  runtime storage, but it is a Critical exposure (see `06_SECURITY_ANALYSIS.md`).

## 5. Cache & local files

- No explicit file cache, no downloaded files, no `getCacheDir()` usage in the app module.
- QR bitmaps are generated in‑memory (`BarcodeEncoder.createBitmap`) and shown in a dialog; not persisted.
- The keyboard module manages its own dictionaries/gesture data/pinned clips under its
  subsystem (out of scope for the app's storage model; a `GestureFileProvider` exists in the
  keyboard manifest for emailing gesture data).

## 6. Backup behaviour

- `android:allowBackup="true"` with `@xml/backup_rules` (`full-backup-content` empty) and
  `@xml/data_extraction_rules` (empty `cloud-backup`). Both are the default templates with all
  rules commented out → **the whole app data set (including `syncmesh_prefs` with the pairing
  code and the SQLite DB with clipboard history) is eligible for Android Auto Backup / cloud
  backup / device‑transfer.** No exclusions are configured. See security doc.

## 7. Where each important datum lives (quick reference)

| Datum | Location |
|-------|----------|
| Local device UUID | `syncmesh_prefs.device_id` |
| Local pairing code | `syncmesh_prefs.pairing_code` |
| Paired devices | `devices` table |
| Clipboard history (local + remote text) | `clipboard_history` table |
| Debug logs | `sync_logs` table (≤250) |
| Nearby devices | in‑memory only (`AppRepository.nearbyDevices`) |
| Keyboard auto‑send flag | `syncmesh_prefs.auto_send_keyboard` (+ legacy mirror) |
| Keyboard auto‑send debounce | `syncmesh_keyboard_bridge` prefs |
| Release signing secrets | `syncmesh-release.jks` + `gradle.properties` (committed) |
