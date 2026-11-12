#! /usr/bin/env python3
"""Step 7 validation: did the VERIFY records resolve the way Step 2 designed them to?

Calls: SzEngine.get_entity_by_record_id(data_source_code, record_id, flags) -> JSON string
Fields read (get_sdk_reference response_schemas, filter get_entity_by_record_id):
  RESOLVED_ENTITY.ENTITY_ID, RESOLVED_ENTITY.RECORDS[].RECORD_ID
"""

import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
DATA_FILE = Path("src/system_verification/verification_data.jsonl")
FLAGS = SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO

MERGE_CLUSTER = ["V-1001", "V-1002", "V-1003"]
DISTRACTORS = ["V-1004"]
EXPECTED_ENTITIES = 2
EXPECTED_MERGE_RECORD_COUNT = 3


def entity_of(engine, data_source, record_id):
    response = json.loads(engine.get_entity_by_record_id(data_source, record_id, FLAGS))
    resolved = response.get("RESOLVED_ENTITY")
    if not resolved or "ENTITY_ID" not in resolved:
        raise ValueError(f"no RESOLVED_ENTITY.ENTITY_ID for {record_id}; keys seen: {sorted(response)}")
    records = [r.get("RECORD_ID") for r in resolved.get("RECORDS", [])]
    if not records:
        raise ValueError(f"no RESOLVED_ENTITY.RECORDS[].RECORD_ID for {record_id}")
    return resolved["ENTITY_ID"], records


try:
    total_records = sum(1 for line in DATA_FILE.read_text(encoding="utf-8").splitlines() if line.strip())
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()

    entity_by_record = {}
    members = {}
    for rid in MERGE_CLUSTER + DISTRACTORS:
        eid, recs = entity_of(sz_engine, "VERIFY", rid)
        entity_by_record[rid] = eid
        members[eid] = recs

    distinct_entities = sorted(set(entity_by_record.values()))
    cluster_ids = {entity_by_record[r] for r in MERGE_CLUSTER}
    distractor_ids = {entity_by_record[r] for r in DISTRACTORS}
    cluster_entity = next(iter(cluster_ids))
    largest = max(len(v) for v in members.values())

    checks = {
        "a_entity_count": len(distinct_entities) == EXPECTED_ENTITIES,
        "b_merge_cluster_one_entity": len(cluster_ids) == 1 and len(members[cluster_entity]) == EXPECTED_MERGE_RECORD_COUNT,
        "c_fewer_entities_than_records": len(distinct_entities) < total_records,
        "d_distractor_singleton": distractor_ids.isdisjoint(cluster_ids)
        and all(len(members[e]) == 1 for e in distractor_ids),
    }
    result = {
        "records_loaded": total_records,
        "entities": len(distinct_entities),
        "expected_entities": EXPECTED_ENTITIES,
        "largest_cluster": largest,
        "cluster_entity_id": cluster_entity,
        "entity_by_record": entity_by_record,
        "checks": checks,
        "status": "passed" if all(checks.values()) else "mismatch",
    }
    print(json.dumps(result, indent=2))
    sys.exit(0 if all(checks.values()) else 2)
except (SzError, ValueError) as err:
    print(f"\n{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
