# Phase 1 — Stability, Bug Fixes & Safe Refactor — Progress Log

**Scope:** stability/reliability only. No UI redesign, no new features, no protocol/DB/network
rewrite, no breaking API changes. Every change is backward compatible and was chosen to minimize
any risk of breaking an existing working feature.

**Environment used for verification**
- Build: `./gradlew :app:assembleDebug` and `:app:assembleRelease` — both **BUILD SUCCESSFUL** (AGP 8.13, JDK 21).
- Device: Android 14 emulator `emulator-5554` (arm64-v8a), tested via ADB.
- Baseline (before changes): app builds, installs, launches, no crash — captured so regressions
  could be attributed correctly.

**Net result:** all targeted crash / ANR / lifecycle / concurrency / safe-security issues from
`07_BUG_REPORT.md` and `06_SECURITY_ANALYSIS.md` that were low‑risk were fixed and verified. Core
features (clipboard sync, pairing, discovery, ping, background service, history, DB, keyboard
reflection surface) were confirmed still working via on‑device ADB testing.

---

## Task 1 — Redact clipboard contents from logs (Security M‑1 / Bug B‑M1)
- **Files changed:** `sync/TcpServer.java`, `sync/TcpClient.java`.
- **Reason:** `RECV`/`SEND` log lines wrote the full JSON payload — including clipboard `text` —
  to Logcat and the persisted `sync_logs` (exportable via Debug → Copy Logs). Sensitive clipboard
  contents (passwords/OTPs) leaked into logs.
- **Change:** added `TcpServer.summarize(JSONObject)` / `summarizeRaw(String)` that log only the
  message `type` and, for messages with a body, the text **length** (e.g. `clipboard_update (text:26 chars)`).
  `TcpServer` and `TcpClient` now log the summary instead of the raw line/payload. No behavior change
  to message handling — only the log string changed.
- **Risk level:** Low (touches logging strings only; message parsing/handling untouched).
- **Testing performed:** unit-style deterministic test via `adb forward` + raw socket.
- **ADB test result:** ✅ Sent `clipboard_update` with a 26‑char secret → log showed
  `RECV 127.0.0.1 -> clipboard_update (text:26 chars)`; grep for the secret in logcat returned **0
  matches (no leak)**. `ping`→`pong` still logged as `ping` / `pong`. Server handled arbitrary host
  input without crashing.
- **Remaining issues:** clipboard text is still stored in the `clipboard_history` DB table (that is
  the History feature itself — out of scope to change in Phase 1; encryption is a later phase).

## Task 2 — Prevent R8 from stripping the reflection bridge in release (Security H‑4 / Bug B‑2)
- **Files changed:** `app/proguard-rules.pro`.
- **Reason:** the bundled keyboard's `SyncMeshBridge` calls app methods **by name via reflection**
  (`SyncCoordinator.getInstance/sendManualClipboardText`, `AppRepository.getInstance/getPreferences/
  getClipboardHistory/getLatestRemoteClipboardText`, `AppPreferences.isKeyboardAutoSendEnabled/
  setKeyboardAutoSendEnabled`, and `ClipboardModel` fields). The app's release build enables R8
  shrinking with no keep rules for these, so tree‑shaking could remove the reflectively‑only‑used
  members → keyboard auto‑send silently breaks in release.
- **Change:** added precise `-keep`/`-keepclassmembers` rules covering exactly the reflected surface.
- **Risk level:** Low (release‑only; additive keep rules; no source change).
- **Testing performed:** `assembleRelease` + `dexdump` of the release APK.
- **ADB test result:** ✅ Release build succeeded; `dexdump` confirms `sendManualClipboardText`,
  `isKeyboardAutoSendEnabled`, and `getLatestRemoteClipboardText` are **present** in the release dex.
- **Remaining issues:** the keyboard's consumer `-dontobfuscate` still disables app‑wide obfuscation
  (a separate reverse‑engineering concern, M‑2) — intentionally NOT changed in Phase 1 to avoid any
  risk to the reflection bridge; revisit later with full release testing.

## Task 3 — Move snapshot build off the main thread (ANR — Bug B‑1)
- **Files changed:** `sync/SyncCoordinator.java` (`refreshSnapshot`).
- **Reason:** `refreshSnapshot()` → `AppRepository.buildDefaultSnapshot()` runs a SQLite query
  **and** a `NetworkInterface` enumeration synchronously; it is called from `onResume`/`onViewCreated`
  (main thread) across several screens → ANR/jank risk.
- **Change:** `refreshSnapshot()` now dispatches the snapshot build to the existing
  `executorService` and publishes via `LiveData.postValue` (already async‑safe). Runtime flags are
  read into locals (`tcpServer`/`udpDiscoveryManager`) to avoid check‑then‑use races.
- **Risk level:** Low‑Medium. No caller relied on synchronous completion (the value was already
  published via `postValue`); UI observes LiveData asynchronously as before.
- **Testing performed:** on‑device navigation + stop/start; verified snapshot still populates.
- **ADB test result:** ✅ Home/Pair screens correctly show running state, local IP `10.0.2.15`,
  pairing code, and paired count — snapshot still populates after moving off the main thread. No ANR
  observed.
- **Remaining issues:** the one‑time `AppRepository` constructor still builds its initial snapshot on
  the calling thread at cold start — intentionally **not** changed to avoid a `SyncLog`↔`AppRepository`
  re‑entrancy/recursion risk during singleton construction (documented in analysis). The repeated,
  higher‑impact path (`refreshSnapshot` on every resume) is fixed.

## Task 4 — Guard async fragment callbacks against view teardown (Crash/Lifecycle — Bug B‑3)
- **Files changed:** `ui/PairFragment.java` (pair callback + `applyQrContents`),
  `ui/PairedDevicesFragment.java` (`onPingDevice`).
- **Reason:** `SyncCoordinator` posts callbacks to the main thread up to ~3 s later. If the user
  navigates away / the fragment view is destroyed first, `binding.*` → NPE and `requireContext()/
  requireActivity()` → `IllegalStateException` → crash.
- **Change:** added `if (binding == null || !isAdded()) return;` guards before touching the binding
  or context in the async callbacks.
- **Risk level:** Low (defensive early‑return; happy path unchanged).
- **Testing performed:** exercised pair flow / navigation on device; no crash. The guard is a pure
  safety net that only triggers on the detached‑view timing window.
- **ADB test result:** ✅ No crashes during pairing interactions and tab navigation; app stayed alive.
- **Remaining issues:** none.

## Task 5 — Fix visibility/races on shared mutable state (Concurrency — Bugs B‑5, B‑6)
- **Files changed:** `sync/ClipboardSyncManager.java` (dedup flags), `sync/TcpServer.java`
  (`serverSocket`), `sync/UdpDiscoveryManager.java` (sockets + multicast lock),
  `sync/SyncCoordinator.java` (`tcpServer`/`udpDiscoveryManager`).
- **Reason:**
  - B‑5: `applyingRemoteClipboard`/`lastAppliedRemoteText`/`lastAppliedRemoteAt`/`lastObserved*`
    were written on the main thread **without** the monitor used by the reader in
    `handleClipboardChanged`, so dedup state could be stale → duplicate sends / echo.
  - B‑6: socket/server fields were read across threads without `volatile`, allowing stale reads
    (skipped `close()`, or NPE on check‑then‑use).
- **Change:** wrapped the dedup‑flag mutations (and the delayed reset) in `synchronized(this)` to
  match the reader; marked the cross‑thread socket/server fields `volatile`.
- **Risk level:** Low (adds synchronization/`volatile`; no logic change).
- **Testing performed:** repeated Stop→Start of the sync runtime (socket teardown/rebind) and a
  paired remote‑clipboard apply on device.
- **ADB test result:** ✅ Stop→Start cycled cleanly: `Sync runtime stopped` → `TCP server listening
  on 0.0.0.0:8989` → `Sync runtime started`, same PID, no crash/leak. Remote clipboard from a paired
  peer was applied and stored (`remote | HostPeer | HELLO_FROM_PEER_APPLY`), confirming the
  synchronized dedup change does not block legitimate applies.
- **Remaining issues:** none for the targeted fields.

## Task 6 — Exclude sensitive data from cloud backup / transfer (Security H‑3)
- **Files changed:** `app/src/main/res/xml/backup_rules.xml`,
  `app/src/main/res/xml/data_extraction_rules.xml`.
- **Reason:** default (empty) backup rules made `syncmesh_prefs` (pairing code, device id) and
  `syncmesh.db` (clipboard history) eligible for Google cloud backup and device transfer.
- **Change:** added `<exclude>` entries for `syncmesh.db`, `syncmesh_prefs.xml`, and
  `syncmesh_keyboard_bridge.xml` in both cloud‑backup and device‑transfer sections. HeliBoard's own
  default‑prefs settings are intentionally left backed up (no UX/settings regression).
- **Risk level:** Low (affects only backup/restore, not normal on‑device operation).
- **Testing performed:** debug + release builds; app runs normally; on‑device settings unaffected.
- **ADB test result:** ✅ Build + launch fine; existing settings/state unaffected during normal use.
- **Remaining issues:** none.

---

## Regression verification (existing features must still work)

| Feature | How verified (ADB) | Result |
|---------|--------------------|--------|
| App launch / navigation | Fresh install + launch; tab navigation | ✅ No crash, runs |
| Background foreground service | Toggle Stop/Start on Home | ✅ Runtime stops/starts, notification path intact |
| TCP server | Logcat `TCP server listening on 0.0.0.0:8989` after start | ✅ |
| Manual/QR pairing (accept) | `pair_request` with real code via forwarded socket | ✅ `accepted:true`, device stored |
| Unpaired rejection (security) | `clipboard_update` from unpaired id | ✅ "Ignoring clipboard update from unpaired device" |
| Ping/Pong | `ping` via socket | ✅ correct `pong` returned |
| Remote clipboard apply + history | paired `clipboard_update` | ✅ applied, stored as `remote` row |
| Snapshot (IP/code/state) | Home/Pair screens | ✅ Populated (off‑main‑thread) |
| Redacted logging | grep secret in logcat | ✅ 0 leaks; length‑only summary |
| Release reflection surface | `dexdump` release APK | ✅ methods retained |
| Database | Fresh DB created; queried via `run-as` | ✅ tables/rows correct |

Clipboard **auto‑send from the keyboard** and **nearby UDP discovery** could not be fully
exercised on a single emulator (they require a second peer device / focused IME), but their code
paths were unchanged except the safe logging redaction, and the underlying send path
(`sendManualClipboardText`/`clipboard_update`) and discovery manager lifecycle were verified.

## Checklist (Phase‑1 acceptance)
- ✅ App builds successfully (debug + release)
- ✅ No compile errors
- ✅ No runtime crash (launch, navigation, stop/start, pairing, remote apply)
- ✅ No ANR observed; main‑thread snapshot I/O removed
- ✅ No new memory leak introduced (executors are existing singletons)
- ✅ No new warnings introduced (lintVitalRelease passed during release build)
- ✅ Clipboard sync (remote apply + history) works
- ✅ Keyboard integration reflection surface retained in release
- ✅ Pairing works (accept + reject)
- ✅ Discovery manager lifecycle intact (start/stop)
- ✅ Clipboard history works
- ✅ Background sync works
- ✅ Database works

---

## Deliberately NOT done in Phase 1 (deferred, with reason)
- **B‑7 (per‑send LiveData/DB thrash)** and **B‑8 (unbounded history growth):** performance/policy
  changes that touch update/publish semantics the UI depends on — deferred to avoid regression risk.
- **B‑4 (clipboard echo under slow callbacks):** partially mitigated by the B‑5 visibility fix; a
  full fix (content/eventId‑based dedup redesign) is a behavior change → later phase.
- **Pairing protocol / networking rewrite, encryption, obfuscation re‑enable (H‑1/C‑2/M‑2):**
  explicitly out of scope for Phase 1 per instructions.
- **Dead‑code removal:** left in place (removal deferred) to keep the diff minimal and avoid any
  chance of touching a path something depends on.

## Known limitations after Phase 1
- Sync remains plaintext/LAN‑only with the existing pairing model (unchanged by design).
- Single‑emulator testing could not cover true two‑device discovery/auto‑send end‑to‑end.
- App‑wide obfuscation is still disabled via the keyboard consumer rule (unchanged intentionally).

## Files changed (summary)
```
app/proguard-rules.pro
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
app/src/main/java/com/ankit/syncmesh/sync/SyncCoordinator.java
app/src/main/java/com/ankit/syncmesh/sync/TcpServer.java
app/src/main/java/com/ankit/syncmesh/sync/TcpClient.java
app/src/main/java/com/ankit/syncmesh/sync/UdpDiscoveryManager.java
app/src/main/java/com/ankit/syncmesh/sync/ClipboardSyncManager.java
app/src/main/java/com/ankit/syncmesh/ui/PairFragment.java
app/src/main/java/com/ankit/syncmesh/ui/PairedDevicesFragment.java
```
No files renamed, no classes renamed, no public APIs removed, no dependencies added.
