# Assumptions Verified

This document separates **verified facts** (read directly from source), **possible issues**
(inferred, needing runtime confirmation), and **things that could not be verified**.

---

## A. Verified facts (read directly from code)

### Identity & build
- App id `com.ankit.syncmesh`, `minSdk 26`, `targetSdk/compileSdk 36`, `versionName 1.0`
  (`app/build.gradle.kts`). ✔
- Two Gradle modules: `:app` (application, Java) and `:keyboard_heliboard` (library, HeliBoard
  fork, Kotlin/Java), the latter's projectDir remapped to `keyboard_heliboard/app`
  (`settings.gradle.kts:32-33`). ✔
- Release build: signed with `syncmesh-release.jks` (committed), `isMinifyEnabled = true`,
  `isShrinkResources = true`, ABI splits enabled unless bundling (`app/build.gradle.kts`). ✔
- Keystore passwords committed in plaintext (`app/build.gradle.kts:11-13`, `gradle.properties`). ✔
- App uses **ViewBinding**, no Compose; keyboard module uses Compose. ✔

### Entry points & lifecycle
- `SyncMeshApplication.onCreate` calls `App.Companion.initialize(this)`, `SyncLog.init(this)`,
  `AppRepository.getInstance(this)`. ✔
- Only `MainActivity` is exported in the app manifest; foreground service `exported="false"`. ✔
- `SyncForegroundService` is `foregroundServiceType="dataSync"`, `START_STICKY`,
  `stopWithTask="false"`, has `onTimeout` → `stopSelf`. ✔
- Sync runtime is **not** auto‑started; it starts on user action (Home → Start Sync) or OS restart. ✔

### Networking
- TCP server binds `0.0.0.0:8989`; UDP discovery binds/broadcasts `8990`; both parse line‑delimited
  JSON. 3 s timeouts. ✔
- Message types: `pair_request`/`pair_response`, `clipboard_update`, `ping`/`pong`,
  `discovery_announce`, plus QR `syncmesh_pair_qr`. ✔
- No TLS/encryption; `usesCleartextTraffic="true"`. ✔
- Pairing acceptance = `localPairingCode.equals(incomingCode)`; clipboard authorization =
  `isPairedDevice(fromDeviceId)` with unverified id. ✔

### Storage
- SQLite `syncmesh.db` v8, tables `devices`, `clipboard_history`, `sync_logs`, `app_settings`;
  additive‑only migrations via `ensureColumn`. ✔
- `sync_logs` capped at 250; `clipboard_history` uncapped. ✔
- SharedPreferences `syncmesh_prefs` (device id/name, pairing code, keyboard flags) and
  `syncmesh_keyboard_bridge` (auto‑send debounce). ✔
- Pairing code = 6‑digit `SecureRandom`; device id = random `UUID`. ✔
- `allowBackup="true"` with empty backup/data‑extraction rule templates (no exclusions). ✔

### Keyboard integration
- `SyncMeshBridge` uses reflection into `com.ankit.syncmesh.*`; only `autoSendClipboardIfNeeded`
  is invoked, from `LatinIME.onStartInputView` (`LatinIME.java:882`). ✔
- Auto‑send reads the primary clipboard and calls `SyncCoordinator.sendManualClipboardText` with a
  3 s / same‑text debounce; gated by the app's `isKeyboardAutoSendEnabled` flag. ✔
- `applicationId` forced to `com.ankit.syncmesh` in the keyboard module. ✔

### Reactive/threading
- `AppRepository` exposes 5 LiveData streams; fragments observe directly (no ViewModel). ✔
- `SyncCoordinator`/`TcpServer`/`UdpDiscoveryManager` use their own `ExecutorService`s; callbacks
  posted to the main thread. ✔

### Dead / unwired code (grep‑verified: no callers)
- Accessibility bridge (`startAccessibilityBridge`/`stopAccessibilityBridge`/
  `pollAccessibilityClipboard`, `startAccessibilityMonitoring`, `isAccessibilityServiceEnabled`). ✔
- `SyncMeshBridge` methods other than `autoSendClipboardIfNeeded`. ✔
- `DatabaseHelper`, `AppRepository.readClipboardEntry`, `app_settings` DAO, several `AppPreferences`
  accessors, `showClipboardNotification` (commented out). ✔

## B. Possible issues (inferred; need runtime confirmation)
- Keyboard auto‑send may fail in **release** builds due to R8 shrinking of reflected members (B‑2) —
  **confirm with a release build.**
- Main‑thread SQLite + interface enumeration on resume/startup may ANR on slow devices / large
  history (B‑1) — confirm via profiling/StrictMode.
- Clipboard echo → duplicate history under slow clip‑changed callbacks (B‑4/B‑5) — confirm on‑device.
- Non‑volatile socket/field visibility races on start/stop churn (B‑6) — confirm under stress.
- Fragment callback NPE/ISE if navigating away during an in‑flight pair/ping (B‑3) — reproducible manually.
- Discovery may be dropped by client‑isolating APs (B‑10) — network‑dependent.

## C. Could not be verified (see `UNKNOWN_AREAS.md`)
- Exact merged manifest / final permission set (needs a build).
- Full internal behaviour of the vendored HeliBoard keyboard (174 Kotlin files + native code not
  exhaustively read).
- Product intent behind disabled/unwired features.
- `tools.versions.toml` contents (not opened; assumed build‑tool versions).
- Real‑world discovery reliability and clipboard callback timing across OEMs/versions.

## D. Explicitly corrected assumptions during analysis
- **Assumed at first** the reflection bridge would break in release purely from obfuscation —
  **corrected:** the keyboard's consumer `-dontobfuscate` disables renaming app‑wide, so the real
  risk is **shrinking (member removal)**, not renaming.
- **Assumed** there might be a ViewModel/Navigation layer — **verified false**: fragments use
  singletons directly and navigation is manual `FragmentTransaction`.
- **Assumed** clipboard notifications inform the user of remote copies — **verified false**: the
  notification code is commented out and returns early.
- **Assumed** `DatabaseHelper` was the active DB helper — **verified false**: it's an empty unused
  subclass; `SyncDatabaseHelper` is the real one.
