# SyncMesh — Complete Project Handoff

> **Purpose of this document.** This is a self-contained handoff for a *new* Claude Code
> session with **zero prior knowledge** of SyncMesh. It captures the project's purpose,
> architecture, every feature, the UI system, security posture, known bugs, technical debt,
> decisions, roadmap, and the rules any future session must follow. Read the **Summary**
> section last-first if you're in a hurry.
>
> **Repo root:** `/Users/ankit/Desktop/astudio/Syncmesh2`
> **Companion docs:** the `summary/` folder holds deeper per-topic analyses
> (`01_PROJECT_SUMMARY.md` … `12_CHANGE_GUIDE.md`, plus `PHASE1/2/3_PROGRESS.md`).
> This document supersedes and consolidates them for handoff purposes.
>
> **Current version:** `versionName = "1.2"`, `versionCode = 12`.
> **Last work done:** Phase 3 (file transfer) complete + debug easter-egg + unpair
> propagation + Play update redirect; a signed release build was produced.

---

# Project Overview

## Purpose of SyncMesh
SyncMesh is an **Android app that syncs the clipboard between devices on the same local
network** (Wi-Fi or hotspot) with **no cloud relay** — all transport is direct
device-to-device over LAN sockets. As of Phase 3 it also does **local-network file
transfer** (any file type, up to 2 GB each). Copy text on one paired phone and it appears
on every other paired phone; send a file and it lands in `Downloads/SyncMesh/` on the peer.

## Target users
- People with multiple Android phones/tablets who want frictionless copy-paste and file
  sharing across their own devices without accounts, cloud, or internet.
- Privacy-conscious users: **no account, no cloud, no analytics** — data never leaves the
  local network and history is stored only on-device.

## Main features
1. Foreground clipboard-sync service (TCP server, UDP discovery, clipboard monitoring).
2. Automatic clipboard propagation to all paired devices.
3. Device pairing via 6-digit code — manual IP/port, QR code, or nearby auto-discovery.
4. Nearby discovery over UDP broadcast.
5. Clipboard history (persisted, tap-to-recopy).
6. **File transfer** (single/multiple files, 2 GB cap, accept/reject, live progress with
   speed/ETA/pause/resume/cancel/retry, conflict-safe naming, searchable history).
7. Bundled HeliBoard keyboard add-on that auto-sends the clipboard when the keyboard opens
   (works around Android 10+ background-clipboard restrictions).
8. Onboarding carousel + spotlight guided tours (main tour + transfer sub-tour).
9. Hidden debug console (unlocked by an easter-egg tap sequence).
10. In-app update check that redirects to the Play Store listing.

## Core architecture
Pragmatic **layered / repository architecture** built on **process-wide singletons** and
**LiveData** — *no ViewModel, no Jetpack Navigation, no DI framework* in the app module.

```
UI (Activities / Fragments / RecyclerView Adapters)
        │  observe LiveData / call methods
        ▼
SyncCoordinator (singleton orchestrator)  ── ClipboardSyncManager (singleton)
        │                                        │
        ├── TcpServer / TcpClient (JSON-over-TCP 8989)  │ system ClipboardManager listener
        ├── UdpDiscoveryManager (UDP broadcast 8990)    │
        ├── FileTransferManager → FileTransferServer/Sender (TCP 8991)
        ▼                                        ▼
AppRepository (singleton, source of truth) ── AppPreferences (SharedPreferences)
        │                                   └─ SyncDatabaseHelper (SQLite)
        ▼
LiveData streams → UI
```

## Tech stack
- **App module language:** Java (~50 source files). Views + **ViewBinding** (no Compose in app).
- **Reactive layer:** `androidx.lifecycle` LiveData.
- **Networking:** raw `java.net` sockets (`ServerSocket`/`Socket`/`DatagramSocket`), `org.json`.
- **Persistence:** raw `SQLiteOpenHelper` + `SharedPreferences` (no Room).
- **Concurrency:** `java.util.concurrent` executors + `Handler(Looper.getMainLooper())`.
- **Libraries:** Material 3, RecyclerView, ZXing (`core` 3.5.3 + `zxing-android-embedded`
  4.3.0) for QR, `androidx.documentfile:1.0.1` (SAF), `com.google.android.play:app-update:2.1.0`.
- **Keyboard module:** vendored **HeliBoard 3.9** fork (Kotlin + Compose + coroutines +
  kotlinx-serialization) bundled as an Android **library** (`:keyboard_heliboard`), merged
  into the same APK. Its `applicationId` is forced to `com.ankit.syncmesh`.
- **Build:** Gradle 8.13 / AGP 8.13.0, Kotlin 2.3.20, `compileSdk/targetSdk 36`, `minSdk 26`,
  Java 17. ABI splits (arm64-v8a, armeabi-v7a, x86, x86_64 + universal).

## Design philosophy
- **Local-first, zero-cloud, zero-account.** Privacy by architecture, not by policy.
- **Additive, non-regressive changes.** Each new subsystem gets its own port, package, DB
  table, and UI; existing paths are only touched via additive hooks. Unknown wire message
  types are ignored by older peers.
- **Minimalist neutral UI** with a single emerald accent; elevation via surface contrast,
  not shadows.
- **Pragmatism over ceremony:** singletons + LiveData instead of full MVVM/DI.

## Current development phase
**Phase 3 complete.** Phases were: Phase 1 (baseline clipboard sync app), Phase 2 (UI bug
fixes + Pair-screen redesign), Phase 3 (file transfer + debug easter-egg + unpair
propagation + Play update + v1.2 release). The project is between phases — the next work is
whatever the user requests in the new session.

---

# Folder Structure

```
Syncmesh2/
├── app/                        SyncMesh application module (Java)
│   ├── build.gradle.kts        version 1.2 / code 12; signing; ABI splits; minify on for release
│   ├── proguard-rules.pro      app R8 rules
│   └── src/main/
│       ├── AndroidManifest.xml Components + permissions (see below)
│       ├── java/com/ankit/syncmesh/   ← all app Java (detailed below)
│       └── res/                layouts, menus, drawables, values (colors/dimens/strings/styles)
├── keyboard_heliboard/         Vendored HeliBoard fork (its OWN git repo + gradle wrapper)
│   └── app/                    Library module; projectDir remapped in settings.gradle.kts
│       └── .../latin/syncmesh/ SyncMeshBridge.java, SyncMeshClipboardItem.java (reflection bridge)
├── gradle/
│   ├── libs.versions.toml      Main version catalog (mostly keyboard deps)
│   └── tools.versions.toml     Secondary "tools" catalog
├── build.gradle.kts            Root plugins (all `apply false`)
├── settings.gradle.kts         Includes :app and :keyboard_heliboard
├── gradle.properties           SDK levels + ⚠️ keystore passwords in plaintext
├── local.properties            SDK path
├── syncmesh-release.jks        ⚠️ RELEASE SIGNING KEYSTORE committed to repo
├── summary/                    Deep-dive analysis docs (00_INDEX … 12_CHANGE_GUIDE, PHASE1/2/3)
└── HANDOFF.md                  ← this document
```

## Entry points
- **Launcher / only exported activity:** `ui.MainActivity` (bottom-nav host).
- **Application:** `SyncMeshApplication.onCreate()` → keyboard `App.initialize()`,
  `SyncLog.init()`, `AppRepository.getInstance()`, `FileTransferManager` foreground tracker.
- **Background:** `sync.SyncForegroundService` (`foregroundServiceType="dataSync"`,
  `START_STICKY`, `stopWithTask="false"`), started from Home's Start Sync button.
- **IME:** `helium314.keyboard.latin.LatinIME` (merged from keyboard module); calls
  `SyncMeshBridge.autoSendClipboardIfNeeded(this)` in `onStartInputView`.
- **Keyboard settings:** `helium314.keyboard.settings.SettingsActivity`.

## Important Java files (`app/src/main/java/com/ankit/syncmesh`)

**Root**
- `SyncMeshApplication.java` — app entry; initializes keyboard, logger, repository, transfer tracker.

**`data/`**
- `AppRepository.java` — **single source of truth.** Owns SQLite helper, prefs, in-memory
  nearby-device map, all `MutableLiveData`. Re-reads SQLite on every publish (no caching).
- `AppPreferences.java` — SharedPreferences: device id/name, pairing code, keyboard flags,
  `transfer_tour_complete`, transfer save-URI, `debug_unlocked`.
- `SyncDatabaseHelper.java` — `SQLiteOpenHelper`, **schema v9**. Tables: `devices`,
  `clipboard_history`, `sync_logs`, `app_settings`, `transfer_history`. Additive migrations
  via `ensureColumn`/`ensureSchema`.
- `TransferRepository.java` — transfer history LiveData + `search()`.
- `DatabaseHelper.java` — empty unused subclass (dead code; see Technical Debt).

**`model/`** (plain public-field POJOs)
- `ClipboardEntry`, `ClipboardModel`, `DiscoveredDevice`, `LogEntry`, `PairedDevice`,
  `ServiceSnapshot`, `TransferFileInfo`, `TransferRecord`.

**`sync/`** (clipboard/pairing/discovery — port 8989/8990)
- `SyncCoordinator.java` — orchestrator singleton. Pairing, ping, clipboard send/receive,
  discovery handling, JSON building, error mapping, `removePairedDeviceAndNotify`,
  `handleUnpair`, transfer-server start/stop hooks in `start/stopRuntime`.
- `SyncForegroundService.java` — foreground `dataSync` service; boots/stops runtime.
- `ClipboardSyncManager.java` — system clipboard listener + dedup + apply-remote.
- `TcpServer.java` — line-delimited JSON TCP server on 8989.
- `TcpClient.java` — one-shot TCP client (3s timeouts).
- `UdpDiscoveryManager.java` — UDP broadcast/listen on 8990 (announce every 3s, expire 15s).

**`transfer/`** (file transfer — port 8991, fully separated subsystem)
- `TransferProtocol.java` — constants (`PORT=8991`, `MAX_FILE_SIZE=2GB`, `CHUNK_SIZE=64KB`),
  message builders/parsers, `sanitizeFileName()`, `validateOffer()`.
- `FileTransferSender.java` — streams one outgoing transfer over a single socket.
- `FileTransferServer.java` — listens on 8991, validates offers (sender must be paired),
  drives accept latch, streams incoming files to storage.
- `FileTransferManager.java` — singleton orchestrator: server lifecycle, single active
  `TransferState` LiveData, `sendFiles`/`retryLast`/`pause`/`resume`/`cancel`,
  `awaitUserDecision`, `finishSession`, `registerForegroundTracker`, notifications.
- `TransferState.java` — UI-facing progress snapshot.
- `TransferStorage.java` — conflict-safe destinations (MediaStore 29+, app-external 26–28,
  or user SAF tree); `numberedName()` for `photo → photo (1)`; path-traversal-proof.
- `TransferActionReceiver.java` — notification Accept/Reject broadcast handler.

**`ui/`**
- `MainActivity.java` — bottom-nav host (Home/Pair/Devices/Transfer/History via manual
  `FragmentTransaction.replace`); toolbar menu (debug visible only if unlocked, check-updates);
  `startTour(steps, onFinished)`, `buildTransferTourSteps`, `maybeStartTransferTour`.
- Fragments: `HomeFragment`, `PairFragment`, `PairedDevicesFragment`,
  `ClipboardHistoryFragment`, `DebugFragment`, `FileTransferFragment`.
- Standalone activity hosts (alternate/deep-link entries for the same fragments):
  `PairDeviceActivity`, `PairedDevicesActivity`, `ClipboardHistoryActivity`, `DebugActivity`,
  `FileTransferActivity`, `TransferHistoryActivity`, plus `IncomingTransferActivity`,
  `QrScannerActivity`, `OnboardingActivity`.
- `TourOverlayView.java` — reusable spotlight tour overlay.
- `adapter/` — `ClipboardHistoryAdapter`, `LogAdapter`, `NearbyDevicesAdapter`,
  `PairedDevicesAdapter`, `TransferFilesAdapter`, `TransferDevicesAdapter`,
  `TransferHistoryAdapter`.

**`util/`**
- `NetworkUtils.java` — local IPv4, broadcast addrs, IME/accessibility checks.
- `NotificationHelper.java` — channels + service/transfer/unpair notifications; `canNotify()`.
- `PermissionHelper.java` — notification + camera permission checks.
- `DisplayUtils.java` — time/endpoint formatting + `formatBytes`/`formatSpeed`/`formatEta`.
- `SyncLog.java` — Logcat + persisted-log facade; `persistToDb` gate (off unless debug unlocked).
- `Toasts.java` — `brief()` shows a toast then cancels it after ~120ms (~0.1s feedback).
- `UpdateManager.java` — Play in-app-update check; redirects to Play listing.

## How modules interact
- UI depends on both `SyncCoordinator` and `AppRepository`; observes repo LiveData directly.
- `SyncCoordinator` depends on `AppRepository` + `ClipboardSyncManager`; constructs network
  services; owns transfer-server start/stop hooks.
- `AppRepository` depends only on `SyncDatabaseHelper`, `AppPreferences`, `NetworkUtils`.
- The **keyboard module cannot compile-time depend on the app** (dependency is one-way:
  app → keyboard). `SyncMeshBridge` therefore reaches app singletons via **Java reflection**
  at runtime (`SyncCoordinator#sendManualClipboardText`, `AppRepository` methods by name).

---

# Features

> Each feature: Purpose · Implementation · Key files · Status · Limitations · Planned.

## Clipboard sync
- **Purpose:** copy on one device → appears on all paired devices.
- **Implementation:** `ClipboardSyncManager` registers `OnPrimaryClipChangedListener`; local
  copies are deduped and pushed as a `clipboard_update` JSON to each paired device over TCP
  8989 (fire-and-forget). Inbound updates verify the sender is paired, dedup by `eventId`,
  persist a `remote` history row, and set the system clipboard on the main thread.
- **Key files:** `ClipboardSyncManager`, `SyncCoordinator`, `TcpServer`, `TcpClient`.
- **Status:** working.
- **Limitations:** Android 10+ blocks background clipboard reads — the app-side listener only
  fires while the app is foreground; reliable background send requires the bundled keyboard.
  Plaintext on the wire and in storage/logs.
- **Planned:** encrypt payloads; stop logging clipboard text.

## File transfer  *(Phase 3, newest)*
- **Purpose:** send any file(s) (≤2 GB each) to a paired device over LAN.
- **Implementation:** separate TCP channel on **8991**, line-delimited JSON control + raw byte
  payloads, 64 KB streaming chunks. Sender picks files via SAF `OpenMultipleDocuments`,
  metadata validated, confirmation dialog, choose paired device, send. Receiver validates the
  offer (sender must be paired), blocks on a `CountDownLatch` while the user accepts/rejects
  (dialog if foreground via `IncomingTransferActivity`, otherwise notification actions), then
  streams to storage with per-file acks. Live progress screen: overall + per-file %, speed,
  ETA, bytes, N/M counter, pause/resume/cancel/retry. State lives in `FileTransferManager` so
  it survives rotation/tab changes and continues in the background under the foreground service.
- **Wire protocol:** `transfer_offer` → `transfer_response` → per file `file_header` + bytes +
  `file_ack` → `transfer_complete` → `transfer_result`; `transfer_cancel` either side.
- **Key files:** everything in `transfer/`, `ui/FileTransferFragment`, `ui/FileTransferActivity`,
  `ui/IncomingTransferActivity`, `ui/TransferHistoryActivity`, the three transfer adapters,
  `TransferRepository`, `transfer_history` DB table.
- **Status:** working end-to-end (verified by user across two emulators + host bridge).
- **Limitations:** one transfer at a time (single session slot); no partial-resume across
  disconnect (Retry restarts from scratch); long pause can hit socket timeout; plaintext channel.
- **Planned:** folder transfer, partial-resume/chunk acks, concurrent transfers, history
  filters, encryption, image thumbnails.

## Device discovery
- **Purpose:** find nearby SyncMesh devices without typing IPs.
- **Implementation:** `UdpDiscoveryManager` broadcasts `discovery_announce` JSON every 3s on
  UDP 8990; received announcements populate an in-memory `ConcurrentHashMap` (self ignored);
  entries expire after 15s. Never persisted; cleared on `stopRuntime`.
- **Key files:** `UdpDiscoveryManager`, `SyncCoordinator.handleDiscoveryAnnouncement`,
  `AppRepository` nearby map, `NearbyDevicesAdapter`.
- **Status:** working on real LANs.
- **Limitations:** standard AVD emulators all self-report `10.0.2.15` and can't route to each
  other — genuine multi-device needs real devices or a host bridge (see Development Rules).
  Leaks device UUID + manufacturer/model on the LAN.

## Pairing
- **Purpose:** establish trust between two devices.
- **Implementation:** each device has a persistent 6-digit code (`SecureRandom`). Sender sends
  `pair_request` with its code; receiver compares to its own local code; on match both upsert a
  `PairedDevice` row and return `pair_response`. Three entry paths: manual IP/port/code, QR, or
  tap a nearby discovered device.
- **Key files:** `SyncCoordinator.sendPairRequest`/`handlePairRequest`, `PairFragment`,
  `PairedDevice`, `devices` table.
- **Status:** working.
- **Limitations:** code space only 10^6, **no rate limiting / lockout / expiry**; `deviceId`
  auth is self-asserted (spoofable). See Security.
- **Planned:** longer/one-time codes, rate limiting, expiry, challenge-response.

## QR pairing
- **Purpose:** pair by showing/scanning a QR instead of typing.
- **Implementation:** `PairFragment.showPairQr` encodes a `syncmesh_pair_qr` JSON
  (deviceId, deviceName, ipAddress, port, pairingCode) via ZXing; `QrScannerActivity`
  (camera, permission-gated) scans and pre-fills the pair form.
- **Key files:** `PairFragment`, `QrScannerActivity`, `dialog_pair_qr.xml`.
- **Status:** working.
- **Limitations:** QR embeds the pairing code in cleartext and codes never expire — anyone who
  photographs the QR gets full credentials.

## Security
See the dedicated **Security** section. Summary: **plaintext everywhere, weak auth.** Positive:
`SecureRandom` codes, minimized exported components, parameterized SQL, path-traversal-proof
file writes, sender-must-be-paired gate on transfers.

## Notifications
- **Purpose:** persistent service state + transfer interactivity.
- **Implementation:** `NotificationHelper` with channels for the foreground service and a
  `TRANSFER_CHANNEL_ID`. Incoming-transfer notification has **Accept/Reject** actions
  (`TransferActionReceiver`); progress + finished notifications; unpaired notification.
  Clipboard-received notification exists but is currently disabled.
- **Status:** working. `canNotify()` respects permission.

## Background services
- **Purpose:** keep sockets + transfers alive when app is backgrounded / screen off.
- **Implementation:** single `SyncForegroundService` (`dataSync`, `START_STICKY`,
  `stopWithTask="false"`). Boots `SyncCoordinator.startRuntime` (TCP 8989, UDP 8990,
  clipboard monitor, transfer server 8991). Not auto-started — user taps Start Sync.
- **Status:** working.

## Onboarding
- **Purpose:** first-run explainer.
- **Implementation:** `OnboardingActivity` — 8-slide carousel (welcome, what it does,
  permissions, how sync works, pairing, keyboard add-on, privacy, ready). Skip/Back/Next/Get
  Started. Shown once (pref-gated).
- **Key files:** `OnboardingActivity`, `activity_onboarding.xml`, `item_onboarding.xml`.
- **Status:** working. (Phase 2 fixed the first-step Back-button layout gap.)

## Guided tour
- **Purpose:** in-app spotlight walkthrough after onboarding.
- **Implementation:** `TourOverlayView` draws a scrim with a spotlight cutout + a tour card
  (`view_tour_card.xml`). `MainActivity.startTour(steps, onFinished)` is generic. Two tours:
  the main tour and a **transfer sub-tour** (`buildTransferTourSteps`) that runs the first time
  the Transfer tab is opened, gated by `transfer_tour_complete`.
- **Status:** working.

## Settings
- **Purpose:** user-adjustable behavior.
- **Implementation:** no dedicated settings screen; toggles live inline — keyboard auto-send
  switch (Home), transfer save-location row (Transfer), keyboard's own `SettingsActivity`.
  Prefs in `AppPreferences`.
- **Status:** minimal-by-design.

## Theme
- **Purpose:** consistent visual language.
- **Implementation:** `Theme.Syncmesh` Material 3; neutral surfaces + single **emerald**
  accent (`#059669`); design tokens in `colors.xml` (`ds_*`) with legacy `syncmesh_*` names
  re-pointed at them. Light theme only (dark not implemented for the app UI; keyboard has its
  own dark palette). See UI/UX section.

## Desktop support
- **Not implemented.** SyncMesh is Android-only. The wire protocol (JSON over TCP/UDP) is
  simple enough that a desktop peer is feasible in future, but nothing exists today.

## Android support
- `minSdk 26` (Android 8.0) → `targetSdk 36`. Handles per-version storage (MediaStore 29+ vs
  app-external 26–28), notification permission (33+), foreground-service-type (`dataSync`).

## Logging
- **Purpose:** diagnostics via a hidden debug console.
- **Implementation:** `SyncLog` writes to Logcat always; persists to the `sync_logs` table
  **only when `persistToDb` is true**, which is only when debug is unlocked. `DebugFragment`
  shows live service state (TCP/UDP/service/IP) + a log stream with Copy Logs / Clear Logs and
  a "Turn off debug mode" button. Debug is **hidden by default** and unlocked by tapping the
  version label ("SyncMesh v1.2") 7 times.
- **Status:** working. ⚠️ Clipboard text currently leaks into logs (see Security M-1).

## Error handling
- Network errors mapped to user-facing strings via `SyncCoordinator.toUserFacingNetworkError`.
- Transfer failures delete partial files, surface a terminal state with Retry, and log.
- Transfer-server start/stop is wrapped in try/catch so it can never crash clipboard sync.
- 2 GB file cap shows an explicit error dialog.

## Performance optimizations
- File transfer **streams in 64 KB chunks** — files are never fully loaded into RAM.
- Progress/speed/ETA updated ~2–3×/second (throttled), not per-chunk.
- ABI splits keep per-device APKs small (~19–22 MB vs 53 MB universal).
- Release build: R8 minify + resource shrink.
- ⚠️ Counter-optimization: `refreshSnapshot()` does SQLite + NIC enumeration **on the main
  thread** (ANR risk — see Known Bugs).

## Other implemented
- **Update check** (`UpdateManager`) — Play in-app-update API; redirects to Play listing;
  no-op on sideloaded builds.
- **Unpair propagation** — removing a device notifies the peer (`unpair` message) so it drops
  the pairing too and shows a notification.
- **Debug easter-egg** — 7 taps on the version label unlocks the debug console + `<>` toolbar
  icon; a button disables it again and hides the icon.

---

# UI/UX

## Current design language
Minimalist, neutral, flat. Material 3 components. **Elevation is expressed through surface
contrast, not shadows** (`syncmesh_shadow` is transparent). Card-based layouts with generous
padding. Single accent color for all interactive/brand elements.

## Color palette (light, `res/values/colors.xml`)
| Token | Hex | Use |
|-------|-----|-----|
| `ds_bg` | `#FFFFFF` | page background |
| `ds_bg_sidebar` | `#F7F7F7` | toolbar / bottom nav |
| `ds_bg_card` | `#F2F2F2` | raised surfaces (cards) |
| `ds_bg_input` | `#F4F4F5` | form controls |
| `ds_bg_hover` | `#EAEAEA` | nested panels |
| `ds_border` | `#D4D4D8` | dividers / outlines |
| `ds_text` | `#18181B` | primary text |
| `ds_text_muted` | `#52525B` | secondary text |
| `ds_text_subtle` | `#A1A1AA` | tertiary / placeholder |
| `ds_accent` | `#059669` | **emerald** — interactive/brand |
| `ds_accent_soft` | `#D1FAE5` | active-state tint |
| `ds_success` | `#059669` | success |
| `ds_warning` | `#B45309` | warning |
| `ds_danger` | `#DC2626` | error/destructive |

Legacy `syncmesh_*` color names are **re-pointed** at these tokens — never hardcode hex, use
tokens. The keyboard has its own dark palette (`syncmesh_keyboard_*`).

## Typography
Custom `TextAppearance.Syncmesh.*` styles: `Overline`, `Title`, `Body`, `Label`, `Mono`,
plus `Widget.Syncmesh.SectionLabel`. Use these, not raw `textSize`.

## Spacing (`res/values/dimens.xml`)
`space_xxs`=2, `space_xs`=4, `space_sm`=8, `space_md`=12, `space_lg`=16, `space_xl`=20,
`space_2xl`=24, `space_3xl`=32. `screen_margin`=16, `card_padding`=20, `screen_padding_bottom`=96.
Radii: `radius_pill`=999, `radius_lg`=12, `radius_md`=8, `radius_sm`=4. **Always use these
dimens, never magic numbers.**

## Animations
Minimal — spotlight tour transitions, standard Material ripples/transitions. No custom motion
system.

## Components
Reusable styles: `Widget.Syncmesh.Card`, `Widget.Syncmesh.Button.Tonal` / `.Outlined`,
`Widget.Syncmesh.Divider`, status badges (`bg_status_badge_active`/`_idle`), metric tiles
(`bg_metric_tile`), panels (`bg_panel`), warning banner (`bg_warning_banner`). RecyclerView
lists via the adapters in `ui/adapter/`.

## Navigation
`BottomNavigationView` with **5 tabs**: Home, Pair, Devices, Transfer, History
(`bottom_nav_menu.xml`). Manual `FragmentTransaction.replace` in `MainActivity` — **no Jetpack
Navigation / nav graph.** Toolbar overflow: Check for updates, Debug (visible only when
unlocked). Standalone activities host the same fragments for deep-links/notifications.

## Onboarding
8-slide first-run carousel (see Features). Skip/Back/Next/Get Started.

## Guided tour
Spotlight overlay (`TourOverlayView`) — main tour after onboarding; transfer sub-tour on first
Transfer-tab open. Steps spotlight a target view + show a titled card.

## Empty states
- Transfer "Send to" with no paired devices → friendly empty state + **Pair a device** button
  jumping to the Pair tab.
- Debug logs empty → centered icon + "empty logs" text.
- Home recent-clip empty → placeholder text.

## Loading states
Transfer progress screen is the main "in-progress" surface (progress bars, speed, ETA, status
badge). Elsewhere, actions are quick one-shot socket calls with toast feedback.

## Future UI direction
Dark theme for the app UI, image thumbnails/previews in transfer, richer transfer-history
filters, possibly a proper settings screen. Keep the neutral+emerald minimalist language.

---

# Security

> Full analysis in `summary/06_SECURITY_ANALYSIS.md`. **The threat model matters:** SyncMesh
> moves clipboard contents (often passwords/OTPs) over a shared LAN with **no encryption and
> weak auth**. Treat all findings below as real.

## Encryption
**None.** No TLS, no payload encryption. `usesCleartextTraffic="true"`. Clipboard + files
travel in plaintext; clipboard history and (when debug on) full RECV/SEND lines are stored in
plaintext SQLite. **This is the #1 thing to fix before any real-world release.**

## Authentication
Pairing = matching a persistent **6-digit code**. After pairing, message authorization is just
`isPairedDevice(fromDeviceId)` where `fromDeviceId` is an **unverified JSON field** — spoofable.
No token, signature, or challenge-response. No rate limiting / lockout / expiry on pairing
(10^6 space, brute-forceable one round-trip per guess).

## Trust model
"Anyone who knows my 6-digit code and is on my LAN is trusted." Discovery announcements leak
device UUIDs on the subnet, making spoofing targets easy to harvest. QR embeds the code in
cleartext; codes never rotate.

## Network communication
Raw sockets bound to `0.0.0.0`: TCP 8989 (clipboard/pairing), UDP 8990 (discovery), TCP 8991
(transfer). All are **unauthenticated, always-on parsers exposed to the whole subnet** while
sync runs. `org.json` parsing is lenient; individual handlers are guarded but the surface is broad.

## Permission handling
Requests: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`CHANGE_WIFI_MULTICAST_STATE`, `FOREGROUND_SERVICE`(+`_DATA_SYNC`), `POST_NOTIFICATIONS`,
`CAMERA` (required=false). Notification permission requested at Start-Sync (API 33+); camera at
QR-scan time. Runtime revoke handled by re-prompt only.

## Privacy decisions (positive)
- No account, no cloud, no analytics — data stays on the LAN.
- History stored only on-device.
- Pairing codes use `SecureRandom`.
- Only `MainActivity` is exported; service + receiver are `exported="false"`.
- Parameterized SQL throughout (no injection).
- **File writes are path-traversal-proof**: filenames sanitized, MediaStore/SAF only receive a
  display name, legacy path canonical-checked against the target dir; sender must be paired.

## Known critical/high issues (fix directions in `06_SECURITY_ANALYSIS.md`)
- **C-1** Release keystore + passwords committed to VCS (`syncmesh-release.jks`,
  `build.gradle.kts`, `gradle.properties`). Rotate + remove from history + inject from CI secret.
- **C-2** Plaintext clipboard in transit/storage/logs. Encrypt; stop logging text.
- **H-1** Brute-forceable pairing + spoofable deviceId auth.
- **H-2** Unauthenticated 0.0.0.0 listeners.
- **H-3** `allowBackup="true"` with empty rules → code + history backup-eligible.
- **H-4** Reflection bridge (`SyncMeshBridge`) not shrink-safe: R8 tree-shaking could drop
  `sendManualClipboardText` in release even though `-dontobfuscate` keeps names. **Verify
  keyboard auto-send works in the release build**; add explicit `-keep` for
  `com.ankit.syncmesh.*` reflected members.

---

# Current TODO
- [ ] Verify keyboard auto-send (`SyncMeshBridge` reflection) survives R8 in the **release**
      build; add `-keep` rules if broken (Security H-4).
- [ ] Confirm the produced release APK installs + runs on the user's physical phone.
- [ ] (Security backlog) Encrypt clipboard + transfer channels; stop logging clipboard text;
      remove keystore from VCS + rotate; tighten backup rules; add pairing rate-limit/expiry.
- [ ] Folder transfer, partial-resume, concurrent transfers, transfer-history filters.
- [ ] Dark theme for the app UI.
- [ ] Consider a real desktop peer.

## Release build location (last produced)
Signed **v1.2** APKs at `app/build/outputs/apk/release/`:
- `app-arm64-v8a-release.apk` (~22M) — recommended for modern phones
- `app-armeabi-v7a-release.apk` (~19M) — older 32-bit phones
- `app-universal-release.apk` (~53M) — works on any device (use if unsure)
- `app-x86-release.apk` / `app-x86_64-release.apk`
Build with `./gradlew :app:assembleRelease`. To install on a physical phone the user must
enable "Install unknown apps" for their file manager/browser. Because it's sideloaded, the
in-app update check is a silent no-op (manual "Check for updates" still opens the Play listing).

---

# Known Bugs
- **ANR/StrictMode risk:** `SyncCoordinator.refreshSnapshot()` / `buildDefaultSnapshot()` run
  on the **caller's thread** and do a SQLite query (`getPairedDevices`) + NIC enumeration
  (`getLocalIpv4Address`) — frequently called from `onResume`/`onViewCreated` on the **main
  thread**. Move off-main. (`summary/07_BUG_REPORT.md`)
- **Clipboard text leaks into persisted logs + Logcat** (Security M-1) when debug is unlocked.
- **Emulator networking:** two default AVDs can't route to each other (all report 10.0.2.15).
  Not an app bug, but blocks emulator-only testing without a host bridge.
- **H-4 latent:** keyboard auto-send may silently break in release (unverified). See TODO.
- Long transfer **pause** can hit the data-socket timeout and fail the transfer.
- See `summary/07_BUG_REPORT.md` for the full enumerated list.

---

# Technical Debt
- `data/DatabaseHelper.java` — empty unused subclass; delete.
- **No tests** — `app/src/test` and `app/src/androidTest` have no sources.
- `AppRepository` re-reads SQLite on every LiveData publish (no caching/diffing) — fine now,
  won't scale.
- Main-thread DB/NIC work in `refreshSnapshot` (see Known Bugs).
- Standalone activities duplicate the fragment-hosting pattern — lots of near-identical hosts.
- Reflection bridge (`SyncMeshBridge`) is brittle to renaming/shrinking; string-named calls.
- `-dontobfuscate` from the keyboard's consumer ProGuard leaks app-wide, disabling obfuscation
  for the whole app (Security M-2).
- No ViewModel — Fragments hold direct singleton refs + observe LiveData; state-restoration is
  manual.
- Committed secrets (keystore + passwords) — must be extracted to secure config.
- See `summary/08_TECHNICAL_DEBT.md`.

---

# Recent Changes (Phase 2 + Phase 3)

**Phase 2 (UI fixes):**
- Fixed onboarding first-step layout gap (invisible Back button reserving space).
- Fixed Pair-screen divider/badge overflow when Sync is OFF; redesigned the "My device" card
  header to a production-quality layout with a tappable warning that shows the error message.

**Phase 3 (file transfer + platform work):**
- Complete file-transfer subsystem (port 8991): sender/receiver engines, manager singleton,
  storage with conflict-safe naming + SAF custom location, incoming accept/reject dialog +
  notification, live progress (speed/ETA/pause/resume/cancel/retry), searchable history,
  path-traversal-proof writes, 2 GB cap. New `transfer/` package, `transfer_history` DB table
  (schema v8→v9), transfer UI + 3 adapters.
- Permanent **Transfer** bottom-nav tab (5 tabs) + one-time transfer sub-tour; empty state
  with "Pair a device"; removed transfer items from toolbar overflow.
- Files save to `Downloads/SyncMesh/` with user-changeable location (SAF tree, persisted grant).
- **Unpair propagation:** `removePairedDeviceAndNotify` sends `unpair`; peer's `handleUnpair`
  drops the pairing + shows a notification.
- **Debug hidden by default** + 7-tap version easter-egg to unlock; toolbar `<>` icon shows
  only when unlocked (`invalidateOptionsMenu` in `onResume`); "Turn off debug mode" button
  (outlined red) disables + hides it; logs only persist to DB when debug is on.
- Version-tap no longer opens the console (only unlocks).
- Version bumped to **1.2 / code 12**; added `documentfile` + Play `app-update` deps.
- **Play update redirect** (`UpdateManager`) — on-launch check + manual "Check for updates".
- `Toasts.brief()` for ~0.1s toast feedback (fixed slow update toast).
- Produced a signed v1.2 release build (ABI splits + universal).

---

# Important Decisions (and why)
- **Singletons + LiveData, no ViewModel/DI:** small app, keeps wiring trivial; `AppRepository`
  is the single source of truth so state is consistent across fragments and the keyboard bridge.
- **Raw sockets + hand-rolled JSON protocol (no Retrofit/gRPC):** peer-to-peer LAN with no
  server; the protocol is intentionally minimal and human-readable for debuggability.
- **Additive protocol design:** unknown `type`s are ignored by older peers, so new message
  types (`unpair`, all transfer types) are backward compatible — no version negotiation needed.
- **File transfer on its own port/package/table/UI:** total separation guarantees it can't
  regress clipboard sync; the only shared read is the paired-device list.
- **Streaming 64 KB chunks:** supports 2 GB files without OOM.
- **Reflection bridge (keyboard→app):** dependency must be one-way (app depends on keyboard as
  a library), so the IME reaches app singletons reflectively at runtime.
- **Bundled HeliBoard keyboard:** Android 10+ blocks background clipboard access; an IME can
  read the clipboard when it opens, making background send reliable.
- **Debug hidden behind an easter-egg:** keep the production UI clean while retaining
  field-diagnostics; logs only touch the DB when explicitly enabled (privacy + perf).
- **Play update redirect instead of bundled updater:** app is intended for Play distribution;
  sideloaded builds simply no-op.
- **Neutral + single-emerald palette, contrast-not-shadow:** calm, modern, cheap to maintain.

---

# Future Roadmap (rough priority order)
1. **Security hardening** — encrypt both channels; remove committed keystore + rotate; stop
   logging clipboard; pairing rate-limit/expiry/one-time codes; tighten backup rules.
2. **Verify + fix release-build reflection** (keyboard auto-send) with `-keep` rules.
3. **File transfer v2** — folder transfer, partial-resume, concurrent transfers, history filters.
4. **Dark theme** for the app UI.
5. **Image thumbnails/previews** in transfer.
6. **Tests** — at least protocol parsing/sanitization + storage naming unit tests.
7. **Desktop peer** (protocol is simple enough to reimplement).

---

# Development Rules

**Coding standards**
- App module is **Java**. Match existing style: 4-space indent, `final` where the codebase
  uses it, no wildcard imports, POJOs with public fields for models.
- Keyboard module is Kotlin — treat it as a vendored dependency; avoid editing it unless the
  task specifically needs the bridge.

**Architecture rules**
- **`AppRepository` is the single source of truth.** New persisted state goes through it +
  `SyncDatabaseHelper` + `AppPreferences`; expose via LiveData.
- **Singletons** use the double-checked-locking `getInstance(Context)` idiom.
- **No ViewModel, no Jetpack Navigation, no DI framework** in the app module — stay consistent.
- **Additive changes only** to existing subsystems. New features get their own package/port/
  table/UI. New wire types must be ignorable by older peers (route unknown `type` to the
  existing "unhandled" branch).
- **DB migrations are additive** — bump version, add tables/columns via `ensureColumn`/
  `createTables`; never rename/drop existing columns.
- Keep network + DB work **off the main thread** (use the existing executors); do not add to
  the `refreshSnapshot` main-thread problem.
- The **keyboard→app bridge is reflection**; if you rename/move app singletons or their
  methods, update `SyncMeshBridge` and add `-keep` rules.

**Naming conventions**
- Classes: `PascalCase`; the transfer subsystem lives under `transfer/`.
- Resources: `snake_case`; colors use `ds_*` tokens (legacy `syncmesh_*` re-point to them);
  dimens use the `space_*`/`radius_*` scale; text styles use `TextAppearance.Syncmesh.*`.
- Wire message types: lowercase `snake_case` strings (`pair_request`, `transfer_offer`, `unpair`).

**UI rules**
- Use the design tokens: **never hardcode hex, sizes, or text sizes** — use `ds_*`/`syncmesh_*`
  colors, `space_*`/`radius_*` dimens, `TextAppearance.Syncmesh.*` and `Widget.Syncmesh.*` styles.
- Neutral surfaces + single emerald accent; elevation via contrast, not shadow.
- New user-facing strings go in `res/values/strings.xml` (no hardcoded strings).
- Bottom-nav is the primary navigation; deep-links/notifications route via the standalone
  activity hosts.

**Process rules**
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` for releasable changes.
- Build check: `./gradlew :app:assembleDebug` should stay green after every change set.
- Do **not** commit or push unless the user asks. If asked, branch off `main` first and use
  the Co-Authored-By trailer.
- Testing multi-device on emulators requires a host bridge (`adb forward` + IP rewrite) because
  AVDs all report `10.0.2.15`; prefer two real devices on the same Wi-Fi.

---

# Things Not To Break
- **Clipboard sync** (the core product): `ClipboardSyncManager`, `SyncCoordinator` send/receive,
  `TcpServer`/`TcpClient` on **8989**, dedup by `eventId`, paired-sender authorization.
- **Pairing + discovery**: `pair_request`/`pair_response` handshake, 6-digit code compare,
  UDP 8990 discovery, `PairedDevice` upsert on both sides.
- **The wire protocol on 8989** — do not change existing message shapes; only add new types.
- **Keyboard integration** — `SyncMeshBridge.autoSendClipboardIfNeeded` and the reflected
  `SyncCoordinator#sendManualClipboardText` / `AppRepository` methods (names + signatures).
  This is the Android 10+ background-send path.
- **Foreground service lifecycle** — `SyncForegroundService` boots/stops the whole runtime;
  keep transfer-server start/stop wrapped in try/catch so it can't crash sync.
- **File transfer separation** — keep it on port 8991, its own package/table; don't couple it
  into the clipboard path.
- **DB schema** — additive only; `transfer_history` (v9) and all existing tables must keep
  working across upgrades.
- **Path-traversal protection** in `TransferStorage`/`TransferProtocol.sanitizeFileName` — do
  not weaken.
- **Only `MainActivity` exported**; service + `TransferActionReceiver` stay `exported="false"`.
- **Release signing config** — must keep resolving to `syncmesh-release.jks` (until keystore is
  properly rotated out of VCS) so update continuity holds.

---

# Summary (read this first)

**SyncMesh** is a **Java Android app** (module `:app`, package `com.ankit.syncmesh`, current
**v1.2 / code 12**) that **syncs clipboard text and transfers files between paired devices on
the same LAN with no cloud** — plus a bundled **HeliBoard keyboard** (`:keyboard_heliboard`
library module) that reliably sends the clipboard on Android 10+.

**Architecture:** singletons + LiveData, **no ViewModel/DI/Nav-component**. `AppRepository` is
the single source of truth (SQLite v9 + SharedPreferences + LiveData). `SyncCoordinator`
orchestrates networking. Three raw-socket channels: **TCP 8989** (clipboard + pairing, JSON
lines), **UDP 8990** (discovery), **TCP 8991** (file transfer, JSON control + raw bytes, 64 KB
streaming). A `dataSync` **foreground service** keeps it alive. The keyboard reaches app
singletons via **reflection** (`SyncMeshBridge`) because the dependency is one-way.

**Newest work (Phase 3):** a fully separated **file-transfer** subsystem (`transfer/` package,
port 8991, `transfer_history` table, dedicated UI + 5th "Transfer" bottom-nav tab) with
accept/reject, live progress (speed/ETA/pause/resume/cancel/retry), conflict-safe naming,
user-selectable SAF save location, and path-traversal-proof writes; plus **unpair propagation**,
a **hidden debug console** (7-tap version easter-egg), and a **Play Store update redirect**.

**Non-negotiables:** don't regress clipboard sync / pairing / the 8989 protocol / keyboard
bridge / foreground service; keep changes **additive** (new port/package/table/UI, unknown
wire types ignored by old peers); DB migrations additive only; use design tokens, never
hardcode colors/sizes/strings; keep network + DB off the main thread; don't commit/push unless
asked.

**Top risks / debt:** everything is **plaintext** (no encryption in transit or at rest) with
**weak pairing auth**; the **release keystore + passwords are committed** to the repo; a
**reflection bridge may break under R8** in release (verify keyboard auto-send); and
`refreshSnapshot` does **DB + NIC work on the main thread** (ANR risk). Full detail lives in
`summary/06_SECURITY_ANALYSIS.md`, `07_BUG_REPORT.md`, and `08_TECHNICAL_DEBT.md`.

To build: `./gradlew :app:assembleDebug` (dev) or `:app:assembleRelease` (signed, outputs in
`app/build/outputs/apk/release/`). Multi-device testing needs two real devices on one Wi-Fi
(emulators can't route to each other).
