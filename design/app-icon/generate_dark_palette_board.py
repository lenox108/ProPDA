#!/usr/bin/env python3
"""Dark background palette study for the approved minimal ProPDA icon."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MASK_SOURCE = ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_monochrome_art.png"
OUTPUT = HERE / "propda-dark-background-palettes-v24.png"

PALETTES = [
    ("01  AMOLED", "#000000", "#F5F5F5"),
    ("02  MIDNIGHT", "#0D1726", "#E9EEF7"),
    ("03  DEEP PETROL", "#0C2528", "#E6F0EB"),
    ("04  GRAPHITE", "#202126", "#F2ECE1"),
    ("05  BURGUNDY", "#281319", "#F4E8E5"),
    ("06  ESPRESSO", "#241A15", "#F0E4D2"),
]


def font(size, bold=False):
    for name in ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf", "Arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i+2], 16) for i in (0, 2, 4))


def tile(alpha, bg, ink, size=380):
    alpha = alpha.resize((size, size), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (size, size), (*rgb(bg), 255))
    mark = Image.new("RGBA", (size, size), (*rgb(ink), 0))
    mark.putalpha(alpha)
    result.alpha_composite(mark)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size-1, size-1), radius=round(size*.22), fill=255)
    result.putalpha(mask)
    return result


def main():
    alpha = Image.open(MASK_SOURCE).getchannel("A")
    board = Image.new("RGB", (1260, 920), "#E8E7E4")
    d = ImageDraw.Draw(board)
    d.text((40, 24), "ProPDA — dark background directions", font=font(34, True), fill="#202126")
    for index, (name, bg, ink) in enumerate(PALETTES):
        col, row = index % 3, index // 3
        x, y = 40 + col * 410, 90 + row * 410
        icon = tile(alpha, bg, ink)
        board.paste(icon, (x, y), icon)
        d.text((x, y + 388), name, font=font(21, True), fill="#202126")
        d.text((x + 205, y + 388), f"{bg} / {ink}", font=font(17), fill="#686A70", anchor="ma")
    board.save(OUTPUT)


if __name__ == "__main__":
    main()
