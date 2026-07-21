#!/usr/bin/env python3
"""iOS-inspired dark squircle treatments for the minimal ProPDA mark."""

from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MASK_SOURCE = ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_monochrome_art.png"
OUTPUT = HERE / "propda-ios-style-directions-v25.png"

PALETTES = [
    ("I1  SPACE BLACK", "#343840", "#090B0E", "#F6F5F2"),
    ("I2  MIDNIGHT BLUE", "#2B3C58", "#07111F", "#F0F3F8"),
    ("I3  TITANIUM", "#55575D", "#17181C", "#F5F1E9"),
    ("I4  DEEP TEAL", "#295054", "#071B1D", "#EAF3F0"),
    ("I5  DARK INDIGO", "#44436B", "#111023", "#F1EFF8"),
    ("I6  GRAPHITE BLUE", "#3C4956", "#111820", "#EDF1F4"),
]


def font(size, bold=False):
    for name in ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf", "Arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def color(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i+2], 16) for i in (0, 2, 4)], dtype=float)


def ios_tile(alpha, top, bottom, ink, size=380):
    yy, xx = np.mgrid[0:size, 0:size]
    vertical = (yy / (size - 1))[..., None]
    rgb = color(top) * (1 - vertical) + color(bottom) * vertical

    # A restrained iOS-like top-left highlight, not a visible glow.
    distance = np.sqrt(((xx - size*.24)/(size*.82))**2 + ((yy - size*.12)/(size*.82))**2)
    highlight = np.clip(1 - distance, 0, 1)[..., None] * 13
    rgb = np.clip(rgb + highlight, 0, 255).astype(np.uint8)
    tile = Image.fromarray(rgb, "RGB").convert("RGBA")

    mark_alpha = alpha.resize((size, size), Image.Resampling.LANCZOS)
    mark = Image.new("RGBA", (size, size), (*tuple(color(ink).astype(int)), 0))
    mark.putalpha(mark_alpha)
    tile.alpha_composite(mark)

    shape = Image.new("L", (size, size), 0)
    ImageDraw.Draw(shape).rounded_rectangle((0, 0, size-1, size-1), radius=round(size*.225), fill=255)
    tile.putalpha(shape)

    # Hairline inner highlight typical of polished iOS artwork.
    overlay = Image.new("RGBA", (size, size), (0,0,0,0))
    ImageDraw.Draw(overlay).rounded_rectangle(
        (2, 2, size-3, size-3), radius=round(size*.22), outline=(255,255,255,38), width=2
    )
    tile.alpha_composite(overlay)
    return tile


def main():
    alpha = Image.open(MASK_SOURCE).getchannel("A")
    board = Image.new("RGB", (1260, 920), "#E9E9E7")
    d = ImageDraw.Draw(board)
    d.text((40, 24), "ProPDA — iOS-inspired dark icon", font=font(34, True), fill="#1E2025")
    for i, (name, top, bottom, ink) in enumerate(PALETTES):
        col, row = i % 3, i // 3
        x, y = 40 + col*410, 90 + row*410
        icon = ios_tile(alpha, top, bottom, ink)
        board.paste(icon, (x, y), icon)
        d.text((x, y+388), name, font=font(20, True), fill="#202228")
    board.save(OUTPUT)


if __name__ == "__main__":
    main()
