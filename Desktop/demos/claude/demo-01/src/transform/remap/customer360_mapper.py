#! /usr/bin/env python3
"""Map the Customer 360 raw CSVs (CRM_SYSTEM, BILLING_SYSTEM, SUPPORT_TICKETS) to Senzing JSONL.

Spec: customer360_mapper.md / mapping_spec.json (produced by the Senzing mapping_workflow).
Values are routed verbatim (stripped of surrounding whitespace only); blank values are omitted,
and a feature object is emitted only when at least one of its attributes is populated.

Usage: python3 customer360_mapper.py <input.csv> -o <output.jsonl> [--source CRM_SYSTEM|BILLING_SYSTEM|SUPPORT_TICKETS] [--sample N]
The source is inferred from the file name when --source is omitted.
"""

import argparse
import csv
import json
import sys
from pathlib import Path

# Per source: record id column, feature groups (one object each, attr <- column), payload columns.
SOURCES = {
    "CRM_SYSTEM": {
        "id": "crm_id",
        "features": [
            {"NAME_FULL": "full_name"},
            {"ADDR_FULL": "address"},
            {"PHONE_NUMBER": "phone"},
            {"EMAIL_ADDRESS": "email"},
        ],
        "payload": ["campaign"],
    },
    "BILLING_SYSTEM": {
        "id": "billing_id",
        "features": [
            {"NAME_FIRST": "first_name", "NAME_LAST": "last_name"},
            {"ADDR_LINE1": "street", "ADDR_CITY": "city", "ADDR_STATE": "state", "ADDR_POSTAL_CODE": "zip"},
            {"PHONE_NUMBER": "phone"},
        ],
        "payload": ["plan"],
    },
    "SUPPORT_TICKETS": {
        "id": "ticket_id",
        "features": [
            {"NAME_FULL": "name"},
            {"EMAIL_ADDRESS": "email"},
        ],
        "payload": ["subject"],
    },
}
FILE_HINTS = {"crm": "CRM_SYSTEM", "billing": "BILLING_SYSTEM", "support": "SUPPORT_TICKETS"}


def infer_source(path):
    name = Path(path).name.lower()
    for hint, ds in FILE_HINTS.items():
        if name.startswith(hint):
            return ds
    raise ValueError(f"cannot infer data source from {path}; pass --source")


def clean(value):
    return (value or "").strip()


def map_row(ds, row):
    cfg = SOURCES[ds]
    features = [{"RECORD_TYPE": "PERSON"}]
    for group in cfg["features"]:
        obj = {attr: clean(row.get(col)) for attr, col in group.items()}
        obj = {k: v for k, v in obj.items() if v}
        if obj:
            features.append(obj)
    rec = {"DATA_SOURCE": ds, "RECORD_ID": clean(row[cfg["id"]]), "FEATURES": features}
    for col in cfg["payload"]:
        if clean(row.get(col)):
            rec[col] = clean(row[col])
    return rec


def map_file(input_path, output_path, ds=None, sample=None):
    ds = ds or infer_source(input_path)
    n = 0
    with open(input_path, newline="", encoding="utf-8") as src, open(output_path, "w", encoding="utf-8") as out:
        for row in csv.DictReader(src):
            out.write(json.dumps(map_row(ds, row), ensure_ascii=False) + "\n")
            n += 1
            if n % 1000 == 0:
                print(f"  {ds}: {n:,} records", file=sys.stderr)
            if sample and n >= sample:
                break
    print(f"{ds}: wrote {n:,} records to {output_path}", file=sys.stderr)
    return n


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("input")
    ap.add_argument("-o", "--output", required=True)
    ap.add_argument("--source", choices=sorted(SOURCES))
    ap.add_argument("--sample", type=int)
    a = ap.parse_args()
    map_file(a.input, a.output, a.source, a.sample)


if __name__ == "__main__":
    main()
