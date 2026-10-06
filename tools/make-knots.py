#!/usr/bin/env python3
"""
Draws the knot steps as vector drawables, app/src/main/res/drawable/knot_<name>_<step>.xml.

    python3 tools/make-knots.py            write the drawables
    python3 tools/make-knots.py --sheet D  also write each step as an SVG into D, to look at

Each knot is one rope, written as a few points it passes through, each with a height: 1 where
it passes over another part, -1 where it passes under, 0 elsewhere. The steps are the same rope
cut short, so a step shows exactly the rope the next one carries on from, with an open arrow on
the working end pointing where it goes next. The last step is the knot drawn tight.

The hand is the shop's, as kinokocho's make-art.py measured it off a drawing of a chanterelle:

1. Thin. One pen line, about 0.011 of the width of the drawing.
2. One weight. The rope and what it is tied to are drawn with the same nib.
3. Few lines. A rope is one line, not two edges; a post is two.
4. No black. Nothing is filled. Over and under is a gap in the lower strand, which is how a
   pen says it, and it holds on sixteen greys where shading would turn to mud.

The diagrams are drawn at 120 x 80 and shown about 220dp wide, so a gap is ~3.4 units: wide
enough to read as a break at arm's length, narrow enough that the strand still reads as one rope
passing under another.
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from knotdraw import Region, Strand, drawable, svg  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")

STROKE = 1.3
GAP = 3.4


def tight(strand, keep_from, keep_to, centre, k, arrow=False):
    """The same rope pulled snug: the points between two indices drawn in toward a centre."""
    cx, cy = centre
    pts = []
    for i, (x, y, z) in enumerate(strand.points):
        if keep_from <= i <= keep_to:
            pts.append((cx + (x - cx) * k, cy + (y - cy) * k, z))
        else:
            pts.append((x, y, z))
    return Strand(pts, arrow=arrow)


def post(x, top=6, bottom=74, w=8):
    """A post seen side on: two edges, rope behind it hidden."""
    l, r = x - w / 2, x + w / 2
    edges = [
        Strand([(l, top, 0), (l, bottom, 0)]),
        Strand([(r, top, 0), (r, bottom, 0)]),
    ]
    return edges, Region([(l, top), (r, top), (r, bottom), (l, bottom)])


def rail(y, left=6, right=114, w=8):
    """A rail or branch seen side on, running across."""
    t, b = y - w / 2, y + w / 2
    edges = [
        Strand([(left, t, 0), (right, t, 0)]),
        Strand([(left, b, 0), (right, b, 0)]),
    ]
    return edges, Region([(left, t), (right, t), (right, b), (left, b)])


# ---------------------------------------------------------------------------------------------
# Shapes the knots are built from.

import math  # noqa: E402

import numpy as np  # noqa: E402

from knotdraw import catmull_rom  # noqa: E402


def coil(x0, x1, cy, r, turns, start=0.0, step=30, lean=4.0):
    """Turns around a line running across at height cy, from x0 to x1: (x, y, z) points.

    Angle 0 is the top of the line, 90 the front (z = 1), 180 below, 270 behind.
    """
    pts = []
    total = 360.0 * turns
    n = int(total / step)
    for i in range(n + 1):
        a = start + total * i / n
        t = math.radians(a)
        x = x0 + (x1 - x0) * i / n
        # Lean the front of each turn sideways, as a spring looks from a little off
        # square, so a turn reads as a loop and not as a zigzag.
        pts.append((x + lean * math.sin(t), cy - r * math.cos(t), math.sin(t)))
    return pts


def coil_on(p0, p1, r, turns, start=0.0, step=30, lean=2.5):
    """Turns around the straight piece of rope from p0 to p1."""
    (ax, ay), (bx, by) = p0, p1
    L = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / L, (by - ay) / L
    nx, ny = -uy, ux
    pts = []
    total = 360.0 * turns
    n = int(total / step)
    for i in range(n + 1):
        a = start + total * i / n
        t = math.radians(a)
        s = L * i / n
        s += lean * math.sin(t)
        pts.append((ax + ux * s - nx * r * math.cos(t), ay + uy * s - ny * r * math.cos(t), math.sin(t)))
    return pts


def doubled(points, d, loop=None):
    """A rope folded in two and used as one: two lines d either side of the path, joined
    at the far end by a tight turn, or opened out there into a round loop of radius loop."""
    c = catmull_rom(points)
    tang = np.gradient(c[:, :2], axis=0)
    tang /= np.linalg.norm(tang, axis=1)[:, None]
    norm = np.stack([-tang[:, 1], tang[:, 0]], axis=1)
    left = np.column_stack([c[:, :2] + norm * d, c[:, 2]])
    right = np.column_stack([c[:, :2] - norm * d, c[:, 2]])
    end, t = c[-1, :2], tang[-1]
    z = c[-1, 2]
    if loop:
        centre = end + t * loop * 0.95
    else:
        centre = end
    a0 = math.atan2(*(left[-1, 1::-1] - centre))
    a1 = math.atan2(*(right[-1, 1::-1] - centre))
    r0 = float(np.hypot(*(left[-1, :2] - centre)))
    # Go round the far side: the way whose midpoint lies further from the knot.
    best = None
    for sweep in ((a1 - a0) % (2 * math.pi), (a1 - a0) % (2 * math.pi) - 2 * math.pi):
        mid = a0 + sweep / 2
        far = np.dot(np.array([math.cos(mid), math.sin(mid)]), t)
        if best is None or far > best[0]:
            best = (far, sweep)
    sweep = best[1]
    turn = [(centre[0] + r0 * math.cos(a0 + sweep * i / 40), centre[1] + r0 * math.sin(a0 + sweep * i / 40), z)
            for i in range(1, 40)]
    return list(map(tuple, left)) + turn + list(map(tuple, right[::-1]))


def tight(strand, keep_from, keep_to, centre, k, arrow=False):
    """The same rope pulled snug: the points between two indices drawn in toward a centre."""
    cx, cy = centre
    pts = []
    for i, (x, y, z) in enumerate(strand.points):
        if keep_from <= i <= keep_to:
            pts.append((cx + (x - cx) * k, cy + (y - cy) * k, z))
        else:
            pts.append((x, y, z))
    return Strand(pts, arrow=arrow)


def post(x, top=6, bottom=74, w=8):
    """A post seen side on: two edges, rope behind it hidden."""
    l, r = x - w / 2, x + w / 2
    edges = [
        Strand([(l, top, 0), (l, bottom, 0)]),
        Strand([(r, top, 0), (r, bottom, 0)]),
    ]
    return edges, Region([(l, top), (r, top), (r, bottom), (l, bottom)])


def rail(y, left=6, right=114, w=8):
    """A rail or branch seen side on, running across."""
    t, b = y - w / 2, y + w / 2
    edges = [
        Strand([(left, t, 0), (right, t, 0)]),
        Strand([(left, b, 0), (right, b, 0)]),
    ]
    return edges, Region([(left, t), (right, t), (right, b), (left, b)])


# ---------------------------------------------------------------------------------------------
# Shapes the knots are built from.

import math  # noqa: E402

import numpy as np  # noqa: E402

from knotdraw import catmull_rom  # noqa: E402


def coil(x0, x1, cy, r, turns, start=0.0, step=30, lean=4.0):
    """Turns around a line running across at height cy, from x0 to x1: (x, y, z) points.

    Angle 0 is the top of the line, 90 the front (z = 1), 180 below, 270 behind.
    """
    pts = []
    total = 360.0 * turns
    n = int(total / step)
    for i in range(n + 1):
        a = start + total * i / n
        t = math.radians(a)
        x = x0 + (x1 - x0) * i / n
        # Lean the front of each turn sideways, as a spring looks from a little off
        # square, so a turn reads as a loop and not as a zigzag.
        pts.append((x + lean * math.sin(t), cy - r * math.cos(t), math.sin(t)))
    return pts


def coil_on(p0, p1, r, turns, start=0.0, step=30, lean=2.5):
    """Turns around the straight piece of rope from p0 to p1."""
    (ax, ay), (bx, by) = p0, p1
    L = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / L, (by - ay) / L
    nx, ny = -uy, ux
    pts = []
    total = 360.0 * turns
    n = int(total / step)
    for i in range(n + 1):
        a = start + total * i / n
        t = math.radians(a)
        s = L * i / n
        s += lean * math.sin(t)
        pts.append((ax + ux * s - nx * r * math.cos(t), ay + uy * s - ny * r * math.cos(t), math.sin(t)))
    return pts


def doubled(points, d, loop=None):
    """A rope folded in two and used as one: two lines d either side of the path.

    The fold is at the far end of the path, opened out into a round loop of the given
    radius on that end, or a tight turn when there is none.
    """
    c = catmull_rom(points)
    tang = np.gradient(c[:, :2], axis=0)
    tang /= np.linalg.norm(tang, axis=1)[:, None]
    norm = np.stack([-tang[:, 1], tang[:, 0]], axis=1)
    left = np.column_stack([c[:, :2] + norm * d, c[:, 2]])
    right = np.column_stack([c[:, :2] - norm * d, c[:, 2]])
    end = c[-1, :2]
    t_end = tang[-1]
    rad = loop if loop else d
    centre = end + t_end * (rad if loop else 0)
    turn = []
    # From the left line round to the right line, through the far side.
    a0 = math.atan2(norm[-1, 1], norm[-1, 0])
    for i in range(1, 24):
        a = a0 - math.pi * i / 24 * (1 if not loop else 1)
        if loop:
            # A round eye: start where the left line ends, go out round the far side.
            pass
        turn.append((centre[0] + rad * math.cos(a), centre[1] + rad * math.sin(a), c[-1, 2]))
    if loop:
        # Open the lines out into the circle smoothly: join left end -> circle -> right end.
        circle = []
        for i in range(0, 41):
            a = a0 + math.pi * 0.5 - (2 * math.pi - 0.0) * i / 40
            circle.append((centre[0] + rad * math.cos(a), centre[1] + rad * math.sin(a), c[-1, 2]))
        turn = circle
    path = list(map(tuple, left)) + turn + list(map(tuple, right[::-1]))
    return path


def tight(strand, idx, centre, k, arrow=False):
    """The same rope pulled snug: the points listed drawn in toward a centre."""
    cx, cy = centre
    pts = []
    for i, (x, y, z) in enumerate(strand.points):
        if i in idx:
            pts.append((cx + (x - cx) * k, cy + (y - cy) * k, z))
        else:
            pts.append((x, y, z))
    return Strand(pts, arrow=arrow)


def transform(points, f):
    return [f(x, y) + (z,) for x, y, z in points]


# ---------------------------------------------------------------------------------------------
# The knots. Coordinates on the 120 x 80 grid, y down; z is 1 over, -1 under.

def overhand():
    # Standing part in from the left; a loop with the end crossing over (A); the end round
    # and up through the loop: under its right side (B), over its left side (C).
    rope = Strand([
        (4, 62, 0), (26, 62, 0),
        (48, 59, -1),            # 2  A, standing part under
        (63, 52, 0),
        (70, 42, 1),             # 4  B, loop over
        (70, 30, 0), (62, 19, 0), (50, 15, 0), (38, 19, 0),
        (33, 31, -1),            # 9  C, loop under
        (35, 45, 0),
        (48, 59, 1),             # 11 A, end over
        (60, 70, 0), (78, 70, 0), (84, 57, 0),
        (70, 42, -1),            # 15 B, end under
        (52, 36, 0),
        (33, 31, 1),             # 17 C, end over
        (18, 28, 0), (6, 26, 0),
    ])
    snug = tight(rope, set(range(2, 18)), (54, 44), 0.42)
    snug.points[0:2] = [(4, 52, 0), (34, 50, 0)]
    snug.points[18:20] = [(30, 40, 0), (6, 36, 0)]
    snug.points[12:15] = [(60, 54, 0), (66, 54, 0), (66, 48, 0)]
    return [rope.upto(13), rope.upto(19, arrow=False)]


FIG8 = [
    (4, 60, 0), (20, 60, 0),
    (34, 60, 1),             # 2  B, standing over
    (48, 60, 0),
    (62, 58, -1),            # 4  A, standing under
    (74, 50, 0),
    (80, 38, 1),             # 6  D, loop over
    (78, 24, 0), (66, 14, 0), (52, 14, 0), (44, 22, 0),
    (43, 36, -1),            # 11 C, loop under
    (48, 48, 0),
    (62, 58, 1),             # 13 A, end over
    (70, 66, 0), (64, 74, 0), (46, 75, 0), (30, 70, 0),
    (34, 60, -1),            # 18 B, end under
    (38, 48, 0),
    (43, 36, 1),             # 20 C, end over
    (58, 30, 0),
    (80, 38, -1),            # 22 D, end under
    (94, 40, 0), (110, 36, 0),
]


def figure_eight():
    rope = Strand(FIG8)
    snug = tight(rope, set(range(2, 23)), (56, 46), 0.45)
    snug.points[0:2] = [(4, 50, 0), (26, 50, 0)]
    snug.points[23:25] = [(84, 44, 0), (112, 42, 0)]
    return [rope.upto(14), rope.upto(19), Strand(FIG8)]


def figure_eight_loop():
    # The figure eight's own path, tied with the rope folded in two, the fold left long
    # as the loop.
    core = [(x * 0.82 - 2, y * 0.9 + 2, z) for x, y, z in FIG8[:-1]]
    core[0] = (2, core[0][1], 0)
    bight = Strand(doubled([(2, 44, 0), (40, 44, 0), (70, 44, 0)], 2.2, loop=None))
    tied = Strand(doubled(core + [(96, 32, 0)], 2.2, loop=13))
    snug_core = [(x, y, z) for x, y, z in core]
    snug_core = [((x - 46) * 0.5 + 46, (y - 42) * 0.5 + 42, z) if 2 <= i <= 22 else (x, y, z)
                 for i, (x, y, z) in enumerate(snug_core)]
    snug_core[0], snug_core[1] = (2, 40, 0), (20, 40, 0)
    snug_core[23] = (66, 40, 0)
    snug = Strand(doubled(snug_core + [(78, 40, 0)], 2.2, loop=16))
    for s in (bight, tied):
        s.raw = s.points
    return [bight, tied]


BOWLINE = [
    (50, 1, 0),
    (50, 8, 1),              # 1  standing part over the collar behind it
    (50, 16, -1),            # 2  standing part under the collar in front
    (50, 26, -1),            # 3  X1, standing part under
    (47, 38, 0), (53, 49, 0),
    (61, 53, 1),             # 6  hole bottom over the end going down
    (70, 52, 1),             # 7  hole bottom over the end coming up
    (79, 46, 0), (83, 36, 0), (79, 26, 0),
    (71, 21, -1),            # 11 hole top under the end coming up
    (61, 20, -1),            # 12 hole top under the end going down
    (50, 26, 1),             # 13 X1, over
    (40, 32, 0), (30, 44, 0), (28, 60, 0), (38, 72, 0), (54, 77, 0), (68, 73, 0), (71, 64, 0),
    (70, 52, -1),            # 21 end up, under the hole's bottom
    (71, 36, 0),
    (71, 21, 1),             # 23 over the hole's top
    (71, 10, 0), (62, 4, 0),
    (50, 8, -1),             # 26 behind the standing part
    (41, 9, 0), (40, 14, 0),
    (50, 16, 1),             # 29 in front of it
    (61, 20, 1),             # 30 back down, over the hole's top
    (62, 36, 0),
    (61, 53, -1),            # 32 under the hole's bottom
    (60, 62, 0), (59, 69, 0),
]


def bowline():
    pts = [((x - 55) * 1.45 + 58, y, z) for x, y, z in BOWLINE]
    rope = Strand(pts)
    return [rope.upto(20), rope.upto(28), Strand(pts)]


def clove_hitch():
    edges, region = post(60, w=14)
    rope = Strand([
        (6, 46, 0), (30, 45, 0),
        (50, 41.5, 0.6),
        (59, 36.6, 0.3),         # 3  under the rider
        (70, 30, 0.6), (76, 24, 0),
        (66, 19, -1), (54, 19, -1),
        (44, 22, 0), (50, 24, 0.6),
        (59, 36.6, 1.4),         # 10 the rider, over
        (70, 52, 0.6), (76, 58, 0),
        (66, 62, -1), (54, 62, -1),
        (44, 58, 0), (50, 54, 0.6),
        (65.8, 46.1, 0.3),       # 17 the end, under the rider
        (72, 43.2, 0.4), (90, 42, 0), (114, 40, 0),
    ])
    return [(edges + [rope.upto(9)], [region]), (edges + [rope.upto(16)], [region]),
            (edges + [Strand(rope.points)], [region])]


def two_half_hitches():
    edges, region = post(14, w=10)
    rope = Strand([
        (116, 28, 0), (92, 28, 0),
        (70, 28, -1), (60, 28, 1), (52, 28, -1), (42, 28, 1),    # 2-5 where the hitches cross
        (28, 28, 0), (14, 28, 1), (4, 40, 0), (14, 56, -1), (26, 56, 0),
        (40, 56, 0),                                             # 11
        (46, 52, 0), (48, 43, 1), (52, 28, 1), (50, 14, 0), (44, 16, 0), (42, 28, -1), (40, 36, 0),
        (48, 43, -1),                                            # 19 tucked under its own turn
        (58, 47, 0),                                             # 20
        (64, 52, 0), (66, 43, 1), (70, 28, 1), (68, 14, 0), (62, 16, 0), (60, 28, -1), (58, 36, 0),
        (66, 43, -1),
        (80, 48, 0), (104, 52, 0),
    ])
    return [(edges + [rope.upto(11)], [region]), (edges + [rope.upto(20)], [region]),
            (edges + [Strand(rope.points)], [region])]


def taut_line():
    edges, region = post(10, w=8)
    rope = Strand([
        (116, 26, 0), (100, 26, 0),
        (90, 26, -1), (80, 26, 1),         # 2-3 the half hitch outside
        (74, 26, -1), (64, 26, 1),         # 4-5 first turn inside
        (56, 26, -1), (46, 26, 1),         # 6-7 second turn inside
        (28, 26, 0), (10, 26, 1), (2, 40, 0), (10, 58, -1), (20, 58, 0),
        (44, 58, 0),                                                     # 13
        (66, 58, 0), (72, 54, 0), (73, 48, 1), (74, 26, 1), (72, 14, 0), (66, 16, 0), (64, 26, -1),
        (62, 34, 0), (60, 40, 0), (56, 26, 1), (54, 14, 0), (48, 16, 0), (46, 26, -1), (44, 36, 0),
        (44, 44, 0),                                                     # 28
        (52, 48, 0), (73, 48, -1),            # back out below the turns, under the first
        (82, 50, 0), (86, 52, 0), (88, 44, 1), (90, 26, 1), (88, 14, 0), (82, 16, 0), (80, 26, -1),
        (78, 34, 0), (88, 44, -1),
        (100, 50, 0), (114, 52, 0),
    ])
    return [(edges + [rope.upto(13)], [region]), (edges + [rope.upto(28)], [region]),
            (edges + [Strand(rope.points)], [region])]


def sheet_bend():
    # The thicker rope folded into a bight, opening to the right; the other comes up
    # through it, round behind both legs, and is tucked under its own standing part.
    bight = Strand([
        (116, 28, 0), (90, 28, 0),
        (74, 28, 1), (60, 28, -1),       # 2-3
        (42, 28, 0), (32, 36, 0), (32, 44, 0), (42, 52, 0),
        (60, 52, 1), (67, 52, -1), (74, 52, 1),   # 8-10
        (90, 52, 0), (116, 52, 0),
    ])
    other = Strand([
        (58, 79, 0), (59, 64, 0),
        (60, 52, -1),                    # 2 up through the bight, under its lower leg
        (60, 40, 1),                     # 3 its own standing part, over the tuck later
        (60, 28, 1),                     # 4 over the upper leg
        (63, 18, 0), (72, 14, 0), (78, 20, 0),
        (74, 28, -1), (74, 52, -1),      # 8-9 down behind both legs
        (76, 62, 0), (70, 64, 0),
        (67, 52, 1),                     # 12 back over the lower leg
        (60, 40, -1),                    # 13 tucked under itself
        (48, 37, 0), (40, 36, 0),
    ])
    return [[Strand(bight.points)], [Strand(bight.points), other.upto(11)], [Strand(bight.points), Strand(other.points)]]


def square_knot():
    red = [
        (2, 53, 0), (24, 53, 0),
        (35.6, 53, 1),
        (46, 54, 0), (54, 60, 0),
        (60, 64, -1),
        (68, 70, 0), (78, 70, 0), (84, 63, 0),
        (84, 53, 1),
        (84, 40, 0),                 # 10
        (84, 26.7, 1),
        (82, 13, 0), (73, 6, 0), (65, 10, 0),
        (60, 16, -1),
        (53, 23, 0), (46, 26.7, 0),
        (35.6, 26.7, 1),
        (16, 26.7, 0),
    ]
    blue = [
        (118, 53, 0), (96, 53, 0),
        (84, 53, -1),
        (74, 54, 0), (66, 60, 0),
        (60, 64, 1),
        (52, 70, 0), (42, 70, 0), (36, 63, 0),
        (35.6, 53, -1),
        (35.6, 40, 0),               # 10
        (35.6, 26.7, -1),
        (38, 13, 0), (47, 6, 0), (55, 10, 0),
        (60, 16, 1),
        (67, 23, 0), (74, 26.7, 0),
        (84, 26.7, -1),
        (104, 26.7, 0),
    ]
    half = [Strand(red[:10] + [(84, 44, 0)], arrow=True), Strand(blue[:10] + [(35.6, 44, 0)], arrow=True)]
    full = [Strand(red), Strand(blue)]
    k, c = 0.55, (60, 40)
    def pull(pts, tails):
        out = []
        for i, (x, y, z) in enumerate(pts):
            if i in tails:
                out.append((x, (y - c[1]) * 0.6 + c[1], z))
            else:
                out.append(((x - c[0]) * k + c[0], (y - c[1]) * k + c[1], z))
        return out
    snug = [Strand(pull(red, {0, 19})), Strand(pull(blue, {0, 19}))]
    return [half, full, snug]


def prusik(turns):
    """A loop of cord round a rope: one coil up, one coil down, joined by the bridge at
    the back (drawn here at the side), the loop's tail leaving from the middle."""
    cx, w, gap = 46, 10, 9
    top = []
    y = 34
    for i in range(turns):
        top += [(cx, y, -1), (cx - w + 1, y - 1, 0), (cx - w, y - 3, 0), (cx - w + 2, y - 4.5, 0.5), (cx, y - 3.5, 1), (cx + w - 2, y - 6, 0.5), (cx + w, y - 7.5, 0)]
        y -= gap
    top = top[:-1]
    left_top = top[-1]
    bottom = []
    y = 80 - 34
    for i in range(turns):
        bottom += [(cx, y, -1), (cx - w + 1, y + 1, 0), (cx - w, y + 3, 0), (cx - w + 2, y + 4.5, 0.5), (cx, y + 3.5, 1), (cx + w - 2, y + 6, 0.5), (cx + w, y + 7.5, 0)]
        y += gap
    bottom = bottom[:-1]
    bridge = [(cx - w - 7, left_top[1] + 4, 0), (cx - w - 10, 40, 0), (cx - w - 7, 80 - left_top[1] - 4, 0)]
    cord = [(cx + w, 37, 0)] + top + bridge + list(reversed(bottom)) + [
        (cx + w, 43, 0), (70, 45, 0), (100, 47, 0), (112, 40, 0), (100, 33, 0), (70, 35, 0)]
    m = [(cx, -2, 0)] + sorted([(x, yv, -z) for x, yv, z in cord if x == cx and z != 0], key=lambda p: p[1]) + [(cx, 82, 0)]
    return [Strand(m), Strand(cord, closed=True)]


def prusik_steps():
    return [prusik(1), prusik(3)]


def double_fishermans():
    a_line = [(2, 36, 0), (40, 36, 0), (70, 36, 0), (92, 36, 0)]
    a_coil = coil(100, 76, 40, 12, 2, start=0)
    a_end = [(70, 31, 0), (66, 40, 0), (88, 40, 0), (106, 40, 0), (116, 43, 0)]
    a = Strand(a_line + a_coil + a_end)

    def rot(pts):
        return [(120 - x, 80 - y, z) for x, y, z in pts]
    b = Strand(rot(a.points))
    a_only = [Strand(a_line + a_coil + a_end), Strand(rot(a_line) + [(4, 44, 0)])]
    b_line_plain = Strand([(118, 44, 0), (60, 44, 0), (4, 44, 0)])
    step1 = [Strand(a.points), b_line_plain]

    def shift(pts, lo, hi, dx):
        return [(x + dx, y, z) if lo <= x <= hi else (x, y, z) for x, y, z in pts]
    a2 = Strand(shift(a.points, 60, 104, -8))
    b2 = Strand(shift(b.points, 16, 60, 8))
    return [step1, [a, b], [a2, b2]]


def timber_hitch():
    edges, region = rail(51, left=4, right=116, w=14)
    dogs = coil_on((61, 34), (56, 64), 5.0, 3, start=270)
    rope = Strand([
        (116, 22, 0), (80, 22, 0),
        (60, 22, 1), (50, 22, -1),          # 2-3 where the end turns round it
        (40, 24, 0), (35, 36, 0),
        (34, 50, -1), (38, 64, 0),          # behind the log
        (48, 68, 0), (57, 62, 1), (60, 50, 1), (61, 36, 1),
        (60, 22, -1), (57, 12, 0), (51, 13, 0),
        (50, 22, 1), (52, 30, 0), (58, 34, 0),
    ] + [(x, y, 1 + 1.4 * z) for x, y, z in dogs[1:]])
    return [(edges + [rope.upto(11)], [region]), (edges + [rope.upto(17)], [region]),
            (edges + [Strand(rope.points)], [region])]


KNOTS = {
    "overhand": overhand,
    "figure_eight": figure_eight,
    "figure_eight_loop": figure_eight_loop,
    "bowline": bowline,
    "clove_hitch": clove_hitch,
    "two_half_hitches": two_half_hitches,
    "taut_line": taut_line,
    "sheet_bend": sheet_bend,
    "square_knot": square_knot,
    "prusik": prusik_steps,
    "double_fishermans": double_fishermans,
    "timber_hitch": timber_hitch,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", help="also write SVGs here")
    ap.add_argument("--only", help="one knot")
    ap.add_argument("--strict", action="store_true",
                    help="fail on any near-touch with no over or under; the known ones are where\n"
                         "two parallel parts lie close, and read correctly when looked at")
    args = ap.parse_args()
    bad = False
    for name, make in KNOTS.items():
        if args.only and name != args.only:
            continue
        steps = make()
        for n, step in enumerate(steps, 1):
            if isinstance(step, Strand):
                strands, regions = [step], ()
            elif isinstance(step, tuple):
                strands, regions = step
            else:
                strands, regions = step, ()

            def warn(where, name=name, n=n):
                nonlocal bad
                bad = True
                print("%s step %d: crossing with no over or under near %s" % (name, n, where[:4]))

            xml = drawable(strands, regions, STROKE, GAP, warn,
                           comment="Generated by tools/make-knots.py; edit that, not this.")
            with open(os.path.join(OUT, "knot_%s_%d.xml" % (name, n)), "w") as f:
                f.write(xml)
            if args.sheet:
                os.makedirs(args.sheet, exist_ok=True)
                with open(os.path.join(args.sheet, "%s_%d.svg" % (name, n)), "w") as f:
                    f.write(svg(strands, regions, STROKE, GAP))
    sys.exit(1 if bad and args.strict else 0)


if __name__ == "__main__":
    main()
