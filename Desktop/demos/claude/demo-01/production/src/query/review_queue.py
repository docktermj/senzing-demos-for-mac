#! /usr/bin/env python3
"""Query 2: which records are likely duplicates but were not merged (the human-review queue)?

Export rows carry RELATED_ENTITIES[] with MATCH_LEVEL_CODE (POSSIBLY_SAME / POSSIBLY_RELATED), MATCH_KEY and
RECORD_SUMMARY; each pair is kept once, keyed (min ENTITY_ID, max ENTITY_ID).
Usage: python3 src/query/review_queue.py [--level POSSIBLY_SAME]
Writes data/query_results/review_queue.json (every pair, highest-evidence match keys first).
"""
import argparse
import json
import sys
from collections import Counter
from pathlib import Path

from senzing import SzError

from sz_export import export_rows, open_engine

ap = argparse.ArgumentParser()
ap.add_argument("--level", default="POSSIBLY_SAME")
args = ap.parse_args()
try:
    factory, engine = open_engine(__file__)
    pairs = {}
    for row in export_rows(engine):
        ent = row["RESOLVED_ENTITY"]
        for rel in row.get("RELATED_ENTITIES", []):
            if rel.get("MATCH_LEVEL_CODE") != args.level:
                continue
            a, b = sorted((ent["ENTITY_ID"], rel["ENTITY_ID"]))
            pairs.setdefault((a, b), {"entity_ids": [a, b], "match_key": rel.get("MATCH_KEY"),
                                      "names": sorted({ent.get("ENTITY_NAME"), rel.get("ENTITY_NAME")} - {None}),
                                      "sources": sorted({s["DATA_SOURCE"] for s in ent.get("RECORD_SUMMARY", []) + rel.get("RECORD_SUMMARY", [])})})
    items = sorted(pairs.values(), key=lambda p: (-p["match_key"].count("+"), p["entity_ids"]))
    by_key = Counter(p["match_key"] for p in items)
    cross = sum(1 for p in items if len(p["sources"]) > 1)
    out = {"level": args.level, "pairs": len(items), "pairs_spanning_2plus_sources": cross,
           "by_match_key": dict(by_key.most_common()), "queue": items}
    Path("data/query_results").mkdir(parents=True, exist_ok=True)
    Path("data/query_results/review_queue.json").write_text(json.dumps(out, indent=2))
    print(json.dumps({**{k: v for k, v in out.items() if k != "queue"}, "top_of_queue": items[:5]}, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
