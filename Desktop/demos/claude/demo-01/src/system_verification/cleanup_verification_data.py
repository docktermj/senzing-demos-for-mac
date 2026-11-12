#! /usr/bin/env python3
"""Step 10: purge the synthetic VERIFY records through the SDK (no SQL).

Calls (get_sdk_reference, methods):
  SzEngine.delete_record(data_source_code, record_id, flags) -> JSON string
  SzEngine.get_record(data_source_code, record_id, flags) -> JSON string  (raises SzNotFoundError once gone)
  SzEngine.search_by_attributes(attributes, flags, search_profile) -> JSON string
Record IDs come from BOTH files: the Step 2 data and Step 7a's Mari record.
Only DATA_SOURCE VERIFY is touched; nothing else in the repository is deleted.
"""

import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError, SzNotFoundError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
DATA_SOURCE = "VERIFY"
FILES = [
    Path("src/system_verification/verification_data.jsonl"),
    Path("src/system_verification/mari_record.jsonl"),
]
SEARCH_ATTRIBUTES = {"NAME_LAST": "Vandergriff", "ADDR_LINE1": "742 Evergreen Terrace"}
SEARCH_FLAGS = SzEngineFlags.SZ_SEARCH_INCLUDE_RESOLVED | SzEngineFlags.SZ_SEARCH_INCLUDE_POSSIBLY_SAME


def verify_record_ids():
    ids = []
    for path in FILES:
        if not path.exists():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            record = json.loads(line)
            if record.get("DATA_SOURCE") == DATA_SOURCE:
                ids.append(record["RECORD_ID"])
    return ids


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    engine = sz_factory.create_engine()

    deleted, failed = [], []
    for rid in verify_record_ids():
        try:
            engine.delete_record(DATA_SOURCE, rid)
            deleted.append(rid)
        except SzError as err:
            failed.append({"record_id": rid, "error": f"{err.__class__.__name__} - {err}"})

    remaining = []
    for rid in verify_record_ids():
        try:
            engine.get_record(DATA_SOURCE, rid)
            remaining.append(rid)
        except SzNotFoundError:
            pass

    search = json.loads(engine.search_by_attributes(json.dumps(SEARCH_ATTRIBUTES), SEARCH_FLAGS))
    search_hits = len(search.get("RESOLVED_ENTITIES", []))

    status = "passed" if not failed and not remaining and search_hits == 0 else "failed"
    print(json.dumps({
        "status": status,
        "deleted": deleted,
        "failed": failed,
        "verify_records_remaining": remaining,
        "search_hits_after_purge": search_hits,
    }, indent=2))
    sys.exit(0 if status == "passed" else 2)
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
