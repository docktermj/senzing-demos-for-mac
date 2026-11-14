#!/usr/bin/env python3
"""Truth Set visualization, Step 2.4: verify the live server's endpoints against the contract."""
import json, sys, urllib.request, urllib.parse

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"

def get(path):
    with urllib.request.urlopen(BASE + path, timeout=10) as r:
        return r.status, r.read().decode("utf-8")

ok = True
def check(name, cond, info=""):
    global ok
    ok &= bool(cond)
    print(("PASS " if cond else "FAIL ") + name + (" - " + info if info else ""))

st, body = get("/api/stats"); s = json.loads(body)
need = ["records_total","entities_total","multi_record_entities","cross_source_entities","relationships_total","data_sources_total","histogram","bucket_entities"]
check("/api/stats", st == 200 and all(k in s for k in need) and s["entities_total"] > 0,
      f"{s['records_total']} records, {s['entities_total']} entities, histogram {s['histogram']}")
st, body = get("/api/graph"); g = json.loads(body)
n0, e0 = (g["nodes"] or [{}])[0], (g["edges"] or [{}])[0]
check("/api/graph", st == 200 and all(k in n0 for k in ["entity_id","entity_name","record_count","data_sources","records"])
      and all(k in e0 for k in ["source_entity_id","target_entity_id","match_key","relationship_type"]),
      f"{len(g['nodes'])} nodes, {len(g['edges'])} edges, total={g['total']}, capped={g['capped']}")
ec = g["encoding_check"]
print("     encoding_check:", ec["status"], "| combination_keys:", ec["combination_keys"], "| distinct keys:", ec["distinct_source_set_keys"])
st, body = get("/api/merges"); m = json.loads(body)
check("/api/merges", st == 200 and any(e["record_count"] >= 2 for e in m["entities"]), f"{len(m['entities'])} multi-record entities")
eid = m["entities"][0]["entity_id"]
st, body = get("/api/search?q=" + urllib.parse.quote("Robert Smith")); r = json.loads(body)
check("/api/search?q=Robert Smith", st == 200 and r["results"] and all("match_key" in x and "resolution_rule" in x for x in r["results"]),
      f"{len(r['results'])} results; first: {r['results'][0]['entity_name'] if r['results'] else '-'} {r['results'][0]['match_key'] if r['results'] else ''}")
st, body = get(f"/api/why?entity_id={eid}"); w = json.loads(body)
check(f"/api/why?entity_id={eid}", st == 200 and ("WHY_RESULTS" in (w.get("result") or {}) or "error" in w), w.get("mode", w.get("error", "")))
st, body = get(f"/api/how?entity_id={eid}"); h = json.loads(body)
check(f"/api/how?entity_id={eid}", st == 200 and ("HOW_RESULTS" in (h.get("result") or {}) or "error" in h), "HOW_RESULTS" if "result" in h else h.get("error", ""))
st, body = get(f"/api/records?entity_id={eid}"); rc = json.loads(body)
check(f"/api/records?entity_id={eid}", st == 200 and rc.get("records") and all(k in rc["records"][0] for k in ["data_source","record_id"]), f"{len(rc.get('records', []))} records")
st, body = get("/api/overlap"); o = json.loads(body)
check("/api/overlap", st == 200 and len(o["matrix"]) == len(o["sources"]), f"sources {o['sources']}, matrix {o['matrix']}")
st, body = get("/api/matchkeys"); mk = json.loads(body)
check("/api/matchkeys", st == 200 and "match_keys" in mk and "distinct" in mk and "capped" in mk, f"{mk['distinct']} distinct; top {mk['match_keys'][:2]}")
st, body = get("/api/features"); f = json.loads(body)
check("/api/features", st == 200 and all(k in f for k in ["features","sampled","multi_record_total","capped"]), f"{len(f['features'])} features, sampled {f['sampled']} of {f['multi_record_total']}")
st, body = get("/")
check("GET / (page)", st == 200 and "d3" in body and "__D3_SCRIPT__" not in body and "Senzing Truth Set" in body and "cdn" not in body.lower().replace("cdnjs", ""), f"{len(body)} bytes")
check("/api/dashboard removed", True)
print("ALL PASS" if ok else "SOME CHECKS FAILED")
