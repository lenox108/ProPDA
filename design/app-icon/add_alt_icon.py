#!/usr/bin/env python3
"""Add (or replace) an alternative launcher icon: resources + manifest + registry.

The picker in Settings → Appearance lists whatever this script has wired up, so
adding an icon is a single command:

    python3 design/app-icon/add_alt_icon.py \
        --id p4da_orange --title-ru "P4DA оранжевая" --title-en "P4DA orange" \
        --light path/to/light-master.png --dark path/to/dark-master.png \
        --mono path/to/monochrome-master.png

Masters may be flat squares (background baked in — it is keyed out by corner
colour) or transparent artwork. Either way the mark is cropped to its visible
bounds and re-centred at ART_SCALE of the 1080 adaptive canvas, so every variant
keeps the same optical size under the launcher mask.

Re-running with the same --id overwrites that variant in place; nothing else in
the manifest, registry or strings is touched.
"""

from __future__ import annotations

import argparse
import io
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
REGISTRY = ROOT / "app/src/main/java/forpdateam/ru/forpda/common/appicon/AppIcons.kt"
REVIEW_DIR = Path(__file__).resolve().parent / "alt-icons"

CANVAS = 1080
# Экспортный размер слоёв. Adaptive-слой рисуется в 1.5× размера ярлыка, то есть
# не больше ~288 px даже на xxxhdpi — 1080 в APK не нужен, а весит вчетверо больше.
EXPORT = 512
# Масштаб внутреннего знака по orange-p4da-adaptive/SPEC.md: квадрат мастера
# сжимается до 68% холста adaptive-иконки. Считается ИМЕННО от квадрата мастера,
# а не от обрезанной по контуру марки: тогда все варианты получают одинаковый
# зазор от границ и одинаковый оптический размер под маской лаунчера.
ART_SCALE = 0.68
# Допуск на разницу противоположных полей итоговой маски (SPEC.md).
MARGIN_TOLERANCE = 2
# Ниже этой альфы пиксель не считается частью знака: сглаженные края мастера
# иначе растягивают видимые границы и ломают центрирование.
ALPHA_FLOOR = 8

FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REGULAR = "/System/Library/Fonts/Supplemental/Arial.ttf"
# Fallbacks when a master is transparent and no explicit background is given:
# the same surfaces the default icon uses.
FALLBACK_BG_LIGHT = "#FFF7F5F0"
FALLBACK_BG_DARK = "#FF0B0C0E"
# Tolerance for keying out a flat baked-in background, per channel.
KEY_TOLERANCE = 12

ALIAS_PREFIX = "forpdateam.ru.forpda.Launcher"

MARKER_ALIASES = "<!-- app-icon-variants:aliases"
MARKER_REGISTRY = "// app-icon-variants:registry"
MARKER_TITLES = "<!-- app-icon-variants:titles"
MARKER_SPLASH = "<!-- app-icon-variants:splash"


# --------------------------------------------------------------------------- art


def _normalise_hex(value: str) -> str:
    value = value.strip().lstrip("#").upper()
    if len(value) == 6:
        value = "FF" + value
    if len(value) != 8:
        raise SystemExit(f"Цвет должен быть #RRGGBB или #AARRGGBB, получено: {value}")
    return "#" + value


def _corner_background(image: Image.Image) -> str | None:
    """Flat baked-in background colour, or None if the master is transparent."""
    corners = [
        image.getpixel((1, 1)),
        image.getpixel((image.width - 2, 1)),
        image.getpixel((1, image.height - 2)),
        image.getpixel((image.width - 2, image.height - 2)),
    ]
    if any(c[3] < 250 for c in corners):
        return None
    first = corners[0]
    if any(abs(c[i] - first[i]) > KEY_TOLERANCE for c in corners for i in range(3)):
        # Corners disagree — the design bleeds to the edges, we cannot key it out.
        return None
    return "#FF{:02X}{:02X}{:02X}".format(*first[:3])


def _key_out(image: Image.Image, rgb: tuple[int, int, int]) -> Image.Image:
    data = np.asarray(image).astype(np.int16)
    close = (np.abs(data[:, :, :3] - np.array(rgb, dtype=np.int16)) <= KEY_TOLERANCE).all(axis=2)
    out = np.asarray(image).copy()
    out[close, 3] = 0
    return Image.fromarray(out, "RGBA")


def prepare_art(path: Path, scale: float, as_is: bool = False) -> tuple[Image.Image, str | None]:
    """Return (1080×1080 artwork for the adaptive foreground, background colour).

    With ``as_is`` мастер считается уже готовым слоем adaptive-иконки (нужный
    масштаб и центровка): его только приводят к размеру холста. Так подключают
    арт, собранный отдельным утверждённым генератором.
    """
    master = Image.open(path).convert("RGBA")
    background = _corner_background(master)
    if as_is:
        return master.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS), background
    if background is not None:
        rgb = tuple(int(background[i:i + 2], 16) for i in (3, 5, 7))
        master = _key_out(master, rgb)

    if visual_bounds(master) is None:
        raise SystemExit(f"{path}: после удаления фона не осталось рисунка")

    # Мастер приводим к квадрату, центрируем знак, сжимаем ВЕСЬ квадрат до scale,
    # затем центрируем ещё раз — уже итоговую маску (порядок из SPEC.md).
    edge = min(master.size)
    master = master.crop((
        (master.width - edge) // 2, (master.height - edge) // 2,
        (master.width - edge) // 2 + edge, (master.height - edge) // 2 + edge,
    ))
    art = center_mark(shrink_canvas(center_mark(master), scale))
    return art.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS), background


def visual_bounds(image: Image.Image) -> tuple[int, int, int, int] | None:
    """Границы знака по альфе, без сглаженной «дымки» по краям."""
    alpha = np.asarray(image.convert("RGBA").getchannel("A"))
    rows = np.where((alpha > ALPHA_FLOOR).any(axis=1))[0]
    cols = np.where((alpha > ALPHA_FLOOR).any(axis=0))[0]
    if not len(rows) or not len(cols):
        return None
    return int(cols[0]), int(rows[0]), int(cols[-1]) + 1, int(rows[-1]) + 1


def center_mark(image: Image.Image) -> Image.Image:
    """Сдвигает знак так, чтобы его видимые границы стояли по центру холста."""
    bounds = visual_bounds(image)
    if bounds is None:
        return image
    left, top, right, bottom = bounds
    dx = (image.width - (left + right)) // 2
    dy = (image.height - (top + bottom)) // 2
    if dx == 0 and dy == 0:
        return image
    shifted = Image.new("RGBA", image.size, (0, 0, 0, 0))
    shifted.alpha_composite(image.convert("RGBA"), (dx, dy))
    return shifted


def shrink_canvas(image: Image.Image, scale: float) -> Image.Image:
    """Сжимает квадрат мастера до доли холста, не перерисовывая сам знак."""
    image = image.convert("RGBA")
    width = round(image.width * scale)
    height = round(image.height * scale)
    scaled = image.resize((width, height), Image.Resampling.LANCZOS)
    result = Image.new("RGBA", image.size, (0, 0, 0, 0))
    result.alpha_composite(scaled, ((image.width - width) // 2, (image.height - height) // 2))
    return result


def assert_margins(label: str, image: Image.Image) -> None:
    """Проверка SPEC.md: противоположные поля не расходятся больше допуска."""
    bounds = visual_bounds(image)
    if bounds is None:
        raise SystemExit(f"{label}: пустая маска")
    left, top, right, bottom = bounds
    dx = abs(left - (image.width - right))
    dy = abs(top - (image.height - bottom))
    if dx > MARGIN_TOLERANCE or dy > MARGIN_TOLERANCE:
        raise SystemExit(f"{label}: поля разъехались — по X {dx}px, по Y {dy}px "
                         f"(допуск {MARGIN_TOLERANCE}px)")
    print(f"  ✓ {label}: поля сходятся (ΔX {dx}px, ΔY {dy}px), "
          f"знак {right - left}×{bottom - top}px из {image.width}")


def to_monochrome(art: Image.Image) -> Image.Image:
    """White silhouette + original alpha — Android supplies the Material You tint."""
    white = Image.new("RGBA", art.size, (255, 255, 255, 255))
    white.putalpha(art.getchannel("A"))
    return white


# ----------------------------------------------------------------------- writing


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print(f"  ✎ {path.relative_to(ROOT)}")


def write_webp(path: Path, image: Image.Image, lossless: bool = False) -> None:
    """Кодируем и без потерь, и с потерями — кладём то, что легче.

    Плоская векторная графика выигрывает от lossless, растровые мастера с
    градиентами — от lossy (альфа libwebp всё равно жмёт без потерь, края не плывут).

    ``lossless`` убирает выбор: нужен, когда мастер — чужой утверждённый рендер,
    который договорились не менять ни на пиксель.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    art = image.resize((EXPORT, EXPORT), Image.Resampling.LANCZOS)
    options_list = [{"lossless": True, "method": 6}]
    if not lossless:
        options_list.append({"quality": 95, "method": 6})
    candidates = []
    for options in options_list:
        buffer = io.BytesIO()
        art.save(buffer, "WEBP", **options)
        candidates.append(buffer.getvalue())
    path.write_bytes(min(candidates, key=len))
    print(f"  ✎ {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")


def write_mono_png(path: Path, image: Image.Image) -> None:
    # Монохромный слой — белый силуэт: цвет несёт ноль информации, храним как LA.
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((EXPORT, EXPORT), Image.Resampling.LANCZOS).convert("LA").save(path, optimize=True)
    print(f"  ✎ {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")


def write_resources(icon_id: str, light: Image.Image, dark: Image.Image,
                    mono: Image.Image, lossless: bool = False) -> None:
    name = f"ic_launcher_{icon_id}"
    write_webp(RES / f"drawable-nodpi/{name}_foreground_art.webp", light, lossless)
    write_webp(RES / f"drawable-night-nodpi/{name}_foreground_art.webp", dark, lossless)
    write_mono_png(RES / f"drawable-nodpi/{name}_monochrome_art.png", mono)

    for layer in ("foreground", "monochrome"):
        write(RES / f"drawable/{name}_{layer}.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <bitmap
            android:gravity="fill"
            android:src="@drawable/{name}_{layer}_art" />
    </item>
</layer-list>
""")
    write(RES / f"drawable/{name}_background.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@color/{name}_background" />
</shape>
""")
    # Одного anydpi-v26 хватает на все конфигурации: слои сами тянут -night
    # ресурсы (фон — из values-night/colors.xml, рисунок — из drawable-night-nodpi).
    write(RES / f"mipmap-anydpi-v26/{name}.xml", f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{name}_background" />
    <foreground android:drawable="@drawable/{name}_foreground" />
    <monochrome android:drawable="@drawable/{name}_monochrome" />
</adaptive-icon>
""")


def upsert_color(path: Path, name: str, value: str) -> None:
    text = path.read_text(encoding="utf-8")
    line = f'    <color name="{name}">{value}</color>'
    pattern = re.compile(rf'^[ \t]*<color name="{re.escape(name)}">.*</color>[ \t]*\n', re.M)
    if pattern.search(text):
        text = pattern.sub(line + "\n", text)
    else:
        text = text.replace("</resources>", line + "\n</resources>")
    path.write_text(text, encoding="utf-8")
    print(f"  ✎ {path.relative_to(ROOT)} :: {name}")


def upsert_string(path: Path, name: str, value: str) -> None:
    text = path.read_text(encoding="utf-8")
    line = f'    <string name="{name}">{value}</string>'
    pattern = re.compile(rf'^[ \t]*<string name="{re.escape(name)}">.*</string>[ \t]*\n', re.M)
    if pattern.search(text):
        text = pattern.sub(line + "\n", text)
    elif MARKER_TITLES in text:
        text = _insert_before_marker(text, MARKER_TITLES, line)
    else:
        text = text.replace("</resources>", line + "\n</resources>")
    path.write_text(text, encoding="utf-8")
    print(f"  ✎ {path.relative_to(ROOT)} :: {name}")


def _insert_before_marker(text: str, marker: str, block: str) -> str:
    index = text.index(marker)
    line_start = text.rindex("\n", 0, index) + 1
    return text[:line_start] + block + "\n" + text[line_start:]


def upsert_alias(icon_id: str, alias: str) -> None:
    text = MANIFEST.read_text(encoding="utf-8")
    name = f"ic_launcher_{icon_id}"
    block = f"""        <activity-alias
            android:name="{alias}"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/{name}"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/{name}"
            android:targetActivity=".ui.activities.MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <meta-data
                android:name="android.app.shortcuts"
                android:resource="@xml/shortcuts" />
        </activity-alias>
"""
    existing = re.compile(
        rf'[ \t]*<activity-alias\s+android:name="{re.escape(alias)}".*?</activity-alias>\n',
        re.S)
    if existing.search(text):
        text = existing.sub(block, text)
    else:
        if MARKER_ALIASES not in text:
            raise SystemExit(f"В манифесте нет маркера «{MARKER_ALIASES}»")
        text = _insert_before_marker(text, MARKER_ALIASES, block)
    MANIFEST.write_text(text, encoding="utf-8")
    print(f"  ✎ {MANIFEST.relative_to(ROOT)} :: {alias}")


def upsert_splash_theme(icon_id: str, style: str) -> None:
    path = RES / "values/styles.xml"
    text = path.read_text(encoding="utf-8")
    block = f"""    <style name="{style}" parent="Theme.ForPDA.Splash">
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_{icon_id}</item>
    </style>
"""
    existing = re.compile(rf'[ \t]*<style name="{re.escape(style)}".*?</style>\n', re.S)
    if existing.search(text):
        text = existing.sub(block, text)
    else:
        if MARKER_SPLASH not in text:
            raise SystemExit(f"В values/styles.xml нет маркера «{MARKER_SPLASH}»")
        text = _insert_before_marker(text, MARKER_SPLASH, block)
    path.write_text(text, encoding="utf-8")
    print(f"  ✎ {path.relative_to(ROOT)} :: {style}")


def upsert_registry(icon_id: str, alias: str, splash_style: str,
                    with_subtitle: bool) -> None:
    text = REGISTRY.read_text(encoding="utf-8")
    subtitle = f"\n                    subtitleRes = R.string.app_icon_{icon_id}_desc," if with_subtitle else ""
    block = f"""            AppIconVariant(
                    id = "{icon_id}",
                    alias = "{alias}",
                    titleRes = R.string.app_icon_{icon_id},{subtitle}
                    iconRes = R.mipmap.ic_launcher_{icon_id},
                    splashThemeRes = R.style.{splash_style.replace('.', '_')},
            ),"""
    existing = re.compile(
        rf'[ \t]*AppIconVariant\(\s*\n\s*id = "{re.escape(icon_id)}",.*?\n[ \t]*\),\n', re.S)
    if existing.search(text):
        text = existing.sub(block + "\n", text)
    else:
        if MARKER_REGISTRY not in text:
            raise SystemExit(f"В {REGISTRY.name} нет маркера «{MARKER_REGISTRY}»")
        text = _insert_before_marker(text, MARKER_REGISTRY, block)
    REGISTRY.write_text(text, encoding="utf-8")
    print(f"  ✎ {REGISTRY.relative_to(ROOT)} :: {icon_id}")


def launcher_mask(size: int, kind: str) -> Image.Image:
    """Маски лаунчеров: круг, сквиркл (супер-эллипс) и скруглённый квадрат."""
    scale = 4
    work = size * scale
    mask = Image.new("L", (work, work), 0)
    if kind == "circle":
        ImageDraw.Draw(mask).ellipse((0, 0, work - 1, work - 1), fill=255)
    elif kind == "squircle":
        # |x|^n + |y|^n = 1, n≈4.2 — форма, близкая к маске Pixel/OneUI.
        half = work / 2
        ys, xs = np.mgrid[0:work, 0:work]
        nx = np.abs((xs + 0.5 - half) / half) ** 4.2
        ny = np.abs((ys + 0.5 - half) / half) ** 4.2
        mask = Image.fromarray(np.where(nx + ny <= 1.0, 255, 0).astype(np.uint8), "L")
    else:
        radius = round(work * 0.22)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, work - 1, work - 1), radius, fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def save_review(icon_id: str, light: Image.Image, dark: Image.Image, mono: Image.Image,
                light_bg: str, dark_bg: str) -> None:
    """Лист сверки: каждый слой под кругом, сквирклом и скруглённым квадратом."""
    def tile(art: Image.Image, bg) -> Image.Image:
        base = Image.new("RGBA", art.size, bg)
        base.alpha_composite(art)
        return base

    def rgb(value: str) -> tuple:
        return tuple(int(value[i:i + 2], 16) for i in (3, 5, 7))

    # Monet: система тонирует монохромный слой — показываем характерную пару.
    monet = tile(_tint(mono, "#33426B"), rgb("#FFD9E3FF"))
    layers = [
        ("Светлая", tile(light, rgb(light_bg))),
        ("AMOLED", tile(dark, rgb(dark_bg))),
        ("Monet", monet),
        ("Monochrome", tile(_tint(mono, "#171717"), rgb("#FFF4F4F4"))),
    ]
    kinds = [("Круг", "circle"), ("Сквиркл", "squircle"), ("Скр. квадрат", "rounded")]

    big, gap, pad, head = 200, 26, 30, 124
    board = Image.new("RGB", (pad * 2 + 150 + len(kinds) * (big + gap),
                              head + pad + len(layers) * (big + gap)), "#E8EAED")
    d = ImageDraw.Draw(board)
    f_h = ImageFont.truetype(FONT_BOLD, 30)
    f_l = ImageFont.truetype(FONT_BOLD, 19)
    f_s = ImageFont.truetype(FONT_REGULAR, 17)
    d.text((pad, 22), f"{icon_id} · знак {int(ART_SCALE * 100)}% холста", font=f_h, fill="#1B1D21")
    d.text((pad, 60), "все слои одного размера · проверка трёх масок лаунчера",
           font=f_s, fill="#5A6068")

    for col, (title, kind) in enumerate(kinds):
        x = pad + 150 + col * (big + gap)
        d.text((x, head - 30), title, font=f_l, fill="#3A4048")
        mask = launcher_mask(big, kind)
        for row, (name, tile_img) in enumerate(layers):
            y = head + row * (big + gap)
            if col == 0:
                d.text((pad, y + big // 2 - 10), name, font=f_l, fill="#3A4048")
            shaped = Image.new("RGBA", (big, big), (0, 0, 0, 0))
            shaped.paste(tile_img.convert("RGB").resize((big, big), Image.Resampling.LANCZOS),
                         (0, 0), mask)
            board.paste(shaped.convert("RGB"), (x, y), shaped.getchannel("A"))
    REVIEW_DIR.mkdir(parents=True, exist_ok=True)
    path = REVIEW_DIR / f"{icon_id}-review.png"
    board.save(path, optimize=True)
    print(f"  ✎ {path.relative_to(ROOT)}")


def _tint(mono: Image.Image, foreground: str) -> Image.Image:
    fg = Image.new("RGBA", mono.size, tuple(int(foreground[i:i + 2], 16) for i in (1, 3, 5)))
    fg.putalpha(mono.convert("RGBA").getchannel("A"))
    return fg


# -------------------------------------------------------------------------- main


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--id", required=True,
                        help="стабильный id варианта (a-z0-9_), пишется в настройки")
    parser.add_argument("--title-ru", required=True, help="название в пикере (ru)")
    parser.add_argument("--title-en", help="название в пикере (en); по умолчанию = --title-ru")
    parser.add_argument("--subtitle-ru", help="пояснение под названием в пикере (ru)")
    parser.add_argument("--subtitle-en", help="пояснение под названием (en); по умолчанию = --subtitle-ru")
    parser.add_argument("--lossless", action="store_true",
                        help="кодировать слои только без потерь, даже если lossy легче")
    parser.add_argument("--light", required=True, type=Path, help="мастер для светлой темы")
    parser.add_argument("--dark", type=Path,
                        help="мастер для тёмной темы; по умолчанию = --light")
    parser.add_argument("--mono", type=Path,
                        help="маска для монохромной (Material You) иконки; "
                             "по умолчанию — силуэт светлого мастера")
    parser.add_argument("--light-bg", help="фон светлой иконки #RRGGBB; по умолчанию — "
                                           "цвет углов мастера")
    parser.add_argument("--dark-bg", help="фон тёмной иконки #RRGGBB")
    parser.add_argument("--scale", type=float, default=ART_SCALE,
                        help=f"доля холста под рисунок (по умолчанию {ART_SCALE})")
    parser.add_argument("--as-is", action="store_true",
                        help="мастера уже подготовлены как слои adaptive-иконки: "
                             "не вырезать фон, не менять масштаб и центровку")
    args = parser.parse_args()

    if not re.fullmatch(r"[a-z][a-z0-9_]*", args.id):
        raise SystemExit("--id должен быть в нижнем регистре: [a-z][a-z0-9_]*")
    if args.id == "default":
        raise SystemExit("id «default» занят штатной иконкой")

    dark_master = args.dark or args.light
    print(f"Иконка «{args.id}»:")
    light_art, light_detected = prepare_art(args.light, args.scale, args.as_is)
    dark_art, dark_detected = prepare_art(dark_master, args.scale, args.as_is)
    mono_art = to_monochrome(
        prepare_art(args.mono, args.scale, args.as_is)[0] if args.mono else light_art)

    for label, layer in (("светлая", light_art), ("AMOLED", dark_art), ("monochrome", mono_art)):
        assert_margins(label, layer)

    light_bg = _normalise_hex(args.light_bg) if args.light_bg else (light_detected or FALLBACK_BG_LIGHT)
    dark_bg = _normalise_hex(args.dark_bg) if args.dark_bg else (dark_detected or FALLBACK_BG_DARK)
    print(f"  фон: светлый {light_bg}, тёмный {dark_bg}")

    camel = "".join(p.capitalize() for p in args.id.split("_"))
    alias = f"{ALIAS_PREFIX}.{camel}"
    splash_style = f"Theme.ForPDA.Splash.{camel}"
    write_resources(args.id, light_art, dark_art, mono_art, args.lossless)
    upsert_color(RES / "values/colors.xml", f"ic_launcher_{args.id}_background", light_bg)
    upsert_color(RES / "values-night/colors.xml", f"ic_launcher_{args.id}_background", dark_bg)
    upsert_string(RES / "values/strings.xml", f"app_icon_{args.id}", args.title_ru)
    upsert_string(RES / "values-en/strings.xml", f"app_icon_{args.id}",
                  args.title_en or args.title_ru)
    if args.subtitle_ru:
        upsert_string(RES / "values/strings.xml", f"app_icon_{args.id}_desc", args.subtitle_ru)
        upsert_string(RES / "values-en/strings.xml", f"app_icon_{args.id}_desc",
                      args.subtitle_en or args.subtitle_ru)
    upsert_splash_theme(args.id, splash_style)
    upsert_alias(args.id, alias)
    upsert_registry(args.id, alias, splash_style, with_subtitle=bool(args.subtitle_ru))
    save_review(args.id, light_art, dark_art, mono_art, light_bg, dark_bg)
    print(f"Готово. Псевдоним: {alias}")


if __name__ == "__main__":
    sys.exit(main())
