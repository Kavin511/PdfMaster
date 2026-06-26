# PdfMaster — Consolidated Bug Audit (2026-06-25)

Produced by a 5-agent overnight static audit (billing, PDF tools, editor/scanner, app shell, build/release). Deduplicated and prioritized. `[ ]` = open, `[x]` = fixed this session.

Status legend: **🔧 fixing now** = safe, high-value, being fixed autonomously · **🧱 refactor** = large architectural change, needs a plan · **🙋 decision** = needs the owner (legal/keys/backend).

---

## 0. Dominant theme — rasterized save paths 🧱
Every save path except the legacy editor **rasterizes pages to bitmaps and rebuilds a fresh `PdfDocument`**, destroying vector text/search/accessibility, bloating files 5–20×, and tying output resolution to screen size. Affects: `mergePdfs`, `extractPages` (split), `rotatePages`, `addOverlayToPage` (signature), annotate save, form save, page-manager save.
- Fix path already exists in-repo: `OpenPdfEditor.applyEdits` (OpenPDF) + `MuPdfTextExtractor`. The legacy editor already migrated successfully.
- **This is the single biggest quality issue. Needs a dedicated refactor pass (PdfUtils + 4 ViewModels).** Tracked separately below.

---

## 1. CRITICAL

### Billing / revenue
- [x] `billing/BillingManager.kt:271-299` — premium never revoked on live updates (refund/expiry keeps user premium forever). **🔧**
- [x] `billing/BillingManager.kt:306-314` — `verifyAndAcknowledge` swallows failures; flips premium before ack (Play auto-refunds unacked >3 days). **🔧**
- [x] `billing/BillingManager.kt:121-126` — `scheduleReconnect` is a tight loop, no backoff; burns 3 attempts then never retries. **🔧**

### Crashes
- [x] `editor/EditorViewModel.kt:744` — `removeLast()` → `NoSuchElementException` on double-undo. **🔧**
- [x] `scanner/ScannerScreen.kt:80,133` — `context as Activity` `ClassCastException` on wrapped context. **🔧**
- [x] `viewer/ViewerComponents.kt:120` — `Slider(steps = totalPages-2)` → `IllegalArgumentException` on 1-page PDF. **🔧**
- [x] `compress/CompressScreen.kt:99` — division-by-zero on "Saved %" when size is 0. **🔧**
- [x] `viewer/ViewerViewModel.kt:107` — bitmaps recycled while bound to live Compose UI → "recycled bitmap" crash. Now drops refs + clears UI pages (no manual recycle on reload). **🔧**

### Play release blockers
- [x] `AndroidManifest.xml:13-14` — `MANAGE_EXTERNAL_STORAGE` → hard Play rejection. **🔧**
- [x] `AndroidManifest.xml:61` — `file://` scheme in VIEW filter → `FileUriExposedException`. **🔧**
- [x] `res/xml/file_paths.xml:3-7` — FileProvider exposes all external storage (`path="."`). **🔧**
- [x] `proguard-rules.pro` — missing keeps (ComPDFKit, billing, pdfbox, MuPDF, OpenPDF, ML Kit, `@Serializable` nav); over-broad `androidx.compose.**` keep. **🔧**
- [ ] `build.gradle.kts:31-45` — no release `signingConfig` (cannot upload). **🙋 (needs keystore)**
- [ ] `AndroidManifest.xml:108-111` — ComPDFKit license placeholder checked in; ships in trial/watermark mode. **🙋 (needs real key)**
- [ ] `AndroidManifest.xml:31-41` — missing `tools:replace` → ComPDFKit manifest-merger collision. **🔧 (verify at release build)**

### Data loss / correctness
- [x] `editor/EditorViewModel.kt:797-808` — replace-original `delete()+renameTo()` ignores result → original lost on rename failure. Now rename-then-copy fallback, never pre-deletes original. **🔧**
- [ ] `editor/EditorViewModel.kt:840,856` — save clears backing maps but leaves stale UI state → undo/move on phantoms. **🔧 (next batch)**

### Functional-broken features
- [x] `annotate/AnnotateScreen.kt:168-174` — freehand never committed. Added `onDragEnd → finishStroke` and keyed `pointerInput` on the tool. Freehand now saves. (Non-freehand tools + coord accuracy still pending — part of raster refactor.) **🔧**
- [ ] `form/FormFillingViewModel.kt:341-351` — form text rendered at 32pt PDF points with raw UI-pixel coords → giant text, wrong place. **🧱 (part of raster refactor)**
- [ ] `signature/AddSignatureScreen.kt:110-127` — signature coord math wrong under `ContentScale.Fit` letterboxing. **🧱**
- [ ] `editor/PageManagerViewModel.kt:147-176` — add-blank-page is cosmetic; blank/duplicate pages dropped on save. **🧱**
- [x] `util/PdfUtils.kt:158-195` — `imagesToPdf` decoded full-res bitmaps → OOM. Now downsamples (inSampleSize, max 2500px), recycles per-image in `finally`, closes doc in `finally`, honors cancellation, fails on zero pages. **🔧**
- [ ] `util/PdfUtils.kt:75-102` + tool VMs — encrypted/corrupt inputs silently dropped from merge/split (no `validatePdf` call). **🔧 (next batch)**

---

## 2. HIGH (selected — full list in agent reports)

### Billing / quota
- [x] `billing/FeatureGate.kt:124-127` — `remainingDaily` snapshots premium at construction; stays stale after mid-session purchase. **🔧**
- [ ] `billing/BillingManager.kt:78` — `_events` `replay=0`; `PremiumGranted` dropped if emitted before collector attaches → stuck spinner. **🔧 (with billing batch)**
- [ ] `billing/BillingManager.kt:255-269` — `refreshPurchases` silent early-return when disconnected → stuck restore spinner.
- [ ] `data/local/preferences/UserPreferences.kt:170-173` — `checkAndResetDailyLimits` is an empty stub; legacy counters never reset.
- [ ] `scanner/ScannerViewModel.kt:46-55` — watermark failure falls back to clean file (free user gets unwatermarked output). **🔧**

### Resource leaks / OOM / threading
- [ ] `util/PdfUtils.kt` — `PdfDocument`/streams not closed on exception path (8 sites); no `ensureActive()` for cancellation.
- [ ] `annotate`, `form`, `editor/PageManager` — eager full-PDF bitmap load → OOM on large docs.
- [ ] `util/OcrUtils.kt:79` — OCR allocates 2× display-width bitmap → OOM on 4K.
- [ ] `util/MuPdfTextExtractor.kt:33-58` — copies full PDF to temp on every call.
- [ ] `viewer/ViewerViewModel.kt:164-184` — render job not cancelled on reload; non-thread-safe map.

### State / URI / navigation
- [ ] `MainActivity.kt:107,112,117` — VIEW/SEND URIs never get persistable permission → Recents reopen crashes (`SecurityException`).
- [ ] `home/HomeScreen.kt:68-94` — SAF picks not persisted (same).
- [ ] `MainActivity.kt:67,77-88` — initial VIEW intent dropped on recreate; onboarding gate flicker (`startDestination` race).
- [ ] `home/HomeViewModel.kt:113-125` — search spawns a collector per keystroke (no cancel/debounce).
- [ ] `home/HomeViewModel.kt:127-156` — `toggleFavorite` non-atomic across two tables.
- [ ] `editor/EditorViewModel.kt:737-768` — undo/redo stacks not per-page.

### Editor/scanner correctness
- [ ] `editor/EditorViewModel.kt:289-338` — OCR gate blocks extraction; never retries after upgrade.
- [ ] `editor/EditorViewModel.kt:911-933` + `OpenPdfEditor.kt:319` — mixed PdfRenderer/OpenPDF coordinate frames mis-position edits on cropped PDFs.
- [ ] `scanner/ScannerScreen.kt:65-92` — scanner launch on one-shot `LaunchedEffect`; retry doesn't re-check permission.

---

## 3. MEDIUM / LOW
Full detail in the per-domain agent reports. Notable:
- [ ] `theme/Theme.kt:61-65` — dark `tertiaryContainer`/`errorContainer` set to the "on" color. **Deferred** — current values still produce dark containers + light text (acceptable); changing hex blind risks a worse palette. Fix with a visual check.
- [ ] `data/local/database/AppDatabase.kt` — dead duplicate `@Database`; delete. **🔧 (next batch)**
- [ ] `di/AppModule.kt:31-36` — no Room migration policy / `schemaLocation`; v2 bump will crash all users.
- [ ] `UserPreferences.kt:137` + `PdfMasterApp.kt:31` — analytics opt-out by default; `AppOpen` fires before consent (GDPR).
- [ ] `FileUtils.kt:107-110` — output filename collisions (1-second precision) silently overwrite.
- [ ] `FileUtils.kt:112-128` — writes to public Documents without permission on API 29+ (lands in app-private silently).
- [ ] Many `rememberSaveable` gaps (state lost on rotation) across editor/annotate/signature.

---

## 4. 🙋 Needs owner decision (not auto-fixable)
1. **Release signing keystore** — create `keystore.properties`, wire `signingConfigs`.
2. **ComPDFKit license key** — real key into `local.properties` → `BuildConfig` → manifest placeholder.
3. **MuPDF AGPL** — `mupdf-fitz` is AGPLv3; a paid closed-source app needs a commercial Artifex license, or remove the dep. Legal blocker.
4. **Analytics backend** — currently Logcat-only; revenue funnel uncollected. Wire Firebase (needs `google-services.json`) or alternative.
5. **Four PDF engines** (PDFBox + MuPDF + OpenPDF + ComPDFKit) — APK bloat + licensing; decide which to keep.
6. **GDPR consent** — default analytics to opt-in with an onboarding consent prompt.

---

## 5. 🧱 Architectural refactor (proposed, needs go-ahead)
Migrate all save paths off rasterization onto `OpenPdfEditor.applyEdits`:
- merge → OpenPDF `PdfCopy`; split/extract → page-tree copy; rotate → `PDPage.setRotation`.
- annotate/form/signature → overlay primitives on original PDF.
- page-manager → real blank-page insert + rotation by page id.
Eliminates ~half the Criticals and fixes output quality. Estimated multi-file change; should be its own reviewed branch.
