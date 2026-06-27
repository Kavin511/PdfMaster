# Refactor: stop rasterizing PDF save paths

Branch: `refactor/non-raster-save-paths`. Goal: every save path preserves the original
PDF's vector text/search/accessibility instead of flattening pages to bitmaps.

## Problem
`annotate`, `form`, `signature`, `merge`, `split`, `rotate`, and page-manager save by
calling `PdfUtils.renderPage(...)` per page and stitching a fresh `PdfDocument` of bitmaps.
Result: searchable text destroyed, files 5–20× larger, output resolution tied to screen.

## Target API (already in repo)
`OpenPdfEditor.applyEdits(sourceUri, outputFile, textEdits, whiteouts, images, highlights)`
stamps overlay primitives onto the *original* PDF via OpenPDF `PdfStamper`, preserving
existing content. Coordinates are PDF points, top-left origin (applyEdits flips Y).

## Two chunks

### Chunk A — overlay save paths (THIS branch, first)
Migrate annotate / form / signature off rasterization onto `applyEdits`.
The hard part is one shared transform: **UI gesture coords → PDF points**.

`PdfCoordinateMapper` (new, `util/`):
- inputs: rendered bitmap size (bmpW,bmpH), the on-screen container size, the PDF page
  size in points (from `OpenPdfEditor.getPageDimensions`).
- `ContentScale.Fit` letterbox: `scale = min(containerW/bmpW, containerH/bmpH)`,
  `offset = ((containerW-bmpW*scale)/2, (containerH-bmpH*scale)/2)`.
- `containerPointToPdf(p)`: subtract offset, /scale → bitmap px, then × (pdfW/bmpW, pdfH/bmpH).
- returns null for taps outside the rendered image rect.

Per screen:
1. **Signature** → one `ImageOverlay` for the signed page (was `addOverlayToPage`, which
   rasterized everything). Capture the real rendered-image rect via `onSizeChanged` and the
   uniform Fit scale. **First migration — simplest, single page, single overlay.**
2. **Annotate** → `HighlightRect` / freehand as `ImageOverlay` (stroke rendered to a
   transparent bitmap sized to the page) / `TextEdit`. Implement the missing non-freehand
   tool gestures while here.
3. **Form** → `TextEdit` per filled field (font size scaled points), checkbox as drawn
   glyph via small `ImageOverlay`; convert field bounds through the mapper.

Acceptance per screen: output opens in a viewer with original text still selectable;
overlay lands within a few px of where it was placed on screen; file size ≈ source + delta.

### Chunk B — page-structure ops (FOLLOW-UP branch, not here)
`merge / split / rotate / page-manager` need page-tree ops not expressible via `applyEdits`:
- merge → OpenPDF `PdfCopy`; split/extract → `PdfReader` + `PdfCopy.getImportedPage`;
  rotate → `reader.getPageN(i).put(PdfName.ROTATE, PdfNumber(deg))`; blank page → real
  inserted page; reorder/delete → selective copy.
- New `PdfUtils` functions replacing the rasterizing ones; rewire 3 ViewModels.

## Verification (REQUIRED before merge)
Coordinate math cannot be fully validated without a device. Before merging this branch:
- run each save path on real multi-page PDFs (portrait + landscape + non-A4 + rotated),
- confirm text remains selectable and overlays land correctly,
- check output file size is reasonable.

## Status
- [x] PdfCoordinateMapper
- [x] **OpenPdfEditor rewritten on PDFBox-Android** (root-cause fix, see below) + instrumented test
- [x] Signature → applyEdits — verified: output is a valid non-empty PDF with the original
      **text layer preserved** (asserted by `OpenPdfEditorTest`)
- [ ] Annotate → applyEdits (+ non-freehand tools) — now unblocked, next
- [ ] Form → applyEdits (+ font scaling) — now unblocked, next
- [ ] Chunk B (separate branch)

## ⚠️ Verification finding (2026-06-26, Medium_Phone arm64 emulator)
End-to-end emulator test of the signature flow (import → draw → place → save, premium forced):
- UI flow works; the new gate fires correctly for free users.
- BUT the saved output `Signed_*.pdf` is **0 bytes** — `OpenPdfEditor.applyEdits` produces an
  empty file. Confirmed across two runs.
- Source PDF ruled out: it is PDF 1.3 with a classic xref table (OpenPDF-compatible), so this
  is a real regression in the ImageOverlay save path, not a test artifact.
- **Most likely cause:** OpenPDF `Image.getInstance()` on the PNG-with-alpha signature bitmap
  (transparent ARGB_8888) throwing inside `applyImageOverlay`, which leaves the just-opened
  `FileOutputStream(outputFile)` empty; the `finally` double-close then can't finalize it.

### ROOT CAUSE (confirmed via instrumented test)
The real cause was NOT the PNG alpha. An instrumented test calling `applyEdits` directly threw:
`java.lang.NoClassDefFoundError: java.awt.Color at com.lowagie.text.pdf.PdfStamper.<init>`.
**OpenPDF (`com.lowagie`/librepdf) references `java.awt.*`, which does not exist on Android**, so
EVERY OpenPdfEditor operation (text edits, image overlays, AND `addWatermark`) threw at
`PdfStamper` construction and left a 0-byte file. OpenPdfEditor had never worked on a device.

### FIX (done)
Rewrote `OpenPdfEditor` entirely on **PDFBox-Android** (`com.tom_roush.pdfbox`, already a
dependency — fully Android-native, no AWT):
- edits stamped via append-mode `PDPageContentStream` → original vector text preserved.
- transparent signature handled natively by `LosslessFactory.createFromImage(doc, bitmap)`.
- `applyEdits`/`addWatermark` now write to a temp file and only move into place on a fully
  successful save, so a failure can never leave a 0-byte/partial output.
- `OpenPdfEditorTest` (instrumented) asserts non-empty valid PDF **and** that the source text
  survives — both green.

### ⚠️ Cross-cutting impact (applies to `main`, not just this branch)
Because OpenPdfEditor was broken app-wide on Android, on `main`:
- the free-tier scan **watermark never actually applied** (addWatermark always returned false);
- the legacy editor's text-edit **save** was also non-functional on device.
The `main`-branch change that *fails* a scan when watermarking fails therefore currently breaks
free scanning on real devices. **This PDFBox fix should be brought to `main`** (cherry-pick
`OpenPdfEditor.kt`) so watermarking actually works and that change becomes correct.
