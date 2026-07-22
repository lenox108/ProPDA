#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
SRC=HERE/"concepts"/"original-d-three-dots"/"propda-original-d-three-dots-1080.png"
OUT=HERE/"concepts"/"code-forum"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255);WHITE=(249,249,247,255)

def font(n,b=False):
    for f in (("Arial Bold.ttf","DejaVuSans-Bold.ttf") if b else ("Arial.ttf","DejaVuSans.ttf")):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def mark(text):
    sc=4;size=180*sc
    tile=Image.new("RGBA",(size,size),(0,0,0,0));d=ImageDraw.Draw(tile)
    d.text((size//2,size//2),text,font=font(58*sc,True),fill=WHITE,anchor="mm")
    tile=tile.rotate(32,Image.Resampling.BICUBIC,expand=False)
    return tile.resize((180,180),Image.Resampling.LANCZOS)

codes=(">_","#_","$_","~_","./","</>","{}","01")
ims=[]
for i,code in enumerate(codes,1):
    im=Image.open(SRC).convert("RGBA");d=ImageDraw.Draw(im)
    d.rectangle((392,552,556,656),fill=INK)
    im.alpha_composite(mark(code),(386,516))
    im.save(OUT/f"propda-code-forum-{i}.png",optimize=True);ims.append(im)

sheet=Image.new("RGBA",(2160,1080),(238,239,241,255));sd=ImageDraw.Draw(sheet)
for i,(im,code) in enumerate(zip(ims,codes)):
    x=(i%4)*540;y=(i//4)*540
    sheet.alpha_composite(im.resize((540,540),Image.Resampling.LANCZOS),(x,y))
    sd.text((x+27,y+24),f"0{i+1}  {code}",font=font(26,True),fill=INK)
sheet.save(OUT/"propda-code-forum-options.png",optimize=True)
