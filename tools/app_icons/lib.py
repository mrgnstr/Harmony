"""Tiny scene model for launcher icons that writes both SVG (previews, PNGs) and Android VectorDrawable XML.

Only what both formats draw the same way is allowed: paths with solid/linear/radial fills and strokes,
alpha, groups with a rotate/scale/translate, clip paths. No blur, no masks, no sweep gradients.
All coordinates are in the 108x108 adaptive-icon viewport (visible part 18..90, safe zone r=33 around 54,54).
"""
import math

C = 54.0  # centre of the 108 viewport


def n(x):
    s = f"{x:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def pt(p):
    return f"{n(p[0])},{n(p[1])}"


# ---- path builders (strings valid as both SVG `d` and VectorDrawable `pathData`) ----

def circle(cx, cy, r):
    return (f"M{n(cx - r)},{n(cy)}A{n(r)},{n(r)} 0 1,1 {n(cx + r)},{n(cy)}"
            f"A{n(r)},{n(r)} 0 1,1 {n(cx - r)},{n(cy)}Z")


def ellipse(cx, cy, rx, ry, rot=0):
    k = 0.5522847498
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    raw = [(rx, 0), (rx, k * ry), (k * rx, ry), (0, ry), (-k * rx, ry), (-rx, k * ry), (-rx, 0),
           (-rx, -k * ry), (-k * rx, -ry), (0, -ry), (k * rx, -ry), (rx, -k * ry), (rx, 0)]
    p = [(cx + x * c - y * s, cy + x * s + y * c) for x, y in raw]
    d = f"M{pt(p[0])}"
    for i in (1, 4, 7, 10):
        d += f"C{pt(p[i])} {pt(p[i + 1])} {pt(p[i + 2])}"
    return d + "Z"


def rrect(x, y, w, h, r=None):
    r = min(w, h) / 2 if r is None else min(r, w / 2, h / 2)
    if r <= 0:
        return f"M{n(x)},{n(y)}h{n(w)}v{n(h)}h{n(-w)}Z"
    a = f"a{n(r)},{n(r)} 0 0,1"
    return (f"M{n(x + r)},{n(y)}h{n(w - 2 * r)}{a} {n(r)},{n(r)}v{n(h - 2 * r)}{a} {n(-r)},{n(r)}"
            f"h{n(-(w - 2 * r))}{a} {n(-r)},{n(-r)}v{n(-(h - 2 * r))}{a} {n(r)},{n(-r)}Z")


def poly(pts, close=True):
    return "M" + "L".join(pt(p) for p in pts) + ("Z" if close else "")


def rpoly(pts, r):
    """Closed polygon with corners rounded by r (quadratic through the corner)."""
    m = len(pts)
    d = ""
    for i in range(m):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % m]

        def toward(a, b):
            L = math.hypot(b[0] - a[0], b[1] - a[1])
            t = min(r, L / 2) / L
            return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

        a, b = toward(p1, p0), toward(p1, p2)
        d += ("M" if i == 0 else "L") + pt(a) + f"Q{pt(p1)} {pt(b)}"
    return d + "Z"


def smooth(pts, closed=False):
    """Catmull-Rom through pts, as cubic beziers."""
    m = len(pts)
    d = f"M{pt(pts[0])}"
    segs = m if closed else m - 1
    for i in range(segs):
        if closed:
            p0, p1, p2, p3 = pts[(i - 1) % m], pts[i], pts[(i + 1) % m], pts[(i + 2) % m]
        else:
            p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, m - 1)]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += f"C{pt(c1)} {pt(c2)} {pt(p2)}"
    return d + ("Z" if closed else "")


def polar(cx, cy, r, deg):
    a = math.radians(deg)
    return (cx + r * math.cos(a), cy + r * math.sin(a))


def arc(cx, cy, r, a0, a1):
    """Open arc for stroking. Degrees, 0 = right, positive = clockwise on screen."""
    p0, p1 = polar(cx, cy, r, a0), polar(cx, cy, r, a1)
    large = 1 if abs(a1 - a0) > 180 else 0
    sweep = 1 if a1 > a0 else 0
    return f"M{pt(p0)}A{n(r)},{n(r)} 0 {large},{sweep} {pt(p1)}"


def wedge(cx, cy, r0, r1, a0, a1):
    """Closed annular sector between radii r0<r1 and angles a0<a1."""
    large = 1 if abs(a1 - a0) > 180 else 0
    o0, o1 = polar(cx, cy, r1, a0), polar(cx, cy, r1, a1)
    i0, i1 = polar(cx, cy, r0, a0), polar(cx, cy, r0, a1)
    return (f"M{pt(o0)}A{n(r1)},{n(r1)} 0 {large},1 {pt(o1)}L{pt(i1)}"
            f"A{n(r0)},{n(r0)} 0 {large},0 {pt(i0)}Z")


def scallop(cx, cy, r, amp, lobes, rot=0, per=8):
    pts = []
    for i in range(lobes * per):
        a = 2 * math.pi * i / (lobes * per) + math.radians(rot)
        rr = r + amp * math.cos(lobes * (a - math.radians(rot)))
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return smooth(pts, True)


def sine(x0, x1, y, amp, periods, phase=0, per=12):
    m = max(2, int(periods * per))
    pts = [(x0 + (x1 - x0) * i / m, y - amp * math.sin(2 * math.pi * periods * i / m + phase)) for i in range(m + 1)]
    return smooth(pts)


def note_head(cx, cy, s=1.0):
    return ellipse(cx, cy, 7.4 * s, 5.5 * s, -22)


# ---- scene items ----

class Lin:
    def __init__(self, x1, y1, x2, y2, stops):
        self.x1, self.y1, self.x2, self.y2, self.stops = x1, y1, x2, y2, _stops(stops)


class Rad:
    def __init__(self, cx, cy, r, stops):
        self.cx, self.cy, self.r, self.stops = cx, cy, r, _stops(stops)


def _stops(stops):
    out = []
    m = len(stops)
    for i, s in enumerate(stops):
        if isinstance(s, str):
            s = (i / (m - 1), s, 1.0)
        elif len(s) == 2:
            s = (s[0], s[1], 1.0) if isinstance(s[1], str) else (i / (m - 1), s[0], s[1])
        out.append(s)
    return out


class P:
    def __init__(self, d, fill=None, stroke=None, sw=0.0, a=1.0, sa=1.0, cap="round", join="round", eo=False):
        self.d, self.fill, self.stroke, self.sw, self.a, self.sa = d, fill, stroke, sw, a, sa
        self.cap, self.join, self.eo = cap, join, eo


class G:
    def __init__(self, items, rot=0.0, pivot=(C, C), t=(0.0, 0.0), s=1.0, sy=None):
        self.items, self.rot, self.pivot, self.t, self.s = items, rot, pivot, t, s
        self.sy = s if sy is None else sy


class Clip:
    def __init__(self, d, items):
        self.d, self.items = d, items


class Icon:
    def __init__(self, slug, title, style, idea, bg, fg, mono, loose=3):
        self.slug, self.title, self.style, self.idea = slug, title, style, idea
        self.bg, self.fg, self.mono, self.loose = bg, fg, mono, loose


# ---- Monet colour tokens ----
# A colour written "a1.600" (a1/a2/a3/n1/n2 . shade) is a system dynamic colour: in the v31 drawables it becomes
# @android:color/system_accent1_600, everywhere else the hex from PALETTE (the fallback, or a preview wallpaper).

PALETTE = {}
DYNAMIC = False
_SYS = {"a1": "accent1", "a2": "accent2", "a3": "accent3", "n1": "neutral1", "n2": "neutral2"}
_TONE = {0: 100, 10: 99, 50: 95, 100: 90, 200: 80, 300: 70, 400: 60, 500: 50, 600: 40, 700: 30, 800: 20, 900: 10, 1000: 0}


def _lch(L, c, h):
    a, b = c * math.cos(math.radians(h)), c * math.sin(math.radians(h))
    fy = (L + 16) / 116
    xyz = []
    for f, w in ((fy + a / 500, 0.95047), (fy, 1.0), (fy - b / 200, 1.08883)):
        xyz.append(w * (f ** 3 if f > 6 / 29 else 3 * (6 / 29) ** 2 * (f - 4 / 29)))
    x, y, z = xyz
    lin = (3.2406 * x - 1.5372 * y - 0.4986 * z, -0.9689 * x + 1.8758 * y + 0.0415 * z, 0.0557 * x - 0.2040 * y + 1.0570 * z)
    return [12.92 * v if v <= 0.0031308 else 1.055 * v ** (1 / 2.4) - 0.055 for v in lin]


def tone(L, c, h):
    while True:
        rgb = _lch(L, c, h)
        if all(-0.002 <= v <= 1.002 for v in rgb) or c < 0.5:
            return "#" + "".join(f"{int(round(min(1, max(0, v)) * 255)):02X}" for v in rgb)
        c *= 0.96


def monet(hue, chroma=(44, 18, 34, 5, 9), tertiary=60):
    """Approximates the system palettes for a wallpaper hue (CIELAB hue; previews and the pre-31 fallback only)."""
    hues = (hue, hue, hue + tertiary, hue, hue)
    return {f"{k}.{s}": tone(L, c, h) for k, c, h in zip(_SYS, chroma, hues) for s, L in _TONE.items()}


def hue_of(color):
    """CIELAB hue of a "#RRGGBB" colour, for monet()."""
    rgb = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    r, g, b = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in rgb]
    xyz = (0.4124 * r + 0.3576 * g + 0.1805 * b, 0.2126 * r + 0.7152 * g + 0.0722 * b, 0.0193 * r + 0.1192 * g + 0.9505 * b)
    fx, fy, fz = [t ** (1 / 3) if t > (6 / 29) ** 3 else t / (3 * (6 / 29) ** 2) + 4 / 29
                  for t in (xyz[0] / 0.95047, xyz[1], xyz[2] / 1.08883)]
    return math.degrees(math.atan2(200 * (fy - fz), 500 * (fx - fy))) % 360


def is_token(p):
    return isinstance(p, str) and not p.startswith("#")


def _hex(p):
    return PALETTE[p] if is_token(p) else p


def _vd_color(p):
    if is_token(p) and DYNAMIC:
        k, s = p.split(".")
        return f"@android:color/system_{_SYS[k]}_{s}"
    return _hex(p).upper()


# ---- SVG ----

class _Defs:
    def __init__(self, prefix):
        self.prefix, self.out, self.i = prefix, [], 0

    def new(self):
        self.i += 1
        return f"{self.prefix}{self.i}"


def _svg_paint(paint, defs, tint):
    if paint is None:
        return "none"
    if tint:
        return tint
    if isinstance(paint, str):
        return _hex(paint)
    gid = defs.new()
    stops = "".join(
        f'<stop offset="{n(o)}" stop-color="{_hex(c)}"' + (f' stop-opacity="{n(al)}"' if al < 1 else "") + "/>"
        for o, c, al in paint.stops)
    if isinstance(paint, Lin):
        defs.out.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{n(paint.x1)}" '
                        f'y1="{n(paint.y1)}" x2="{n(paint.x2)}" y2="{n(paint.y2)}">{stops}</linearGradient>')
    else:
        defs.out.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{n(paint.cx)}" '
                        f'cy="{n(paint.cy)}" r="{n(paint.r)}">{stops}</radialGradient>')
    return f"url(#{gid})"


def _mean_alpha(paint):
    return 1.0 if isinstance(paint, str) or paint is None else sum(s[2] for s in paint.stops) / len(paint.stops)


def svg_items(items, defs, tint=None):
    out = []
    for it in items:
        if isinstance(it, P):
            s = f'<path d="{it.d}" fill="{_svg_paint(it.fill, defs, tint)}"'
            fa = it.a * (_mean_alpha(it.fill) if tint else 1)
            if it.fill is not None and fa < 1:
                s += f' fill-opacity="{n(fa)}"'
            if it.eo:
                s += ' fill-rule="evenodd"'
            if it.stroke is not None:
                s += (f' stroke="{_svg_paint(it.stroke, defs, tint)}" stroke-width="{n(it.sw)}" '
                      f'stroke-linecap="{it.cap}" stroke-linejoin="{it.join}"')
                if it.sa < 1:
                    s += f' stroke-opacity="{n(it.sa)}"'
            out.append(s + "/>")
        elif isinstance(it, G):
            px, py = it.pivot
            tr = f"translate({n(it.t[0] + px)},{n(it.t[1] + py)}) rotate({n(it.rot)}) scale({n(it.s)},{n(it.sy)}) translate({n(-px)},{n(-py)})"
            out.append(f'<g transform="{tr}">' + svg_items(it.items, defs, tint) + "</g>")
        elif isinstance(it, Clip):
            cid = defs.new()
            defs.out.append(f'<clipPath id="{cid}"><path d="{it.d}"/></clipPath>')
            out.append(f'<g clip-path="url(#{cid})">' + svg_items(it.items, defs, tint) + "</g>")
    return "".join(out)


def svg_doc(body, defs, viewbox="0 0 108 108", size=108):
    d = "".join(defs)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}" width="{size}" height="{size}">'
            f"<defs>{d}</defs>{body}</svg>")


def layer_svg(items, tint=None):
    defs = _Defs("g")
    body = svg_items(items, defs, tint)
    return svg_doc(body, defs.out)


# ---- VectorDrawable ----

def _argb(color, alpha=1.0):
    return f"#{int(round(alpha * 255)):02X}{color[1:].upper()}"


def _vd_gradient(attr, paint, ind):
    s = f'{ind}<aapt:attr name="android:{attr}">\n'
    if isinstance(paint, Lin):
        s += (f'{ind}    <gradient android:type="linear" android:startX="{n(paint.x1)}" android:startY="{n(paint.y1)}" '
              f'android:endX="{n(paint.x2)}" android:endY="{n(paint.y2)}">\n')
    else:
        s += (f'{ind}    <gradient android:type="radial" android:centerX="{n(paint.cx)}" android:centerY="{n(paint.cy)}" '
              f'android:gradientRadius="{n(paint.r)}">\n')
    for o, c, al in paint.stops:
        # A system colour reference cannot carry its own alpha, so a token stop with alpha falls back to the hex.
        col = _vd_color(c) if is_token(c) and DYNAMIC and al >= 1 else _argb(_hex(c), al)
        s += f'{ind}        <item android:offset="{n(o)}" android:color="{col}" />\n'
    return s + f"{ind}    </gradient>\n{ind}</aapt:attr>\n"


def vd_items(items, ind="    "):
    out = ""
    for it in items:
        if isinstance(it, P):
            s = f'{ind}<path android:pathData="{it.d}"'
            inner = ""
            if isinstance(it.fill, str):
                s += f' android:fillColor="{_vd_color(it.fill)}"'
            elif it.fill is not None:
                inner += _vd_gradient("fillColor", it.fill, ind + "    ")
            if it.fill is not None and it.a < 1:
                s += f' android:fillAlpha="{n(it.a)}"'
            if it.eo:
                s += ' android:fillType="evenOdd"'
            if it.stroke is not None:
                if isinstance(it.stroke, str):
                    s += f' android:strokeColor="{_vd_color(it.stroke)}"'
                else:
                    inner += _vd_gradient("strokeColor", it.stroke, ind + "    ")
                s += (f' android:strokeWidth="{n(it.sw)}" android:strokeLineCap="{it.cap}" '
                      f'android:strokeLineJoin="{it.join}"')
                if it.sa < 1:
                    s += f' android:strokeAlpha="{n(it.sa)}"'
            out += s + (f">\n{inner}{ind}</path>\n" if inner else " />\n")
        elif isinstance(it, G):
            s = f"{ind}<group"
            if it.rot:
                s += f' android:rotation="{n(it.rot)}"'
            if it.rot or it.s != 1 or it.sy != 1:
                s += f' android:pivotX="{n(it.pivot[0])}" android:pivotY="{n(it.pivot[1])}"'
            if it.s != 1 or it.sy != 1:
                s += f' android:scaleX="{n(it.s)}" android:scaleY="{n(it.sy)}"'
            if it.t[0]:
                s += f' android:translateX="{n(it.t[0])}"'
            if it.t[1]:
                s += f' android:translateY="{n(it.t[1])}"'
            out += s + ">\n" + vd_items(it.items, ind + "    ") + f"{ind}</group>\n"
        elif isinstance(it, Clip):
            out += (f'{ind}<group>\n{ind}    <clip-path android:pathData="{it.d}" />\n'
                    + vd_items(it.items, ind + "    ") + f"{ind}</group>\n")
    return out


def vd_doc(items):
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            + vd_items(items) + "</vector>\n")


# ---- small shared pieces ----

def full(fill):
    return P("M0,0h108v108h-108Z", fill)


def shadow(d, dy=1.4, a=0.22, dx=0.0, color="#000000"):
    return G([P(d, color, a=a)], t=(dx, dy))


def ground(cx, cy, rx, ry=None, a=0.3):
    """Soft shadow: a radial gradient, squashed to rx by ry."""
    ry = rx * 0.3 if ry is None else ry
    return G([P(circle(cx, cy, rx), Rad(cx, cy, rx, [("#000000", a), (0.55, "#000000", a * 0.5), ("#000000", 0.0)]))],
             pivot=(cx, cy), s=1.0, sy=ry / rx)
