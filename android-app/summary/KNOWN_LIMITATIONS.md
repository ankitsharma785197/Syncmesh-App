# Known Limitations

Design/scope limitations that are **verified from code** — these are how the app currently is, not
necessarily bugs. Bugs are in `07_BUG_REPORT.md`; security issues in `06_SECURITY_ANALYSIS.md`.

## Transport & connectivity
- **LAN‑only, same broadcast domain.** Sync works only when devices share a Wi‑Fi/hotspot subnet.
  No internet relay, no NAT traversal, no cross‑network sync (`TcpServer`/`UdpDiscoveryManager`,
  `home_subtitle` string confirms intent).
- **Bluetooth transport is not implemented.** The `transport`/`bluetooth_address` columns and
  `PairedDevice.bluetoothAddress` exist, and strings advertise "Bluetooth — Coming next", but no
  Bluetooth code exists. `transport` is always `"wifi"`.
- **One request per TCP connection.** No persistent connections/keep‑alive; a new socket per message.
- **Fixed 3 s timeouts, no retry/backoff** (`TcpClient.TIMEOUT_MS`). Transient failures surface immediately.
- **Discovery depends on broadcast delivery.** Some APs/interfaces drop broadcasts or return no
  `getBroadcast()`; discovery can silently fail (B‑10).

## Data types & scope
- **Text clipboard only.** Only `CharSequence` clipboard content is synced (`coerceToText`); images,
  files, rich data, and multi‑item clips are not supported.
- **No conflict resolution / ordering guarantees.** Last write to the clipboard wins; concurrent
  copies on multiple devices race.
- **Clipboard history is unbounded** and never auto‑pruned (only manual Clear). Logs are capped at 250.

## Security model (see `06`)
- **No encryption, weak pairing, spoofable identity, plaintext storage/logs, backup‑eligible data.**
- **Pairing code never expires or rotates.**

## Platform behaviour
- **Android 10+ background clipboard restriction.** The auto listener only reliably fires while the
  app or the bundled keyboard has focus; hence the keyboard add‑on exists (`home_keyboard_summary`).
- **Foreground service required** for sync; `dataSync` FGS on Android 14+ has a ~6 h timeout
  (`onTimeout` → `stopSelf`), after which sync stops until restarted.
- **`minSdk 26`** for the app (keyboard module `minSdk 21`, but the merged app uses 26).

## UI / feature completeness
- **No ViewModel, no Navigation component** — manual fragment swapping; state re‑read from DB on resume.
- **Remote clipboard applies silently** (notification code disabled — B‑9).
- **History pin/delete not exposed** in the app UI though DB support exists (B‑13).
- **Device rename not exposed** — `AppPreferences.setDeviceName` exists but no UI calls it; name is
  fixed to `MANUFACTURER MODEL`.
- **Keyboard toolbar SyncMesh actions (Send/Paste/History/Open App)** are described by strings and
  bridge methods but **not wired** into the keyboard UI — only auto‑send works.
- **Accessibility‑based clipboard capture** is scaffolded but unused (no accessibility service exists).

## Build / distribution
- **Release build disables obfuscation app‑wide** via the keyboard consumer proguard (`-dontobfuscate`).
- **ABI splits** produce per‑ABI APKs (+ universal), disabled when building a bundle.
- **GPL‑3.0 obligations** from the vendored HeliBoard apply to the distributed app.

## Testing
- **No automated tests in the app module.** Correctness relies on manual, two‑device testing.
