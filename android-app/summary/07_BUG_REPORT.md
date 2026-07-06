# 07 — Bug Report

Each bug: **Description, Location, Why it happens, Severity, Impact, Suggested fix.** Severity
reflects likelihood × user impact. All are derived from the actual source; where runtime
confirmation (e.g. a release build) is needed, it is stated.

Severity legend: 🔴 High · 🟠 Medium · 🟡 Low.

---

## 🔴 B‑1 — Main‑thread SQLite + network‑interface enumeration (ANR / jank / StrictMode)
- **Location:** `SyncCoordinator.refreshSnapshot()` / `AppRepository.buildDefaultSnapshot()`
  (calls `getPairedDevices()` = SQLite query **and** `NetworkUtils.getLocalIpv4Address()` =
  `NetworkInterface.getNetworkInterfaces()` enumeration). Callers: `MainActivity.onResume`,
  `HomeFragment.onViewCreated/onResume`, `PairFragment`, `PairedDevicesFragment.onResume`,
  `DebugFragment` — all main thread. Also `AppRepository` constructor calls `refreshAll()`
  (three DB queries) from `SyncMeshApplication.onCreate` (main thread, app cold start).
- **Why:** these synchronous I/O operations run on whatever thread calls them; the UI always calls them.
- **Impact:** UI jank and ANR risk on slow devices / large history; blocks app startup; would trip
  `StrictMode` disk/network‑on‑main‑thread violations. Frequency is high (every resume).
- **Fix:** move snapshot building and repository refresh to a background executor and post results
  to LiveData; cache the local IP.

## 🔴 B‑2 — Keyboard auto‑send may silently fail in minified release builds
- **Location:** `SyncMeshBridge.sendClipboardText` (reflection to
  `SyncCoordinator#sendManualClipboardText`); app release `isMinifyEnabled=true`,
  `isShrinkResources=true` (`app/build.gradle.kts`); no `-keep` for `com.ankit.syncmesh.*`.
- **Why:** `sendManualClipboardText` is referenced **only** via reflection; R8 tree‑shaking can
  remove it as unused. (Names are not renamed because the keyboard's consumer proguard sets
  `-dontobfuscate`, but shrinking still applies.)
- **Impact:** auto‑send from the keyboard throws `NoSuchMethodException`, caught → "SyncMesh send
  failed" toast. Core keyboard integration broken in release. **Confirm with a real release build.**
- **Fix:** add explicit `-keep` rules for the reflected classes/methods (and model fields used by
  the other bridge methods if they are ever wired).

## 🔴 B‑3 — Fragment crashes if an async network callback returns after view teardown
- **Location:** `PairFragment.submitPairRequest` callback (`binding.buttonPairDevice.setEnabled(true)`,
  `requireContext()`, `requireActivity()` with no null/attach check); likewise
  `PairedDevicesFragment.onPingDevice`/`onRemoveDevice` callbacks use `requireContext()`.
- **Why:** `SyncCoordinator` posts callbacks to the main thread up to ~3 s later. If the user
  navigates away (fragment view destroyed → `binding = null`, fragment detached) before the
  callback fires, `binding.*` → NPE and `requireContext()/requireActivity()` → `IllegalStateException`.
- **Impact:** crash on a plausible interaction (start pairing, switch tabs / rotate).
- **Fix:** guard callbacks with `if (binding == null || !isAdded()) return;` or use lifecycle‑aware delivery.

## 🟠 B‑4 — Clipboard echo → duplicate history entries / spurious remote apply
- **Location:** `ClipboardSyncManager.applyRemoteClipboard` + `handleClipboardChanged`
  dedup (`DUPLICATE_WINDOW_MS = 2000`); `SyncCoordinator.handleRemoteClipboard`.
- **Why:** after a remote clip is applied via `setPrimaryClip`, the system fires the primary‑clip
  listener; it is suppressed only if the callback arrives **within 2 s** and the flags are visible.
  On slow devices (or under the data race in B‑5) the callback can arrive later, be treated as a
  fresh local copy, and be re‑broadcast with a **new `eventId`** — which the peer does not
  recognize as a duplicate (`eventId` dedup misses), so it re‑applies and stores a second history
  row (and would re‑notify if notifications were enabled).
- **Impact:** duplicate clipboard history rows and a bounded ping‑pong (usually one extra hop, but
  timing‑dependent). Not an infinite loop under normal timing, but user‑visible duplicates.
- **Fix:** dedup on content hash + origin across a longer window, and/or propagate the original
  `eventId` so echoes are recognized; avoid a fixed 2 s wall‑clock window.

## 🟠 B‑5 — Data race on clipboard dedup flags (visibility)
- **Location:** `ClipboardSyncManager`: `applyingRemoteClipboard`, `lastAppliedRemoteText`,
  `lastAppliedRemoteAt`, `lastObservedText/At` are written inside a main‑thread `Runnable` in
  `applyRemoteClipboard` **without synchronization**, but read inside `synchronized(this)` in
  `handleClipboardChanged`.
- **Why:** the write path is not under the same lock (nor volatile), so updates may not be visible
  to the reader thread that receives the clip‑changed callback.
- **Impact:** intermittent dedup failure feeding B‑4; hard‑to‑reproduce duplicate sends.
- **Fix:** perform all mutations under `synchronized(this)` or make the fields volatile/consistently locked.

## 🟠 B‑6 — Non‑volatile socket/server fields read across threads (NPE / stuck accept)
- **Location:** `TcpServer.serverSocket` (assigned on accept thread, read in `stop()`/`isRunning()`
  from other threads); `UdpDiscoveryManager.broadcastSocket`/`receiveSocket`;
  `SyncCoordinator.tcpServer`/`udpDiscoveryManager` (read in `refreshSnapshot`, nulled in `stopRuntime`).
- **Why:** these mutable fields lack `volatile`/synchronization. `stop()` may read a stale `null`
  `serverSocket` and skip `close()`, leaving `accept()` blocked (thread leak). `refreshSnapshot`'s
  `tcpServer != null && tcpServer.isRunning()` is a check‑then‑use that can NPE if `stopRuntime`
  nulls the field between the two reads.
- **Impact:** rare thread leaks / crashes on start‑stop churn.
- **Fix:** make fields `volatile` (or synchronize), and snapshot into a local before check‑then‑use.

## 🟠 B‑7 — LiveData/DB thrash during clipboard fan‑out
- **Location:** `SyncCoordinator.handleLocalClipboardChanged` / `sendManualClipboardText` call
  `repository.updateDeviceLastSeen`/`updateDeviceLastError` per device; each of those re‑queries
  the **entire** `devices` table (`postPairedDevices`) and posts LiveData.
- **Why:** every per‑device send success/failure triggers a full table read + LiveData emit.
- **Impact:** with N paired devices, one copy causes ~N DB reads + N LiveData emits → adapter
  rebinds (`notifyDataSetChanged`); wasteful, scales poorly.
- **Fix:** batch updates; update only the changed row in memory; debounce LiveData emits.

## 🟠 B‑8 — Unbounded clipboard history growth
- **Location:** `clipboard_history` table — no row cap (unlike `sync_logs` at 250).
- **Why:** every local/remote clip inserts a row; only manual **Clear History** removes them.
- **Impact:** DB grows without bound; long‑running installs accumulate all clipboard text (also a
  privacy concern — see security C‑2). Read queries slow over time; each publish reads the whole table.
- **Fix:** cap rows / add retention policy.

## 🟡 B‑9 — Remote clipboard arrives with no user feedback
- **Location:** `NotificationHelper.showClipboardNotification` is fully commented out and returns
  immediately; `CLIPBOARD_CHANNEL_ID` is created but never used.
- **Why:** notification code disabled (intentional or WIP).
- **Impact:** remote clipboard is applied silently — user may not realize their clipboard changed;
  strings (`notification_clipboard_*`, `toast_remote_clipboard`) and the channel are dead.
- **Fix:** decide on feedback UX; remove the unused channel/strings or re‑enable notifications.

## 🟡 B‑10 — Broadcast address `getBroadcast()` unreliable on some interfaces
- **Location:** `NetworkUtils.getBroadcastAddresses` relies on `InterfaceAddress.getBroadcast()`,
  which is often `null` on cellular/VPN/some Wi‑Fi interfaces; a `255.255.255.255` fallback is added.
- **Why:** platform limitation.
- **Impact:** directed subnet broadcasts may be missed; discovery leans on the global broadcast,
  which some APs drop. Discovery can silently fail on certain networks.
- **Fix:** also derive broadcast from address+prefix length; document network requirements.

## 🟡 B‑11 — QR encode failure surfaces a raw/possibly‑null exception message
- **Location:** `PairFragment.showPairQr` catch block: `Toast.makeText(..., exception.getMessage(), ...)`.
- **Why:** `getMessage()` may be null → toast shows "null"; not localized.
- **Impact:** minor UX.
- **Fix:** show a localized error string.

## 🟡 B‑12 — Dead/half‑wired feature code paths (behavioural traps for future edits)
- **Location:**
  - Accessibility bridge: `SyncCoordinator.startAccessibilityBridge/stopAccessibilityBridge/pollAccessibilityClipboard`,
    `ClipboardSyncManager.start/stopAccessibilityMonitoring`, `NetworkUtils.isAccessibilityServiceEnabled`
    — **no callers** (verified). `ServiceSnapshot.accessibilityEnabled` is always `false`.
  - `SyncMeshBridge` methods `sendPrimaryClipboard`, `getClipboardHistory`,
    `getLatestRemoteClipboardText`, `openSyncMeshApp`, `openSyncMeshHistory`, `getSetting`/`setSetting`
    — **not invoked** by the keyboard (only `autoSendClipboardIfNeeded` is).
  - `DatabaseHelper` (empty subclass), `AppRepository.readClipboardEntry` (private, unused),
    `app_settings` table + `getSetting`/`setSetting`, `AppPreferences` `keyboard_language`/
    `last_keyboard_sent_*` accessors — unused.
- **Why:** scaffolding for planned features (keyboard toolbar, accessibility capture) left in place.
- **Impact:** not a runtime bug, but misleads readers and risks "fixing"/wiring half‑built paths. Also
  reflection targets in the unused bridge methods would break under shrinking if ever enabled (see B‑2).
- **Fix:** remove or clearly mark as future work.

## 🟡 B‑13 — Pinning/deleting history not reachable from app UI
- **Location:** `SyncDatabaseHelper.updateClipboardPinned/deleteClipboardEntry`,
  `AppRepository.updateClipboardPinned/deleteClipboardEntry` exist; `ClipboardHistoryFragment`
  only wires copy + clear‑all. History query orders by `is_pinned DESC` but nothing sets pins in the app.
- **Impact:** feature appears designed (pin column, ordering) but is inert in the app UI.
- **Fix:** wire pin/delete actions or remove the affordance.

## 🟡 B‑14 — `stopWithTask="false"` + START_STICKY can resurrect a service the user swiped away
- **Location:** `AndroidManifest.xml` service decl + `SyncForegroundService` `START_STICKY`.
- **Why:** intended persistence, but combined with no user‑visible "sync auto‑restarted" cue it can
  surprise users (background sockets + battery while they think the app is closed).
- **Impact:** battery/behavioural; not a crash.
- **Fix:** confirm desired persistence semantics; consider `START_NOT_STICKY` or a user preference.

---

## Cross‑cutting risk categories (mapping)

- **Crash risks:** B‑2 (release), B‑3 (lifecycle NPE/ISE), B‑6 (NPE).
- **ANR:** B‑1.
- **Concurrency / race:** B‑4, B‑5, B‑6.
- **Memory / storage:** B‑8 (unbounded), B‑7 (thrash).
- **Network:** B‑10, and see `05_API_AND_NETWORK.md` (no retry/backoff, 3 s hard timeout).
- **Battery / background:** B‑14 (sticky service), UDP broadcast every 3 s + multicast lock while running.
- **UX / logic:** B‑9, B‑11, B‑13.
- **Maintainability traps:** B‑12.
