#! /usr/bin/env python3
"""Data collection (return from Data Quality, Mapping, and Transformation): regenerate BILLING_SYSTEM to >=80.

Same records, same RECORD_IDs, same invented people: this fills part of each gap with that person's
own value and changes nothing that is already present. Values are written in each field's dominant
format (no off-pattern values), so Step 7a's normalization is not undone.

Inputs:  data/raw/billing_system-improved.csv  (the current file; unchanged)
         data/ground_truth/scenario_ground_truth.csv  (record -> person)
         data/temp/people.json  (from generate_scenario_data.py --people-only, same seed)
Output:  data/raw/billing_system-regenerated.csv, data/temp/regeneration_report.json
Narrowed gap targets (share of records left without the value):
  phone 35%; whole address block 15%; each address part 3% more; first_name 3%; last_name 2%
"""

import csv
import json
import random
import re
from collections import Counter
from pathlib import Path

rng = random.Random(20261110)
SRC = Path("data/raw/billing_system-improved.csv")
OUT = Path("data/raw/billing_system-regenerated.csv")
ADDR = ["street", "city", "state", "zip"]
TARGET = {"phone": 0.35, "block": 0.15, "part": 0.03, "first_name": 0.03, "last_name": 0.02}

people = {p["eid"]: p for p in json.loads(Path("data/temp/people.json").read_text(encoding="utf-8"))}
owner = {}
for r in csv.DictReader(open("data/ground_truth/scenario_ground_truth.csv", encoding="utf-8")):
    if r["data_source"] == "BILLING_SYSTEM":
        owner[r["record_id"]] = int(r["person_id"])

rows = list(csv.DictReader(open(SRC, encoding="utf-8")))
fieldnames = list(rows[0].keys())
n = len(rows)


def present(v):
    return v is not None and v.strip() != ""


# Consistency check: every present last_name must be the owner's surname (people.json is the same people).
mismatch = sum(1 for r in rows if present(r["last_name"]) and r["last_name"] != people[owner[r["billing_id"]]]["last"])
if mismatch:
    raise SystemExit(f"people.json does not match the billing file ({mismatch} surname mismatches); stop")


def value_for(field, p):
    return {
        "first_name": p["first"], "last_name": p["last"],
        "street": f"{p['number']} {p['sname']} {p['stype_long']}", "city": p["city"], "state": p["state"],
        "zip": p["zip"][:5], "phone": f"{p['phone'][:3]}-{p['phone'][3:6]}-{p['phone'][6:]}",
    }[field]


def keep_missing_prob(current_missing, target):
    """Probability that a currently missing value stays missing, so the overall rate lands on target."""
    return min(1.0, target / current_missing) if current_missing else 1.0


filled = Counter()
block_absent = [r for r in rows if not any(present(r[f]) for f in ADDR)]
p_block = keep_missing_prob(len(block_absent) / n, TARGET["block"])
for f in ("phone", "first_name", "last_name"):
    cur = sum(not present(r[f]) for r in rows) / n
    TARGET[f + "_keep"] = keep_missing_prob(cur, TARGET[f])
part_missing = {f: sum(1 for r in rows if not present(r[f]) and any(present(r[g]) for g in ADDR)) / n for f in ADDR}

out = []
for r in rows:
    p = people[owner[r["billing_id"]]]
    new = dict(r)
    if not any(present(r[f]) for f in ADDR):
        if rng.random() >= p_block:
            for f in ADDR:
                new[f] = value_for(f, p)
                filled[f] += 1
    else:
        for f in ADDR:
            if not present(r[f]) and rng.random() >= keep_missing_prob(part_missing[f], TARGET["part"]):
                new[f] = value_for(f, p)
                filled[f] += 1
    for f in ("phone", "first_name", "last_name"):
        if not present(r[f]) and rng.random() >= TARGET[f + "_keep"]:
            new[f] = value_for(f, p)
            filled[f] += 1
    # nothing already present was changed
    assert all(new[f] == r[f] for f in fieldnames if present(r[f]))
    out.append(new)

with OUT.open("w", encoding="utf-8", newline="") as fh:
    w = csv.DictWriter(fh, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(out)

# ---- self-checks (Data collection Step 2): band, identifier collisions, name sharing
applicable = ["first_name", "last_name", "street", "city", "state", "zip", "phone"]
completeness = 100 * sum((1 + sum(present(r[f]) for f in applicable)) / 8 for r in out) / n


def shape(v):
    return re.sub(r"[a-z]", "a", re.sub(r"[A-Z]", "A", re.sub(r"\d", "9", v)))


def casing(v):
    letters = [c for c in v if c.isalpha()]
    return "UPPER" if letters and all(c.isupper() for c in letters) else \
        "lower" if letters and all(c.islower() for c in letters) else "Mixed"


match = pop = 0
for f, fn in {"first_name": casing, "last_name": casing, "state": shape, "zip": shape, "phone": shape,
              "last_invoice_date": shape}.items():
    vals = [fn(r[f].strip()) for r in out if present(r[f])]
    match += Counter(vals).most_common(1)[0][1]
    pop += len(vals)
fc = 100 * match / pop
dup = 100 * sum(c for c in Counter(r["billing_id"] for r in out).values() if c > 1) / n
score = round(0.70 * completeness + 0.25 * fc + 0.05 * (100 - dup), 1)

phones, emails, names = {}, {}, {}
for p in people.values():
    phones.setdefault(p["phone"], set()).add(p["eid"])
    emails.setdefault(p["email"], set()).add(p["eid"])
    names.setdefault(re.sub(r"[^a-z0-9]", "", (p["first"] + p["last"]).lower()), set()).add(p["eid"])
collisions = sum(len(v) > 1 for v in phones.values()) + sum(len(v) > 1 for v in emails.values())
sharing_entities = sum(len(v) for v in names.values() if len(v) > 1)
sharing_pairs = sum(len(v) * (len(v) - 1) // 2 for v in names.values() if len(v) > 1)

missing = {f: round(100 * sum(not present(r[f]) for r in out) / n, 1) for f in applicable}
report = {
    "file": str(OUT), "records": n, "filled": dict(filled), "missing_pct": missing,
    "completeness": round(completeness, 1), "format_consistency": round(fc, 1), "duplicate_rate": round(dup, 1),
    "measured_score": score, "band": ">=80" if score >= 80 else "70-79" if score >= 70 else "<70",
    "identifier_collisions": collisions,
    "name_collisions": {"measured_entities": sharing_entities, "measured_pairs": sharing_pairs,
                        "measured_share": round(sharing_entities / len(people), 4)},
}
Path("data/temp/regeneration_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
