# 01 — Project Summary

> All statements below are derived directly from the source in this repository. Where a
> behaviour could not be verified from code, it is called out explicitly (see
> `UNKNOWN_AREAS.md`).

## 1. Project overview

**SyncMesh** is an Android application that synchronizes **clipboard text between
devices on the same local network (Wi‑Fi or hotspot)** — with **no cloud relay**. All
transport happens directly device‑to‑device over LAN sockets.

The project is a **Gradle multi‑module Android build** containing two modules:

| Module | Gradle path | Plugin | Namespace | Role |
|--------|-------------|--------|-----------|------|
| App | `:app` | `com.android.application` | `com.ankit.syncmesh` | The SyncMesh clipboard‑sync application (Java) |
| Keyboard | `:keyboard_heliboard` (dir `keyboard_heliboard/app`) | `com.android.library` | `helium314.keyboard.latin` | A vendored fork of **HeliBoard 3.9** (Kotlin/Java IME) bundled as an add‑on keyboard |

The keyboard module is compiled into the same APK as an Android **library**, so its
components (the `LatinIME` input method service, `SettingsActivity`, spell checker, etc.)
are merged into the final application via manifest merging. `applicationId` is forced to
`com.ankit.syncmesh` for both (`keyboard_heliboard/app/build.gradle.kts:16`).

- App source language: **Java** (37 source files, ~4,086 LOC — `app/src/main/java`).
- Keyboard source language: **Kotlin + Java** (174 Kotlin files plus Java native/JNI glue).
- Build system: **Gradle 8.13 / AGP 8.13.0**, Kotlin 2.3.20, type‑safe project accessors, version catalogs.
- **No unit or instrumentation tests exist** in the app module (`app/src/test` and `app/src/androidTest` contain no source; the old example tests were deleted per git status).

## 2. Main purpose

Copy text on one paired phone → it appears on the clipboard of every other paired phone
on the same network, automatically (while the sync service runs) or manually (via the
bundled keyboard). A local history of every clipboard event is stored, and devices are
paired using a 6‑digit code exchanged manually, by QR code, or by picking a nearby
auto‑discovered device.

## 3. Features (high level — full detail in `03_FEATURES.md`)

1. **Foreground clipboard sync service** — `SyncForegroundService` keeps TCP server, UDP
   discovery, and clipboard monitoring alive with a persistent notification.
2. **Automatic clipboard propagation** — a `ClipboardManager.OnPrimaryClipChangedListener`
   detects local copies and pushes them to all paired devices over TCP.
3. **Device pairing** — three paths: manual IP+port+code, QR code (ZXing), and nearby
   discovery (UDP broadcast). Uses a per‑device 6‑digit pairing code.
4. **Nearby discovery** — UDP broadcast every 3 s on port 8990; nearby devices expire after 15 s.
5. **Ping / reachability test** — send a `ping` message and expect a `pong`.
6. **Clipboard history** — persisted SQLite history of local + remote clipboard events, copy back on tap.
7. **Debug console** — live service state (TCP/UDP/service running, local IP) plus a
   persisted, exportable log stream.
8. **Bundled HeliBoard keyboard** — optional IME with a bridge that auto‑sends the
   clipboard to paired devices when the keyboard opens (`SyncMeshBridge.autoSendClipboardIfNeeded`).

## 4. Technology stack

**App module dependencies** (`app/build.gradle.kts`):
- `androidx.core`, `androidx.appcompat`, `androidx.activity:activity-ktx`, `androidx.fragment`
- `androidx.lifecycle:lifecycle-runtime` + `lifecycle-livedata` (LiveData is the reactive layer)
- `androidx.recyclerview`
- `com.google.android.material` (Material 3 components, dialogs, bottom nav)
- `com.google.zxing:core` **3.5.3** and `com.journeyapps:zxing-android-embedded` **4.3.0** (QR generate + scan)
- `projects.keyboardHeliboard` (the keyboard library module)

**Storage / concurrency / networking:** all built on the Android platform SDK — raw
`SQLiteOpenHelper`, `SharedPreferences`, `java.net` sockets (`ServerSocket`, `Socket`,
`DatagramSocket`), `org.json`, `java.util.concurrent` executors. **No** Retrofit/OkHttp,
Room, Hilt/Dagger, RxJava, or Coroutines are used *in the app module* (the keyboard module
uses Kotlin coroutines, Compose, kotlinx‑serialization, etc.).

**View layer:** classic Android Views + **ViewBinding** (`buildFeatures { viewBinding = true }`).
No Jetpack Compose is used in the app module (Compose belongs to the keyboard module only).

## 5. Architecture (summary — see `02_ARCHITECTURE.md`)

A pragmatic, hand‑rolled layered architecture centred on **process‑wide singletons** and
**LiveData**:

```
UI (Activities / Fragments / RecyclerView Adapters)
        │  observe LiveData / call methods
        ▼
SyncCoordinator (singleton orchestrator)  ── ClipboardSyncManager (singleton)
        │                                        │
        ├── TcpServer / TcpClient (JSON-over-TCP)│ system ClipboardManager listener
        ├── UdpDiscoveryManager (UDP broadcast)  │
        ▼                                        ▼
AppRepository (singleton) ── AppPreferences (SharedPreferences)
        │                 └─ SyncDatabaseHelper (SQLite)
        ▼
LiveData streams → UI
```

- **`AppRepository`** is the single source of truth: it owns the SQLite helper,
  preferences, the in‑memory nearby‑device map, and all `MutableLiveData` streams.
- **`SyncCoordinator`** orchestrates networking and clipboard logic; it is the public API
  surface the UI (and the keyboard, via reflection) call into.
- **`ClipboardSyncManager`** owns the system clipboard listener and de‑duplication logic.
- There is **no ViewModel layer** — Fragments observe repository LiveData directly.

## 6. Package structure (`app/src/main/java/com/ankit/syncmesh`)

```
com.ankit.syncmesh
├── SyncMeshApplication.java        Application entry; initializes keyboard App + repo + logger
├── data/
│   ├── AppPreferences.java         SharedPreferences: device id, name, pairing code, keyboard flags
│   ├── AppRepository.java          Singleton source-of-truth; LiveData; SQLite CRUD
│   ├── SyncDatabaseHelper.java     SQLiteOpenHelper (schema v8: devices, clipboard_history, sync_logs, app_settings)
│   └── DatabaseHelper.java         Empty subclass of SyncDatabaseHelper (unused)
├── model/                          Plain public-field POJOs
│   ├── ClipboardModel / ClipboardEntry, DiscoveredDevice, LogEntry, PairedDevice, ServiceSnapshot
├── sync/
│   ├── SyncCoordinator.java        Orchestrator singleton (pairing, ping, send/receive, discovery)
│   ├── SyncForegroundService.java  Foreground dataSync service
│   ├── ClipboardSyncManager.java   Clipboard listener + dedup + apply remote
│   ├── TcpServer.java              Line-delimited JSON TCP server on port 8989
│   ├── TcpClient.java              One-shot TCP client
│   └── UdpDiscoveryManager.java    UDP broadcast/listen on port 8990
├── ui/
│   ├── MainActivity.java           Bottom-nav host (Home/Pair/Devices/History) + Debug menu
│   ├── HomeFragment / PairFragment / PairedDevicesFragment / ClipboardHistoryFragment / DebugFragment
│   ├── *Activity.java              Standalone hosts for the same fragments (Pair/PairedDevices/History/Debug) + QrScannerActivity
│   └── adapter/                    RecyclerView adapters (Clipboard, Log, NearbyDevices, PairedDevices)
└── util/
    ├── NetworkUtils.java           Local IPv4, broadcast addrs, IME/accessibility checks
    ├── NotificationHelper.java     Channels + service notification (clipboard notif disabled)
    ├── PermissionHelper.java       Notification + camera permission checks
    ├── DisplayUtils.java           Time/endpoint formatting helpers
    └── SyncLog.java                Logcat + persisted-log facade
```

Keyboard integration bridge lives in the **keyboard** module:
`keyboard_heliboard/app/src/main/java/helium314/keyboard/latin/syncmesh/SyncMeshBridge.java`
(+ `SyncMeshClipboardItem.java`).

## 7. Folder structure (repo root)

```
Syncmesh2/
├── app/                     SyncMesh application module
├── keyboard_heliboard/      Vendored HeliBoard fork (its own git repo + gradle wrapper)
│   └── app/                 Library module (projectDir remapped in settings.gradle.kts)
├── gradle/
│   ├── libs.versions.toml   Main version catalog (mostly keyboard deps)
│   └── tools.versions.toml  Secondary "tools" catalog
├── build.gradle.kts         Root plugins (all apply false)
├── settings.gradle.kts      Includes :app and :keyboard_heliboard
├── gradle.properties        Project SDK levels + KEYSTORE PASSWORDS IN PLAINTEXT
├── local.properties         SDK path (should not be committed; is present)
├── syncmesh-release.jks      RELEASE SIGNING KEYSTORE committed to the repo
├── lib/                     empty
└── keyboard_heliboard/...
```

> ⚠️ `syncmesh-release.jks` (release keystore) and its passwords (`gradle.properties`,
> `app/build.gradle.kts`) are committed. See `06_SECURITY_ANALYSIS.md` (Critical).

## 8. Major modules / important classes / entry points

- **Launcher entry point:** `com.ankit.syncmesh.ui.MainActivity` (only exported activity,
  `AndroidManifest.xml:28`).
- **Application entry point:** `SyncMeshApplication.onCreate()` → `App.Companion.initialize(this)`
  (keyboard), `SyncLog.init(this)`, `AppRepository.getInstance(this)`.
- **Background entry point:** `SyncForegroundService` (`foregroundServiceType="dataSync"`),
  started from `HomeFragment` via `ContextCompat.startForegroundService(...)`.
- **IME entry point:** `helium314.keyboard.latin.LatinIME` (merged from keyboard module),
  which calls `SyncMeshBridge.autoSendClipboardIfNeeded(this)` in `onStartInputView`
  (`LatinIME.java:882`).
- **Keyboard settings entry point:** `helium314.keyboard.settings.SettingsActivity`, opened
  from `HomeFragment.openKeyboardSettings()`.

## 9. High‑level workflow

1. User opens the app → `MainActivity` shows the **Home** tab.
2. User taps **Start Sync** → notification permission is requested (API 33+) → foreground
   service starts → `SyncCoordinator.startRuntime()` boots the TCP server (8989), UDP
   discovery (8990), and clipboard monitoring.
3. On another phone the same is done. Both broadcast discovery; each appears in the
   other's **Pair → Nearby devices** list.
4. User pairs (manual / QR / nearby) by exchanging the 6‑digit code. Success stores a
   `PairedDevice` row on both sides.
5. Copying text on one phone triggers the clipboard listener → `SyncCoordinator` sends a
   `clipboard_update` JSON to each paired device over TCP → the receiver writes it to its
   system clipboard, saves a history row, and (notification currently disabled) would notify.
6. History, device management, ping, and a debug console are available from the bottom nav.

## 10. App lifecycle & startup sequence

1. **Process start** → `SyncMeshApplication.onCreate()`:
   - `App.Companion.initialize(this)` initializes HeliBoard subsystems (Settings, subtypes,
     emoji load on a background coroutine, etc. — `App.kt`).
   - `SyncLog.init(this)` caches the app context for persisted logging.
   - `AppRepository.getInstance(this)` constructs the repository, which **immediately reads
     the database** (`refreshAll()` → paired devices, history, logs) on the calling thread.
2. **`MainActivity.onCreate()`** inflates `ActivityMainBinding`, sets the toolbar, applies
   window insets, wires bottom navigation, and selects the start destination (default `nav_home`,
   overridable via `EXTRA_START_DESTINATION`).
3. **Fragment shown** (e.g. `HomeFragment`) obtains the repo + coordinator singletons,
   observes `serviceSnapshotLiveData`, and calls `coordinator.refreshSnapshot()`.
4. The sync **runtime is NOT auto‑started** — it only runs when the user taps Start Sync (or
   the service is restarted by the OS because it is `START_STICKY`). Note the service is
   `stopWithTask="false"`, so it survives task swipe.

See `02_ARCHITECTURE.md` for detailed data/request/response flows and
`03_FEATURES.md` for per‑feature file maps.
