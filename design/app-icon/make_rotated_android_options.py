#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
SRC=HERE/"concepts"/"original-d-three-dots"/"propda-original-d-three-dots-1080.png"
OUT=HERE/"concepts"/"rotated-android"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255);WHITE=(249,249,247,255)

def font(n):
    for f in ("Arial Bold.ttf","DejaVuSans-Bold.ttf"):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def robot(angle):
    sc=4;size=150*sc
    tile=Image.new("RGBA",(size,size),(0,0,0,0));d=ImageDraw.Draw(tile)
    cx=cy=size//2;s=82*sc;w=5*sc;c=WHITE
    d.arc((cx-s*.4,cy-s*.30,cx+s*.4,cy+s*.48),180,360,fill=c,width=w)
    d.line((cx-s*.4,cy+s*.10,cx+s*.4,cy+s*.10),fill=c,width=w)
    d.line((cx-s*.23,cy-s*.29,cx-s*.34,cy-s*.44),fill=c,width=w)
    d.line((cx+s*.23,cy-s*.29,cx+s*.34,cy-s*.44),fill=c,width=w)
    for dx in (-.16,.16):d.ellipse((cx+s*dx-3*sc,cy-5*sc,cx+s*dx+3*sc,cy+sc),fill=c)
    tile=tile.rotate(angle,Image.Resampling.BICUBIC,expand=False)
    return tile.resize((150,150),Image.Resampling.LANCZOS)

def code_mark(angle):
    sc=4;size=150*sc
    tile=Image.new("RGBA",(size,size),(0,0,0,0));d=ImageDraw.Draw(tile)
    d.text((size//2,size//2),"</>",font=font(54*sc),fill=WHITE,anchor="mm")
    tile=tile.rotate(angle,Image.Resampling.BICUBIC,expand=False)
    return tile.resize((150,150),Image.Resampling.LANCZOS)

angles=(0,32,43,52)
ims=[];codes=[]
for i,a in enumerate(angles,1):
    im=Image.open(SRC).convert("RGBA");d=ImageDraw.Draw(im)
    d.rectangle((398,558,550,650),fill=INK)
    im.alpha_composite(robot(a),(399,531))
    im.save(OUT/f"propda-android-rotate-{a}.png",optimize=True);ims.append(im)

    code=Image.open(SRC).convert("RGBA");cd=ImageDraw.Draw(code)
    cd.rectangle((398,558,550,650),fill=INK)
    code.alpha_composite(code_mark(a),(399,531))
    code.save(OUT/f"propda-code-rotate-{a}.png",optimize=True);codes.append(code)

sheet=Image.new("RGBA",(2160,1080),(238,239,241,255));sd=ImageDraw.Draw(sheet)
for i,(im,a) in enumerate(zip(ims,angles)):
    sheet.alpha_composite(im.resize((540,540),Image.Resampling.LANCZOS),(i*540,0))
    sheet.alpha_composite(codes[i].resize((540,540),Image.Resampling.LANCZOS),(i*540,540))
    sd.text((i*540+28,24),f"A{i+1}  {a}°",font=font(26),fill=INK)
    sd.text((i*540+28,564),f"C{i+1}  {a}°",font=font(26),fill=INK)
sheet.save(OUT/"propda-rotated-symbol-options.png",optimize=True)
