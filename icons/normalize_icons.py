"""Redraw the hand-made icon set at one size and one stroke weight.

`svg/` is the design source and is never written to. This writes `normalized/`, which is
what `make_drawables.py` reads, so the app can be regenerated from hand-made artwork while
the artwork itself stays untouched.

Two things are unified, and nothing else.

* **Size.** Every glyph's *ink* -- what is actually painted, not the path coordinates -- is
  scaled so its longer side measures `LIVE` units, and centred on a 100 x 100 canvas. The
  incoming set spans 204..294 px on a 320 px canvas (1.44x) for one reason only: each file
  arrived on its own canvas -- 24, 32, 50, 512 and 100.

* **Weight.** Normalising size changes weight, because scaling a drawing scales its lines
  with it, so a glyph that filled little of its canvas comes out heavier than one that
  filled a lot. The line weight is therefore put back, to `STROKE` units of the same
  100-unit canvas.

Shapes are never otherwise touched, and no outline is ever moved *inwards*, because eating
into a shape is how a glyph loses a detail it needed. Only two routes exist:

* `vector` (the normal one) -- rescale the canvas, and if the ink is carried by stroked
  paths rather than by filled areas, re-issue the stroke widths too. Exact: only the pen
  changes, never a centreline, and caps and arcs survive verbatim. A glyph that is already
  at or above the target weight takes this route, which covers everything from the thin
  drawn icons to the solid bank and WeChat marks.

* `mask` -- only for a filled outline that comes out *lighter* than the target, where the
  width is baked into the shape and there is no pen to change. The glyph is traced back out
  of a 700 px bitmap of itself with its outline pushed out by exactly half the difference.
  Thickening a shape cannot lose a feature, and the traced outline sits within ~0.5 px of
  the original at 700 px -- about 1/500 of a pixel at 24 dp.

The stroke widths have to be settled before the ink box is known, because a thinner pen
draws a smaller glyph -- so every stroked icon is rendered twice, once at its final weights
and scale 1, and that second render is what the scale is fitted to.

Run:  python normalize_icons.py [--dry-run]
"""

import argparse
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image
from scipy import ndimage

import trace_logos

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE_DIR = os.path.join(HERE, "svg")
OUT_DIR = os.path.join(HERE, "normalized")
WORK_DIR = os.path.join(HERE, "_work")
PROBE_DIR = os.path.join(WORK_DIR, "probe")
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

SIDE = 100.0        # the canvas every normalised icon is drawn on
LIVE = 78.0         # the box a glyph's ink is scaled into
STROKE = 8.5        # the one stroke weight, in the same units
LEVEL = 0.5         # half coverage is the outline

CELL = 700          # pixels per glyph when measuring and tracing
COLS = 6
SKIP = ("launcher",)
STYLE_ATTRS = ("fill", "fill-rule", "stroke", "stroke-linecap", "stroke-linejoin")

# A glyph this far off the target weight is redrawn through the mask route.
LIGHT_BY = 0.9
# A stroke is worth re-issuing only if it plausibly carries the glyph's ink; below this it
# is a hairline detail sitting on a filled drawing and rescaling it would wreck the detail.
STROKE_SHARE = 0.4
# Pushing an outline out is always safe, so it is allowed further than pulling one in.
GROW_LIMIT = 1.6
# Pulling one in can eat a feature, so it is capped harder -- and never allowed at all on a
# glyph that holds ink further from its edge than any stroke could, i.e. a solid mass, where
# an offset would move the shape rather than change a line.
SHRINK_LIMIT = 1.0
MASS_LIMIT = 0.10


def icon_names(directory=SOURCE_DIR):
    return sorted(
        os.path.splitext(f)[0]
        for f in os.listdir(directory)
        if f.endswith(".svg") and not f.startswith(SKIP)
    )


def source_parts(name):
    """Root attributes and inner markup, verbatim apart from the XML preamble."""
    text = open(os.path.join(SOURCE_DIR, name + ".svg"), encoding="utf-8").read()
    text = re.sub(r"<\?xml[^>]*\?>", "", text)
    match = re.search(r"(?s)<svg\b([^>]*)>(.*)</svg>", text)
    attrs = dict(re.findall(r'([\w:.-]+)="([^"]*)"', match.group(1)))
    inner = re.sub(r"<!--.*?-->", "", match.group(2), flags=re.S)
    inner = re.sub(r"\s*/>", "/>", inner).strip()
    return attrs, inner


def viewbox_of(name):
    attrs, _ = source_parts(name)
    match = re.match(r"([-\d.]+)[,\s]+([-\d.]+)[,\s]+([-\d.]+)[,\s]+([-\d.]+)",
                     attrs.get("viewBox", f"0 0 {SIDE:g} {SIDE:g}"))
    return tuple(float(v) for v in match.groups())


def widest_stroke(name):
    """The heaviest stroke in the file, with the scale its ancestors impose on it.

    `stroke` and `stroke-width` inherit in SVG, so they are carried down the walk: a path
    that names only a width is still stroked if an ancestor says so, which is exactly how
    the drawn half of this set is written.
    """
    root = ET.parse(os.path.join(SOURCE_DIR, name + ".svg")).getroot()
    found = []

    def walk(element, scale, stroke, width):
        match = re.match(r"translate\([-\d.]+,[-\d.]+\)\s*scale\(([-\d.]+)\)",
                         element.get("transform", ""))
        if match:
            scale = scale * float(match.group(1))
        stroke = element.get("stroke", stroke)
        width = element.get("stroke-width", width)
        if stroke not in ("none", None) and width is not None:
            found.append(float(width) * scale)
        for child in element:
            walk(child, scale, stroke, width)

    walk(root, 1.0, root.get("stroke"), root.get("stroke-width"))
    return max(found) if found else 0.0


def scaled_widths(inner, factor):
    return re.sub(r'stroke-width="([-\d.]+)"',
                  lambda m: f'stroke-width="{float(m.group(1)) * factor:g}"', inner)


def style_of(attrs):
    return " ".join(f'{key}="{attrs[key]}"' for key in STYLE_ATTRS if key in attrs)


# --------------------------------------------------------------------------- rendering


def build_sheet(names, directory, path):
    cells = []
    for name in names:
        source = open(os.path.join(directory, name + ".svg"), encoding="utf-8").read()
        source = re.sub(r"<\?xml[^>]*\?>", "", source)
        source = re.sub(r"<!--.*?-->", "", source, flags=re.S)
        source = re.sub(r'(<svg\b[^>]*?)\s+width="[^"]*"', r"\1", source, count=1)
        source = re.sub(r'(<svg\b[^>]*?)\s+height="[^"]*"', r"\1", source, count=1)
        source = source.replace("<svg ", f'<svg width="{CELL}" height="{CELL}" ', 1)
        cells.append(f'<div class="cell">{source}</div>')
    rows = (len(names) + COLS - 1) // COLS
    doc = f"""<!doctype html>
<html><meta charset="utf-8"><title>normalize</title>
<style>
  html, body {{ margin: 0; padding: 0; background: #ffffff; }}
  .grid {{ display: grid; grid-template-columns: repeat({COLS}, {CELL}px); }}
  .cell {{ width: {CELL}px; height: {CELL}px; overflow: hidden; }}
  .cell svg {{ display: block; }}
</style>
<div class="grid">{''.join(cells)}</div>
</html>
"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(doc)
    return COLS * CELL, rows * CELL


def render(names, directory, path):
    """A contact sheet of `names` from `directory`, read back as grey levels."""
    width, height = build_sheet(names, directory, path)
    png = os.path.splitext(path)[0] + ".png"
    subprocess.run(
        [
            CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--force-device-scale-factor=1", "--default-background-color=FFFFFFFF",
            f"--screenshot={png}", f"--window-size={width},{height}",
            "file:///" + path.replace("\\", "/"),
        ],
        check=True, capture_output=True, timeout=300,
    )
    return np.asarray(Image.open(png).convert("L")).astype(np.float64)


def cell_of(sheet, index, viewbox):
    """Ink coverage for one cell, and pixels per viewBox unit."""
    row, col = index // COLS, index % COLS
    cell = sheet[row * CELL:(row + 1) * CELL, col * CELL:(col + 1) * CELL]
    coverage = 1.0 - cell / 255.0
    coverage[coverage < 0.02] = 0.0
    _, _, width, height = viewbox
    return coverage, min(CELL / width, CELL / height)


# --------------------------------------------------------------------------- measuring


def measure(coverage, per_unit, origin=(0.0, 0.0)):
    """Ink box, visual weight and solidity, in viewBox units."""
    inside = coverage > LEVEL
    if not inside.any():
        return None
    rows, cols = np.nonzero(inside)
    y0, y1, x0, x1 = rows.min(), rows.max() + 1, cols.min(), cols.max() + 1
    distance = ndimage.distance_transform_edt(inside)[inside]
    weight = 4.0 * float(distance.mean()) / per_unit
    # Everything below is in viewBox units, and the glyph is about to be scaled so its ink
    # measures LIVE on the canvas. The mass test has to ask the same question of the
    # *finished* glyph, or a 512-unit source and a 24-unit source are not comparable.
    settle = LIVE / (max(x1 - x0, y1 - y0) / per_unit)
    return {
        "x0": (x0 + origin[0]) / per_unit, "x1": (x1 + origin[0]) / per_unit,
        "y0": (y0 + origin[1]) / per_unit, "y1": (y1 + origin[1]) / per_unit,
        "w": (x1 - x0) / per_unit, "h": (y1 - y0) / per_unit,
        "long": max(x1 - x0, y1 - y0) / per_unit,
        "weight": weight,
        "scale": settle,
        # How much ink ends up further from the edge than any stroke could reach.
        "mass": float((distance * settle / per_unit > STROKE).mean()),
    }


def offset_outline(coverage, radius_px):
    """Move the half-coverage outline outward by `radius_px` (negative moves it in).

    A signed distance field rather than a morphological filter: the offset is a real number
    of pixels either way, and rebuilding the ramp afterwards is what lets the tracer still
    place the edge between two pixels instead of on one.
    """
    if abs(radius_px) < 0.15:
        return coverage
    inside = coverage > LEVEL
    if not inside.any():
        return coverage
    signed = (ndimage.distance_transform_edt(inside)
              - ndimage.distance_transform_edt(~inside))
    return np.clip(LEVEL + signed + radius_px, 0.0, 1.0)


# --------------------------------------------------------------------------- the routes


def probe_body(name, factor):
    """The artwork with its stroke weights multiplied by `factor` and nothing else changed,
    so one render says how big the ink becomes once the pen has changed."""
    attrs, inner = source_parts(name)
    viewbox = attrs.get("viewBox", f"0 0 {SIDE:g} {SIDE:g}")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}" '
            f'{style_of(attrs)}>{scaled_widths(inner, factor)}</svg>\n')


def vector_body(name, scale, probe, rewrite):
    """The artwork, scaled to the live area, at the one stroke weight."""
    attrs, inner = source_parts(name)
    if rewrite:
        heaviest = widest_stroke(name)
        inner = scaled_widths(inner, STROKE / (heaviest * scale))
    center_x = (probe["x0"] + probe["x1"]) / 2.0
    center_y = (probe["y0"] + probe["y1"]) / 2.0
    tx = SIDE / 2.0 - center_x * scale
    ty = SIDE / 2.0 - center_y * scale
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIDE:g}" height="{SIDE:g}" '
        f'viewBox="0 0 {SIDE:g} {SIDE:g}" {style_of(attrs)}>\n'
        f'  <g transform="translate({tx:.4f},{ty:.4f}) scale({scale:.6f})">{inner}</g>\n'
        f"</svg>\n"
    )


def mask_body(name, coverage, per_unit, shape):
    """Trace the glyph back out of its own bitmap at the target weight."""
    scale = LIVE / shape["long"]
    wanted = STROKE / scale                      # the width we need, in viewBox units
    shift = (wanted - shape["weight"]) / 2.0     # half of it on each side of the outline
    limit = (GROW_LIMIT if shift > 0 else SHRINK_LIMIT) / scale
    applied = max(-limit, min(limit, shift)) * per_unit
    adjusted = offset_outline(coverage, applied)

    # Tracing only the ink saves a third of a million cell visits per icon.
    inside = adjusted > LEVEL
    rows, cols = np.nonzero(inside)
    pad = 4
    y0 = max(0, rows.min() - pad)
    x0 = max(0, cols.min() - pad)
    y1 = min(adjusted.shape[0], rows.max() + 1 + pad)
    x1 = min(adjusted.shape[1], cols.max() + 1 + pad)
    window = adjusted[y0:y1, x0:x1]

    epsilon = max(0.35, CELL / 1500.0)
    loops = []
    for loop in trace_logos.trace(_as_image(window)):
        shifted = [(x + x0, y + y0) for x, y in loop]
        simplified = trace_logos.simplify(shifted, epsilon)
        if len(simplified) >= 3:
            loops.append(simplified)
    if not loops:
        raise SystemExit(f"{name}: traced to nothing")

    points = [p for loop in loops for p in loop]
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    low_x, high_x = min(xs), max(xs)
    low_y, high_y = min(ys), max(ys)
    span_x = (high_x - low_x) / per_unit * scale
    span_y = (high_y - low_y) / per_unit * scale
    placed = [
        [((p[0] - low_x) / per_unit * scale + (SIDE - span_x) / 2.0,
          (p[1] - low_y) / per_unit * scale + (SIDE - span_y) / 2.0)
         for p in loop]
        for loop in loops
    ]
    body = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIDE:g}" height="{SIDE:g}" '
        f'viewBox="0 0 {SIDE:g} {SIDE:g}" fill="#000000" fill-rule="evenodd">\n'
        f"  <path d=\"{trace_logos.to_path(placed)}\"/>\n"
        f"</svg>\n"
    )
    return body, sum(len(loop) for loop in placed), applied / per_unit


def _as_image(array):
    return Image.fromarray(np.round(array * 255.0).astype(np.uint8), mode="L")


def write_preview(names):
    """A sheet and a browser page, so the set can be judged without building the app."""
    sheet = render(names, OUT_DIR, os.path.join(WORK_DIR, "sheet-preview.html"))
    image = _as_image(sheet)
    image.resize((image.width // 2, image.height // 2), Image.LANCZOS).save(
        os.path.join(HERE, "preview.png"))

    rows = (len(names) + COLS - 1) // COLS
    figures = []
    for name in names:
        small = "".join(
            f'<img src="normalized/{name}.svg" width="{size}" height="{size}">'
            for size in (24, 32, 40))
        figures.append(
            f'<figure><img class="art" src="normalized/{name}.svg" '
            f'width="72" height="72"><figcaption>{name}</figcaption>'
            f'<div class="sizes">{small}</div></figure>')
    doc = f"""<!doctype html>
<html lang="zh"><meta charset="utf-8"><title>PocketLedger 图标</title>
<style>
  body {{ margin: 0; padding: 32px; background: #fafafa; color: #1b1b1b;
         font: 14px/1.5 system-ui, "Microsoft YaHei", sans-serif; }}
  h1 {{ font-size: 20px; margin: 0 0 4px; }}
  p  {{ margin: 0 0 28px; color: #666; max-width: 60em; }}
  code {{ background: #eee; padding: 1px 4px; border-radius: 4px; }}
  .grid {{ display: grid; gap: 12px;
           grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }}
  figure {{ margin: 0; padding: 14px; background: #fff; border: 1px solid #e6e6e6;
            border-radius: 12px; text-align: center; }}
  figcaption {{ margin-top: 8px; font-size: 12px; color: #888; }}
  .sizes {{ display: flex; gap: 10px; align-items: flex-end; justify-content: center;
            margin-top: 10px; padding-top: 10px; border-top: 1px dashed #eee; }}
  .sizes img {{ opacity: .75; }}
</style>
<h1>PocketLedger 图标</h1>
<p>全部由 <code>normalized/</code> 生成：墨迹长边统一为 {LIVE:g}/100，
线条基准粗细统一为 {STROKE:g}/100，形状按原样保留。
下方每格依次是 72 / 24 / 32 / 40 像素，用来检查小尺寸下是否依然清晰。</p>
<div class="grid">{''.join(figures)}</div>
</html>
"""
    with open(os.path.join(HERE, "index.html"), "w", encoding="utf-8",
              newline="\n") as handle:
        handle.write(doc)
    print(f"preview: icons/preview.png and icons/index.html ({rows} rows)")


# ---------------------------------------------------------------------------------- main


def plan_route(shape, rewrite):
    """`mask` only where a filled outline is off weight; `vector` everywhere else.

    A glyph whose ink is carried by strokes is always solved in vector, because the stroke
    width *is* the weight and re-issuing it is exact. A filled outline has the width baked
    into its shape, so the only way to move it is to redraw it -- which is only worth the
    tracing error when the difference is one you would notice, and only allowed to pull the
    outline inwards when there is no solid mass in the glyph to deform.
    """
    if rewrite:
        return "vector"
    off = shape["settled"] - STROKE
    if off < -LIGHT_BY:
        return "mask"
    if off > LIGHT_BY and shape["mass"] < MASS_LIMIT:
        return "mask"
    return "vector"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    os.makedirs(WORK_DIR, exist_ok=True)
    names = icon_names()
    boxes = {name: viewbox_of(name) for name in names}

    print(f"rendering {len(names)} icons from svg/ ...")
    sheet = render(names, SOURCE_DIR, os.path.join(WORK_DIR, "sheet-normalize.html"))
    source_shapes, routes, rewrite = {}, {}, {}
    for index, name in enumerate(names):
        coverage, per_unit = cell_of(sheet, index, boxes[name])
        shape = measure(coverage, per_unit)
        shape["settled"] = shape["weight"] * LIVE / shape["long"]
        source_shapes[name] = shape
        widest = widest_stroke(name)
        rewrite[name] = widest > 0 and widest / shape["weight"] > STROKE_SHARE
        routes[name] = plan_route(shape, rewrite[name])

    # One more render, of the icons whose strokes changed, decides the scale each needs:
    # the pen is settled first, and only then can the ink box it draws be measured. The two
    # depend on each other -- a thinner pen draws a smaller glyph, which asks for a larger
    # scale, which thins the pen again -- so the answer is iterated, and converges because
    # the coupling is weak (the stroke is under a ninth of the live box).
    print("rendering the stroke-corrected probes ...")
    probed_names = [name for name in names if routes[name] == "vector" and rewrite[name]]
    os.makedirs(PROBE_DIR, exist_ok=True)
    scale_guess = {name: LIVE / source_shapes[name]["long"] for name in probed_names}
    probe_shapes = {}
    for round_number in range(3):
        for name in probed_names:
            heaviest = widest_stroke(name)
            factor = STROKE / (heaviest * scale_guess[name])
            with open(os.path.join(PROBE_DIR, name + ".svg"), "w",
                      encoding="utf-8", newline="\n") as handle:
                handle.write(probe_body(name, factor))
        probe_sheet = render(probed_names, PROBE_DIR,
                             os.path.join(WORK_DIR, "sheet-probe.html"))
        drift = 0.0
        for index, name in enumerate(probed_names):
            shape = measure(*cell_of(probe_sheet, index, boxes[name]))
            probe_shapes[name] = shape
            settled = LIVE / shape["long"]
            drift = max(drift, abs(settled - scale_guess[name]) / settled)
            scale_guess[name] = settled
        print(f"  pass {round_number + 1}: largest scale change {drift * 100:.2f}%")

    print(f"\n{'icon':<16}{'ink w':>7}{'ink h':>7}{'long':>7}{'weight':>8}"
          f"{'settled':>9}{'mass':>7}{'route':>8}{'pen':>6}{'after long':>11}{'after wt':>9}")
    print("-" * 96)
    plans = {}
    for name in names:
        shape = source_shapes[name]
        route = routes[name]
        if route == "vector":
            probe = probe_shapes.get(name, shape)
            scale = LIVE / probe["long"]
            after_long, after_weight = LIVE, probe["weight"] * scale
        else:
            probe = shape
            scale = LIVE / shape["long"]
            shift = (STROKE / scale - shape["weight"]) / 2.0
            limit = (GROW_LIMIT if shift > 0 else SHRINK_LIMIT) / scale
            applied = max(-limit, min(limit, shift))
            after_long = LIVE
            after_weight = (shape["weight"] + 2.0 * applied) * scale
        plans[name] = (route, scale, probe)
        print(f"{name:<16}{shape['w']:>7.1f}{shape['h']:>7.1f}{shape['long']:>7.1f}"
              f"{shape['weight']:>8.2f}{shape['settled']:>9.2f}{shape['mass']:>7.3f}"
              f"{route:>8}{'yes' if rewrite[name] else '-':>6}"
              f"{after_long:>11.1f}{after_weight:>9.2f}")

    if args.dry_run:
        return

    if os.path.isdir(OUT_DIR):
        shutil.rmtree(OUT_DIR)
    os.makedirs(OUT_DIR)
    print()
    for index, name in enumerate(names):
        route, scale, probe = plans[name]
        if route == "vector":
            body = vector_body(name, scale, probe, rewrite[name])
        else:
            coverage, per_unit = cell_of(sheet, index, boxes[name])
            body, points, moved = mask_body(name, coverage, per_unit, source_shapes[name])
            print(f"  {name:<16} mask  {points:>4} points, outline moved {moved:+.2f} units")
        with open(os.path.join(OUT_DIR, name + ".svg"), "w",
                  encoding="utf-8", newline="\n") as handle:
            handle.write(body)
    print(f"\nwrote {len(os.listdir(OUT_DIR))} icons to {OUT_DIR}")
    write_preview(names)


if __name__ == "__main__":
    main()
