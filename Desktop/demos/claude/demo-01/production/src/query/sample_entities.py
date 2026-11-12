#! /usr/bin/env python3
"""Show the records of given entities (evidence for quality review): get_entity_by_entity_id with record JSON
and matching info (RESOLVED_ENTITY.RECORDS[].DATA_SOURCE/RECORD_ID/MATCH_KEY/JSON_DATA.FEATURES[]).
Usage: python3 src/query/sample_entities.py ENTITY_ID [ENTITY_ID ...]
"""
import json
import sys

from senzing import SzEngineFlags, SzError

from sz_export import open_engine

FLAGS = (SzEngineFlags.SZ_ENTITY_INCLUDE_ENTITY_NAME | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA
         | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA)
try:
    factory, engine = open_engine(__file__)
    out = []
    for eid in map(int, sys.argv[1:]):
        ent = json.loads(engine.get_entity_by_entity_id(eid, FLAGS))["RESOLVED_ENTITY"]
        out.append({"entity_id": eid, "entity_name": ent.get("ENTITY_NAME"), "records": [
            {"ds": r["DATA_SOURCE"], "id": r["RECORD_ID"], "match_key": r.get("MATCH_KEY"),
             "values": {k: v for f in r.get("JSON_DATA", {}).get("FEATURES", []) for k, v in f.items() if k != "RECORD_TYPE"}}
            for r in ent["RECORDS"]]})
    print(json.dumps(out, indent=1))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
