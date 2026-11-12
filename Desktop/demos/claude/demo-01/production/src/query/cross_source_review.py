#! /usr/bin/env python3
"""Check resolved entities against a ground-truth answer key (record -> real person).

Inputs (both passed as arguments; nothing is read from a fixed path):
  ENTITY_MAP  JSON {entity_id: ["DS:RECORD_ID", ...]} written by src/query/record_counts.py --entity-map
  TRUTH_CSV   CSV with columns data_source, record_id, person_id
Computes, per reporting_guide(topic='quality'): pairwise precision, recall and F1; splits (a true person spread
over several entities) and merges (an entity holding several true persons); and a reproducible sample of
cross-source entities (seed 6) and multi-record entities judged correct when all their records are one person.
Usage: python3 src/query/cross_source_review.py ENTITY_MAP TRUTH_CSV [--sample 20]
"""

import argparse
import csv
import json
import random
from collections import defaultdict
from itertools import combinations

ap = argparse.ArgumentParser()
ap.add_argument("entity_map")
ap.add_argument("truth_csv")
ap.add_argument("--sample", type=int, default=20)
args = ap.parse_args()

emap = {int(k): v for k, v in json.load(open(args.entity_map, encoding="utf-8")).items()}
truth = {f"{r['data_source']}:{r['record_id']}": r["person_id"] for r in csv.DictReader(open(args.truth_csv, encoding="utf-8"))}
rec_entity = {rec: eid for eid, recs in emap.items() for rec in recs}

def pairs(groups):
    out = set()
    for g in groups:
        out.update(tuple(sorted(p)) for p in combinations(g, 2))
    return out

by_person = defaultdict(list)
for rec, pid in truth.items():
    by_person[pid].append(rec)
resolved_pairs, true_pairs = pairs(emap.values()), pairs(by_person.values())
tp = len(resolved_pairs & true_pairs)
precision = tp / len(resolved_pairs) if resolved_pairs else None
recall = tp / len(true_pairs) if true_pairs else None
f1 = 2 * precision * recall / (precision + recall) if precision and recall else None
splits = [pid for pid, recs in by_person.items() if len({rec_entity[r] for r in recs}) > 1]
merges = [eid for eid, recs in emap.items() if len({truth[r] for r in recs}) > 1]

rng = random.Random(6)
cross = sorted(eid for eid, recs in emap.items() if len({r.split(':')[0] for r in recs}) >= 2)
multi = sorted(eid for eid, recs in emap.items() if len(recs) >= 2)
def judge(eids):
    rows = [{"entity_id": e, "records": emap[e], "persons": sorted({truth[r] for r in emap[e]}),
             "correct": len({truth[r] for r in emap[e]}) == 1} for e in eids]
    return rows, sum(r["correct"] for r in rows)
cross_rows, cross_ok = judge(rng.sample(cross, min(args.sample, len(cross))))
spot_rows, spot_ok = judge(rng.sample(multi, min(10, len(multi))))

print(json.dumps({
    "records": len(truth), "true_persons": len(by_person), "resolved_entities": len(emap),
    "pairwise": {"resolved_pairs": len(resolved_pairs), "true_pairs": len(true_pairs), "correct_pairs": tp,
                 "precision": round(precision, 4), "recall": round(recall, 4), "f1": round(f1, 4)},
    "splits": len(splits), "merges": len(merges), "merge_entities": merges[:10],
    "cross_source_sample": {"checked": len(cross_rows), "correct": cross_ok, "rows": cross_rows},
    "uat_spot_check": {"checked": len(spot_rows), "correct": spot_ok, "rows": spot_rows},
}, indent=2))
