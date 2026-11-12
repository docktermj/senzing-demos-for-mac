#! /usr/bin/env python3
"""Discover step 4a: find demonstration candidates by looking up every loaded record's entity.

For each (DATA_SOURCE, RECORD_ID) in the loaded files (deduplicated on that key), calls
get_entity_by_record_id with SZ_ENTITY_INCLUDE_RECORD_DATA | SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO |
SZ_ENTITY_INCLUDE_ENTITY_NAME | SZ_ENTITY_INCLUDE_ALL_RELATIONS | SZ_ENTITY_INCLUDE_RELATED_MATCHING_INFO
(ALL_RELATIONS = possibly-same, possibly-related, name-only and disclosed, i.e. discovered AND disclosed links).
Reads RESOLVED_ENTITY.ENTITY_ID/RECORDS[], RELATED_ENTITIES[].ENTITY_ID/MATCH_LEVEL_CODE/IS_DISCLOSED.
Each entity is fetched once (its other records are skipped once seen).
Usage: python3 src/query/discover_patterns.py [--record DS:ID]  (--record: just print that record's entity ID)
Writes data/query_results/discover_patterns.json.
"""
import argparse
import json
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError

from sz_export import open_engine

FILES = ["data/senzing-ready/crm_system.jsonl", "data/senzing-ready/support_tickets.jsonl",
         "data/senzing-ready/billing_system.jsonl"]
FLAGS = (SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO
         | SzEngineFlags.SZ_ENTITY_INCLUDE_ENTITY_NAME | SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_RELATIONS
         | SzEngineFlags.SZ_ENTITY_INCLUDE_RELATED_MATCHING_INFO)
ROOT = Path(__file__).resolve().parents[2]

ap = argparse.ArgumentParser()
ap.add_argument("--record")
args = ap.parse_args()
try:
    factory, engine = open_engine(__file__)
    if args.record:
        ds, rid = args.record.split(":", 1)
        print(json.loads(engine.get_entity_by_record_id(ds, rid, FLAGS))["RESOLVED_ENTITY"]["ENTITY_ID"])
        sys.exit(0)
    keys = []
    for f in FILES:
        for line in open(ROOT / f, encoding="utf-8"):
            if line.strip():
                r = json.loads(line)
                keys.append((r["DATA_SOURCE"], r["RECORD_ID"]))
    keys = list(dict.fromkeys(keys))
    seen, rec_entity, ents = set(), {}, {}
    for ds, rid in keys:
        if f"{ds}:{rid}" in rec_entity:
            continue
        doc = json.loads(engine.get_entity_by_record_id(ds, rid, FLAGS))
        ent = doc["RESOLVED_ENTITY"]
        eid = ent["ENTITY_ID"]
        recs = [f'{r["DATA_SOURCE"]}:{r["RECORD_ID"]}' for r in ent["RECORDS"]]
        for k in recs:
            rec_entity[k] = eid
        rels = doc.get("RELATED_ENTITIES", [])
        ents[eid] = {"name": ent.get("ENTITY_NAME"), "records": recs,
                     "sources": sorted({k.split(":")[0] for k in recs}),
                     "relationships": [{"entity_id": r["ENTITY_ID"], "level": r.get("MATCH_LEVEL_CODE"),
                                        "match_key": r.get("MATCH_KEY"), "disclosed": r.get("IS_DISCLOSED", 0)} for r in rels]}
    multi = {e: v for e, v in ents.items() if len(v["records"]) >= 3}
    cross = {e: v for e, v in multi.items() if len(v["sources"]) >= 2}
    related = {e: v for e, v in ents.items() if v["relationships"]}
    out = {"records_checked": len(keys), "entities": len(ents),
           "multi_record_3plus": len(multi), "cross_source_3plus": len(cross), "with_relationships": len(related),
           "disclosed_relationship_entities": sum(1 for v in related.values() if any(r["disclosed"] for r in v["relationships"])),
           "top_related": sorted(related, key=lambda e: (-len(related[e]["relationships"]), e))[:5],
           "entities_detail": ents, "record_entity": rec_entity}
    (ROOT / "data/query_results").mkdir(parents=True, exist_ok=True)
    (ROOT / "data/query_results/discover_patterns.json").write_text(json.dumps(out))
    print(json.dumps({k: v for k, v in out.items() if k not in ("entities_detail", "record_entity")}, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
