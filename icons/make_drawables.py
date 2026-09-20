"""Turn the exported SVGs into the VectorDrawables the app actually ships.

The SVGs in `svg/` are the design source; this rewrites the same geometry into Android's
container. VectorDrawable's `pathData` accepts the same syntax as SVG -- including the
arcs the drawn icons are made of -- so a `<path>` transfers verbatim and cannot drift from
what the preview showed. Only the three things VectorDrawable cannot express have to be
translated:

* `<rect rx>`   -> a path of four lines and four arcs
* `<circle>`    -> two semicircular arcs
* `<g transform="translate(...) scale(...)">` -> `<group>` with the same values
  (VectorDrawable's group matrix is `translate * scale` about pivot 0,0, which is what the
  SVG transform means once the pivot is zero)

Stroke colour and line caps are inherited from the SVG root there and have to be written
onto every path here, because VectorDrawable inherits nothing.

Run:  python make_drawables.py
Writes ../app/src/main/res/drawable/ic_ledger_*.xml (one per UI icon).
"""

import os
import re
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.join(HERE, "svg")
RES_DIR = os.path.normpath(
    os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
)

SVG_NS = "{http://www.w3.org/2000/svg}"
INK = "#000000"

# The launcher icons are already VectorDrawables in the project and are not part of this set.
SKIP = {"launcher", "launcher-background", "launcher-foreground"}


def trimmed(value):
    return round(float(value), 3)


def num(value):
    return f"{trimmed(value):g}"


def rounded_rect(x, y, w, h, rx):
    """A rounded rectangle as path data; `rx` may be 0, which degenerates to lines."""
    if rx <= 0:
        return (f"M{num(x)},{num(y)} L{num(x + w)},{num(y)} "
                f"L{num(x + w)},{num(y + h)} L{num(x)},{num(y + h)} Z")
    return (
        f"M{num(x + rx)},{num(y)} "
        f"L{num(x + w - rx)},{num(y)} A{num(rx)},{num(rx)} 0 0 1 {num(x + w)},{num(y + rx)} "
        f"L{num(x + w)},{num(y + h - rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x + w - rx)},{num(y + h)} "
        f"L{num(x + rx)},{num(y + h)} A{num(rx)},{num(rx)} 0 0 1 {num(x)},{num(y + h - rx)} "
        f"L{num(x)},{num(y + rx)} A{num(rx)},{num(rx)} 0 0 1 {num(x + rx)},{num(y)} Z"
    )


def circle(cx, cy, r):
    """A circle as two arcs. Direction is irrelevant for a shape that closes on itself."""
    return (f"M{num(cx - r)},{num(cy)} "
            f"A{num(r)},{num(r)} 0 1 0 {num(cx + r)},{num(cy)} "
            f"A{num(r)},{num(r)} 0 1 0 {num(cx - r)},{num(cy)} Z")


class Path:
    """One VectorDrawable `<path>`: path data plus how it is painted."""

    def __init__(self, data, stroked, stroke_width, filled, even_odd=False):
        self.data = data
        self.stroked = stroked
        self.stroke_width = stroke_width
        self.filled = filled
        self.even_odd = even_odd

    def xml(self, indent):
        attrs = [f'{indent}    android:pathData="{self.data}"']
        if self.stroked:
            attrs += [
                f'{indent}    android:strokeColor="{INK}"',
                f'{indent}    android:strokeWidth="{num(self.stroke_width)}"',
                f'{indent}    android:strokeLineCap="round"',
                f'{indent}    android:strokeLineJoin="round"',
            ]
        if self.filled:
            attrs.append(f'{indent}    android:fillColor="{INK}"')
            if self.even_odd:
                attrs.append(f'{indent}    android:fillType="evenOdd"')
        return f"{indent}<path\n" + "\n".join(attrs) + " />"


def parse_children(element, inherited, paths):
    """Walk the tree, resolving inheritance the way SVG does (child over root)."""
    for child in element:
        tag = child.tag.replace(SVG_NS, "")
        if tag == "g":
            # Only the one transform form this generator emits.
            match = re.match(
                r"translate\(([-\d.]+),([-\d.]+)\)\s*scale\(([-\d.]+)\)",
                child.get("transform", ""),
            )
            if not match:
                raise SystemExit(f"unsupported <g transform>: {child.get('transform')}")
            group = child
            paths.append(("group", tuple(float(v) for v in match.groups())))
            parse_children(group, inherited, paths)
            paths.append(("endgroup", None))
        elif tag in ("rect", "circle", "path"):
            style = dict(inherited)
            style.update({k: v for k, v in child.attrib.items()})
            width = style.get("stroke-width")
            stroked = style.get("stroke", "none") not in ("none", None)
            filled = style.get("fill", "none") not in ("none", None)
            if tag == "rect":
                x, y = float(child.get("x", 0)), float(child.get("y", 0))
                w, h = float(child.get("width")), float(child.get("height"))
                data = rounded_rect(x, y, w, h, float(child.get("rx", 0)))
            elif tag == "circle":
                data = circle(float(child.get("cx", 0)), float(child.get("cy", 0)),
                              float(child.get("r")))
            else:
                data = child.get("d", "")
            paths.append(("path", Path(
                data=data,
                stroked=stroked,
                stroke_width=float(width) if width else 0.0,
                filled=filled,
                even_odd=style.get("fill-rule") == "evenodd",
            )))


def document(name, paths):
    body = []
    depth = 1
    for kind, value in paths:
        indent = "    " * depth
        if kind == "group":
            tx, ty, scale = value
            body.append(
                f'{indent}<group\n'
                f'{indent}    android:translateX="{num(tx)}"\n'
                f'{indent}    android:translateY="{num(ty)}"\n'
                f'{indent}    android:scaleX="{num(scale)}"\n'
                f'{indent}    android:scaleY="{num(scale)}">'
            )
            depth += 1
        elif kind == "endgroup":
            depth -= 1
            body.append("    " * depth + "</group>")
        else:
            body.append(value.xml(indent))
    joined = "\n".join(body)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by icons/make_drawables.py from icons/svg/"
        f"{name}.svg. Edit the generator, not this file. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="100"\n'
        '    android:viewportHeight="100">\n'
        f"{joined}\n"
        "</vector>\n"
    )


def resource_name(svg_name):
    """`chevron-right` -> `ic_ledger_chevron_right` (resource names take no hyphens)."""
    return "ic_ledger_" + svg_name.replace("-", "_")


def main():
    os.makedirs(RES_DIR, exist_ok=True)
    written = []
    for svg_name in sorted(
        os.path.splitext(f)[0] for f in os.listdir(SVG_DIR) if f.endswith(".svg")
    ):
        if svg_name in SKIP:
            continue
        tree = ET.parse(os.path.join(SVG_DIR, svg_name + ".svg"))
        root = tree.getroot()
        inherited = {
            key: value for key, value in root.attrib.items()
            if key in ("stroke", "stroke-width", "fill", "fill-rule")
        }
        paths = []
        parse_children(root, inherited, paths)
        out = os.path.join(RES_DIR, resource_name(svg_name) + ".xml")
        with open(out, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(document(svg_name, paths))
        written.append(resource_name(svg_name))

    print(f"wrote {len(written)} vector drawables to {RES_DIR}")


if __name__ == "__main__":
    main()
