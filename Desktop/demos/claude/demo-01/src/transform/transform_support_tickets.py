#! /usr/bin/env python3
"""Transform SUPPORT_TICKETS (CSV) into Senzing JSON, one record per line.

Mapping (mapping_workflow step 3, spec: docs/mapping/SUPPORT_TICKETS_mapper.md):
  ticket_id   -> RECORD_ID (root)            DATA_SOURCE = "SUPPORT_TICKETS" (root)
  (constant)  -> RECORD_TYPE "PERSON"        tickets are filed by individual customers
  name        -> NAME_FULL                   single name field, never parsed; casing kept as-is
  email       -> EMAIL_ADDRESS
  subject     -> payload "subject" (root)    ticket category, carried but not matched
  opened_date -> ignored
Values are emitted verbatim; empty values are omitted (no empty feature objects).

Usage: python3 src/transform/transform_support_tickets.py <input.csv> <output.jsonl> [--sample N]
"""

import argparse
import csv
import json
import sys

DATA_SOURCE = "SUPPORT_TICKETS"
FEATURE_FIELDS = [("name", "NAME_FULL"), ("email", "EMAIL_ADDRESS")]
PAYLOAD_FIELDS = ["subject"]


def value(row, field):
    v = row.get(field)
    return v if v is not None and v.strip() != "" else None


def transform(row):
    record_id = value(row, "ticket_id")
    if record_id is None:
        raise ValueError("missing ticket_id")
    features = [{"RECORD_TYPE": "PERSON"}]
    for field, attribute in FEATURE_FIELDS:
        v = value(row, field)
        if v is not None:
            features.append({attribute: v})
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
