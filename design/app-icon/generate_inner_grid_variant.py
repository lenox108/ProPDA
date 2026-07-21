#!/usr/bin/env python3
"""Alternative P2: continuous ring and a small apps grid inside the D counter."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter


HERE = Path(__file__).resolve().parent
SOURCE = HERE / "propda-p2-refined-nodes-v20.png"
OUTPUT = HERE / "propda-p2-inner-grid-variant-v22.png"

S = 4  # SOURCE was built from the 380px P2 geometry at 4x.
BG = (246, 242, 239)
INK = (0, 0, 0)


def q(value):
    return round(value * S)


def main():
    source = Image.open(SOURCE).convert("RGB")
    out = source.copy()

    # Remove the previous ring and all four satellite nodes, including their
    # antialiased outer pixels, while leaving the central P2 mark untouched.
    erase = Image.new("L", out.size, 0)
    ed = ImageDraw.Draw(erase)
    ed.ellipse(tuple(q(v) for v in (15, -2, 366, 357)), fill=255)
    ed.ellipse(tuple(q(v) for v in (53, 36, 328, 317)), fill=0)
    for cx, cy in ((70, 54), (317, 54), (70, 302), (317, 302)):
        ed.ellipse(tuple(q(v) for v in (cx - 58, cy - 58, cx + 58, cy + 58)), fill=255)
    erase = erase.filter(ImageFilter.GaussianBlur(q(.7)))
    out.paste(Image.new("RGB", out.size, BG), (0, 0), erase)

    # One uninterrupted, high-resolution AMOLED-black ring.
    d = ImageDraw.Draw(out)
    d.ellipse(tuple(q(v) for v in (34, 17, 347, 336)), outline=INK, width=q(10))

    # Four restrained rounded squares inside the D counter. They are small
    # enough to preserve D readability and reuse the apps-grid visual language.
    for x in (205.5, 218.0):
        for y in (171.5, 184.0):
            d.rounded_rectangle(
                tuple(q(v) for v in (x, y, x + 7.0, y + 7.0)),
                radius=q(1.5),
                fill=INK,
            )

    # Match the installed AMOLED treatment: no grey or soft halo remains.
    gray = out.convert("L").filter(ImageFilter.GaussianBlur(1.1))
    alpha = gray.point(
        lambda v: 0 if v >= 212 else 255 if v <= 155 else round((212 - v) * 255 / 57)
    )
    final = Image.new("RGB", out.size, BG)
    black = Image.new("RGB", out.size, INK)
    final.paste(black, (0, 0), alpha)
    final.save(OUTPUT)


if __name__ == "__main__":
    main()
