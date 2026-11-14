#!/usr/bin/env python3
"""Evaluate the loaded entity resolution results (Data processing module, Phase D).

Reads only files: the SDK export written by src/validate/export_entities.java
(data/validation/export.jsonl, data/validation/how.jsonl), the load-ready files in
data/senzing-ready/, and the scenario's answer key (passed as an argument; never loaded into
Senzing). No SDK calls and no SQL. Statistics follow reporting_guide(topic='evaluation'):
per-source records/entities/compression, cross-source overlap, entity size buckets, match
principle (ERRULE_CODE + MATCH_KEY) frequency, relationship categories deduplicated by
(min_id, max_id). Adds: the match-key suppressor audit (three buckets, own denominators), the
how-state audit (NEED_REEVALUATION / VIRTUAL_ENTITIES per multi-record entity), and pairwise
precision/recall against the answer key.

Usage: python3 -I src/validate/evaluate_results.py <answer_key.csv>
Writes data/validation/evaluation.json and prints a summary.
"""
import collections
import csv
import itertools
import json
import re
import sys

key_path = sys.argv[1]
rows = [json.loads(l) for l in open("data/validation/export.jsonl", encoding="utf-8") if l.strip()]
how = [json.loads(l) for l in open("data/validation/how.jsonl", encoding="utf-8") if l.strip()]

# ---- source records (for evidence) ---------------------------------------------------------
src = {}
for f in ["core_banking", "card_services", "credit_bureau", "business_registry", "watchlist"]:
    for l in open(f"data/senzing-ready/{f}.jsonl", encoding="utf-8"):
        r = json.loads(l)
        src[(r["DATA_SOURCE"], r["RECORD_ID"])] = r
loaded_total = len(src)


def label(k):
    """A short human description of one source record: name, DOB, first identifier."""
    r = src.get(k, {})
    name = dob = ident = rtype = None
    for f in r.get("FEATURES", []):
        rtype = f.get("RECORD_TYPE", rtype)
        if not name:
            name = f.get("NAME_ORG") or f.get("NAME_FULL") or " ".join(
                x for x in (f.get("NAME_FIRST"), f.get("NAME_MIDDLE"), f.get("NAME_LAST")) if x) or None
        dob = dob or f.get("DATE_OF_BIRTH")
        for a in ("SSN_NUMBER", "TAX_ID_NUMBER", "PASSPORT_NUMBER", "NATIONAL_ID_NUMBER", "LEI_NUMBER", "NPI_NUMBER"):
            if not ident and f.get(a):
                ident = f"{a.split('_')[0]} {f[a]}"
    return {"record": f"{k[0]}/{k[1]}", "type": rtype, "name": name, "dob": dob, "id": ident}


# ---- entities --------------------------------------------------------------------------------
ent_records = {}
rec_entity = {}
rec_matchkey = collections.Counter()
principles = collections.Counter()
suppress = {"single_source": collections.Counter(), "cross_source": collections.Counter(), "relationship": collections.Counter()}
compares = {"single_source": 0, "cross_source": 0, "relationship": 0}


def signed_features(key):
    """Split a match key into (sign, feature): roles removed first, backslash escapes honored."""
    key = re.sub(r"\([^)]*\)", "", key or "")
    out, sign, cur, i = [], None, "", 0
    while i < len(key):
        c = key[i]
        if c == "\\" and i + 1 < len(key):
            cur += key[i + 1]; i += 2; continue
        if c in "+-":
            if sign and cur: out.append((sign, cur))
            sign, cur = c, ""
        else:
            cur += c
        i += 1
    if sign and cur: out.append((sign, cur))
    return out


rels = {}
for row in rows:
    re_ = row["RESOLVED_ENTITY"]
    eid = re_["ENTITY_ID"]
    recs = [(r["DATA_SOURCE"], r["RECORD_ID"]) for r in re_["RECORDS"]]
    ent_records[eid] = recs
    sources = {r[0] for r in recs}
    bucket = "cross_source" if len(sources) > 1 else "single_source"
    for r in re_["RECORDS"]:
        rec_entity[(r["DATA_SOURCE"], r["RECORD_ID"])] = eid
        if r.get("MATCH_KEY"):
            principles[(r.get("ERRULE_CODE"), r["MATCH_KEY"])] += 1
            compares[bucket] += 1
            for s, f in signed_features(r["MATCH_KEY"]):
                if s == "-": suppress[bucket][f] += 1
    for rel in row.get("RELATED_ENTITIES", []) or []:
        pair = (min(eid, rel["ENTITY_ID"]), max(eid, rel["ENTITY_ID"]))
        rels[pair] = rel
for pair, rel in rels.items():
    compares["relationship"] += 1
    for s, f in signed_features(rel.get("MATCH_KEY")):
        if s == "-": suppress["relationship"][f] += 1

record_sum = sum(len(v) for v in ent_records.values())
rel_cats = collections.Counter(r.get("MATCH_LEVEL_CODE") for r in rels.values())

per_source = {}
for ds in sorted({k[0] for k in src}):
    recs = [k for k in src if k[0] == ds]
    ents = {rec_entity.get(k) for k in recs}
    per_source[ds] = {"records": len(recs), "entities": len(ents),
                      "compression": round(1 - len(ents) / len(recs), 3)}

sizes = collections.Counter()
for v in ent_records.values():
    n = len(v)
    sizes["1" if n == 1 else "2" if n == 2 else "3-5" if n <= 5 else "6-10" if n <= 10 else "11+"] += 1

cross = [eid for eid, v in ent_records.items() if len({r[0] for r in v}) > 1]
pair_counts = collections.Counter()
for eid in cross:
    for a, b in itertools.combinations(sorted({r[0] for r in ent_records[eid]}), 2):
        pair_counts[f"{a} + {b}"] += 1

# ---- how-state audit ---------------------------------------------------------------------------
multi = [eid for eid, v in ent_records.items() if len(v) >= 2]
how_by = {h["ENTITY_ID"]: h["HOW"] for h in how}
unsettled, checked = [], 0
for eid in multi:
    fs = (how_by.get(eid) or {}).get("HOW_RESULTS", {}).get("FINAL_STATE")
    if fs is None:
        continue
    checked += 1
    signs = []
    if fs.get("NEED_REEVALUATION"): signs.append("NEED_REEVALUATION=%s" % fs["NEED_REEVALUATION"])
    if len(fs.get("VIRTUAL_ENTITIES", []) or []) > 1: signs.append("%d virtual entities" % len(fs["VIRTUAL_ENTITIES"]))
    if signs:
        unsettled.append({"entity_id": eid, "name": label(ent_records[eid][0])["name"], "signs": signs})

# ---- accuracy against the answer key ----------------------------------------------------------
key = collections.defaultdict(set)
rec_cluster = {}
for r in csv.DictReader(open(key_path, encoding="utf-8")):
    k = (r["DATA_SOURCE"], r["RECORD_ID"])
    key[r["CLUSTER_ID"]].add(k)
    rec_cluster[k] = r["CLUSTER_ID"]


def pairs(groups):
    s = set()
    for g in groups:
        for a, b in itertools.combinations(sorted(g), 2):
            s.add((a, b))
    return s


truth_pairs = pairs(key.values())
found_pairs = pairs(set(v) for v in ent_records.values())
tp = len(truth_pairs & found_pairs)
precision = tp / len(found_pairs) if found_pairs else 1.0
recall = tp / len(truth_pairs) if truth_pairs else 1.0
exact = sum(1 for g in key.values() if {rec_entity.get(k) for k in g} == {rec_entity.get(next(iter(g)))}
            and set(ent_records[rec_entity.get(next(iter(g)))]) == g)
over = []   # entities holding records from more than one true cluster
for eid, v in ent_records.items():
    cl = {rec_cluster.get(k) for k in v}
    if len(cl) > 1:
        over.append({"entity_id": eid, "clusters": sorted(cl), "records": [label(k) for k in v]})
split = []  # true clusters spread over more than one entity
for cid, g in key.items():
    ents = {rec_entity.get(k) for k in g}
    if len(ents) > 1:
        rel_between = [rels[(min(a, b), max(a, b))].get("MATCH_LEVEL_CODE") + " " + (rels[(min(a, b), max(a, b))].get("MATCH_KEY") or "")
                       for a, b in itertools.combinations(sorted(e for e in ents if e), 2) if (min(a, b), max(a, b)) in rels]
        split.append({"cluster": cid, "entities": sorted(e for e in ents if e), "records": [label(k) for k in sorted(g)],
                      "relationships_between": rel_between})

out = {
    "entities": len(ent_records), "records_in_export": record_sum, "records_loaded": loaded_total,
    "per_source": per_source, "entity_sizes": dict(sizes),
    "cross_source_entities": len(cross), "cross_source_pairs": dict(pair_counts.most_common()),
    "match_principles": [{"errule": k[0], "match_key": k[1], "count": c} for k, c in principles.most_common()],
    "relationships": dict(rel_cats), "relationship_pairs": len(rels),
    "suppressors": {b: {"comparisons": compares[b], "features": dict(suppress[b].most_common())} for b in suppress},
    "how_audit": {"M": len(multi), "checked": checked, "unsettled": unsettled},
    "accuracy": {"truth_clusters": len(key), "true_pairs": len(truth_pairs), "found_pairs": len(found_pairs),
                 "true_positive_pairs": tp, "pair_precision": round(precision, 4), "pair_recall": round(recall, 4),
                 "clusters_exactly_resolved": exact, "over_merged_entities": over, "split_clusters": split},
}
json.dump(out, open("data/validation/evaluation.json", "w"), indent=1, ensure_ascii=False)
print(json.dumps({k: v for k, v in out.items() if k not in ("accuracy", "match_principles")}, indent=1, ensure_ascii=False))
print("top match principles:", [(m["match_key"], m["count"]) for m in out["match_principles"][:12]])
a = out["accuracy"]
print("accuracy:", {k: a[k] for k in a if k not in ("over_merged_entities", "split_clusters")})
print("over-merged:", json.dumps(a["over_merged_entities"], ensure_ascii=False))
print("split clusters:", json.dumps(a["split_clusters"], ensure_ascii=False))
