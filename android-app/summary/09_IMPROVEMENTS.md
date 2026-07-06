# 09 — Improvement Suggestions

Grouped by concern. These are recommendations only — nothing here has been applied. Each ties
back to observed code and the findings in docs 06–08.

## Security (highest leverage)
1. **Remove the signing keystore + passwords from VCS**, purge history, rotate the key, and inject
   signing from CI secrets (fixes C‑1).
2. **Encrypt the wire protocol.** Derive a shared secret during pairing (e.g. an ECDH exchange or a
   PAKE seeded by the pairing code) and use authenticated encryption (AES‑GCM) for every message.
   This closes plaintext sniffing (C‑2) and message spoofing (H‑1) at once.
3. **Authenticate every message**, not just pairing: sign/MAC with the pairing‑derived key so
   `fromDeviceId` cannot be spoofed for `clipboard_update`/`ping`.
4. **Harden pairing:** one‑time or short‑TTL codes, rate limiting + exponential backoff on failed
   attempts, and code rotation after successful pairing (fixes H‑1, M‑4, L‑4).
5. **Stop logging clipboard contents** (M‑1); redact payload bodies in `TcpServer`/`TcpClient` logs.
6. **Configure backup rules** to exclude `syncmesh_prefs` and `syncmesh.db`, or set
   `allowBackup="false"` (fixes H‑3).
7. **Fix proguard intent:** add explicit `-keep` for reflected app classes and do not depend on the
   keyboard's `-dontobfuscate`; re‑enable obfuscation for the app (fixes B‑2, M‑2).

## Performance
1. Move all repository reads/snapshot building off the main thread (fixes B‑1); cache the local IP
   and refresh it on connectivity changes only.
2. Replace `notifyDataSetChanged()` with `ListAdapter` + `DiffUtil` in all four adapters.
3. Batch per‑device DB/LiveData updates during clipboard fan‑out (fixes B‑7); update single rows
   in memory instead of re‑querying the whole table.
4. Cap `clipboard_history` and add an index on `created_at` (fixes B‑8).

## Security‑adjacent networking
1. Add retry with backoff and a configurable timeout instead of a hard 3 s (see `05`).
2. Validate inbound payloads (max text size, port range, IP format) before persisting/acting.
3. Derive broadcast addresses from prefix length as well as `getBroadcast()` (fixes B‑10).

## Architecture
1. Introduce a **ViewModel layer** (or at least a small presenter) so fragments stop calling
   singletons directly; makes the UI testable and lifecycle‑safe (helps B‑3).
2. Extract a **`Transport`/`MessageCodec`** abstraction and a `MessageRouter` from `SyncCoordinator`;
   split pairing, clipboard, ping, and discovery into focused services.
3. Break the **`SyncLog` ↔ `AppRepository`** cycle: log to an interface/queue, persist on a
   dedicated background thread, decouple from the data layer.
4. Add lightweight DI (manual or Hilt) to replace `getInstance` singletons; enables faking in tests.
5. Replace the reflection bridge with a **typed API module** shared by app and keyboard (e.g. a small
   interface module both depend on) to get compile‑time safety.

## Scalability
1. Centralize the wire protocol (field names, `type` constants, versioning) into one class shared by
   app + keyboard; add a protocol version field for forward compatibility.
2. Support more than clipboard text (files/images) behind the same authenticated transport if that's
   the roadmap (the `keyboard_toolbar_*` strings hint at broader ambitions).

## Maintainability / code quality
1. Extract window‑inset handling into a base activity/helper (removes ~5 duplicated blocks).
2. Collapse the four near‑identical host activities into one generic `FragmentHostActivity`.
3. Consolidate cursor→model mapping into a single DAO layer (or adopt Room).
4. Move hardcoded UI strings in adapters/`SyncCoordinator` into `strings.xml`; remove unused strings.
5. Add unit tests for: dedup logic, pairing acceptance, JSON round‑trips, DB migrations, error mapping.
6. Add StrictMode in debug builds to catch main‑thread I/O early.

## UX / UI
1. Restore or intentionally remove remote‑clipboard feedback (B‑9) — currently silent.
2. Wire pin/delete in history (B‑13) or remove the pin column/ordering.
3. Localize error toasts (B‑11) and show device‑friendly error copy.
4. Show pairing progress and disable/enable controls lifecycle‑safely.
5. Surface a clear "sync auto‑restarted in background" indicator given `START_STICKY` (B‑14).

## Battery optimization
1. Make the UDP announce interval adaptive (back off when no peers / screen off) instead of a fixed 3 s.
2. Release the multicast lock and pause discovery when the app is backgrounded but sync is idle.
3. Reconsider `START_STICKY` + `stopWithTask="false"` or gate it behind a user setting.

## Network optimization
1. Reuse a persistent connection per paired device instead of a new socket per message.
2. Coalesce rapid clipboard changes (debounce) before broadcasting.

## Memory optimization
1. Bound history in memory and DB; stream/paginate history in the UI.
2. Avoid re‑reading whole tables on every mutation.

## Offline support
- The app is already fully offline/LAN‑only (a strength). Consider explicit messaging when no
  network is present and a queue for sends that fail while a peer is temporarily unreachable.

## Developer experience
1. Add tests + CI (lint, unit, a release‑build smoke test to catch B‑2).
2. Document the protocol in‑repo.
3. Remove committed `local.properties`, keystore, `.DS_Store`, and `.idea` churn from VCS.
4. Split the shared version catalog or annotate which entries belong to which module.
