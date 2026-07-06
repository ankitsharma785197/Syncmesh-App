# Unknown Areas

Things that **could not be fully verified from static reading alone**, or that require a build /
runtime / external context to confirm. Each notes what is known and what remains open.

## 1. Whether R8 actually strips the reflection targets in release (B‑2)
- **Known:** app release enables `isMinifyEnabled`/`isShrinkResources`; keyboard consumer proguard
  sets `-dontobfuscate` (so no renaming); no `-keep` covers `com.ankit.syncmesh.*`;
  `SyncCoordinator.sendManualClipboardText` is referenced only via reflection.
- **Unknown:** whether R8's tree‑shaking removes it in practice (depends on R8 version behavior and
  whether any keep‑by‑default rule applies). **Requires an actual release build + on‑device test.**

## 2. Full behaviour of the vendored HeliBoard keyboard
- **Known:** it's a fork of HeliBoard ~3.9 (`VERSION_NAME "3.9"`, `VERSION_CODE 3901`), a library
  module merged into the app; the only SyncMesh hook is `LatinIME.java:882`.
- **Unknown:** the internal keyboard subsystems (prediction/JNI dictionaries, gesture typing,
  settings datastore, spell checker, emoji) were **not exhaustively read** (174 Kotlin files + native
  code). Assumed to behave as upstream HeliBoard. The `keyboard_toolbar_*` strings suggest planned
  SyncMesh toolbar buttons whose implementation status inside the keyboard UI was not confirmed.

## 3. Actual runtime discovery reliability on real networks
- **Known:** broadcast every 3 s to interface broadcast + `255.255.255.255`.
- **Unknown:** how many real Wi‑Fi APs / Android client‑isolation settings permit these broadcasts.
  Cannot be determined without on‑network testing.

## 4. Precise Android‑version behavior of clipboard listener
- **Known:** `OnPrimaryClipChangedListener` + dedup; strings acknowledge Android 10+ restrictions.
- **Unknown:** exact per‑OEM/per‑version delivery timing of clip‑changed callbacks, which affects the
  echo/dedup windows (B‑4). Needs device testing across versions.

## 5. Whether the `app_settings` table / several accessors are truly dead
- **Known:** no production caller found in the app for `getSetting`/`setSetting`, `keyboard_language`,
  `last_keyboard_sent_*`, `DatabaseHelper`, accessibility bridge methods.
- **Unknown:** whether anything in the keyboard module (beyond the grep‑verified `SyncMeshBridge`)
  or a future entry point is intended to use them. Grep found no callers, but reflection elsewhere
  could in principle reach them (none observed).

## 6. Manifest merge result (final merged manifest)
- **Known:** app manifest + keyboard manifest components and permissions individually.
- **Unknown:** the **final merged** `AndroidManifest.xml` (with the keyboard's IME service, spell
  checker, `SettingsActivity`, receivers, provider, and the extra permissions `READ/WRITE_USER_DICTIONARY`,
  `RECEIVE_BOOT_COMPLETED`, `VIBRATE`) was reasoned about but not dumped from a build. Verify with
  `./gradlew :app:processReleaseManifest` output if exact merged permissions matter.

## 7. `tools.versions.toml` contents
- **Known:** referenced by `settings.gradle.kts` as a second catalog named `tools`.
- **Unknown:** its contents were not opened; assumed to hold build‑tool versions. Low relevance to app behavior.

## 8. Keyboard module's own git history / divergence from upstream
- **Known:** `keyboard_heliboard` has its own `.git`.
- **Unknown:** the exact diff from upstream HeliBoard beyond the `syncmesh/` package and the single
  hook. A full upstream diff was not performed.

## 9. Runtime performance under load
- **Known (static):** per‑send full‑table re‑reads, main‑thread snapshot I/O, `notifyDataSetChanged`.
- **Unknown:** actual jank/ANR thresholds — requires profiling on target devices with large history
  and many paired devices.

## 10. Intended UX for disabled features
- **Unknown (product intent):** whether the disabled clipboard notification, unwired keyboard toolbar,
  and accessibility scaffolding are deferred‑by‑design or abandoned. Code cannot answer intent.
