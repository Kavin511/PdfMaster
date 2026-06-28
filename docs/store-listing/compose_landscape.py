#!/usr/bin/env python3
"""Landscape / tablet Play screenshots: 1920x1200. Brand gradient, headline column
on the left, real tablet screenshot framed on the right."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SHOTS = "/private/tmp/claude-1219417223/-Users-kavin-18660-AndroidStudioProjects-PdfMaster/a417b1e1-9ae1-4981-adc0-a6215b5809d0/scratchpad/shots"
OUT   = "/Users/kavin-18660/AndroidStudioProjects/PdfMaster/docs/store-listing/screenshots-tablet"
os.makedirs(OUT, exist_ok=True)
W, H = 1920, 1200
ARIAL_BLACK = "/System/Library/Fonts/Supplemental/Arial Black.ttf"
ARIAL       = "/System/Library/Fonts/Supplemental/Arial.ttf"
f_head = ImageFont.truetype(ARIAL_BLACK, 82)
f_sub  = ImageFont.truetype(ARIAL, 40)
TOP_CROP, BOT_CROP = 56, 40

def gradient(top, bot):
    base = Image.new("RGB",(W,H),top)
    d=ImageDraw.Draw(base)
    for y in range(H):
        t=y/(H-1)
        d.line([(0,y),(W,y)], fill=tuple(int(top[i]+(bot[i]-top[i])*t) for i in range(3)))
    glow=Image.new("RGBA",(W,H),(0,0,0,0))
    ImageDraw.Draw(glow).ellipse([W-520,-360,W+360,520], fill=(255,255,255,20))
    return Image.alpha_composite(base.convert("RGBA"),glow)

def rounded(img,rad):
    m=Image.new("L",img.size,0); ImageDraw.Draw(m).rounded_rectangle([0,0,*img.size],rad,fill=255)
    img=img.convert("RGBA"); img.putalpha(m); return img

def wrap(d,text,font,maxw):
    out,cur=[],""
    for w in text.split():
        t=(cur+" "+w).strip()
        if d.textlength(t,font=font)<=maxw: cur=t
        else: out.append(cur); cur=w
    if cur: out.append(cur)
    return out

def compose(src, headline, subtitle, out, gt, gb):
    canvas=gradient(gt,gb); d=ImageDraw.Draw(canvas)
    COL=660  # left text column width
    # vertically center text block
    hlines=wrap(d,headline,f_head,COL-100)
    slines=wrap(d,subtitle,f_sub,COL-90)
    block_h=len(hlines)*96+18+len(slines)*54
    y=(H-block_h)//2
    for ln in hlines:
        d.text((90,y),ln,font=f_head,fill=(255,255,255,255)); y+=96
    y+=18
    for ln in slines:
        d.text((90,y),ln,font=f_sub,fill=(255,255,255,225)); y+=54
    # screenshot on the right
    shot=Image.open(os.path.join(SHOTS,src)).convert("RGB")
    sw,sh=shot.size; shot=shot.crop((0,TOP_CROP,sw,sh-BOT_CROP))
    tw=1150; scale=tw/shot.size[0]; th=int(shot.size[1]*scale)
    shot=rounded(shot.resize((tw,th),Image.LANCZOS),34)
    sx=COL+40; sy=(H-th)//2
    shadow=Image.new("RGBA",(W,H),(0,0,0,0))
    sb=Image.new("RGBA",(tw+80,th+80),(0,0,0,0))
    ImageDraw.Draw(sb).rounded_rectangle([40,40,tw+40,th+40],44,fill=(38,12,4,150))
    shadow.alpha_composite(sb.filter(ImageFilter.GaussianBlur(26)),(sx-40,sy-18))
    canvas=Image.alpha_composite(canvas,shadow)
    bd=Image.new("RGBA",(W,H),(0,0,0,0))
    ImageDraw.Draw(bd).rounded_rectangle([sx-2,sy-2,sx+tw+2,sy+th+2],36,outline=(255,255,255,70),width=3)
    canvas=Image.alpha_composite(canvas,bd)
    canvas.alpha_composite(shot,(sx,sy))
    canvas.convert("RGB").save(os.path.join(OUT,out),"PNG"); print("wrote",out)

G1=((124,45,18),(199,86,49)); G2=((150,52,28),(221,111,74))
SPEC=[
 ("L_home.png",          "Every PDF tool in one app", "Scan, edit, sign and convert — now on tablets", "t1_home.png", G1),
 ("L_view_proposal.png", "View PDFs in rich detail",  "Crisp, full-screen reading on any device",      "t2_view.png", G2),
 ("L_annotate.png",      "Annotate with room to work","Highlight, draw and add notes, then save",      "t3_annotate.png", G1),
 ("L_edit.png",          "Edit text right on the page","Add text, images, white-out and highlights",   "t4_edit.png", G2),
 ("L_settings.png",      "Private by design",         "No account needed. Your files stay yours.",     "t5_settings.png", G1),
]
for s,h,sub,o,p in SPEC: compose(s,h,sub,o,p[0],p[1])
print("done")
