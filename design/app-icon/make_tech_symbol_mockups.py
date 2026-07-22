#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent / "concepts"
OUT.mkdir(parents=True, exist_ok=True)
S = 760
SS = 1

def font(sz, bold=False):
    names = ("Arial Bold.ttf", "DejaVuSans-Bold.ttf") if bold else ("Arial.ttf", "DejaVuSans.ttf")
    for n in names:
        try: return ImageFont.truetype(n, sz)
        except OSError: pass
    return ImageFont.load_default()

def line(d, xy, fill, w): d.line(xy, fill=fill, width=w, joint="curve")

def grid(d, cx, cy, c, w):
    q=16
    for dx in (-q, q):
        for dy in (-q, q):
            d.rounded_rectangle((cx+dx-10,cy+dy-10,cx+dx+10,cy+dy+10),5,outline=c,width=w)

def app_window(d, cx, cy, c, w):
    d.rounded_rectangle((cx-27,cy-21,cx+27,cy+21),9,outline=c,width=w)
    for dx in (-9,9):
        for dy in (-7,7):
            d.rounded_rectangle((cx+dx-4,cy+dy-4,cx+dx+4,cy+dy+4),2,fill=c)

def chat(d,cx,cy,c,w):
    d.rounded_rectangle((cx-38,cy-27,cx+38,cy+24),15,outline=c,width=w)
    line(d,[(cx-19,cy+23),(cx-30,cy+39),(cx-3,cy+25)],c,w)
    for x in (-17,0,17): d.ellipse((cx+x-3,cy-4,cx+x+3,cy+2),fill=c)

def phone(d,cx,cy,c,w):
    d.rounded_rectangle((cx-23,cy-38,cx+23,cy+38),8,outline=c,width=w)
    line(d,[(cx-9,cy+27),(cx+9,cy+27)],c,w)

def monitor(d,cx,cy,c,w):
    d.rounded_rectangle((cx-39,cy-29,cx+39,cy+22),7,outline=c,width=w)
    line(d,[(cx,cy+23),(cx,cy+37),(cx-22,cy+37),(cx+22,cy+37)],c,w)

def chip(d,cx,cy,c,w):
    d.rounded_rectangle((cx-25,cy-25,cx+25,cy+25),6,outline=c,width=w)
    d.rounded_rectangle((cx-11,cy-11,cx+11,cy+11),3,outline=c,width=w)
    for k in (-16,0,16):
        line(d,[(cx+k,cy-35),(cx+k,cy-26)],c,w); line(d,[(cx+k,cy+26),(cx+k,cy+35)],c,w)
        line(d,[(cx-35,cy+k),(cx-26,cy+k)],c,w); line(d,[(cx+26,cy+k),(cx+35,cy+k)],c,w)

def globe(d,cx,cy,c,w):
    d.ellipse((cx-34,cy-34,cx+34,cy+34),outline=c,width=w)
    d.ellipse((cx-16,cy-34,cx+16,cy+34),outline=c,width=w)
    line(d,[(cx-31,cy-12),(cx+31,cy-12)],c,w); line(d,[(cx-31,cy+12),(cx+31,cy+12)],c,w)

def android(d,cx,cy,c,w):
    d.pieslice((cx-37,cy-25,cx+37,cy+45),180,360,outline=c,width=w)
    line(d,[(cx-37,cy+10),(cx+37,cy+10)],c,w)
    line(d,[(cx-23,cy-25),(cx-34,cy-42)],c,w); line(d,[(cx+23,cy-25),(cx+34,cy-42)],c,w)
    d.ellipse((cx-18,cy-9,cx-12,cy-3),fill=c); d.ellipse((cx+12,cy-9,cx+18,cy-3),fill=c)

ICONS={"grid":grid,"chat":chat,"phone":phone,"monitor":monitor,"chip":chip,"globe":globe,"android":android}

def card(symbols, num, caption):
    im=Image.new("RGB",(S*SS,S*SS),(244,244,244)); d=ImageDraw.Draw(im)
    bg=(249,247,242); ink=(24,25,29); soft=(219,221,225)
    d.rounded_rectangle((35*SS,35*SS,(S-35)*SS,(S-35)*SS),145*SS,fill=bg,outline=soft,width=2*SS)
    # Stable ProPDA four silhouette.
    poly=[(180,430),(180,380),(365,155),(463,155),(463,342),(555,342),(555,435),(463,435),(463,584),(360,584),(360,435)]
    d.polygon([(x*SS,y*SS) for x,y in poly],fill=ink)
    d.rounded_rectangle((180*SS,375*SS,463*SS,435*SS),25*SS,fill=ink)
    # Inner counter of the four.
    d.polygon([(286*SS,342*SS),(360*SS,245*SS),(360*SS,342*SS)],fill=bg)
    # Technology pictograms occupy the right stem and share one stroke system.
    white=bg; x=412*SS
    for y,name in zip((225,355,500),symbols): ICONS[name](d,x,y*SS,white,5*SS)
    # Small four-pane app window above the pro signature.
    app_window(d,265*SS,366*SS,white,4*SS)
    d.text((242*SS,397*SS),"pro",font=font(35*SS),fill=(125,127,132),anchor="la")
    d.text((56*SS,57*SS),f"0{num}",font=font(34*SS,True),fill=ink)
    d.text((S//2*SS,680*SS),caption,font=font(27*SS,True),fill=ink,anchor="mm")
    return im.resize((S,S),Image.Resampling.LANCZOS)

variants=[
    (("android","chat","phone"),1,"ANDROID · FORUM · PHONE"),
    (("grid","chat","monitor"),2,"APPS · FORUM · COMPUTER"),
    (("chip","chat","phone"),3,"TECH · FORUM · MOBILE"),
    (("globe","monitor","phone"),4,"WEB · COMPUTER · MOBILE"),
]

sheet=Image.new("RGB",(S*2,S*2),(232,233,235))
for i,(symbols,n,title) in enumerate(variants):
    icon=card(symbols,n,title); icon.save(OUT/f"propda-tech-concept-{n}.png",optimize=True)
    sheet.paste(icon,((i%2)*S,(i//2)*S))
sheet.save(OUT/"propda-tech-symbols-contact-sheet.png",optimize=True)
