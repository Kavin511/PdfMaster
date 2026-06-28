# Google Play Store Listing Assets — PDF Master

Everything needed for the Play Console store listing. Real Pixel 8 screenshots,
composed into Play-compliant marketing frames, plus two copy variants.

## Files

| Asset | Path | Spec |
|---|---|---|
| Listing copy — **Bold & informative** | `LISTING_A_bold.md` | title / short / full description |
| Listing copy — **Google-recommended** | `LISTING_B_google_recommended.md` | policy-safe, value-first |
| Feature graphic | `screenshots/feature_graphic.png` | **1024 × 500** (required) |
| Phone screenshots ×7 | `screenshots/01..07_*.png` | **1080 × 1920** (9:16) |
| Tablet screenshots ×5 (landscape) | `screenshots-tablet/t1..t5_*.png` | **1920 × 1200** (16:10) |

### Phone screenshots
1. `01_home.png` — Every PDF tool in one app (home / file browser)
2. `02_view.png` — View any PDF, beautifully (reader)
3. `03_edit.png` — Edit text inside your PDF (editor)
4. `04_annotate.png` — Highlight & annotate
5. `05_sign.png` — Sign in seconds (signature pad)
6. `06_compress.png` — Compress, merge & split
7. `07_open.png` — Open anything instantly (invoice viewer)

## Why these sizes
Play phone screenshots must be 320–3840 px per side with the **longer side ≤ 2× the
shorter** and aspect ratio between 16:9 and 9:16. A raw 1080×2400 capture is 9:20
(2.22×) — **too tall and would be rejected** — so each frame is rebuilt at 1080×1920.

### Tablet / landscape screenshots
Captured on the **Resizable emulator in tablet mode (1920×1200)** and composed into a
side-by-side layout (headline column + framed screenshot) with `compose_landscape.py`.
1. `t1_home.png` — Every PDF tool in one app
2. `t2_view.png` — View PDFs in rich detail
3. `t3_annotate.png` — Annotate with room to work
4. `t4_edit.png` — Edit text right on the page
5. `t5_settings.png` — Private by design (no account)

> ⚠️ **Brand finding — dynamic colors default ON.** `UserPreferences.getDynamicColorEnabled()`
> defaults to **true**, so on Android 12+ the app adopts the device wallpaper's palette
> instead of the terracotta brand (the emulator rendered the whole UI **blue**). For these
> shots dynamic colors were turned OFF in Settings. Recommend defaulting it to **false** so
> the brand is consistent for all users — see "Before you publish".

## How they were made
- Captured live on a physical **Pixel 8 (1080×2400)** running the debug build.
- Status bar shown via SystemUI **demo mode** (clean 12:00 clock, full battery/signal).
- Premium was temporarily force-enabled only to capture gated screens; **that change
  was reverted** and a clean build reinstalled. No forced-Pro ships.
- Composited with `scratchpad/compose.py` + `feature_graphic.py` (Pillow): brand
  terracotta gradient, headline, and the cropped real screenshot with rounded corners
  and a drop shadow.

## Before you publish — TODO
- [ ] Pick **one** listing copy (A or B) and paste title / short / full description.
- [ ] Fill in real **subscription terms** (trial length, price, period) in the Pro paragraph.
- [x] **Privacy policy drafted** — `PRIVACY_POLICY.md` + hostable `privacy-policy.html`.
  - [ ] Replace `[SUPPORT EMAIL]` placeholder, host it at a public URL (e.g. GitHub Pages), and paste the URL into Play Console → App content → Privacy policy.
- [x] **App icon (512×512)** — `app-icon-512.png` (rendered from the launcher vector; source `app-icon.svg`).
- [ ] (Optional) Re-shoot `05_sign` with a cleaner signature if desired.
- [ ] Localize copy if targeting non-English markets.

## Regenerating
The source captures live in the session scratchpad. To re-run the compositor, copy the
raw `*.png` captures back into `shots/` and run `python3 compose.py`.
