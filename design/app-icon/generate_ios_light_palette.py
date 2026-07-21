#!/usr/bin/env python3
"""Light iOS-style colour directions for the minimal ProPDA icon."""

from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
MASK_SOURCE=ROOT/"app/src/main/res/drawable-nodpi/ic_launcher_monochrome_art.png"
OUTPUT=HERE/"propda-ios-light-palettes-v27.png"

PALETTES=[
 ("L01 PEARL","#FFFFFF","#E8E8E5","#17191D"),
 ("L02 ICE BLUE","#F3F8FF","#D5E3F5","#18304F"),
 ("L03 SKY","#EEF8FF","#C9E5F4","#17415C"),
 ("L04 MIST","#F3F7F8","#D6E1E5","#263944"),
 ("L05 MINT","#F1FBF7","#CDE9DD","#17463A"),
 ("L06 SAGE","#F3F6EF","#D3DDC9","#344634"),
 ("L07 AQUA","#EEFAFA","#C9E7E5","#184847"),
 ("L08 LAVENDER","#F7F4FF","#DDD5F0","#3C315D"),
 ("L09 LILAC","#FBF3FC","#E8D4EA","#543450"),
 ("L10 BLUSH","#FFF5F6","#EED6D9","#62343D"),
 ("L11 ROSE","#FFF2F2","#E9CDCF","#6A3038"),
 ("L12 PEACH","#FFF7F0","#ECD8C8","#68402B"),
 ("L13 CHAMPAGNE","#FFF9EC","#E8D9B9","#554124"),
 ("L14 SILVER","#F7F7F8","#D7D9DD","#2E3239"),
 ("L15 WARM GREY","#FAF8F5","#DED8D0","#38332E"),
]

def font(size,bold=False):
 for name in ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf","Arial.ttf"):
  try:return ImageFont.truetype(name,size)
  except OSError:pass
 return ImageFont.load_default()

def colour(v):
 v=v.lstrip('#');return np.array([int(v[i:i+2],16) for i in (0,2,4)],dtype=float)

def tile(alpha,top,bottom,ink,size=220):
 yy,xx=np.mgrid[0:size,0:size];t=(yy/(size-1))[...,None]
 pixels=colour(top)*(1-t)+colour(bottom)*t
 # Soft white reflection across the upper-left surface.
 dist=np.sqrt(((xx-size*.22)/(size*.9))**2+((yy-size*.04)/(size*.72))**2)
 pixels=np.clip(pixels+np.clip(1-dist,0,1)[...,None]*7,0,255).astype(np.uint8)
 result=Image.fromarray(pixels,'RGB').convert('RGBA')
 a=alpha.resize((size,size),Image.Resampling.LANCZOS)
 mark=Image.new('RGBA',(size,size),(*tuple(colour(ink).astype(int)),0));mark.putalpha(a);result.alpha_composite(mark)
 mask=Image.new('L',(size,size),0);ImageDraw.Draw(mask).rounded_rectangle((0,0,size-1,size-1),radius=round(size*.225),fill=255);result.putalpha(mask)
 edge=Image.new('RGBA',(size,size),(0,0,0,0));ImageDraw.Draw(edge).rounded_rectangle((2,2,size-3,size-3),radius=round(size*.22),outline=(255,255,255,130),width=2);result.alpha_composite(edge)
 return result

def main():
 alpha=Image.open(MASK_SOURCE).getchannel('A')
 board=Image.new('RGB',(1240,850),'#E6E6E4');d=ImageDraw.Draw(board)
 d.text((40,22),'ProPDA — light iOS colour palette',font=font(32,True),fill='#1E2025')
 for i,(name,top,bottom,ink) in enumerate(PALETTES):
  col,row=i%5,i//5;x=40+col*240;y=78+row*255
  icon=tile(alpha,top,bottom,ink);board.paste(icon,(x,y),icon)
  d.text((x,y+224),name,font=font(15,True),fill='#25272C')
 board.save(OUTPUT)

if __name__=='__main__':main()
