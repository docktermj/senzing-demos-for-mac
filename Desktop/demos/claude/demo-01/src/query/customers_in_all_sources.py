#! /usr/bin/env python3
"""Query 1: which customers appear in all three systems, and what does their unified record look like?

Export rows (RESOLVED_ENTITY.RECORD_SUMMARY[].DATA_SOURCE) select entities holding records from every loaded
source; the unified view of the first N (lowest ENTITY_ID) comes from get_entity_by_entity_id with record JSON
and the entity's FEATURES (RESOLVED_ENTITY.FEATURES.<TYPE>[].FEAT_DESC).
Usage: python3 src/query/customers_in_all_sources.py [--show N]
Writes data/query_results/customers_in_all_sources.json.
"""
import argparse
import json
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError

from sz_export import export_rows, open_engine

SOURCES = {"CRM_SYSTEM", "BILLING_SYSTEM", "SUPPORT_TICKETS"}
GET_FLAGS = (SzEngineFlags.SZ_ENTITY_INCLUDE_ENTITY_NAME | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_SUMMARY
             | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO
             | SzEngineFlags.SZ_ENTITY_INCLUDE_REPRESENTATIVE_FEATURES)

ap = argparse.ArgumentParser()
ap.add_argument("--show", type=int, default=3)
args = ap.parse_args()
try:
    factory, engine = open_engine(__file__)
    ids = sorted(r["RESOLVED_ENTITY"]["ENTITY_ID"] for r in export_rows(engine)
                 if {s["DATA_SOURCE"] for s in r["RESOLVED_ENTITY"].get("RECORD_SUMMARY", [])} >= SOURCES)
    views = []
    for eid in ids[:args.show]:
        ent = json.loads(engine.get_entity_by_entity_id(eid, GET_FLAGS))["RESOLVED_ENTITY"]
        feats = {t: sorted({f["FEAT_DESC"] for f in v}) for t, v in ent.get("FEATURES", {}).items() if t != "RECORD_TYPE"}
        views.append({"entity_id": eid, "entity_name": ent.get("ENTITY_NAME"),
                      "records": [f'{r["DATA_SOURCE"]}:{r["RECORD_ID"]} ({r.get("MATCH_KEY") or "first record"})'
                                  for r in sorted(ent["RECORDS"], key=lambda r: (r["DATA_SOURCE"], r["RECORD_ID"]))],
                      "unified_features": feats})
    out = {"entities_in_all_sources": len(ids), "entity_ids": ids, "unified_views": views}
    Path("data/query_results").mkdir(parents=True, exist_ok=True)
    Path("data/query_results/customers_in_all_sources.json").write_text(json.dumps(out, indent=2))
    print(json.dumps({k: v for k, v in out.items() if k != "entity_ids"}, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
