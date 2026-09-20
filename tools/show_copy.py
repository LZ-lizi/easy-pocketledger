"""Print the extractor's output so it can be read and checked by eye.

    python tools/show_copy.py                 # every extracted string
    python tools/show_copy.py dropped         # what was left out and why
    python tools/show_copy.py area 导入账单    # one section
    python tools/show_copy.py grep 取消        # one wording
"""

import collections
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
data = json.load(open(os.path.join(HERE, "app-copy.json"), encoding="utf-8"))

mode = sys.argv[1] if len(sys.argv) > 1 else "rows"
needle = sys.argv[2] if len(sys.argv) > 2 else None

if mode == "dropped":
    rows = data["dropped"]
    for why, count in collections.Counter(r["why"] for r in rows).most_common():
        print(f"{count:>4}  {why}")
    print("-" * 110)
    for row in rows:
        print(f"{row['why'][:14]:<16}{row['file']}:{row['line']}  {row['text'][:80]}")
else:
    rows = data["rows"]
    if mode == "area":
        rows = [r for r in rows if needle in r["area"]]
    elif mode == "grep":
        rows = [r for r in rows if needle in (r["text"] or "")]
    for area, count in collections.Counter(r["area"] for r in rows).most_common():
        print(f"{count:>4}  {area}")
    print("-" * 110)
    for row in rows:
        places = "；".join(row["places"][:2])
        if len(row["places"]) > 2:
            places += f" 等{len(row['places'])}处"
        print(f"{row['scene'][:9]:<11}{(row['where'] or '-')[:20]:<22}"
              f"{(row['text'] or '').replace(chr(10), ' ⏎ ')[:62]:<64}{places[:44]}")
