#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
SRC=HERE/"concepts"/"original-d-three-dots"/"propda-original-d-three-dots-1080.png"
OUT=HERE/"concepts"/"pro-replacements"
OUT.mkdir(parents=True,exist_ok=True)
INK=(23,25,29,255); WHITE=(249,249,247,255)

def font(n,b=False):
    for f in (("Arial Bold.ttf","DejaVuSans-Bold.ttf") if b else ("Arial.ttf","DejaVuSans.ttf")):
        try:return ImageFont.truetype(f,n)
        except OSError:pass
    return ImageFont.load_default()

def symbol(kind):
    im=Image.open(SRC).convert("RGBA");d=ImageDraw.Draw(im)
    d.rectangle((398,558,550,650),fill=INK)
    sc=4;o=Image.new("RGBA",(4320,4320),(0,0,0,0));q=ImageDraw.Draw(o)
    cx,cy=474*sc,606*sc;c=WHITE;w=5*sc
    if kind=="android":
        s=78*sc;q.arc((cx-s*.4,cy-s*.30,cx+s*.4,cy+s*.48),180,360,fill=c,width=w)
        q.line((cx-s*.4,cy+s*.10,cx+s*.4,cy+s*.10),fill=c,width=w)
        q.line((cx-s*.23,cy-s*.29,cx-s*.34,cy-s*.44),fill=c,width=w);q.line((cx+s*.23,cy-s*.29,cx+s*.34,cy-s*.44),fill=c,width=w)
        for dx in (-.16,.16):q.ellipse((cx+s*dx-3*sc,cy-5*sc,cx+s*dx+3*sc,cy+sc),fill=c)
    elif kind=="pro":
        d.text((474,606),"PRO",font=font(44,True),fill=c,anchor="mm")
    elif kind=="code":
        d.text((474,604),"</>",font=font(52),fill=c,anchor="mm")
    elif kind=="terminal":
        d.text((474,604),">_",font=font(54,True),fill=c,anchor="mm")
    elif kind=="braces":
        d.text((474,604),"{ }",font=font(52,True),fill=c,anchor="mm")
    elif kind=="binary":
        d.text((474,604),"01",font=font(51,True),fill=c,anchor="mm")
    elif kind=="hash":
        d.text((474,604),"#",font=font(58,True),fill=c,anchor="mm")
    elif kind=="chip":
        q.rounded_rectangle((cx-24*sc,cy-24*sc,cx+24*sc,cy+24*sc),6*sc,outline=c,width=w)
        q.rounded_rectangle((cx-10*sc,cy-10*sc,cx+10*sc,cy+10*sc),2*sc,outline=c,width=4*sc)
        for k in (-15,0,15):
            q.line((cx+k*sc,cy-34*sc,cx+k*sc,cy-25*sc),fill=c,width=4*sc);q.line((cx+k*sc,cy+25*sc,cx+k*sc,cy+34*sc),fill=c,width=4*sc)
    elif kind=="globe":
        q.ellipse((cx-29*sc,cy-29*sc,cx+29*sc,cy+29*sc),outline=c,width=w)
        q.ellipse((cx-13*sc,cy-29*sc,cx+13*sc,cy+29*sc),outline=c,width=4*sc)
        q.line((cx-27*sc,cy,cx+27*sc,cy),fill=c,width=4*sc)
    elif kind=="phone":
        q.rounded_rectangle((cx-18*sc,cy-31*sc,cx+18*sc,cy+31*sc),7*sc,outline=c,width=w)
        q.line((cx-7*sc,cy+22*sc,cx+7*sc,cy+22*sc),fill=c,width=4*sc)
    elif kind=="monitor":
        q.rounded_rectangle((cx-35*sc,cy-24*sc,cx+35*sc,cy+21*sc),7*sc,outline=c,width=w)
        q.line((cx,cy+22*sc,cx,cy+34*sc),fill=c,width=w);q.line((cx-18*sc,cy+34*sc,cx+18*sc,cy+34*sc),fill=c,width=w)
    elif kind=="browser":
        q.rounded_rectangle((cx-35*sc,cy-28*sc,cx+35*sc,cy+28*sc),7*sc,outline=c,width=w)
        q.line((cx-34*sc,cy-12*sc,cx+34*sc,cy-12*sc),fill=c,width=4*sc)
        for k in (-22,-10,2):q.ellipse((cx+k*sc-2*sc,cy-21*sc,cx+k*sc+2*sc,cy-17*sc),fill=c)
    elif kind=="plus":
        q.ellipse((cx-29*sc,cy-29*sc,cx+29*sc,cy+29*sc),outline=c,width=w)
        q.line((cx-13*sc,cy,cx+13*sc,cy),fill=c,width=w);q.line((cx,cy-13*sc,cx,cy+13*sc),fill=c,width=w)
    if kind not in ("pro","code","terminal","braces","binary","hash","empty"):
        im.alpha_composite(o.resize((1080,1080),Image.Resampling.LANCZOS))
    return im

kinds=("android","code","terminal","braces","binary","hash","chip","browser","monitor","globe","phone","empty")
ims=[]
for i,k in enumerate(kinds,1):
    im=symbol(k);im.save(OUT/f"propda-pro-replacement-{i}-{k}.png",optimize=True);ims.append(im)

sheet=Image.new("RGBA",(2160,1620),(238,239,241,255));sd=ImageDraw.Draw(sheet)
for i,im in enumerate(ims):
    sheet.alpha_composite(im.resize((540,540),Image.Resampling.LANCZOS),((i%4)*540,(i//4)*540))
    sd.text(((i%4)*540+28,(i//4)*540+25),f"0{i+1}",font=font(28,True),fill=INK)
sheet.save(OUT/"propda-pro-replacement-options.png",optimize=True)
