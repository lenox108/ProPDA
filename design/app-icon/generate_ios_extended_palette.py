#!/usr/bin/env python3
"""Extended iOS-style colour directions for the minimal ProPDA icon."""

from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MASK_SOURCE = ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_monochrome_art.png"
OUTPUT = HERE / "propda-ios-extended-palettes-v26.png"

PALETTES = [
    ("C01 COBALT", "#315EBC", "#091A42", "#F1F5FF"),
    ("C02 SAPPHIRE", "#243F78", "#080F22", "#EDF2FC"),
    ("C03 ARCTIC", "#347A91", "#071B25", "#EAF7FA"),
    ("C04 OCEAN", "#176A86", "#061D2A", "#E7F5F8"),
    ("C05 BLUE GREY", "#536A7D", "#131C25", "#F0F3F5"),
    ("C06 EMERALD", "#23705F", "#061E19", "#E8F5F0"),
    ("C07 FOREST", "#365D45", "#0C1A10", "#EEF4EA"),
    ("C08 JADE", "#3B766F", "#0B2321", "#EBF5F2"),
    ("C09 VIOLET", "#63549B", "#19132F", "#F3EFFB"),
    ("C10 PLUM", "#704767", "#21101E", "#F7EDF4"),
    ("C11 WINE", "#713D4B", "#230C13", "#F8ECEE"),
    ("C12 CRIMSON", "#873C48", "#290A0E", "#FBEDEF"),
    ("C13 COPPER", "#8B5A3C", "#28150C", "#F7EEE7"),
    ("C14 MOCHA", "#675348", "#1C1512", "#F3ECE7"),
    ("C15 GUNMETAL", "#4D545D", "#111419", "#F2F3F4"),
]


def font(size, bold=False):
    for name in ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf", "Arial.ttf"):
        try: return ImageFont.truetype(name, size)
        except OSError: pass
    return ImageFont.load_default()


def colour(value):
    value=value.lstrip("#")
    return np.array([int(value[i:i+2],16) for i in (0,2,4)],dtype=float)


def tile(alpha, top, bottom, ink, size=220):
    yy,xx=np.mgrid[0:size,0:size]
    t=(yy/(size-1))[...,None]
    pixels=colour(top)*(1-t)+colour(bottom)*t
    distance=np.sqrt(((xx-size*.25)/(size*.82))**2+((yy-size*.1)/(size*.82))**2)
    pixels=np.clip(pixels+np.clip(1-distance,0,1)[...,None]*14,0,255).astype(np.uint8)
    result=Image.fromarray(pixels,"RGB").convert("RGBA")
    a=alpha.resize((size,size),Image.Resampling.LANCZOS)
    mark=Image.new("RGBA",(size,size),(*tuple(colour(ink).astype(int)),0));mark.putalpha(a);result.alpha_composite(mark)
    mask=Image.new("L",(size,size),0);ImageDraw.Draw(mask).rounded_rectangle((0,0,size-1,size-1),radius=round(size*.225),fill=255);result.putalpha(mask)
    highlight=Image.new("RGBA",(size,size),(0,0,0,0));ImageDraw.Draw(highlight).rounded_rectangle((2,2,size-3,size-3),radius=round(size*.22),outline=(255,255,255,38),width=2);result.alpha_composite(highlight)
    return result


def main():
    alpha=Image.open(MASK_SOURCE).getchannel("A")
    board=Image.new("RGB",(1240,850),"#E9E9E7");d=ImageDraw.Draw(board)
    d.text((40,22),"ProPDA — extended iOS colour palette",font=font(32,True),fill="#1E2025")
    for i,(name,top,bottom,ink) in enumerate(PALETTES):
        col,row=i%5,i//5;x=40+col*240;y=78+row*255
        icon=tile(alpha,top,bottom,ink);board.paste(icon,(x,y),icon)
        d.text((x,y+224),name,font=font(15,True),fill="#25272C")
    board.save(OUTPUT)


if __name__=="__main__":main()
