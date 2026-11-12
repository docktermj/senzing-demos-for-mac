#! /usr/bin/env python3
"""Phase 1 Step 7a: normalize BILLING_SYSTEM's minority formats to each field's dominant format.

Reads data/raw/billing_system.csv (left unchanged) and writes data/raw/billing_system-improved.csv.
Mechanical changes only; no missing value is filled in:
  phone              -> 999-999-9999 (the dominant format); digits are kept, only punctuation changes
  last_invoice_date  -> YYYY-MM-DD from MM/DD/YYYY
  state              -> uppercase two-letter code
  zip                -> 5-digit ZIP; a ZIP+4 loses its 4-digit extension
Prints a per-field count of the values changed.
"""

import csv
import re
from collections import Counter
from pathlib import Path

SRC = Path("data/raw/billing_system.csv")
OUT = Path("data/raw/billing_system-improved.csv")
changed = Counter()


def norm_phone(v):
    d = re.sub(r"\D", "", v)
    if len(d) == 10:
        return f"{d[:3]}-{d[3:6]}-{d[6:]}"
    return v  # leave anything that is not a 10-digit number untouched


def norm_date(v):
    m = re.fullmatch(r"(\d{2})/(\d{2})/(\d{4})", v)
    return f"{m.group(3)}-{m.group(1)}-{m.group(2)}" if m else v


def norm_zip(v):
    m = re.fullmatch(r"(\d{5})-\d{4}", v)
    return m.group(1) if m else v


RULES = {"phone": norm_phone, "last_invoice_date": norm_date, "state": str.upper, "zip": norm_zip}

with SRC.open(encoding="utf-8", newline="") as fin, OUT.open("w", encoding="utf-8", newline="") as fout:
    reader = csv.DictReader(fin)
    writer = csv.DictWriter(fout, fieldnames=reader.fieldnames)
    writer.writeheader()
    rows = 0
    for row in reader:
        rows += 1
        for field, fn in RULES.items():
            value = row[field]
            if value.strip():
                new = fn(value.strip())
                if new != value:
                    changed[field] += 1
                    row[field] = new
        writer.writerow(row)

print(f"wrote {OUT} ({rows} records)")
for field in RULES:
    print(f"  {field}: {changed[field]} values normalized")
