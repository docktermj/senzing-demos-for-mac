#! /usr/bin/env python3
"""Discover step 4c: how was this entity built, step by step?

how_entity_by_entity_id(entity_id, flags) with SZ_INCLUDE_FEATURE_SCORES | SZ_INCLUDE_MATCH_KEY_DETAILS |
SZ_ENTITY_INCLUDE_ALL_RELATIONS (get_sdk_reference flags/response_schemas for how_entity_by_entity_id).
Reads HOW_RESULTS.RESOLUTION_STEPS[]: STEP, VIRTUAL_ENTITY_1/_2 (.VIRTUAL_ENTITY_ID, .MEMBER_RECORDS[].RECORDS[]),
RESULT_VIRTUAL_ENTITY_ID, MATCH_INFO.MATCH_KEY / ERRULE_CODE / FEATURE_SCORES / MATCH_KEY_DETAILS (how-side names);
and HOW_RESULTS.FINAL_STATE (NEED_REEVALUATION, VIRTUAL_ENTITIES[]).
Usage: python3 src/query/how_entity.py ENTITY_ID
"""
import json
import sys

from senzing import SzEngineFlags, SzError

from sz_export import open_engine

FLAGS = (SzEngineFlags.SZ_INCLUDE_FEATURE_SCORES | SzEngineFlags.SZ_INCLUDE_MATCH_KEY_DETAILS
         | SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_RELATIONS)


def recs(ve):
    return [f'{r["DATA_SOURCE"]}:{r["RECORD_ID"]}' for m in ve.get("MEMBER_RECORDS", []) for r in m.get("RECORDS", [])]


try:
    factory, engine = open_engine(__file__)
    how = json.loads(engine.how_entity_by_entity_id(int(sys.argv[1]), FLAGS))["HOW_RESULTS"]
    steps = []
    for s in sorted(how.get("RESOLUTION_STEPS", []), key=lambda s: s["STEP"]):
        mi = s.get("MATCH_INFO", {})
        steps.append({"step": s["STEP"], "side_1": recs(s["VIRTUAL_ENTITY_1"]), "side_2": recs(s["VIRTUAL_ENTITY_2"]),
                      "result": s.get("RESULT_VIRTUAL_ENTITY_ID"), "match_key": mi.get("MATCH_KEY"), "rule": mi.get("ERRULE_CODE"),
                      "match_info_keys": sorted(mi),
                      "scores": {ft: [(x.get("INBOUND_FEAT_DESC"), x.get("CANDIDATE_FEAT_DESC"), x.get("SCORE"), x.get("SCORE_BUCKET"))
                                      for x in lst] for ft, lst in mi.get("FEATURE_SCORES", {}).items() if ft != "RECORD_TYPE"},
                      "confirmations": [(c.get("FTYPE_CODE"), c.get("SCORE"), c.get("SCORE_BUCKET"))
                                        for c in (mi.get("MATCH_KEY_DETAILS") or {}).get("CONFIRMATIONS", [])]})
    fs = how.get("FINAL_STATE", {})
    print(json.dumps({"steps": steps, "final_state": {"need_reevaluation": fs.get("NEED_REEVALUATION"),
                      "virtual_entities": [recs(v) for v in fs.get("VIRTUAL_ENTITIES", [])]}}, indent=1))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
