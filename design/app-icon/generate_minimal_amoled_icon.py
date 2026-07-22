#!/usr/bin/env python3
"""Final terminal ProPDA icon with adaptive Light, AMOLED and Monet states."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops


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
MUTED_GOLD = (178, 143, 82)
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


def adaptive(mask, color, offset=(0, 0)):
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
    alpha.paste(cropped, ((1080-target_w)//2 + offset[0], (1080-target_h)//2 + offset[1]))
    alpha = terminal_refinement(alpha, offset)
    alpha = vector_outer_alpha(alpha, offset)
    art = Image.new("RGBA", (1080, 1080), (*color, 0))
    art.putalpha(alpha)
    return art


def _cubic(points, p0, p1, p2, p3, steps=24):
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        points.append((
            u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0],
            u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1],
        ))


def vector_outer_mask(offset=(0, 0), scale=8):
    """Mathematically clean outer 4 silhouette rendered at 8x."""
    ox, oy = offset
    pts = [(590+ox, 280+oy), (716+ox, 280+oy)]
    _cubic(pts, pts[-1], (734+ox, 280+oy), (746+ox, 293+oy), (746+ox, 311+oy))
    pts.append((746+ox, 769+oy))
    _cubic(pts, pts[-1], (746+ox, 788+oy), (734+ox, 800+oy), (715+ox, 800+oy))
    pts.append((580+ox, 800+oy))
    _cubic(pts, pts[-1], (561+ox, 800+oy), (548+ox, 787+oy), (548+ox, 768+oy))
    pts.append((548+ox, 724+oy))
    _cubic(pts, pts[-1], (548+ox, 707+oy), (536+ox, 696+oy), (519+ox, 696+oy))
    pts.append((391+ox, 696+oy))
    _cubic(pts, pts[-1], (360+ox, 696+oy), (337+ox, 678+oy), (333+ox, 649+oy))
    _cubic(pts, pts[-1], (330+ox, 629+oy), (337+ox, 610+oy), (349+ox, 594+oy))
    pts.append((562+ox, 298+oy))
    _cubic(pts, pts[-1], (570+ox, 287+oy), (579+ox, 280+oy), (590+ox, 280+oy))

    hi = Image.new("L", (1080*scale, 1080*scale), 0)
    ImageDraw.Draw(hi).polygon([(round(x*scale), round(y*scale)) for x, y in pts], fill=255)
    return hi.resize((1080, 1080), Image.Resampling.LANCZOS)


def vector_outer_alpha(alpha, offset=(0, 0)):
    """Replace only the old raster perimeter, retaining all inner glyphs."""
    binary = alpha.point(lambda v: 255 if v > 96 else 0)
    flood = binary.copy()
    ImageDraw.floodfill(flood, (0, 0), 128, thresh=0)
    old_outer = flood.point(lambda v: 0 if v == 128 else 255)
    # Keep only negative shapes well inside the body. Eroding the old outer
    # mask prevents its raster perimeter from leaking back into the new one.
    inner_zone = old_outer.filter(ImageFilter.MinFilter(21))
    holes = ImageChops.multiply(ImageChops.invert(alpha), inner_zone)
    return ImageChops.subtract(vector_outer_mask(offset), holes)


def terminal_refinement(alpha, offset=(0, 0)):
    """Replace pro and the D app grid with the selected compact T01 terminal."""
    alpha = alpha.copy()
    d = ImageDraw.Draw(alpha)
    ox, oy = offset
    # Restore the solid crossbar where the former pro word was cut out.
    d.rectangle((398+ox, 558+oy, 550+ox, 650+oy), fill=255)
    # Clear the former 2x2 cells inside D, then add three forum dots.
    d.rectangle((631+ox, 503+oy, 695+ox, 572+oy), fill=0)
    for cx in (646, 663, 680):
        d.ellipse((cx+ox-6, 532+oy, cx+ox+6, 544+oy), fill=255)

    # T01 terminal: compact, horizontal and optically centred. It is cut from
    # the foreground, so it automatically inverts in Light/AMOLED/Monet.
    scale = 6
    cut = Image.new("L", (1080 * scale, 1080 * scale), 0)
    cd = ImageDraw.Draw(cut)
    cx, cy = (474+ox) * scale, (606+oy) * scale
    # Final T01 scale: +30% after launcher-size review.
    unit = .99 * scale
    width = round(7.5 * unit)
    x0 = cx - round(34 * unit)
    x1 = cx - round(7 * unit)
    gap = round(13 * unit)
    cd.line((x0, cy - round(23 * unit), x1, cy, x0, cy + round(23 * unit)), fill=255, width=width, joint="curve")
    cd.line((x1 + gap, cy + round(23 * unit), x1 + gap + round(34 * unit), cy + round(23 * unit)), fill=255, width=width)
    radius = width // 2
    for x, y in ((x0, cy-round(23*unit)), (x0, cy+round(23*unit)), (x1+gap, cy+round(23*unit)), (x1+gap+round(34*unit), cy+round(23*unit))):
        cd.ellipse((x-radius, y-radius, x+radius, y+radius), fill=255)
    cut = cut.resize((1080, 1080), Image.Resampling.LANCZOS)
    return ImageChops.subtract(alpha, cut)


def outer_ring(alpha, radius=3):
    """Smooth external hairline; counters and glyph cut-outs stay untouched.

    The source concept is raster, so the silhouette is softened before the
    contour is derived. A lightly blurred dilation gives a continuous line
    without the short Lanczos notches that appeared on horizontal edges.
    """
    binary = alpha.point(lambda v: 255 if v > 96 else 0)
    flood = binary.copy()
    ImageDraw.floodfill(flood, (0, 0), 128, thresh=0)
    silhouette = flood.point(lambda v: 0 if v == 128 else 255)
    # Remove one-pixel bumps inherited from the early raster concept before
    # deriving the outline. This changes only the hairline, not the logo fill.
    silhouette = silhouette.filter(ImageFilter.GaussianBlur(2.0))
    silhouette = silhouette.point(lambda v: 255 if v >= 128 else 0)

    dilated = silhouette.filter(ImageFilter.MaxFilter(radius * 2 + 1))
    dilated = dilated.filter(ImageFilter.GaussianBlur(.65))
    inner = silhouette.filter(ImageFilter.GaussianBlur(.35))
    ring = ImageChops.subtract(dilated, inner)
    return ring.point(lambda v: 0 if v < 10 else min(255, round(v * 1.2)))


def add_hairline(art, color, radius=3):
    ring = outer_ring(art.getchannel("A"), radius)
    result = Image.new("RGBA", art.size, (*color, 0))
    result.putalpha(ring)
    result.alpha_composite(art)
    return result


def expand_monochrome_hairline(art, radius=3):
    alpha = art.getchannel("A")
    alpha = ImageChops.lighter(alpha, outer_ring(alpha, radius))
    result = Image.new("RGBA", art.size, (255, 255, 255, 0))
    result.putalpha(alpha)
    return result


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
    # Draw the silhouette at 8x resolution and downsample it. Drawing the
    # rounded rectangle directly at 129 px produces visibly uneven stair-steps.
    scale = 8
    mask = Image.new("L", (size * scale, size * scale), 0)
    inset = scale
    ImageDraw.Draw(mask).rounded_rectangle(
        (inset, inset, size * scale - inset - 1, size * scale - inset - 1),
        radius=round(size * scale * .22),
        fill=255,
    )
    mask = mask.resize((size, size), Image.Resampling.LANCZOS)
    tile.putalpha(mask)
    return tile


def circle_preview(image, size=512):
    """Pixel-style circular launcher crop for optical-alignment review."""
    tile = image.resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((1, 1, size - 2, size - 2), fill=255)
    tile.putalpha(mask)
    return tile


def outline_palette_sheet(mask):
    """Hairline colour candidates on the final iOS White icon."""
    variants = (
        ("01  GRAPHITE", (83, 87, 96)),
        ("02  TITANIUM", (112, 120, 132)),
        ("03  SOFT SILVER", (151, 157, 166)),
        ("04  iOS BLUE", (10, 132, 255)),
        ("05  MUTED GOLD", (178, 143, 82)),
        ("06  ICE BLUE", (104, 145, 181)),
    )
    cell = 760
    sheet = Image.new("RGB", (cell * 3, cell * 2), (241, 242, 245))
    label_font = font(30)
    base_art = adaptive(mask, IOS_INK)
    for index, (label, colour) in enumerate(variants):
        icon = ios_white_background()
        icon.alpha_composite(add_hairline(base_art, colour))
        icon = web_squircle(icon, 610)
        x = (index % 3) * cell + 75
        y = (index // 3) * cell + 80
        sheet.paste(icon, (x, y), icon)
        draw = ImageDraw.Draw(sheet)
        draw.text((x + 305, y + 635), label, font=label_font,
                  fill=(35, 37, 42), anchor="ma")
    return sheet


def main():
    master = build_master()
    master.save(HERE / "propda-minimal-master-v23.png")
    mask = crisp_mask(master)
    light_art = add_hairline(adaptive(mask, IOS_INK), MUTED_GOLD)
    dark_art = add_hairline(adaptive(mask, WHITE), MUTED_GOLD)
    # The asymmetric four has a geometric centre at 540, but its visual mass
    # sits roughly 37 px to the right. Pixel's circular themed-icon mask makes
    # that especially obvious, so Monet receives a small optical correction.
    monet_offset = (-36, -10)
    mono_art = expand_monochrome_hairline(adaptive(mask, (255, 255, 255), monet_offset))

    light_art.save(RES / "drawable-nodpi/ic_launcher_foreground_art.png")
    dark_art.save(RES / "drawable-night-nodpi/ic_launcher_foreground_art.png")
    mono_art.save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png")

    light = ios_white_background()
    light.alpha_composite(light_art)
    dark = ios_night_background()
    dark.alpha_composite(dark_art)
    monet_mask = expand_monochrome_hairline(adaptive(mask, (255, 255, 255), monet_offset))
    monet_colored = Image.new("RGBA", monet_mask.size, (*MONET_INK, 0))
    monet_colored.putalpha(monet_mask.getchannel("A"))
    monet = composite(MONET_BG, monet_colored)
    mono_mask = expand_monochrome_hairline(adaptive(mask, (255, 255, 255)))
    mono_colored = Image.new("RGBA", mono_mask.size, (*IOS_INK, 0))
    mono_colored.putalpha(mono_mask.getchannel("A"))
    monochrome = composite((242, 242, 242), mono_colored)
    light.save(HERE / "propda-minimal-final-light-v23.png")
    dark.save(HERE / "propda-minimal-final-dark-v23.png")
    monet.save(HERE / "propda-minimal-final-monet-v23.png")
    light.save(HERE / "propda-terminal-final-light-v24.png")
    dark.save(HERE / "propda-terminal-final-amoled-v24.png")
    monet.save(HERE / "propda-terminal-final-monet-v24.png")
    monochrome.save(HERE / "propda-terminal-final-monochrome-v24.png")
    circle_preview(monet).save(HERE / "propda-terminal-final-monet-circle-v24.png", optimize=True)
    circle_preview(monochrome).save(HERE / "propda-terminal-final-monochrome-circle-v24.png", optimize=True)
    outline_palette_sheet(mask).save(
        HERE / "propda-terminal-hairline-color-options-v25.png", optimize=True
    )

    web = HERE / "web"
    web.mkdir(parents=True, exist_ok=True)
    web_512 = web_squircle(light, 512)
    web_512.save(web / "propda-icon-light-ios-512.png", optimize=True)
    web_squircle(dark, 512).save(web / "propda-icon-night-ios-512.png", optimize=True)
    web_squircle(monet, 512).save(web / "propda-icon-monet-blue-512.png", optimize=True)
    # Compatibility names retained for existing site references.
    web_512.save(web / "propda-app-icon-512.png", optimize=True)
    web_512.save(web / "propda-icon-light-ios-512.webp", format="WEBP", lossless=True, quality=100)
    web_squircle(dark, 512).save(web / "propda-icon-night-ios-512.webp", format="WEBP", lossless=True, quality=100)
    web_squircle(monet, 512).save(web / "propda-icon-monet-blue-512.webp", format="WEBP", lossless=True, quality=100)
    web_512.save(web / "propda-app-icon-512.webp", format="WEBP", lossless=True, quality=100)

    web_129 = web_squircle(light, 129)
    web_129.save(web / "propda-icon-light-ios-129.png", optimize=True)
    web_squircle(dark, 129).save(web / "propda-icon-night-ios-129.png", optimize=True)
    web_squircle(monet, 129).save(web / "propda-icon-monet-blue-129.png", optimize=True)
    web_129.save(web / "propda-icon-light-ios-129.webp", format="WEBP", lossless=True, quality=100)
    web_squircle(dark, 129).save(web / "propda-icon-night-ios-129.webp", format="WEBP", lossless=True, quality=100)
    web_squircle(monet, 129).save(web / "propda-icon-monet-blue-129.webp", format="WEBP", lossless=True, quality=100)

    # Final terminal-v24 website previews, including the monochrome state.
    for name, image in (("light", light), ("amoled", dark), ("monet", monet), ("monochrome", monochrome)):
        preview = web_squircle(image, 129)
        preview.save(web / f"propda-terminal-v24-{name}-129.png", optimize=True)
        preview.save(web / f"propda-terminal-v24-{name}-129.webp", format="WEBP", lossless=True, quality=100)

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
