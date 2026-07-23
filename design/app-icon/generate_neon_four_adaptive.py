"""Build the neon wireframe "4" launcher icon from the supplied master.

The master is a glowing cyan wireframe digit sitting on flat light grey with a
drop shadow. Only the digit is used: the background is re-created from the app's
own theme colours (crystal white in light, AMOLED black in dark), so the
supplied dark-navy circuit panel is deliberately ignored.

By default the script only renders review boards. Pass --apply to replace the
launcher resources, once a board has been approved.
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
OUT = ROOT / "design/app-icon/neon-four-adaptive"
MASTER = OUT / "neon-four-master.png"

CANVAS = 1080
# SPEC.md's 0.68 is measured on the *master square*, not on the mark — that is
# what add_alt_icon.py applies, and every shipped variant follows it. Their marks
# fill about 0.60 of their master square, so on the finished canvas they measure
# 0.41 on the long side (four_dark 0.406, four_blue 0.408, puzzle 0.432).
# Scaling this mark to 0.68 of the canvas directly made it half again larger than
# its neighbours, so the same two factors are applied here.
SPEC_SCALE = 0.68
MARK_IN_MASTER = 0.60
MARK_SCALE = SPEC_SCALE * MARK_IN_MASTER
# Monochrome morphology runs here, not at master size: the filters are O(k²)/px.
MONO_WORKING_WIDTH = 1080
# Size and centring are measured on the lattice contour, not on the glow that
# fades out around it — otherwise each variant's halo falls off at a different
# opacity and the "same size everywhere" rule is measured against noise.
BOUNDS_FLOOR = 0.30
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

LIGHT_BACKGROUND = "#FFFFFF"
DARK_BACKGROUND = "#000000"
# Light theme cannot use additive glow — cyan light on white is invisible. The
# same wireframe is drawn as deep teal ink instead.
LIGHT_INK = (11, 74, 99)


def master_intensity(image: Image.Image) -> "tuple[np.ndarray, np.ndarray]":
    """Split the digit into (whole mark, glowing struts), each in 0..1.

    Two signals, because the mark is not uniform: the lattice fill is *darker*
    than the grey backdrop while the struts and nodes are *brighter*. Both are
    strongly cyan, and the drop shadow is neutral, so chroma separates the mark
    from the shadow that must not survive. Keeping the struts separate is what
    lets the light-theme rendering stay a wireframe instead of a solid blob.
    """
    rgb = np.asarray(image.convert("RGB")).astype(np.float32)
    backdrop = np.median(
        np.concatenate([rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3)]), axis=0
    )

    chroma = rgb.max(axis=2) - rgb.min(axis=2)
    backdrop_chroma = float(backdrop.max() - backdrop.min())
    chromatic = np.clip((chroma - backdrop_chroma - 5.0) / 40.0, 0.0, 1.0)

    weights = np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)
    luma = rgb @ weights
    backdrop_luma = float(backdrop @ weights)
    luminous = np.clip((luma - backdrop_luma - 6.0) / 45.0, 0.0, 1.0)

    mark = largest_component(np.maximum(chromatic, luminous))
    return mark, np.where(mark > 0, luminous, 0.0)


def largest_component(intensity: np.ndarray, floor: float = 0.12) -> np.ndarray:
    """Keep only the digit.

    The master carries a generator watermark in its bottom-right corner. Left in,
    it joins the visible bounds, so every later step — scale, centring, margin
    check — would be measured against the digit *plus* the watermark.
    """
    # .copy() detaches the image from the read-only numpy buffer; floodfill
    # silently does nothing on a shared one.
    binary = Image.fromarray(((intensity > floor) * 255).astype(np.uint8), "L").copy()
    seed = np.unravel_index(int(np.argmax(intensity)), intensity.shape)
    ImageDraw.floodfill(binary, (int(seed[1]), int(seed[0])), 128)
    connected = np.asarray(binary) == 128
    return np.where(connected, intensity, 0.0)


def alpha_bounds(alpha: np.ndarray, floor: float = BOUNDS_FLOOR) -> tuple[int, int, int, int]:
    ys, xs = np.where(alpha > floor)
    if len(xs) == 0:
        raise ValueError("Master contains no mark")
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


def place(layer: Image.Image, intensity: np.ndarray) -> Image.Image:
    """Scale the mark to MARK_SCALE and centre it on the adaptive canvas."""
    left, top, right, bottom = alpha_bounds(intensity)
    mark = layer.crop((left, top, right, bottom))

    target = round(CANVAS * MARK_SCALE)
    ratio = target / max(mark.width, mark.height)
    mark = mark.resize(
        (max(1, round(mark.width * ratio)), max(1, round(mark.height * ratio))),
        Image.Resampling.LANCZOS,
    )

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(mark, ((CANVAS - mark.width) // 2, (CANVAS - mark.height) // 2))
    return recentre(canvas)


def recentre(layer: Image.Image) -> Image.Image:
    """Equalise opposite margins of the visible bounds; translation only."""
    alpha = np.asarray(layer.getchannel("A")).astype(np.float32) / 255.0
    left, top, right, bottom = alpha_bounds(alpha)
    dx = round((CANVAS - (left + right)) / 2)
    dy = round((CANVAS - (top + bottom)) / 2)
    if dx == 0 and dy == 0:
        return layer
    shifted = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    shifted.alpha_composite(layer, (dx, dy))
    return shifted


def neon_layer(image: Image.Image, intensity: np.ndarray) -> Image.Image:
    """The mark exactly as drawn, carried over to a transparent layer."""
    rgb = np.asarray(image.convert("RGB")).astype(np.uint8)
    alpha = (np.clip(intensity, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, alpha]), "RGBA")


def ink_layer(mark: np.ndarray, struts: np.ndarray) -> Image.Image:
    """Deep-teal rendering for the crystal-white background.

    Weighting the two signals apart is the whole point: with one flat alpha the
    body and the struts land at similar darkness and the digit reads as a blob.
    Here the body stays a pale tint and only the struts and nodes go dark, so
    the wireframe survives the loss of the glow.
    """
    height, width = mark.shape
    ink = np.zeros((height, width, 4), dtype=np.uint8)
    ink[..., 0], ink[..., 1], ink[..., 2] = LIGHT_INK
    # The glow is cut off rather than tinted: on white it would read as a dirty
    # shadow around the digit instead of light.
    body = np.clip((mark - 0.22) / 0.55, 0.0, 1.0)
    alpha = np.clip(0.62 * body + 0.95 * struts, 0.0, 1.0)
    ink[..., 3] = (alpha ** 0.85 * 255).astype(np.uint8)
    return Image.fromarray(ink, "RGBA")


def enclosed(mask: Image.Image) -> Image.Image:
    """Everything the outer contour encloses, holes included."""
    padded = Image.new("L", (mask.width + 2, mask.height + 2), 0)
    padded.paste(mask, (1, 1))
    outside = padded.copy()
    ImageDraw.floodfill(outside, (0, 0), 255)
    interior = Image.eval(outside, lambda v: 255 - v)
    return Image.composite(
        Image.new("L", padded.size, 255), padded, interior
    ).crop((1, 1, mask.width + 1, mask.height + 1))


def close(mask: Image.Image, width: int) -> Image.Image:
    return mask.filter(ImageFilter.MaxFilter(width)).filter(ImageFilter.MinFilter(width))


def open_(mask: Image.Image, width: int) -> Image.Image:
    return mask.filter(ImageFilter.MinFilter(width)).filter(ImageFilter.MaxFilter(width))


def smooth(mask: Image.Image) -> Image.Image:
    """Take the bead off the contour left by the nodes sitting on the edge."""
    blurred = mask.filter(ImageFilter.GaussianBlur(radius=9.0))
    return blurred.point(lambda v: 255 if v > 128 else 0)


def solid_silhouette(mask: Image.Image) -> Image.Image:
    """Fill the lattice interstices but keep the counter of the 4 open.

    Filling every enclosed region turns the digit into a slab — the triangular
    counter is a hole too. Holes are told apart by size: an opening erases the
    small lattice gaps and leaves only the counter, which is then re-cut.
    """
    filled = enclosed(mask)
    holes = ImageChops.subtract(filled, mask)
    counter = open_(holes, 15)
    return smooth(ImageChops.subtract(filled, counter))


def mono_variants(mark: np.ndarray, struts: np.ndarray) -> "dict[str, Image.Image]":
    """Three readings of the same mark for the Android 13 themed-icon slot."""
    # Derived at a working resolution: the morphology below is O(k²) per pixel.
    scale = MONO_WORKING_WIDTH / mark.shape[1]
    size = (MONO_WORKING_WIDTH, round(mark.shape[0] * scale))

    def resized(channel: np.ndarray, threshold: float) -> Image.Image:
        source = Image.fromarray((np.clip(channel, 0, 1) * 255).astype(np.uint8), "L")
        return source.resize(size, Image.Resampling.LANCZOS).point(
            lambda v: 255 if v > threshold * 255 else 0
        )

    body = resized(mark, 0.10)
    lattice = close(resized(struts, 0.30), 3)

    solid = solid_silhouette(close(body, 5))
    wireframe = lattice
    # Solid digit with the lattice knocked back out: texture without the
    # legibility loss of bare struts.
    engraved = ImageChops.subtract(solid, lattice)

    variants = {}
    for name, silhouette in (("solid", solid), ("wireframe", wireframe), ("engraved", engraved)):
        layer = Image.new("RGBA", silhouette.size, (255, 255, 255, 0))
        layer.putalpha(silhouette)
        variants[name] = place(layer, np.asarray(silhouette).astype(np.float32) / 255.0)
    return variants


def visible_bounds(layer: Image.Image) -> tuple[int, int, int, int]:
    """Where this particular rendering stops being visible.

    A relative floor, because the variants fade out differently: the neon layer
    keeps a glow past the lattice, the flat monochrome mask stops dead at it.
    An absolute floor would measure those falloffs, not the mark.
    """
    alpha = np.asarray(layer.getchannel("A")).astype(np.float32) / 255.0
    return alpha_bounds(alpha, floor=BOUNDS_FLOOR * float(alpha.max()))


def assert_centred(label: str, layer: Image.Image) -> None:
    left, top, right, bottom = visible_bounds(layer)
    margins = (left, CANVAS - right, top, CANVAS - bottom)
    if abs(margins[0] - margins[1]) > 2 or abs(margins[2] - margins[3]) > 2:
        raise AssertionError(f"{label} is not centred: margins={margins}")


def report_scale(label: str, layer: Image.Image) -> None:
    """The mark is placed from one shared measurement, so every variant is the
    same size by construction. What differs is where each rendering's own edge
    lands, so print it rather than assert a number the renderings cannot share.
    """
    left, top, right, bottom = visible_bounds(layer)
    span = max(right - left, bottom - top)
    print(f"{label:>16}: {span}px = {span / CANVAS:.3f} of canvas")


def flatten(layer: Image.Image, background: str) -> Image.Image:
    result = Image.new("RGBA", layer.size, background)
    result.alpha_composite(layer)
    return result


def tinted(mask: Image.Image, background: str, foreground: str) -> Image.Image:
    result = Image.new("RGBA", mask.size, background)
    result.paste(foreground, (0, 0, mask.width, mask.height), mask.getchannel("A"))
    return result


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


def masked(icon: Image.Image, kind: str, size: int = 156) -> Image.Image:
    scaled = icon.resize((size, size), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(scaled, (0, 0), launcher_mask(size, kind))
    return result


def board(path: Path, rows: list[tuple[str, Image.Image]]) -> None:
    columns = 2 if len(rows) > 3 else 1
    lines = (len(rows) + columns - 1) // columns
    width, height = 35 + columns * 600, 35 + lines * 420
    canvas = Image.new("RGB", (width, height), "#E7E9ED")
    draw = ImageDraw.Draw(canvas)
    for index, (label, icon) in enumerate(rows):
        col, row = index % columns, index // columns
        x, y = 35 + col * 600, 35 + row * 420
        draw.rounded_rectangle((x, y, x + 565, y + 370), radius=30, fill="#FFFFFF")
        draw.text((x + 24, y + 20), label, fill="#202124")
        for n, shape in enumerate(("circle", "squircle", "rounded")):
            preview = masked(icon, shape)
            canvas.paste(preview, (x + 30 + n * 176, y + 118), preview)
            draw.text((x + 30 + n * 176, y + 292), shape, fill="#6B6F76")
    canvas.save(path, optimize=True)


def size_strip(path: Path, rows: "list[tuple[str, Image.Image]]") -> None:
    """Every variant at the sizes a launcher actually draws.

    The lattice is the whole risk here: struts that read fine at 1080 turn to
    grey mush at mdpi, and that is exactly where a themed icon has to survive.
    """
    sizes = [192, 144, 96, 72, 48]
    width = 260 + sum(sizes) + 40 * len(sizes)
    canvas = Image.new("RGB", (width, 35 + len(rows) * 230), "#E7E9ED")
    draw = ImageDraw.Draw(canvas)
    for index, (label, icon) in enumerate(rows):
        y = 35 + index * 230
        draw.rounded_rectangle((35, y, width - 35, y + 200), radius=24, fill="#FFFFFF")
        draw.text((60, y + 90), label, fill="#202124")
        x = 260
        for size in sizes:
            preview = masked(icon, "circle", size)
            canvas.paste(preview, (x, y + 100 - size // 2), preview)
            draw.text((x, y + 170), f"{size}px", fill="#6B6F76")
            x += size + 40
    canvas.save(path, optimize=True)


def build() -> dict[str, Image.Image]:
    master = Image.open(MASTER).convert("RGBA")
    mark, struts = master_intensity(master)

    night = place(neon_layer(master, mark), mark)
    day = place(ink_layer(mark, struts), mark)
    monos = mono_variants(mark, struts)

    for label, layer in [("light", day), ("amoled", night)] + list(monos.items()):
        assert_centred(label, layer)
        report_scale(label, layer)

    return {"day": day, "night": night, **{f"mono-{k}": v for k, v in monos.items()}}


def write_android_resources(layers: dict[str, Image.Image], mono: str) -> None:
    """Replace the launcher resources. Only ever run after a board is approved."""
    day = flatten(layers["day"], LIGHT_BACKGROUND)
    night = flatten(layers["night"], DARK_BACKGROUND)

    layers["day"].save(RES / "drawable-nodpi/ic_launcher_foreground_art.webp", format="WEBP", quality=95, method=6)
    layers["night"].save(RES / "drawable-night-nodpi/ic_launcher_foreground_art.webp", format="WEBP", quality=95, method=6)
    layers[f"mono-{mono}"].save(RES / "drawable-nodpi/ic_launcher_monochrome_art.png", optimize=True)

    for density, size in DENSITIES.items():
        day.resize((size, size), Image.Resampling.LANCZOS).save(RES / f"mipmap-{density}/ic_launcher.png", optimize=True)
        night.resize((size, size), Image.Resampling.LANCZOS).save(RES / f"mipmap-night-{density}/ic_launcher.png", optimize=True)


def main(apply: "str | None") -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    layers = build()

    day = flatten(layers["day"], LIGHT_BACKGROUND)
    night = flatten(layers["night"], DARK_BACKGROUND)
    monet = tinted(layers["mono-solid"], "#D9E3FF", "#33426B")
    monochrome = tinted(layers["mono-solid"], "#F4F4F4", "#171717")

    board(
        OUT / "neon-four-review.png",
        [
            ("LIGHT · crystal white", day),
            ("AMOLED · black", night),
            ("MONET", monet),
            ("MONOCHROME", monochrome),
        ],
    )
    board(
        OUT / "neon-four-monochrome-options.png",
        [
            ("A · SOLID", tinted(layers["mono-solid"], "#F4F4F4", "#171717")),
            ("B · WIREFRAME", tinted(layers["mono-wireframe"], "#F4F4F4", "#171717")),
            ("C · ENGRAVED", tinted(layers["mono-engraved"], "#F4F4F4", "#171717")),
            ("A · SOLID / Monet", tinted(layers["mono-solid"], "#D9E3FF", "#33426B")),
            ("B · WIREFRAME / Monet", tinted(layers["mono-wireframe"], "#D9E3FF", "#33426B")),
            ("C · ENGRAVED / Monet", tinted(layers["mono-engraved"], "#D9E3FF", "#33426B")),
        ],
    )
    board(
        OUT / "neon-four-light-options.png",
        [
            ("LIGHT A · deep teal ink", day),
            ("LIGHT B · neon as supplied", flatten(layers["night"], LIGHT_BACKGROUND)),
        ],
    )

    size_strip(
        OUT / "neon-four-sizes.png",
        [
            ("LIGHT", day),
            ("AMOLED", night),
            ("MONO A · solid", monochrome),
            ("MONO C · engraved", tinted(layers["mono-engraved"], "#F4F4F4", "#171717")),
        ],
    )

    preview = OUT / "preview-129"
    preview.mkdir(parents=True, exist_ok=True)
    for name, icon in (("light", day), ("amoled", night), ("monet", monet), ("monochrome", monochrome)):
        icon.resize((129, 129), Image.Resampling.LANCZOS).save(preview / f"neon-four-{name}-129.png", optimize=True)

    if apply:
        write_android_resources(layers, apply)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--apply",
        nargs="?",
        const="solid",
        choices=["solid", "wireframe", "engraved"],
        help="Write the launcher resources using the named monochrome variant.",
    )
    main(parser.parse_args().apply)
