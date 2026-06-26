# Release Readiness Audit — PDF Master 1.0.0 (2026-06-25)

## Verdict: 🔴 NO-GO

The app is **functionally strong and runs cleanly on a real device** (Pixel 8 / Android 16:
launches, no crash, billing initializes, features work). But it **cannot ship today** — four
hard blockers stand between it and a Play upload, and none have been exercised yet because all
testing so far was on the `debug` build (minify off, debug-signed).

**Shortest path to GO:** (1) add a release signing config, (2) remove `MANAGE_EXTERNAL_STORAGE`
(and the unused biometric permission), (3) resolve the 16 KB native-lib alignment, (4) build a
**release** AAB, fix the R8/ProGuard keep rules it exposes, and smoke-test it. Then complete the
Play Console paperwork (privacy policy live, Data safety, subscription products).

---

## Blockers (must fix before release)

| # | Area | Finding | Evidence | Fix |
|---|---|---|---|---|
| B1 | Signing | **No `signingConfig` for `release`** → `bundleRelease` produces an *unsigned* artifact that Play won't accept. | `app/build.gradle.kts:30-37` (release block has no signing) | Create an upload keystore; add `signingConfigs { create("release") {...} }` reading creds from `local.properties`/env (never commit them); enroll in Play App Signing. |
| B2 | Permissions | **`MANAGE_EXTERNAL_STORAGE` (All files access)** declared. Play requires a special-use review and rejects it unless the app's core purpose genuinely needs broad file access. This app uses the system file picker (SAF) + scoped storage, so it almost certainly does **not** qualify → near-certain rejection. | `AndroidManifest.xml` (`MANAGE_EXTERNAL_STORAGE`) | Remove the permission; rely on SAF (`OpenDocument`) + `READ_MEDIA_*` + scoped output dirs (already how the tools work). |
| B3 | Native libs | **16 KB page-size non-compliance.** Bundled `.so`s are not 16 KB-aligned — Android 16 flagged it on-device. Play is enforcing 16 KB support for new submissions targeting recent APIs. | Device dialog: `libmlkit_google_ocr_pipeline.so`, `libmupdf_java.so`, `libcompdf.so`, `libsurface_util_jni.so`, `libimage_processing_util_jni.so`, `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` | Update each dep to a 16 KB-aligned release (newer CameraX, ML Kit, MuPDF, AndroidX graphics-path, DataStore; ComPDFKit ≥ a 16 KB build) and re-verify with `check-elf-alignment` / APK Analyzer. Dropping ComPDFKit (see H5) removes `libcompdf.so` outright. |
| B4 | Release build | **The release build has never been produced or run, and the ProGuard rules are insufficient.** `isMinifyEnabled=true` but `proguard-rules.pro` does **not** keep the type-safe Navigation routes (`Screen.*` are `@Serializable`) nor OpenPDF/PdfBox/ComPDFKit reflective classes. High chance of a release-only crash on navigation or PDF ops. | `app/build.gradle.kts:38-45`; `proguard-rules.pro` (generic `kotlinx.serialization.json.**` keep only — does not cover `com.pdfmaster.app.presentation.navigation.Screen`) | Add keep rules for the `Screen` serializer hierarchy and the PDF libs; then `bundleRelease`, install, and run the full smoke path before trusting it. |

---

## High-risk (likely rejection / ship-risk)

| # | Area | Finding | Evidence | Severity |
|---|---|---|---|---|
| H1 | Store | **Privacy Policy URL must be live.** Required for the listing + Data safety; the app collects analytics. | `PLAY_STORE_LISTING.md` lists `pdfmaster.app/privacy` (placeholder) | HIGH |
| H2 | Data safety | Console **Data safety form must be filled** to declare the (optional, opt-out) analytics and to declare documents as *not* collected. | `ANALYTICS.md` §Data safety has the answers — not yet entered in Console | HIGH |
| H3 | Target API | `targetSdk = 35`. Confirm the **current** Play minimum (the API-35 deadline was Aug 2025; an API-36 requirement may land Aug 2026). | `app/build.gradle.kts:20` | HIGH (verify) |
| H4 | Permissions | **`USE_BIOMETRIC` declared but App Lock is not implemented** (dead permission + dead Settings prefs). Reviewers flag unused sensitive permissions. | `AndroidManifest.xml` (`USE_BIOMETRIC`); no `BiometricPrompt` anywhere | HIGH |
| H5 | Dependency | **ComPDFKit (commercial SDK) is bundled but disabled** (`USE_COMPDFKIT=false`) with a placeholder license key. It adds ~native weight, contributes to B3, and is a licensing exposure if shipped. | `EditorScreen.kt:57`; `AndroidManifest.xml` `compdfkit_key="YOUR_…"`; `build.gradle.kts` compdf deps | HIGH |

---

## Feature completeness

Proven end-to-end on device / in code (post Phase-1 fixes):

| Feature | Status | Evidence / Gap |
|---|---|---|
| PDF viewer + zoom/pages | ✅ | renders on device |
| In-PDF text search | ✅ | `ViewerViewModel.search()` via `PdfUtils.extractTextBlocks` (fixed) |
| Password unlock (validated) | ✅ | `ViewerViewModel.unlockPdf()` verifies + decrypts (fixed) |
| Merge / Split / Compress / Images→PDF | ✅ | tool VMs → `PdfUtils` |
| Scanner (+ free watermark) | ✅ | `ScannerViewModel` + `OpenPdfEditor.addWatermark` |
| Editor — text add/edit | ✅ (overlay) | whiteout+overlay, not true text-stream edit — acceptable, market honestly |
| Editor — highlights | ✅ | now persisted via `HighlightRect` (fixed) |
| Signatures | ✅ | `AddSignatureScreen` → `PdfUtils.addOverlayToPage` |
| Password protect | ✅ | `SetPasswordScreen` → `PdfUtils.encryptPdf` |
| Annotate / Form-fill | 🚧 | functional but **rasterizes** pages to bitmap on save (lossy, no text layer) |
| Billing / paywall / gating | ✅ | built; **requires Play Console products** before it can transact |
| Analytics + opt-out | ✅ | built; **LogcatAnalytics only** — see M3 |
| OCR / searchable PDF | ❌ N/A | no screen; route removed. Not advertised. |
| PDF→Word / PDF→Text export | ❌ N/A | `PdfRepositoryImpl` stubs. Not advertised. |
| Cloud sync | ❌ N/A | not built. Not advertised. |
| App Lock / biometric | ❌ | dead config + unused permission (H4) |

> Dead code to clean: `PdfRepositoryImpl` `encryptPdf/decryptPdf/validatePassword/addBlankPage/
> pdfToText` are unused stubs (screens bypass them via `PdfUtils`) — harmless at runtime but a trap.

---

## Play policy compliance

| Item | Status | Severity | Notes |
|---|---|---|---|
| All-files access | ❌ Blocker | BLOCKER | B2 — remove `MANAGE_EXTERNAL_STORAGE` |
| 16 KB page size | ❌ Blocker | BLOCKER | B3 |
| Target API level | ⚠️ | HIGH | H3 — confirm current threshold |
| Data safety form | ⚠️ | HIGH | H2 — declare analytics; documents NOT collected |
| Privacy policy | ⚠️ | HIGH | H1 — must be live |
| Permissions justified | ⚠️ | HIGH | H4 biometric unused; "Internet for Ads" comment is stale (ads cut) |
| Account deletion | ✅ N/A | — | no accounts; reflect in Data safety |
| Ads | ✅ | — | none (deliberately cut) |
| Subscriptions disclosure | ⚠️ | MED | paywall shows prices/terms; ensure listing repeats trial+auto-renew terms |
| Deceptive metadata | ✅ | — | listing copy is honest, no superlatives, only shipping features |
| Content rating | ⚠️ | MED | complete IARC questionnaire (Everyone) |

---

## DB / data migration

| Item | Status | Severity | Notes |
|---|---|---|---|
| Room version | ✅ | — | `PdfDatabase` v1 — first release, no migration needed |
| Migration discipline | ⚠️ | MED (BLOCKER for v2) | No `Migration`s and `exportSchema=true` but **no `schemas/` dir / `room.schemaLocation`** → schema not actually exported. Set this up now so v2 has a baseline; a future version bump without a migration will crash existing users. |
| Duplicate DB | ⚠️ | MED | Both `AppDatabase` (exportSchema=false) and `PdfDatabase` exist; DI uses `PdfDatabase` — `AppDatabase` looks unused/dead. Remove to avoid confusion. |
| Backup | ⚠️ | LOW | `allowBackup=true` — confirm `backup_rules.xml`/`data_extraction_rules.xml` don't restore the premium-entitlement cache across devices. |

---

## Build & security

| Item | Status | Severity | Notes |
|---|---|---|---|
| Release signing | ❌ | BLOCKER | B1 |
| Minify / shrink | ✅ (config) / ⚠️ (untested) | BLOCKER | enabled, but B4 — rules incomplete & never run |
| `debuggable` | ✅ | — | release defaults to false |
| Hardcoded secrets | ✅ | — | none in code; `local.properties` only has `sdk.dir`; ComPDFKit key is an unset placeholder |
| Cleartext / network security | ⚠️ | LOW | no `networkSecurityConfig`; app is offline-first — fine, but consider `usesCleartextTraffic=false` |
| Exported components | ✅ | — | only `MainActivity` exported (launcher + intents); FileProvider not exported; ComPdfKit activity not exported |

---

## Lower priority (polish)
- `-keep class androidx.compose.** { *; }` is overly broad — defeats some R8 size wins.
- `tools:targetApi="34"` in manifest vs `targetSdk=35` — cosmetic mismatch.
- Stale `<!-- Internet for Ads -->` comment (ads removed).
- `versionCode=1` / `1.0.0` — fine for first launch; remember to bump per upload.

---

## Prioritized fix list (blockers first)

1. **B1** Add release signing config (upload keystore, creds outside VCS).
2. **B2** Remove `MANAGE_EXTERNAL_STORAGE` (+ verify SAF flows still cover every file op).
3. **B4** Build `bundleRelease`; add ProGuard keeps for `Screen` serializers + OpenPDF/PdfBox; smoke-test the signed release end-to-end.
4. **B3** Resolve 16 KB alignment (bump native deps; dropping ComPDFKit removes one offender).
5. **H4/H5** Remove unused `USE_BIOMETRIC`; remove or properly license+enable ComPDFKit.
6. **H1/H2/H3** Publish privacy policy; complete Data safety; confirm target-API threshold.
7. **M (DB)** Set up Room schema export now; adopt migration discipline before any v2 schema change.
8. Clean dead code (unused `AppDatabase`, `PdfRepositoryImpl` stubs).

> Then: create the Play Console subscription products (`pdfmaster_premium` weekly/annual +
> `pdfmaster_lifetime`), capture real screenshots into the brand-kit frames, and upload to a
> closed-testing track first.
