"""Shared pieces of the launcher icon generator (gen_m3.py): compositing a variant in a mask, rendering with resvg,
the legacy PNG shapes, and the preview sheets. Needs resvg and Pillow."""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFilter, ImageFont  # noqa: E402

import lib  # noqa: E402
from lib import _Defs, svg_items, svg_doc, layer_svg, vd_doc  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TMP = os.environ.get("ICON_TMP", "/tmp/harmony-icons")

ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""

# Masks in the 108 viewport (the launcher shows 18..90).
def squircle():
    import math
    pts = []
    for i in range(72):
        a = 2 * math.pi * i / 72
        c, s = math.cos(a), math.sin(a)
        pts.append((54 + 36 * abs(c) ** 0.5 * (1 if c >= 0 else -1), 54 + 36 * abs(s) ** 0.5 * (1 if s >= 0 else -1)))
    return lib.smooth(pts, True)


MASKS = {"circle": lib.circle(54, 54, 36), "squircle": squircle(), "rounded": lib.rrect(18, 18, 72, 72, 17),
         "square": lib.rrect(18, 18, 72, 72, 0)}
THEMED = {"light": ("#D7E3FF", "#2F4A7D"), "dark": ("#2B3445", "#C9DAFF")}


def composite(icon, mask="circle", themed=None, guides=False):
    defs = _Defs("g")
    if themed:
        bgc, fgc = THEMED[themed]
        body = f'<rect width="108" height="108" fill="{bgc}"/>' + svg_items(icon.mono, defs, fgc)
    else:
        body = svg_items(icon.bg, defs) + svg_items(icon.fg, defs)
    defs.out.append(f'<clipPath id="mask"><path d="{MASKS[mask]}"/></clipPath>')
    body = f'<g clip-path="url(#mask)">{body}</g>'
    if guides:
        body += ('<circle cx="54" cy="54" r="33" fill="none" stroke="#00E5FF" stroke-width="0.5"/>'
                 '<circle cx="54" cy="54" r="24" fill="none" stroke="#00E5FF" stroke-width="0.3" stroke-dasharray="1.5 1.5"/>')
    return svg_doc(body, defs.out, "18 18 72 72", 72)


def render(svg, px, name):
    os.makedirs(TMP, exist_ok=True)
    src, dst = os.path.join(TMP, name + ".svg"), os.path.join(TMP, name + ".png")
    with open(src, "w") as f:
        f.write(svg)
    subprocess.run(["resvg", "-w", str(px), "-h", str(px), src, dst], check=True)
    return Image.open(dst).convert("RGBA")


def legacy(icon, round_):
    """48dp legacy icon at 4x (192px): the adaptive artwork in a shape with a small shadow, as Image Asset Studio does."""
    S = 768  # 4x supersampled
    k = S / 48
    inset = (2 if round_ else 5) * k  # Image Asset Studio: 44dp circle, 38dp square
    side = int(S - 2 * inset)
    art = render(composite(icon, "square"), side, f"{icon.slug}-legacy")
    m = Image.new("L", (side, side), 0)
    d = ImageDraw.Draw(m)
    if round_:
        d.ellipse((0, 0, side - 1, side - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, side - 1, side - 1), radius=int(8 * k), fill=255)
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    sh = Image.new("L", (S, S), 0)
    sh.paste(m.point(lambda v: int(v * 0.30)), (int(inset), int(inset + 1.0 * k)))
    sh = sh.filter(ImageFilter.GaussianBlur(1.1 * k))
    canvas.paste((0, 0, 0, 255), (0, 0), sh)
    canvas.paste(art, (int(inset), int(inset)), m)
    return canvas.resize((192, 192), Image.LANCZOS)


def font(size, bold=False):
    for p in ("/usr/share/fonts/TTF/Roboto-%s.ttf" % ("Medium" if bold else "Regular"),
              "/usr/share/fonts/noto/NotoSans-%s.ttf" % ("Bold" if bold else "Regular"),
              "/usr/share/fonts/TTF/DejaVuSans%s.ttf" % ("-Bold" if bold else "")):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


BG = (246, 246, 249, 255)
INK = (28, 28, 34, 255)
SUB = (110, 110, 122, 255)


def overview(icons, numbers, px=232, cols=5, mask="squircle", themed=None, title=None):
    gap, lab = 30, 54
    rows = (len(icons) + cols - 1) // cols
    top = 70 if title else gap
    W, H = cols * (px + gap) + gap, rows * (px + lab + gap) + top
    im = Image.new("RGBA", (W, H), BG if themed != "dark" else (18, 20, 26, 255))
    dr = ImageDraw.Draw(im)
    ink, sub = (INK, SUB) if themed != "dark" else ((236, 238, 244, 255), (150, 155, 168, 255))
    if title:
        dr.text((gap, 22), title, font=font(26, True), fill=ink)
    for k, icon in enumerate(icons):
        x, y = gap + (k % cols) * (px + gap), top + (k // cols) * (px + lab + gap)
        cell = render(composite(icon, mask, themed), px, f"{icon.slug}-o{mask}{themed}")
        if not themed:
            sh = Image.new("RGBA", im.size, (0, 0, 0, 0))
            a = cell.split()[3].point(lambda v: int(v * 0.28))
            sh.paste((0, 0, 0, 255), (x, y + 6), a)
            im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(9)))
        im.alpha_composite(cell, (x, y))
        dr.text((x + 2, y + px + 10), f"{numbers[k]:02d}  {icon.title}", font=font(18, True), fill=ink)
        dr.text((x + 2, y + px + 33), icon.style, font=font(14), fill=sub)
    return im
