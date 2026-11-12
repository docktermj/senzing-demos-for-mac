#! /usr/bin/env python3
"""ER evaluation statistics in one pass over the export stream.

From reporting_guide(topic='evaluation') pattern export_with_stats (Senzing MCP server), unchanged in its
SDK calls: export_json_entity_report(SZ_EXPORT_DEFAULT_FLAGS) / fetch_next / close_export_report.
Fine for POC volumes; at production scale use INFO messages (SZ_WITH_INFO) and an incremental mart.
Usage: python3 src/query/record_counts.py [--entity-map OUT.json]   (optionally write entity_id -> records)
"""

import argparse
import json
import os
import sys
from collections import defaultdict
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def collect_er_stats(sz_engine, entity_map=None):
    source_records = defaultdict(int)
    source_entities = defaultdict(set)
    entity_sizes = defaultdict(int)
    entity_sources = defaultdict(set)
    match_principles = defaultdict(int)
    relationship_pairs = set()
    rel_categories = defaultdict(int)
    size_samples = defaultdict(list)
    handle = sz_engine.export_json_entity_report(SzEngineFlags.SZ_EXPORT_DEFAULT_FLAGS)
    try:
        while True:
            row = sz_engine.fetch_next(handle)
            if not row:
                break
            entity = json.loads(row)
            resolved = entity.get("RESOLVED_ENTITY", {})
            eid = resolved.get("ENTITY_ID")
            records = resolved.get("RECORDS", [])
            if entity_map is not None:
                entity_map[eid] = [f"{r.get('DATA_SOURCE')}:{r.get('RECORD_ID')}" for r in records]
            for rec in records:
                ds = rec.get("DATA_SOURCE", "UNKNOWN")
                source_records[ds] += 1
                source_entities[ds].add(eid)
                entity_sources[eid].add(ds)
                mk, er = rec.get("MATCH_KEY", ""), rec.get("ERRULE_CODE", "")
                if mk or er:
                    match_principles[(er, mk)] += 1
            entity_sizes[eid] = len(records)
            for rel in entity.get("RELATED_ENTITIES", []):
                pair = (min(eid, rel.get("ENTITY_ID")), max(eid, rel.get("ENTITY_ID")))
                if pair not in relationship_pairs:
                    relationship_pairs.add(pair)
                    rel_categories[rel.get("MATCH_LEVEL_CODE", "UNKNOWN")] += 1
    finally:
        sz_engine.close_export_report(handle)
    buckets = {"1": 0, "2": 0, "3-5": 0, "6-10": 0, "11-50": 0, "51+": 0}
    for eid, size in entity_sizes.items():
        b = "1" if size == 1 else "2" if size == 2 else "3-5" if size <= 5 else "6-10" if size <= 10 else "11-50" if size <= 50 else "51+"
        buckets[b] += 1
        if len(size_samples[b]) < 5:
            size_samples[b].append(eid)
    srcs = sorted(source_records)
    return {
        "per_source": {ds: {"record_count": source_records[ds], "entity_count": len(source_entities[ds]),
                            "compression_rate": round(1 - len(source_entities[ds]) / source_records[ds], 4)} for ds in srcs},
        "cross_source": {f"{a}|{b}": len(source_entities[a] & source_entities[b]) for i, a in enumerate(srcs) for b in srcs[i + 1:]},
        "entities_in_all_sources": sum(1 for s in entity_sources.values() if len(s) == len(srcs)),
        "entities_in_2plus_sources": sum(1 for s in entity_sources.values() if len(s) >= 2),
        "entity_size_distribution": buckets,
        "size_bucket_samples": dict(size_samples),
        "total_entities": len(entity_sizes),
        "total_records": sum(source_records.values()),
        "match_principles": [{"errule_code": k[0], "match_key": k[1], "count": v}
                             for k, v in sorted(match_principles.items(), key=lambda x: -x[1])[:25]],
        "relationships": dict(rel_categories),
        "relationship_count": len(relationship_pairs),
    }


ap = argparse.ArgumentParser()
ap.add_argument("--entity-map", default=None)
args = ap.parse_args()
try:
    sz_factory = SzAbstractFactoryCore(Path(__file__).stem, SETTINGS, verbose_logging=False)
    engine = sz_factory.create_engine()
    emap = {} if args.entity_map else None
    stats = collect_er_stats(engine, emap)
    if args.entity_map:
        Path(args.entity_map).write_text(json.dumps(emap), encoding="utf-8")
    print(json.dumps(stats, indent=2))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
