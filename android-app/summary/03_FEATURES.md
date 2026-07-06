# 03 — Features

Each feature below is documented from the actual source. Fields: **Purpose, Files, Classes,
User flow, APIs (wire), Storage, Dependencies.**

---

## Feature 1 — Foreground sync service (Start/Stop Sync)

- **Purpose:** Keep the sync runtime (TCP server, UDP discovery, clipboard monitoring) alive
  in the background with a persistent notification.
- **Files:** `sync/SyncForegroundService.java`, `sync/SyncCoordinator.java`,
  `util/NotificationHelper.java`, `ui/HomeFragment.java`.
- **Classes:** `SyncForegroundService` (Service), `SyncCoordinator`, `NotificationHelper`.
- **User flow:** Home → **Start Sync**. On API 33+ requests `POST_NOTIFICATIONS` first
  (`HomeFragment.toggleService` → `RC_NOTIFICATIONS`). `ContextCompat.startForegroundService`
  → `onStartCommand(ACTION_START)` → `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_DATA_SYNC)`
  → `coordinator.startRuntime()`. Stop via notification action or the button →
  `createStopIntent` → `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf`.
- **APIs (wire):** none directly; it boots the servers.
- **Storage:** none directly; snapshot state pushed to `serviceSnapshotLiveData`.
- **Dependencies:** `androidx.core` `ServiceCompat`, notification channels.
- **Notes:** `START_STICKY`, `stopWithTask="false"` (survives task swipe), `onTimeout()` calls
  `stopSelf` (Android 14 dataSync FGS timeout). Runtime state guarded by `AtomicBoolean runtimeRunning`.

## Feature 2 — Automatic clipboard propagation

- **Purpose:** When text is copied locally, push it to all paired devices; when a remote
  device sends text, place it on this device's clipboard.
- **Files:** `sync/ClipboardSyncManager.java`, `sync/SyncCoordinator.java`,
  `sync/TcpClient.java`, `sync/TcpServer.java`.
- **Classes:** `ClipboardSyncManager` (owns `OnPrimaryClipChangedListener`), `SyncCoordinator`.
- **User flow (send):** copy text anywhere → `onPrimaryClipChanged` → `handleClipboardChanged`
  dedups (see below) → `LocalClipboardListener.onLocalClipboardChanged` →
  `SyncCoordinator.handleLocalClipboardChanged` saves a `local` history row and, if runtime
  running, fans out `clipboard_update` to every paired device on `executorService`.
- **User flow (receive):** `TcpServer` → `handleRemoteClipboard` verifies sender is paired,
  dedups by `eventId`, saves a `remote` row, and `ClipboardSyncManager.applyRemoteClipboard`
  sets the system clipboard on the main thread.
- **APIs (wire):** `clipboard_update` (fire‑and‑forget TCP).
- **Storage:** `clipboard_history` table (event_id UNIQUE, `CONFLICT_IGNORE`).
- **Dependencies:** Android `ClipboardManager`.
- **Dedup logic:** `recentEventIds` LinkedHashMap with 30 s TTL; a 2 s `DUPLICATE_WINDOW_MS`
  suppresses the echo of a just‑applied remote clip and rapid duplicate listener fires.
- **Constraint (documented in strings):** Android 10+ restricts background clipboard reads —
  the auto listener only fires reliably while the app/keyboard has focus; hence the keyboard add‑on.

## Feature 3 — Device pairing (manual / QR / nearby)

- **Purpose:** Establish a trusted `PairedDevice` on both ends using a 6‑digit code.
- **Files:** `ui/PairFragment.java`, `sync/SyncCoordinator.java` (`sendPairRequest`,
  `handlePairRequest`), `ui/QrScannerActivity.java`, `ui/adapter/NearbyDevicesAdapter.java`,
  `data/AppPreferences.java` (pairing code), `data/AppRepository.java` (upsert).
- **Classes:** `PairFragment` (implements `NearbyDevicesAdapter.Listener`), `SyncCoordinator`,
  `QrScannerActivity`, ZXing `IntentIntegrator`/`BarcodeEncoder`/`CaptureManager`.
- **User flow:**
  - *Manual:* enter IPv4 + port (prefilled `8989`) + code → **Pair Device** → `sendPairRequest`.
  - *QR (show):* `showPairQr` builds a `syncmesh_pair_qr` JSON, renders a 720×720 QR via
    `MultiFormatWriter`/`BarcodeEncoder`, shows it in a Material dialog. **Requires sync running.**
  - *QR (scan):* **Scan QR** → camera permission → `IntentIntegrator` → `QrScannerActivity`
    (`CaptureManager`) → `applyQrContents` autofills IP/port/code.
  - *Nearby:* tap a discovered device (`onUseDevice`) → autofills IP/port; if code present, pairs immediately.
- **APIs (wire):** `pair_request` → `pair_response` (TCP). QR uses out‑of‑band `syncmesh_pair_qr`.
- **Storage:** `devices` table (`device_id` UNIQUE, `CONFLICT_REPLACE`); pairing code in `syncmesh_prefs`.
- **Dependencies:** ZXing core 3.5.3 + zxing‑android‑embedded 4.3.0; Material dialogs.
- **Security note:** acceptance is purely `localPairingCode.equals(incomingCode)`; both sides
  store each other. No challenge/response, no rate limiting. See `06_SECURITY_ANALYSIS.md`.

## Feature 4 — Nearby discovery

- **Purpose:** Advertise this device and list others on the same L2 network.
- **Files:** `sync/UdpDiscoveryManager.java`, `sync/SyncCoordinator.java`
  (`handleDiscoveryAnnouncement`), `data/AppRepository.java` (nearby map), `util/NetworkUtils.java`.
- **Classes:** `UdpDiscoveryManager`, `SyncCoordinator`, `AppRepository`.
- **User flow:** automatic while sync runs; results appear in **Pair → Nearby devices**.
- **APIs (wire):** `discovery_announce` UDP broadcast every 3 s (`ANNOUNCE_INTERVAL_MS`) to all
  interface broadcast addresses + `255.255.255.255`, port 8990.
- **Storage:** in‑memory `ConcurrentHashMap` only; **not persisted**. Pruned after 15 s
  (`STALE_THRESHOLD_MS`) and cleared on stop.
- **Dependencies:** `WifiManager.MulticastLock` ("syncmesh:discovery"), `CHANGE_WIFI_MULTICAST_STATE` permission.

## Feature 5 — Ping / reachability

- **Purpose:** Test whether a paired device is reachable and update last‑seen/last‑error.
- **Files:** `ui/PairedDevicesFragment.java`, `ui/adapter/PairedDevicesAdapter.java`,
  `sync/SyncCoordinator.java` (`pingDevice`, `handlePing`).
- **User flow:** Devices → **Ping** on a device row → toast success/failure; on failure the
  error string is stored and shown in red on the card.
- **APIs (wire):** `ping` → `pong` (TCP, expectResponse=true).
- **Storage:** `devices.last_seen` / `devices.last_error` updated.

## Feature 6 — Clipboard history

- **Purpose:** Persist and browse every clipboard event; copy any entry back.
- **Files:** `ui/ClipboardHistoryFragment.java`, `ui/ClipboardHistoryActivity.java`,
  `ui/adapter/ClipboardHistoryAdapter.java`, `data/SyncDatabaseHelper.java`, `data/AppRepository.java`.
- **User flow:** History tab → list ordered `is_pinned DESC, created_at DESC`; tap a row to
  copy its text to the clipboard; **Clear History** wipes the table (with confirm dialog).
- **Storage:** `clipboard_history` table. Pin/delete APIs exist in the DB layer
  (`updateClipboardPinned`, `deleteClipboardEntry`) but **no UI wires pin/delete** in the app
  (only copy + clear). Pinning is set only through the keyboard/DB paths.
- **Dependencies:** RecyclerView, Material dialog.

## Feature 7 — Debug console

- **Purpose:** Inspect live runtime state and stored logs; export logs.
- **Files:** `ui/DebugActivity.java`, `ui/DebugFragment.java`, `ui/adapter/LogAdapter.java`,
  `util/SyncLog.java`, `data/AppRepository.java` (log CRUD + export).
- **User flow:** Toolbar overflow → **Debug** → shows service/TCP/UDP running + local IP; a
  live log list; **Clear Logs** and **Copy Logs** (exports `buildLogExportText()` to clipboard).
- **Storage:** `sync_logs` table, capped at `MAX_LOG_ROWS = 250` rows (trimmed on each insert).
- **Notes:** Every `SyncLog.d/i/w/e` call writes both to Logcat and the DB via `AppRepository.addLog`.

## Feature 8 — Bundled HeliBoard keyboard + SyncMesh bridge

- **Purpose:** Provide an IME that can push the clipboard to paired devices while typing
  (works around Android 10+ background clipboard restrictions since an active IME can read the clip).
- **Files:** `keyboard_heliboard/.../syncmesh/SyncMeshBridge.java`, `SyncMeshClipboardItem.java`,
  `keyboard_heliboard/.../LatinIME.java:882`, `ui/HomeFragment.java` (enable/choose/settings +
  auto‑send toggle), `data/AppPreferences.java` (auto‑send flag), `sync/SyncCoordinator.sendManualClipboardText`.
- **Classes:** `SyncMeshBridge` (reflection), `LatinIME`, `HomeFragment`, `AppPreferences`, `SyncCoordinator`.
- **User flow:**
  - Home **Keyboard Add‑on** card: **Enable Keyboard** (`ACTION_INPUT_METHOD_SETTINGS`),
    **Choose Keyboard** (`showInputMethodPicker`), **Keyboard Settings** (opens HeliBoard `SettingsActivity`),
    and an **Auto Send** switch (persisted via `AppPreferences.setKeyboardAutoSendEnabled`).
  - When the keyboard opens a text field (`onStartInputView`), `autoSendClipboardIfNeeded`
    reads the primary clipboard and, if enabled and not debounced (3 s / same‑text), calls
    `SyncCoordinator.sendManualClipboardText` **via reflection** to broadcast it.
- **APIs (wire):** reuses `clipboard_update` (through `sendManualClipboardText`).
- **Storage:** app prefs `syncmesh_prefs` (`auto_send_keyboard`) and bridge prefs
  `syncmesh_keyboard_bridge` (`last_auto_sent_text` / `_at` for debounce).
- **Dependencies:** reflection into `com.ankit.syncmesh.*`; the whole HeliBoard stack.
- **⚠️ Important:** only `autoSendClipboardIfNeeded` is actually invoked. The other bridge
  methods — `sendPrimaryClipboard`, `getClipboardHistory`, `getLatestRemoteClipboardText`,
  `openSyncMeshApp`, `openSyncMeshHistory`, `getSetting`/`setSetting` — are **not called from
  anywhere in the keyboard source** (verified by grep). They are dead/future‑wiring code. The
  many `keyboard_toolbar_*` strings (Send Clipboard, Paste Remote, History, Open App) suggest a
  planned toolbar UI that is not wired. See `08_TECHNICAL_DEBT.md`.
- **⚠️ Release risk:** the reflection targets are not `-keep`‑protected; app release enables
  R8 shrinking. See `07_BUG_REPORT.md` (auto‑send may fail in minified release builds).

## Feature 9 — Device identity & pairing code generation

- **Purpose:** Give each install a stable identity and a 6‑digit code.
- **Files:** `data/AppPreferences.java`, `util/NetworkUtils.shortenDeviceId`.
- **Details:** `device_id` = random `UUID` (lazily generated, persisted). `device_name` =
  `Build.MANUFACTURER + " " + Build.MODEL` (editable via `setDeviceName`, though no UI calls it).
  `pairing_code` = `SecureRandom` 6‑digit `100000–999999`, persisted, regenerated only if missing/invalid.
- **Storage:** `syncmesh_prefs`.

## Feature 10 — Window‑inset / edge‑to‑edge handling

- **Purpose:** Draw edge‑to‑edge and pad toolbar/content/bottom‑nav for system bars + IME.
- **Files:** every Activity's `applyWindowInsets()` (`MainActivity`, `*Activity`).
- **Notes:** highly duplicated inset code across five activities (candidate for extraction — see debt doc).
