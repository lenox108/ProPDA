#!/usr/bin/env python3
"""Build the crisp AMOLED ProPDA launcher assets from the approved P2 master."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
RES = ROOT / "app/src/main/res"
SOURCE = HERE / "propda-p2-refined-nodes-v20.png"

LIGHT_BG = (247, 243, 236)
AMOLED = (0, 0, 0)
NIGHT_INK = (245, 245, 245)
MONET_BG = (214, 226, 255)
MONET_INK = (45, 64, 94)
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def crisp_mask(source: Image.Image) -> Image.Image:
    """Convert soft AI antialiasing into a clean, high-contrast alpha edge."""
    gray = source.convert("L").filter(ImageFilter.GaussianBlur(.28))
    # Background and ivory counters stay transparent. Dark graphite becomes
    # fully opaque AMOLED black; the narrow transition is antialiased.
    return gray.point(lambda v: 0 if v >= 212 else 255 if v <= 155 else round((212 - v) * 255 / 57))


def adaptive_art(mask: Image.Image, color: tuple[int, int, int]) -> Image.Image:
    # The visible composition fits Android's 66dp adaptive safe zone.
    mask = mask.resize((780, 780), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (1080, 1080), (*color, 0))
    alpha = Image.new("L", (1080, 1080), 0)
    alpha.paste(mask, (150, 150))
    result.putalpha(alpha)
    return result


def composite(bg, art):
    result = Image.new("RGBA", art.size, (*bg, 255))
    result.alpha_composite(art)
    return result


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def main():
    source = Image.open(SOURCE).convert("RGB")
    mask = crisp_mask(source)
    black_art = adaptive_art(mask, AMOLED)
    white_art = adaptive_art(mask, NIGHT_INK)
    mono_art = adaptive_art(mask, (255, 255, 255))

    black_art.save(RES / "drawable-nodpi/ic_launcher_foreground_art.png")
    white_art.save(RES / "drawable-night-nodpi/ic_launcher_foreground_art.png")
    mono_art.save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png")

    light = composite(LIGHT_BG, black_art)
    dark = composite(AMOLED, white_art)
    monet = composite(MONET_BG, adaptive_art(mask, MONET_INK))
    light.save(HERE / "propda-amoled-final-light-v21.png")
    dark.save(HERE / "propda-amoled-final-dark-v21.png")
    monet.save(HERE / "propda-amoled-final-monet-v21.png")

    for density, size in SIZES.items():
        for night, master in ((False, light), (True, dark)):
            output = master.resize((size, size), Image.Resampling.LANCZOS)
            output.putalpha(rounded_mask(size, round(size * .22)))
            folder = f"mipmap-night-{density}" if night else f"mipmap-{density}"
            path = RES / folder / "ic_launcher.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            output.save(path)


if __name__ == "__main__":
    main()
