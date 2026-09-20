"""Apply an edited copy workbook back to the Kotlin sources.

Reads the workbook the extractor produced, the edited copy beside it, and
`app-copy.json`, which pins every row to the exact byte range of every literal that spells
it. A row whose 「当前文字」 was rewritten is spliced in place; a row emptied out is
reported rather than applied, because removing a string usually means removing the control
around it too.

Markers survive the round trip: `{monthLabel}` came from `$monthLabel` and goes back to it,
`{file.rowCount}` came from `${file.rowCount}` and goes back to that. A marker the editor
deleted or retyped is reported instead of guessed at.

    python tools/apply_copy.py --dry-run
    python tools/apply_copy.py
"""

import argparse
import json
import os
import re
import sys

import openpyxl

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
# `app-copy.json` names files the way the extractor does, so `source_relative` strips the
# package prefix; put it back here, or every path misses by two directories.
SOURCE_ROOT = os.path.join(PROJECT, "app", "src", "main", "java", "com", "pocketledger")
EDITED = os.path.join(HERE, "edits", "记账本文案-已修改.xlsx")
ORIGINAL = os.path.join(HERE, "记账本文案.xlsx")
COPY_JSON = os.path.join(HERE, "app-copy.json")

IDENTIFIER = re.compile(r"^[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*$")


def read_sheet(path, sheet="界面文字"):
    """序号 -> the text in the 「当前文字」 column."""
    book = openpyxl.load_workbook(path)
    page = book[sheet]
    out = {}
    for row in range(4, page.max_row + 1):
        number = page.cell(row=row, column=1).value
        if number is None:
            continue
        out[int(number)] = page.cell(row=row, column=5).value
    return out


def to_kotlin(text, marks):
    """The edited sentence as the inside of a Kotlin string literal.

    Markers become their original `$name` / `${expr}` form, and everything else is escaped,
    so a sentence containing a quote or a dollar sign cannot break out of the literal.
    """
    pieces, missing = [], []
    i, n = 0, len(text)
    while i < n:
        if text[i] == "{":
            close = text.find("}", i)
            name = text[i + 1:close] if close > 0 else None
            if name in marks:
                pieces.append(("mark", name))
                i = close + 1
                continue
            if name:
                missing.append(name)
        pieces.append(("char", text[i]))
        i += 1

    out = []
    for kind, value in pieces:
        if kind == "mark":
            if marks[value] == "braced" or not IDENTIFIER.match(value):
                out.append("${%s}" % value)
            else:
                out.append("$" + value)
        elif value == "\n":
            out.append("\\n")
        elif value in '\\"$':
            out.append("\\" + value)
        else:
            out.append(value)
    return "".join(out), missing


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    before = read_sheet(ORIGINAL)
    after = read_sheet(EDITED)
    data = json.load(open(COPY_JSON, encoding="utf-8"))
    rows = {row["number"]: row for row in data["rows"] if "number" in row}

    changed, emptied, problems = [], [], []
    for number, old in before.items():
        # A number missing from the edited sheet was never touched; a number present but
        # blank is a deliberate deletion, and the two must not be confused.
        new = after.get(number, old)
        if new == old:
            continue
        row = rows.get(number)
        if row is None:
            problems.append(f"序号 {number}: no row in app-copy.json")
            continue
        if not row.get("locations"):
            problems.append(f"序号 {number}: nothing to splice ({row['text']!r})")
            continue
        if new is None or not str(new).strip():
            emptied.append((number, row))
            continue
        changed.append((number, row, str(new)))

    edits = {}
    for number, row, new in changed:
        for location in row["locations"]:
            literal, missing = to_kotlin(new, location.get("marks") or {})
            if missing:
                problems.append(
                    f"序号 {number} in {location['file']}:{location['line']} "
                    f"mentions {missing}, which was not a placeholder there")
            edits.setdefault(location["file"], []).append(
                (location["start"], location["end"],
                 '"%s"' % literal, location["line"], row["raw"], new))

    print(f"{len(changed)} rewritten rows -> {sum(len(v) for v in edits.values())} sites "
          f"across {len(edits)} files")
    for file, sites in sorted(edits.items()):
        for start, end, replacement, line, raw, new in sorted(sites, reverse=True):
            print(f"  {file}:{line}\n      - {raw[:78]}\n      + {new[:78]}")

    print(f"\n{len(emptied)} rows emptied (delete the control by hand):")
    for number, row in emptied:
        print(f"  序号 {number} · {row['area']} · {row['where']}: {row['text'][:70]}")

    if problems:
        print(f"\n{len(problems)} problems:")
        for problem in problems:
            print("  " + problem)

    if args.dry_run or problems:
        print("\nnothing written")
        return 1 if problems else 0

    for file, sites in edits.items():
        path = os.path.join(SOURCE_ROOT, file)
        text = open(path, encoding="utf-8").read()
        for start, end, replacement, *_ in sorted(sites, reverse=True):
            text = text[:start] + replacement + text[end:]
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
        print(f"rewrote {len(sites)} in {file}")
    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
