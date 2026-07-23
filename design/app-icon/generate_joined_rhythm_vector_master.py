"""Draw Joined Rhythm as clean high-resolution geometry (design only)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
OUT = HERE / "joined-rhythm-vector-polish"
FONT = "/System/Library/Fonts/SFNS.ttf"
S = 6
PALETTES = {
    "light": ("#F7F5F0", "#111317", "#F7F5F0", "#D4AF37"),
    "amoled": ("#000000", "#F7F5F0", "#000000", "#D4AF37"),
}


def qbez(p0, p1, p2, steps=16):
    return [((1-t)**2*p0[0] + 2*(1-t)*t*p1[0] + t*t*p2[0],
             (1-t)**2*p0[1] + 2*(1-t)*t*p1[1] + t*t*p2[1])
            for t in (i/steps for i in range(1, steps+1))]


def p_path():
    pts = [(58, 166), (58, 48)]
    pts += qbez((58, 48), (58, 35), (72, 35))
    pts += [(127, 35)]
    pts += qbez((127, 35), (157, 35), (157, 65), 22)
    pts += [(157, 91)]
    pts += qbez((157, 91), (157, 120), (128, 120), 22)
    pts += [(58, 120)]
    return pts


def fitted(text, max_width, size, weight="Semibold"):
    while size > 1:
        font = ImageFont.truetype(FONT, size*S)
        font.set_variation_by_name(weight)
        box = font.getbbox(text)
        if box[2]-box[0] <= max_width*S:
            return font
        size -= 1
    font = ImageFont.truetype(FONT, S)
    font.set_variation_by_name(weight)
    return font


def render(mode, final_size=1080):
    bg, ink, screen, gold = PALETTES[mode]
    im = Image.new("RGB", (192*S, 192*S), bg)
    d = ImageDraw.Draw(im)
    sc = lambda v: round(v*S)

    d.rounded_rectangle((sc(68), sc(50), sc(137), sc(108)), radius=sc(10), fill=screen)
    pts = [(sc(x), sc(y)) for x, y in p_path()]
    width = sc(21)
    d.line(pts, fill=ink, width=width, joint="curve")
    r = width//2
    x, y = pts[0]
    d.ellipse((x-r, y-r, x+r, y+r), fill=ink)
    d.rounded_rectangle((sc(28), sc(66), sc(33), sc(113)), radius=sc(2.5), fill=gold)

    four = fitted("4", 52, 67, "Bold")
    box = d.textbbox((0, 0), "4", font=four)
    fw, fh = box[2]-box[0], box[3]-box[1]
    cx, cy = sc(103), sc(79)
    d.text((cx-fw/2-box[0], cy-fh/2-box[1]-sc(1)), "4", font=four, fill=ink)

    da = fitted("DA", 62, 41, "Semibold")
    box = d.textbbox((0, 0), "DA", font=da)
    # Align the visible bottom of DA exactly with the rounded foot of the P.
    p_bottom = sc(166) + width // 2
    d.text((sc(80)-box[0], p_bottom-box[3]), "DA", font=da, fill=ink)
    return im.resize((final_size, final_size), Image.Resampling.LANCZOS)


def board(light, dark):
    canvas = Image.new("RGB", (2280, 1260), "#ECEEF2")
    canvas.paste(light.resize((960, 960), Image.Resampling.LANCZOS), (120, 160))
    canvas.paste(dark.resize((960, 960), Image.Resampling.LANCZOS), (1200, 160))
    d = ImageDraw.Draw(canvas)
    label = ImageFont.truetype(FONT, 54)
    d.text((120, 70), "LIGHT · VECTOR MASTER", font=label, fill="#111317")
    d.text((1200, 70), "AMOLED · VECTOR MASTER", font=label, fill="#111317")
    return canvas


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    light, dark = render("light"), render("amoled")
    light.save(OUT / "joined-rhythm-light-vector-1080.png", optimize=True)
    dark.save(OUT / "joined-rhythm-amoled-vector-1080.png", optimize=True)
    board(light, dark).save(OUT / "joined-rhythm-vector-comparison.png", optimize=True)
    render("light", 129).save(OUT / "joined-rhythm-light-vector-129.png", optimize=True)
    render("amoled", 129).save(OUT / "joined-rhythm-amoled-vector-129.png", optimize=True)


if __name__ == "__main__":
    main()
