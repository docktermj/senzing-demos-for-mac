#! /usr/bin/env python3
"""How-state audit: for EVERY entity with 2+ records, ask the engine for its construction history.

Calls: export_json_entity_report(SZ_EXPORT_DEFAULT_FLAGS) to list multi-record entities (no sampling), then
how_entity_by_entity_id(entity_id) with its default flags (get_sdk_reference, Python binding).
Reads HOW_RESULTS.FINAL_STATE.NEED_REEVALUATION and HOW_RESULTS.FINAL_STATE.VIRTUAL_ENTITIES[]; an entity is
flagged when NEED_REEVALUATION is non-zero or there is more than one virtual entity. A response without
FINAL_STATE counts as not measured. Reports "checked N of M"; offers no remedy (the flag is undocumented).
Usage: python3 src/query/how_state_audit.py [--dump-one]
"""

import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

try:
    sz_factory = SzAbstractFactoryCore(Path(__file__).stem, SETTINGS, verbose_logging=False)
    engine = sz_factory.create_engine()
    multi, exported, record_sum = [], 0, 0
    handle = engine.export_json_entity_report(SzEngineFlags.SZ_EXPORT_DEFAULT_FLAGS)
    try:
        while True:
            row = engine.fetch_next(handle)
            if not row:
                break
            res = json.loads(row)["RESOLVED_ENTITY"]
            exported += 1
            n = len(res.get("RECORDS", []))
            record_sum += n
            if n >= 2:
                multi.append((res["ENTITY_ID"], res.get("ENTITY_NAME")))
    finally:
        engine.close_export_report(handle)
    if "--dump-one" in sys.argv and multi:
        print(json.dumps(sorted(json.loads(engine.how_entity_by_entity_id(multi[0][0]))), indent=None))
        print(json.dumps(json.loads(engine.how_entity_by_entity_id(multi[0][0])).get("HOW_RESULTS", {}).get("FINAL_STATE"), indent=None)[:400])
        sys.exit(0)
    checked, flagged, failed = 0, [], 0
    for eid, name in multi:
        try:
            fs = json.loads(engine.how_entity_by_entity_id(eid)).get("HOW_RESULTS", {}).get("FINAL_STATE")
        except SzError:
            failed += 1
            continue
        if fs is None:
            failed += 1
            continue
        checked += 1
        signs = []
        if fs.get("NEED_REEVALUATION"):
            signs.append(f"NEED_REEVALUATION={fs['NEED_REEVALUATION']}")
        if len(fs.get("VIRTUAL_ENTITIES", [])) > 1:
            signs.append(f"{len(fs['VIRTUAL_ENTITIES'])} virtual entities")
        if signs:
            flagged.append({"entity_id": eid, "name": name, "signs": signs})
    m = len(multi)
    outcome = ("finding" if flagged else "no finding") if checked == m and m else ("nothing to check" if m == 0 else "could not measure")
    print(json.dumps({"exported_entities": exported, "record_sum": record_sum, "M": m, "checked": checked,
                      "not_checked": failed, "unsettled": len(flagged), "flagged": flagged[:25], "outcome": outcome}, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
