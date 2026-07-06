# 12 — Change Guide

A pre‑modification reference: for common change types, which files are affected, the risk level,
dependencies to watch, testing required, and likely side effects. Use alongside `10_FILE_INDEX.md`.

> Golden rule for this codebase: **the wire protocol, the reflection bridge, and the DB schema are
> the three "spooky action at a distance" areas.** A change in one silently affects the other side
> of a socket, the keyboard module, or existing installs.

---

## 1. Changing the wire protocol (message `type`, field names, ports)
- **Affected:** `SyncCoordinator` (builders + handlers), `UdpDiscoveryManager`, `TcpServer/Client`,
  `PairFragment` (QR JSON), and **the keyboard `SyncMeshBridge`** if payload‑shaping changes.
  Field‑name literals are duplicated across these files (no shared constants).
- **Risk:** 🔴 High — both peers must agree; there is **no protocol version field**, so a change
  breaks interop with any un‑updated device. Ports `8989/8990` are hardcoded in multiple places.
- **Dependencies:** cross‑device compatibility; QR format (`syncmesh_pair_qr`).
- **Testing:** two‑device manual test (pair, copy, ping, discovery) on old↔new; add JSON round‑trip tests.
- **Side effects:** silent pairing/sync failures if only one side updates.

## 2. Changing the SQLite schema (`SyncDatabaseHelper`)
- **Affected:** `SyncDatabaseHelper` (`createTables`/`ensureSchema`/`ensureColumn`), all cursor
  mappers in `SyncDatabaseHelper` **and** `AppRepository` (`readDevice`/`readClipboardEntry`/
  `readLogEntry`), plus any model field the keyboard bridge reads by reflection (`text`,
  `direction`, `sourceDeviceName`, `createdAt`).
- **Risk:** 🟠 Medium — migrations are additive‑only via `ensureColumn`; **removing/renaming a
  column is unsupported** and will break `getColumnIndexOrThrow`. Bump `DATABASE_VERSION` and add an
  `ensureColumn` for new columns.
- **Testing:** install‑over‑upgrade from a prior DB; verify no `IllegalArgumentException` from cursor lookups.
- **Side effects:** the keyboard bridge reads model **field names** via reflection — rename a Java
  field and `getClipboardHistory` (if ever wired) breaks.

## 3. Changing the keyboard↔app bridge (`SyncMeshBridge`) or the methods it reflects
- **Affected:** `SyncMeshBridge` (keyboard) and the reflected app members: `SyncCoordinator.getInstance`,
  `SyncCoordinator.sendManualClipboardText(String, ActionCallback)`, `SyncCoordinator$ActionCallback`,
  `AppRepository.getInstance/getPreferences/getClipboardHistory/getLatestRemoteClipboardText`,
  `AppPreferences.isKeyboardAutoSendEnabled/setKeyboardAutoSendEnabled`, and model fields.
- **Risk:** 🔴 High — reflection is **stringly‑typed**; renames compile fine but fail at runtime.
  R8 shrinking can also remove reflectively‑only members in release (see B‑2). No `-keep` protects them.
- **Dependencies:** `app/proguard-rules.pro` (add `-keep` before relying on release behavior).
- **Testing:** **release build** smoke test of keyboard auto‑send, not just debug.
- **Side effects:** breakage is silent ("SyncMesh send failed" toast).

## 4. Changing clipboard capture / dedup logic (`ClipboardSyncManager`)
- **Affected:** `ClipboardSyncManager` (listener, dedup windows, apply‑remote), `SyncCoordinator`
  (`handleLocalClipboardChanged`, `handleRemoteClipboard`).
- **Risk:** 🟠 Medium — timing‑sensitive; the 2 s/30 s windows and the `applyingRemoteClipboard`
  flag guard against echo loops (see B‑4/B‑5). Small changes can create duplicate sends or loops.
- **Testing:** two‑device copy round‑trip, rapid repeated copies, remote‑then‑local sequences.
- **Side effects:** duplicate history rows; ping‑pong between devices.

## 5. Adding a new screen / navigation destination
- **Affected:** `MainActivity.onNavigationItemSelected` + `bottom_nav_menu.xml` (add item), or a new
  standalone activity (follow the `*Activity` + `applyWindowInsets` pattern) + manifest entry.
- **Risk:** 🟡 Low — self‑contained. Remember there is **no Nav component**; navigation is manual
  `FragmentTransaction.replace`.
- **Testing:** manual navigation, rotation (fragment re‑creation), inset behavior with IME.
- **Side effects:** duplicated inset boilerplate grows (consider a base activity).

## 6. Changing the foreground service lifecycle
- **Affected:** `SyncForegroundService`, `NotificationHelper`, `HomeFragment` (start/stop),
  `SyncCoordinator.start/stopRuntime`, manifest (`foregroundServiceType`, `stopWithTask`).
- **Risk:** 🟠 Medium — Android 14+ FGS type rules (`dataSync`, 6 h timeout via `onTimeout`),
  notification permission (API 33+), and `START_STICKY`/`stopWithTask=false` semantics all interact.
- **Testing:** start/stop, swipe‑away behavior, notification action, Android 13 vs 14+ devices.
- **Side effects:** service may be killed or resurrected unexpectedly; battery.

## 7. Touching persistence/refresh paths (`AppRepository`)
- **Affected:** `AppRepository` publish helpers (`postPairedDevices`/`postClipboardHistory`/
  `postLogs`/`publishNearbyDevices`), snapshot building, and every observing fragment.
- **Risk:** 🟠 Medium — many callers; snapshot/refresh currently run on the main thread (B‑1).
  Changing publish frequency affects adapter rebinds (all use `notifyDataSetChanged`).
- **Testing:** verify LiveData still emits on the right threads; watch for main‑thread I/O.
- **Side effects:** performance regressions; missed UI updates if `postValue` timing changes.

## 8. Changing logging (`SyncLog`)
- **Affected:** `SyncLog` and `AppRepository.addLog` (cycle), the `sync_logs` table, `DebugFragment`.
- **Risk:** 🟡 Low functionally, but note logging writes to SQLite on the **caller's thread**
  (including socket threads) and currently logs full clipboard text (privacy — M‑1).
- **Testing:** debug console shows entries; no excessive DB writes under load.

## 9. Build config changes (signing, minify, SDK, ABI splits)
- **Affected:** `app/build.gradle.kts`, `gradle.properties`, `proguard-rules.pro`.
- **Risk:** 🔴 High for signing (secrets in VCS — C‑1) and 🟠 Medium for minify (bridge reflection — B‑2).
- **Testing:** produce a **release** APK/AAB and smoke‑test keyboard auto‑send + core flows;
  verify ABI split/universal APK still installs.
- **Side effects:** release‑only breakage that debug builds won't reveal.

## 10. Upgrading / modifying the keyboard module
- **Affected:** the entire `keyboard_heliboard` subtree; only the `syncmesh/` package and the
  single `LatinIME.java:882` call are SyncMesh‑specific.
- **Risk:** 🟠 Medium — a HeliBoard upstream merge could move `onStartInputView` or the bridge hook;
  re‑apply the `SyncMeshBridge.autoSendClipboardIfNeeded` call and keep `applicationId` forced to
  `com.ankit.syncmesh`. Watch GPL‑3.0 obligations.
- **Testing:** keyboard enable/select, settings open, auto‑send; full app build (NDK/Compose).

---

## General testing checklist (any change)
1. Build **both** debug and release (release catches shrink/reflection issues).
2. Two‑device LAN test: discovery → pair (manual + QR + nearby) → copy round‑trip → ping.
3. Rotation + tab switching during an in‑flight pair/ping (lifecycle — B‑3).
4. Upgrade‑over‑install to validate DB migration.
5. Verify no main‑thread I/O regressions (enable StrictMode in debug).
