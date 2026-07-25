#!/usr/bin/env python3
"""Normalise Android monochrome/Monet foregrounds without touching colour art."""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/drawable-nodpi"
OUT = Path(__file__).resolve().parent / "all-themed-icons-46-review.png"
TARGET_SCALE = 0.46

FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REGULAR = "/System/Library/Fonts/Supplemental/Arial.ttf"


def visible_bounds(image: Image.Image) -> tuple[int, int, int, int]:
    alpha = np.asarray(image.convert("RGBA").getchannel("A"))
    ys, xs = np.where(alpha > 8)
    if not len(xs):
        raise ValueError("empty monochrome layer")
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def centre_mark(image: Image.Image) -> Image.Image:
    left, top, right, bottom = visible_bounds(image)
    dx = round((image.width - left - right) / 2)
    dy = round((image.height - top - bottom) / 2)
    if not dx and not dy:
        return image
    result = Image.new("RGBA", image.size, (255, 255, 255, 0))
    result.alpha_composite(image.convert("RGBA"), (dx, dy))
    return result


def resize_layer(path: Path) -> None:
    image = centre_mark(Image.open(path).convert("RGBA"))
    left, top, right, bottom = visible_bounds(image)
    current = max(right - left, bottom - top)
    ratio = TARGET_SCALE * max(image.size) / current
    scaled = image.resize(
        (round(image.width * ratio), round(image.height * ratio)),
        Image.Resampling.LANCZOS,
    )
    result = Image.new("RGBA", image.size, (255, 255, 255, 0))
    result.alpha_composite(
        scaled,
        ((image.width - scaled.width) // 2, (image.height - scaled.height) // 2),
    )
    result = centre_mark(result)
    # The tint comes from Android, therefore white luminance + alpha is enough.
    result.convert("LA").save(path, optimize=True)
    print(f"  ✓ {path.relative_to(ROOT)}: {current / image.width:.1%} → {TARGET_SCALE:.0%}")


def adaptive_viewport(layer: Image.Image) -> Image.Image:
    image = layer.convert("RGBA")
    visible = round(image.width * 2 / 3)
    left = (image.width - visible) // 2
    top = (image.height - visible) // 2
    return image.crop((left, top, left + visible, top + visible)).resize(
        image.size, Image.Resampling.LANCZOS
    )


def tint(mask: Image.Image, color: str) -> Image.Image:
    result = Image.new("RGBA", mask.size, color)
    result.putalpha(mask.convert("RGBA").getchannel("A"))
    return result


def launcher_mask(size: int, kind: str) -> Image.Image:
    scale = 4
    work = size * scale
    if kind == "circle":
        mask = Image.new("L", (work, work), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, work - 1, work - 1), fill=255)
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


def icon_id(path: Path) -> str:
    if path.name == "ic_launcher_monochrome_art.png":
        return "default"
    return path.name.removeprefix("ic_launcher_").removesuffix("_monochrome_art.png")


def save_review(paths: list[Path]) -> None:
    entries = sorted(((icon_id(path), path) for path in paths),
                     key=lambda item: (item[0] != "default", item[0]))
    columns, tile_w, tile_h = 4, 232, 174
    rows = (len(entries) + columns - 1) // columns
    board = Image.new("RGB", (columns * tile_w + 48, rows * tile_h + 130), "#E8EAED")
    draw = ImageDraw.Draw(board)
    title = ImageFont.truetype(FONT_BOLD, 28)
    label = ImageFont.truetype(FONT_BOLD, 16)
    note = ImageFont.truetype(FONT_REGULAR, 16)
    draw.text((24, 20), "Monet и monochrome · знак 46% холста",
              font=title, fill="#202124")
    draw.text((24, 58), "реальная геометрия adaptive icon (слой ×1,5) · цветные слои не изменены",
              font=note, fill="#5F6368")

    for index, (name, path) in enumerate(entries):
        row, col = divmod(index, columns)
        x, y = 24 + col * tile_w, 100 + row * tile_h
        draw.rounded_rectangle((x, y, x + tile_w - 12, y + tile_h - 12),
                               18, fill="#FFFFFF")
        draw.text((x + 14, y + 12), name, font=label, fill="#303134")

        mask_layer = adaptive_viewport(
            Image.open(path).convert("RGBA").resize((512, 512), Image.Resampling.LANCZOS)
        )
        modes = (
            (14, "circle", "#493A37", "#FFDAD5"),
            (116, "squircle", "#F4F4F4", "#171717"),
        )
        for offset, shape, background, foreground in modes:
            icon = Image.new("RGBA", (512, 512), background)
            icon.alpha_composite(tint(mask_layer, foreground))
            preview = icon.resize((90, 90), Image.Resampling.LANCZOS)
            shape_mask = launcher_mask(90, shape)
            shaped = Image.new("RGBA", (90, 90))
            shaped.paste(preview, (0, 0), shape_mask)
            board.paste(shaped, (x + offset, y + 53), shaped)

    board.save(OUT, optimize=True)
    print(f"  ✓ {OUT.relative_to(ROOT)}")


def main() -> None:
    paths = sorted(RES.glob("ic_launcher*monochrome_art.png"))
    for path in paths:
        resize_layer(path)
    save_review(paths)
    print(f"Готово: {len(paths)} themed-слоёв нормализованы до {TARGET_SCALE:.0%}")


if __name__ == "__main__":
    main()
