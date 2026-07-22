#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE=Path(__file__).resolve().parent
SRC=HERE/"propda-minimal-final-light-v23.png"
OUT=HERE/"concepts"/"real-four-window"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255); PAPER=(249,249,247,255)

def font(n):
    for f in ("Arial Bold.ttf","DejaVuSans-Bold.ttf"):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def background():
    src=Image.open(SRC).convert("RGBA")
    bg=Image.new("RGBA",src.size)
    p=bg.load()
    for y in range(1080):
        c=src.getpixel((20,y))
        for x in range(1080):p[x,y]=c
    return bg

BG=background()

def icon(kind,cx,cy,size=67):
    sc=4;o=Image.new("RGBA",(1080*sc,1080*sc),(0,0,0,0));d=ImageDraw.Draw(o)
    cx*=sc;cy*=sc;s=size*sc;w=5*sc;c=PAPER
    if kind=="android":
        d.arc((cx-s*.4,cy-s*.28,cx+s*.4,cy+s*.5),180,360,fill=c,width=w)
        d.line((cx-s*.4,cy+s*.11,cx+s*.4,cy+s*.11),fill=c,width=w)
        d.line((cx-s*.24,cy-s*.27,cx-s*.36,cy-s*.43),fill=c,width=w);d.line((cx+s*.24,cy-s*.27,cx+s*.36,cy-s*.43),fill=c,width=w)
        for dx in (-.17,.17):d.ellipse((cx+s*dx-3*sc,cy-5*sc,cx+s*dx+3*sc,cy+sc),fill=c)
    elif kind=="chat":
        d.rounded_rectangle((cx-s*.43,cy-s*.3,cx+s*.43,cy+s*.25),14*sc,outline=c,width=w)
        d.line((cx-s*.18,cy+s*.24,cx-s*.3,cy+s*.42,cx-s*.02,cy+s*.27),fill=c,width=w,joint="curve")
        for dx in (-.18,0,.18):d.ellipse((cx+s*dx-3*sc,cy-3*sc,cx+s*dx+3*sc,cy+3*sc),fill=c)
    else:
        d.rounded_rectangle((cx-s*.43,cy-s*.3,cx+s*.43,cy+s*.22),7*sc,outline=c,width=w)
        d.line((cx,cy+s*.23,cx,cy+s*.38),fill=c,width=w);d.line((cx-s*.23,cy+s*.38,cx+s*.23,cy+s*.38),fill=c,width=w)
    return o.resize((1080,1080),Image.Resampling.LANCZOS)

def aperture(im,points,soft=0):
    sc=4;m=Image.new("L",(1080*sc,1080*sc),0);d=ImageDraw.Draw(m)
    d.polygon([(x*sc,y*sc) for x,y in points],fill=255)
    if soft:
        m=m.filter(ImageFilter.GaussianBlur(soft*sc))
        m=m.point(lambda v:0 if v<20 else 255 if v>235 else v)
    m=m.resize((1080,1080),Image.Resampling.LANCZOS)
    im.paste(BG,(0,0),m)

def make(n,points,soft=0,divider=False):
    im=Image.open(SRC).convert("RGBA");d=ImageDraw.Draw(im)
    # Remove only pro and PDA, then cut a genuine counter from the four.
    d.rectangle((400,558,550,650),fill=INK)
    d.rectangle((618,325,708,442),fill=INK);d.rectangle((618,475,708,600),fill=INK);d.rectangle((592,633,711,773),fill=INK)
    aperture(im,points,soft)
    if divider:
        ov=Image.new("RGBA",(4320,4320),(0,0,0,0));od=ImageDraw.Draw(ov)
        od.line((585*4,310*4,585*4,763*4),fill=(90,94,102,255),width=2*4)
        im.alpha_composite(ov.resize((1080,1080),Image.Resampling.LANCZOS))
    for k,c in zip(("android","chat","monitor"),((666,375),(666,532),(666,689))):im.alpha_composite(icon(k,*c))
    im.save(OUT/f"propda-real-four-{n}.png",optimize=True)
    return im

ims=[
 make(1,[(350,650),(535,420),(535,650)]),
 make(2,[(382,638),(538,455),(538,638)]),
 make(3,[(365,648),(520,450),(548,485),(548,648)],2),
 make(4,[(370,646),(530,440),(530,646)],0,True),
]
sheet=Image.new("RGBA",(1240,1240),(236,237,239,255))
for i,im in enumerate(ims):
    sheet.alpha_composite(im.resize((620,620),Image.Resampling.LANCZOS),((i%2)*620,(i//2)*620))
    ImageDraw.Draw(sheet).text(((i%2)*620+36,(i//2)*620+34),f"0{i+1}",font=font(30),fill=INK)
sheet.save(OUT/"propda-real-four-options.png",optimize=True)
