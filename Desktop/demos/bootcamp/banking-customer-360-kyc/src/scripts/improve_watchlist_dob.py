#!/usr/bin/env python3
"""Module 5 Step 7a: normalize WATCHLIST DATE_OF_BIRTH values to ISO YYYY-MM-DD.

Reads data/raw/screening_list.jsonl (left untouched) and writes data/raw/screening_list-improved.jsonl.
Only DATE_OF_BIRTH changes. Two-digit years are expanded to 19xx only when 20xx would be a future
date (so the century is not a guess); any value that cannot be converted unambiguously is left as-is
and reported. Phone numbers are not changed: standardizing them would mean adding a country code the
data does not carry.
"""
import datetime, json, re
SRC, OUT = "data/raw/screening_list.jsonl", "data/raw/screening_list-improved.jsonl"
this_year = datetime.date.today().year
changed, kept = [], []
def iso(v):
    m = re.fullmatch(r"(\d{1,2})/(\d{1,2})/(\d{2}|\d{4})", v.strip())
    if not m: return None
    mo, d, y = int(m.group(1)), int(m.group(2)), m.group(3)
    if len(y) == 2:
        y2 = int(y)
        if 2000 + y2 <= this_year: return None   # century genuinely ambiguous: leave as-is
        y = 1900 + y2
    datetime.date(int(y), mo, d)                    # raises on an impossible date
    return f"{int(y):04d}-{mo:02d}-{d:02d}"
with open(SRC, encoding="utf-8") as fin, open(OUT, "w", encoding="utf-8") as fout:
    for line in fin:
        if not line.strip(): continue
        r = json.loads(line)
        for o in r.get("FEATURES", []):
            if "DATE_OF_BIRTH" in o:
                new = iso(str(o["DATE_OF_BIRTH"]))
                if new and new != o["DATE_OF_BIRTH"]:
                    changed.append((r["RECORD_ID"], o["DATE_OF_BIRTH"], new)); o["DATE_OF_BIRTH"] = new
                elif not new: kept.append((r["RECORD_ID"], o["DATE_OF_BIRTH"]))
        fout.write(json.dumps(r, ensure_ascii=False) + "\n")
for c in changed: print("changed", *c)
for k in kept: print("kept (ambiguous)", *k)
