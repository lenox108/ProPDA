from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
OUT = ROOT / "p4da-style-pack"
OUT.mkdir(exist_ok=True)

FONT_REG = "/System/Library/Fonts/SFNS.ttf"
FONT_BOLD = "/System/Library/Fonts/SFNS.ttf"


def font(size: int):
    return ImageFont.truetype(FONT_BOLD, size)


def rounded_mask(size: int, radius: int, kind: str):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    if kind == "circle":
        d.ellipse((0, 0, size - 1, size - 1), fill=255)
    elif kind == "squircle":
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    else:
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius // 2, fill=255)
    return m


def center_text(draw, box, text, fnt, fill):
    x0, y0, x1, y1 = box
    b = draw.textbbox((0, 0), text, font=fnt)
    w, h = b[2] - b[0], b[3] - b[1]
    draw.text(((x0 + x1 - w) / 2 - b[0], (y0 + y1 - h) / 2 - b[1]), text, font=fnt, fill=fill)


def draw_mark(size: int, mode: str):
    s = size / 1024
    def q(v): return round(v * s)

    palettes = {
        "light": dict(bg="#F7F6F2", fg="#202329", screen="#ECE9E2", four="#202329", accent="#A9956F"),
        "amoled": dict(bg="#000000", fg="#F0EBE1", screen="#0C0D0F", four="#F0EBE1", accent="#9B8968"),
        "monochrome": dict(bg=(0, 0, 0, 0), fg="#FFFFFF", screen=(0, 0, 0, 0), four="#FFFFFF", accent=None),
        "monet": dict(bg="#D9E2FF", fg="#253052", screen="#BCC8EC", four="#253052", accent=None),
    }
    p = palettes[mode]
    im = Image.new("RGBA", (size, size), p["bg"])
    d = ImageDraw.Draw(im)

    # One continuous P: stem plus softly rounded bowl.
    d.rounded_rectangle((q(220), q(150), q(350), q(790)), radius=q(38), fill=p["fg"])
    d.rounded_rectangle((q(220), q(150), q(770), q(590)), radius=q(120), fill=p["fg"])

    # Display is a true cutout in monochrome and a quiet inset surface otherwise.
    screen_box = (q(350), q(245), q(655), q(500))
    d.rounded_rectangle(screen_box, radius=q(54), fill=p["screen"])

    # Tiny titanium accent only on the upper/right display edge.
    if p["accent"]:
        d.arc((q(346), q(241), q(659), q(504)), start=270, end=360, fill=p["accent"], width=max(1, q(4)))
        d.line((q(655), q(300), q(655), q(430)), fill=p["accent"], width=max(1, q(4)))

    center_text(d, screen_box, "4", font(q(175)), p["four"])

    # A short shelf joins DA to P instead of turning it into a caption.
    d.rounded_rectangle((q(320), q(655), q(465), q(718)), radius=q(24), fill=p["fg"])
    center_text(d, (q(430), q(615), q(750), q(760)), "DA", font(q(142)), p["fg"])
    return im


def launcher_preview(icon, kind, px=192):
    scaled = icon.resize((px, px), Image.Resampling.LANCZOS)
    mask = rounded_mask(px, 58 if kind == "squircle" else 42, kind)
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    out.paste(scaled, (0, 0), mask)
    return out


def save_pack():
    modes = ["light", "amoled", "monochrome", "monet"]
    icons = {}
    for mode in modes:
        icon = draw_mark(1024, mode)
        icons[mode] = icon
        icon.save(OUT / f"propda-p4da-{mode}-1024.png")

    board = Image.new("RGB", (1800, 1320), "#EEF0F4")
    bd = ImageDraw.Draw(board)
    title = ImageFont.truetype(FONT_REG, 52)
    label = ImageFont.truetype(FONT_REG, 40)
    bd.text((72, 46), "ProPDA · P4DA adaptive icon family", fill="#202329", font=title)
    names = [("light", "LIGHT"), ("amoled", "AMOLED"), ("monochrome", "MONOCHROME"), ("monet", "MONET")]
    for i, (mode, name) in enumerate(names):
        x = 72 + i * 430
        y = 145
        bd.rounded_rectangle((x, y, x + 380, y + 860), radius=48, fill="#FFFFFF")
        bd.text((x + 24, y + 24), f"0{i+1}  {name}", fill="#202329", font=label)
        main = icons[mode].resize((340, 340), Image.Resampling.LANCZOS)
        board.paste(main.convert("RGB"), (x + 20, y + 105))
        kinds = ["circle", "squircle", "rounded"]
        for j, kind in enumerate(kinds):
            pv = launcher_preview(icons[mode], kind, 104)
            backdrop = Image.new("RGBA", (124, 124), "#D9DCE2")
            backdrop.alpha_composite(pv, (10, 10))
            board.paste(backdrop.convert("RGB"), (x + 12 + j * 122, y + 485))
        bd.text((x + 24, y + 650), "Same geometry\nLight / dark / system tint", fill="#59606C", font=ImageFont.truetype(FONT_REG, 29), spacing=10)
    board.save(ROOT / "propda-p4da-adaptive-family-v68.png")


if __name__ == "__main__":
    save_pack()
