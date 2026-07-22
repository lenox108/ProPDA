#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE=Path(__file__).resolve().parent
LIGHT=HERE/"propda-terminal-final-light-v24.png"
DARK=HERE/"propda-terminal-final-amoled-v24.png"
OUT=HERE/"concepts"/"pda-weight"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255);IVORY=(249,249,247,255)
BOXES=((610,315,716,449),(610,466,716,610),(584,624,720,781))

def font(n):
    for f in ("Arial Bold.ttf","DejaVuSans-Bold.ttf"):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def adjust(path,scale,erosion,dark=False):
    src=Image.open(path).convert("RGBA");out=src.copy();od=ImageDraw.Draw(out)
    mark=IVORY if dark else INK
    for box in BOXES:
        x0,y0,x1,y1=box;crop=src.crop(box)
        gray=crop.convert("L")
        # Isolate the negative letter counter, preserving the three D dots.
        mask=gray.point((lambda v:255 if v<80 else 0) if dark else (lambda v:255 if v>180 else 0))
        if erosion:
            mask=mask.filter(ImageFilter.MinFilter(erosion))
        nw=max(1,round(mask.width*scale));nh=max(1,round(mask.height*scale))
        mask=mask.resize((nw,nh),Image.Resampling.LANCZOS)
        od.rectangle(box,fill=mark)
        px=round((x0+x1-nw)/2);py=round((y0+y1-nh)/2)
        # Recreate the exact vertical background gradient through the counter.
        bg=Image.new("RGBA",(nw,nh))
        bp=bg.load()
        for yy in range(nh):
            sample_y=min(1079,max(0,py+yy));color=src.getpixel((20,sample_y))
            for xx in range(nw):bp[xx,yy]=color
        out.paste(bg,(px,py),mask)
    return out

specs=((1.00,0,"CURRENT"),(.96,3,"LIGHT"),(.92,3,"BALANCED"),(.88,5,"SLIM"))
lights=[];darks=[]
for i,(scale,erode,name) in enumerate(specs,1):
    li=adjust(LIGHT,scale,erode);da=adjust(DARK,scale,erode,True)
    li.save(OUT/f"propda-pda-{i}-{name.lower()}-light.png",optimize=True)
    da.save(OUT/f"propda-pda-{i}-{name.lower()}-amoled.png",optimize=True)
    lights.append(li);darks.append(da)

sheet=Image.new("RGBA",(2160,1080),(231,232,234,255));sd=ImageDraw.Draw(sheet)
for i,((scale,erode,name),li,da) in enumerate(zip(specs,lights,darks)):
    x=i*540
    sheet.alpha_composite(li.resize((540,540),Image.Resampling.LANCZOS),(x,0))
    sheet.alpha_composite(da.resize((540,540),Image.Resampling.LANCZOS),(x,540))
    sd.text((x+20,18),f"0{i+1}  {name}",font=font(24),fill=INK)
    sd.text((x+20,558),f"0{i+1}  AMOLED",font=font(24),fill=(235,235,235,255))
sheet.save(OUT/"propda-pda-weight-options.png",optimize=True)
