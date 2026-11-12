#! /usr/bin/env python3
"""Find and show one resolved entity: the one with the most records, ties broken by the lowest ENTITY_ID.

Calls (reporting_guide topic='export'; get_sdk_reference flags/response_schemas):
  export_json_entity_report(flags) / fetch_next(handle) / close_export_report(handle)
    flags: SZ_EXPORT_INCLUDE_MULTI_RECORD_ENTITIES (which rows) | SZ_ENTITY_INCLUDE_RECORD_SUMMARY (record
    counts per source on each row) -- the selection flag alone returns rows with only ENTITY_ID.
  get_entity_by_entity_id(entity_id, flags) with record data, record JSON, matching info and entity name.
Fields read: RESOLVED_ENTITY.ENTITY_ID, .ENTITY_NAME, .RECORD_SUMMARY[].RECORD_COUNT/DATA_SOURCE,
  .RECORDS[].DATA_SOURCE/RECORD_ID/MATCH_KEY/ERRULE_CODE/JSON_DATA.
Usage: python3 src/query/show_entity.py [--data-source DS] [--rank records|sources]   (restrict the ranking to entities with records from DS)
Prints one JSON document; {"entity": null} when no entity has two or more records.
"""

import argparse
import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
EXPORT_FLAGS = SzEngineFlags.SZ_EXPORT_INCLUDE_MULTI_RECORD_ENTITIES | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_SUMMARY
GET_FLAGS = (SzEngineFlags.SZ_ENTITY_INCLUDE_ENTITY_NAME | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_SUMMARY
             | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO
             | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA)


def values_of(json_data):
    out = {}
    for f in json_data.get("FEATURES", []):
        for k, v in f.items():
            if k != "RECORD_TYPE":
                out[k] = v
    return out


ap = argparse.ArgumentParser()
ap.add_argument("--data-source", default=None)
ap.add_argument("--rank", choices=["records", "sources"], default="records",
                help="records: most records (midpoint); sources: most data sources, then most records (headline)")
args = ap.parse_args()
try:
    sz_factory = SzAbstractFactoryCore(Path(__file__).stem, SETTINGS, verbose_logging=False)  # keep a reference
    engine = sz_factory.create_engine()
    best = None  # (record_count, -entity_id)
    multi = 0
    handle = engine.export_json_entity_report(EXPORT_FLAGS)
    try:
        while True:
            row = engine.fetch_next(handle)
            if not row:
                break
            ent = json.loads(row)["RESOLVED_ENTITY"]
            summary = ent.get("RECORD_SUMMARY", [])
            if args.data_source and args.data_source not in {s["DATA_SOURCE"] for s in summary}:
                continue
            count = sum(s.get("RECORD_COUNT", 0) for s in summary)
            if count < 2:
                continue
            multi += 1
            n_src = len({s["DATA_SOURCE"] for s in summary})
            key = ((n_src, count) if args.rank == "sources" else (count,)) + (-ent["ENTITY_ID"],)
            if best is None or key > best:
                best = key
    finally:
        engine.close_export_report(handle)
    if best is None:
        print(json.dumps({"multi_record_entities": 0, "entity": None}))
        sys.exit(0)
    eid = -best[-1]
    ent = json.loads(engine.get_entity_by_entity_id(eid, GET_FLAGS))["RESOLVED_ENTITY"]
    recs = [{"data_source": r["DATA_SOURCE"], "record_id": r["RECORD_ID"], "match_key": r.get("MATCH_KEY"),
             "rule": r.get("ERRULE_CODE"), "values": values_of(r.get("JSON_DATA", {}))}
            for r in sorted(ent.get("RECORDS", []), key=lambda r: (r["DATA_SOURCE"], r["RECORD_ID"]))]
    print(json.dumps({"multi_record_entities": multi, "entity": {
        "entity_id": eid, "entity_name": ent.get("ENTITY_NAME"),
        "record_count": sum(s.get("RECORD_COUNT", 0) for s in ent.get("RECORD_SUMMARY", [])), "record_summary": ent.get("RECORD_SUMMARY"), "records": recs}}, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
