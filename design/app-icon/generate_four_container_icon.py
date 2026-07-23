"""Generate the 4-shaped ProPDA launcher icon and Android resources.

The source is deliberately constructed from geometry instead of a generated
bitmap: this keeps the 4, the inner P/chat/A stack and every launcher density
aligned and repeatable.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "design/app-icon"
OUT = HERE / "four-container-icon"
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


def draw_four(draw: ImageDraw.ImageDraw, ink: str) -> None:
    """Draw the compact, softly rounded 4 from the supplied reference."""
    # A tapered diagonal joins the stem below its rounded cap, so there is no
    # bump or pointed ear at the shoulder.
    draw.polygon(
        [
            (235 * SCALE, 650 * SCALE),
            (528 * SCALE, 226 * SCALE),
            (580 * SCALE, 190 * SCALE),
            (638 * SCALE, 190 * SCALE),
            (405 * SCALE, 584 * SCALE),
            (278 * SCALE, 704 * SCALE),
        ],
        fill=ink,
    )

    # The right stem is deliberately narrower than the old blocky version.
    draw.rounded_rectangle(
        tuple(value * SCALE for value in (568, 158, 744, 846)),
        radius=44 * SCALE,
        fill=ink,
    )
    draw.rounded_rectangle(
        tuple(value * SCALE for value in (220, 584, 657, 716)),
        radius=55 * SCALE,
        fill=ink,
    )


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
    draw_four(draw, ink)
    # Open triangular counter with the same proportions as the reference.
    draw.polygon(
        [
            (424 * SCALE, 566 * SCALE),
            (568 * SCALE, 336 * SCALE),
            (568 * SCALE, 566 * SCALE),
        ],
        fill=BG_LIGHT if not transparent else (0, 0, 0, 0),
    )

    cutout = (0, 0, 0, 0)
    rail_x = 656
    centered_text(draw, "P", rail_x, 266, 96, cutout)
    draw_chat(draw, rail_x, 420, cutout)
    centered_text(draw, "A", rail_x, 606, 96, cutout)

    # Tiny terminal cue from the reference, simplified to remain legible.
    centered_text(draw, ">_", 302, 618, 42, cutout)

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
