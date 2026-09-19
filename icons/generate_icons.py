"""Emit the app's icon set as SVG.

The icons in `LedgerIcon.kt` are drawn procedurally with Compose `DrawScope`, so there
are no vector files to copy -- this script is the transcription. Every coordinate below
is the same fraction of the canvas side that the Kotlin uses, so the two can be diffed by
eye; the only change of units is the multiply by `SIDE` (the SVG viewBox is 100 x 100).

Run:  python generate_icons.py
Writes svg/*.svg, index.html and MANIFEST.md next to this file.
"""
import math
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.join(HERE, "svg")

SIDE = 100.0
STROKE = 0.085          # Kotlin: w * 0.085f
THIN = 0.065            # Kotlin: w * 0.065f
INK = "#000000"         # placeholder; the app tints at runtime

# ----------------------------------------------------------------- primitives


def n(v):
    """Fraction of the canvas -> SVG user units, trimmed."""
    out = round(v * SIDE, 3)
    return f"{out:g}"


def line(x1, y1, x2, y2, width):
    return (
        f'<path d="M{n(x1)},{n(y1)} L{n(x2)},{n(y2)}" '
        f'stroke-width="{n(width)}"/>'
    )


def rect(x, y, w, h, rx):
    return (
        f'<rect x="{n(x)}" y="{n(y)}" width="{n(w)}" height="{n(h)}" '
        f'rx="{n(rx)}" stroke-width="{n(STROKE)}"/>'
    )


def dot(cx, cy, r):
    """A filled circle, matching drawCircle without a style."""
    return f'<circle cx="{n(cx)}" cy="{n(cy)}" r="{n(r)}" stroke="none" fill="{INK}"/>'


def ring(cx, cy, r, width):
    """A stroked circle, matching drawCircle(style = Stroke(width))."""
    return f'<circle cx="{n(cx)}" cy="{n(cy)}" r="{n(r)}" stroke-width="{n(width)}"/>'


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
    return (
        f'<path d="M{n(x1)},{n(y1)} A{n(rx)},{n(ry)} 0 {large} {flag} {n(x2)},{n(y2)}" '
        f'stroke-width="{n(width)}"/>'
    )


def poly(points, *, closed=False, filled=False, width=STROKE):
    d = "M" + " L".join(f"{n(x)},{n(y)}" for x, y in points)
    if closed:
        d += " Z"
    if filled:
        return f'<path d="{d}" stroke="none" fill="{INK}"/>'
    return f'<path d="{d}" stroke-width="{n(width)}"/>'


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
        rect(0.12, 0.28, 0.76, 0.44, 0.10),
        line(0.36, 0.42, 0.64, 0.42, THIN),
        line(0.50, 0.42, 0.50, 0.60, THIN),
        line(0.34, 0.58, 0.66, 0.58, THIN),
    ]),
    ("WECHAT", ["wechat"], [
        rect(0.10, 0.22, 0.56, 0.42, 0.14),
        rect(0.36, 0.42, 0.54, 0.38, 0.14),
        dot(0.28, 0.43, 0.045),
        dot(0.48, 0.43, 0.045),
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
    for enum_name, keys, ops in ICONS:
        body = "\n  ".join(ops)
        path = os.path.join(SVG_DIR, file_name(enum_name) + ".svg")
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(svg_document(body))
        written.append((enum_name, keys, ops))

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
    for enum_name, keys, _ in written:
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
            f'<figcaption><b>{name}.svg</b><span>{key_text}</span></figcaption>'
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
    with open(os.path.join(HERE, "index.html"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(html)

    # ---- generated manifest
    lines = [
        "# 图标清单（由 `generate_icons.py` 生成，请勿手改）",
        "",
        f"界面图标 **{len(written)}** 个，启动图标 3 个（背景 / 前景 / 合成）。",
        "",
        "| 文件 | LedgerIcon | iconKey | 分组 |",
        "| --- | --- | --- | --- |",
    ]
    for enum_name, keys, _ in written:
        lines.append(
            f"| `svg/{file_name(enum_name)}.svg` | `{enum_name}` | "
            f"{' / '.join('`' + k + '`' for k in keys) if keys else '—'} | "
            f"{SECTION_OF[enum_name]} |"
        )
    lines += [
        "| `svg/launcher-background.svg` | — | — | 启动图标 |",
        "| `svg/launcher-foreground.svg` | — | — | 启动图标 |",
        "| `svg/launcher.svg` | — | — | 启动图标（合成） |",
        "",
    ]
    with open(os.path.join(HERE, "MANIFEST.md"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))

    print(f"wrote {len(written)} ui icons + 3 launcher icons to {SVG_DIR}")


if __name__ == "__main__":
    main()
