#! /usr/bin/env python3
"""Truth Set visualization, Step 4: purge the Truth Set records through the SDK (no SQL).

Calls (get_sdk_reference, methods):
  SzEngine.delete_record(data_source_code, record_id, flags) -> JSON string
  SzEngine.get_record(data_source_code, record_id, flags) -> JSON string  (raises SzNotFoundError once gone)
Only the (DATA_SOURCE, RECORD_ID) pairs in the Truth Set file are touched; other database state is kept.
"""

import json
import os
import sys
from pathlib import Path

from senzing import SzError, SzNotFoundError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
DATA_FILE = Path(sys.argv[1] if len(sys.argv) > 1 else "src/system_verification/truthset_data.jsonl")


def record_keys(path):
    keys = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            record = json.loads(line)
            keys.append((record["DATA_SOURCE"], record["RECORD_ID"]))
    return keys


try:
    keys = record_keys(DATA_FILE)
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    engine = sz_factory.create_engine()

    deleted, failed = 0, []
    for data_source, record_id in keys:
        try:
            engine.delete_record(data_source, record_id)
            deleted += 1
        except SzError as err:
            failed.append({"record": f"{data_source}:{record_id}", "error": f"{err.__class__.__name__} - {err}"})

    remaining = []
    for data_source, record_id in keys:
        try:
            engine.get_record(data_source, record_id)
            remaining.append(f"{data_source}:{record_id}")
        except SzNotFoundError:
            pass

    by_source = {}
    for data_source, _ in keys:
        by_source[data_source] = by_source.get(data_source, 0) + 1

    status = "passed" if not failed and not remaining else "failed"
    print(json.dumps({
        "status": status,
        "records_in_file": len(keys),
        "by_source": by_source,
        "deleted": deleted,
        "failed": failed,
        "truthset_records_remaining": remaining,
    }, indent=2))
    sys.exit(0 if status == "passed" else 2)
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
