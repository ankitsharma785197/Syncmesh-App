# SyncMesh — Play Store release kit (v2.0.0)

Everything you need to publish the **SyncMesh 2.0.0** update on Google Play.

## 📦 The upload file (App Bundle)
```
SyncMesh-2.0.0-release.aab      ← upload this to Play Console
```
- Also available at: `../app/build/outputs/bundle/release/app-release.aab` (identical file).
- **Package:** `com.ankit.syncmesh`
- **versionName:** `2.0.0`  ·  **versionCode:** `20`
- **Signed with:** the project release keystore (`syncmesh-release.jks`, alias `syncmesh`).
- Min Android 8.0 (API 26), target Android 15 (API 36).

> ⚠️ versionCode must always increase. If Play rejects `20` as not higher than what's live,
> bump `versionCode` in `app/build.gradle.kts` and rebuild with `./gradlew :app:bundleRelease`.

## 🖼️ Graphic assets  (`assets/`)
| File | Play requirement | Use |
|------|------------------|-----|
| `assets/icon-512.png` | 512×512, 32-bit PNG | **App icon** (Store listing → Graphics) |
| `assets/feature-graphic-1024x500.png` | 1024×500 PNG/JPEG | **Feature graphic** |
| `assets/screenshots/01..04-*.png` | 1080×2160 (9:16) | **Phone screenshots** (min 2, max 8) |
| `assets/icon-round-preview.png` | preview only | Not uploaded — shows how the icon looks rounded |

The in-app launcher icon was also replaced to match (`app/src/main/res/drawable/ic_launcher_*`).

> Note on screenshots: the four in `assets/screenshots/` are polished **promotional** slides
> (on-brand text + logo). They're upload-ready, but Google reviewers prefer screenshots that
> show the real UI. To capture real ones from a phone, run `./capture-real-screenshots.sh`
> (see that script) and upload those instead of, or alongside, the promo slides.

## 📝 Listing text  (copy/paste into Play Console)
| File | Where it goes |
|------|---------------|
| `store-listing.md` | App name, short & full description, category, contact |
| `whats-new.md` | Release notes ("What's new", ≤500 chars) |
| `privacy-policy.md` | Host publicly, paste the URL into the listing + App content |
| `data-safety.md` | Play Console → App content → Data safety + content rating |

## ✅ Publish checklist
1. **Play Console → your app → Production → Create new release.**
2. Upload `SyncMesh-2.0.0-release.aab`.
3. Paste **What's new** from `whats-new.md`.
4. **Main store listing:** set name, short/full description (`store-listing.md`), upload
   `icon-512.png` + `feature-graphic-1024x500.png` + screenshots.
5. **App content:** complete Privacy policy URL, Data safety (`data-safety.md`), content
   rating (IARC → Everyone), target audience, ads = No.
6. Review → **Roll out to production** (or start with an internal/closed test track first).

## 🔁 To rebuild the bundle later
```
./gradlew :app:bundleRelease
# output: app/build/outputs/bundle/release/app-release.aab
```
