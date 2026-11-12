#! /usr/bin/env python3
"""Match-key audit: count suppressed features (a '-FEATURE' in a match key) in three separate buckets.

One export pass with SZ_EXPORT_DEFAULT_FLAGS (reporting_guide topic='evaluation'): per-record keys from
RESOLVED_ENTITY.RECORDS[].MATCH_KEY ('+' contributed, '-' detracted) and relationship keys from
RELATED_ENTITIES[].MATCH_KEY (deduplicated by (min_id, max_id)). Buckets are reported against their own totals:
  single_source_records  records merged within one source despite the feature disagreeing
  cross_source_records   records merged across sources despite it disagreeing (the mapping signal)
  relationships          entities Senzing declined to merge because it disagreed (restraint, not a defect)
Key parsing: drop "(...)" roles first, honour '\\' escapes, split on unescaped '+' / '-'.
"""

import json
import os
import re
import sys
from collections import Counter
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def signed_features(key):
    key = re.sub(r"\([^)]*\)", "", key or "")
    feats, sign, cur, esc = [], None, "", False
    for ch in key:
        if esc:
            cur += ch; esc = False
        elif ch == "\\":
            esc = True
        elif ch in "+-":
            if sign and cur:
                feats.append((sign, cur))
            sign, cur = ch, ""
        else:
            cur += ch
    if sign and cur:
        feats.append((sign, cur))
    return feats


try:
    sz_factory = SzAbstractFactoryCore(Path(__file__).stem, SETTINGS, verbose_logging=False)
    engine = sz_factory.create_engine()
    buckets = {b: {"keys": 0, "with_suppressor": 0, "suppressors": Counter()} for b in
               ("single_source_records", "cross_source_records", "relationships")}
    seen_rel, first_keys, rel_entity = set(), None, None
    handle = engine.export_json_entity_report(SzEngineFlags.SZ_EXPORT_DEFAULT_FLAGS)
    try:
        while True:
            row = engine.fetch_next(handle)
            if not row:
                break
            ent = json.loads(row)
            if first_keys is None:
                first_keys = sorted(ent)
            res = ent["RESOLVED_ENTITY"]
            recs = res.get("RECORDS", [])
            b = "cross_source_records" if len({r["DATA_SOURCE"] for r in recs}) > 1 else "single_source_records"
            for r in recs:
                if r.get("MATCH_KEY"):
                    neg = [f for s, f in signed_features(r["MATCH_KEY"]) if s == "-"]
                    buckets[b]["keys"] += 1
                    buckets[b]["with_suppressor"] += bool(neg)
                    buckets[b]["suppressors"].update(neg)
            for rel in ent.get("RELATED_ENTITIES", []):
                pair = (min(res["ENTITY_ID"], rel["ENTITY_ID"]), max(res["ENTITY_ID"], rel["ENTITY_ID"]))
                if rel_entity is None:
                    rel_entity = res["ENTITY_ID"]
                if pair in seen_rel:
                    continue
                seen_rel.add(pair)
                neg = [f for s, f in signed_features(rel.get("MATCH_KEY")) if s == "-"]
                buckets["relationships"]["keys"] += 1
                buckets["relationships"]["with_suppressor"] += bool(neg)
                buckets["relationships"]["suppressors"].update(neg)
    finally:
        engine.close_export_report(handle)
    out = {"first_row_top_level_keys": first_keys, "carries_RELATED_ENTITIES": "RELATED_ENTITIES" in (first_keys or []),
           "relationship_reader_proven": rel_entity is not None, "example_entity_with_relationship": rel_entity}
    for b, v in buckets.items():
        out[b] = {"keys": v["keys"], "with_suppressor": v["with_suppressor"],
                  "share": round(v["with_suppressor"] / v["keys"], 4) if v["keys"] else None,
                  "top_suppressors": v["suppressors"].most_common(5)}
    print(json.dumps(out, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
