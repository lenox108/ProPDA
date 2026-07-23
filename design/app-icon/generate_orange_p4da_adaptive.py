"""Package the approved orange P4DA renders as Android launcher resources.

The light and AMOLED artwork is never redrawn or colour-adjusted. Android gets
the approved masters pixel-for-pixel (apart from one high-quality resize).
Only the one-colour Android 13 themed-icon mask is derived separately.

By default the script renders the review board only. Pass --apply to replace the
launcher resources, once the board has been approved.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
OUT = ROOT / "design/app-icon/orange-p4da-adaptive"
LIGHT_MASTER = OUT / "orange-p4da-light-master.png"
AMOLED_MASTER = OUT / "orange-p4da-amoled-master.png"
CANVAS = 1080
# The launcher zooms adaptive foregrounds; 68% matches the visual footprint of
# the neighbouring 4PDA icon in the emulator's circular mask.
ART_SCALE = 0.68
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def visual_bounds(image: Image.Image) -> tuple[int, int, int, int]:
    """Bounds of the approved mark, ignoring its uniform theme background."""
    rgb = image.convert("RGB")
    bg = rgb.getpixel((5, 5))
    mask = Image.new("L", rgb.size, 0)
    source = rgb.load()
    target = mask.load()
    for y in range(rgb.height):
        for x in range(rgb.width):
            color = source[x, y]
            if max(abs(color[i] - bg[i]) for i in range(3)) > 24:
                target[x, y] = 255
    bounds = mask.getbbox()
    if bounds is None:
        raise ValueError("Approved icon contains no visible mark")
    return bounds


def center_mark(image: Image.Image) -> tuple[Image.Image, tuple[int, int]]:
    """Translate only: equalise opposite margins without altering the artwork."""
    image = image.convert("RGBA")
    left, top, right, bottom = visual_bounds(image)
    dx = round((image.width - (left + right)) / 2)
    dy = round((image.height - (top + bottom)) / 2)
    background = image.getpixel((5, 5))
    result = Image.new("RGBA", image.size, background)
    result.alpha_composite(image, (dx, dy))
    return result, (dx, dy)


def shrink_canvas(image: Image.Image, scale: float = ART_SCALE) -> Image.Image:
    """Add adaptive-icon breathing room without changing the approved artwork."""
    image = image.convert("RGBA")
    background = image.getpixel((5, 5))
    width = round(image.width * scale)
    height = round(image.height * scale)
    scaled = image.resize((width, height), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", image.size, background)
    result.alpha_composite(scaled, ((image.width - width) // 2, (image.height - height) // 2))
    return result


def approved(path: Path, size: int = CANVAS) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    edge = min(image.size)
    left = (image.width - edge) // 2
    top = (image.height - edge) // 2
    image = image.crop((left, top, left + edge, top + edge))
    image, _ = center_mark(image)
    image = shrink_canvas(image)
    image, _ = center_mark(image)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def close(mask: Image.Image, width: int = 5) -> Image.Image:
    """Close tiny sampling gaps without altering the visible outer contour."""
    return mask.filter(ImageFilter.MaxFilter(width)).filter(ImageFilter.MinFilter(width))


def themed_mask() -> Image.Image:
    """Build one solid, intentional silhouette from the approved light master."""
    source = Image.open(LIGHT_MASTER).convert("RGB")
    width, height = source.size
    pixels = source.load()
    p = Image.new("L", source.size, 0)
    da = Image.new("L", source.size, 0)
    digit = Image.new("L", source.size, 0)
    pp, dp, fp = p.load(), da.load(), digit.load()

    for y in range(height):
        for x in range(width):
            r, g, b = pixels[x, y]
            lum = round(0.2126 * r + 0.7152 * g + 0.0722 * b)
            # Orange P, including its darker orange antialiased rim.
            if r > 145 and r - g > 55 and g - b > 12:
                pp[x, y] = 255
            # Exact dark DA region only; the display is handled as a cutout.
            if y > round(height * 0.59) and lum < 125:
                dp[x, y] = 255
            # Exact luminous 4 inside the display.
            if (
                round(width * 0.455) < x < round(width * 0.535)
                and round(height * 0.395) < y < round(height * 0.505)
                and lum > 175
            ):
                fp[x, y] = 255

    p = close(p, 7)
    da = close(da, 5)
    digit = close(digit, 3)
    mask = Image.new("L", source.size, 0)
    mask = ImageChops.lighter(mask, p)
    mask = ImageChops.lighter(mask, da)

    # Deliberate negative display window: one filled rounded rectangle, never a
    # noisy threshold edge. The approved 4 is then restored as positive ink.
    screen = Image.new("L", source.size, 0)
    ImageDraw.Draw(screen).rounded_rectangle(
        (
            round(width * 0.406),
            round(height * 0.369),
            round(width * 0.578),
            round(height * 0.533),
        ),
        radius=round(width * 0.018),
        fill=255,
    )
    mask = Image.composite(Image.new("L", source.size, 0), mask, screen)
    mask = ImageChops.lighter(mask, digit)
    # Match the exact optical centring applied to the approved light artwork.
    _, (dx, dy) = center_mark(Image.open(LIGHT_MASTER))
    centred = Image.new("L", source.size, 0)
    centred.paste(mask, (dx, dy))
    mask = centred.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)
    # Threshold-derived glyph bounds differ slightly from the coloured shadow
    # bounds, so centre the final one-colour mask independently as well.
    bounds = mask.getbbox()
    if bounds is None:
        raise AssertionError("Themed icon mask is empty")
    left, top, right, bottom = bounds
    mdx = round((CANVAS - (left + right)) / 2)
    mdy = round((CANVAS - (top + bottom)) / 2)
    final_mask = Image.new("L", (CANVAS, CANVAS), 0)
    final_mask.paste(mask, (mdx, mdy))
    # Match the breathing-room reduction used by both coloured masters.
    reduced = final_mask.resize(
        (round(CANVAS * ART_SCALE), round(CANVAS * ART_SCALE)),
        Image.Resampling.LANCZOS,
    )
    mask = Image.new("L", (CANVAS, CANVAS), 0)
    mask.paste(reduced, ((CANVAS - reduced.width) // 2, (CANVAS - reduced.height) // 2))

    mono = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    mono.putalpha(mask)
    return mono


def launcher_mask(size: int, kind: str) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    if kind == "circle":
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    elif kind == "squircle":
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * 0.29), fill=255)
    else:
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * 0.18), fill=255)
    return mask


def masked(icon: Image.Image, kind: str, size: int = 196) -> Image.Image:
    scaled = icon.resize((size, size), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(scaled, (0, 0), launcher_mask(size, kind))
    return result


def tinted(mask: Image.Image, background: str, foreground: str) -> Image.Image:
    result = Image.new("RGBA", mask.size, background)
    result.paste(foreground, (0, 0, mask.width, mask.height), mask.getchannel("A"))
    return result


def assert_symmetric(label: str, bounds: tuple[int, int, int, int], size: int = CANVAS) -> None:
    left, top, right, bottom = bounds
    margins = (left, size - right, top, size - bottom)
    horizontal_error = abs(margins[0] - margins[1])
    vertical_error = abs(margins[2] - margins[3])
    if horizontal_error > 2 or vertical_error > 2:
        raise AssertionError(f"{label} is not centred: margins={margins}")


def validate(light: Image.Image, dark: Image.Image, mono: Image.Image) -> None:
    assert_symmetric("light", visual_bounds(light))
    assert_symmetric("amoled", visual_bounds(dark))
    alpha_bounds = mono.getchannel("A").getbbox()
    if alpha_bounds is None:
        raise AssertionError("monochrome mask is empty")
    assert_symmetric("monochrome/Monet", alpha_bounds)


def save_review(light: Image.Image, dark: Image.Image, mono: Image.Image) -> None:
    monet = tinted(mono, "#D9E3FF", "#33426B")
    monochrome = tinted(mono, "#F4F4F4", "#171717")
    variants = [("LIGHT · APPROVED", light), ("AMOLED · APPROVED", dark), ("MONET", monet), ("MONOCHROME", monochrome)]

    board = Image.new("RGB", (1200, 900), "#E7E9ED")
    draw = ImageDraw.Draw(board)
    for index, (label, icon) in enumerate(variants):
        col, row = index % 2, index // 2
        x, y = 35 + col * 580, 35 + row * 420
        draw.rounded_rectangle((x, y, x + 545, y + 370), radius=30, fill="#FFFFFF")
        draw.text((x + 24, y + 20), label, fill="#202124")
        for n, shape in enumerate(("circle", "squircle", "rounded")):
            preview = masked(icon, shape)
            board.paste(preview, (x + 20 + n * 172, y + 105), preview)
    board.save(OUT / "orange-p4da-adaptive-review.png", optimize=True)


def write_android_resources(light: Image.Image, dark: Image.Image, mono: Image.Image) -> None:
    """Replace the launcher resources. Only ever run after the board is approved."""
    # Full approved renders are intentional foregrounds: this is the only way
    # to keep their gradients, highlights and typography exactly unchanged.
    light.save(RES / "drawable-nodpi/ic_launcher_foreground_art.webp", format="WEBP", quality=90, method=6)
    dark.save(RES / "drawable-night-nodpi/ic_launcher_foreground_art.webp", format="WEBP", quality=90, method=6)
    mono.save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png", optimize=True)

    for density, size in DENSITIES.items():
        approved(LIGHT_MASTER, size).save(RES / f"mipmap-{density}/ic_launcher.png", optimize=True)
        approved(AMOLED_MASTER, size).save(RES / f"mipmap-night-{density}/ic_launcher.png", optimize=True)


def main(apply: bool = False) -> None:
    light = approved(LIGHT_MASTER)
    dark = approved(AMOLED_MASTER)
    mono = themed_mask()
    validate(light, dark, mono)

    mono.save(OUT / "orange-p4da-monochrome-1080.png", optimize=True)
    tinted(mono, "#D9E3FF", "#33426B").save(OUT / "orange-p4da-monet-preview-1080.png", optimize=True)
    preview_dir = OUT / "preview-129"
    preview_dir.mkdir(parents=True, exist_ok=True)
    previews = {
        "orange-p4da-light-129.png": light,
        "orange-p4da-amoled-129.png": dark,
        "orange-p4da-monet-129.png": tinted(mono, "#D9E3FF", "#33426B"),
        "orange-p4da-monochrome-129.png": tinted(mono, "#F4F4F4", "#171717"),
    }
    for name, image in previews.items():
        image.resize((129, 129), Image.Resampling.LANCZOS).save(preview_dir / name, optimize=True)
    save_review(light, dark, mono)

    if apply:
        write_android_resources(light, dark, mono)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Write the launcher resources into app/src/main/res. Without it the "
        "script only renders the review board for approval.",
    )
    main(apply=parser.parse_args().apply)
