# Phase 3 — File Transfer System — Progress Log

**Scope:** add a complete, production-quality local-network **file transfer** capability without
regressing any existing feature (clipboard sync, keyboard integration, pairing, discovery, the
wire protocol on port 8989, the database, or the foreground service). File transfer is an
**additive** subsystem on its **own port (8991)** with its **own package** (`transfer/`), its
**own DB table**, and its **own UI**. Nothing in the clipboard/pairing path was rewritten.

**Build/version:** `versionName` bumped `1.0 → 1.2`, `versionCode 1 → 12`
(`app/build.gradle.kts`). Debug builds green throughout (AGP 8.13, JDK 21).

---

## 1. Architecture

The transfer feature is deliberately separated into focused components, mirroring the existing
coordinator/repository/LiveData style of the app:

| Layer | Class | Responsibility |
|-------|-------|----------------|
| **Protocol** | `transfer/TransferProtocol` | Wire message builders/parsers, size/name validation, filename sanitization, constants (port, 2 GB cap, chunk size, timeouts). |
| **Engine (send)** | `transfer/FileTransferSender` | Streams one outgoing transfer over a single socket; per-chunk progress, pause/cancel checks. |
| **Engine (receive)** | `transfer/FileTransferServer` | Listens on 8991, validates offers, drives the accept handshake, streams incoming files to storage. |
| **Manager** | `transfer/FileTransferManager` | Singleton orchestrator: server lifecycle, the single active `TransferState` (LiveData), user controls (pause/resume/cancel/retry), incoming accept/reject latch, speed/ETA, history persistence, notifications, foreground-activity tracking. |
| **Storage** | `transfer/TransferStorage` | Conflict-safe destination files (MediaStore on API 29+, app-external on 26–28, or a user-chosen SAF tree); path-traversal-proof. |
| **Models** | `model/TransferFileInfo`, `model/TransferRecord`, `transfer/TransferState` | Data carriers + UI-facing progress snapshot. |
| **Repository** | `data/TransferRepository` | History persistence + search over the `transfer_history` table via LiveData. |
| **Notifications** | `util/NotificationHelper` (+ `transfer/TransferActionReceiver`) | Incoming request (Accept/Reject actions), ongoing progress, terminal result, unpair notice. |
| **UI** | `ui/FileTransferFragment` + `FileTransferActivity`, `ui/IncomingTransferActivity`, `ui/TransferHistoryActivity` + 3 adapters | Send flow, incoming dialog, live progress, searchable history. |

Transfer logic never touches `ClipboardSyncManager`/clipboard code; the only shared read is the
paired-device list (to populate the target picker and to authorize incoming senders).

## 2. New classes / files

**Java (transfer engine + data):**
- `transfer/TransferProtocol.java`, `transfer/TransferState.java`, `transfer/TransferStorage.java`
- `transfer/FileTransferSender.java`, `transfer/FileTransferServer.java`, `transfer/FileTransferManager.java`
- `transfer/TransferActionReceiver.java`
- `data/TransferRepository.java`
- `model/TransferFileInfo.java`, `model/TransferRecord.java`
- `util/UpdateManager.java`

**Java (UI):**
- `ui/FileTransferFragment.java` (hosted by the Transfer tab and by `FileTransferActivity`)
- `ui/FileTransferActivity.java`, `ui/IncomingTransferActivity.java`, `ui/TransferHistoryActivity.java`
- `ui/adapter/TransferFilesAdapter.java`, `TransferDevicesAdapter.java`, `TransferHistoryAdapter.java`

**Resources:** `layout/fragment_file_transfer.xml`, `activity_file_transfer.xml`,
`activity_incoming_transfer.xml`, `activity_transfer_history.xml`, `item_transfer_file.xml`,
`item_transfer_device.xml`, `item_transfer_record.xml`; drawables `ic_file`, `ic_upload`,
`ic_download`, `ic_warning`, `bg_warning_banner`; new strings; new colors `syncmesh_warning_soft`.

**Modified:** `SyncDatabaseHelper` (v9 + transfer table & CRUD), `SyncCoordinator` (server
start/stop + unpair message), `SyncForegroundService`/`SyncMeshApplication` (lifecycle hooks),
`NotificationHelper` (transfer channel + notifications), `AppPreferences` (transfer tour flag,
save-URI), `MainActivity`/`bottom_nav_menu.xml`/`main_top_app_bar_menu.xml`,
`DisplayUtils` (byte/speed/ETA formatters), `AndroidManifest.xml`, `app/build.gradle.kts`.

## 3. Protocol additions (new channel, port 8991)

Line-delimited JSON for control + raw bytes for payload. Independent of the 8989 clipboard
protocol.

```
sender → receiver : transfer_offer    {transferId, fromDeviceId, fromDeviceName,
                                        fileCount, totalSize, files:[{name,size}]}
receiver → sender : transfer_response  {transferId, accepted, message}
per file:
sender → receiver : file_header        {transferId, index, name, size}
sender → receiver : <size raw bytes>
receiver → sender : file_ack           {transferId, index, ok}
finally:
sender → receiver : transfer_complete  {transferId}
receiver → sender : transfer_result    {transferId}
cancel (either side): transfer_cancel  {transferId}  (best effort) + socket close
```

Also added a new **`unpair`** message on the existing 8989 channel (see §8). Both additions are
backward compatible — an older peer routes unknown `type`s to the existing
"Unhandled TCP message type" branch and ignores them.

## 4. Sender flow

Select files (SAF `OpenMultipleDocuments`) → metadata resolved (name + size via
`OpenableColumns`) and validated (2 GB cap, unreadable, too many) → confirmation dialog showing
file count and total size → choose a paired device (auto-selected when only one) → `Send`.
`FileTransferManager.sendFiles` claims the single session slot and runs `FileTransferSender`,
which connects, sends the offer, waits for accept, then streams each file in 64 KB chunks,
updating progress/speed/ETA ~2–3×/second.

## 5. Receiver flow

`FileTransferServer` accepts a socket, validates the offer (sender must be **paired**, sizes/
names/counts sane), then blocks on a `CountDownLatch` while the user decides. If the app is
foreground it launches `IncomingTransferActivity` (modern dialog listing sender, each filename +
size, and totals, with **Accept / Reject / Cancel**); either way a high-priority notification
with **Accept / Reject** actions is posted. Only on explicit Accept does streaming begin;
files are written conflict-safely and acked per file.

## 6. Transfer screen

Live UI shows overall progress + %, current file + its own progress bar, current speed, ETA,
transferred/remaining bytes, `N / M files` counter, connection status, peer name, and a status
badge. Controls: **Pause / Resume / Cancel** during transfer; **Retry / Done** on a terminal
state (Retry re-sends the last outgoing request). State lives in the manager, so the screen
survives rotation and tab changes, and a running transfer continues in the background under the
existing foreground service.

## 7. Storage, conflicts, security

- **Default location:** `Downloads/SyncMesh/` via MediaStore (API 29+, no permission) or the
  app-external `files/SyncMesh` dir (API 26–28).
- **User-selectable location:** a "Received files are saved to … / Change" row lets the user pick
  any folder via SAF (`OpenDocumentTree`); the tree URI is persisted with a durable permission
  grant and used through `DocumentFile`. Falls back to the default if the folder becomes
  inaccessible.
- **No overwrite ever:** `photo.jpg → photo (1).jpg → photo (2).jpg` across all three backends.
- **Security gates:** incoming sender must be a paired device; every offer and every per-file
  header is validated (size ≤ 2 GB, count match, total match); filenames are sanitized (path
  components stripped, illegal/control chars removed, dot-only names rejected, length bounded) so
  **path traversal is impossible** — MediaStore/SAF only ever receive a display name, and the
  legacy path is canonical-checked against the target dir.
- **Streaming only:** 64 KB chunks; files are never fully loaded into RAM; partial files are
  deleted on failure/cancel.

## 8. Unpair propagation (requested fix)

Removing a device now calls `SyncCoordinator.removePairedDeviceAndNotify`, which deletes the
local row **and** best-effort sends an `unpair` message to the peer. The peer's
`handleUnpair` removes the initiator from its own device list and posts a
"Device unpaired — <name> removed the pairing" notification, so an unpair on one side propagates
to the other. Removal is applied locally regardless of whether the peer is reachable.

## 9. Entry points / navigation (requested design)

- A permanent **Transfer** bottom-navigation tab (5 tabs: Home, Pair, Devices, Transfer,
  History), hosting `FileTransferFragment`. It is always visible.
- A one-time **transfer sub-tour** runs the first time the Transfer tab is opened (spotlights
  Select files → choose device → Send → Transfer history), tracked by a new
  `transfer_tour_complete` preference and reusing the existing `TourOverlayView`.
- When no devices are paired, the "Send to" section shows a friendly empty state with a
  **Pair a device** button that jumps to the Pair tab.
- Send-files / Transfer-history were **removed from the toolbar overflow**; the tab is the entry
  point. Notifications deep-link into `FileTransferActivity` (which hosts the same fragment).

## 10. App updates (requested)

`util/UpdateManager` uses Google Play's in-app-update API to detect a newer version on launch;
if one is available it redirects to the Play Store listing
(`https://play.google.com/store/apps/details?id=com.ankit.syncmesh`, via `market://` with a
browser fallback). A manual **Check for updates** overflow item always opens the listing. On
sideloaded/debug installs the Play check reports no update and does nothing.

## 11. Database changes

`SyncDatabaseHelper` bumped to **v9** (additive). New `transfer_history` table
(`transfer_id, direction, peer_device_id, peer_device_name, file_count, total_size, status,
started_at, duration_ms, file_names`) created in `createTables` (so it is created on both fresh
installs and upgrades via the existing `ensureSchema` path). Existing tables/columns untouched;
no column was renamed or dropped.

## 12. Background / lifecycle

The transfer server starts/stops with the sync runtime (`SyncCoordinator.start/stopRuntime`),
wrapped in try/catch so a transfer-server failure can never bring down clipboard sync. Active
transfers keep running while the screen is off / the app is backgrounded because the existing
`dataSync` foreground service keeps the process alive; progress is mirrored to an ongoing
notification. `FileTransferManager.registerForegroundTracker` (registered in
`SyncMeshApplication`) decides dialog-vs-notification for incoming requests.

## 13. Testing performed

- **Builds:** `:app:assembleDebug` green after every change set.
- **Cross-device pairing (two emulators, emulator-5554 ↔ emulator-5558):** genuine pairing using
  the second device's real pairing code through the real Pair button was verified — the receiver
  validated its own code and both sides stored each other. Because standard AVDs all self-report
  the NAT address `10.0.2.15` and cannot route to each other's LAN IP, the peer address was
  bridged to the host loopback (`10.0.2.2`) with `adb forward` so the two app instances could
  actually exchange bytes; the transfer data path itself is the unmodified app code.
- **Transfer:** confirmed working end-to-end by the user across the two emulators (send + receive).
- **UI:** Transfer tab visibility, empty-state "Pair a device", save-location row, incoming
  dialog, and progress screen verified on-device via screenshots.

> Note: per the user's instruction, the final unpair-propagation, version-bump, and update-check
> changes were implemented but **not** re-exercised on device in this session; they compile
> cleanly and are additive.

## 14. Regression posture

- No clipboard/pairing/discovery code path was modified except additive hooks (transfer server
  start/stop, the new `unpair` message type). The clipboard `TcpServer` (8989) and its handlers
  are unchanged.
- New wire types are additive and ignored by older peers.
- DB migration is additive (v9, new table only).
- The reflection bridge surface (keyboard) is untouched; existing `-keep` rules still apply.

## 15. Known limitations

- **Emulator networking:** two default AVDs cannot route to each other's real IP; genuine
  two-device transfer needs real devices on the same Wi-Fi (or the host-bridge used here).
- **One transfer at a time** per device (single session slot); a second concurrent incoming
  offer is rejected as busy. Self-transfer (send to a row pointing back at the same device) is
  therefore not supported.
- **Pause** holds the socket open; a very long pause can hit the data socket timeout.
- **No resume across disconnect:** a dropped connection fails the transfer (Retry re-sends from
  the start); there is no partial-resume yet.
- Play in-app update detection only works for Play-installed builds.

## 16. Future improvements

- Folder transfer (the models/protocol already carry per-file entries; add tree walking).
- Partial-resume / chunk-level acknowledgement for very large files over flaky links.
- Multiple concurrent transfers (per-peer session slots).
- Transfer-history filters (direction/status/date) — the search field and schema are ready.
- Encrypt the transfer channel (shares the same plaintext-LAN posture as clipboard sync).
- Thumbnails/previews for images and richer per-file status in the incoming dialog.
