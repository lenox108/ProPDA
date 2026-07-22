#!/usr/bin/env python3
"""Exact current icon with a unified horizontal/vertical proPDA lockup."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "propda-minimal-final-light-v23.png"
OUT = HERE / "concepts" / "exact-pda-replacements"
OUT.mkdir(parents=True, exist_ok=True)
INK = (23, 25, 29, 255)
PAPER = (249, 249, 247, 255)

def font(size, bold=False):
    names = ("Arial Bold.ttf", "DejaVuSans-Bold.ttf") if bold else ("Arial.ttf", "DejaVuSans.ttf")
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()

im = Image.open(SOURCE).convert("RGBA")
d = ImageDraw.Draw(im)

# Remove only the old pro and the three PDA counters. The outer four remains
# byte-for-byte identical to the currently approved icon.
d.rectangle((402, 565, 548, 646), fill=INK)
d.rectangle((618, 325, 708, 442), fill=INK)
d.rectangle((618, 475, 708, 600), fill=INK)
d.rectangle((592, 633, 711, 773), fill=INK)

# One continuous typographic signature: pro flows into P; D and A share its
# vertical axis. Slightly softened white keeps it subordinate to the four.
d.text((436, 568), "pro", font=font(61), fill=PAPER)
d.text((556, 563), "P", font=font(70, True), fill=PAPER)
d.text((556, 630), "D", font=font(70, True), fill=PAPER)
d.text((556, 697), "A", font=font(70, True), fill=PAPER)

out = OUT / "propda-exact-connected-lockup.png"
im.save(out, optimize=True)

# Close presentation crop; the icon itself is not rescaled or redrawn.
preview = im.crop((250, 190, 830, 870)).resize((870, 1020), Image.Resampling.LANCZOS)
preview.save(OUT / "propda-exact-connected-lockup-preview.png", optimize=True)
