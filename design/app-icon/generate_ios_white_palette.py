#!/usr/bin/env python3
"""White and near-white iOS tile treatments for ProPDA."""

from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
MASK=ROOT/"app/src/main/res/drawable-nodpi/ic_launcher_monochrome_art.png"
OUTPUT=HERE/"propda-ios-white-palettes-v28.png"

PALETTES=[
 ("W1 PURE WHITE","#FFFFFF","#F4F4F2","#050505"),
 ("W2 iOS WHITE","#FFFFFF","#ECEEF1","#17191D"),
 ("W3 PORCELAIN","#FFFEFC","#EEEAE3","#191919"),
 ("W4 SNOW BLUE","#FFFFFF","#EAF1F7","#15283C"),
 ("W5 PEARL","#FFFFFF","#E7E7E5","#24262B"),
 ("W6 SOFT IVORY","#FFFDF8","#F0E9DD","#201D19"),
 ("W7 CERAMIC","#FAFAFA","#E5E2DE","#2A2724"),
 ("W8 TITANIUM WHITE","#FDFDFD","#E2E4E7","#30343A"),
]

def font(size,bold=False):
 for name in ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf","Arial.ttf"):
  try:return ImageFont.truetype(name,size)
  except OSError:pass
 return ImageFont.load_default()

def c(v):
 v=v.lstrip('#');return np.array([int(v[i:i+2],16) for i in (0,2,4)],dtype=float)

def tile(alpha,top,bottom,ink,size=280):
 yy,xx=np.mgrid[0:size,0:size];t=(yy/(size-1))[...,None]
 pixels=c(top)*(1-t)+c(bottom)*t
 # Very subtle ceramic highlight.
 dist=np.sqrt(((xx-size*.23)/(size*.9))**2+((yy-size*.04)/(size*.7))**2)
 pixels=np.clip(pixels+np.clip(1-dist,0,1)[...,None]*4,0,255).astype(np.uint8)
 out=Image.fromarray(pixels,'RGB').convert('RGBA')
 a=alpha.resize((size,size),Image.Resampling.LANCZOS)
 mark=Image.new('RGBA',(size,size),(*tuple(c(ink).astype(int)),0));mark.putalpha(a);out.alpha_composite(mark)
 shape=Image.new('L',(size,size),0);ImageDraw.Draw(shape).rounded_rectangle((0,0,size-1,size-1),radius=round(size*.225),fill=255);out.putalpha(shape)
 edge=Image.new('RGBA',(size,size),(0,0,0,0));ImageDraw.Draw(edge).rounded_rectangle((1,1,size-2,size-2),radius=round(size*.223),outline=(255,255,255,210),width=2);out.alpha_composite(edge)
 return out

def main():
 alpha=Image.open(MASK).getchannel('A')
 board=Image.new('RGB',(1240,720),'#DADBDB');d=ImageDraw.Draw(board)
 d.text((40,22),'ProPDA — white iOS directions',font=font(32,True),fill='#1D1F23')
 for i,(name,top,bottom,ink) in enumerate(PALETTES):
  col,row=i%4,i//4;x=40+col*300;y=78+row*315
  icon=tile(alpha,top,bottom,ink);board.paste(icon,(x,y),icon)
  d.text((x,y+285),name,font=font(16,True),fill='#25272C')
 board.save(OUTPUT)

if __name__=='__main__':main()
