# Phase 2 — UI Bug Fixes & Pair-Screen Polish — Progress Log

**Scope:** UI-only fixes. No networking, pairing, clipboard-sync, database, or business-logic
changes. All changes are presentation-layer (layouts, drawables, colors, strings, and the
view-binding code that toggles visibility/width).

**Environment used for verification**
- Build: `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (AGP 8.13, JDK 21).
- Device: Android emulator `emulator-5554` (arm64-v8a), tested via ADB
  (install → `pm clear` → launch → scripted taps → screenshots).

---

## Issue 1 — Guided Tour / Onboarding button layout

### Root cause
Both the full-screen **guided tour** card (`view_tour_card.xml` + `TourOverlayView`) and the
**onboarding** screen (`activity_onboarding.xml` + `OnboardingActivity`) hid the Back/Previous
button on the first step with `View.INVISIBLE`. An invisible view still occupies its slot in a
horizontal `LinearLayout`, so on step 1 the Next button only used ~60–70 % of the row and an
empty gap remained where Back would be.

### Fix implemented
- **Tour** (`TourOverlayView.render()` + `view_tour_card.xml`):
  - First step: Skip, the flexible spacer (given a new id `tour_spacer`), and Back are set to
    `View.GONE`; Next is stretched to `MATCH_PARENT` with its start margin removed → one
    full-width button with proper margins.
  - Step 2 onwards: Skip/spacer/Back restored to `VISIBLE`, Next restored to `wrap_content`
    with its original `space_sm` start margin (original proportions preserved).
  - Final step: Back + "Finish" (existing behavior kept — only the label changes).
  - No layout jump/flicker: the card is `INVISIBLE` while a step is prepared and fades in,
    so the row is re-laid-out off-screen.
- **Onboarding** (`OnboardingActivity.updatePage()`):
  - Page 1: `button_back` set to `View.GONE` (was `INVISIBLE`) and `button_next`'s start
    margin cleared → the weighted Next button fills the entire row.
  - Page 2 onwards: Back visible again and Next's `space_sm` start margin restored; the
    original 1:2 Back:Next weight split is unchanged.
  - Last page: Back + "Get started" (existing behavior kept).

### Files modified
- `app/src/main/res/layout/view_tour_card.xml` (id added to spacer)
- `app/src/main/java/com/ankit/syncmesh/ui/TourOverlayView.java`
- `app/src/main/java/com/ankit/syncmesh/ui/OnboardingActivity.java`

### ADB verification
- Fresh install + `pm clear` → onboarding page 1 shows a single full-width Next
  (screenshot-verified); swiping/pressing Next restores Back+Next from page 2.
- Skipping onboarding auto-starts the guided tour: step "1 OF 9" shows one full-width Next;
  step 2 shows Skip / Back / Next in the original proportions; steps advance without flicker.

## Issue 2 — Pair Device screen overflow when Sync is OFF

### Root cause
In `fragment_pair.xml`, the "My device" card header row was
`[avatar][device name (0dp, weight 1)][status badge (wrap_content)]`. The badge text bound in
`PairFragment.bindLocalDeviceCard()` is a full sentence when sync is off
(`pair_sync_stopped`: "Sync is off. Start Sync from Home…"). A `wrap_content` child of a
horizontal `LinearLayout` is measured at its full text width, so the pill badge measured wider
than the screen: its rounded background/border extended past the right edge of the card and
screen, and the weighted device-name view collapsed to 0 px. Not a ConstraintLayout/clipping
issue — the root cause was unconstrained badge width combined with sentence-length badge text.

### Fix implemented (root cause, plus production polish requested during review)
Redesigned only this card header:
- Header row: avatar + a vertical name block (device name, single line, `ellipsize="middle"`,
  plus a "This device" caption) + a **compact** status chip ("Sync on" / "Sync off" — new
  short strings `pair_badge_sync_on` / `pair_badge_sync_off`). Every child is now
  width-bounded, so nothing can overflow at any screen size.
- The long guidance sentence moved into a proper full-width **warning banner** inside the card
  (`banner_sync_off`): amber warning icon (new `ic_warning.xml`) + the existing
  `pair_sync_stopped` text on a soft amber container (`bg_warning_banner.xml`, new
  theme-aware color `syncmesh_warning_soft` in `values/` and `values-night/`).
  The banner is shown only while sync is off; `PairFragment` toggles it from the existing
  `ServiceSnapshot` LiveData (no new business logic).

### Files modified / added
- `app/src/main/res/layout/fragment_pair.xml` (card header + banner)
- `app/src/main/java/com/ankit/syncmesh/ui/PairFragment.java` (badge strings + banner toggle)
- `app/src/main/res/values/strings.xml` (badge strings, "This device" caption)
- `app/src/main/res/values/colors.xml`, `values-night/colors.xml` (`syncmesh_warning_soft`)
- `app/src/main/res/drawable/ic_warning.xml`, `drawable/bg_warning_banner.xml` (new)

### ADB verification
- **Sync OFF:** amber banner visible, "SYNC OFF" chip, device name readable, Show QR disabled,
  nothing overflows (screenshot-verified in dark and light theme).
- **Sync ON:** banner gone, green "SYNC ON" chip, Show QR enabled, QR dialog renders correctly.
- **QR visible/hidden:** Show QR dialog opens when running; blocked with the existing toast
  when stopped (behavior unchanged).
- **Portrait + landscape:** rotated via `user_rotation`; scrolling card lays out correctly,
  no horizontal overflow.
- Long device name (`Google sdk_gphone64_arm64`) middle-ellipsizes instead of disappearing.

## Regression check
- App launch, tab navigation, onboarding, tour, Start/Stop Sync, Pair screen inputs, QR dialog
  all exercised on-device after the changes — no crashes, no Logcat exceptions from the app.
- No networking / pairing / clipboard / database / service code was touched; changes are
  limited to layouts, resources, and view-visibility logic in three UI classes.

## Devices / emulators tested
- Android emulator `emulator-5554` (Pixel-class, arm64-v8a), dark and light theme,
  portrait and landscape.

## Known limitations
- Release build was verified for Phase 1; Phase 2 changes are resource/UI-only and are
  re-verified together with the Phase 3 release build.
