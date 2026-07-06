# SyncMesh — Documentation Index

Permanent reference produced by a full, code‑verified read of the repository. **No source code
was modified** to create this documentation. Every claim is derived from the actual code; items
needing runtime confirmation are flagged in `ASSUMPTIONS_VERIFIED.md` and `UNKNOWN_AREAS.md`.

| Doc | Contents |
|-----|----------|
| `01_PROJECT_SUMMARY.md` | Overview, purpose, features, stack, structure, lifecycle, startup |
| `02_ARCHITECTURE.md` | Pattern, layers, data/request/response/navigation flows, wire protocol |
| `03_FEATURES.md` | Per‑feature: purpose, files, classes, flow, APIs, storage, deps |
| `04_DATABASE_AND_STORAGE.md` | SQLite schema, SharedPreferences, backup, where each datum lives |
| `05_API_AND_NETWORK.md` | Custom JSON‑over‑socket protocol, ports, auth, timeouts, errors |
| `06_SECURITY_ANALYSIS.md` | Findings by severity (Critical→Low) with source citations |
| `07_BUG_REPORT.md` | Bugs with location, cause, severity, impact, fix |
| `08_TECHNICAL_DEBT.md` | Duplication, coupling, god classes, magic values, dead code (ranked) |
| `09_IMPROVEMENTS.md` | Suggestions across security/perf/arch/UX/battery/etc. |
| `10_FILE_INDEX.md` | Every important file: purpose, responsibility, deps, used‑by |
| `11_DEPENDENCIES.md` | Dependency inventory, need assessment, alternatives |
| `12_CHANGE_GUIDE.md` | Pre‑change impact/risk/testing map for common modifications |
| `KNOWN_LIMITATIONS.md` | Verified scope/design limitations |
| `UNKNOWN_AREAS.md` | What could not be verified statically |
| `ASSUMPTIONS_VERIFIED.md` | Verified facts vs. possible issues vs. unverifiable |

## One‑paragraph summary
**SyncMesh** is a LAN‑only, no‑cloud Android app (Java) that syncs **clipboard text** between
paired devices over raw TCP (port 8989) and UDP discovery (port 8990) using line‑delimited JSON,
with a foreground `dataSync` service, SQLite persistence, LiveData‑driven classic Views, and a
bundled **HeliBoard** keyboard fork (`:keyboard_heliboard` library) that auto‑sends the clipboard
via a reflection bridge. Architecture is singleton + repository + LiveData (no ViewModel/DI/Nav
component). The most important risks are a **committed release keystore**, **plaintext/weakly‑
authenticated sync**, **main‑thread I/O**, and a **reflection bridge that may break under R8
shrinking in release builds** — see docs 06 and 07.
