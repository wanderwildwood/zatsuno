"""
The drawing machinery behind make-knots.py: a rope as a smooth line through a few points,
with each point given a height, so that wherever two parts of the rope cross, the lower one
is broken by a gap and the upper one runs on unbroken.

That gap is the whole of how a knot diagram says over and under. It is what a pen does on
paper, and it survives sixteen greys where shading would not.
"""

import math

import numpy as np

GRID_W, GRID_H = 120.0, 80.0


def catmull_rom(points, samples_per_unit=2.0, closed=False):
    """Centripetal Catmull-Rom through (x, y, z) points; returns an array of (x, y, z)."""
    p = [np.array(q, dtype=float) for q in points]
    if closed:
        p = [p[-1]] + p + [p[0], p[1]]
    else:
        p = [2 * p[0] - p[1]] + p + [2 * p[-1] - p[-2]]
    out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]

        def tj(ti, a, b):
            d = np.hypot(*(b[:2] - a[:2]))
            return ti + max(d, 1e-6) ** 0.5

        t0 = 0.0
        t1 = tj(t0, p0, p1)
        t2 = tj(t1, p1, p2)
        t3 = tj(t2, p2, p3)
        seg = np.hypot(*(p2[:2] - p1[:2]))
        n = max(2, int(seg * samples_per_unit))
        for k in range(n):
            t = t1 + (t2 - t1) * k / n
            a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1
            a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2
            a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3
            b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2
            b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3
            c = (t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2
            # Height is not smoothed: it runs straight from one point to the next, so a
            # point marked over stays over all the way to its neighbours.
            c[2] = p1[2] + (p2[2] - p1[2]) * k / n
            out.append(c)
    if not closed:
        out.append(p[-2])
    return np.array(out)


class Strand:
    """A line to draw: a rope, or the outline of something a rope is tied to."""

    def __init__(self, points, arrow=False, closed=False, start_arrow=False, raw=None):
        self.points = [tuple(q) if len(q) == 3 else (q[0], q[1], 0.0) for q in points]
        self.arrow = arrow
        self.start_arrow = start_arrow
        self.closed = closed
        # Already sampled (x, y, z) rows, drawn as they are rather than smoothed again.
        self.raw = raw

    def sampled(self):
        if self.raw is not None:
            return np.array(self.raw, dtype=float)
        return catmull_rom(self.points, closed=self.closed)

    def upto(self, n, arrow=True):
        """The rope as far as its n-th point, the working end still travelling."""
        return Strand(self.points[: n + 1], arrow=arrow)

    def shifted(self, dx, dy):
        return Strand([(x + dx, y + dy, z) for x, y, z in self.points], self.arrow, self.closed, self.start_arrow)


class Region:
    """A solid thing seen side on, like a post or a rail: rope behind it is hidden."""

    def __init__(self, polygon, z=0.0):
        self.polygon = polygon
        self.z = z

    def inside(self, x, y, margin):
        # Point in polygon, grown by margin (checked by distance to edges).
        poly = self.polygon
        n = len(poly)
        c = False
        for i in range(n):
            x1, y1 = poly[i]
            x2, y2 = poly[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                c = not c
        if c:
            return True
        for i in range(n):
            if seg_dist(x, y, *poly[i], *poly[(i + 1) % n]) < margin:
                return True
        return False


def seg_dist(px, py, x1, y1, x2, y2):
    dx, dy = x2 - x1, y2 - y1
    L = dx * dx + dy * dy
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / L))
    return math.hypot(px - x1 - t * dx, py - y1 - t * dy)


def arclen(a):
    d = np.hypot(np.diff(a[:, 0]), np.diff(a[:, 1]))
    return np.concatenate([[0.0], np.cumsum(d)])


def visible_runs(strands, regions, gap, warn):
    """For each strand, the runs of sampled points that are not hidden."""
    sampled = [s.sampled() for s in strands]
    lengths = [arclen(a) for a in sampled]
    runs = []
    ambiguous = []
    for i, a in enumerate(sampled):
        hide = np.zeros(len(a), dtype=bool)
        for j, b in enumerate(sampled):
            # Distance from every point of a to every point of b.
            d = np.hypot(a[:, None, 0] - b[None, :, 0], a[:, None, 1] - b[None, :, 1])
            near = d < gap
            if i == j:
                # A strand never hides itself where the two points are close along it.
                s = lengths[i]
                near &= np.abs(s[:, None] - s[None, :]) > gap * 3.0
            if not near.any():
                continue
            dz = b[None, :, 2] - a[:, None, 2]
            over = near & (dz > 0.25)
            hide |= over.any(axis=1)
            tie = near & (np.abs(dz) <= 0.25) & (d < gap * 0.35)
            if tie.any() and i <= j:
                for k in np.argwhere(tie.any(axis=1))[:1]:
                    ambiguous.append((round(float(a[k[0], 0]), 1), round(float(a[k[0], 1]), 1)))
        for r in regions:
            for k, (x, y, z) in enumerate(a):
                if z < r.z - 0.25 and r.inside(x, y, gap * 0.55):
                    hide[k] = True
        out, cur = [], []
        for k, h in enumerate(hide):
            if h:
                if len(cur) > 1:
                    out.append(np.array(cur))
                cur = []
            else:
                cur.append(a[k])
        if len(cur) > 1:
            out.append(np.array(cur))
        runs.append(out)
    if ambiguous and warn:
        warn(ambiguous)
    return sampled, runs


def rdp(points, eps):
    """Ramer-Douglas-Peucker on an (n, 2+) array."""
    if len(points) < 3:
        return points
    a, b = points[0][:2], points[-1][:2]
    dmax, idx = 0.0, 0
    for i in range(1, len(points) - 1):
        d = seg_dist(points[i][0], points[i][1], a[0], a[1], b[0], b[1])
        if d > dmax:
            dmax, idx = d, i
    if dmax > eps:
        left = rdp(points[: idx + 1], eps)
        right = rdp(points[idx:], eps)
        return np.concatenate([left[:-1], right])
    return np.array([points[0], points[-1]])


def path_data(run):
    pts = rdp(run, 0.06)
    s = "M%.1f,%.1f" % (pts[0][0], pts[0][1])
    for p in pts[1:]:
        s += " L%.1f,%.1f" % (p[0], p[1])
    return s


def arrowhead(a, size, at_start=False):
    """An open chevron at the end of a sampled strand, pointing the way it travels."""
    if at_start:
        a = a[::-1]
    tip = a[-1][:2]
    # Direction over the last couple of units, not the last sample, which can be noisy.
    s = arclen(a)
    back = np.searchsorted(s, s[-1] - 2.5)
    d = tip - a[max(0, back - 1)][:2]
    d = d / (np.hypot(*d) or 1)
    n = np.array([-d[1], d[0]])
    tip2 = tip + d * size * 0.9
    p1 = tip2 - d * size + n * size * 0.75
    p2 = tip2 - d * size - n * size * 0.75
    return "M%.1f,%.1f L%.1f,%.1f L%.1f,%.1f" % (p1[0], p1[1], tip2[0], tip2[1], p2[0], p2[1])


def drawable(strands, regions=(), stroke=1.3, gap=3.4, warn=None, comment=""):
    sampled, runs = visible_runs(strands, regions, gap, warn)
    paths = []
    for s, a, rs in zip(strands, sampled, runs):
        for r in rs:
            paths.append(path_data(r))
        if s.arrow:
            paths.append(arrowhead(a, 3.2))
        if s.start_arrow:
            paths.append(arrowhead(a, 3.2, at_start=True))
    body = "\n".join(
        '    <path android:strokeColor="#000000" android:strokeWidth="%.2f" '
        'android:strokeLineCap="round" android:strokeLineJoin="round"\n'
        '        android:pathData="%s" />' % (stroke, p)
        for p in paths
    )
    head = '<?xml version="1.0" encoding="utf-8"?>\n'
    if comment:
        head += "<!-- %s -->\n" % comment
    return (
        head
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="240dp" android:height="160dp"\n'
        '    android:viewportWidth="%g" android:viewportHeight="%g">\n' % (GRID_W, GRID_H)
        + body
        + "\n</vector>\n"
    )


def svg(strands, regions=(), stroke=1.3, gap=3.4, scale=4.0, warn=None):
    sampled, runs = visible_runs(strands, regions, gap, warn)
    out = []
    for s, a, rs in zip(strands, sampled, runs):
        for r in rs:
            out.append(path_data(r))
        if s.arrow:
            out.append(arrowhead(a, 3.2))
        if s.start_arrow:
            out.append(arrowhead(a, 3.2, at_start=True))
    paths = "".join(
        '<path d="%s" fill="none" stroke="#000" stroke-width="%.2f" stroke-linecap="round" stroke-linejoin="round"/>' % (p, stroke)
        for p in out
    )
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %g %g">'
        '<rect width="100%%" height="100%%" fill="#fff"/>%s</svg>' % (GRID_W * scale, GRID_H * scale, GRID_W, GRID_H, paths)
    )
