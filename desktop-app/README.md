# SyncMesh Desktop

SyncMesh Desktop **v2.0** is an Electron companion app for the SyncMesh Android app (v1.2). It syncs clipboard text and transfers files over the local WiFi/LAN only. There is no cloud server.

## Protocol

- TCP server: `0.0.0.0:8989` (clipboard + pairing)
- UDP discovery: port `8990`
- File transfer TCP server: `0.0.0.0:8991`
- Message format: JSON object followed by `\n`
- Supported TCP messages on 8989:
  - `pair_request`
  - `pair_response`
  - `clipboard_update`
  - `unpair`
  - `ping`
- File transfer messages on 8991 (wire-compatible with the Android app):
  - `transfer_offer` → `transfer_response` → per file `file_header` + raw bytes + `file_ack` → `transfer_complete` → `transfer_result`; `transfer_cancel` from either side
  - 64 KB streaming chunks, 2 GB per-file cap, max 500 files per offer

UDP discovery announces every 3 seconds:

```json
{
  "type": "discovery_announce",
  "deviceId": "desktop_device_uuid",
  "deviceName": "MacBook / Windows PC",
  "ipAddress": "local_ipv4",
  "port": 8989,
  "platform": "desktop",
  "timestamp": 123456789
}
```

## Setup

```bash
npm install
npm start
```

The SQLite database is stored in Electron's user data directory as `syncmesh-desktop.sqlite`.

## Build

Windows installer:

```bash
npm run build:win
```

macOS DMG:

```bash
npm run build:mac
```

Build output is written to `dist/`.

## App Features

- System tray/menu bar app with start sync, stop sync, open dashboard, and quit.
- Clipboard watcher for Windows and macOS desktop text clipboard.
- Sends `clipboard_update` to paired Android devices.
- Receives Android `clipboard_update` and writes to the desktop clipboard.
- Anti-loop duplicate protection with `eventId` and in-memory recent event cache.
- Local SQLite clipboard history.
- Manual IP pairing with pairing code.
- QR payload for Android scan support.
- Nearby devices list from UDP discovery.
- Rejects clipboard updates from unpaired devices.
- Debug logs in the dashboard.
- **File transfer** (v1.2): send any files (≤2 GB each) to a paired Android device and receive files from it; accept/reject dialog, live progress with speed/ETA, pause/resume/cancel/retry, conflict-safe naming (`photo.jpg` → `photo (1).jpg`), searchable local history.
- **Drag & drop** (v1.2): drop files anywhere in the window (or on the drop zone) to stage them for sending.
- Received files are saved to `~/Downloads/SyncMesh/`.
- **Unpair propagation** (v1.2): removing a paired device notifies the peer so it drops the pairing too, and vice versa.
- **macOS-native UI** (v1.2): hidden inset title bar, sidebar vibrancy, system font, mac-style controls.

## SQLite Tables

```sql
devices (
  id,
  device_id,
  device_name,
  ip_address,
  port,
  platform,
  paired_at,
  last_seen,
  last_error
)
```

```sql
clipboard_history (
  id,
  event_id,
  text,
  source_device_id,
  source_device_name,
  direction,
  created_at
)
```

```sql
settings (
  key,
  value
)
```

```sql
transfer_history (
  id,
  transfer_id,
  direction,
  device_id,
  device_name,
  file_count,
  total_size,
  transferred_bytes,
  status,
  message,
  files_json,
  created_at
)
```

## Pairing

Manual desktop-to-Android pairing:

1. Start SyncMesh Android and SyncMesh Desktop on the same LAN.
2. Open `Pair Device`.
3. Enter the Android IP address, TCP port `8989`, and the Android pairing code.
4. Click `Pair Device`.

Android-to-desktop pairing:

1. Open SyncMesh Desktop.
2. Use the desktop pairing code shown on the dashboard, or scan the desktop QR code if the Android app supports it.
3. Send `pair_request` to the desktop TCP server with the pairing code.

The desktop saves accepted devices in SQLite. Clipboard updates from devices not in SQLite are rejected.

## Testing Checklist

1. Start Android SyncMesh.
2. Start desktop SyncMesh with `npm start`.
3. Pair desktop with Android.
4. Copy text on desktop.
5. Verify Android receives the clipboard text.
6. Copy text on Android using the SyncMesh/keyboard flow.
7. Verify desktop clipboard updates.
8. Open `Clipboard History` and verify incoming/outgoing events.
9. Open `Debug Logs` if messages are not flowing.

## Notes

- macOS may require Accessibility or clipboard permissions depending on OS policy.
- Windows Defender Firewall or macOS firewall may prompt for LAN access; allow local network traffic for SyncMesh Desktop.
- Both devices must be on the same local network and able to reach TCP `8989`, UDP `8990`, and TCP `8991` (file transfer).
