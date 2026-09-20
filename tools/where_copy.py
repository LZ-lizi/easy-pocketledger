"""Print where a given 序号 (or several) lives in the sources."""

import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
SOURCE = os.path.join(PROJECT, "app", "src", "main", "java", "com", "pocketledger")

data = json.load(open(os.path.join(HERE, "app-copy.json"), encoding="utf-8"))
wanted = {int(a) for a in sys.argv[1:]}

for row in data["rows"]:
    if row.get("number") not in wanted:
        continue
    print(f"===== 序号 {row['number']} · {row['scene']} · {row['where']}")
    print(f"      {row['text']}")
    for location in row["locations"]:
        print(f"   --> {location['file']}:{location['line']}")
