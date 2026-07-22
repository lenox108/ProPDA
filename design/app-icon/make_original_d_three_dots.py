#!/usr/bin/env python3
"""Original approved icon with only the D counter changed from 2x2 to three dots."""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
SRC = HERE / "propda-minimal-final-light-v23.png"
OUT = HERE / "concepts" / "original-d-three-dots"
OUT.mkdir(parents=True, exist_ok=True)

im = Image.open(SRC).convert("RGBA")
px = im.load()

# Restore the exact iOS-white vertical gradient behind the four former cells.
# This rectangle remains safely inside the white counter of D.
for y in range(503, 572):
    bg = px[20, y]
    for x in range(631, 695):
        px[x, y] = bg

# Draw three optically centred forum dots at 4x and downsample for clean edges.
scale = 4
overlay = Image.new("RGBA", (1080 * scale, 1080 * scale), (0, 0, 0, 0))
d = ImageDraw.Draw(overlay)
ink = (23, 25, 29, 255)
for cx in (646, 663, 680):
    r = 6
    d.ellipse(((cx-r)*scale, (538-r)*scale, (cx+r)*scale, (538+r)*scale), fill=ink)
im.alpha_composite(overlay.resize((1080, 1080), Image.Resampling.LANCZOS))

im.save(OUT / "propda-original-d-three-dots-1080.png", optimize=True)
im.crop((250, 190, 830, 870)).resize((870, 1020), Image.Resampling.LANCZOS).save(
    OUT / "propda-original-d-three-dots-preview.png", optimize=True
)
