#!/usr/bin/env python3
"""Concepts that preserve the current icon pixel-for-pixel outside PDA cutouts."""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "propda-minimal-final-light-v23.png"
OUT = HERE / "concepts" / "exact-pda-replacements"
OUT.mkdir(parents=True, exist_ok=True)
INK = (23, 25, 29, 255)
PAPER = (249, 249, 247, 255)

def font(size, bold=False):
    names = ("Arial Bold.ttf", "DejaVuSans-Bold.ttf") if bold else ("Arial.ttf", "DejaVuSans.ttf")
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()

def icon_layer(kind, center, size=76, scale=4):
    layer = Image.new("RGBA", (1080*scale, 1080*scale), (0,0,0,0))
    d = ImageDraw.Draw(layer)
    cx, cy = center[0]*scale, center[1]*scale
    s=size*scale; w=6*scale; c=PAPER
    if kind == "grid":
        q=s*.22; r=s*.13
        for dx in (-q,q):
            for dy in (-q,q):
                d.rounded_rectangle((cx+dx-r,cy+dy-r,cx+dx+r,cy+dy+r),radius=5*scale,outline=c,width=w)
    elif kind == "chat":
        d.rounded_rectangle((cx-s*.43,cy-s*.30,cx+s*.43,cy+s*.25),radius=14*scale,outline=c,width=w)
        d.line((cx-s*.19,cy+s*.24,cx-s*.30,cy+s*.42,cx-s*.02,cy+s*.27),fill=c,width=w,joint="curve")
        for dx in (-.18,0,.18): d.ellipse((cx+s*dx-3*scale,cy-3*scale,cx+s*dx+3*scale,cy+3*scale),fill=c)
    elif kind == "phone":
        d.rounded_rectangle((cx-s*.26,cy-s*.44,cx+s*.26,cy+s*.44),radius=9*scale,outline=c,width=w)
        d.line((cx-s*.10,cy+s*.32,cx+s*.10,cy+s*.32),fill=c,width=w)
    elif kind == "monitor":
        d.rounded_rectangle((cx-s*.43,cy-s*.31,cx+s*.43,cy+s*.22),radius=7*scale,outline=c,width=w)
        d.line((cx,cy+s*.23,cx,cy+s*.38),fill=c,width=w)
        d.line((cx-s*.23,cy+s*.38,cx+s*.23,cy+s*.38),fill=c,width=w)
    elif kind == "chip":
        d.rounded_rectangle((cx-s*.28,cy-s*.28,cx+s*.28,cy+s*.28),radius=7*scale,outline=c,width=w)
        d.rounded_rectangle((cx-s*.12,cy-s*.12,cx+s*.12,cy+s*.12),radius=3*scale,outline=c,width=w)
        for k in (-.18,0,.18):
            d.line((cx+s*k,cy-s*.43,cx+s*k,cy-s*.29),fill=c,width=w)
            d.line((cx+s*k,cy+s*.29,cx+s*k,cy+s*.43),fill=c,width=w)
            d.line((cx-s*.43,cy+s*k,cx-s*.29,cy+s*k),fill=c,width=w)
            d.line((cx+s*.29,cy+s*k,cx+s*.43,cy+s*k),fill=c,width=w)
    elif kind == "android":
        d.arc((cx-s*.40,cy-s*.28,cx+s*.40,cy+s*.50),180,360,fill=c,width=w)
        d.line((cx-s*.40,cy+s*.11,cx+s*.40,cy+s*.11),fill=c,width=w)
        d.line((cx-s*.24,cy-s*.27,cx-s*.36,cy-s*.43),fill=c,width=w)
        d.line((cx+s*.24,cy-s*.27,cx+s*.36,cy-s*.43),fill=c,width=w)
        for dx in (-.17,.17): d.ellipse((cx+s*dx-3*scale,cy-5*scale,cx+s*dx+3*scale,cy+scale),fill=c)
    return layer.resize((1080,1080),Image.Resampling.LANCZOS)

def make(symbols, name):
    im=Image.open(SOURCE).convert("RGBA")
    d=ImageDraw.Draw(im)
    # Only cover the three light PDA glyphs; all other source pixels remain intact.
    d.rectangle((618,325,708,442),fill=INK)
    d.rectangle((618,475,708,600),fill=INK)
    d.rectangle((592,633,711,773),fill=INK)
    for kind,center,size in zip(symbols,((663,382),(663,538),(652,704)),(70,70,72)):
        im.alpha_composite(icon_layer(kind,center,size))
    im.save(OUT/f"propda-exact-{name}.png",optimize=True)
    return im

def make_rising_pda():
    # Keep the approved four, its original pro signature and all three icons.
    im = make(("android", "chat", "phone"), "icons-with-rising-pda")
    d = ImageDraw.Draw(im)
    # The fine stem grows directly from the centre of the 'o'. PDA remains
    # readable top-to-bottom, while its lower A is visually anchored to pro.
    c = PAPER
    d.line((491, 586, 491, 548), fill=c, width=4)
    d.text((491, 540), "A", font=font(32, True), fill=c, anchor="ms")
    d.text((491, 500), "D", font=font(32, True), fill=c, anchor="ms")
    d.text((491, 460), "P", font=font(32, True), fill=c, anchor="ms")
    path = OUT / "propda-exact-icons-rising-pda.png"
    im.save(path, optimize=True)
    preview = im.crop((250, 190, 830, 870)).resize((870, 1020), Image.Resampling.LANCZOS)
    preview.save(OUT / "propda-exact-icons-rising-pda-preview.png", optimize=True)
    return im

def make_partitioned_window():
    im = Image.open(SOURCE).convert("RGBA")
    d = ImageDraw.Draw(im)
    # Clear only the old inner lettering; the approved outer silhouette stays.
    d.rectangle((402, 565, 548, 646), fill=INK)
    d.rectangle((618, 325, 708, 442), fill=INK)
    d.rectangle((618, 475, 708, 600), fill=INK)
    d.rectangle((592, 633, 711, 773), fill=INK)

    scale = 4
    overlay = Image.new("RGBA", (1080*scale,1080*scale), (0,0,0,0))
    od = ImageDraw.Draw(overlay)
    subtle = (126,130,137,255)
    bright = PAPER
    def pts(values): return [(round(x*scale),round(y*scale)) for x,y in values]

    # One restrained divider, gently following the vertical rhythm of the 4.
    curve=[]
    for i in range(61):
        t=i/60
        y=294+480*t
        x=582 + 10*math.sin(t*math.pi) - 3*t
        curve.append((x,y))
    od.line(pts(curve),fill=subtle,width=3*scale,joint="curve")

    # A quiet triangular window for the wordmark; its right side shares the
    # divider visually, so the composition remains minimal rather than boxed.
    window=[(350,650),(514,472),(558,514),(570,650),(350,650)]
    od.line(pts(window),fill=subtle,width=3*scale,joint="curve")
    overlay=overlay.resize((1080,1080),Image.Resampling.LANCZOS)
    im.alpha_composite(overlay)

    d=ImageDraw.Draw(im)
    d.text((410,588),"pro",font=font(44),fill=bright)
    # PDA reads downward and forms a compact vertical continuation of pro.
    d.text((520,520),"P",font=font(31,True),fill=bright,anchor="ms")
    d.text((520,560),"D",font=font(31,True),fill=bright,anchor="ms")
    d.text((520,600),"A",font=font(31,True),fill=bright,anchor="ms")

    for kind,center,size in zip(("android","chat","monitor"),((668,380),(668,535),(668,690)),(67,67,67)):
        im.alpha_composite(icon_layer(kind,center,size))

    im.save(OUT/"propda-exact-partitioned-window.png",optimize=True)
    preview=im.crop((250,190,830,870)).resize((870,1020),Image.Resampling.LANCZOS)
    preview.save(OUT/"propda-exact-partitioned-window-preview.png",optimize=True)
    return im

variants=[
    (("grid","chat","phone"),"apps-forum-phone"),
    (("android","chat","monitor"),"android-forum-computer"),
    (("chip","chat","phone"),"tech-forum-mobile"),
]
images=[make(*v) for v in variants]
make_rising_pda()
make_partitioned_window()
thumbs=[]
for im in images:
    thumbs.append(im.resize((620,620),Image.Resampling.LANCZOS))
sheet=Image.new("RGBA",(620*3,700),(238,239,241,255))
for i,im in enumerate(thumbs): sheet.alpha_composite(im,(i*620,0))
sheet.save(OUT/"propda-exact-pda-options.png",optimize=True)
