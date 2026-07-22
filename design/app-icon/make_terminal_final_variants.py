#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
LIGHT=HERE/"propda-minimal-final-light-v23.png"
DARK=HERE/"propda-minimal-final-dark-v23.png"
OUT=HERE/"concepts"/"terminal-final"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255); IVORY=(249,249,247,255)

def font(n):
    for f in ("Arial Bold.ttf","DejaVuSans-Bold.ttf"):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def terminal_tile(size,angle,gap,color):
    sc=6;canvas=180*sc
    im=Image.new("RGBA",(canvas,canvas),(0,0,0,0));d=ImageDraw.Draw(im)
    cx=cy=canvas//2;u=size*sc/100;w=round(7.5*u)
    # Custom geometry gives rounded, equally weighted terminal strokes.
    x0=cx-round(34*u);x1=cx-round(7*u);g=round(gap*u)
    d.line((x0,cy-round(23*u),x1,cy,x0,cy+round(23*u)),fill=color,width=w,joint="curve")
    d.line((x1+g,cy+round(23*u),x1+g+round(34*u),cy+round(23*u)),fill=color,width=w)
    # Round every exposed line cap.
    r=w//2
    for x,y in ((x0,cy-round(23*u)),(x0,cy+round(23*u)),(x1+g,cy+round(23*u)),(x1+g+round(34*u),cy+round(23*u))):
        d.ellipse((x-r,y-r,x+r,y+r),fill=color)
    im=im.rotate(angle,Image.Resampling.BICUBIC,expand=False)
    return im.resize((180,180),Image.Resampling.LANCZOS)

def prepare(path,dark=False):
    im=Image.open(path).convert("RGBA");px=im.load()
    # Replace the four cells inside D with the exact background gradient.
    for y in range(503,572):
        bg=px[20,y]
        for x in range(631,695):px[x,y]=bg
    sc=4;o=Image.new("RGBA",(4320,4320),(0,0,0,0));od=ImageDraw.Draw(o)
    dot_color=IVORY if dark else INK
    for cx in (646,663,680):od.ellipse(((cx-6)*sc,(538-6)*sc,(cx+6)*sc,(538+6)*sc),fill=dot_color)
    im.alpha_composite(o.resize((1080,1080),Image.Resampling.LANCZOS))
    # Remove old pro without touching the approved outer silhouette.
    ImageDraw.Draw(im).rectangle((398,558,550,650),fill=IVORY if dark else INK)
    return im

specs=((76,0,13),(84,0,13),(87,0,13),(91,0,13))
lights=[];darks=[]
for i,(size,angle,gap) in enumerate(specs,1):
    li=prepare(LIGHT);li.alpha_composite(terminal_tile(size,angle,gap,IVORY),(384,516))
    da=prepare(DARK,True);da.alpha_composite(terminal_tile(size,angle,gap,INK),(384,516))
    li.save(OUT/f"propda-terminal-v{i}-light.png",optimize=True)
    da.save(OUT/f"propda-terminal-v{i}-amoled.png",optimize=True)
    lights.append(li);darks.append(da)

sheet=Image.new("RGBA",(2160,1080),(231,232,234,255));sd=ImageDraw.Draw(sheet)
for i,(li,da) in enumerate(zip(lights,darks)):
    x=i*540
    sheet.alpha_composite(li.resize((540,540),Image.Resampling.LANCZOS),(x,0))
    sheet.alpha_composite(da.resize((540,540),Image.Resampling.LANCZOS),(x,540))
    size,angle,gap=specs[i]
    growth=round((size/76-1)*100)
    sd.text((x+22,20),f"0{i+1}  T01 +{growth}%",font=font(24),fill=INK)
    sd.text((x+22,560),f"0{i+1}  AMOLED +{growth}%",font=font(24),fill=(235,235,235,255))
sheet.save(OUT/"propda-terminal-light-amoled-options.png",optimize=True)
