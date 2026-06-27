# Refactor: non-raster page-structure ops (Chunk B)

Branch: `refactor/non-raster-page-ops` (off `main`). Companion to Chunk A
(`refactor/non-raster-save-paths`, overlay save paths). Independent files — no overlap.

## Problem
`PdfUtils.mergePdfs`, `extractPages` (split/reorder/delete), and `rotatePages` rasterized every
page (render to bitmap → new `PdfDocument`), destroying searchable text, bloating files, and
tying output resolution to screen size.

## Done
Reimplemented on **PDFBox-Android** (`com.tom_roush.pdfbox`) page-tree operations:
- `extractPages` → `PDDocument.importPage` of the selected source pages, in order, duplicates
  allowed. Powers split, `reorderPages`, `deletePages`, and page-manager reorder/delete/duplicate.
- `mergePdfs` → import every page of each source into one target; unreadable/encrypted sources
  are skipped (not turned into blank pages).
- `rotatePages` → set page `/Rotate` (additive, normalized to [0,360)); no rasterization.
- New `saveAtomic()` helper: write to a temp file and move into place only on a fully-successful
  save, so a failure can never leave a 0-byte/partial output. Errors are now logged (were swallowed).

Vector text/search/accessibility are preserved; file sizes stay reasonable; output no longer
depends on screen resolution.

## Verified
`PageOpsTest` (instrumented, 3/3 green on device) asserts, against a 3-page PDF with unique
per-page markers:
- merge → 6 pages, correct pages, text extractable;
- extract `[2,0]` → 2 pages in that exact order, text extractable;
- rotate page 0 by 90° → page count unchanged, `/Rotate == 90`, text extractable.

## Remaining (small)
- Page-manager **blank-page insertion**: `PageManagerViewModel` still represents a blank page as
  index `-1` and filters it out at save (so "add blank page" is cosmetic). Needs a small
  `PdfUtils.insertBlankPage`-style op (PDFBox `PDPage(PDRectangle.A4)`) + a VM tweak to build the
  page list including blanks. Not a rasterization issue.
- Optional: drop the now-unused `OpenPDF` dependency once Chunk A/B are merged (see
  [[openpdf-broken-on-android]]).
