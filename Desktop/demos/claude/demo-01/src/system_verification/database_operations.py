#! /usr/bin/env python3
"""Step 8: write, read and search operations through the SDK (no SQL).

Calls (get_sdk_reference, methods + response_schemas):
  SzEngine.get_record(data_source_code, record_id, flags) -> JSON string
  SzEngine.get_entity_by_record_id(data_source_code, record_id, flags) -> JSON string
  SzEngine.get_entity_by_entity_id(entity_id, flags) -> JSON string
  SzEngine.search_by_attributes(attributes, flags, search_profile) -> JSON string
Fields read:
  get_record: RECORD_ID
  get_entity_*: RESOLVED_ENTITY.ENTITY_ID, .ENTITY_NAME, .RECORDS[].DATA_SOURCE, .RECORDS[].RECORD_ID
  search_by_attributes: RESOLVED_ENTITIES[].ENTITY.RESOLVED_ENTITY.ENTITY_ID
"""

import json
import os
import sys
import time
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
DATA_SOURCE = "VERIFY"
STEP2_FILE = Path("src/system_verification/verification_data.jsonl")
MARI_FILE = Path("src/system_verification/mari_record.jsonl")
MERGE_RECORD = "V-1001"
TIMEOUT_SECONDS = 30

GET_FLAGS = SzEngineFlags.SZ_ENTITY_INCLUDE_ENTITY_NAME | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA
SEARCH_FLAGS = (
    SzEngineFlags.SZ_SEARCH_INCLUDE_RESOLVED
    | SzEngineFlags.SZ_SEARCH_INCLUDE_POSSIBLY_SAME
    | SzEngineFlags.SZ_ENTITY_INCLUDE_ENTITY_NAME
    | SzEngineFlags.SZ_ENTITY_INCLUDE_RECORD_DATA
)

# Attributes taken from the synthetic Marisol record (verification_data.jsonl, V-1001).
SEARCH_ATTRIBUTES = {
    "NAME_FIRST": "Marisol",
    "NAME_LAST": "Vandergriff",
    "ADDR_LINE1": "742 Evergreen Terrace",
    "ADDR_CITY": "Springfield",
    "ADDR_STATE": "OR",
    "ADDR_POSTAL_CODE": "97477",
}


def record_ids(path):
    ids = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            ids.append(json.loads(line)["RECORD_ID"])
    return ids


def timed(label, func):
    start = time.monotonic()
    value = func()
    elapsed = time.monotonic() - start
    if elapsed > TIMEOUT_SECONDS:
        raise TimeoutError(f"{label} took {elapsed:.1f}s (limit {TIMEOUT_SECONDS}s)")
    return value, round(elapsed * 1000)


results = {}
try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    engine = sz_factory.create_engine()

    # 1. Write count: every record loaded in Steps 6 and 7a is retrievable from the engine.
    expected_ids = record_ids(STEP2_FILE) + (record_ids(MARI_FILE) if MARI_FILE.exists() else [])

    def count_records():
        found = []
        for rid in expected_ids:
            record = json.loads(engine.get_record(DATA_SOURCE, rid))
            found.append(record.get("RECORD_ID"))
        return found

    found, ms = timed("write", count_records)
    results["write"] = {
        "expected": len(expected_ids),
        "found": len(found),
        "ok": sorted(found) == sorted(expected_ids),
        "ms": ms,
    }

    # 2. Read by entity ID: the merge-cluster entity, from Marisol's record.
    def read_entity():
        by_record = json.loads(engine.get_entity_by_record_id(DATA_SOURCE, MERGE_RECORD, GET_FLAGS))
        entity_id = by_record["RESOLVED_ENTITY"]["ENTITY_ID"]
        entity = json.loads(engine.get_entity_by_entity_id(entity_id, GET_FLAGS))["RESOLVED_ENTITY"]
        return entity_id, entity

    (entity_id, entity), ms = timed("read", read_entity)
    keys = [(r.get("DATA_SOURCE"), r.get("RECORD_ID")) for r in entity.get("RECORDS", [])]
    name = entity.get("ENTITY_NAME", "")
    results["read"] = {
        "entity_id": entity_id,
        "record_keys": [f"{ds}:{rid}" for ds, rid in keys],
        "entity_name": name,
        "ok": entity.get("ENTITY_ID") == entity_id and bool(keys) and "Vandergriff" in name,
        "ms": ms,
    }

    # 3. Search by attributes: the cluster entity must appear in the results.
    def search():
        response = json.loads(engine.search_by_attributes(json.dumps(SEARCH_ATTRIBUTES), SEARCH_FLAGS))
        return [
            item["ENTITY"]["RESOLVED_ENTITY"]["ENTITY_ID"]
            for item in response.get("RESOLVED_ENTITIES", [])
        ]

    hits, ms = timed("search", search)
    results["search"] = {"entity_ids_returned": hits, "ok": entity_id in hits, "ms": ms}

    status = "passed" if all(r["ok"] for r in results.values()) else "failed"
    print(json.dumps({"status": status, "operations": results}, indent=2))
    sys.exit(0 if status == "passed" else 2)
except (SzError, KeyError, TimeoutError, ValueError) as err:
    print(f"\n{err.__class__.__name__} - {err}", file=sys.stderr)
    print(json.dumps({"status": "failed", "operations": results}, indent=2))
    sys.exit(1)
