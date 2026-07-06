# 08 — Technical Debt

Ranked by priority (P1 = address first). Each item cites source.

---

## P1 — Build/secrets debt (also Security C‑1)
- **Committed release keystore + plaintext passwords** (`syncmesh-release.jks`,
  `app/build.gradle.kts:9-14`, `gradle.properties`). Highest‑priority debt: it is both a security
  incident and a build‑hygiene failure. `local.properties` is also committed despite `.gitignore`.

## P1 — No tests at all
- `app/src/test` and `app/src/androidTest` contain no source (the generated example tests were
  deleted per git status). Zero coverage over the protocol, dedup, DB migrations, and pairing logic —
  the exact areas most prone to regressions. The keyboard module has tests (Robolectric/JUnit/Mockito).

## P1 — Threading / main‑thread I/O
- Snapshot + repository refresh do SQLite + network enumeration on the main thread (see B‑1).
  No consistent background‑execution strategy; ad‑hoc `Executors` per class. No StrictMode.

## P2 — Duplicated code
- **Window‑inset boilerplate** (`applyWindowInsets()`) is copy‑pasted across `MainActivity`,
  `PairDeviceActivity`, `PairedDevicesActivity`, `ClipboardHistoryActivity`, `DebugActivity`,
  `QrScannerActivity` — ~40 near‑identical lines each. Extract to a base activity / helper.
- **Standalone activities are near‑identical shells** hosting one fragment each
  (`PairDeviceActivity`, `PairedDevicesActivity`, `ClipboardHistoryActivity`, `DebugActivity`).
  Could be a single generic `FragmentHostActivity`.
- **Cursor→model mapping duplicated:** `AppRepository.readClipboardEntry` vs
  `SyncDatabaseHelper.readClipboardEntry`; `readDevice` vs `SyncDatabaseHelper.getAllPairedDevices`
  inline mapping. Two `getAllPairedDevices` (repo delegates to `getPairedDevices`; helper has its own).
- **RecyclerView adapters** are four copies of the same pattern with `submitList`+`notifyDataSetChanged`
  — should use `ListAdapter`/`DiffUtil` and a shared base.
- **Reflection singleton lookup** duplicated across every `SyncMeshBridge` method (`getSingleton`).

## P2 — Tight coupling / questionable dependencies
- **Bidirectional coupling `SyncLog` ↔ `AppRepository`:** `SyncLog.log` calls
  `AppRepository.getInstance(...).addLog(...)`, and `AppRepository.addLog` is only used by `SyncLog`.
  A logging util reaching into the data layer creates a cycle and makes logging do DB writes on the
  caller's thread (e.g. socket threads write to SQLite on every RECV/SEND line).
- **UI reaches into singletons directly** (no ViewModel / DI). Every fragment calls
  `AppRepository.getInstance` / `SyncCoordinator.getInstance`. Hard to test, hard to fake.
- **Keyboard↔app reflection bridge** (`SyncMeshBridge`) is stringly‑typed coupling with no compile
  safety; any rename in the app silently breaks the keyboard (and R8 can strip targets — B‑2).

## P2 — God/large classes & long methods
- `SyncCoordinator` (509 LOC) mixes transport, JSON building, pairing policy, error mapping,
  discovery, and clipboard orchestration — should be split (pairing service, clipboard service,
  message router, error mapper).
- `AppRepository` (443 LOC) mixes SQLite CRUD for three tables, LiveData publication, in‑memory
  nearby cache, and snapshot building.
- `SyncDatabaseHelper` (302 LOC) mixes schema, migration, and DAO responsibilities for four tables.

## P3 — Magic values / hardcoded strings
- Ports `8989`/`8990`, timeouts `3000`, `DUPLICATE_WINDOW_MS=2000`, `RECENT_EVENT_TTL_MS=30000`,
  `ANNOUNCE_INTERVAL_MS=3000`, `STALE_THRESHOLD_MS=15000`, `MAX_LOG_ROWS=250`,
  `AUTO_SEND_DEBOUNCE_MS=3000`, QR size `720`, buffer `4096`, notification IDs `8989/8991`,
  request codes `501/601`, PendingIntent request codes `100/101`.
- **Wire protocol field names and `type` values are string literals** repeated across
  `SyncCoordinator`, `UdpDiscoveryManager`, `PairFragment`, `SyncMeshBridge` (e.g. `"clipboard_update"`,
  `"pairingCode"`, `"fromDeviceId"`, `"syncmesh_pair_qr"`). No shared constants → easy to typo/diverge.
- **Direction/transport magic strings** `"local"`/`"remote"`/`"wifi"` used in DB and UI as raw literals.
- Adapter display strings hardcoded in code (`"Unknown source"`, `"Local"`, `"Remote"`, `"Seen "`,
  `"ID "`, `"Last seen "`) instead of `strings.xml`.

## P3 — Missing abstraction / validation
- No abstraction over the transport (JSON‑over‑socket logic inline). A `MessageCodec` / `Transport`
  interface would enable testing and future encryption.
- No validation of inbound message fields beyond `opt*` defaults (e.g. no bounds on `text` size,
  no IP/port sanity for stored devices).
- `PairFragment` parses port with a bare `Integer.parseInt` (range not validated to 1–65535).
- No shared model ↔ JSON (de)serialization; hand‑rolled per call site.

## P3 — Dead / half‑wired code (see B‑12 for full list)
- Accessibility bridge, most `SyncMeshBridge` methods, `DatabaseHelper`, `app_settings` DAO,
  several `AppPreferences` accessors, clipboard notification, pin/delete history, and ~30 unused
  `keyboard_toolbar_*`/permission‑checklist strings. Prune or clearly mark as roadmap.

## P4 — Naming / style
- `ClipboardEntry` is an empty subclass of `ClipboardModel` (no added behaviour) — redundant type.
- Model classes use public mutable fields (no encapsulation) — pragmatic but fragile; the keyboard
  bridge depends on the exact field names via reflection.
- `addClipboardEntry` returns a `boolean` named `insertedId`; callers ignore it.
- Mixed responsibilities in `NetworkUtils` (IP resolution + IME/accessibility checks are unrelated concerns).

## P4 — Dependency catalog bloat
- `gradle/libs.versions.toml` declares a large catalog (Compose BOM, Room, Coil, coroutines,
  kotest, turbine, etc.) that the **app module does not use** — those exist for the keyboard module.
  The app only consumes ~10 entries. Catalog is shared, so this is expected, but it obscures the
  app's real footprint. See `11_DEPENDENCIES.md`.

## P4 — Proguard leakage
- Keyboard consumer rule `-dontobfuscate` silently disables obfuscation for the whole app; the app's
  own `proguard-rules.pro` only keeps HeliBoard JNI classes and assumes nothing about the app's own
  reflection targets. Intent vs. effect are misaligned (see B‑2, M‑2).

---

## Priority summary

| Priority | Theme | Representative items |
|----------|-------|----------------------|
| P1 | Secrets, tests, main‑thread I/O | committed keystore; zero app tests; snapshot on main thread |
| P2 | Duplication & coupling | inset boilerplate; SyncLog↔Repository cycle; god classes; reflection bridge |
| P3 | Magic values, validation, dead code | protocol string literals; no input validation; accessibility scaffolding |
| P4 | Naming, catalog, proguard | empty subclass; unused catalog entries; `-dontobfuscate` leakage |
