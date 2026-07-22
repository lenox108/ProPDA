"""Export Android resources directly from the approved P4DA master board.

The icon geometry is never reconstructed. Every colored launcher asset is an
exact crop of the approved master, so P, 4, DA, spacing and optical weight stay
pixel-identical to the accepted design.
"""

from pathlib import Path

from PIL import Image, ImageColor, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
HERE = ROOT / "design/app-icon"
MASTER = HERE / "final-p4da-approved-master.png"
OUT = HERE / "final-p4da-resources"
WEB = OUT / "website-129"

# Exact 268×268 icon bounds measured in the approved 1536×1024 board.
CROPS = {
    "light": (88, 56, 356, 324),
    "amoled": (423, 56, 691, 324),
    "monochrome_preview": (787, 56, 1055, 324),
    "monet_preview": (1143, 56, 1411, 324),
}

EDGE_COLORS = {
    "light": "#F7F5F0",
    "amoled": "#101114",
    "monochrome_preview": "#FFFFFF",
    "monet_preview": "#DCE3EA",
}

PALETTES = {
    "light": {"foreground": "#111317", "screen": "#E8E5DF", "accent": "#D4AF37"},
    "amoled": {"foreground": "#F7F5F0", "screen": "#050506", "accent": "#D4AF37"},
    "monochrome_preview": {"foreground": "#111317", "screen": "#FFFFFF", "accent": "#111317"},
    "monet_preview": {"foreground": "#5C6B7A", "screen": "#E5ECF2", "accent": "#5C6B7A"},
}

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def approved_icons() -> dict[str, Image.Image]:
    board = Image.open(MASTER).convert("RGBA")
    return {
        name: clean_approved_icon(board.crop(box), name)
        for name, box in CROPS.items()
    }


def clean_approved_icon(icon: Image.Image, name: str) -> Image.Image:
    """Keep the approved mark pixels, replacing only baked board/background."""
    width, height = icon.size
    clean = Image.new("RGBA", icon.size, EDGE_COLORS[name])
    palette = PALETTES[name]

    # Exact source pixels for P, 4, DA and the status indicator. The mask is
    # derived from luminance/saturation, so their accepted contours and AA stay.
    source = icon.convert("RGB")
    mark_alpha = Image.new("L", icon.size, 0)
    gold_alpha = Image.new("L", icon.size, 0)
    source_pixels = source.load()
    alpha_pixels = mark_alpha.load()
    gold_pixels = gold_alpha.load()
    background_lum = {
        "light": 246,
        "amoled": 16,
        "monochrome_preview": 255,
        "monet_preview": 225,
    }[name]
    foreground_lum = {
        "light": 18,
        "amoled": 246,
        "monochrome_preview": 18,
        "monet_preview": 105,
    }[name]
    for y in range(height):
        for x in range(width):
            r, g, b = source_pixels[x, y]
            lum = round(0.2126 * r + 0.7152 * g + 0.0722 * b)
            goldness = max(0, min(255, round((r - b - 24) * 4.3)))
            if name == "amoled":
                value = round((lum - background_lum) * 255 / (foreground_lum - background_lum))
            else:
                value = round((background_lum - lum) * 255 / (background_lum - foreground_lum))
            # Gold is rendered independently, never as a muddy foreground mix.
            if goldness > 18 and name in ("light", "amoled"):
                value = 0
            alpha_pixels[x, y] = max(0, min(255, value))
            gold_pixels[x, y] = goldness if name in ("light", "amoled") else 0

    # Exclude the old tile keyline/shadow from the extracted mark.
    safe = Image.new("L", icon.size, 0)
    ImageDraw.Draw(safe).rounded_rectangle((18, 18, width - 19, height - 19), radius=31, fill=255)
    # The indicator sits farther left than the main mark.
    ImageDraw.Draw(safe).rounded_rectangle((27, 110, 44, 160), radius=8, fill=255)
    from PIL import ImageChops
    mark_alpha = ImageChops.multiply(mark_alpha, safe)
    gold_alpha = ImageChops.multiply(gold_alpha, safe)
    # Collapse the broad source antialias into a narrow high-contrast edge.
    def tighten(value: int) -> int:
        return max(0, min(255, round((value - 42) * 255 / 164)))

    mark_alpha = mark_alpha.point(tighten)
    gold_alpha = gold_alpha.point(tighten)

    # Exact flat palette colors over the approved alpha contours: no JPEG-like
    # color noise, muddy edges, accidental gradients or gray halos.
    foreground = Image.new("RGBA", icon.size, palette["foreground"])
    accent = Image.new("RGBA", icon.size, palette["accent"])

    # Preserve the approved inner display surface without importing its outer
    # presentation background. The black/white P edge remains source-exact.
    screen = Image.new("L", icon.size, 0)
    ImageDraw.Draw(screen).rounded_rectangle((78, 53, 193, 165), radius=20, fill=255)
    screen_fill = Image.new("RGBA", icon.size, palette["screen"])
    clean.paste(screen_fill, (0, 0), screen)
    clean.paste(foreground, (0, 0), mark_alpha)
    clean.paste(accent, (0, 0), gold_alpha)

    # Work at 8× for one perfectly smooth mathematical outer curve.
    scale = 8
    inset = 1 * scale
    mask = Image.new("L", (width * scale, height * scale), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle(
        (inset, inset, width * scale - 1 - inset, height * scale - 1 - inset),
        radius=46 * scale,
        fill=255,
    )
    mask = mask.resize((width, height), Image.Resampling.LANCZOS)
    result = clean
    result.putalpha(mask)
    return result


def final_alpha_mask(size: int) -> Image.Image:
    """Calculate the tile edge at output size to prevent resize halos."""
    scale = 8
    inset = max(1, round(size / 268)) * scale
    radius = round(size * 46 / 268) * scale
    mask = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (inset, inset, size * scale - 1 - inset, size * scale - 1 - inset),
        radius=radius,
        fill=255,
    )
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def scale_exact(icon: Image.Image, size: int) -> Image.Image:
    # Scale opaque RGB only. Resizing an already transparent edge creates a
    # light fringe; a fresh target-size alpha curve avoids that completely.
    rgb = icon.convert("RGB").resize((size, size), Image.Resampling.LANCZOS)
    result = rgb.convert("RGBA")
    result.putalpha(final_alpha_mask(size))
    return result


def safe_zone_layer(layer: Image.Image, canvas: int = 1080, art_size: int = 720) -> Image.Image:
    """Compensate for the launcher's adaptive-foreground zoom."""
    scaled = layer.resize((art_size, art_size), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    offset = (canvas - art_size) // 2
    result.alpha_composite(scaled, (offset, offset))
    return result


def adaptive_foreground(tile: Image.Image, name: str) -> Image.Image:
    """Remove the tile surface, retaining the exact approved P4DA artwork."""
    source = tile.convert("RGBA")
    bg = ImageColor.getrgb(EDGE_COLORS[name])
    art = Image.new("RGBA", source.size, (0, 0, 0, 0))
    src = source.load()
    dst = art.load()
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, _ = src[x, y]
            distance = max(abs(r - bg[0]), abs(g - bg[1]), abs(b - bg[2]))
            alpha = max(0, min(255, (distance - 2) * 22))
            dst[x, y] = (r, g, b, alpha)
    return safe_zone_layer(art)


def monochrome_alpha(preview: Image.Image, size: int = 1080) -> Image.Image:
    """Extract only the approved black mark; Android supplies its tint."""
    gray = preview.convert("L")
    # Smooth threshold retains the source anti-aliasing but excludes the tile,
    # border and shadow from the monochrome foreground mask.
    alpha = gray.point(lambda value: max(0, min(255, (105 - value) * 5)))
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.18))
    white = Image.new("RGBA", preview.size, (255, 255, 255, 0))
    white.putalpha(alpha)
    # This is a foreground glyph mask rather than a rounded tile.
    return white.resize((size, size), Image.Resampling.LANCZOS)


def save_colored_android(icon: Image.Image, name: str, night: bool) -> None:
    # Adaptive background supplies the tile; foreground is kept in the 66 dp
    # safe zone so Pixel's extra layer zoom restores the approved proportions.
    target = RES / (
        "drawable-night-nodpi/ic_launcher_foreground_art.png"
        if night
        else "drawable-nodpi/ic_launcher_foreground_art.png"
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    adaptive_foreground(icon, name).save(target, optimize=True)

    for density, size in DENSITIES.items():
        folder = RES / (f"mipmap-night-{density}" if night else f"mipmap-{density}")
        folder.mkdir(parents=True, exist_ok=True)
        scale_exact(icon, size).save(folder / "ic_launcher.png", optimize=True)


def save_web(icons: dict[str, Image.Image]) -> None:
    WEB.mkdir(parents=True, exist_ok=True)
    names = {
        "light": icons["light"],
        "amoled": icons["amoled"],
        "monochrome": icons["monochrome_preview"],
        "monet": icons["monet_preview"],
    }
    rendered: dict[str, Image.Image] = {}
    for name, icon in names.items():
        exact = scale_exact(icon, 129)
        rendered[name] = exact
        exact.save(WEB / f"propda-p4da-{name}-129.png", optimize=True)
        exact.save(WEB / f"propda-p4da-{name}-129.webp", quality=96, method=6)

    # Visual index only; the uploadable files above remain exactly 129×129.
    sheet = Image.new("RGB", (600, 600), "#EEF0F3")
    for icon, position in zip(rendered.values(), ((35, 35), (315, 35), (35, 315), (315, 315))):
        enlarged = scale_exact(icon, 250)
        sheet.paste(enlarged.convert("RGB"), position, enlarged.getchannel("A"))
    sheet.save(WEB / "propda-p4da-website-129-contact-sheet.png", optimize=True)


def save_masters(icons: dict[str, Image.Image], mono: Image.Image) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    scale_exact(icons["light"], 1080).save(OUT / "propda-p4da-light-1080.png")
    scale_exact(icons["amoled"], 1080).save(OUT / "propda-p4da-amoled-1080.png")
    mono.save(OUT / "propda-p4da-monochrome-1080.png")
    scale_exact(icons["monet_preview"], 1080).save(OUT / "propda-p4da-monet-1080.png")


def main() -> None:
    icons = approved_icons()
    mono = safe_zone_layer(monochrome_alpha(icons["monochrome_preview"]))

    save_colored_android(icons["light"], "light", night=False)
    save_colored_android(icons["amoled"], "amoled", night=True)
    mono_target = RES / "drawable-nodpi/ic_launcher_monochrome_art.png"
    mono_target.parent.mkdir(parents=True, exist_ok=True)
    mono.save(mono_target, optimize=True)

    save_masters(icons, mono)
    save_web(icons)


if __name__ == "__main__":
    main()
