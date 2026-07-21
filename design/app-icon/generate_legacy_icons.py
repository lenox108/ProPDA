#!/usr/bin/env python3
"""Prepare Android assets from the approved P2 template without redrawing it."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
RES = ROOT / "app/src/main/res"
SOURCE = HERE / "propda-pro-color-comparison-v16.png"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

LIGHT_BG = (243, 237, 225)
DARK_BG = (18, 26, 38)
DARK_FG = (212, 216, 223)
MONET_BG = (214, 226, 255)
MONET_FG = (64, 84, 116)


def get_font(size, bold=False):
    for name in ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf", "Arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def approved_crop():
    """The P2 panel itself. Coordinates deliberately exclude all board labels."""
    return Image.open(SOURCE).convert("RGB").crop((640, 96, 1020, 476))


def remove_brown_segment(im):
    """Replace only the previously rejected brown arc with neighbouring graphite."""
    out = im.copy()
    px = out.load()
    graphite = (43, 47, 54)
    bg = (246, 242, 237)
    for y in range(0, 85):
        for x in range(out.width):
            r, g, b = px[x, y]
            if r > 75 and r - g > 7 and g - b > 12:
                # Preserve the source antialias coverage against its warm canvas.
                lum = (r + g + b) / 3
                coverage = max(0.0, min(1.0, (242 - lum) / 130))
                px[x, y] = tuple(round(graphite[i] * coverage + bg[i] * (1 - coverage)) for i in range(3))
    return out


def foreground_alpha(im):
    """Separate the approved mark from its near-white presentation canvas."""
    gray = im.convert("L")
    # Remove the complete warm presentation surface, including its subtle
    # panel-edge variation, while retaining graphite antialiasing and 'pro'.
    alpha = gray.point(lambda v: 0 if v > 235 else min(255, round((235 - v) * 8)))
    alpha = alpha.filter(ImageFilter.GaussianBlur(.25))
    rgba = im.convert("RGBA")
    rgba.putalpha(alpha)
    return rgba


def place_in_adaptive_canvas(art, canvas=1080, art_size=780):
    art = art.resize((art_size, art_size), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    at = (canvas - art_size) // 2
    result.alpha_composite(art, (at, at))
    return result


def recolor_by_alpha(art, color):
    result = Image.new("RGBA", art.size, (*color, 0))
    result.putalpha(art.getchannel("A"))
    return result


def make_dark_art(light_art):
    dark = recolor_by_alpha(light_art, DARK_FG)
    # Keep the tiny P2 signature understated against the pale monogram.
    d = ImageDraw.Draw(dark)
    d.text((430, 523), "pro", font=get_font(34, True), fill=(98, 105, 115, 255))
    return dark


def composite(bg, art, size=1024):
    base = Image.new("RGBA", (size, size), (*bg, 255))
    base.alpha_composite(art.resize((size, size), Image.Resampling.LANCZOS))
    return base


def launcher_mask(name, size):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    s = size - 1
    if name == "Circle": d.ellipse((0, 0, s, s), fill=255)
    elif name == "Pixel squircle": d.rounded_rectangle((0, 0, s, s), radius=int(size * .32), fill=255)
    elif name == "Samsung": d.rounded_rectangle((0, 0, s, s), radius=int(size * .23), fill=255)
    elif name == "Rounded square": d.rounded_rectangle((0, 0, s, s), radius=int(size * .16), fill=255)
    elif name == "Teardrop":
        d.rounded_rectangle((0, 0, s, s), radius=int(size * .5), fill=255)
        d.rectangle((size // 2, 0, s, size // 2), fill=255)
    else: d.regular_polygon((size // 2, size // 2, int(size * .5)), 6, rotation=30, fill=255)
    return m


def make_mask_board(master):
    names = ["Circle", "Pixel squircle", "Samsung", "Rounded square", "Teardrop", "Hexagon"]
    tile, gap, top = 300, 36, 76
    board = Image.new("RGB", (gap * 4 + tile * 3, 860), (236, 234, 230))
    d = ImageDraw.Draw(board)
    d.text((gap, 22), "ProPDA P2 — launcher masks", font=get_font(34, True), fill=(41, 44, 50))
    for i, name in enumerate(names):
        x = gap + (i % 3) * (tile + gap)
        y = top + (i // 3) * (tile + gap + 34)
        art = master.resize((tile, tile), Image.Resampling.LANCZOS)
        plate = Image.new("RGBA", (tile, tile), (0, 0, 0, 0))
        plate.paste(art, (0, 0), launcher_mask(name, tile))
        board.paste(plate, (x, y), plate)
        d.text((x + tile / 2, y + tile + 8), name, font=get_font(22, True), fill=(41, 44, 50), anchor="ma")
    return board


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m


def main():
    exact = remove_brown_segment(approved_crop())
    exact.save(HERE / "propda-p2-exact-template-v18.png")
    light_art = place_in_adaptive_canvas(foreground_alpha(exact))
    dark_art = make_dark_art(light_art)
    mono_art = recolor_by_alpha(light_art, (255, 255, 255))

    light_art.save(RES / "drawable-nodpi/ic_launcher_foreground_art.png")
    dark_art.save(RES / "drawable-night-nodpi/ic_launcher_foreground_art.png")
    mono_art.save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png")

    light = composite(LIGHT_BG, light_art)
    dark = composite(DARK_BG, dark_art)
    monet = composite(MONET_BG, recolor_by_alpha(light_art, MONET_FG))
    light.save(HERE / "propda-p2-final-light-v18.png")
    dark.save(HERE / "propda-p2-final-dark-v18.png")
    monet.save(HERE / "propda-p2-final-monet-v18.png")
    make_mask_board(light).save(HERE / "propda-p2-launcher-masks-v18.png")

    for density, size in SIZES.items():
        legacy = light.resize((size, size), Image.Resampling.LANCZOS)
        legacy.putalpha(rounded_mask(size, int(size * .22)))
        out = RES / f"mipmap-{density}/ic_launcher.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        legacy.save(out)


if __name__ == "__main__":
    main()
