#! /usr/bin/env python3
"""Transform BILLING_SYSTEM (CSV) into Senzing JSON, one record per line.

Mapping (mapping_workflow step 3, spec: docs/mapping/BILLING_SYSTEM_mapper.md):
  billing_id        -> RECORD_ID (root)          DATA_SOURCE = "BILLING_SYSTEM" (root)
  (constant)        -> RECORD_TYPE "PERSON"      individual customers; no type field in the source
  first_name, last_name           -> one NAME object (NAME_FIRST, NAME_LAST)
  street, city, state, zip        -> one ADDRESS object (ADDR_LINE1, ADDR_CITY, ADDR_STATE, ADDR_POSTAL_CODE)
  phone             -> PHONE_NUMBER
  plan              -> payload "plan" (root)     subscription category, carried but not matched
  last_invoice_date -> ignored
A feature's parts always share one object; only the parts present are written, and a feature with
no part present is omitted. Values are emitted verbatim.

Usage: python3 src/transform/transform_billing_system.py <input.csv> <output.jsonl> [--sample N]
"""

import argparse
import csv
import json
import sys

DATA_SOURCE = "BILLING_SYSTEM"
GROUPED_FEATURES = [
    [("first_name", "NAME_FIRST"), ("last_name", "NAME_LAST")],
    [("street", "ADDR_LINE1"), ("city", "ADDR_CITY"), ("state", "ADDR_STATE"), ("zip", "ADDR_POSTAL_CODE")],
    [("phone", "PHONE_NUMBER")],
]
PAYLOAD_FIELDS = ["plan"]


def value(row, field):
    v = row.get(field)
    return v if v is not None and v.strip() != "" else None


def transform(row):
    record_id = value(row, "billing_id")
    if record_id is None:
        raise ValueError("missing billing_id")
    features = [{"RECORD_TYPE": "PERSON"}]
    for group in GROUPED_FEATURES:
        obj = {attribute: value(row, field) for field, attribute in group if value(row, field) is not None}
        if obj:
            features.append(obj)
    record = {"DATA_SOURCE": DATA_SOURCE, "RECORD_ID": record_id, "FEATURES": features}
    for field in PAYLOAD_FIELDS:
        v = value(row, field)
        if v is not None:
            record[field] = v
    return record


def run(input_path, output_path, sample=None):
    written = rejected = 0
    with open(input_path, encoding="utf-8", newline="") as fin, open(output_path, "w", encoding="utf-8") as fout:
        for n, row in enumerate(csv.DictReader(fin), 1):
            if sample is not None and n > sample:
                break
            try:
                fout.write(json.dumps(transform(row), ensure_ascii=False) + "\n")
                written += 1
            except ValueError as err:
                rejected += 1
                print(f"row {n}: rejected ({err})", file=sys.stderr)
            if n % 1000 == 0:
                print(f"  {n:,} rows processed", file=sys.stderr)
    print(f"{DATA_SOURCE}: {written:,} records written to {output_path}, {rejected:,} rejected")
    return written, rejected


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--sample", type=int, default=None, help="transform only the first N rows")
    args = ap.parse_args()
    _, rejected = run(args.input, args.output, args.sample)
    sys.exit(1 if rejected else 0)
