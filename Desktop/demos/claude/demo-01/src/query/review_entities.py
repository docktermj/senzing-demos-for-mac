#! /usr/bin/env python3
"""Look up how Senzing resolved a set of records, and why each pair did or did not resolve together.

Calls (get_sdk_reference, methods + response_schemas):
  get_entity_by_record_id(data_source_code, record_id, flags) -> RESOLVED_ENTITY.ENTITY_ID, .ENTITY_NAME,
      .RECORDS[].DATA_SOURCE/RECORD_ID/MATCH_KEY/ERRULE_CODE
  why_records(ds1, rid1, ds2, rid2, flags=SZ_INCLUDE_FEATURE_SCORES) -> WHY_RESULTS[].MATCH_INFO.WHY_KEY,
      .MATCH_LEVEL_CODE, .WHY_ERRULE_CODE, .CANDIDATE_KEYS, .FEATURE_SCORES.<FEATURE>[].SCORE/SCORE_BUCKET
Usage: python3 src/query/review_entities.py DS:RECORD_ID DS:RECORD_ID [...]
"""

import itertools
import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
ENTITY_FLAGS = (SzEngineFlags.SZ_ENTITY_INCLUDE_ENTITY_NAME | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA
                | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO)
WHY_FLAGS = SzEngineFlags.SZ_INCLUDE_FEATURE_SCORES

keys = [tuple(a.split(":", 1)) for a in sys.argv[1:]]
try:
    sz_factory = SzAbstractFactoryCore(Path(__file__).stem, SETTINGS, verbose_logging=False)
    engine = sz_factory.create_engine()
    out = {"records": {}, "pairs": []}
    for ds, rid in keys:
        ent = json.loads(engine.get_entity_by_record_id(ds, rid, ENTITY_FLAGS))["RESOLVED_ENTITY"]
        out["records"][f"{ds}:{rid}"] = {
            "entity_id": ent["ENTITY_ID"], "entity_name": ent.get("ENTITY_NAME"),
            "entity_records": [f"{r['DATA_SOURCE']}:{r['RECORD_ID']}" + (f" ({r['MATCH_KEY']})" if r.get("MATCH_KEY") else "")
                               for r in ent.get("RECORDS", [])]}
    for (d1, r1), (d2, r2) in itertools.combinations(keys, 2):
        why = json.loads(engine.why_records(d1, r1, d2, r2, WHY_FLAGS)).get("WHY_RESULTS", [{}])[0].get("MATCH_INFO", {})
        scores = {f: {"score": v[0].get("SCORE"), "bucket": v[0].get("SCORE_BUCKET"),
                      "values": [v[0].get("INBOUND_FEAT_DESC"), v[0].get("CANDIDATE_FEAT_DESC")]}
                  for f, v in why.get("FEATURE_SCORES", {}).items() if v}
        same = out["records"][f"{d1}:{r1}"]["entity_id"] == out["records"][f"{d2}:{r2}"]["entity_id"]
        out["pairs"].append({"pair": [f"{d1}:{r1}", f"{d2}:{r2}"], "same_entity": same, "why_key": why.get("WHY_KEY"),
                             "match_level": why.get("MATCH_LEVEL_CODE"), "rule": why.get("WHY_ERRULE_CODE"),
                             "candidate_keys": sorted(why.get("CANDIDATE_KEYS", {})), "feature_scores": scores})
    print(json.dumps(out, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
