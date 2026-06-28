#!/usr/bin/env python3
"""Compose Play-Store marketing screenshots from raw device captures.
Canvas 1080x1920 (9:16, Play-compliant). Brand-terracotta gradient, bold headline,
framed real-device screenshot with rounded corners + drop shadow."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SHOTS = "/private/tmp/claude-1219417223/-Users-kavin-18660-AndroidStudioProjects-PdfMaster/a417b1e1-9ae1-4981-adc0-a6215b5809d0/scratchpad/shots"
OUT   = "/Users/kavin-18660/AndroidStudioProjects/PdfMaster/docs/store-listing/screenshots"
os.makedirs(OUT, exist_ok=True)

W, H = 1080, 1920
HN = "/System/Library/Fonts/HelveticaNeue.ttc"          # ttc: 0=Regular..
ARIAL_BLACK = "/System/Library/Fonts/Supplemental/Arial Black.ttf"
ARIAL_BOLD  = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
ARIAL       = "/System/Library/Fonts/Supplemental/Arial.ttf"

f_head = ImageFont.truetype(ARIAL_BLACK, 78)
f_sub  = ImageFont.truetype(ARIAL, 38)

# crop the OS status bar (top) and nav bar (bottom) from a 1080x2400 capture
TOP_CROP, BOT_CROP = 104, 92

def gradient(top, bot):
    base = Image.new("RGB", (W, H), top)
    top = tuple(top); bot = tuple(bot)
    for y in range(H):
        t = y / (H - 1)
        # ease toward bottom a bit lower so headline area stays deep
        r = int(top[0] + (bot[0]-top[0]) * t)
        g = int(top[1] + (bot[1]-top[1]) * t)
        b = int(top[2] + (bot[2]-top[2]) * t)
        ImageDraw.Draw(base).line([(0, y), (W, y)], fill=(r, g, b))
    # soft decorative highlight circle, top-right
    glow = Image.new("RGBA", (W, H), (0,0,0,0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W-360, -260, W+260, 360], fill=(255,255,255,26))
    gd.ellipse([-300, H-520, 360, H+260], fill=(0,0,0,28))
    base = Image.alpha_composite(base.convert("RGBA"), glow)
    return base

def rounded(img, rad):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0,0,img.size[0],img.size[1]], rad, fill=255)
    img = img.convert("RGBA"); img.putalpha(mask); return img

def wrap(draw, text, font, maxw):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=font) <= maxw: cur = t
        else: lines.append(cur); cur = w
    if cur: lines.append(cur)
    return lines

def compose(src, headline, subtitle, out, grad_top, grad_bot):
    canvas = gradient(grad_top, grad_bot)
    d = ImageDraw.Draw(canvas)
    # headline (wrapped)
    x = 84; y = 132
    for ln in wrap(d, headline, f_head, W-150):
        d.text((x, y), ln, font=f_head, fill=(255,255,255,255))
        y += 90
    y += 8
    for ln in wrap(d, subtitle, f_sub, W-180):
        d.text((x, y), ln, font=f_sub, fill=(255,255,255,220))
        y += 50

    # screenshot
    shot = Image.open(os.path.join(SHOTS, src)).convert("RGB")
    sw, sh = shot.size
    shot = shot.crop((0, TOP_CROP, sw, sh - BOT_CROP))
    target_w = 812
    scale = target_w / shot.size[0]
    target_h = int(shot.size[1] * scale)
    shot = shot.resize((target_w, target_h), Image.LANCZOS)
    shot = rounded(shot, 46)

    sx = (W - target_w)//2
    sy = 472
    # drop shadow
    shadow = Image.new("RGBA", (W, H), (0,0,0,0))
    sh_box = Image.new("RGBA", (target_w+80, target_h+80), (0,0,0,0))
    ImageDraw.Draw(sh_box).rounded_rectangle([40,40,target_w+40,target_h+40], 60, fill=(40,12,4,150))
    sh_box = sh_box.filter(ImageFilter.GaussianBlur(28))
    shadow.alpha_composite(sh_box, (sx-40, sy-22))
    canvas = Image.alpha_composite(canvas, shadow)
    # subtle light frame border
    border = Image.new("RGBA", (W, H), (0,0,0,0))
    ImageDraw.Draw(border).rounded_rectangle([sx-3, sy-3, sx+target_w+3, sy+target_h+3], 49, outline=(255,255,255,60), width=3)
    canvas = Image.alpha_composite(canvas, border)
    canvas.alpha_composite(shot, (sx, sy))

    canvas.convert("RGB").save(os.path.join(OUT, out), "PNG")
    print("wrote", out)

# deep terracotta gradient palette
G1 = ((124,45,18), (200,86,49))    # deep -> mid
G2 = ((150,52,28), (221,111,74))   # warm variant

FRAMES = [
 ("30_home_recent.png",     "Every PDF tool,\nin one app",      "Scan, edit, sign, convert and more", "01_home.png",     *([G1[0],G1[1]],)) ,
]

# explicit list (headline, subtitle, palette)
SPEC = [
 ("30_home_recent.png",    "Every PDF tool in one app",  "Scan, edit, sign, convert and more",            "01_home.png",     G1),
 ("25_viewer_proposal.png","View any PDF, beautifully",  "Fast, smooth and distraction-free reading",     "02_view.png",     G2),
 ("23_edit.png",           "Edit text inside your PDF",  "Add text, images, white-out and highlights",    "03_edit.png",     G1),
 ("21_annotate.png",       "Highlight & annotate",       "Draw, underline and add notes, then save",      "04_annotate.png", G2),
 ("22_sign.png",           "Sign in seconds",            "Draw your signature and drop it anywhere",      "05_sign.png",     G1),
 ("32_compress.png",       "Compress, merge & split",    "Powerful tools to manage every document",       "06_compress.png", G2),
 ("24_viewer_invoice.png", "Open anything instantly",    "Invoices, contracts and reports in one place",  "07_open.png",     G1),
]

for src, hl, sub, out, pal in SPEC:
    compose(src, hl, sub, out, pal[0], pal[1])
print("done")
