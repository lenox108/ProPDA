"""Generate the 4-shaped ProPDA launcher icon and Android resources.

The source is deliberately constructed from geometry instead of a generated
bitmap: this keeps the 4, the inner P/chat/A stack and every launcher density
aligned and repeatable.
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "design/app-icon"
OUT = HERE / "four-container-icon"
REFERENCE = OUT / "reference-original.png"
RES = ROOT / "app/src/main/res"

CANVAS = 1080
SCALE = 4
BG_LIGHT = "#F7F5F0"
BG_DARK = "#0B0C0E"
INK_LIGHT = "#111317"
INK_DARK = "#F7F5F0"
ACCENT = "#D4AF37"

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

FONT_REGULAR = Path("/System/Library/Fonts/SFNS.ttf")
FONT_BOLD = Path("/System/Library/Fonts/Supplemental/Arial Bold.ttf")


def font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONT_BOLD if bold else FONT_REGULAR), size * SCALE)


def connected_components(mask: np.ndarray) -> list[list[tuple[int, int]]]:
    height, width = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    components: list[list[tuple[int, int]]] = []
    for y in range(height):
        for x in range(width):
            if not mask[y, x] or seen[y, x]:
                continue
            stack = [(x, y)]
            seen[y, x] = True
            component: list[tuple[int, int]] = []
            while stack:
                px, py = stack.pop()
                component.append((px, py))
                for nx, ny in ((px - 1, py), (px + 1, py), (px, py - 1), (px, py + 1)):
                    if (
                        0 <= nx < width
                        and 0 <= ny < height
                        and mask[ny, nx]
                        and not seen[ny, nx]
                    ):
                        seen[ny, nx] = True
                        stack.append((nx, ny))
            components.append(component)
    return components


def traced_four_mask() -> Image.Image:
    """Trace the actual 4 from the user's reference; do not reinterpret it."""
    rgb = np.asarray(Image.open(REFERENCE).convert("RGB"))
    luminance = (
        0.2126 * rgb[:, :, 0]
        + 0.7152 * rgb[:, :, 1]
        + 0.0722 * rgb[:, :, 2]
    )
    dark = luminance < 90
    body = max(connected_components(dark), key=len)
    mask = np.zeros_like(dark, dtype=bool)
    for x, y in body:
        mask[y, x] = True

    xs = [point[0] for point in body]
    ys = [point[1] for point in body]
    left, top, right, bottom = min(xs), min(ys), max(xs), max(ys)

    # Fill the old P/chat/A/terminal cutouts. Preserve only the large triangular
    # counter, which is part of the numeral itself.
    crop_inverse = ~mask[top : bottom + 1, left : right + 1]
    holes = connected_components(crop_inverse)
    enclosed: list[list[tuple[int, int]]] = []
    crop_height, crop_width = crop_inverse.shape
    for component in holes:
        touches_edge = any(
            x in (0, crop_width - 1) or y in (0, crop_height - 1)
            for x, y in component
        )
        if not touches_edge:
            enclosed.append(component)
    counter = max(enclosed, key=len)
    for component in enclosed:
        if component is counter:
            continue
        for x, y in component:
            mask[top + y, left + x] = True

    exact = Image.fromarray((mask[top : bottom + 1, left : right + 1] * 255).astype("uint8"))
    enlarged = exact.resize((545 * SCALE, 690 * SCALE), Image.Resampling.LANCZOS)
    enlarged = enlarged.filter(ImageFilter.GaussianBlur(5 * SCALE))
    return enlarged.point(lambda value: max(0, min(255, round((value - 82) * 255 / 92))))


def draw_four(image: Image.Image, ink: str) -> None:
    alpha = traced_four_mask()
    layer = Image.new("RGBA", image.size, ink)
    positioned = Image.new("L", image.size, 0)
    positioned.paste(alpha, (245 * SCALE, 170 * SCALE))
    image.paste(layer, (0, 0), positioned)


def centered_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    center_x: int,
    top: int,
    size: int,
    fill: str,
) -> None:
    face = font(size, bold=True)
    box = draw.textbbox((0, 0), text, font=face, stroke_width=0)
    width = box[2] - box[0]
    draw.text(
        (center_x * SCALE - width / 2 - box[0], top * SCALE - box[1]),
        text,
        font=face,
        fill=fill,
    )


def draw_chat(draw: ImageDraw.ImageDraw, center_x: int, top: int, ink: str) -> None:
    """A simple forum bubble that survives downsampling to mdpi."""
    x0, y0, x1, y1 = center_x - 55, top, center_x + 55, top + 77
    width = 10
    draw.rounded_rectangle(
        tuple(value * SCALE for value in (x0, y0, x1, y1)),
        radius=22 * SCALE,
        outline=ink,
        width=width * SCALE,
    )
    draw.line(
        [
            ((x0 + 20) * SCALE, (y1 - 4) * SCALE),
            ((x0 + 13) * SCALE, (y1 + 20) * SCALE),
            ((x0 + 40) * SCALE, (y1 - 2) * SCALE),
        ],
        fill=ink,
        width=width * SCALE,
        joint="curve",
    )
    dot_r = 7
    for dx in (-24, 0, 24):
        draw.ellipse(
            tuple(
                value * SCALE
                for value in (
                    center_x + dx - dot_r,
                    top + 32 - dot_r,
                    center_x + dx + dot_r,
                    top + 32 + dot_r,
                )
            ),
            fill=ink,
        )


def render_foreground(ink: str, transparent: bool = True) -> Image.Image:
    surface = (0, 0, 0, 0) if transparent else BG_LIGHT
    image = Image.new("RGBA", (CANVAS * SCALE, CANVAS * SCALE), surface)
    draw = ImageDraw.Draw(image)

    # The 4 occupies a 650 px optical square. Its right stem is the stable
    # alignment rail for all three inner symbols.
    draw_four(image, ink)

    cutout = (0, 0, 0, 0)
    rail_x = 687
    centered_text(draw, "P", rail_x, 292, 92, cutout)
    draw_chat(draw, rail_x, 450, cutout)
    centered_text(draw, "A", rail_x, 642, 92, cutout)

    # Tiny terminal cue from the reference, simplified to remain legible.
    centered_text(draw, ">_", 347, 652, 40, cutout)

    return image.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)


def composite_tile(foreground: Image.Image, background: str, size: int) -> Image.Image:
    tile = Image.new("RGBA", (CANVAS, CANVAS), background)
    tile.alpha_composite(foreground)
    return tile.resize((size, size), Image.Resampling.LANCZOS)


def save_android(light: Image.Image, dark: Image.Image) -> None:
    for artwork, night in ((light, False), (dark, True)):
        target = RES / (
            "drawable-night-nodpi/ic_launcher_foreground_art.webp"
            if night
            else "drawable-nodpi/ic_launcher_foreground_art.webp"
        )
        target.parent.mkdir(parents=True, exist_ok=True)
        artwork.save(target, "WEBP", lossless=True, method=6)

        bg = BG_DARK if night else BG_LIGHT
        for density, size in DENSITIES.items():
            folder = RES / (f"mipmap-night-{density}" if night else f"mipmap-{density}")
            folder.mkdir(parents=True, exist_ok=True)
            composite_tile(artwork, bg, size).save(folder / "ic_launcher.png", optimize=True)

    mono = render_foreground("#FFFFFF")
    mono.save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png", optimize=True)


def save_review(light: Image.Image, dark: Image.Image) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    light_tile = composite_tile(light, BG_LIGHT, 1080)
    dark_tile = composite_tile(dark, BG_DARK, 1080)
    light_tile.save(OUT / "four-container-light-1080.png", optimize=True)
    dark_tile.save(OUT / "four-container-dark-1080.png", optimize=True)

    board = Image.new("RGB", (1600, 900), "#E8EAED")
    draw = ImageDraw.Draw(board)
    for tile, x, label in ((light_tile, 100, "LIGHT"), (dark_tile, 850, "DARK / AMOLED")):
        preview = tile.resize((650, 650), Image.Resampling.LANCZOS)
        board.paste(preview.convert("RGB"), (x, 90))
        draw.text((x, 770), label, font=ImageFont.truetype(str(FONT_REGULAR), 36), fill="#303238")
        small = tile.resize((96, 96), Image.Resampling.LANCZOS)
        board.paste(small.convert("RGB"), (x + 530, 760))
    board.save(OUT / "four-container-review.png", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--design-only",
        action="store_true",
        help="write review masters without replacing Android resources",
    )
    args = parser.parse_args()
    light = render_foreground(INK_LIGHT)
    dark = render_foreground(INK_DARK)
    save_review(light, dark)
    if not args.design_only:
        save_android(light, dark)


if __name__ == "__main__":
    main()
