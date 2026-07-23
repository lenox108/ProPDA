#!/usr/bin/env python3
"""Rasterise a flat Figma-exported SVG icon into transparent launcher artwork.

Написан под экспорт вида «<rect> фона + несколько <path> сплошной заливки или
линейного градиента» — этого достаточно для иконок, и это надёжнее QuickLook,
который добавляет по краю полоску незакрытого viewBox.

Что делает:

* `<rect>` во всю канву считается фоном: он НЕ рисуется, а его цвет печатается
  в stdout — им потом кормят `--light-bg` / `--dark-bg` у add_alt_icon.py;
* пути накладываются в порядке документа (painter's algorithm), поэтому
  «перекрывающий» путь честно закрывает нижний — как в Figma;
* путь, залитый цветом фона, трактуется как ВЫРЕЗ (alpha → 0): в экспортах
  именно так рисуют выемки внутри марки;
* подпути внутри одного `d` складываются по even-odd, то есть внутренний контур
  становится дыркой (счётчик у «4»).

Результат — марка на прозрачном фоне: дальше её масштабирует add_alt_icon.py.

    python3 design/app-icon/render_flat_svg.py in.svg out.png [--size 1024]
"""

from __future__ import annotations

import argparse
import re
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

SUPERSAMPLE = 4
SVG_NS = "{http://www.w3.org/2000/svg}"
# Кубическую кривую дробим на столько отрезков: на иконочных размерах этого
# заведомо больше, чем нужно, а с supersample'ом шва не видно.
BEZIER_STEPS = 48


def parse_color(value: str, gradients: dict) -> tuple | str:
    value = (value or "").strip()
    if value.startswith("url(#"):
        return gradients[value[5:-1]]
    if value in ("none", ""):
        return None
    if value == "white":
        return (255, 255, 255)
    if value == "black":
        return (0, 0, 0)
    if value.startswith("#"):
        h = value[1:]
        if len(h) == 3:
            h = "".join(c * 2 for c in h)
        return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    raise SystemExit(f"Не разобрал цвет: {value!r}")


def parse_gradients(root: ET.Element) -> dict:
    out = {}
    for grad in root.iter(f"{SVG_NS}linearGradient"):
        stops = []
        for stop in grad.iter(f"{SVG_NS}stop"):
            offset = float(stop.get("offset", 0))
            stops.append((offset, parse_color(stop.get("stop-color"), {})))
        out[grad.get("id")] = {
            "kind": "linear",
            "x1": float(grad.get("x1", 0)), "y1": float(grad.get("y1", 0)),
            "x2": float(grad.get("x2", 0)), "y2": float(grad.get("y2", 0)),
            "stops": sorted(stops),
        }
    return out


TOKEN = re.compile(r"([MmLlHhVvCcZz])|(-?\d*\.?\d+(?:e-?\d+)?)")


def parse_path(d: str) -> list[list[tuple[float, float]]]:
    """Return subpaths as point lists. Supports M/L/H/V/C/Z, abs and rel."""
    tokens = [(m.group(1), m.group(2)) for m in TOKEN.finditer(d)]
    subpaths, points = [], []
    x = y = 0.0
    cmd = None
    i = 0

    def nums(count: int) -> list[float]:
        nonlocal i
        out = []
        while len(out) < count:
            letter, num = tokens[i]
            if letter is not None:
                raise SystemExit(f"Ожидалось число в пути, получено {letter!r}")
            out.append(float(num))
            i += 1
        return out

    while i < len(tokens):
        letter, num = tokens[i]
        if letter is not None:
            cmd = letter
            i += 1
            if cmd in "Zz":
                if points:
                    subpaths.append(points)
                    points = []
                continue
        if cmd is None:
            raise SystemExit("Путь не начинается с команды")
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            dx, dy = nums(2)
            if points:
                subpaths.append(points)
                points = []
            x, y = (x + dx, y + dy) if rel else (dx, dy)
            points.append((x, y))
            cmd = "l" if rel else "L"  # последующие пары в M — это линии
        elif c == "L":
            dx, dy = nums(2)
            x, y = (x + dx, y + dy) if rel else (dx, dy)
            points.append((x, y))
        elif c == "H":
            (dx,) = nums(1)
            x = x + dx if rel else dx
            points.append((x, y))
        elif c == "V":
            (dy,) = nums(1)
            y = y + dy if rel else dy
            points.append((x, y))
        elif c == "C":
            x1, y1, x2, y2, x3, y3 = nums(6)
            if rel:
                x1, y1, x2, y2, x3, y3 = x + x1, y + y1, x + x2, y + y2, x + x3, y + y3
            x0, y0 = x, y
            for step in range(1, BEZIER_STEPS + 1):
                t = step / BEZIER_STEPS
                u = 1 - t
                points.append((
                    u ** 3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x3,
                    u ** 3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y3,
                ))
            x, y = x3, y3
    if points:
        subpaths.append(points)
    return subpaths


def coverage(subpaths: list, size: int, scale: float, offset: tuple) -> np.ndarray:
    """Even-odd покрытие пути: внутренний контур становится дыркой."""
    acc = np.zeros((size, size), dtype=bool)
    for points in subpaths:
        if len(points) < 3:
            continue
        mask = Image.new("1", (size, size), 0)
        ImageDraw.Draw(mask).polygon(
            [((px - offset[0]) * scale, (py - offset[1]) * scale) for px, py in points], fill=1)
        acc ^= np.asarray(mask, dtype=bool)
    return acc.astype(np.float32)


def gradient_rgb(spec: dict, size: int, scale: float, offset: tuple) -> np.ndarray:
    x1 = (spec["x1"] - offset[0]) * scale
    y1 = (spec["y1"] - offset[1]) * scale
    x2 = (spec["x2"] - offset[0]) * scale
    y2 = (spec["y2"] - offset[1]) * scale
    dx, dy = x2 - x1, y2 - y1
    length2 = dx * dx + dy * dy or 1.0
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    t = np.clip(((xs - x1) * dx + (ys - y1) * dy) / length2, 0.0, 1.0)
    stops = spec["stops"]
    rgb = np.zeros((size, size, 3), dtype=np.float32)
    for channel in range(3):
        rgb[:, :, channel] = np.interp(
            t, [s[0] for s in stops], [s[1][channel] for s in stops])
    return rgb


def render(svg: Path, out: Path, size: int) -> str:
    root = ET.parse(svg).getroot()
    gradients = parse_gradients(root)

    view = root.get("viewBox")
    if view:
        vx, vy, vw, vh = (float(v) for v in view.replace(",", " ").split())
    else:
        vx = vy = 0.0
        vw = vh = float(root.get("width", size))
    if abs(vw - vh) > 0.5:
        raise SystemExit(f"{svg.name}: ожидается квадратный viewBox, а тут {vw}×{vh}")

    work = size * SUPERSAMPLE
    scale = work / vw
    offset = (vx, vy)

    background = None
    rect = root.find(f"{SVG_NS}rect")
    if rect is not None:
        background = parse_color(rect.get("fill"), gradients)
        if not isinstance(background, tuple):
            background = None

    rgb = np.zeros((work, work, 3), dtype=np.float32)
    alpha = np.zeros((work, work), dtype=np.float32)

    for path in root.iter(f"{SVG_NS}path"):
        fill = parse_color(path.get("fill"), gradients)
        if fill is None:
            continue
        cov = coverage(parse_path(path.get("d")), work, scale, offset)
        if isinstance(fill, tuple) and background is not None and fill == background:
            # Заливка цветом фона = вырез в марке (так Figma рисует выемки).
            alpha *= 1.0 - cov
            continue
        src = gradient_rgb(fill, work, scale, offset) if isinstance(fill, dict) \
            else np.broadcast_to(np.float32(fill), (work, work, 3))
        a = cov[:, :, None]
        rgb = src * a + rgb * (1.0 - a)
        alpha = cov + alpha * (1.0 - cov)

    art = np.dstack([rgb, alpha[:, :, None] * 255.0]).astype(np.uint8)
    image = Image.fromarray(art, "RGBA").resize((size, size), Image.Resampling.BOX)
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out, optimize=True)
    return "#{:02X}{:02X}{:02X}".format(*background) if background else ""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("svg", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--size", type=int, default=1024)
    args = parser.parse_args()
    background = render(args.svg, args.out, args.size)
    print(f"{args.out} · фон {background or 'нет'}")


if __name__ == "__main__":
    main()
