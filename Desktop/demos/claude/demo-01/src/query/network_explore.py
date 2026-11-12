#! /usr/bin/env python3
"""Discover step 4d: relationship networks and shortest paths.

find_network_by_entity_id(entity_ids: List[int], max_degrees, build_out_degrees, build_out_max_entities, flags)
  -> ENTITIES[].RESOLVED_ENTITY.ENTITY_ID/ENTITY_NAME/RECORD_SUMMARY[], ENTITY_NETWORK_LINKS[] with
     MIN_ENTITY_ID/MAX_ENTITY_ID (undirected), MATCH_KEY, MATCH_LEVEL_CODE, IS_DISCLOSED.
find_path_by_entity_id(start, end, max_degrees, flags=...) -> ENTITY_PATHS[].ENTITIES[] (ordered IDs) and
  ENTITY_PATH_LINKS[] (MIN_/MAX_ENTITY_ID, MATCH_KEY, MATCH_LEVEL_CODE).
Flags: SZ_FIND_NETWORK_DEFAULT_FLAGS / SZ_FIND_PATH_DEFAULT_FLAGS (matching info + entity name + record summary).
Usage: python3 src/query/network_explore.py network ID [ID ...]
       python3 src/query/network_explore.py path START END [MAX_DEGREES]
       python3 src/query/network_explore.py hubpath HUB_ID   (find a 2+ degree pair among the hub's neighbors)
"""
import itertools
import json
import sys

from senzing import SzEngineFlags, SzError

from sz_export import open_engine

NET_FLAGS = SzEngineFlags.SZ_FIND_NETWORK_DEFAULT_FLAGS
PATH_FLAGS = SzEngineFlags.SZ_FIND_PATH_DEFAULT_FLAGS
REL_FLAGS = SzEngineFlags.SZ_ENTITY_INCLUDE_ALL_RELATIONS


def names(doc):
    return {e["RESOLVED_ENTITY"]["ENTITY_ID"]: {"name": e["RESOLVED_ENTITY"].get("ENTITY_NAME"),
            "sources": {s["DATA_SOURCE"]: s["RECORD_COUNT"] for s in e["RESOLVED_ENTITY"].get("RECORD_SUMMARY", [])}}
            for e in doc.get("ENTITIES", [])}


def path(engine, a, b, deg=3):
    doc = json.loads(engine.find_path_by_entity_id(a, b, deg, flags=PATH_FLAGS))
    ids = (doc.get("ENTITY_PATHS") or [{}])[0].get("ENTITIES", [])
    links = {(l["MIN_ENTITY_ID"], l["MAX_ENTITY_ID"]): (l.get("MATCH_LEVEL_CODE"), l.get("MATCH_KEY"))
             for l in doc.get("ENTITY_PATH_LINKS", [])}
    n = names(doc)
    hops = [{"from": x, "to": y, "link": links.get((min(x, y), max(x, y)))} for x, y in zip(ids, ids[1:])]
    return {"path": [{"entity_id": i, **n.get(i, {})} for i in ids], "degrees": max(len(ids) - 1, 0), "hops": hops}


try:
    factory, engine = open_engine(__file__)
    mode = sys.argv[1]
    if mode == "network":
        ids = [int(x) for x in sys.argv[2:]]
        doc = json.loads(engine.find_network_by_entity_id(ids, 3, 1, 50, NET_FLAGS))
        print(json.dumps({"entities": names(doc), "links": [
            {"a": l["MIN_ENTITY_ID"], "b": l["MAX_ENTITY_ID"], "level": l.get("MATCH_LEVEL_CODE"),
             "match_key": l.get("MATCH_KEY"), "disclosed": l.get("IS_DISCLOSED")} for l in doc.get("ENTITY_NETWORK_LINKS", [])],
            "paths": [{"start": p["START_ENTITY_ID"], "end": p["END_ENTITY_ID"], "entities": p["ENTITIES"]}
                      for p in doc.get("ENTITY_PATHS", [])]}, indent=1))
    elif mode == "path":
        print(json.dumps(path(engine, int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]) if len(sys.argv) > 4 else 3), indent=1))
    elif mode == "hubpath":
        hub = int(sys.argv[2])
        rel = lambda e: {r["ENTITY_ID"] for r in json.loads(engine.get_entity_by_entity_id(e, REL_FLAGS)).get("RELATED_ENTITIES", [])}
        nb = sorted(rel(hub))
        for a, b in itertools.combinations(nb, 2):
            if b in rel(a):
                continue
            p = path(engine, a, b)
            if p["degrees"] >= 2:
                print(json.dumps({"hub": hub, **p}, indent=1))
                break
        else:
            print(json.dumps({"hub": hub, "found": False}))
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
    sys.exit(1)
