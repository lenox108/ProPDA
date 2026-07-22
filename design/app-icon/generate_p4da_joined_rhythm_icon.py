"""Generate the approved P4DA 08 Joined Rhythm Android icon family."""

from pathlib import Path

from PIL import Image, ImageColor, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
HERE = ROOT / "design/app-icon"
MASTER = HERE / "final-p4da-v08-joined-rhythm-master-192.png"
OUT = HERE / "final-p4da-joined-rhythm-resources"
WEB = OUT / "website-129"

PALETTES = {
    "light": ("#F7F5F0", "#111317", "#E8E5DF", "#D4AF37"),
    "amoled": ("#101114", "#F7F5F0", "#050506", "#D4AF37"),
    "monochrome": ("#FFFFFF", "#111317", "#FFFFFF", "#111317"),
    "monet": ("#DCE3EA", "#5C6B7A", "#E5ECF2", "#5C6B7A"),
}

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def mask_from_master() -> tuple[Image.Image, Image.Image]:
    source = Image.open(MASTER).convert("RGB")
    mark = Image.new("L", source.size, 0)
    gold = Image.new("L", source.size, 0)
    src, dst, gd = source.load(), mark.load(), gold.load()
    for y in range(source.height):
        for x in range(source.width):
            r, g, b = src[x, y]
            lum = round(0.2126 * r + 0.7152 * g + 0.0722 * b)
            goldness = max(0, min(255, round((r - b - 22) * 4.5)))
            alpha = max(0, min(255, round((244 - lum) * 255 / 225)))
            if goldness > 22:
                alpha = 0
            dst[x, y] = alpha
            gd[x, y] = goldness

    # Narrow source antialias without sharpening halos.
    mark = mark.point(lambda v: max(0, min(255, round((v - 35) * 255 / 178))))
    gold = gold.point(lambda v: max(0, min(255, round((v - 30) * 255 / 185))))
    return mark, gold


def tile_alpha(size: int) -> Image.Image:
    scale = 8
    mask = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (scale, scale, size * scale - 1 - scale, size * scale - 1 - scale),
        radius=34 * scale,
        fill=255,
    )
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def render(mode: str) -> Image.Image:
    bg, fg, screen_color, accent = PALETTES[mode]
    mark, gold = mask_from_master()
    tile = Image.new("RGBA", (192, 192), bg)

    # Screen bounds measured directly from the selected 08 master.
    screen = Image.new("L", tile.size, 0)
    ImageDraw.Draw(screen).rounded_rectangle((61, 47, 134, 116), radius=13, fill=255)
    tile.paste(Image.new("RGBA", tile.size, screen_color), (0, 0), screen)
    tile.paste(Image.new("RGBA", tile.size, fg), (0, 0), mark)
    tile.paste(Image.new("RGBA", tile.size, accent), (0, 0), gold)
    tile.putalpha(tile_alpha(192))
    return tile


def resize_tile(tile: Image.Image, size: int) -> Image.Image:
    rgb = tile.convert("RGB").resize((size, size), Image.Resampling.LANCZOS)
    out = rgb.convert("RGBA")
    scale = 8
    mask = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (scale, scale, size * scale - 1 - scale, size * scale - 1 - scale),
        radius=round(size * 34 / 192) * scale,
        fill=255,
    )
    out.putalpha(mask.resize((size, size), Image.Resampling.LANCZOS))
    return out


def safe_layer(tile: Image.Image, background: str, canvas: int = 1080, art_size: int = 720) -> Image.Image:
    bg = ImageColor.getrgb(background)
    art = Image.new("RGBA", tile.size, (0, 0, 0, 0))
    src, dst = tile.convert("RGBA").load(), art.load()
    for y in range(tile.height):
        for x in range(tile.width):
            r, g, b, _ = src[x, y]
            distance = max(abs(r - bg[0]), abs(g - bg[1]), abs(b - bg[2]))
            dst[x, y] = (r, g, b, max(0, min(255, (distance - 2) * 22)))
    scaled = art.resize((art_size, art_size), Image.Resampling.LANCZOS)
    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.alpha_composite(scaled, ((canvas - art_size) // 2, (canvas - art_size) // 2))
    return layer


def monochrome_layer(tile: Image.Image) -> Image.Image:
    bg = ImageColor.getrgb(PALETTES["monochrome"][0])
    alpha = Image.new("L", tile.size, 0)
    src, dst = tile.convert("RGB").load(), alpha.load()
    for y in range(tile.height):
        for x in range(tile.width):
            r, g, b = src[x, y]
            dst[x, y] = max(0, min(255, (max(abs(r-bg[0]), abs(g-bg[1]), abs(b-bg[2])) - 2) * 22))
    white = Image.new("RGBA", tile.size, (255, 255, 255, 0))
    white.putalpha(alpha)
    scaled = white.resize((720, 720), Image.Resampling.LANCZOS)
    layer = Image.new("RGBA", (1080, 1080), (0, 0, 0, 0))
    layer.alpha_composite(scaled, (180, 180))
    return layer


def main() -> None:
    icons = {mode: render(mode) for mode in PALETTES}
    OUT.mkdir(parents=True, exist_ok=True)
    WEB.mkdir(parents=True, exist_ok=True)

    light_layer = safe_layer(icons["light"], PALETTES["light"][0])
    dark_layer = safe_layer(icons["amoled"], PALETTES["amoled"][0])
    mono_layer = monochrome_layer(icons["monochrome"])
    light_layer.save(RES / "drawable-nodpi/ic_launcher_foreground_art.png")
    dark_layer.save(RES / "drawable-night-nodpi/ic_launcher_foreground_art.png")
    mono_layer.save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png")

    for mode, icon in icons.items():
        resize_tile(icon, 1080).save(OUT / f"propda-p4da-joined-{mode}-1080.png")
        web = resize_tile(icon, 129)
        web.save(WEB / f"propda-p4da-joined-{mode}-129.png", optimize=True)
        web.save(WEB / f"propda-p4da-joined-{mode}-129.webp", quality=96, method=6)

    for night, mode in ((False, "light"), (True, "amoled")):
        for density, size in DENSITIES.items():
            folder = RES / (f"mipmap-night-{density}" if night else f"mipmap-{density}")
            folder.mkdir(parents=True, exist_ok=True)
            resize_tile(icons[mode], size).save(folder / "ic_launcher.png", optimize=True)


if __name__ == "__main__":
    main()
