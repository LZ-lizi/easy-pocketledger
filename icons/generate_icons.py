"""The original procedural icon set, kept as a reference.

This was the source of the whole set until the icons were redrawn by hand. `svg/` is now
**hand-made artwork and is never written by a script** -- this one included -- so its output
goes to `drawn/` instead, which nothing else reads. Running it is how you compare the old
geometry with what the set actually ships; it can no longer overwrite anything that matters.

The rest of the pipeline is:

    svg/  (hand-made)  ->  normalize_icons.py  ->  normalized/  ->  make_drawables.py
                                                                   ->  res/drawable/

Two entries here are the exception to "drawn": **微信支付 and 支付宝 are real brand marks**,
taken from the PNGs in `source/` and traced by `trace_logos.py` into `logos.json`.
Hand-fitting a path for a logo would be a guess at someone else's artwork, so the outline is
measured rather than drawn; this script only places the result on the same canvas, and
regenerating it means re-running the tracer over the sources.

Run:  python generate_icons.py
Writes drawn/*.svg, index.html and MANIFEST.md next to this file.
"""
import json
import math
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.join(HERE, "drawn")
LOGOS_PATH = os.path.join(HERE, "logos.json")

SIDE = 100.0
STROKE = 0.085          # Kotlin: w * 0.085f
THIN = 0.065            # Kotlin: w * 0.065f
INK = "#000000"         # placeholder; the app tints at runtime
TARGET = 80.0           # box a traced brand mark's longer side is scaled into

# ----------------------------------------------------------------- primitives


class Shape:
    """One drawn element: its markup, its stroke width, and where its centre line runs.

    The samples are what make the set's sizes checkable rather than eyeballed. A stroke
    sticks out half its width past the centre line it is drawn along, so an icon whose
    coordinates span 0.14..0.86 covers rather more than 72 units -- and comparing the
    *boxes* instead of the ink is how a set ends up with icons that are nominally the same
    size and visibly are not.
    """

    __slots__ = ("svg", "stroke", "samples")

    def __init__(self, svg, stroke, samples):
        self.svg = svg
        self.stroke = stroke        # stroke width in canvas units; None when filled
        self.samples = samples      # [(x, y), ...] on the centre line, canvas units

    def ink_box(self):
        """(x0, y0, x1, y1) of everything this shape paints, stroke included."""
        pad = (self.stroke or 0.0) / 2.0
        xs = [point[0] for point in self.samples]
        ys = [point[1] for point in self.samples]
        return min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad


def pt(x, y):
    """A fraction-of-canvas pair as canvas units."""
    return x * SIDE, y * SIDE


def corners(x, y, w, h):
    return [pt(x, y), pt(x + w, y), pt(x, y + h), pt(x + w, y + h)]


def n(v):
    """Fraction of the canvas -> SVG user units, trimmed."""
    out = round(v * SIDE, 3)
    return f"{out:g}"


def line(x1, y1, x2, y2, width):
    return Shape(
        f'<path d="M{n(x1)},{n(y1)} L{n(x2)},{n(y2)}" '
        f'stroke-width="{n(width)}"/>',
        width * SIDE,
        [pt(x1, y1), pt(x2, y2)],
    )


def rect(x, y, w, h, rx):
    return Shape(
        f'<rect x="{n(x)}" y="{n(y)}" width="{n(w)}" height="{n(h)}" '
        f'rx="{n(rx)}" stroke-width="{n(STROKE)}"/>',
        STROKE * SIDE,
        corners(x, y, w, h),
    )


def dot(cx, cy, r):
    """A filled circle, matching drawCircle without a style."""
    return Shape(
        f'<circle cx="{n(cx)}" cy="{n(cy)}" r="{n(r)}" stroke="none" fill="{INK}"/>',
        None,
        corners(cx - r, cy - r, 2 * r, 2 * r),
    )


def ring(cx, cy, r, width):
    """A stroked circle, matching drawCircle(style = Stroke(width))."""
    return Shape(
        f'<circle cx="{n(cx)}" cy="{n(cy)}" r="{n(r)}" stroke-width="{n(width)}"/>',
        width * SIDE,
        corners(cx - r, cy - r, 2 * r, 2 * r),
    )


def arc(cx, cy, rx, ry, start, sweep, width):
    """A stroked elliptical arc, matching drawArc(useCenter = false, style = Stroke).

    Compose measures angles clockwise from 3 o'clock with y pointing down, which is the
    same convention as an SVG arc traversed with sweep-flag 1 -- so the angles transfer
    directly and only the endpoints have to be computed.
    """
    a1 = math.radians(start)
    a2 = math.radians(start + sweep)
    x1, y1 = cx + rx * math.cos(a1), cy + ry * math.sin(a1)
    x2, y2 = cx + rx * math.cos(a2), cy + ry * math.sin(a2)
    large = 1 if abs(sweep) > 180 else 0
    flag = 1 if sweep >= 0 else 0
    # Sampled every 5 degrees: an arc's extremes are not at its endpoints, so measuring a
    # semicircle by its ends would under-report the box by a whole radius.
    samples = [
        pt(cx + rx * math.cos(math.radians(start + t)),
           cy + ry * math.sin(math.radians(start + t)))
        for t in range(0, int(abs(sweep)) + 1, 5)
    ]
    samples.append(pt(x2, y2))
    return Shape(
        f'<path d="M{n(x1)},{n(y1)} A{n(rx)},{n(ry)} 0 {large} {flag} {n(x2)},{n(y2)}" '
        f'stroke-width="{n(width)}"/>',
        width * SIDE,
        samples,
    )


def poly(points, *, closed=False, filled=False, width=STROKE):
    d = "M" + " L".join(f"{n(x)},{n(y)}" for x, y in points)
    if closed:
        d += " Z"
    samples = [pt(x, y) for x, y in points]
    if filled:
        return Shape(f'<path d="{d}" stroke="none" fill="{INK}"/>', None, samples)
    return Shape(f'<path d="{d}" stroke-width="{n(width)}"/>', width * SIDE, samples)


_LOGOS = None


def logo(name):
    """A brand mark traced from a PNG; see `trace_logos.py`.

    `fill-rule="evenodd"` is what makes the knocked-out parts of a logo -- the check inside
    the WeChat bubble, the counter inside 支 -- come out as holes instead of being filled
    in. Both attributes are set explicitly because the document wrapper sets `fill="none"`
    and a stroke colour for the drawn icons, and neither applies here.
    """
    global _LOGOS
    if _LOGOS is None:
        with open(LOGOS_PATH, encoding="utf-8") as fh:
            _LOGOS = json.load(fh)
    entry = _LOGOS[name]
    path = entry["path"]
    samples = [
        (float(x), float(y))
        for x, y in re.findall(r"(-?[\d.]+),(-?[\d.]+)", path)
    ]
    return Shape(
        f'<path d="{path}" stroke="none" fill="{INK}" fill-rule="evenodd"/>',
        None,
        samples,
    )


def logo_note(name):
    """One line about where a traced mark came from, for the manifest."""
    if _LOGOS is None:
        logo(name)
    entry = _LOGOS[name]
    return (f'由 `{entry["source"]}``（{entry["sourceSize"][0]}×{entry["sourceSize"][1]}）'
            f'描摹，{entry["contours"]} 条轮廓 / {entry["points"]} 点')


# ------------------------------------------------------- the one common size

# The band every icon's ink is fitted into, measured on its longer side.
#
# Why a band and not one number: an exact common size sounds like "uniform" and looks
# wrong. Scaling to a single box multiplies the stroke with the geometry, so a chevron
# (56.5 units, a deliberately small glyph that sits inside a list row) would be blown up
# to the size of the 银行卡 icon and read as heavier than everything it sits beside. What
# actually needed fixing was the spread, not the variety:
#
#   before:  long side ran 52.0 .. 88.5, and 校园 at 88.5 nearly touched the canvas edge
#   after:   everything inside [64, 84], aspect ratio untouched
#
# Anything already inside the band is left exactly as drawn -- only centred. Icons that
# are small on purpose stay small; icons that overflowed come in.
FIT_LONG_MIN = 64.0
FIT_LONG_MAX = 84.0


def ink_box(shapes):
    boxes = [shape.ink_box() for shape in shapes]
    return (
        min(b[0] for b in boxes), min(b[1] for b in boxes),
        max(b[2] for b in boxes), max(b[3] for b in boxes),
    )


def fit_for(box):
    """How to place one icon's ink on the shared canvas: (scale, translate x/y)."""
    x0, y0, x1, y1 = box
    long_side = max(x1 - x0, y1 - y0)
    if long_side > FIT_LONG_MAX:
        scale = FIT_LONG_MAX / long_side
    elif 0 < long_side < FIT_LONG_MIN:
        scale = FIT_LONG_MIN / long_side
    else:
        scale = 1.0
    tx = SIDE / 2 - scale * (x0 + x1) / 2
    ty = SIDE / 2 - scale * (y0 + y1) / 2
    return scale, tx, ty


def placed(body, scale, tx, ty):
    """Wrap a body in the shared placement transform, or leave it alone when that is 1:1."""
    if abs(scale - 1.0) < 0.005 and abs(tx) < 0.05 and abs(ty) < 0.05:
        return body
    inner = "\n".join("    " + line for line in body.splitlines())
    return (
        f'<g transform="translate({tx:.2f},{ty:.2f}) scale({scale:.4f})">\n'
        f"{inner}\n"
        f"  </g>"
    )


# --------------------------------------------------------------------- icons
# (enum name, iconKeys, [operations])  -- operations in the Kotlin's own order.

ICONS = [
    # ---- Navigation
    ("LIST", [], [
        line(0.22, 0.28, 0.78, 0.28, STROKE),
        line(0.22, 0.50, 0.78, 0.50, STROKE),
        line(0.22, 0.72, 0.78, 0.72, STROKE),
    ]),
    ("CHART", [], [
        line(0.28, 0.80, 0.28, 0.44, 0.13),
        line(0.50, 0.80, 0.50, 0.22, 0.13),
        line(0.72, 0.80, 0.72, 0.34, 0.13),
    ]),
    ("CALENDAR", ["calendar"], [
        rect(0.14, 0.26, 0.72, 0.58, 0.11),
        line(0.14, 0.44, 0.86, 0.44, THIN),
        line(0.34, 0.16, 0.34, 0.30, THIN),
        line(0.66, 0.16, 0.66, 0.30, THIN),
        dot(0.34, 0.58, 0.045), dot(0.50, 0.58, 0.045), dot(0.66, 0.58, 0.045),
        dot(0.34, 0.72, 0.045), dot(0.50, 0.72, 0.045), dot(0.66, 0.72, 0.045),
    ]),
    ("SEARCH", [], [
        ring(0.44, 0.42, 0.26, STROKE),
        line(0.63, 0.61, 0.82, 0.80, STROKE),
    ]),
    ("WALLET", ["wallet"], [
        rect(0.14, 0.26, 0.72, 0.48, 0.12),
        dot(0.70, 0.50, 0.07),
    ]),
    ("PERSON", [], [
        dot(0.50, 0.33, 0.16),
        arc(0.50, 0.80, 0.32, 0.28, 200, 140, STROKE),
    ]),
    ("PLUS", [], [
        line(0.50, 0.24, 0.50, 0.76, STROKE * 1.25),
        line(0.24, 0.50, 0.76, 0.50, STROKE * 1.25),
    ]),
    ("SETTINGS", [], [
        line(0.20, 0.30, 0.80, 0.30, THIN), dot(0.66, 0.30, 0.085),
        line(0.20, 0.50, 0.80, 0.50, THIN), dot(0.36, 0.50, 0.085),
        line(0.20, 0.70, 0.80, 0.70, THIN), dot(0.58, 0.70, 0.085),
    ]),
    ("CHEVRON_RIGHT", [], [
        line(0.40, 0.26, 0.62, 0.50, STROKE),
        line(0.62, 0.50, 0.40, 0.74, STROKE),
    ]),
    ("ELLIPSIS", [], [
        dot(0.26, 0.50, 0.085),
        dot(0.50, 0.50, 0.085),
        dot(0.74, 0.50, 0.085),
    ]),

    # ---- Expense 大类
    ("FOOD", ["food"], [
        arc(0.50, 0.58, 0.32, 0.22, 0, 180, STROKE),
        line(0.12, 0.36, 0.88, 0.36, STROKE),
    ]),
    ("TRANSPORT", ["transport"], [
        rect(0.14, 0.40, 0.72, 0.28, 0.09),
        poly([(0.30, 0.40), (0.40, 0.26), (0.60, 0.26), (0.70, 0.40)]),
        dot(0.30, 0.74, 0.07),
        dot(0.70, 0.74, 0.07),
    ]),
    ("SHOPPING", ["shopping"], [
        rect(0.22, 0.36, 0.56, 0.44, 0.09),
        arc(0.50, 0.33, 0.14, 0.15, 180, 180, THIN),
    ]),
    ("HOME", ["home"], [
        poly([(0.50, 0.20), (0.86, 0.48), (0.14, 0.48)], closed=True),
        line(0.26, 0.48, 0.26, 0.80, STROKE),
        line(0.74, 0.48, 0.74, 0.80, STROKE),
        line(0.26, 0.80, 0.74, 0.80, STROKE),
    ]),
    ("COMMS", ["comms"], [
        rect(0.32, 0.16, 0.36, 0.68, 0.09),
        line(0.43, 0.73, 0.57, 0.73, THIN),
    ]),
    ("STUDY", ["study"], [
        rect(0.18, 0.26, 0.64, 0.50, 0.07),
        line(0.50, 0.26, 0.50, 0.76, THIN),
    ]),
    ("CAMPUS", ["campus"], [
        poly([(0.50, 0.22), (0.90, 0.42), (0.50, 0.62), (0.10, 0.42)], closed=True),
        line(0.76, 0.47, 0.76, 0.72, THIN),
    ]),
    ("FUN", ["fun"], [
        poly([(0.34, 0.24), (0.78, 0.50), (0.34, 0.76)], closed=True, filled=True),
    ]),
    ("MEDICAL", ["medical"], [
        line(0.50, 0.20, 0.50, 0.80, 0.16),
        line(0.20, 0.50, 0.80, 0.50, 0.16),
    ]),
    ("GIFT", ["gift"], [
        rect(0.18, 0.42, 0.64, 0.40, 0.06),
        line(0.12, 0.42, 0.88, 0.42, STROKE),
        line(0.50, 0.42, 0.50, 0.82, THIN),
        line(0.50, 0.42, 0.50, 0.24, THIN),
    ]),
    ("FINANCE", ["finance"], [
        ring(0.50, 0.50, 0.30, STROKE),
        line(0.50, 0.32, 0.50, 0.68, THIN),
    ]),
    ("WORK", ["work"], [
        rect(0.14, 0.38, 0.72, 0.42, 0.08),
        arc(0.50, 0.37, 0.12, 0.15, 180, 180, THIN),
    ]),
    ("PET", ["pet"], [
        dot(0.30, 0.34, 0.070),
        dot(0.50, 0.27, 0.070),
        dot(0.70, 0.34, 0.070),
        dot(0.50, 0.66, 0.155),
    ]),
    ("OTHER", ["other"], [
        dot(0.28, 0.50, 0.070),
        dot(0.50, 0.50, 0.070),
        dot(0.72, 0.50, 0.070),
    ]),

    # ---- Income and accounts
    ("INCOME", ["income"], [
        line(0.50, 0.20, 0.50, 0.58, STROKE),
        line(0.36, 0.45, 0.50, 0.59, STROKE),
        line(0.64, 0.45, 0.50, 0.59, STROKE),
        line(0.22, 0.78, 0.78, 0.78, STROKE),
    ]),
    ("CASH", ["cash"], [
        rect(0.12, 0.32, 0.76, 0.36, 0.07),
        ring(0.50, 0.50, 0.09, THIN),
    ]),
    ("CARD", ["card", "prepaid"], [
        rect(0.12, 0.30, 0.76, 0.40, 0.08),
        line(0.12, 0.44, 0.88, 0.44, 0.10),
        line(0.24, 0.60, 0.46, 0.60, THIN),
    ]),
    ("BANK", ["bank"], [
        poly([(0.50, 0.18), (0.88, 0.42), (0.12, 0.42)], closed=True),
        line(0.28, 0.48, 0.28, 0.74, THIN),
        line(0.50, 0.48, 0.50, 0.74, THIN),
        line(0.72, 0.48, 0.72, 0.74, THIN),
        line(0.14, 0.80, 0.86, 0.80, STROKE),
    ]),
    ("ALIPAY", ["alipay"], [
        logo("alipay"),
    ]),
    ("WECHAT", ["wechat"], [
        logo("wechat"),
    ]),

    # ---- Fallback
    ("TAG", [], [
        poly([(0.52, 0.18), (0.86, 0.52), (0.52, 0.86), (0.16, 0.50), (0.16, 0.18)],
             closed=True),
        dot(0.30, 0.31, 0.06),
    ]),
]

SECTION_OF = {
    "LIST": "导航", "CHART": "导航", "CALENDAR": "导航", "SEARCH": "导航",
    "WALLET": "导航", "PERSON": "导航", "PLUS": "导航", "SETTINGS": "导航",
    "CHEVRON_RIGHT": "导航", "ELLIPSIS": "导航",
    "FOOD": "支出大类", "TRANSPORT": "支出大类", "SHOPPING": "支出大类",
    "HOME": "支出大类", "COMMS": "支出大类", "STUDY": "支出大类",
    "CAMPUS": "支出大类", "FUN": "支出大类", "MEDICAL": "支出大类",
    "GIFT": "支出大类", "FINANCE": "支出大类", "WORK": "支出大类",
    "PET": "支出大类", "OTHER": "支出大类",
    "INCOME": "收入与账户", "CASH": "收入与账户", "CARD": "收入与账户",
    "BANK": "收入与账户", "ALIPAY": "收入与账户", "WECHAT": "收入与账户",
    "TAG": "兜底",
}

# The two entries whose path is traced from a real brand mark rather than drawn.
TRACED = {"ALIPAY": "alipay", "WECHAT": "wechat"}

# The launcher icon is already an Android vector; transcribed here so the folder holds
# the complete set in one format. Its canvas is 108 x 108, not the icons' 100 x 100.
LAUNCHER_BG = """<rect x="0" y="0" width="108" height="108" fill="#2F6BFF"/>
  <circle cx="54" cy="10" r="44" fill="#3D77FF"/>
  <circle cx="54" cy="14" r="30" fill="#5A8CFF"/>"""

LAUNCHER_FG = """<path d="M38,30 L54,50 L70,30" fill="none" stroke="#FFFFFF" stroke-width="7"
        stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M54,50 L54,78" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round"/>
  <path d="M39,57 L69,57" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round"/>
  <path d="M39,66 L69,66" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round"/>"""


def file_name(enum_name):
    return enum_name.lower().replace("_", "-")


def svg_document(body, size=100):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 {size} {size}" fill="none" stroke="{INK}" '
        f'stroke-linecap="round" stroke-linejoin="round">\n'
        f"  {body}\n"
        f"</svg>\n"
    )


def main():
    os.makedirs(SVG_DIR, exist_ok=True)

    written = []
    changed = []
    for enum_name, keys, ops in ICONS:
        box = ink_box(ops)
        scale, tx, ty = fit_for(box)
        body = placed("\n  ".join(shape.svg for shape in ops), scale, tx, ty)
        path = os.path.join(SVG_DIR, file_name(enum_name) + ".svg")
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(svg_document(body))
        long_side = round(max(box[2] - box[0], box[3] - box[1]), 1)
        after = round(long_side * scale, 1)
        written.append((enum_name, keys, long_side, after))
        if abs(scale - 1.0) >= 0.005:
            changed.append((enum_name, long_side, after, scale))

    print(f"{len(ICONS)} icons; {len(changed)} rescaled to land in "
          f"[{FIT_LONG_MIN:g}, {FIT_LONG_MAX:g}]")
    for enum_name, before, after, scale in sorted(changed, key=lambda r: r[3]):
        print(f"  {enum_name:<14} {before:>5} -> {after:>5}  (x{scale:.3f})")

    with open(os.path.join(SVG_DIR, "launcher-background.svg"), "w",
              encoding="utf-8", newline="\n") as fh:
        fh.write(svg_document(LAUNCHER_BG, 108))
    with open(os.path.join(SVG_DIR, "launcher-foreground.svg"), "w",
              encoding="utf-8", newline="\n") as fh:
        fh.write(svg_document(LAUNCHER_FG, 108))
    # Composite, so the mark can be seen on the field it was designed against.
    with open(os.path.join(SVG_DIR, "launcher.svg"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(
            '<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" '
            'viewBox="0 0 108 108">\n'
            f"  {LAUNCHER_BG}\n"
            f"  {LAUNCHER_FG}\n"
            "</svg>\n"
        )

    # ---- preview page
    rows = []
    last_section = None
    for enum_name, keys, long_side, after in written:
        section = SECTION_OF[enum_name]
        if section != last_section:
            rows.append(f'<h2>{section}</h2>')
            last_section = section
        key_text = " / ".join(keys) if keys else "—"
        name = file_name(enum_name)
        rows.append(
            f'<figure class="card">'
            f'<div class="strip">'
            f'<div class="tile light"><img src="svg/{name}.svg" alt="{name}"></div>'
            f'<div class="tile dark"><img src="svg/{name}.svg" alt=""></div>'
            f"</div>"
            f'<figcaption><b>{name}.svg</b><span>{key_text} · 长边 {after:g}</span></figcaption>'
            f"</figure>"
        )

    html = f"""<!doctype html>
<html lang="zh">
<meta charset="utf-8">
<title>记账本 图标集</title>
<style>
  body {{ font: 14px/1.5 system-ui, "MiSans", sans-serif; margin: 32px; color: #111; }}
  h1 {{ font-size: 20px; margin: 0 0 4px; }}
  h2 {{ font-size: 13px; font-weight: 600; color: #666; margin: 28px 0 10px; }}
  .note {{ color: #666; margin: 0 0 8px; max-width: 70em; }}
  .grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
           gap: 12px; }}
  .card {{ margin: 0; border: 1px solid #e5e5e5; border-radius: 10px; overflow: hidden; }}
  .strip {{ display: flex; }}
  /* The dark half only inverts the icon, never the tile -- inverting the tile would
     flip its background to near-white and stop simulating a dark surface. */
  .tile {{ flex: 1; display: flex; align-items: center; justify-content: center; height: 72px; }}
  .tile.light {{ background: #ffffff; }}
  .tile.dark {{ background: #17181c; }}
  .tile.dark img {{ filter: invert(1); }}
  .tile img {{ width: 48px; height: 48px; }}
  .tile.big img {{ width: 76px; height: 76px; }}
  figcaption {{ border-top: 1px solid #eee; padding: 8px 10px; display: flex;
                flex-direction: column; }}
  figcaption b {{ font-size: 12px; }}
  figcaption span {{ font-size: 11px; color: #888; }}
</style>
<h1>记账本 图标集</h1>
<p class="note">共 {len(written)} 个界面图标 + 3 个启动图标。每个卡片左半为浅色背景、右半为深色背景；
深色一侧用 <code>filter: invert(1)</code> 把源文件的黑色描边反相为白色，仅为对照查看 ——
源文件本身都是黑色描边，实际由 App 在运行时着色。</p>
<div class="grid">
{chr(10).join(rows)}
</div>
<h2>启动图标</h2>
<div class="grid">
  <figure class="card"><div class="strip"><div class="tile light big"><img src="svg/launcher.svg" alt="launcher"></div></div><figcaption><b>launcher.svg</b><span>合成预览</span></figcaption></figure>
  <figure class="card"><div class="strip"><div class="tile light big"><img src="svg/launcher-background.svg" alt=""></div></div><figcaption><b>launcher-background.svg</b><span>背景</span></figcaption></figure>
  <figure class="card"><div class="strip"><div class="tile dark big"><img src="svg/launcher-foreground.svg" alt=""></div></div><figcaption><b>launcher-foreground.svg</b><span>前景 ￥</span></figcaption></figure>
</div>
</html>
"""
    with open(os.path.join(SVG_DIR, "index.html"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(html)

    # ---- generated manifest
    lines = [
        "# 图标清单（由 `generate_icons.py` 生成，请勿手改）",
        "",
        f"界面图标 **{len(written)}** 个，启动图标 3 个（背景 / 前景 / 合成）。",
        "",
        "「长边」是图标实际着色范围（含描边宽度的一半）在 100×100 画布上的较长边。",
        f"全部落在 {FIT_LONG_MIN:g}–{FIT_LONG_MAX:g} 之间：超出上限的缩进来，"
        "低于下限的放大到下限，其余原样居中。",
        "",
        "| 文件 | LedgerIcon | iconKey | 分组 | 长边 |",
        "| --- | --- | --- | --- | --- |",
    ]
    for enum_name, keys, _, after in written:
        lines.append(
            f"| `svg/{file_name(enum_name)}.svg` | `{enum_name}` | "
            f"{' / '.join('`' + k + '`' for k in keys) if keys else '—'} | "
            f"{SECTION_OF[enum_name]} | {after:g} |"
        )
    lines += [
        "| `svg/launcher-background.svg` | — | — | 启动图标 | — |",
        "| `svg/launcher-foreground.svg` | — | — | 启动图标 | — |",
        "| `svg/launcher.svg` | — | — | 启动图标（合成） | — |",
        "",
        "## 两个品牌标识不是手绘的",
        "",
        "其余图标都是从 `LedgerIcon.kt` 的绘图代码转写的（描边 + 坐标 = 画布边长的比例），",
        "下面两个是真实品牌标识，描摹自 `source/` 里的 PNG，因此是**填充路径**而非描边，",
        "多出来的部分（微信气泡里的对勾、支付宝「支」字内部的封闭空间）用 `fill-rule=\"evenodd\"` 挖空。",
        "",
    ]
    for enum_name, key in TRACED.items():
        lines.append(f"- `svg/{file_name(enum_name)}.svg`（`{enum_name}`）：{logo_note(key)}")
    lines.append("")
    with open(os.path.join(SVG_DIR, "MANIFEST.md"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))

    print(f"wrote {len(written)} ui icons + 3 launcher icons to {SVG_DIR}")


if __name__ == "__main__":
    main()
