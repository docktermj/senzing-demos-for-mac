#! /usr/bin/env python3
"""Discover step 4b: explain why two records did (or did not) resolve together.

why_records(ds1, id1, ds2, id2, flags) with SZ_INCLUDE_FEATURE_SCORES | SZ_INCLUDE_MATCH_KEY_DETAILS |
SZ_ENTITY_INCLUDE_ALL_RELATIONS (get_sdk_reference flags/response_schemas for why_records). Parses
WHY_RESULTS[].MATCH_INFO.WHY_KEY / WHY_ERRULE_CODE / MATCH_LEVEL_CODE / WHY_KEY_DETAILS.CONFIRMATIONS[] and
FEATURE_SCORES (the why-side names; MATCH_KEY/ERRULE_CODE are entity-side only).
Usage: python3 src/query/why_pair.py DS1:ID1 DS2:ID2 [--raw]
"""
import json
import sys

from senzing import SzEngineFlags, SzError

from sz_export import open_engine

FLAGS = (SzEngineFlags.SZ_INCLUDE_FEATURE_SCORES | SzEngineFlags.SZ_INCLUDE_MATCH_KEY_DETAILS
         | SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_RELATIONS)
try:
    (d1, i1), (d2, i2) = (a.split(":", 1) for a in sys.argv[1:3])
    factory, engine = open_engine(__file__)
    doc = json.loads(engine.why_records(d1, i1, d2, i2, FLAGS))
    if "--raw" in sys.argv:
        print(json.dumps(doc, indent=1)); sys.exit(0)
    for w in doc.get("WHY_RESULTS", []):
        mi = w.get("MATCH_INFO", {})
        scores = {ft: [{"inbound": s.get("INBOUND_FEAT_DESC"), "candidate": s.get("CANDIDATE_FEAT_DESC"),
                        "score": s.get("SCORE"), "bucket": s.get("SCORE_BUCKET")} for s in lst]
                  for ft, lst in mi.get("FEATURE_SCORES", {}).items()}
        conf = [{k: c.get(k) for k in ("FTYPE_CODE", "TOKEN", "SCORE", "SCORE_BUCKET", "SOURCE")}
                for c in (mi.get("WHY_KEY_DETAILS") or {}).get("CONFIRMATIONS", [])]
        print(json.dumps({"entity_ids": [w.get("ENTITY_ID"), w.get("ENTITY_ID_2")],
                          "why_key": mi.get("WHY_KEY"), "rule": mi.get("WHY_ERRULE_CODE"),
                          "match_level": mi.get("MATCH_LEVEL_CODE"), "confirmations": conf,
                          "feature_scores": scores}, indent=1))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
