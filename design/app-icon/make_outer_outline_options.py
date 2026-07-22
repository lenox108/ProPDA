#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE=Path(__file__).resolve().parent
LIGHT=HERE/"propda-terminal-final-light-v24.png"
DARK=HERE/"propda-terminal-final-amoled-v24.png"
ART=HERE.parents[1]/"app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png"
OUT=HERE/"concepts"/"outer-outline"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255)

def font(n):
    for f in ("Arial Bold.ttf","DejaVuSans-Bold.ttf"):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def external_silhouette():
    alpha=Image.open(ART).convert("RGBA").getchannel("A").point(lambda v:255 if v>96 else 0)
    flood=alpha.copy()
    ImageDraw.floodfill(flood,(0,0),128,thresh=0)
    # Everything not reached from the outside is the filled four silhouette;
    # this deliberately excludes internal PDA/terminal counters from outlining.
    return flood.point(lambda v:0 if v==128 else 255)

SIL=external_silhouette()

def outlined(path,radius,dark=False):
    im=Image.open(path).convert("RGBA")
    if radius==0:return im
    dilated=SIL.filter(ImageFilter.MaxFilter(radius*2+1))
    ring=Image.eval(dilated,lambda v:v)
    # Remove the original filled silhouette, retaining only its outside edge.
    from PIL import ImageChops
    ring=ImageChops.subtract(ring,SIL)
    colour=(112,116,125,255) if dark else (83,87,96,255)
    edge=Image.new("RGBA",im.size,colour)
    im.paste(edge,(0,0),ring)
    return im

specs=((0,"NONE"),(4,"HAIRLINE"),(7,"BALANCED"),(10,"ACCENT"))
lights=[];darks=[]
for i,(radius,name) in enumerate(specs,1):
    li=outlined(LIGHT,radius);da=outlined(DARK,radius,True)
    li.save(OUT/f"propda-outline-{i}-{name.lower()}-light.png",optimize=True)
    da.save(OUT/f"propda-outline-{i}-{name.lower()}-amoled.png",optimize=True)
    lights.append(li);darks.append(da)

sheet=Image.new("RGBA",(2160,1080),(231,232,234,255));sd=ImageDraw.Draw(sheet)
for i,((radius,name),li,da) in enumerate(zip(specs,lights,darks)):
    x=i*540
    sheet.alpha_composite(li.resize((540,540),Image.Resampling.LANCZOS),(x,0))
    sheet.alpha_composite(da.resize((540,540),Image.Resampling.LANCZOS),(x,540))
    sd.text((x+20,18),f"0{i+1}  {name}",font=font(24),fill=INK)
    sd.text((x+20,558),f"0{i+1}  AMOLED",font=font(24),fill=(235,235,235,255))
sheet.save(OUT/"propda-outer-outline-options.png",optimize=True)
