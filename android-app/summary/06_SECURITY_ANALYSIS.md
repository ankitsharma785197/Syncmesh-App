# 06 — Security Analysis

Severity scale: **Critical / High / Medium / Low**. Every item cites source.

> Context: SyncMesh moves clipboard contents (which frequently include passwords, OTPs, 2FA
> codes, and personal data) between devices on a shared LAN with **no encryption and weak
> authentication**. This materially raises the impact of the findings below.

---

## CRITICAL

### C‑1. Release signing keystore and passwords committed to the repository
- **Where:** `syncmesh-release.jks` (binary keystore at repo root), `app/build.gradle.kts:9-14`
  (`storePassword = "707089Ankit"`, `keyPassword = "707089Ankit"`, `keyAlias = "syncmesh"`),
  and `gradle.properties` (`SYNC_KEYSTORE_PASSWORD`, `SYNC_KEY_PASSWORD`, `SYNC_KEY_ALIAS` in plaintext).
- **Impact:** Anyone with repo access can sign APKs as this app's publisher identity, enabling
  malicious updates / impersonation. The keystore cannot be rotated without breaking update
  continuity. This is the single most serious issue.
- **Fix direction:** Remove keystore + passwords from VCS and history, rotate the key, inject
  signing config from a secure CI secret / `~/.gradle` outside the repo.

### C‑2. Clipboard data transmitted and stored entirely in plaintext
- **Where:** `TcpClient`/`TcpServer` (no TLS), `AndroidManifest.xml:26` `usesCleartextTraffic="true"`,
  `clipboard_history` table stores full text, `SyncLog`/`AppRepository.addLog` persist full
  RECV/SEND lines (`TcpServer.handleClient` logs `line`, `TcpClient` logs payloads).
- **Impact:** Passive network attackers on the same Wi‑Fi/hotspot can read every synced clipboard
  (passwords, OTPs). Clipboard contents are also written to the `sync_logs` table and Logcat,
  and are eligible for cloud backup (see H‑3). No at‑rest or in‑transit protection.
- **Fix direction:** Encrypt payloads (e.g. authenticated encryption keyed by a shared secret
  derived during pairing), stop logging clipboard text, redact in logs.

---

## HIGH

### H‑1. Weak, brute‑forceable pairing with no rate limiting and self‑asserted identity
- **Where:** `SyncCoordinator.handlePairRequest` (`accepted = localPairingCode.equals(incomingCode)`),
  `handleRemoteClipboard` (authorization = `isPairedDevice(fromDeviceId)` where `fromDeviceId`
  is an unverified JSON field), `AppPreferences.getPairingCode` (6‑digit numeric).
- **Impact:**
  - The pairing code space is only 10^6 and there is **no attempt throttling, lockout, or
    expiry** — an on‑network attacker can brute‑force pairing (one TCP round trip per guess).
  - Once any paired device's UUID is known/guessed, clipboard‑update authorization is trivially
    spoofed (the `deviceId` is not cryptographically bound). Discovery announcements leak device
    UUIDs on the LAN, making them easy to harvest.
- **Fix direction:** longer/one‑time codes, rate limiting + backoff, code expiry, and a
  challenge‑response that proves possession of a pairing‑derived secret on every message.

### H‑2. Unauthenticated attack surface listening on all interfaces
- **Where:** `TcpServer` binds `0.0.0.0:8989`; `UdpDiscoveryManager` binds `0.0.0.0:8990`; both
  accept and parse arbitrary input from any host.
- **Impact:** Any LAN host can send crafted JSON to the pair/ping/clipboard handlers and to the
  UDP listener. Parsing is lenient (`org.json`), and while individual handlers are guarded, this
  is an unauthenticated, always‑on parser exposed to the whole subnet whenever sync runs.
- **Fix direction:** bind to the intended interface only where possible, validate/limit payload
  sizes, authenticate before acting.

### H‑3. All app data (pairing code, clipboard history) eligible for cloud backup
- **Where:** `AndroidManifest.xml:19-21` `allowBackup="true"` with empty `@xml/backup_rules` and
  `@xml/data_extraction_rules` (default templates, no exclusions).
- **Impact:** `syncmesh_prefs` (pairing code, device id) and `syncmesh.db` (clipboard history,
  which may contain secrets) can be backed up to Google's cloud and transferred to new devices.
- **Fix direction:** exclude sensitive prefs/DB via backup rules, or set `allowBackup="false"`.

### H‑4. Reflection bridge is not obfuscation/shrink‑safe (functional + integrity risk)
- **Where:** `SyncMeshBridge` calls `com.ankit.syncmesh.sync.SyncCoordinator#sendManualClipboardText`
  and `AppRepository` methods by string name via reflection; app release build sets
  `isMinifyEnabled = true` + `isShrinkResources = true` (`app/build.gradle.kts:36-44`); no
  `-keep` rules cover `com.ankit.syncmesh.*`.
- **Nuance:** the keyboard's **consumer** ProGuard file (`keyboard_heliboard/app/proguard-rules.pro`)
  contains `-dontobfuscate`, which is applied to the whole app R8 run — so **names are not
  renamed**, but **R8 tree‑shaking can still remove** the reflectively‑only‑referenced
  `sendManualClipboardText` method, breaking keyboard auto‑send in release. (Confirm with a
  release build.) Separately, `-dontobfuscate` leaking app‑wide **removes name obfuscation from
  the entire application**, hurting reverse‑engineering resistance (see M‑2).
- **Fix direction:** add explicit `-keep` for the reflected classes/members; do not rely on a
  library's `-dontobfuscate` for app behaviour.

---

## MEDIUM

### M‑1. Clipboard contents written to persistent logs and Logcat
- **Where:** `TcpServer.handleClient` / `TcpClient.sendMessage` log full JSON lines via `SyncLog.i`,
  which persists to `sync_logs` and is exportable via **Copy Logs** (`DebugFragment.copyLogs`).
- **Impact:** sensitive clipboard text is duplicated into logs readable via the Debug console and
  Logcat (on debuggable builds / via ADB).
- **Fix direction:** never log payload bodies; log lengths/types only.

### M‑2. Reverse‑engineering resistance effectively disabled app‑wide
- **Where:** `-dontobfuscate` (keyboard consumer proguard) applied to the app; keyboard also uses
  `-dontoptimize` (its own non‑consumer file). Net effect: release APK class/method names are
  intact.
- **Impact:** the entire app (and the protocol) is trivial to inspect and tamper with.
- **Note:** this is likely unintentional leakage of a keyboard build convenience into the app.

### M‑3. No root/tamper/emulator detection; no integrity checks
- **Where:** entire codebase (verified: no such logic).
- **Impact:** standard for many apps, but combined with plaintext clipboard sync it lowers the bar
  for on‑device attackers. Severity Medium given the data sensitivity.

### M‑4. QR pairing payload contains the pairing code in cleartext
- **Where:** `PairFragment.showPairQr` embeds `pairingCode` in the `syncmesh_pair_qr` JSON QR.
- **Impact:** anyone who photographs/screenshots the QR obtains full pairing credentials. Codes do
  not expire, amplifying the risk.
- **Fix direction:** short‑lived codes / rotate after pairing.

---

## LOW

### L‑1. Overly broad Wi‑Fi/network permissions
- **Where:** `AndroidManifest.xml` requests `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`,
  `ACCESS_NETWORK_STATE`, `INTERNET`. Reasonable for the feature set, but `INTERNET` plus
  cleartext broadens exposure if the protocol is ever reachable off‑LAN.

### L‑2. Camera permission requested at feature time (good) but no runtime revoke handling beyond re‑prompt
- **Where:** `PairFragment.startQrScanOrRequestPermission` / `onRequestPermissionsResult`. Minor UX,
  not a vulnerability.

### L‑3. Device name leaks manufacturer/model on the LAN
- **Where:** `AppPreferences.getDeviceName` defaults to `MANUFACTURER MODEL`; broadcast in
  `discovery_announce` and pairing. Minor fingerprinting/PII on the local network.

### L‑4. `SecureRandom` used for codes (good) but code kept indefinitely
- **Where:** `AppPreferences.getPairingCode` uses `SecureRandom` (correct), but the code is
  persistent and reused across all pairings; there is no rotation. Low on its own; compounds H‑1/M‑4.

---

## Positive security notes (verified)

- Pairing code uses `SecureRandom`, not `Math.random`.
- All exported components minimized: only `MainActivity` is exported in the app manifest; the
  foreground service is `exported="false"`. (The keyboard module necessarily exports its IME /
  spell‑checker services, which is standard and permission‑gated by the platform.)
- SQLite access uses parameterized queries / `ContentValues` (no string‑concatenated user input
  into SQL), except the log‑trim `DELETE ... LIMIT 250` which uses a constant — no injection.
- Cursors are consistently closed.

## Summary table

| ID | Severity | Issue |
|----|----------|-------|
| C‑1 | Critical | Release keystore + passwords in VCS |
| C‑2 | Critical | Plaintext clipboard in transit, storage, and logs |
| H‑1 | High | Brute‑forceable 6‑digit pairing, no rate limit, spoofable deviceId auth |
| H‑2 | High | Unauthenticated listeners on 0.0.0.0 (TCP 8989 / UDP 8990) |
| H‑3 | High | All app data (code, history) backup‑eligible |
| H‑4 | High | Reflection bridge not shrink‑safe (auto‑send may break in release) |
| M‑1 | Medium | Clipboard text persisted to logs |
| M‑2 | Medium | Obfuscation disabled app‑wide via keyboard consumer rule |
| M‑3 | Medium | No root/tamper/integrity detection |
| M‑4 | Medium | Pairing code embedded in QR, never expires |
| L‑1..L‑4 | Low | Broad permissions, device‑name leak, code non‑rotation |
