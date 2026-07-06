# 11 — Dependencies

Two distinct dependency sets: the **app module** (small, verified against `app/build.gradle.kts`)
and the **keyboard module** (large HeliBoard stack). The shared `gradle/libs.versions.toml` catalog
declares many entries used **only** by the keyboard.

---

## A. App module dependencies (`app/build.gradle.kts`)

| Dependency | Version | Why it exists | Where used | Needed? | Alternatives |
|-----------|---------|---------------|-----------|---------|--------------|
| `androidx.core:core` | 1.18.0 | `ContextCompat`, `ServiceCompat`, `NotificationCompat`, window insets compat | services, notifications, activities | Yes | — |
| `androidx.appcompat:appcompat` | 1.7.0 | `AppCompatActivity` base for all activities | every activity | Yes | — |
| `androidx.activity:activity-ktx` | 1.13.0 | Activity APIs (transitively used) | activities | Yes (light) | plain activity |
| `androidx.fragment:fragment` | 1.8.5 | Fragment framework | all fragments | Yes | — |
| `androidx.lifecycle:lifecycle-runtime` | 2.8.7 | Lifecycle owners for LiveData observation | fragments | Yes | — |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.7 | `LiveData`/`MutableLiveData` — the reactive layer | `AppRepository` + all fragments | Yes (core) | StateFlow/coroutines |
| `androidx.recyclerview:recyclerview` | 1.3.2 | All list UIs | 4 adapters | Yes | — |
| `com.google.android.material:material` | 1.12.0 | Material 3 theme, `MaterialToolbar`, `BottomNavigationView`, `MaterialAlertDialogBuilder`, text fields | activities, fragments, dialogs | Yes | — |
| `com.google.zxing:core` | 3.5.3 | QR **encode** (`MultiFormatWriter`, `BitMatrix`) | `PairFragment.showPairQr` | Yes (for QR) | — |
| `com.journeyapps:zxing-android-embedded` | 4.3.0 | QR **scan** (`IntentIntegrator`, `CaptureManager`, `BarcodeEncoder`) | `PairFragment`, `QrScannerActivity` | Yes (for QR) | ML Kit Barcode, CameraX + ML Kit |
| `projects.keyboardHeliboard` | local | The bundled HeliBoard IME + SyncMesh bridge | `HomeFragment` (`LatinIME`, `SettingsActivity`), `SyncMeshApplication` (`App`) | Yes (feature) | any IME, or drop the add‑on |
| `junit:junit` (test) | 4.13.2 | Unit test runner | **no tests present** | Currently unused | — |
| `androidx.test.ext:junit` (androidTest) | 1.3.0 | Instrumentation tests | **no tests present** | Currently unused | — |
| `androidx.test.espresso:espresso-core` (androidTest) | 3.7.0 | UI tests | **no tests present** | Currently unused | — |

**App‑module observations**
- The app module uses **no** Room, Retrofit/OkHttp, Hilt/Dagger, RxJava, Coroutines, Coil, or
  Compose. Networking/DB/JSON are all platform SDK. This keeps the app light but means much
  hand‑rolled infrastructure (see technical debt).
- Test dependencies are declared but there are **no tests**, so they add nothing today.
- ZXing is the only "external" (non‑AndroidX/Material) runtime library in the app module.

## B. Keyboard module dependencies (`keyboard_heliboard/app/build.gradle.kts`)

These are pulled in transitively by depending on `:keyboard_heliboard`. Key ones:

| Dependency | Version | Why (in keyboard) |
|-----------|---------|-------------------|
| `androidx.core:core-ktx` | 1.17.0 | keyboard KTX |
| `androidx.recyclerview` | 1.4.0 | keyboard lists |
| `androidx.autofill` | 1.3.0 | autofill integration |
| `androidx.viewpager2` | 1.1.0 | keyboard paging |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | settings/layout serialization |
| Jetpack **Compose** (BOM 2025.11.01, material3, ui‑tooling) | — | keyboard settings UI |
| `androidx.navigation:navigation-compose` | 2.9.8 | settings navigation |
| `sh.calvin.reorderable:reorderable` | 3.1.0 | drag‑reorder in settings |
| `com.github.skydoves:colorpicker-compose` | 1.1.3 | custom theme colors |
| `com.android.tools:desugar_jdk_libs` | 2.1.5 | Java‑8+ API desugaring |
| NDK (`ndkBuild`, `Android.mk`) | ndk 28.0.13004108 | native dictionary/prediction engine (JNI) |
| test: kotlin‑test, junit 4.13.2, mockito 5.23.0, robolectric 4.16.1, androidx.test | — | keyboard unit tests |

## C. Catalog entries declared but NOT used by the app

`gradle/libs.versions.toml` also declares (unused by `:app`, some unused entirely / for keyboard
only): `androidx-room-*`, `coil-*`, `cache4k`, `material-kolor`, `mikepenz-aboutlibraries-*`,
`patrickgold-jetpref-*`, `patrickgold-compose-tooltip`, `androidx-compose-*`, `kotlinx-coroutines*`,
`kotest-*`, `turbine`, `androidx-benchmark-*`, `androidx-test-uiautomator`, `androidx-window-core`,
`androidx-emoji2*`, `androidx-exifinterface`, `androidx-profileinstaller`, `androidx-collection-ktx`,
`androidx-navigation-compose`, `kotlin-reflect`, etc. Plugins declared but `apply false`: KSP,
serialization, compose, aboutlibraries, kotest, kover.

**Note:** these do not bloat the app APK unless a module actually depends on them; they are catalog
*declarations*. They do, however, make it hard to tell the app's true footprint at a glance.

## D. Are dependencies actually needed? (app module verdict)

- **Needed / used:** core, appcompat, fragment, lifecycle‑runtime, lifecycle‑livedata, recyclerview,
  material, zxing core + embedded, keyboard module.
- **Declared, currently dead weight:** the three test libraries (no tests). `activity-ktx` is
  minimally used (Java code; mostly transitive).
- **Missing but arguably warranted:** a JSON/serialization lib is avoided in favor of `org.json`
  (platform) — fine; a DB abstraction (Room) is avoided in favor of raw SQLite — a trade‑off.

## E. Dependency risk notes
- **ZXing embedded 4.3.0** is stable but the ecosystem is trending toward **ML Kit Barcode** /
  CameraX; a future migration is plausible if camera behavior needs modernizing.
- The **keyboard fork is vendored** (its own `.git`, gradle wrapper, licenses GPL‑3.0/Apache‑2.0/
  CC‑BY‑SA‑4.0). Bundling HeliBoard imposes **GPL‑3.0 obligations** on the combined app — a
  licensing consideration for distribution (verify compliance; source availability required).
- `compileSdk = 36` / `targetSdk = 36` are very new; some catalog versions (e.g. `compose-bom
  2026.03.01` in the catalog) are future‑dated — ensure the resolved versions actually exist in the
  configured repos at build time.
