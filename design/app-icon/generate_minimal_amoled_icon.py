#!/usr/bin/env python3
"""Minimal AMOLED ProPDA icon: enlarged PDA mark, no outer ring or satellites."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
RES = ROOT / "app/src/main/res"
SOURCE = HERE / "propda-p2-inner-grid-variant-v22.png"

BG = (255, 255, 255)
BLACK = (0, 0, 0)
WHITE = (245, 245, 245)
IOS_INK = (23, 25, 29)
IOS_BOTTOM = (236, 238, 241)
IOS_NIGHT_TOP = (23, 25, 29)
MONET_BG = (214, 226, 255)
MONET_INK = (45, 64, 94)
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def font(size):
    for name in ("DejaVuSans-Bold.ttf", "Arial Bold.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def build_master():
    src = Image.open(SOURCE).convert("RGB")
    master = Image.new("RGB", src.size, BG)

    # Copy only the central 4/PDA mark; the complete outer ring disappears.
    mark_box = (330, 315, 1015, 1145)
    master.paste(src.crop(mark_box), mark_box)
    d = ImageDraw.Draw(master)
    d.rectangle((315, 1070, 420, 1180), fill=BG)

    # Remove the former tiny lettering from the black crossbar and replace it
    # with a larger, semibold signature that survives real launcher sizes.
    d.rounded_rectangle((455, 755, 690, 905), radius=18, fill=BLACK)
    d.text((485, 770), "pro", font=font(92), fill=BG, anchor="la")
    return master


def crisp_mask(master):
    gray = master.convert("L").filter(ImageFilter.GaussianBlur(.7))
    return gray.point(lambda v: 0 if v >= 212 else 255 if v <= 155 else round((212-v)*255/57))


def adaptive(mask, color):
    bbox = mask.getbbox()
    cropped = mask.crop(bbox)
    # Increase the mark substantially now that the outer ring is gone, while
    # keeping it within Android's adaptive-icon safe area.
    # 520px maps to roughly 72% of the launcher's visible adaptive mask.
    # The previous 690px mark occupied about 96% and touched Pixel's edges.
    target_h = 520
    target_w = round(cropped.width * target_h / cropped.height)
    cropped = cropped.resize((target_w, target_h), Image.Resampling.LANCZOS)
    alpha = Image.new("L", (1080, 1080), 0)
    alpha.paste(cropped, ((1080-target_w)//2, (1080-target_h)//2))
    art = Image.new("RGBA", (1080, 1080), (*color, 0))
    art.putalpha(alpha)
    return art


def composite(bg, art):
    out = Image.new("RGBA", art.size, (*bg, 255))
    out.alpha_composite(art)
    return out


def ios_white_background(size=1080):
    """W2 iOS White: clean white top fading into a cool pearl base."""
    result = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    pixels = result.load()
    for y in range(size):
        t = y / (size - 1)
        colour = tuple(round(255 * (1 - t) + IOS_BOTTOM[i] * t) for i in range(3))
        for x in range(size):
            pixels[x, y] = (*colour, 255)
    return result


def ios_night_background(size=1080):
    """iOS Night: graphite at the top fading into true AMOLED black."""
    result = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    pixels = result.load()
    for y in range(size):
        t = y / (size - 1)
        colour = tuple(round(IOS_NIGHT_TOP[i] * (1 - t)) for i in range(3))
        for x in range(size):
            pixels[x, y] = (*colour, 255)
    return result


def rounded_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size-1, size-1), radius=round(size*.22), fill=255)
    return m


def web_squircle(image, size):
    """Export a standalone app-icon tile with transparent outer corners."""
    tile = image.resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    # 22% corner radius closely matches the Android rounded-square silhouette.
    ImageDraw.Draw(mask).rounded_rectangle(
        (1, 1, size - 2, size - 2),
        radius=round(size * .22),
        fill=255,
    )
    tile.putalpha(mask)
    return tile


def main():
    master = build_master()
    master.save(HERE / "propda-minimal-master-v23.png")
    mask = crisp_mask(master)
    light_art = adaptive(mask, IOS_INK)
    dark_art = adaptive(mask, WHITE)
    mono_art = adaptive(mask, (255, 255, 255))

    light_art.save(RES / "drawable-nodpi/ic_launcher_foreground_art.png")
    dark_art.save(RES / "drawable-night-nodpi/ic_launcher_foreground_art.png")
    mono_art.save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png")

    light = ios_white_background()
    light.alpha_composite(light_art)
    dark = ios_night_background()
    dark.alpha_composite(dark_art)
    monet = composite(MONET_BG, adaptive(mask, MONET_INK))
    light.save(HERE / "propda-minimal-final-light-v23.png")
    dark.save(HERE / "propda-minimal-final-dark-v23.png")
    monet.save(HERE / "propda-minimal-final-monet-v23.png")

    web = HERE / "web"
    web.mkdir(parents=True, exist_ok=True)
    web_1024 = web_squircle(light, 1024)
    web_1024.save(web / "propda-app-icon-1024.png", optimize=True)
    web_squircle(light, 512).save(web / "propda-app-icon-512.png", optimize=True)
    web_1024.save(web / "propda-app-icon-1024.webp", format="WEBP", lossless=True, quality=100)

    for density, size in SIZES.items():
        for night, image in ((False, light), (True, dark)):
            icon = image.resize((size, size), Image.Resampling.LANCZOS)
            icon.putalpha(rounded_mask(size))
            folder = f"mipmap-night-{density}" if night else f"mipmap-{density}"
            path = RES / folder / "ic_launcher.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            icon.save(path)


if __name__ == "__main__":
    main()
