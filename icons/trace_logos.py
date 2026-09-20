"""Trace the two brand logos (微信支付 / 支付宝) from PNG into outlines.

The rest of the icon set is drawn from geometric primitives, but these two arrive as
bitmaps: a solid speech bubble with a knocked-out check, and the 支付宝 支 character. A
hand-fit path for either would be a guess, so they are traced instead -- and the tracer is
kept here so the result can be reproduced from the sources in `source/` rather than being
an unexplained lump of coordinates.

Pipeline, per image:

1. **Ink coverage.** `alpha * (1 - luminance)` per pixel. Using both channels matters:
   the sources are black-on-transparent, so alpha alone would treat a half-transparent
   edge pixel as full ink, and luminance alone would treat the transparent background
   (which is black) as ink. The product is a real "how much ink is here" value, and it is
   anti-aliased, which is what gives sub-pixel accuracy below.
2. **Upscale** if the source is small. 96 px is not enough resolution for a Chinese
   character: the traced staircase would survive simplification and show up as jaggies at
   icon size. The upscale is **bilinear on purpose** -- it interpolates the coverage
   linearly, so the half-coverage crossing stays exactly where it was. Lanczos is sharper
   but overshoots at a hard edge, and that ringing pushes the level line outward: measured
   against its own source, a Lanczos-upscaled trace came out ~1 % fatter in area, while
   bilinear is symmetric to within a few tenths of a pixel.
3. **Marching squares** at the half-coverage level, with linear interpolation along cell
   edges, producing closed contours at sub-pixel positions. Chaining is done on undirected
   segments, so the table's segment orientation cannot silently break a loop.
4. **Douglas-Peucker** per contour, which is what turns the pixel staircase into straight
   runs and smooth curves.
5. **Normalise** to the icons' canvas: the longer side of the whole glyph is scaled to
   `TARGET` units and centred in a 100 x 100 box.

Why `TARGET = 80` and not the ~84.5 the stroked icons actually occupy: a stroke reaching
0.10..0.90 of the canvas is *centred* on that line, so its ink spills ~4 units further than
the coordinates suggest. These are solid shapes -- matching that 84.5 ink box would make
them read visibly heavier than every other icon. 80 units is the box the stroked icons are
*drawn against*, which is the honest optical match.

Writes `logos.json` next to this file:
    {"wechat": {"viewBox": 100, "paths": ["M... Z", ...]}, "alipay": {...}}

Run:  python trace_logos.py
"""

import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE_DIR = os.path.join(HERE, "source")

SIDE = 100.0      # the icon canvas every other file in svg/ uses
TARGET = 80.0     # the box a logo's longer side is scaled into
LEVEL = 0.5       # half coverage is the outline

# Source file -> enum-ish name. The names match `LedgerIcon.WECHAT` / `.ALIPAY`.
SOURCES = [("wechat", "wechat.png"), ("alipay", "alipay.png")]

# Simplification tolerance, in working pixels. Scaled with the working resolution so the
# two images are simplified to the same *visual* tolerance: a 96 px character needs a
# relatively finer pass than a 359 px bubble to keep its stroke ends square.
def tolerance(working_size):
    return max(0.4, working_size / 460.0)


def ink_coverage(path, min_working=420):
    """Anti-aliased ink amount per pixel, at a workable resolution."""
    image = Image.open(path).convert("RGBA")
    width, height = image.size
    source = image.load()

    grey = Image.new("L", (width, height))
    target = grey.load()
    for y in range(height):
        for x in range(width):
            r, g, b, a = source[x, y]
            luminance = (299 * r + 587 * g + 114 * b) // 1000
            target[x, y] = (255 - luminance) * a // 255

    longest = max(width, height)
    if longest < min_working:
        factor = max(1, round(min_working / longest))
        grey = grey.resize((width * factor, height * factor), Image.BILINEAR)
    return grey


def _edge_point(kind, x, y, a, b):
    """The LEVEL crossing on one cell edge, linearly interpolated."""
    if a == b:
        t = 0.5
    else:
        t = (LEVEL - a) / (b - a)
        t = min(1.0, max(0.0, t))
    if kind == "T":      # between (x, y) and (x+1, y)
        return (x + t, float(y))
    if kind == "B":      # between (x, y+1) and (x+1, y+1)
        return (x + t, float(y + 1))
    if kind == "L":      # between (x, y) and (x, y+1)
        return (float(x), y + t)
    return (float(x + 1), y + t)   # "R"


# Marching-squares cases: which cell edges the level line cuts. Index bits, low to high,
# are the top-left, top-right, bottom-right, bottom-left corners being above LEVEL.
CASES = {
    1: [("L", "T")], 2: [("T", "R")], 3: [("L", "R")], 4: [("R", "B")],
    5: [("L", "T"), ("R", "B")], 6: [("T", "B")], 7: [("L", "B")],
    8: [("B", "L")], 9: [("B", "T")], 10: [("T", "R"), ("B", "L")],
    11: [("B", "R")], 12: [("R", "L")], 13: [("R", "T")], 14: [("T", "L")],
}


def trace(grey):
    """Closed contours of the LEVEL isoline, as lists of (x, y)."""
    width, height = grey.size
    pixels = grey.load()

    def at(x, y):
        return pixels[x, y] / 255.0

    # Undirected graph of segment endpoints: a point on a cell edge is shared by exactly
    # two cells, and both compute it from the same pair of corner values, so the keys are
    # bit-identical. Rounding only guards against a future change to that.
    def key(point):
        return (round(point[0], 5), round(point[1], 5))

    graph = {}
    for y in range(height - 1):
        for x in range(width - 1):
            v00, v10 = at(x, y), at(x + 1, y)
            v01, v11 = at(x, y + 1), at(x + 1, y + 1)
            index = (1 if v00 > LEVEL else 0) | (2 if v10 > LEVEL else 0) \
                | (4 if v11 > LEVEL else 0) | (8 if v01 > LEVEL else 0)
            if index in (0, 15):
                continue
            if index in (5, 10):
                # Saddle: the two corners that agree decide how the two lines pair up.
                average = (v00 + v10 + v11 + v01) / 4.0
                centre_above = average > LEVEL
                if index == 5:
                    pairs = [("L", "T"), ("R", "B")] if centre_above \
                        else [("L", "B"), ("T", "R")]
                else:
                    pairs = [("T", "R"), ("B", "L")] if centre_above \
                        else [("T", "L"), ("R", "B")]
            else:
                pairs = CASES[index]
            corners = {"T": (v00, v10), "B": (v01, v11),
                       "L": (v00, v01), "R": (v10, v11)}
            for first, second in pairs:
                a = key(_edge_point(first, x, y, *corners[first]))
                b = key(_edge_point(second, x, y, *corners[second]))
                graph.setdefault(a, []).append(b)
                graph.setdefault(b, []).append(a)

    contours = []
    used = set()
    for start, neighbours in graph.items():
        for first in neighbours:
            if (start, first) in used:
                continue
            loop = [start]
            used.add((start, first))
            used.add((first, start))
            previous, current = start, first
            while current != start:
                loop.append(current)
                options = graph.get(current, [])
                nxt = None
                for candidate in options:
                    if candidate != previous and (current, candidate) not in used:
                        nxt = candidate
                        break
                if nxt is None:
                    # Degenerate (a contour touching itself at one point); stop the loop
                    # rather than following the wrong branch.
                    for candidate in options:
                        if (current, candidate) not in used:
                            nxt = candidate
                            break
                if nxt is None:
                    break
                used.add((current, nxt))
                used.add((nxt, current))
                previous, current = current, nxt
            if len(loop) >= 4:
                contours.append([(float(p[0]), float(p[1])) for p in loop])
    return contours


def _distance_to_segment(point, start, end):
    (px, py), (ax, ay), (bx, by) = point, start, end
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
    t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = min(1.0, max(0.0, t))
    cx, cy = ax + t * dx, ay + t * dy
    return ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5


def simplify(points, epsilon):
    """Douglas-Peucker on a closed contour (kept as an open run, closed by the caller)."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        if last <= first + 1:
            continue
        worst, worst_at = 0.0, None
        for i in range(first + 1, last):
            distance = _distance_to_segment(points[i], points[first], points[last])
            if distance > worst:
                worst, worst_at = distance, i
        if worst_at is not None and worst > epsilon:
            keep[worst_at] = True
            stack.append((first, worst_at))
            stack.append((worst_at, last))
    return [p for p, k in zip(points, keep) if k]


def normalise(contours, side=SIDE, target=TARGET):
    """Scale the whole glyph into a `target` box, centred on a `side` canvas."""
    xs = [p[0] for loop in contours for p in loop]
    ys = [p[1] for loop in contours for p in loop]
    min_x, max_x, min_y, max_y = min(xs), max(xs), min(ys), max(ys)
    width, height = max_x - min_x, max_y - min_y
    scale = target / max(width, height)
    offset_x = (side - width * scale) / 2.0 - min_x * scale
    offset_y = (side - height * scale) / 2.0 - min_y * scale
    return [
        [(p[0] * scale + offset_x, p[1] * scale + offset_y) for p in loop]
        for loop in contours
    ]


def to_path(contours):
    """One path holding every contour; `evenodd` turns the inner ones into holes."""
    parts = []
    for loop in contours:
        points = loop + [loop[0]]
        body = " L".join(f"{round(x, 2):g},{round(y, 2):g}" for x, y in points)
        parts.append("M" + body + " Z")
    return " ".join(parts)


def main():
    result = {}
    for name, filename in SOURCES:
        path = os.path.join(SOURCE_DIR, filename)
        grey = ink_coverage(path)
        contours = trace(grey)
        epsilon = tolerance(max(grey.size))
        simplified = [simplify(loop, epsilon) for loop in contours]
        simplified = [loop for loop in simplified if len(loop) >= 3]
        placed = normalise(simplified)
        total = sum(len(loop) for loop in placed)
        result[name] = {
            "viewBox": 100,
            "source": f"source/{filename}",
            "sourceSize": list(Image.open(path).size),
            "workingSize": list(grey.size),
            "tolerance": round(epsilon, 3),
            "contours": len(placed),
            "points": total,
            "path": to_path(placed),
        }
        print(f"{name}: {len(placed)} contours, {total} points, "
              f"tolerance {epsilon:.2f} at {grey.size}")

    out = os.path.join(HERE, "logos.json")
    with open(out, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(result, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")
    print("wrote", out)


if __name__ == "__main__":
    main()
