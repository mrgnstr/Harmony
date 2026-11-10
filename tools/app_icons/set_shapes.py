"""The app's launcher icon (two notes a third apart, stems going opposite ways) on different Material 3 Expressive
shapes: the variants the user can pick in Settings. Every colour is a Monet token ("a1.600" = system_accent1_600).
The shapes are drawn here in the spirit of MaterialShapes, not copied from the library.
The slug is the resource suffix (ic_launcher_<slug>) and must match AppIcon in core/appicon; "cookie" is the default."""
import math

from lib import *  # noqa: F401,F403

K = "#000000"
INK = "a1.800"
A, B = (55.3, 47.5), (52.7, 60.5)  # upper head (stem up), lower head (stem down)
# The stems run off the shape and are clipped by it.
STEMS = rrect(A[0] + 5.2, 10, 2.7, A[1] - 10, 1.35) + rrect(B[0] - 7.9, B[1], 2.7, 98 - B[1], 1.35)
HEADS = {A: "a1.600", B: "a3.500"}


def head(c):
    return ellipse(c[0], c[1], 7.7, 5.7, -22)


def star(points, outer, inner, r, rot=-90):
    pts = []
    for i in range(points * 2):
        pts.append(polar(54, 54, outer if i % 2 == 0 else inner, rot + 180 * i / points))
    return rpoly(pts, r)


def pill(length, width, rot):
    r, half = width / 2, (length - width) / 2
    pts = [(half + r * math.cos(math.radians(a)), r * math.sin(math.radians(a))) for a in range(-90, 91, 15)]
    pts += [(-x, -y) for x, y in pts]
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    return smooth([(54 + x * c - y * s, 54 + x * s + y * c) for x, y in pts], True)


SHAPES = [
    ("cookie", "Cookie", scallop(54, 54, 29.5, 1.6, 9)),
    ("circle", "Circle", circle(54, 54, 29)),
    ("square", "Square", rrect(28, 28, 52, 52, 14)),
    ("pill", "Pill", pill(62, 41, -45)),
    ("clover", "Clover", scallop(54, 54, 24.5, 5, 4, rot=45)),
    ("flower", "Flower", scallop(54, 54, 26.2, 3.6, 8, rot=22.5)),
    ("sunny", "Sunny", star(8, 31.5, 25, 5)),
    ("burst", "Burst", star(12, 31.5, 26, 1.2)),
    ("pentagon", "Pentagon", rpoly([polar(54, 56, 31, -90 + 72 * i) for i in range(5)], 9)),
    ("arch", "Arch", "M30,52A24,24 0 0,1 78,52V72.5a7,7 0 0,1 -7,7H37a7,7 0 0,1 -7,-7Z"),
]


def icon(slug, title, shape):
    # The shape is in the foreground so parallax cannot slide it away from the clipped stem ends.
    fg = [P(shape, "a1.200"), Clip(shape, [P(STEMS, INK)])] + [P(head(c), HEADS[c]) for c in (A, B)]
    mono = [P(shape, K, a=0.3), Clip(shape, [P(STEMS, K)]), P(head(A), K), P(head(B), K)]
    return Icon(slug, title, "shape", "", [full("a2.100")], fg, mono)


ICONS = [icon(*s) for s in SHAPES]
