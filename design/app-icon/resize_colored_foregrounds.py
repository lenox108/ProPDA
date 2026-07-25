#!/usr/bin/env python3
"""Resize every coloured adaptive-icon foreground without touching monochrome."""

from __future__ import annotations

import argparse
import io
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
OUT = Path(__file__).resolve().parent / "all-icons-50-review.png"
TARGET_SCALE = 0.50
PER_ICON_SCALE = {
    "blue_4": 0.46,
    "bold_4": 0.46,
}

FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REGULAR = "/System/Library/Fonts/Supplemental/Arial.ttf"


def icon_id(path: Path) -> str:
    if path.name == "ic_launcher_foreground_art.webp":
        return "default"
    return path.name.removeprefix("ic_launcher_").removesuffix("_foreground_art.webp")


def visible_bounds(image: Image.Image) -> tuple[int, int, int, int]:
    pixels = np.asarray(image.convert("RGBA"))
    alpha = pixels[:, :, 3]
    if alpha.min() < 255:
        visible = alpha > 8
    else:
        corners = np.array(
            [pixels[2, 2, :3], pixels[2, -3, :3], pixels[-3, 2, :3], pixels[-3, -3, :3]]
        )
        background = np.median(corners, axis=0)
        visible = np.max(np.abs(pixels[:, :, :3].astype(np.int16) - background), axis=2) > 24
    ys, xs = np.where(visible)
    if not len(xs):
        raise ValueError("foreground is empty")
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def centre_foreground(image: Image.Image, fill: tuple[int, ...]) -> Image.Image:
    left, top, right, bottom = visible_bounds(image)
    dx = round((image.width - left - right) / 2)
    dy = round((image.height - top - bottom) / 2)
    if not dx and not dy:
        return image
    result = Image.new("RGBA", image.size, fill)
    result.alpha_composite(image.convert("RGBA"), (dx, dy))
    return result


def resize_foreground(path: Path) -> None:
    image = Image.open(path).convert("RGBA")
    width, height = image.size
    alpha = np.asarray(image.getchannel("A"))
    fill = image.getpixel((2, 2)) if alpha.min() == 255 else (0, 0, 0, 0)
    image = centre_foreground(image, fill)
    left, top, right, bottom = visible_bounds(image)
    visible_edge = max(right - left, bottom - top)
    target = PER_ICON_SCALE.get(icon_id(path), TARGET_SCALE)
    ratio = target * max(width, height) / visible_edge
    scaled = image.resize(
        (round(width * ratio), round(height * ratio)),
        Image.Resampling.LANCZOS,
    )
    result = Image.new("RGBA", image.size, fill)
    result.alpha_composite(
        scaled,
        ((width - scaled.width) // 2, (height - scaled.height) // 2),
    )
    result = centre_foreground(result, fill)
    left, top, right, bottom = visible_bounds(result)
    margin_dx = abs(left - (width - right))
    margin_dy = abs(top - (height - bottom))
    if margin_dx > 1 or margin_dy > 1:
        raise ValueError(f"{path}: asymmetric margins ΔX={margin_dx}, ΔY={margin_dy}")

    candidates: list[bytes] = []
    for options in ({"lossless": True, "method": 6}, {"quality": 95, "method": 6}):
        buffer = io.BytesIO()
        result.save(buffer, "WEBP", **options)
        candidates.append(buffer.getvalue())
    path.write_bytes(min(candidates, key=len))
    print(
        f"  ✓ {path.relative_to(ROOT)}: {visible_edge / width:.1%} → {target:.0%}, "
        f"поля ΔX={margin_dx}px ΔY={margin_dy}px"
    )


def adaptive_viewport(layer: Image.Image) -> Image.Image:
    """Simulate Android's 108→72 dp adaptive-layer crop (1.5× foreground zoom)."""
    image = layer.convert("RGBA")
    visible = round(image.width * 2 / 3)
    left = (image.width - visible) // 2
    top = (image.height - visible) // 2
    return image.crop((left, top, left + visible, top + visible)).resize(
        image.size, Image.Resampling.LANCZOS
    )


def launcher_mask(size: int, kind: str) -> Image.Image:
    scale = 4
    work = size * scale
    mask = Image.new("L", (work, work), 0)
    draw = ImageDraw.Draw(mask)
    if kind == "circle":
        draw.ellipse((0, 0, work - 1, work - 1), fill=255)
    else:
        ys, xs = np.mgrid[0:work, 0:work]
        half = work / 2
        inside = (
            np.abs((xs + 0.5 - half) / half) ** 4.2
            + np.abs((ys + 0.5 - half) / half) ** 4.2
            <= 1.0
        )
        mask = Image.fromarray(np.where(inside, 255, 0).astype(np.uint8), "L")
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def colors() -> dict[str, str]:
    text = (RES / "values/colors.xml").read_text(encoding="utf-8")
    return dict(re.findall(r'<color name="([^"]+)">#(?:FF)?([0-9A-Fa-f]{6})</color>', text))


def background(icon_id: str, size: int, palette: dict[str, str]) -> Image.Image:
    suffix = "" if icon_id == "default" else f"_{icon_id}"
    art = RES / f"drawable-nodpi/ic_launcher{suffix}_background_art.webp"
    if art.exists():
        return Image.open(art).convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
    name = "ic_launcher_background" if icon_id == "default" else f"ic_launcher_{icon_id}_background"
    rgb = palette.get(name, "FFFFFF")
    return Image.new("RGBA", (size, size), "#" + rgb)


def save_review(paths: list[Path]) -> None:
    palette = colors()
    ids = [icon_id(p) for p in paths]
    entries = sorted(zip(ids, paths), key=lambda item: (item[0] != "default", item[0]))

    columns, tile_w, tile_h = 4, 232, 174
    rows = (len(entries) + columns - 1) // columns
    board = Image.new("RGB", (columns * tile_w + 48, rows * tile_h + 130), "#E8EAED")
    draw = ImageDraw.Draw(board)
    title = ImageFont.truetype(FONT_BOLD, 28)
    label = ImageFont.truetype(FONT_BOLD, 16)
    note = ImageFont.truetype(FONT_REGULAR, 16)
    draw.text((24, 20), "Цветные иконки · 50%, blue_4 и bold_4 · 46%",
              font=title, fill="#202124")
    draw.text(
        (24, 58),
        "реальная геометрия adaptive icon (слой ×1,5) · monochrome не изменён",
        font=note,
        fill="#5F6368",
    )

    for index, (variant_id, path) in enumerate(entries):
        row, col = divmod(index, columns)
        x, y = 24 + col * tile_w, 100 + row * tile_h
        draw.rounded_rectangle((x, y, x + tile_w - 12, y + tile_h - 12), 18, fill="#FFFFFF")
        draw.text((x + 14, y + 12), variant_id, font=label, fill="#303134")

        fg = Image.open(path).convert("RGBA").resize((512, 512), Image.Resampling.LANCZOS)
        bg = background(variant_id, 512, palette)
        icon = adaptive_viewport(bg)
        icon.alpha_composite(adaptive_viewport(fg))
        for offset, kind in ((14, "circle"), (116, "squircle")):
            preview = icon.resize((90, 90), Image.Resampling.LANCZOS)
            mask = launcher_mask(90, kind)
            shaped = Image.new("RGBA", (90, 90))
            shaped.paste(preview, (0, 0), mask)
            board.paste(shaped, (x + offset, y + 53), shaped)

    board.save(OUT, optimize=True)
    print(f"  ✓ {OUT.relative_to(ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ids", nargs="*", help="обработать только указанные id")
    args = parser.parse_args()
    light = sorted((RES / "drawable-nodpi").glob("ic_launcher*foreground_art.webp"))
    dark = sorted((RES / "drawable-night-nodpi").glob("ic_launcher*foreground_art.webp"))
    selected = light + dark
    if args.ids:
        wanted = set(args.ids)
        selected = [path for path in selected if icon_id(path) in wanted]
    for path in selected:
        resize_foreground(path)
    save_review(light)
    print(f"Готово: обработано {len(selected)} цветных слоёв")


if __name__ == "__main__":
    main()
