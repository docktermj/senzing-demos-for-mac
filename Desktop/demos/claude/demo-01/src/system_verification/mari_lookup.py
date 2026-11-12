#! /usr/bin/env python3
"""Step 7a reveal: where did the Mari record land, and why?

Calls (get_sdk_reference, methods + response_schemas):
  SzEngine.get_entity_by_record_id(data_source_code, record_id, flags) -> JSON string
  SzEngine.why_records(data_source_code_1, record_id_1, data_source_code_2, record_id_2, flags) -> JSON string
Fields read:
  RESOLVED_ENTITY.ENTITY_ID, RESOLVED_ENTITY.RECORDS[].RECORD_ID
  WHY_RESULTS[].MATCH_INFO.WHY_KEY, .MATCH_LEVEL_CODE, .WHY_ERRULE_CODE,
  .FEATURE_SCORES.<FEATURE>[].SCORE / SCORE_BUCKET / INBOUND_FEAT_DESC / CANDIDATE_FEAT_DESC
"""

import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
DATA_SOURCE = "VERIFY"
MARI_ID = "V-MARI-1"
MARISOL_ID = "V-1001"
ENTITY_FLAGS = SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO
WHY_FLAGS = SzEngineFlags.SZ_INCLUDE_FEATURE_SCORES


def entity_of(engine, record_id):
    response = json.loads(engine.get_entity_by_record_id(DATA_SOURCE, record_id, ENTITY_FLAGS))
    resolved = response.get("RESOLVED_ENTITY")
    if not resolved or "ENTITY_ID" not in resolved:
        raise ValueError(f"no RESOLVED_ENTITY.ENTITY_ID for {record_id}; keys seen: {sorted(response)}")
    return resolved["ENTITY_ID"], [r.get("RECORD_ID") for r in resolved.get("RECORDS", [])]


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()

    mari_entity, mari_members = entity_of(sz_engine, MARI_ID)
    marisol_entity, marisol_members = entity_of(sz_engine, MARISOL_ID)

    why = json.loads(sz_engine.why_records(DATA_SOURCE, MARI_ID, DATA_SOURCE, MARISOL_ID, WHY_FLAGS))
    results = why.get("WHY_RESULTS", [])
    info = results[0].get("MATCH_INFO", {}) if results else {}

    scores = {}
    for feature, rows in info.get("FEATURE_SCORES", {}).items():
        if rows:
            row = rows[0]
            scores[feature] = {
                "score": row.get("SCORE"),
                "bucket": row.get("SCORE_BUCKET"),
                "mari": row.get("INBOUND_FEAT_DESC"),
                "marisol": row.get("CANDIDATE_FEAT_DESC"),
            }

    output = {
        "mari_record": MARI_ID,
        "mari_entity_id": mari_entity,
        "mari_entity_records": mari_members,
        "marisol_record": MARISOL_ID,
        "marisol_entity_id": marisol_entity,
        "marisol_entity_records": marisol_members,
        "same_entity": mari_entity == marisol_entity,
        "why_key": info.get("WHY_KEY"),
        "match_level_code": info.get("MATCH_LEVEL_CODE"),
        "why_errule_code": info.get("WHY_ERRULE_CODE"),
        "candidate_keys": sorted(info.get("CANDIDATE_KEYS", {}).keys()),
        "feature_scores": scores,
    }
    print(json.dumps(output, indent=2))
except (SzError, ValueError) as err:
    print(f"\n{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
