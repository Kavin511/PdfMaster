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
- [~] Signature → applyEdits — code done & compiles, BUT emulator verify failed (see below)
- [ ] Annotate → applyEdits (+ non-freehand tools) — BLOCKED on the finding below
- [ ] Form → applyEdits (+ font scaling) — BLOCKED on the finding below
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

### Required fix before proceeding
1. Capture the exact exception (logcat `System.err` during save) to confirm the cause.
2. In `OpenPdfEditor.applyImageOverlay`, flatten/encode the signature bitmap in a form OpenPDF
   accepts (e.g. draw onto an opaque ARGB→RGB surface, or use `Image.getInstance(bitmap, null)`
   appropriately), and ensure `applyEdits` never leaves a 0-byte file (write to a temp file and
   only move into place on a fully-successful close).
3. Re-verify signature on-device, THEN replicate the now-proven pattern to annotate + form.

This is exactly why verification preceded propagation — the bug would otherwise have shipped to
three save paths.
