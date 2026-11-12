#! /usr/bin/env python3
"""Query 3: how many unique customers do we really have, compared with each system's raw record count?

Raw counts: records Senzing holds per source = sum of RECORD_SUMMARY[].RECORD_COUNT over the export (Senzing's
own count of distinct (DATA_SOURCE, RECORD_ID) keys, so duplicate file rows cannot inflate it). Unique
customers per source = entities with at least one record from it; overall = total entities.
Usage: python3 src/query/unique_customer_counts.py
Writes data/query_results/unique_customer_counts.json.
"""
import json
import sys
from collections import Counter
from pathlib import Path

from senzing import SzError

from sz_export import export_rows, open_engine

try:
    factory, engine = open_engine(__file__)
    recs, ents, total = Counter(), Counter(), 0
    for row in export_rows(engine):
        total += 1
        for s in row["RESOLVED_ENTITY"].get("RECORD_SUMMARY", []):
            recs[s["DATA_SOURCE"]] += s["RECORD_COUNT"]
            ents[s["DATA_SOURCE"]] += 1
    per = {ds: {"raw_records": recs[ds], "unique_customers": ents[ds], "duplicates_within_source": recs[ds] - ents[ds]}
           for ds in sorted(recs)}
    raw = sum(recs.values())
    out = {"raw_records_total": raw, "unique_customers_total": total,
           "records_per_customer": round(raw / total, 2) if total else None, "per_source": per}
    Path("data/query_results").mkdir(parents=True, exist_ok=True)
    Path("data/query_results/unique_customer_counts.json").write_text(json.dumps(out, indent=2))
    print(json.dumps(out, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
