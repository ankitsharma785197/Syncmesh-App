# SyncMesh Desktop

SyncMesh Desktop is an Electron companion app for the SyncMesh Android app. It syncs clipboard text over the local WiFi/LAN only. There is no cloud server.

## Protocol

- TCP server: `0.0.0.0:8989`
- UDP discovery: port `8990`
- Message format: JSON object followed by `\n`
- Supported TCP messages:
  - `pair_request`
  - `pair_response`
  - `clipboard_update`
  - `ping`

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
- Both devices must be on the same local network and able to reach TCP `8989` and UDP `8990`.
