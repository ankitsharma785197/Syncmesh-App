# 05 — API & Network

There is **no HTTP/REST API and no cloud backend**. All networking is peer‑to‑peer over the
local network using raw sockets and line‑delimited JSON. This document describes that
custom protocol and its implementation.

## 1. Endpoints / ports / base "URLs"

| Purpose | Protocol | Port | Bind | Constant |
|---------|----------|------|------|----------|
| Message exchange | TCP | **8989** | `0.0.0.0` | `TcpServer.PORT` |
| Discovery | UDP | **8990** | `0.0.0.0` (broadcast) | `UdpDiscoveryManager.PORT` |

The "address" of a peer is its LAN IPv4 + `8989`. Local IPv4 is resolved by
`NetworkUtils.getLocalIpv4Address()` (prefers a site‑local `Inet4Address` on an up,
non‑loopback interface; falls back to any non‑loopback IPv4; may be `null`).
`ensureLocalIp()` substitutes `"0.0.0.0"` when null.

## 2. TCP server (`TcpServer`)

- `ServerSocket` with `setReuseAddress(true)`, bound to `0.0.0.0:8989`.
- Single‑thread accept loop; each accepted `Socket` handled on a cached thread pool.
- Per connection: `setSoTimeout(3000)`, read **one** `\n`‑delimited line, parse JSON, dispatch
  to `MessageHandler.onMessage`, optionally write one response line, then **close** (one
  request per connection — not persistent).
- Logs every RECV/SEND line verbatim via `SyncLog.i` (so full clipboard text is written to the
  persisted `sync_logs` and Logcat — see security/PII note).
- `isRunning()` = `running && serverSocket != null && !isClosed()`.

## 3. TCP client (`TcpClient`)

- `new Socket()` → `connect(InetSocketAddress, 3000ms)`, `setSoTimeout(3000)`.
- Writes `payload.toString() + "\n"`, flushes. If `expectResponse`, reads one line and parses.
- Always closes the socket in `finally`. Throws on any I/O error (caught upstream).
- A **new socket per message** — no pooling, no keep‑alive.

## 4. UDP discovery (`UdpDiscoveryManager`)

- **Broadcast loop** (single thread): every 3 s, builds a `discovery_announce` JSON, sends it to
  every interface broadcast address (`NetworkUtils.getBroadcastAddresses()`) plus
  `255.255.255.255`, on port 8990. Also prunes stale nearby devices (>15 s) each cycle.
- **Listen loop** (single thread): `DatagramSocket(null)` + `setReuseAddress(true)` + `bind(8990)`,
  4096‑byte buffer, parses each datagram and forwards to `AnnouncementHandler`.
- Holds a non‑reference‑counted `WifiManager.MulticastLock` while running.

## 5. Message catalogue (wire schema)

All JSON, single line, UTF‑8, `\n`‑terminated. See `02_ARCHITECTURE.md §10` for the table.
Handlers live in `SyncCoordinator`:
- `handleIncomingMessage` → routes by `type`.
- `handlePairRequest`, `handleRemoteClipboard`, `handlePing`, `handleDiscoveryAnnouncement`.
- Outbound builders: `buildClipboardPayload`, inline builders in `sendPairRequest`/`pingDevice`.

## 6. Authentication / authorization

- **Pairing:** the only auth gate. `handlePairRequest` accepts iff
  `repository.getLocalPairingCode().equals(incomingCode)`. On success both sides store each other.
- **Clipboard updates:** authorized only by `repository.isPairedDevice(fromDeviceId)` — i.e. the
  claimed sender `deviceId` must already exist in the local `devices` table. The `deviceId` is a
  self‑asserted field in the JSON with **no cryptographic proof**, so it is spoofable by anyone
  who can observe/guess a paired device's UUID on the LAN.
- **Ping:** no auth; anyone can elicit a `pong` and (if the sender id is paired) bump last‑seen.
- **No tokens, no TLS client certs, no HMAC/signature** on any message.

## 7. Headers / framing / serialization

- No headers — the "frame" is one JSON object per line.
- Serialization: `org.json.JSONObject` (lenient; missing fields default via `optString/optInt/optLong/optBoolean`).
- No content‑length, no compression, no chunking. Large clipboard text is sent as one line;
  `BufferedReader.readLine()` reads until `\n` — a payload without a newline or an oversized
  payload will hit the 3 s socket timeout.

## 8. Retry logic / timeouts

- **Timeouts:** connect + read both fixed at **3000 ms** (`TcpClient.TIMEOUT_MS`, and
  `TcpServer` `setSoTimeout(3000)`).
- **Retries:** **none.** A failed pair/ping/clipboard send is reported once; the error string
  is stored on the device row (`last_error`). Auto clipboard fan‑out logs per‑device failures
  but does not retry.
- **Backoff / circuit breaking:** none.

## 9. Error handling & mapping

`SyncCoordinator.toUserFacingNetworkError(exception, ip, port, label)` maps:
- `IllegalStateException` (with message) → its message (e.g. "Invalid pairing code", "No pong received").
- `UnknownHostException` → `error_invalid_ip_address`.
- `SocketTimeoutException` → `error_connection_timed_out`.
- `ConnectException` / message containing `ECONNREFUSED` → `error_remote_sync_not_running`.
- else → generic `error_connection_failed`.

## 10. Cleartext / transport security posture

- `android:usesCleartextTraffic="true"` (`AndroidManifest.xml:26`) — required because all
  traffic is unencrypted TCP/UDP. **No TLS/SSL anywhere.** No `network_security_config`.
- Consequently there is **no certificate validation to speak of** (no HTTPS). Any device on the
  same L2 segment can sniff clipboard contents and discovery announcements.

## 11. Network architecture summary

```
Phone A                                   Phone B
─────────                                 ─────────
UDP 8990 broadcast  ───────────────────►  UDP 8990 listen  → nearby list
                    ◄───────────────────  UDP 8990 broadcast
TCP client :ephemeral ─ pair_request ──►  TCP server 8989  → pair_response
TCP client ─ clipboard_update ─────────►  TCP server 8989  → apply + store
TCP client ─ ping ─────────────────────►  TCP server 8989  → pong
```

- Fully symmetric: every device runs both a server and a client, and both broadcasts and
  listens. All within a single broadcast domain (same Wi‑Fi / hotspot). No NAT traversal, no
  relay, no internet dependency (the `INTERNET` permission is used only for local sockets).
