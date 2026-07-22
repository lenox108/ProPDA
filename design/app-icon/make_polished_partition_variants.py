#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
SRC=HERE/"propda-minimal-final-light-v23.png"
OUT=HERE/"concepts"/"polished-partitions"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255); PAPER=(249,249,247,255); LINE=(101,105,113,255); PANEL=(34,37,43,255)

def font(n,b=False):
    for f in (("Arial Bold.ttf","DejaVuSans-Bold.ttf") if b else ("Arial.ttf","DejaVuSans.ttf")):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def layer(): return Image.new("RGBA",(4320,4320),(0,0,0,0))
def P(x): return tuple(round(v*4) for v in x)

def pictogram(kind,cx,cy,size=66):
    o=layer();d=ImageDraw.Draw(o);cx*=4;cy*=4;s=size*4;w=5*4;c=PAPER
    if kind=="android":
        d.arc((cx-s*.4,cy-s*.28,cx+s*.4,cy+s*.5),180,360,fill=c,width=w)
        d.line((cx-s*.4,cy+s*.11,cx+s*.4,cy+s*.11),fill=c,width=w)
        d.line((cx-s*.24,cy-s*.27,cx-s*.36,cy-s*.43),fill=c,width=w);d.line((cx+s*.24,cy-s*.27,cx+s*.36,cy-s*.43),fill=c,width=w)
        for dx in (-.17,.17):d.ellipse((cx+s*dx-3*4,cy-5*4,cx+s*dx+3*4,cy+4),fill=c)
    elif kind=="chat":
        d.rounded_rectangle((cx-s*.43,cy-s*.3,cx+s*.43,cy+s*.25),14*4,outline=c,width=w)
        d.line((cx-s*.18,cy+s*.24,cx-s*.3,cy+s*.42,cx-s*.02,cy+s*.27),fill=c,width=w,joint="curve")
        for dx in (-.18,0,.18):d.ellipse((cx+s*dx-3*4,cy-3*4,cx+s*dx+3*4,cy+3*4),fill=c)
    else:
        d.rounded_rectangle((cx-s*.43,cy-s*.3,cx+s*.43,cy+s*.22),7*4,outline=c,width=w)
        d.line((cx,cy+s*.23,cx,cy+s*.38),fill=c,width=w);d.line((cx-s*.23,cy+s*.38,cx+s*.23,cy+s*.38),fill=c,width=w)
    return o.resize((1080,1080),Image.Resampling.LANCZOS)

def base():
    im=Image.open(SRC).convert("RGBA");d=ImageDraw.Draw(im)
    d.rectangle((400,560,550,648),fill=INK)
    d.rectangle((618,325,708,442),fill=INK);d.rectangle((618,475,708,600),fill=INK);d.rectangle((592,633,711,773),fill=INK)
    return im

def words(im,mode):
    d=ImageDraw.Draw(im)
    if mode==1:
        d.text((382,588),"pro",font=font(43),fill=PAPER)
        for y,t in ((505,"P"),(548,"D"),(591,"A")):d.text((526,y),t,font=font(30,True),fill=PAPER,anchor="ms")
    elif mode==2:
        d.text((386,585),"pro",font=font(45),fill=PAPER)
        d.text((506,584),"P",font=font(39,True),fill=PAPER)
        d.text((526,625),"D",font=font(30,True),fill=PAPER);d.text((526,666),"A",font=font(30,True),fill=PAPER)
    elif mode==3:
        d.text((390,590),"pro",font=font(42),fill=PAPER)
        d.text((492,590),"PDA",font=font(25,True),fill=(181,184,190,255))
    else:
        d.text((397,586),"pro",font=font(42),fill=PAPER)
        for y,t in ((492,"P"),(535,"D"),(578,"A")):d.text((528,y),t,font=font(28,True),fill=PAPER,anchor="ms")

def geometry(im,mode):
    o=layer();d=ImageDraw.Draw(o)
    if mode==1: # inset wedge
        poly=[(342,650),(508,472),(563,519),(563,650)]
        d.polygon([P(p) for p in poly],fill=PANEL)
        d.line([P(p) for p in poly+[poly[0]]],fill=LINE,width=2*4,joint="curve")
        d.line((586*4,305*4,586*4,772*4),fill=LINE,width=2*4)
    elif mode==2: # clean diagonal seam, no box
        d.line([P((350,651)),P((515,476)),P((566,520))],fill=LINE,width=3*4,joint="curve")
        d.line((581*4,303*4,581*4,770*4),fill=LINE,width=2*4)
    elif mode==3: # soft capsule window
        d.rounded_rectangle(P((354,548,555,646)),radius=28*4,fill=PANEL,outline=LINE,width=2*4)
        d.line((584*4,316*4,584*4,759*4),fill=LINE,width=2*4)
    else: # architectural triangular aperture
        poly=[(350,650),(509,478),(559,520),(559,650)]
        d.polygon([P(p) for p in poly],fill=(249,249,247,255))
        inner=[(366,638),(510,493),(544,525),(544,638)]
        d.polygon([P(p) for p in inner],fill=INK)
        d.line((581*4,305*4,581*4,771*4),fill=LINE,width=2*4)
    im.alpha_composite(o.resize((1080,1080),Image.Resampling.LANCZOS))

def make(mode):
    im=base();geometry(im,mode);words(im,mode)
    for k,c in zip(("android","chat","monitor"),((666,375),(666,532),(666,689))):im.alpha_composite(pictogram(k,*c))
    im.save(OUT/f"propda-polished-{mode}.png",optimize=True)
    return im

ims=[make(i) for i in range(1,5)]
sheet=Image.new("RGBA",(1240,1240),(236,237,239,255))
for i,im in enumerate(ims):
    thumb=im.resize((620,620),Image.Resampling.LANCZOS);sheet.alpha_composite(thumb,((i%2)*620,(i//2)*620))
    ImageDraw.Draw(sheet).text(((i%2)*620+36,(i//2)*620+34),f"0{i+1}",font=font(30,True),fill=INK)
sheet.save(OUT/"propda-polished-options.png",optimize=True)
