#!/usr/bin/env python3
"""Play feature graphic: 1024x500. Brand gradient, logo mark, name, tagline,
feature pills, and the home screenshot peeking from the right."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SHOTS = "/private/tmp/claude-1219417223/-Users-kavin-18660-AndroidStudioProjects-PdfMaster/a417b1e1-9ae1-4981-adc0-a6215b5809d0/scratchpad/shots"
OUT   = "/Users/kavin-18660/AndroidStudioProjects/PdfMaster/docs/store-listing/screenshots"
W, H = 1024, 500
ARIAL_BLACK = "/System/Library/Fonts/Supplemental/Arial Black.ttf"
ARIAL_BOLD  = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
ARIAL       = "/System/Library/Fonts/Supplemental/Arial.ttf"

def grad(top, bot):
    img = Image.new("RGB", (W, H), top)
    d = ImageDraw.Draw(img)
    for x in range(W):  # horizontal gradient
        t = x/(W-1)
        d.line([(x,0),(x,H)], fill=tuple(int(top[i]+(bot[i]-top[i])*t) for i in range(3)))
    glow = Image.new("RGBA",(W,H),(0,0,0,0))
    ImageDraw.Draw(glow).ellipse([-160,-200,260,300], fill=(255,255,255,22))
    return Image.alpha_composite(img.convert("RGBA"), glow)

img = grad((110,40,16),(202,86,48))
d = ImageDraw.Draw(img)

# logo mark: terracotta rounded square with white document glyph
lx, ly, ls = 70, 74, 96
logo = Image.new("RGBA",(ls,ls),(0,0,0,0))
ld = ImageDraw.Draw(logo)
ld.rounded_rectangle([0,0,ls,ls], 26, fill=(221,111,76,255))
# white document glyph
ld.rounded_rectangle([30,24,68,74], 8, fill=(255,255,255,255))
ld.polygon([(58,24),(68,34),(58,34)], fill=(221,111,76,255))
for yy in (50,60):
    ld.line([(38,yy),(60,yy)], fill=(221,111,76,255), width=4)
img.alpha_composite(logo,(lx,ly))

f_name = ImageFont.truetype(ARIAL_BLACK, 76)
d.text((lx+ls+28, ly-4), "PDF Master", font=f_name, fill=(255,255,255,255))
f_tag = ImageFont.truetype(ARIAL_BOLD, 38)
d.text((lx, 210), "Edit, Scan, Sign & Convert PDFs", font=f_tag, fill=(255,255,255,235))
f_sub = ImageFont.truetype(ARIAL, 27)
d.text((lx, 262), "The all-in-one PDF toolkit — no account needed", font=f_sub, fill=(255,255,255,200))

# feature pills
pills = ["Edit", "Scan", "Sign", "Annotate", "Merge", "Compress"]
f_p = ImageFont.truetype(ARIAL_BOLD, 25)
px, py = lx, 330
for p in pills:
    w = d.textlength(p, font=f_p)
    if px + w + 44 > 640:
        px = lx; py += 58
    d.rounded_rectangle([px, py, px+w+44, py+46], 23, fill=(255,255,255,235))
    d.text((px+22, py+9), p, font=f_p, fill=(150,52,28,255))
    px += w + 44 + 16

# home screenshot peeking on the right, tilted slightly
shot = Image.open(os.path.join(SHOTS, "30_home_recent.png")).convert("RGB").crop((0,104,1080,2308))
tw = 300; th = int(shot.size[1]*tw/shot.size[0])
shot = shot.resize((tw, th), Image.LANCZOS)
mask = Image.new("L",(tw,th),0); ImageDraw.Draw(mask).rounded_rectangle([0,0,tw,th],28,fill=255)
shot.putalpha(mask)
shot = shot.rotate(-8, expand=True, resample=Image.BICUBIC)
# shadow
sh = Image.new("RGBA",(W,H),(0,0,0,0))
sb = shot.split()[3].point(lambda a: a*0.6)
shc = Image.new("RGBA", shot.size, (30,10,4,255)); shc.putalpha(sb)
sh.alpha_composite(shc, (W-330+10, 70+14))
sh = sh.filter(ImageFilter.GaussianBlur(18))
img = Image.alpha_composite(img, sh)
img.alpha_composite(shot, (W-330, 70))

os.makedirs(OUT, exist_ok=True)
img.convert("RGB").save(os.path.join(OUT,"feature_graphic.png"),"PNG")
print("wrote feature_graphic.png", img.size)
