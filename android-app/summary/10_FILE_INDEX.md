# 10 — File Index

Important files with **Purpose, Responsibility, Dependencies, Used by**. Paths are relative to
repo root. LOC from `wc -l`.

## App module — Java source (`app/src/main/java/com/ankit/syncmesh`)

### Entry point
| File | LOC | Purpose / Responsibility | Depends on | Used by |
|------|-----|--------------------------|-----------|---------|
| `SyncMeshApplication.java` | 19 | `Application`; initializes keyboard `App`, `SyncLog`, `AppRepository` | `helium314.keyboard.latin.App`, `AppRepository`, `SyncLog` | Declared in manifest (`android:name`) |

### data/
| File | LOC | Purpose / Responsibility | Depends on | Used by |
|------|-----|--------------------------|-----------|---------|
| `AppRepository.java` | 443 | Singleton source of truth: SQLite CRUD, LiveData streams, nearby cache, snapshot | `SyncDatabaseHelper`, `AppPreferences`, `NetworkUtils`, models | All fragments, `SyncCoordinator`, `SyncLog`, `SyncMeshBridge` |
| `SyncDatabaseHelper.java` | 302 | `SQLiteOpenHelper`; schema v8, idempotent migrations, clipboard/device/setting DAO | `ClipboardEntry`, `PairedDevice` | `AppRepository` |
| `AppPreferences.java` | 100 | `SharedPreferences` wrapper: device id/name, pairing code, keyboard flags | `SecureRandom`, `Build` | `AppRepository`, `HomeFragment` (via repo), `SyncMeshBridge` (reflection) |
| `DatabaseHelper.java` | 9 | Empty subclass of `SyncDatabaseHelper` | `SyncDatabaseHelper` | **Nobody (dead)** |

### model/
| File | LOC | Purpose | Used by |
|------|-----|---------|---------|
| `ClipboardModel.java` | 12 | Clipboard row fields (id, eventId, text, source, direction, createdAt, pinned) | DB, UI, JSON, bridge |
| `ClipboardEntry.java` | 4 | Empty subclass of `ClipboardModel` | `AppRepository`, `SyncDatabaseHelper`, adapters |
| `DiscoveredDevice.java` | 10 | Nearby device (id/name/ip/port/timestamp/lastSeen) | discovery, `AppRepository`, `NearbyDevicesAdapter` |
| `PairedDevice.java` | 14 | Paired device row | `devices` table, `PairedDevicesAdapter`, coordinator |
| `LogEntry.java` | 9 | Log row (level/tag/message/createdAt) | `sync_logs`, `LogAdapter` |
| `ServiceSnapshot.java` | 12 | Runtime status snapshot (service/tcp/udp running, ip, code, id, count) | `serviceSnapshotLiveData`, Home/Pair/Debug fragments |

### sync/
| File | LOC | Purpose / Responsibility | Depends on | Used by |
|------|-----|--------------------------|-----------|---------|
| `SyncCoordinator.java` | 509 | Orchestrator: pairing, ping, clipboard send/receive, discovery, error mapping | `AppRepository`, `ClipboardSyncManager`, `TcpServer/Client`, `UdpDiscoveryManager`, `NetworkUtils` | `SyncForegroundService`, all fragments, `SyncMeshBridge` (reflection) |
| `SyncForegroundService.java` | 78 | Foreground `dataSync` service; starts/stops runtime | `SyncCoordinator`, `NotificationHelper` | `HomeFragment` (start/stop intents), notification action |
| `ClipboardSyncManager.java` | 211 | System clipboard listener, dedup, apply remote clip | Android `ClipboardManager`, `NotificationHelper` | `SyncCoordinator` |
| `TcpServer.java` | 127 | Line‑JSON TCP server on 8989 | `SyncLog`, `org.json` | `SyncCoordinator` |
| `TcpClient.java` | 52 | One‑shot TCP client (3 s timeouts) | `SyncLog`, `org.json` | `SyncCoordinator` |
| `UdpDiscoveryManager.java` | 173 | UDP broadcast/listen on 8990; multicast lock | `AppRepository`, `NetworkUtils` | `SyncCoordinator` |

### ui/
| File | LOC | Purpose | Depends on | Used by |
|------|-----|---------|-----------|---------|
| `MainActivity.java` | 171 | Bottom‑nav host; fragment swapping; insets; debug entry | ViewBinding, `SyncCoordinator` | Launcher intent, `NotificationHelper` open intent, bridge |
| `HomeFragment.java` | 213 | Start/stop sync, keyboard card, quick actions, notif permission | `AppRepository`, `SyncCoordinator`, `LatinIME`, `SettingsActivity`, `NetworkUtils` | `MainActivity` |
| `PairFragment.java` | 294 | Manual/QR/nearby pairing; QR gen/scan | ZXing, `SyncCoordinator`, `AppRepository`, `NearbyDevicesAdapter` | `MainActivity`, `PairDeviceActivity` |
| `PairedDevicesFragment.java` | 86 | List paired devices; ping/remove | `AppRepository`, `SyncCoordinator`, `PairedDevicesAdapter` | `MainActivity`, `PairedDevicesActivity` |
| `ClipboardHistoryFragment.java` | 79 | List/copy/clear clipboard history | `AppRepository`, `ClipboardHistoryAdapter` | `MainActivity`, `ClipboardHistoryActivity` |
| `DebugFragment.java` | 109 | Service/network status + logs; clear/copy logs | `AppRepository`, `SyncCoordinator`, `LogAdapter` | `DebugActivity` |
| `ClipboardHistoryActivity.java` | 71 | Standalone host for history fragment + insets | ViewBinding | Bridge `openSyncMeshHistory`, manifest |
| `DebugActivity.java` | 71 | Standalone host for debug fragment | ViewBinding | `MainActivity.openDebugScreen` |
| `PairDeviceActivity.java` | 71 | Standalone host for pair fragment | ViewBinding | manifest (not linked from app UI) |
| `PairedDevicesActivity.java` | 71 | Standalone host for paired‑devices fragment | ViewBinding | manifest (not linked from app UI) |
| `QrScannerActivity.java` | 106 | ZXing capture activity for QR scan | zxing‑embedded `CaptureManager` | `PairFragment` (IntentIntegrator) |

### ui/adapter/
| File | LOC | Purpose | Used by |
|------|-----|---------|---------|
| `ClipboardHistoryAdapter.java` | 76 | Bind clipboard rows; copy on click | `ClipboardHistoryFragment` |
| `LogAdapter.java` | 71 | Bind log rows; colorize by level | `DebugFragment` |
| `NearbyDevicesAdapter.java` | 67 | Bind nearby devices; "Use" action | `PairFragment` |
| `PairedDevicesAdapter.java` | 78 | Bind paired devices; ping/remove; last error | `PairedDevicesFragment` |

### util/
| File | LOC | Purpose | Used by |
|------|-----|---------|---------|
| `NetworkUtils.java` | 145 | Local IPv4, broadcast addrs, IME/accessibility checks, shorten id | coordinator, discovery, repo, HomeFragment, PairFragment |
| `NotificationHelper.java` | 101 | Channels + service notification (clipboard notif **disabled**) | service, `ClipboardSyncManager` |
| `PermissionHelper.java` | 24 | Notification + camera permission checks | HomeFragment, PairFragment |
| `DisplayUtils.java` | 38 | Relative/absolute time, endpoint, safe‑string formatting | adapters, PairFragment |
| `SyncLog.java` | 60 | Logcat + persisted log facade (writes to `AppRepository`) | everywhere |

## Keyboard module — SyncMesh integration
| File | Purpose | Used by |
|------|---------|---------|
| `keyboard_heliboard/app/src/main/java/helium314/keyboard/latin/syncmesh/SyncMeshBridge.java` | Reflection bridge from IME to app singletons; `autoSendClipboardIfNeeded` is the only wired method | `LatinIME.java:882` |
| `.../syncmesh/SyncMeshClipboardItem.java` | POJO for bridge history results | `SyncMeshBridge.getClipboardHistory` (unused) |
| `.../latin/LatinIME.java` | HeliBoard IME; calls the bridge on `onStartInputView` | platform IME framework |
| `.../latin/App.kt` | HeliBoard `Application.initialize` (called by `SyncMeshApplication`) | `SyncMeshApplication` |

## Build / config files
| File | Purpose |
|------|---------|
| `settings.gradle.kts` | Includes `:app` + `:keyboard_heliboard` (dir remapped to `keyboard_heliboard/app`) |
| `build.gradle.kts` (root) | Declares plugins (all `apply false`) |
| `app/build.gradle.kts` | App module: SDK 26–36, viewBinding, release signing + minify + ABI splits, deps |
| `keyboard_heliboard/app/build.gradle.kts` | Library module: HeliBoard build, NDK, Compose, forces `applicationId=com.ankit.syncmesh` |
| `gradle/libs.versions.toml` | Version catalog (mostly keyboard deps; app uses a subset) |
| `gradle/tools.versions.toml` | Secondary catalog `tools` |
| `gradle.properties` | SDK levels + **plaintext keystore passwords** |
| `app/proguard-rules.pro` | Keeps HeliBoard JNI classes; no app‑reflection keeps |
| `app/src/main/AndroidManifest.xml` | Permissions, `MainActivity` (exported), 4 internal activities, foreground service |
| `app/src/main/res/values/strings.xml` | 230 strings (incl. many unused keyboard/permission strings) |
| `app/src/main/res/menu/bottom_nav_menu.xml` | Home/Pair/Devices/History nav items |
| `app/src/main/res/menu/main_top_app_bar_menu.xml` | Debug overflow action |
| `syncmesh-release.jks` | **Release signing keystore (committed — should not be)** |

## Layouts (`app/src/main/res/layout`) — 1:1 with screens
`activity_main`, `activity_pair_device`, `activity_paired_devices`, `activity_clipboard_history`,
`activity_debug`, `activity_qr_scanner`, `dialog_pair_qr`, `fragment_home`, `fragment_pair`,
`fragment_paired_devices`, `fragment_clipboard_history`, `fragment_debug`, and item layouts
(`item_clipboard_history`, `item_log`, `item_nearby_device`, `item_paired_device`). Bound via
ViewBinding‑generated classes referenced throughout the UI.
