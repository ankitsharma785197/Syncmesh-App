# 02 — Architecture

## 1. Architecture pattern

The app module uses a **pragmatic layered / repository architecture** with:

- **Singletons** for all shared state and services (`AppRepository`, `SyncCoordinator`,
  `ClipboardSyncManager`), each using the double‑checked‑locking `getInstance(Context)` idiom.
- **LiveData** (`androidx.lifecycle`) as the one‑way reactive channel from the data layer to
  the UI. There is **no ViewModel** — Fragments observe repository LiveData directly with
  `getViewLifecycleOwner()`.
- **Callback interfaces** for imperative results (`SyncCoordinator.ActionCallback`,
  adapter `Listener` interfaces, `TcpServer.MessageHandler`, `UdpDiscoveryManager.AnnouncementHandler`,
  `ClipboardSyncManager.LocalClipboardListener`).
- **Plain POJO models** with public mutable fields (no encapsulation, no immutability).
- **Manual threading** via `ExecutorService` and `Handler(Looper.getMainLooper())`.

It is **not** MVVM, MVI, or Clean Architecture. It is closest to **MVC with a Repository**,
where Fragments act as controllers and the Repository is the model.

## 2. Layers & responsibilities

| Layer | Classes | Responsibility |
|-------|---------|----------------|
| **Presentation** | `MainActivity`, `*Activity`, `*Fragment`, `adapter/*` | Inflate views (ViewBinding), observe LiveData, forward user intents to `SyncCoordinator`/`AppRepository`, apply window insets. |
| **Orchestration** | `SyncCoordinator` | Coordinates pairing, ping, clipboard send/receive, discovery handling; builds JSON payloads; maps exceptions to user‑facing strings; posts callbacks to main thread. |
| **Domain services** | `ClipboardSyncManager`, `TcpServer`, `TcpClient`, `UdpDiscoveryManager`, `SyncForegroundService`, `NotificationHelper` | Clipboard listening/dedup, socket I/O, UDP discovery, foreground lifecycle, notifications. |
| **Data** | `AppRepository`, `SyncDatabaseHelper`, `AppPreferences` | SQLite persistence, SharedPreferences, in‑memory nearby cache, LiveData publication. |
| **Models** | `model/*` | Data carriers between all layers and the JSON wire format. |
| **Utilities** | `NetworkUtils`, `DisplayUtils`, `PermissionHelper`, `SyncLog` | Cross‑cutting helpers. |
| **Keyboard bridge** | `SyncMeshBridge` (keyboard module) | Reflection‑based calls from the IME into the app's singletons. |

## 3. Dependency flow

```
Fragments/Activities ─────► SyncCoordinator ─────► AppRepository ─────► SyncDatabaseHelper / AppPreferences
        │                        │                     ▲
        └───────► AppRepository ─┘                     │  (LiveData observed back up)
                                 └──► ClipboardSyncManager
                                 └──► TcpServer / TcpClient / UdpDiscoveryManager
SyncForegroundService ──► SyncCoordinator
LatinIME (keyboard) ──reflection──► SyncMeshBridge ──► SyncCoordinator / AppRepository
```

- The UI depends on both `SyncCoordinator` and `AppRepository`.
- `SyncCoordinator` depends on `AppRepository` and `ClipboardSyncManager` and constructs the
  network services.
- `AppRepository` depends only on `SyncDatabaseHelper`, `AppPreferences`, and `NetworkUtils`.
- The keyboard module cannot compile‑time depend on the app (the app depends on the keyboard,
  not vice versa), so the bridge uses **Java reflection** to reach `com.ankit.syncmesh.*`
  singletons at runtime.

## 4. Data flow

**Source of truth:** `AppRepository` holds five `MutableLiveData` streams:
`pairedDevicesLiveData`, `clipboardHistoryLiveData`, `logsLiveData`, `nearbyDevicesLiveData`,
`serviceSnapshotLiveData`.

- Writes (insert device, add clipboard entry, add log, update nearby) mutate SQLite or the
  in‑memory `ConcurrentHashMap<String,DiscoveredDevice> nearbyDevices`, then call a
  `postXxx()` helper that re‑queries and `postValue()`s the corresponding LiveData.
- Paired devices, clipboard history, and logs are **re‑read from SQLite on every publish**
  (no in‑memory caching, no diffing).
- Nearby devices live only in memory (never persisted) and are cleared on `stopRuntime()`.
- `ServiceSnapshot` is a computed value (`buildDefaultSnapshot()` + runtime flags set by
  `SyncCoordinator.refreshSnapshot()`).

## 5. Request flow (outbound — this device initiates)

Example: **pair request** (`SyncCoordinator.sendPairRequest`):
1. UI (`PairFragment.submitPairRequest`) validates fields and calls `coordinator.sendPairRequest(ip, port, code, callback)`.
2. Coordinator runs on `executorService` (cached thread pool), builds a `pair_request` JSON
   (`requestId`, `fromDeviceId`, `fromDeviceName`, local IP, port `8989`, `pairingCode`, `timestamp`).
3. `new TcpClient().sendMessage(ip, port, payload, expectResponse=true)` opens a `Socket`
   (3 s connect + read timeout), writes one `\n`‑terminated JSON line, reads one response line.
4. On `accepted`, a `PairedDevice` is upserted and the snapshot refreshed; callback posted to main thread.
5. Errors are mapped by `toUserFacingNetworkError(...)` to localized strings.

Same one‑shot request pattern is used for **ping** (`ping`→`pong`) and **clipboard send**
(`clipboard_update`, fire‑and‑forget, `expectResponse=false`).

## 6. Response flow (inbound — this device is the server)

1. `TcpServer.runAcceptLoop()` accepts a socket, dispatches to `clientExecutor` (cached pool).
2. `handleClient()` sets a 3 s read timeout, reads **one line**, parses JSON, and calls
   `messageHandler.onMessage(remoteAddress, json)` → `SyncCoordinator.handleIncomingMessage`.
3. Dispatch by `type`:
   - `pair_request` → validate `pairingCode` against local code; upsert device if match; return `pair_response` JSON.
   - `clipboard_update` → verify sender is a paired device, dedup by `eventId`, persist a
     `remote` history row, and apply to the system clipboard; returns `null` (no reply).
   - `ping` → return `pong` JSON.
4. If the handler returns a non‑null string, it is written back on the same socket and the
   socket is closed. One request = one connection.

**UDP discovery inbound:** `UdpDiscoveryManager.listenLoop()` receives datagrams on port
8990, parses `discovery_announce` JSON, and calls `SyncCoordinator.handleDiscoveryAnnouncement`
which updates the in‑memory nearby map (ignoring self).

## 7. Navigation flow

- **In‑app navigation is manual `FragmentTransaction.replace`**, driven by the
  `BottomNavigationView` in `MainActivity` (`onNavigationItemSelected`). There is **no**
  Jetpack Navigation component / nav graph in the app module.
- Bottom‑nav items: `nav_home` → `HomeFragment`, `nav_pair` → `PairFragment`,
  `nav_devices` → `PairedDevicesFragment`, `nav_history` → `ClipboardHistoryFragment`
  (`bottom_nav_menu.xml`). Default/fallback is Home.
- The **Debug** screen is a *separate activity* (`DebugActivity`), reached from the toolbar
  overflow (`main_top_app_bar_menu.xml` → `action_debug`) via `MainActivity.openDebugScreen()`.
- Standalone activities (`PairDeviceActivity`, `PairedDevicesActivity`, `ClipboardHistoryActivity`,
  `DebugActivity`) each host the *same* fragment as the tabs — an alternate deep‑link/entry
  path (e.g. the keyboard bridge opens `MainActivity` / `ClipboardHistoryActivity`).
- `MainActivity.createLaunchIntent(context, destinationId)` + `EXTRA_START_DESTINATION`
  allow launching directly into a tab; `onNewIntent` re‑navigates (`launchMode` is default,
  so this only matters if the caller adds `FLAG_ACTIVITY_SINGLE_TOP`/`CLEAR_TOP`).

## 8. Threading model

- **Main thread:** all UI, LiveData observation, callbacks (posted via `mainHandler`),
  `applyRemoteClipboard` (posts to main to call `setPrimaryClip`).
- **`SyncCoordinator.executorService`** — `Executors.newCachedThreadPool()`: outbound
  pair/ping/clipboard sends, per‑device fan‑out.
- **`TcpServer`** — one single‑thread accept executor + a cached client executor.
- **`UdpDiscoveryManager`** — one single‑thread broadcast executor + one single‑thread listen executor.
- **`AppRepository`** — DB access is serialized by a `databaseLock` object monitor and by
  `synchronized` methods in `SyncDatabaseHelper`. LiveData is updated with `postValue`
  (thread‑safe) except in the constructor which uses `setValue` on the constructing thread.
- ⚠️ `SyncCoordinator.refreshSnapshot()` and `buildDefaultSnapshot()` run **on the caller's
  thread**; they are frequently called from `onResume`/`onViewCreated` (main thread) and do
  a **SQLite query (`getPairedDevices`) + network‑interface enumeration (`getLocalIpv4Address`)
  on the main thread** — see `07_BUG_REPORT.md` (ANR/StrictMode risk).

## 9. Class relationships (key associations)

- `SyncMeshApplication` → creates `AppRepository`, initializes keyboard `App`, `SyncLog`.
- `SyncForegroundService` → holds `SyncCoordinator`; `onStartCommand` starts runtime, `onDestroy` stops it.
- `SyncCoordinator` → owns `TcpServer`, `UdpDiscoveryManager`; references `ClipboardSyncManager`, `AppRepository`.
- `ClipboardSyncManager` → wraps system `ClipboardManager`; notifies `SyncCoordinator` via listener; calls `NotificationHelper`.
- `AppRepository` → owns `SyncDatabaseHelper` + `AppPreferences`; publishes LiveData consumed by all fragments.
- `SyncLog` → statically calls back into `AppRepository.addLog` (bidirectional coupling: repo → log via nothing, log → repo).
- Fragments/Activities → ViewBinding classes generated per layout; adapters bound to LiveData lists.

## 10. Wire protocol (JSON over TCP/UDP)

All messages are single‑line UTF‑8 JSON terminated by `\n`. `type` discriminates:

| type | transport | direction | key fields |
|------|-----------|-----------|-----------|
| `pair_request` | TCP 8989 | client→server | requestId, fromDeviceId, fromDeviceName, ipAddress, port, pairingCode, timestamp |
| `pair_response` | TCP 8989 | server→client | requestId, fromDeviceId, fromDeviceName, ipAddress, port, accepted, message, timestamp |
| `clipboard_update` | TCP 8989 | one‑way | eventId, fromDeviceId, fromDeviceName, text, timestamp |
| `ping` / `pong` | TCP 8989 | request/response | requestId, fromDeviceId, fromDeviceName, (ip/port on pong), timestamp |
| `discovery_announce` | UDP 8990 | broadcast | deviceId, deviceName, ipAddress, port, timestamp |
| `syncmesh_pair_qr` | QR image | out‑of‑band | deviceId, deviceName, ipAddress, port, pairingCode |

There is **no versioning, authentication token, signature, or encryption** on any message.
