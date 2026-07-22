#!/usr/bin/env python3
"""PDA lettering studies inside the approved ProPDA 4 silhouette."""

import importlib.util
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageChops, ImageFilter, ImageOps

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("icon", HERE / "generate_minimal_amoled_icon.py")
icon = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(icon)


def face(size, bold=True):
    names = ("DejaVuSans-Bold.ttf", "Arial Bold.ttf") if bold else ("DejaVuSans.ttf", "Arial.ttf")
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def clean_base():
    outer = icon.vector_outer_mask()
    approved = icon.adaptive(icon.crisp_mask(icon.build_master()), icon.IOS_INK).getchannel("A")
    # Retain only the approved terminal cut-out; the PDA area is rebuilt below.
    terminal_hole = Image.new("L", outer.size, 0)
    inv = ImageChops.invert(approved)
    terminal_hole.paste(inv.crop((405, 555, 555, 665)), (405, 555))
    return ImageChops.subtract(outer, terminal_hole)


def subtract(alpha, cut):
    return ImageChops.subtract(alpha, cut)


def text_cut(chars, size, centers, bold=True):
    cut = Image.new("L", (1080, 1080), 0)
    d = ImageDraw.Draw(cut)
    f = face(size, bold)
    for ch, (x, y) in zip(chars, centers):
        d.text((x, y), ch, font=f, fill=255, anchor="mm", stroke_width=0)
    return cut


def open_a_cut(center=(663, 690), height=104, width=72, stroke=18):
    """Samsung-like open A / lambda, with rounded terminals."""
    cx, cy = center
    cut = Image.new("L", (1080, 1080), 0)
    d = ImageDraw.Draw(cut)
    left = (cx-width//2, cy+height//2)
    apex = (cx, cy-height//2)
    right = (cx+width//2, cy+height//2)
    d.line((left, apex, right), fill=255, width=stroke, joint="curve")
    r = stroke//2
    for x, y in (left, right):
        d.ellipse((x-r, y-r, x+r, y+r), fill=255)
    return cut


def p_and_open_a_cut(p_size=103):
    return aligned_pa_cut(647, p_size)


def aligned_pa_cut(center_x, p_size=103):
    # Final direction: a conventional A keeps the vertical PDA reading clear.
    cut = Image.new("L", (1080, 1080), 0)
    f = face(p_size)
    target_width = 70
    # The actual right stem spans x=548..746. Its exact centre is x=647;
    # a 70 px glyph therefore leaves 64 px on both sides.
    stem_center_x = center_x
    # Normalise the visible glyph widths as well as their centres. P is
    # naturally narrower than A in the font, which made their outer edges
    # disagree even though both were centred on x=663.
    for ch, (cx, cy) in zip("PA", ((stem_center_x, 382), (stem_center_x, 690))):
        glyph = Image.new("L", (180, 180), 0)
        gd = ImageDraw.Draw(glyph)
        gd.text((90, 90), ch, font=f, fill=255, anchor="mm")
        glyph = glyph.crop(glyph.getbbox())
        glyph = glyph.resize((target_width, glyph.height), Image.Resampling.LANCZOS)
        cut.paste(glyph, (round(cx-target_width/2), round(cy-glyph.height/2)), glyph)
    return cut


def terminal_prompt_cut(cx, cy, direction="right"):
    cut = Image.new("L", (1080, 1080), 0)
    d = ImageDraw.Draw(cut)
    if direction == "right":
        d.line(((cx-34, cy-24), (cx-8, cy), (cx-34, cy+24)),
               fill=255, width=12, joint="curve")
        d.rounded_rectangle((cx+7, cy+17, cx+50, cy+29), radius=5, fill=255)
    else:
        d.line(((cx+34, cy-24), (cx+8, cy), (cx+34, cy+24)),
               fill=255, width=12, joint="curve")
        d.rounded_rectangle((cx-50, cy+17, cx-7, cy+29), radius=5, fill=255)
    return cut


def add_dots(alpha, y, spacing=17, radius=6):
    d = ImageDraw.Draw(alpha)
    for x in (663-spacing, 663, 663+spacing):
        d.ellipse((x-radius, y-radius, x+radius, y+radius), fill=255)
    return alpha


def variant(kind):
    alpha = clean_base()
    if kind == 1:  # unified, balanced
        alpha = subtract(alpha, text_cut("PDA", 111, ((663, 382), (663, 535), (663, 690))))
        return add_dots(alpha, 535, 16, 5)
    if kind == 2:  # compact and airy
        alpha = subtract(alpha, text_cut("PDA", 94, ((663, 390), (663, 535), (663, 680))))
        return add_dots(alpha, 535, 14, 4)
    if kind == 3:  # softer regular geometry
        alpha = subtract(alpha, text_cut("PDA", 108, ((663, 382), (663, 535), (663, 690)), bold=False))
        return add_dots(alpha, 535, 15, 5)
    if kind == 4:  # hairline outlined letters
        solid = text_cut("PDA", 112, ((663, 382), (663, 535), (663, 690)))
        inner = solid.filter(ImageFilter.MinFilter(11))
        alpha = subtract(alpha, ImageChops.subtract(solid, inner))
        return alpha
    if kind == 5:  # connected vertical ligature
        cut = text_cut("PDA", 104, ((660, 383), (660, 535), (660, 688)))
        d = ImageDraw.Draw(cut)
        d.rounded_rectangle((620, 414, 632, 658), radius=6, fill=255)
        alpha = subtract(alpha, cut)
        return add_dots(alpha, 535, 15, 5)
    # P + forum bubble + A
    alpha = subtract(alpha, p_and_open_a_cut())
    bubble = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(bubble)
    d.rounded_rectangle((617, 493, 707, 570), radius=24, fill=255)
    d.polygon(((631, 566), (619, 588), (651, 568)), fill=255)
    alpha = subtract(alpha, bubble)
    return add_dots(alpha, 535, 16, 5)


def render(alpha):
    art = Image.new("RGBA", alpha.size, (*icon.IOS_INK, 0))
    art.putalpha(alpha)
    art = icon.add_hairline(art, icon.MUTED_GOLD)
    canvas = icon.ios_white_background()
    canvas.alpha_composite(art)
    return icon.web_squircle(canvas, 610)


def forum_symbol_variant(kind):
    alpha = clean_base()
    alpha = subtract(alpha, p_and_open_a_cut())
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)

    if kind == 1:  # filled dialogue
        d.rounded_rectangle((617, 493, 707, 570), radius=24, fill=255)
        d.polygon(((631, 566), (619, 588), (651, 568)), fill=255)
        alpha = subtract(alpha, cut)
        return add_dots(alpha, 535, 16, 5)
    if kind == 2:  # outline dialogue
        d.rounded_rectangle((618, 496, 706, 568), radius=23, outline=255, width=8)
        d.line(((637, 565), (622, 584), (650, 568)), fill=255, width=8, joint="curve")
        alpha = subtract(alpha, cut)
        return alpha
    if kind == 3:  # discussion / two messages
        d.rounded_rectangle((612, 494, 681, 550), radius=18, outline=255, width=7)
        d.line(((626, 548), (615, 564), (640, 550)), fill=255, width=7, joint="curve")
        d.rounded_rectangle((642, 526, 710, 580), radius=18, outline=255, width=7)
        d.line(((694, 578), (705, 590), (681, 580)), fill=255, width=7, joint="curve")
    elif kind == 4:  # compact ellipsis capsule
        d.rounded_rectangle((620, 508, 706, 562), radius=27, outline=255, width=7)
        for x in (642, 663, 684):
            d.ellipse((x-5, 530-5, x+5, 530+5), fill=255)
    elif kind == 5:  # apps grid
        for x in (630, 667):
            for y in (503, 540):
                d.rounded_rectangle((x, y, x+27, y+27), radius=6, outline=255, width=7)
    elif kind == 6:  # internet globe
        d.ellipse((622, 492, 704, 574), outline=255, width=7)
        d.arc((642, 492, 684, 574), 90, 270, fill=255, width=6)
        d.arc((642, 492, 684, 574), 270, 90, fill=255, width=6)
        d.line((625, 533, 701, 533), fill=255, width=6)
    elif kind == 7:  # code brackets
        d.line(((649, 507), (625, 533), (649, 559)), fill=255, width=8, joint="curve")
        d.line(((677, 507), (701, 533), (677, 559)), fill=255, width=8, joint="curve")
        d.line((670, 500, 654, 566), fill=255, width=6)
    else:  # wifi
        d.arc((619, 493, 707, 575), 210, 330, fill=255, width=8)
        d.arc((635, 511, 691, 566), 210, 330, fill=255, width=8)
        d.ellipse((658, 553, 668, 563), fill=255)
    return subtract(alpha, cut)


def forum_sheet():
    labels = (
        "F1  CHAT", "F2  CHAT LINE", "F3  THREAD", "F4  ELLIPSIS",
        "F5  APPS", "F6  INTERNET", "F7  CODE", "F8  WI-FI",
    )
    cell = 640
    sheet = Image.new("RGB", (cell*4, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(27)
    for i, label in enumerate(labels):
        tile = render(forum_symbol_variant(i+1)).resize((520, 520), Image.Resampling.LANCZOS)
        x = (i % 4)*cell + 60
        y = (i // 4)*cell + 45
        sheet.paste(tile, (x, y), tile)
        draw.text((x+260, y+545), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-forum-symbol-options-v27.png"
    sheet.save(out, optimize=True)
    return out


def android_d_variant(kind):
    alpha = clean_base()
    alpha = subtract(alpha, p_and_open_a_cut())
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)

    if kind in (1, 4):  # solid D body
        d.rounded_rectangle((620, 492, 676, 575), radius=8, fill=255)
        d.ellipse((638, 492, 710, 575), fill=255)
    elif kind in (2, 5):  # outlined D body
        d.line((623, 499, 623, 570), fill=255, width=8)
        d.arc((622, 493, 708, 576), -90, 90, fill=255, width=8)
        d.line((665, 575, 623, 575), fill=255, width=8)
    else:  # compact Android dome shaped like D
        d.line((622, 515, 622, 568), fill=255, width=8)
        d.arc((621, 500, 707, 579), 190, 350, fill=255, width=8)
        d.line((626, 563, 700, 563), fill=255, width=8)

    # Antennae make the D read as Android without a separate robot icon.
    if kind != 5:
        d.line((641, 501, 630, 484), fill=255, width=6)
        d.line((686, 501, 697, 484), fill=255, width=6)

    alpha = subtract(alpha, cut)
    ad = ImageDraw.Draw(alpha)
    if kind in (1, 3):
        for x in (649, 680):
            ad.ellipse((x-5, 532-5, x+5, 532+5), fill=255)
    elif kind == 4:
        for x in (648, 681):
            ad.ellipse((x-5, 523-5, x+5, 523+5), fill=255)
        ad.rounded_rectangle((644, 544, 685, 551), radius=3, fill=255)
    elif kind == 6:
        for x in (649, 680):
            ad.ellipse((x-4, 537-4, x+4, 537+4), fill=255)
    else:
        # One small forum ellipsis inside the Android-D counter.
        for x in (646, 663, 680):
            ad.ellipse((x-4, 535-4, x+4, 535+4), fill=255)
    return alpha


def android_d_sheet():
    labels = (
        "A1  DROID D", "A2  D + CHAT", "A3  DROID DOME",
        "A4  ROBOT FACE", "A5  MINIMAL D", "A6  COMPACT",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(29)
    for i, label in enumerate(labels):
        tile = render(android_d_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-android-d-options-v28.png"
    sheet.save(out, optimize=True)
    return out


def apps_d_variant(kind):
    alpha = clean_base()
    alpha = subtract(alpha, p_and_open_a_cut())
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)

    if kind == 1:  # approved outlined 2x2
        for x in (634, 669):
            for y in (505, 540):
                d.rounded_rectangle((x, y, x+25, y+25), radius=5, outline=255, width=6)
    elif kind == 2:  # solid 2x2
        for x in (634, 669):
            for y in (505, 540):
                d.rounded_rectangle((x, y, x+25, y+25), radius=6, fill=255)
    elif kind == 3:  # compact 3x3 app drawer
        for x in (636, 659, 682):
            for y in (508, 531, 554):
                d.rounded_rectangle((x, y, x+13, y+13), radius=4, fill=255)
    elif kind == 4:  # one app tile containing a grid
        d.rounded_rectangle((622, 495, 708, 579), radius=22, fill=255)
        alpha = subtract(alpha, cut)
        ad = ImageDraw.Draw(alpha)
        for x in (643, 672):
            for y in (516, 545):
                ad.rounded_rectangle((x, y, x+17, y+17), radius=4, fill=255)
        return alpha
    elif kind == 5:  # D-shaped modular container
        d.rounded_rectangle((619, 493, 665, 578), radius=7, fill=255)
        d.ellipse((638, 493, 709, 578), fill=255)
        alpha = subtract(alpha, cut)
        ad = ImageDraw.Draw(alpha)
        for x in (640, 670):
            for y in (514, 544):
                ad.rounded_rectangle((x, y, x+17, y+17), radius=4, fill=255)
        return alpha
    elif kind == 6:  # staggered dynamic tiles
        boxes = ((631, 503, 658, 530), (668, 497, 700, 529),
                 (626, 539, 660, 571), (670, 540, 697, 567))
        for box in boxes:
            d.rounded_rectangle(box, radius=7, outline=255, width=6)
    elif kind == 7:  # phone/app screen
        d.rounded_rectangle((627, 488, 702, 582), radius=17, outline=255, width=7)
        for x in (643, 669):
            for y in (510, 536):
                d.rounded_rectangle((x, y, x+15, y+15), radius=4, fill=255)
        d.rounded_rectangle((654, 565, 675, 570), radius=2, fill=255)
    else:  # four circular app nodes
        for x in (646, 681):
            for y in (517, 552):
                d.ellipse((x-12, y-12, x+12, y+12), outline=255, width=6)
    return subtract(alpha, cut)


def apps_d_sheet():
    labels = (
        "G1  GRID LINE", "G2  GRID SOLID", "G3  APP DRAWER", "G4  APP TILE",
        "G5  D-GRID", "G6  DYNAMIC", "G7  PHONE APPS", "G8  NODES",
    )
    cell = 640
    sheet = Image.new("RGB", (cell*4, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(27)
    for i, label in enumerate(labels):
        tile = render(apps_d_variant(i+1)).resize((520, 520), Image.Resampling.LANCZOS)
        x = (i % 4)*cell + 60
        y = (i // 4)*cell + 45
        sheet.paste(tile, (x, y), tile)
        draw.text((x+260, y+545), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-app-grid-options-v29.png"
    sheet.save(out, optimize=True)
    return out


def robot_face_variant(kind):
    alpha = clean_base()
    alpha = subtract(alpha, p_and_open_a_cut())
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)

    if kind in (1, 2, 3, 4):
        # Solid D-shaped robot head.
        d.rounded_rectangle((620, 500, 671, 570), radius=8, fill=255)
        d.ellipse((637, 500, 708, 570), fill=255)
    elif kind in (5, 7):
        # Outline version for a lighter visual weight.
        d.rounded_rectangle((620, 499, 705, 571), radius=20, outline=255, width=7)
    elif kind == 6:
        # Pixel-tech robot.
        d.rounded_rectangle((621, 499, 705, 571), radius=8, fill=255)
    else:
        # Android dome with a short D-like stem.
        d.rounded_rectangle((620, 526, 705, 571), radius=7, fill=255)
        d.pieslice((620, 488, 705, 570), 180, 360, fill=255)

    # Antenna styles.
    if kind in (1, 3, 4, 6, 8):
        d.line((640, 504, 630, 487), fill=255, width=6)
        d.line((687, 504, 697, 487), fill=255, width=6)
    elif kind == 2:
        d.line((663, 500, 663, 478), fill=255, width=6)
        d.ellipse((658, 473, 668, 483), fill=255)

    alpha = subtract(alpha, cut)
    ink = ImageDraw.Draw(alpha)

    # Face details are restored in the foreground colour.
    if kind == 1:  # balanced face
        for x in (648, 681):
            ink.ellipse((x-5, 525-5, x+5, 525+5), fill=255)
        ink.rounded_rectangle((647, 546, 683, 553), radius=3, fill=255)
    elif kind == 2:  # three-dot forum bot
        for x in (647, 664, 681):
            ink.ellipse((x-4, 536-4, x+4, 536+4), fill=255)
    elif kind == 3:  # friendly smile
        for x in (648, 681):
            ink.ellipse((x-4, 523-4, x+4, 523+4), fill=255)
        ink.arc((646, 529, 684, 558), 15, 165, fill=255, width=5)
    elif kind == 4:  # terminal face
        ink.line(((646, 520), (655, 528), (646, 536)), fill=255, width=5, joint="curve")
        ink.rounded_rectangle((667, 533, 685, 538), radius=2, fill=255)
    elif kind == 5:  # outline + eyes
        for x in (648, 680):
            ink.ellipse((x-4, 535-4, x+4, 535+4), fill=255)
    elif kind == 6:  # pixel eyes and mouth
        ink.rectangle((644, 520, 654, 530), fill=255)
        ink.rectangle((678, 520, 688, 530), fill=255)
        ink.rectangle((651, 547, 681, 553), fill=255)
    elif kind == 7:  # minimal one-line face
        ink.rounded_rectangle((643, 532, 682, 540), radius=4, fill=255)
    else:  # classic Android eyes
        for x in (648, 680):
            ink.ellipse((x-4, 535-4, x+4, 535+4), fill=255)
    return alpha


def robot_face_sheet():
    labels = (
        "R1  BALANCED", "R2  FORUM BOT", "R3  FRIENDLY", "R4  TERMINAL BOT",
        "R5  OUTLINE", "R6  PIXEL BOT", "R7  MINIMAL", "R8  ANDROID DOME",
    )
    cell = 640
    sheet = Image.new("RGB", (cell*4, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(26)
    for i, label in enumerate(labels):
        tile = render(robot_face_variant(i+1)).resize((520, 520), Image.Resampling.LANCZOS)
        x = (i % 4)*cell + 60
        y = (i // 4)*cell + 45
        sheet.paste(tile, (x, y), tile)
        draw.text((x+260, y+545), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-robot-face-options-v30.png"
    sheet.save(out, optimize=True)
    return out


def robot_left_variant(kind):
    # Start from the clean vector body: the terminal is intentionally removed.
    alpha = icon.vector_outer_mask()
    alpha = subtract(alpha, p_and_open_a_cut())
    settings = {
        1: (500, 535, 82, 72, "pixel"),
        2: (492, 535, 94, 80, "pixel"),
        3: (475, 542, 86, 74, "pixel"),
        4: (510, 540, 76, 66, "pixel"),
        5: (493, 535, 88, 74, "outline"),
        6: (485, 535, 98, 82, "forum"),
    }
    cx, cy, w, h, style = settings[kind]
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    left, top = cx-w//2, cy-h//2
    right, bottom = cx+w//2, cy+h//2
    if style == "outline":
        d.rounded_rectangle((left, top, right, bottom), radius=12, outline=255, width=7)
    else:
        d.rounded_rectangle((left, top, right, bottom), radius=10, fill=255)
    d.line((left+18, top+3, left+9, top-13), fill=255, width=6)
    d.line((right-18, top+3, right-9, top-13), fill=255, width=6)
    alpha = subtract(alpha, cut)
    ink = ImageDraw.Draw(alpha)
    if style == "forum":
        for x in (cx-17, cx, cx+17):
            ink.ellipse((x-4, cy-4, x+4, cy+4), fill=255)
    else:
        eye_y = cy-9
        ink.rectangle((cx-22, eye_y-5, cx-12, eye_y+5), fill=255)
        ink.rectangle((cx+12, eye_y-5, cx+22, eye_y+5), fill=255)
        ink.rounded_rectangle((cx-18, cy+14, cx+18, cy+21), radius=3, fill=255)
    return alpha


def robot_left_sheet():
    labels = (
        "L1  BALANCED", "L2  LARGE", "L3  LEFT", "L4  COMPACT",
        "L5  OUTLINE", "L6  FORUM BOT",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(29)
    for i, label in enumerate(labels):
        tile = render(robot_left_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-robot-left-layout-options-v31.png"
    sheet.save(out, optimize=True)
    return out


def terminal_bot_left_variant(kind):
    alpha = icon.vector_outer_mask()
    alpha = subtract(alpha, p_and_open_a_cut())
    settings = {
        1: (500, 535, 88, 70),
        2: (492, 535, 100, 80),
        3: (475, 540, 90, 72),
        4: (510, 540, 78, 62),
        5: (495, 525, 92, 74),
        6: (487, 548, 94, 76),
    }
    cx, cy, w, h = settings[kind]
    left, top = cx-w//2, cy-h//2
    right, bottom = cx+w//2, cy+h//2
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    # Slight D-shaped head: flat left edge, rounder right side.
    d.rounded_rectangle((left, top, cx+8, bottom), radius=9, fill=255)
    d.ellipse((cx-18, top, right, bottom), fill=255)
    d.line((left+19, top+3, left+10, top-13), fill=255, width=6)
    d.line((right-18, top+3, right-9, top-13), fill=255, width=6)
    alpha = subtract(alpha, cut)

    ink = ImageDraw.Draw(alpha)
    # Terminal prompt is contained inside the robot face.
    sx = cx-20
    sy = cy-8
    ink.line(((sx, sy-7), (sx+9, sy), (sx, sy+7)), fill=255, width=5, joint="curve")
    ink.rounded_rectangle((cx+4, cy+6, cx+25, cy+11), radius=2, fill=255)
    return alpha


def terminal_bot_left_sheet():
    labels = (
        "T1  BALANCED", "T2  LARGE", "T3  LEFT",
        "T4  COMPACT", "T5  HIGH", "T6  LOW",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(29)
    for i, label in enumerate(labels):
        tile = render(terminal_bot_left_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-terminal-bot-left-options-v32.png"
    sheet.save(out, optimize=True)
    return out


def signature_variant(kind):
    """Final-detail studies: Android + forum + terminal in one compact mark."""
    alpha = icon.vector_outer_mask()
    alpha = subtract(alpha, p_and_open_a_cut())
    accent = Image.new("L", alpha.size, 0)

    # A stable base position chosen from T1 Balanced.
    cx, cy = (500, 535)
    w, h = (88, 70)
    if kind in (6, 8):
        w, h = (96, 76)
        cx = 496
    left, top = cx-w//2, cy-h//2
    right, bottom = cx+w//2, cy+h//2

    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    # Flat left + rounded right subtly recalls the missing letter D.
    d.rounded_rectangle((left, top, cx+7, bottom), radius=9, fill=255)
    d.ellipse((cx-18, top, right, bottom), fill=255)
    d.line((left+19, top+3, left+10, top-13), fill=255, width=6)
    d.line((right-18, top+3, right-9, top-13), fill=255, width=6)

    # Forum speech tail. S8 uses gold for the tiny signature accent.
    if kind in (2, 6, 8):
        if kind == 8:
            ad = ImageDraw.Draw(accent)
            ad.polygon(((left+14, bottom-3), (left+5, bottom+15), (left+28, bottom-2)), fill=255)
        else:
            d.polygon(((left+14, bottom-3), (left+5, bottom+15), (left+28, bottom-2)), fill=255)

    alpha = subtract(alpha, cut)
    ink = ImageDraw.Draw(alpha)

    if kind in (3, 7):  # forum ellipsis
        for x in (cx-17, cx, cx+17):
            ink.ellipse((x-4, cy-4, x+4, cy+4), fill=255)
    elif kind == 5:  # simple friendly Android face
        for x in (cx-18, cx+18):
            ink.ellipse((x-5, cy-9-5, x+5, cy-9+5), fill=255)
        ink.rounded_rectangle((cx-18, cy+14, cx+18, cy+21), radius=3, fill=255)
    else:  # terminal prompt contained inside the bot
        sx, sy = cx-20, cy-8
        ink.line(((sx, sy-7), (sx+9, sy), (sx, sy+7)), fill=255, width=5, joint="curve")
        ink.rounded_rectangle((cx+4, cy+6, cx+25, cy+11), radius=2, fill=255)

    if kind == 4:  # small online/status accent
        ImageDraw.Draw(accent).ellipse((right-3, top-8, right+9, top+4), fill=255)
    elif kind == 7:  # one restrained gold forum dot
        ImageDraw.Draw(accent).ellipse((cx-4, cy-4, cx+4, cy+4), fill=255)
    return alpha, accent


def render_signature(alpha, accent):
    art = Image.new("RGBA", alpha.size, (*icon.IOS_INK, 0))
    art.putalpha(alpha)
    art = icon.add_hairline(art, icon.MUTED_GOLD)
    canvas = icon.ios_white_background()
    canvas.alpha_composite(art)
    if accent.getbbox():
        gold = Image.new("RGBA", accent.size, (*icon.MUTED_GOLD, 0))
        gold.putalpha(accent)
        canvas.alpha_composite(gold)
    return icon.web_squircle(canvas, 610)


def signature_sheet():
    labels = (
        "S1  PURE", "S2  CHAT TAIL", "S3  FORUM", "S4  ONLINE",
        "S5  FRIENDLY", "S6  D-CHAT", "S7  GOLD DOT", "S8  SIGNATURE",
    )
    cell = 640
    sheet = Image.new("RGB", (cell*4, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(26)
    for i, label in enumerate(labels):
        alpha, accent = signature_variant(i+1)
        tile = render_signature(alpha, accent).resize((520, 520), Image.Resampling.LANCZOS)
        x = (i % 4)*cell + 60
        y = (i // 4)*cell + 45
        sheet.paste(tile, (x, y), tile)
        draw.text((x+260, y+545), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-final-signature-options-v33.png"
    sheet.save(out, optimize=True)
    return out


def _terminal_bot(alpha, cx, cy, w=88, h=70, tail=False):
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    left, top, right, bottom = cx-w//2, cy-h//2, cx+w//2, cy+h//2
    d.rounded_rectangle((left, top, cx+7, bottom), radius=9, fill=255)
    d.ellipse((cx-18, top, right, bottom), fill=255)
    d.line((left+19, top+3, left+10, top-13), fill=255, width=6)
    d.line((right-18, top+3, right-9, top-13), fill=255, width=6)
    if tail:
        d.polygon(((left+14, bottom-3), (left+5, bottom+15), (left+28, bottom-2)), fill=255)
    alpha = subtract(alpha, cut)
    ink = ImageDraw.Draw(alpha)
    sx, sy = cx-20, cy-8
    ink.line(((sx, sy-7), (sx+9, sy), (sx, sy+7)), fill=255, width=5, joint="curve")
    ink.rounded_rectangle((cx+4, cy+6, cx+25, cy+11), radius=2, fill=255)
    return alpha


def structural_variant(kind):
    alpha = icon.vector_outer_mask()
    accent = Image.new("L", alpha.size, 0)

    if kind == 1:  # triangular counter becomes the forum/Android chamber
        alpha = subtract(alpha, p_and_open_a_cut())
        window = Image.new("L", alpha.size, 0)
        wd = ImageDraw.Draw(window)
        wd.polygon(((392, 622), (520, 440), (520, 630)), fill=255)
        alpha = subtract(alpha, window)
        # Black robot restored inside the white counter.
        rd = ImageDraw.Draw(alpha)
        rd.rounded_rectangle((443, 535, 500, 584), radius=10, fill=255)
        rd.line((454, 538, 448, 527), fill=255, width=5)
        rd.line((489, 538, 495, 527), fill=255, width=5)
    elif kind == 2:  # gold architectural divider
        alpha = subtract(alpha, p_and_open_a_cut())
        alpha = _terminal_bot(alpha, 486, 535, 86, 68)
        ImageDraw.Draw(accent).rounded_rectangle((558, 348, 564, 724), radius=3, fill=255)
    elif kind == 3:  # horizontal wordmark; no vertical PA column
        alpha = _terminal_bot(alpha, 505, 505, 102, 80, tail=True)
        word = Image.new("L", alpha.size, 0)
        d = ImageDraw.Draw(word)
        d.text((647, 690), "PDA", font=face(61), fill=255, anchor="mm")
        alpha = subtract(alpha, word)
    elif kind == 4:  # robot badge in the open triangular field
        alpha = subtract(alpha, p_and_open_a_cut())
        alpha = _terminal_bot(alpha, 486, 535, 82, 66)
        ImageDraw.Draw(accent).ellipse((425, 474, 547, 596), outline=255, width=6)
    elif kind == 5:  # large forum bubble replaces the little robot
        alpha = subtract(alpha, p_and_open_a_cut())
        bubble = Image.new("L", alpha.size, 0)
        d = ImageDraw.Draw(bubble)
        d.rounded_rectangle((410, 485, 548, 584), radius=31, outline=255, width=9)
        d.line(((438, 578), (419, 610), (463, 583)), fill=255, width=9, joint="curve")
        for x in (447, 479, 511):
            d.ellipse((x-6, 535-6, x+6, 535+6), fill=255)
        alpha = subtract(alpha, bubble)
    elif kind == 6:  # centre icon, compact brand signature on the foot
        alpha = _terminal_bot(alpha, 542, 500, 108, 84, tail=True)
        word = Image.new("L", alpha.size, 0)
        ImageDraw.Draw(word).text((647, 704), "PDA", font=face(48), fill=255, anchor="mm")
        alpha = subtract(alpha, word)
    elif kind == 7:  # PA rail + forum node
        alpha = subtract(alpha, p_and_open_a_cut())
        node = Image.new("L", alpha.size, 0)
        nd = ImageDraw.Draw(node)
        nd.ellipse((446, 492, 536, 582), outline=255, width=8)
        for x in (473, 491, 509):
            nd.ellipse((x-4, 537-4, x+4, 537+4), fill=255)
        alpha = subtract(alpha, node)
        ad = ImageDraw.Draw(accent)
        ad.line((565, 382, 565, 690), fill=255, width=5)
        ad.ellipse((558, 528, 572, 542), fill=255)
    else:  # golden corner brackets frame the terminal bot
        alpha = subtract(alpha, p_and_open_a_cut())
        alpha = _terminal_bot(alpha, 486, 535, 88, 70)
        ad = ImageDraw.Draw(accent)
        ad.line((418, 483, 418, 462, 441, 462), fill=255, width=6, joint="curve")
        ad.line((531, 587, 531, 608, 508, 608), fill=255, width=6, joint="curve")
    return alpha, accent


def structural_sheet():
    labels = (
        "C1  COUNTER", "C2  SPLIT", "C3  WORDMARK", "C4  BADGE",
        "C5  FORUM", "C6  CENTER", "C7  NODE", "C8  FRAME",
    )
    cell = 640
    sheet = Image.new("RGB", (cell*4, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(26)
    for i, label in enumerate(labels):
        alpha, accent = structural_variant(i+1)
        tile = render_signature(alpha, accent).resize((520, 520), Image.Resampling.LANCZOS)
        x = (i % 4)*cell + 60
        y = (i // 4)*cell + 45
        sheet.paste(tile, (x, y), tile)
        draw.text((x+260, y+545), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-structural-concepts-v34.png"
    sheet.save(out, optimize=True)
    return out


def wordmark_balance_variant(kind):
    alpha = icon.vector_outer_mask()
    settings = {
        1: (540, 530, 104, 82, 647, 738, 50),
        2: (540, 540, 104, 82, 647, 744, 50),
        3: (530, 535, 104, 82, 647, 738, 50),
        4: (540, 528, 114, 88, 647, 742, 48),
        5: (540, 520, 96, 76, 647, 738, 52),
        6: (540, 535, 108, 84, 647, 748, 47),
    }
    cx, cy, w, h, tx, ty, size = settings[kind]
    alpha = _terminal_bot(alpha, cx, cy, w, h, tail=True)
    word = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(word)
    f = face(size)
    # Centre the word by its actual visible bounds inside the x=548..746 foot.
    box = d.textbbox((0, 0), "PDA", font=f, anchor="lt")
    x = tx - (box[0] + box[2]) / 2
    y = ty - (box[1] + box[3]) / 2
    d.text((x, y), "PDA", font=f, fill=255, anchor="lt")
    return subtract(alpha, word)


def wordmark_balance_sheet():
    labels = (
        "W1  BALANCED", "W2  LOWER", "W3  OPTICAL LEFT",
        "W4  LARGE BOT", "W5  AIRY", "W6  LOW SIGNATURE",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(28)
    for i, label in enumerate(labels):
        tile = render(wordmark_balance_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-centered-bot-wordmark-options-v35.png"
    sheet.save(out, optimize=True)
    return out


def _directional_bot(alpha, cx, cy, w=104, h=82, direction="left", tail_side="right"):
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    left, top, right, bottom = cx-w//2, cy-h//2, cx+w//2, cy+h//2
    if direction == "left":
        d.ellipse((left, top, cx+18, bottom), fill=255)
        d.rounded_rectangle((cx-7, top, right, bottom), radius=9, fill=255)
    else:
        d.rounded_rectangle((left, top, cx+7, bottom), radius=9, fill=255)
        d.ellipse((cx-18, top, right, bottom), fill=255)
    d.line((left+22, top+4, left+12, top-14), fill=255, width=6)
    d.line((right-22, top+4, right-12, top-14), fill=255, width=6)
    if tail_side == "right":
        d.polygon(((right-28, bottom-3), (right-6, bottom+15), (right-14, bottom-3)), fill=255)
    elif tail_side == "left":
        d.polygon(((left+14, bottom-3), (left+5, bottom+15), (left+28, bottom-2)), fill=255)
    alpha = subtract(alpha, cut)

    ink = ImageDraw.Draw(alpha)
    sy = cy-7
    if direction == "left":
        x = cx+14
        ink.line(((x, sy-7), (x-9, sy), (x, sy+7)), fill=255, width=5, joint="curve")
        ink.rounded_rectangle((cx-25, cy+7, cx-4, cy+12), radius=2, fill=255)
    else:
        x = cx-20
        ink.line(((x, sy-7), (x+9, sy), (x, sy+7)), fill=255, width=5, joint="curve")
        ink.rounded_rectangle((cx+4, cy+7, cx+25, cy+12), radius=2, fill=255)
    return alpha


def directional_wordmark_variant(kind):
    alpha = icon.vector_outer_mask()
    settings = {
        1: (540, 530, 104, 82, "left", "right", 738),
        2: (540, 540, 104, 82, "left", "right", 744),
        3: (535, 530, 104, 82, "left", "left", 738),
        4: (540, 528, 114, 88, "left", "right", 742),
        5: (540, 525, 100, 78, "left", None, 738),
        6: (540, 530, 104, 82, "right", "left", 738),
    }
    cx, cy, w, h, direction, tail, word_y = settings[kind]
    alpha = _directional_bot(alpha, cx, cy, w, h, direction, tail)
    word = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(word)
    f = face(50)
    box = d.textbbox((0, 0), "PDA", font=f, anchor="lt")
    x = 647 - (box[0] + box[2]) / 2
    y = word_y - (box[1] + box[3]) / 2
    d.text((x, y), "PDA", font=f, fill=255, anchor="lt")
    return subtract(alpha, word)


def directional_wordmark_sheet():
    labels = (
        "D1  LOOK LEFT", "D2  LEFT LOWER", "D3  TAIL LEFT",
        "D4  LEFT LARGE", "D5  NO TAIL", "D6  LOOK RIGHT",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(28)
    for i, label in enumerate(labels):
        tile = render(directional_wordmark_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-bot-direction-options-v36.png"
    sheet.save(out, optimize=True)
    return out


def interesting_robot_variant(kind):
    alpha = icon.vector_outer_mask()
    cx, cy = 540, 530
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)

    if kind == 1:  # self-similar mini four
        d.polygon(((cx-48, cy+25), (cx-8, cy-31), (cx+37, cy-31),
                   (cx+37, cy+34), (cx+10, cy+34), (cx+10, cy+11),
                   (cx-48, cy+11)), fill=255)
        d.line((cx-3, cy-28, cx-10, cy-42), fill=255, width=5)
        d.line((cx+25, cy-28, cx+32, cy-42), fill=255, width=5)
    elif kind == 2:  # classic Android dome
        d.pieslice((cx-48, cy-43, cx+48, cy+45), 180, 360, fill=255)
        d.rounded_rectangle((cx-48, cy, cx+48, cy+35), radius=7, fill=255)
        d.line((cx-25, cy-30, cx-35, cy-47), fill=255, width=6)
        d.line((cx+25, cy-30, cx+35, cy-47), fill=255, width=6)
    elif kind == 3:  # D-shaped terminal robot
        d.rounded_rectangle((cx-48, cy-37, cx, cy+37), radius=8, fill=255)
        d.ellipse((cx-22, cy-37, cx+50, cy+37), fill=255)
        d.line((cx-28, cy-34, cx-37, cy-49), fill=255, width=6)
        d.line((cx+30, cy-34, cx+39, cy-49), fill=255, width=6)
    elif kind == 4:  # forum speech robot
        d.rounded_rectangle((cx-48, cy-34, cx+48, cy+34), radius=21, fill=255)
        d.polygon(((cx-28, cy+30), (cx-42, cy+52), (cx-5, cy+33)), fill=255)
        d.line((cx-26, cy-31, cx-34, cy-46), fill=255, width=5)
        d.line((cx+26, cy-31, cx+34, cy-46), fill=255, width=5)
    elif kind == 5:  # hexagonal tech bot
        d.polygon(((cx-37, cy-36), (cx+25, cy-36), (cx+48, cy-12),
                   (cx+48, cy+23), (cx+25, cy+38), (cx-37, cy+38),
                   (cx-50, cy+10), (cx-50, cy-12)), fill=255)
        d.line((cx-24, cy-34, cx-32, cy-49), fill=255, width=5)
        d.line((cx+22, cy-34, cx+30, cy-49), fill=255, width=5)
    elif kind == 6:  # pixel robot
        d.rounded_rectangle((cx-46, cy-37, cx+46, cy+37), radius=7, fill=255)
        d.line((cx-24, cy-35, cx-33, cy-49), fill=255, width=6)
        d.line((cx+24, cy-35, cx+33, cy-49), fill=255, width=6)
    elif kind == 7:  # app-grid bot
        d.rounded_rectangle((cx-45, cy-37, cx+45, cy+37), radius=18, fill=255)
        d.line((cx-23, cy-34, cx-31, cy-48), fill=255, width=5)
        d.line((cx+23, cy-34, cx+31, cy-48), fill=255, width=5)
    else:  # circular community bot
        d.ellipse((cx-43, cy-43, cx+43, cy+43), fill=255)
        d.line((cx-20, cy-38, cx-28, cy-51), fill=255, width=5)
        d.line((cx+20, cy-38, cx+28, cy-51), fill=255, width=5)

    alpha = subtract(alpha, cut)
    ink = ImageDraw.Draw(alpha)
    if kind in (4, 8):
        for x in (cx-18, cx, cx+18):
            ink.ellipse((x-5, cy-5, x+5, cy+5), fill=255)
    elif kind == 7:
        for x in (cx-20, cx+8):
            for y in (cy-18, cy+10):
                ink.rounded_rectangle((x, y, x+13, y+13), radius=3, fill=255)
    elif kind in (2, 5, 6):
        for x in (cx-19, cx+19):
            ink.ellipse((x-5, cy-8-5, x+5, cy-8+5), fill=255)
        ink.rounded_rectangle((cx-18, cy+14, cx+18, cy+21), radius=3, fill=255)
    else:
        # terminal face, oriented along the outer four
        ink.line(((cx-22, cy-12), (cx-12, cy-3), (cx-22, cy+6)), fill=255, width=5, joint="curve")
        ink.rounded_rectangle((cx+2, cy+8, cx+25, cy+13), radius=2, fill=255)

    word = Image.new("L", alpha.size, 0)
    wd = ImageDraw.Draw(word)
    f = face(50)
    box = wd.textbbox((0, 0), "PDA", font=f, anchor="lt")
    wd.text((647-(box[0]+box[2])/2, 738-(box[1]+box[3])/2),
            "PDA", font=f, fill=255, anchor="lt")
    return subtract(alpha, word)


def interesting_robot_sheet():
    labels = (
        "B1  FOUR BOT", "B2  DROID", "B3  D-BOT", "B4  FORUM BOT",
        "B5  HEX BOT", "B6  PIXEL", "B7  APP BOT", "B8  COMMUNITY",
    )
    cell = 640
    sheet = Image.new("RGB", (cell*4, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(26)
    for i, label in enumerate(labels):
        tile = render(interesting_robot_variant(i+1)).resize((520, 520), Image.Resampling.LANCZOS)
        x = (i % 4)*cell + 60
        y = (i // 4)*cell + 45
        sheet.paste(tile, (x, y), tile)
        draw.text((x+260, y+545), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-android-robot-concepts-v37.png"
    sheet.save(out, optimize=True)
    return out


def semantic_symbol_variant(kind):
    alpha = icon.vector_outer_mask()
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    cx, cy = 540, 525

    if kind == 1:  # discussion thread
        d.rounded_rectangle((482, 484, 548, 538), radius=17, outline=255, width=8)
        d.line(((497, 535), (486, 552), (510, 539)), fill=255, width=8, joint="curve")
        d.rounded_rectangle((526, 515, 596, 570), radius=17, outline=255, width=8)
        d.line(((580, 566), (591, 582), (567, 570)), fill=255, width=8, joint="curve")
    elif kind == 2:  # chip + forum
        d.rounded_rectangle((493, 482, 587, 568), radius=16, outline=255, width=8)
        for x in (510, 532, 554, 576):
            d.line((x, 472, x, 486), fill=255, width=6)
            d.line((x, 564, x, 578), fill=255, width=6)
        for y in (500, 525, 550):
            d.line((483, y, 497, y), fill=255, width=6)
            d.line((583, y, 597, y), fill=255, width=6)
        for x in (518, 540, 562):
            d.ellipse((x-5, 525-5, x+5, 525+5), fill=255)
    elif kind == 3:  # internet globe
        d.ellipse((488, 473, 592, 577), outline=255, width=8)
        d.arc((514, 473, 566, 577), 90, 270, fill=255, width=7)
        d.arc((514, 473, 566, 577), 270, 90, fill=255, width=7)
        d.line((492, 525, 588, 525), fill=255, width=7)
    elif kind == 4:  # community nodes
        d.ellipse((526, 480, 554, 508), fill=255)
        d.ellipse((481, 518, 509, 546), fill=255)
        d.ellipse((571, 518, 599, 546), fill=255)
        d.line((540, 503, 496, 532, 584, 532, 540, 503), fill=255, width=7, joint="curve")
    elif kind == 5:  # phone + chat
        d.rounded_rectangle((503, 469, 577, 581), radius=18, outline=255, width=8)
        d.rounded_rectangle((515, 493, 568, 537), radius=14, outline=255, width=7)
        d.line(((528, 534), (518, 549), (540, 537)), fill=255, width=7, joint="curve")
        d.rounded_rectangle((530, 561, 550, 567), radius=3, fill=255)
    elif kind == 6:  # app catalogue
        for x in (500, 546):
            for y in (485, 531):
                d.rounded_rectangle((x, y, x+34, y+34), radius=8, outline=255, width=8)
    elif kind == 7:  # RSS
        d.ellipse((491, 558, 507, 574), fill=255)
        d.arc((475, 510, 555, 590), 270, 360, fill=255, width=9)
        d.arc((458, 478, 587, 607), 270, 360, fill=255, width=9)
    elif kind == 8:  # linked community
        d.rounded_rectangle((478, 495, 548, 548), radius=25, outline=255, width=9)
        d.rounded_rectangle((532, 502, 602, 555), radius=25, outline=255, width=9)
        d.line((526, 525, 554, 525), fill=255, width=8)
    elif kind == 9:  # wifi
        d.arc((480, 475, 600, 585), 210, 330, fill=255, width=10)
        d.arc((501, 498, 579, 572), 210, 330, fill=255, width=10)
        d.arc((521, 520, 559, 558), 210, 330, fill=255, width=9)
        d.ellipse((534, 554, 546, 566), fill=255)
    elif kind == 10:  # code conversation
        d.line(((514, 488), (485, 525), (514, 562)), fill=255, width=9, joint="curve")
        d.line(((566, 488), (595, 525), (566, 562)), fill=255, width=9, joint="curve")
        for x in (526, 540, 554):
            d.ellipse((x-4, 525-4, x+4, 525+4), fill=255)
    elif kind == 11:  # forum topic card
        d.rounded_rectangle((482, 478, 598, 574), radius=18, outline=255, width=8)
        d.ellipse((499, 498, 517, 516), fill=255)
        d.rounded_rectangle((528, 499, 578, 507), radius=3, fill=255)
        for y in (529, 550):
            d.rounded_rectangle((500, y, 579, y+7), radius=3, fill=255)
    else:  # layered topics
        d.rounded_rectangle((500, 473, 593, 536), radius=16, outline=255, width=7)
        d.rounded_rectangle((486, 494, 579, 557), radius=16, outline=255, width=7)
        d.rounded_rectangle((472, 515, 565, 578), radius=16, outline=255, width=7)

    alpha = subtract(alpha, cut)
    word = Image.new("L", alpha.size, 0)
    wd = ImageDraw.Draw(word)
    f = face(50)
    box = wd.textbbox((0, 0), "PDA", font=f, anchor="lt")
    wd.text((647-(box[0]+box[2])/2, 738-(box[1]+box[3])/2),
            "PDA", font=f, fill=255, anchor="lt")
    return subtract(alpha, word)


def semantic_symbol_sheet():
    labels = (
        "M1  THREAD", "M2  CHIP CHAT", "M3  INTERNET", "M4  COMMUNITY",
        "M5  PHONE CHAT", "M6  APPS", "M7  RSS", "M8  LINK",
        "M9  WI-FI", "M10  CODE TALK", "M11  TOPIC", "M12  LAYERS",
    )
    cell = 580
    sheet = Image.new("RGB", (cell*4, cell*3), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(24)
    for i, label in enumerate(labels):
        tile = render(semantic_symbol_variant(i+1)).resize((470, 470), Image.Resampling.LANCZOS)
        x = (i % 4)*cell + 55
        y = (i // 4)*cell + 35
        sheet.paste(tile, (x, y), tile)
        draw.text((x+235, y+492), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-semantic-symbol-options-v38.png"
    sheet.save(out, optimize=True)
    return out


def tech_forum_core_variant(kind):
    alpha = icon.vector_outer_mask()
    accent = Image.new("L", alpha.size, 0)
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    cx, cy = 535, 520

    if kind in (1, 3, 4, 5):
        # A proprietary solid speech-chip silhouette.
        d.rounded_rectangle((472, 478, 598, 565), radius=24, fill=255)
        d.polygon(((493, 560), (477, 590), (518, 564)), fill=255)
        # Four restrained contacts: enough to read as hardware, not decoration.
        for x in (500, 570):
            d.line((x, 466, x, 481), fill=255, width=7)
        d.line((460, 506, 475, 506), fill=255, width=7)
        d.line((595, 536, 610, 536), fill=255, width=7)
    elif kind == 2:
        d.rounded_rectangle((472, 478, 598, 565), radius=24, outline=255, width=9)
        d.line(((495, 560), (478, 589), (518, 564)), fill=255, width=9, joint="curve")
        for x in (500, 570):
            d.line((x, 466, x, 481), fill=255, width=7)
        d.line((460, 506, 475, 506), fill=255, width=7)
        d.line((595, 536, 610, 536), fill=255, width=7)
    else:  # hexagonal forum processor
        d.polygon(((486, 475), (580, 475), (607, 503), (607, 548),
                   (580, 576), (510, 576), (479, 594), (490, 565),
                   (463, 542), (463, 504)), fill=255)
        for x in (515, 565):
            d.line((x, 463, x, 478), fill=255, width=7)

    alpha = subtract(alpha, cut)
    ink = ImageDraw.Draw(alpha)
    if kind in (1, 2, 6):  # forum core
        for x in (507, 535, 563):
            ink.ellipse((x-6, 522-6, x+6, 522+6), fill=255)
    elif kind == 3:  # terminal conversation
        ink.line(((503, 506), (518, 520), (503, 534)), fill=255, width=7, joint="curve")
        ink.rounded_rectangle((537, 532, 568, 539), radius=3, fill=255)
    elif kind == 4:  # apps inside the discussion core
        for x in (510, 546):
            for y in (500, 536):
                ink.rounded_rectangle((x, y, x+22, y+22), radius=5, fill=255)
    else:  # circuit dialogue: three endpoints connected by one trace
        ink.line((500, 534, 520, 534, 520, 510, 548, 510, 548, 538, 572, 538),
                 fill=255, width=6, joint="curve")
        for x, y in ((500, 534), (548, 510), (572, 538)):
            ink.ellipse((x-6, y-6, x+6, y+6), fill=255)

    if kind == 5:
        ImageDraw.Draw(accent).ellipse((600, 474, 614, 488), fill=255)
    elif kind == 6:
        ImageDraw.Draw(accent).rounded_rectangle((455, 467, 615, 590), radius=30,
                                                  outline=255, width=5)

    word = Image.new("L", alpha.size, 0)
    wd = ImageDraw.Draw(word)
    f = face(50)
    box = wd.textbbox((0, 0), "PDA", font=f, anchor="lt")
    wd.text((647-(box[0]+box[2])/2, 738-(box[1]+box[3])/2),
            "PDA", font=f, fill=255, anchor="lt")
    return subtract(alpha, word), accent


def tech_forum_core_sheet():
    labels = (
        "Q1  FORUM CORE", "Q2  LINE CORE", "Q3  TERMINAL CORE",
        "Q4  APP CORE", "Q5  CIRCUIT CORE", "Q6  GOLD CORE",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(28)
    for i, label in enumerate(labels):
        alpha, accent = tech_forum_core_variant(i+1)
        tile = render_signature(alpha, accent)
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-tech-forum-core-options-v39.png"
    sheet.save(out, optimize=True)
    return out


def pacman_pda_variant(kind):
    alpha = clean_base()
    alpha = subtract(alpha, p_and_open_a_cut())
    settings = {
        1: (647, 535, 84, "right", True),
        2: (647, 535, 84, "left", True),
        3: (647, 535, 92, "right", True),
        4: (647, 535, 74, "right", True),
        5: (647, 535, 84, "right", False),
        6: (647, 535, 86, "left", False),
    }
    cx, cy, size, direction, eye = settings[kind]
    r = size // 2
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    d.ellipse((cx-r, cy-r, cx+r, cy+r), fill=255)

    # Carve the mouth back into the dark body. The 70-degree sector keeps
    # enough of the circle to remain readable as the missing letter D.
    if direction == "right":
        d.polygon(((cx, cy), (cx+r+3, cy-r+10), (cx+r+3, cy+r-10)), fill=0)
        eye_x = cx + 4
    else:
        d.polygon(((cx, cy), (cx-r-3, cy-r+10), (cx-r-3, cy+r-10)), fill=0)
        eye_x = cx - 4
    if eye:
        d.ellipse((eye_x-5, cy-r//2-5, eye_x+5, cy-r//2+5), fill=0)

    alpha = subtract(alpha, cut)
    # P4: a restrained three-dot trail, linking the Pacman metaphor to forum.
    if kind == 4:
        ad = ImageDraw.Draw(alpha)
        for x in (cx+50, cx+67, cx+84):
            ad.ellipse((x-4, cy-4, x+4, cy+4), fill=0)
    return alpha


def pacman_pda_sheet():
    labels = (
        "P1  RIGHT", "P2  LEFT", "P3  LARGE",
        "P4  COMPACT + DOTS", "P5  NO EYE", "P6  LEFT MINIMAL",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(28)
    for i, label in enumerate(labels):
        tile = render(pacman_pda_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-pacman-pda-options-v43.png"
    sheet.save(out, optimize=True)
    return out


def mirrored_four_pacman_variant(kind):
    alpha = ImageOps.mirror(icon.vector_outer_mask())
    alpha = subtract(alpha, aligned_pa_cut(433))
    prompt_direction = "left" if kind in (2, 4, 6) else "right"
    alpha = subtract(alpha, terminal_prompt_cut(606, 606, prompt_direction))

    settings = {
        1: (433, 535, 84, "left", True),
        2: (433, 535, 84, "right", True),
        3: (433, 535, 92, "left", True),
        4: (433, 535, 74, "left", True),
        5: (433, 535, 84, "left", False),
        6: (433, 535, 86, "right", False),
    }
    cx, cy, size, mouth, eye = settings[kind]
    r = size // 2
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)
    d.ellipse((cx-r, cy-r, cx+r, cy+r), fill=255)
    if mouth == "left":
        d.polygon(((cx, cy), (cx-r-3, cy-r+10), (cx-r-3, cy+r-10)), fill=0)
        eye_x = cx-4
    else:
        d.polygon(((cx, cy), (cx+r+3, cy-r+10), (cx+r+3, cy+r-10)), fill=0)
        eye_x = cx+4
    if eye:
        d.ellipse((eye_x-5, cy-r//2-5, eye_x+5, cy-r//2+5), fill=0)
    alpha = subtract(alpha, cut)

    if kind == 4:
        ad = ImageDraw.Draw(alpha)
        for x in (cx-50, cx-67, cx-84):
            ad.ellipse((x-4, cy-4, x+4, cy+4), fill=0)
    return alpha


def mirrored_four_pacman_sheet():
    labels = (
        "R1  OUTWARD", "R2  INWARD", "R3  LARGE",
        "R4  DOT TRAIL", "R5  NO EYE", "R6  MINIMAL",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(28)
    for i, label in enumerate(labels):
        tile = render(mirrored_four_pacman_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-mirrored-four-pacman-options-v44.png"
    sheet.save(out, optimize=True)
    return out


def p_dotted_a_cut(center_x=433, p_size=103):
    cut = Image.new("L", (1080, 1080), 0)
    # P keeps the same normalized 70 px width.
    pa = aligned_pa_cut(center_x, p_size)
    cut.paste(pa.crop((0, 320, 1080, 445)), (0, 320), pa.crop((0, 320, 1080, 445)))
    d = ImageDraw.Draw(cut)
    cx, cy = center_x, 690
    width, height, stroke = 70, 78, 16
    left = (cx-width//2, cy+height//2)
    apex = (cx, cy-height//2)
    right = (cx+width//2, cy+height//2)
    d.line((left, apex, right), fill=255, width=stroke, joint="curve")
    r = stroke//2
    for x, y in (left, right):
        d.ellipse((x-r, y-r, x+r, y+r), fill=255)
    # The conventional crossbar is replaced with a single centred point.
    d.ellipse((cx-7, cy+7-7, cx+7, cy+7+7), fill=255)
    return cut


def pac_d_variant(kind):
    alpha = ImageOps.mirror(icon.vector_outer_mask())
    alpha = subtract(alpha, p_dotted_a_cut(433))
    alpha = subtract(alpha, terminal_prompt_cut(606, 606, "left"))
    cx, cy, r = 433, 535, 42
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)

    if kind == 4:  # outline D/Pac hybrid
        d.line((cx-r+8, cy-r+7, cx-r+8, cy+r-7), fill=255, width=10)
        d.arc((cx-r+3, cy-r, cx+r, cy+r), -90, 90, fill=255, width=10)
        d.arc((cx-r+3, cy-r, cx+r, cy+r), 90, 270, fill=255, width=10)
    else:
        d.ellipse((cx-r, cy-r, cx+r, cy+r), fill=255)
        if kind in (1, 3):  # flatten the back like a typographic D
            d.rectangle((cx-r-2, cy-r, cx-r+10, cy+r), fill=0)
            d.rectangle((cx-r+8, cy-r+6, cx-r+19, cy+r-6), fill=255)
        elif kind == 2:  # explicit short vertical D spine
            d.rounded_rectangle((cx-r-8, cy-r+3, cx-r+12, cy+r-3), radius=5, fill=255)

        mouth_half = 21 if kind in (3, 5) else 31
        d.polygon(((cx+2, cy), (cx+r+4, cy-mouth_half), (cx+r+4, cy+mouth_half)), fill=0)

    if kind != 6:
        d.ellipse((cx-2, cy-r//2-5, cx+8, cy-r//2+5), fill=0)
    alpha = subtract(alpha, cut)

    # One restrained discussion dot sits in the mouth on P5.
    if kind == 5:
        ad = ImageDraw.Draw(alpha)
        ad.ellipse((cx+51-5, cy-5, cx+51+5, cy+5), fill=0)
    return alpha


def pac_d_sheet():
    labels = (
        "D1  FLAT BACK", "D2  D-SPINE", "D3  NARROW MOUTH",
        "D4  OUTLINE", "D5  FORUM DOT", "D6  MINIMAL",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(28)
    for i, label in enumerate(labels):
        tile = render(pac_d_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-pac-d-dotted-a-options-v45.png"
    sheet.save(out, optimize=True)
    return out


def droid_d_variant(kind):
    alpha = ImageOps.mirror(icon.vector_outer_mask())
    alpha = subtract(alpha, p_dotted_a_cut(433))
    alpha = subtract(alpha, terminal_prompt_cut(606, 606, "left"))
    cx, cy = 433, 535
    cut = Image.new("L", alpha.size, 0)
    d = ImageDraw.Draw(cut)

    if kind == 1:  # classic compact Android package
        d.pieslice((cx-40, cy-48, cx+40, cy+28), 180, 360, fill=255)
        d.rounded_rectangle((cx-40, cy-8, cx+40, cy+42), radius=7, fill=255)
        d.rectangle((cx-40, cy+1, cx+40, cy+10), fill=0)
    elif kind == 2:  # explicit D spine + Android dome
        d.rounded_rectangle((cx-46, cy-39, cx-28, cy+40), radius=5, fill=255)
        d.pieslice((cx-42, cy-45, cx+44, cy+35), 180, 360, fill=255)
        d.rounded_rectangle((cx-42, cy-4, cx+44, cy+40), radius=7, fill=255)
        d.rectangle((cx-27, cy+1, cx+44, cy+10), fill=0)
    elif kind == 3:  # D-shaped single body
        d.rounded_rectangle((cx-45, cy-40, cx-18, cy+40), radius=6, fill=255)
        d.ellipse((cx-35, cy-40, cx+45, cy+40), fill=255)
        d.rectangle((cx-27, cy+3, cx+45, cy+11), fill=0)
    elif kind == 4:  # outline Droid-D
        d.line((cx-40, cy-34, cx-40, cy+37), fill=255, width=9)
        d.arc((cx-40, cy-41, cx+43, cy+39), -90, 90, fill=255, width=9)
        d.line((cx+2, cy+39, cx-40, cy+39), fill=255, width=9)
        d.line((cx-33, cy+2, cx+35, cy+2), fill=255, width=7)
    elif kind == 5:  # Android/forum hybrid
        d.pieslice((cx-42, cy-47, cx+42, cy+30), 180, 360, fill=255)
        d.rounded_rectangle((cx-42, cy-6, cx+42, cy+41), radius=8, fill=255)
        d.rectangle((cx-42, cy+1, cx+42, cy+10), fill=0)
    else:  # minimal dome and baseline
        d.pieslice((cx-42, cy-43, cx+42, cy+35), 180, 360, fill=255)
        d.rounded_rectangle((cx-42, cy+15, cx+42, cy+27), radius=5, fill=255)

    if kind != 4:
        d.line((cx-23, cy-31, cx-34, cy-49), fill=255, width=6)
        d.line((cx+23, cy-31, cx+34, cy-49), fill=255, width=6)

    alpha = subtract(alpha, cut)
    ink = ImageDraw.Draw(alpha)
    if kind == 5:
        for x in (cx-19, cx, cx+19):
            ink.ellipse((x-4, cy-15-4, x+4, cy-15+4), fill=255)
    elif kind != 4:
        for x in (cx-19, cx+19):
            ink.rounded_rectangle((x-5, cy-17-8, x+5, cy-17+8), radius=4, fill=255)
    return alpha


def droid_d_sheet():
    labels = (
        "A1  CLASSIC", "A2  D-SPINE", "A3  D-BODY",
        "A4  OUTLINE", "A5  FORUM DROID", "A6  MINIMAL",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(28)
    for i, label in enumerate(labels):
        tile = render(droid_d_variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-droid-d-options-v46.png"
    sheet.save(out, optimize=True)
    return out


def main():
    labels = (
        "01  UNIFIED", "02  COMPACT", "03  SOFT",
        "04  OUTLINE", "05  LIGATURE", "06  FORUM D",
    )
    cell = 760
    sheet = Image.new("RGB", (cell*3, cell*2), (241, 242, 245))
    draw = ImageDraw.Draw(sheet)
    f = face(30)
    for i, label in enumerate(labels):
        tile = render(variant(i+1))
        x = (i % 3)*cell + 75
        y = (i // 3)*cell + 65
        sheet.paste(tile, (x, y), tile)
        draw.text((x+305, y+635), label, font=f, fill=(35, 37, 42), anchor="ma")
    out = HERE / "propda-pda-letter-options-v26.png"
    sheet.save(out, optimize=True)
    print(out)
    print(forum_sheet())
    print(android_d_sheet())
    print(apps_d_sheet())
    print(robot_face_sheet())
    print(robot_left_sheet())
    print(terminal_bot_left_sheet())
    print(signature_sheet())
    print(structural_sheet())
    print(wordmark_balance_sheet())
    print(directional_wordmark_sheet())
    print(interesting_robot_sheet())
    print(semantic_symbol_sheet())
    print(tech_forum_core_sheet())
    print(pacman_pda_sheet())
    print(mirrored_four_pacman_sheet())
    print(pac_d_sheet())
    print(droid_d_sheet())


if __name__ == "__main__":
    main()
