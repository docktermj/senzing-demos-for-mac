#! /usr/bin/env python3
"""Data Quality, Mapping, and Transformation, Phase 1 Step 6: score each source.

quality_score = 0.70 x completeness + 0.25 x format_consistency + 0.05 x (100 - duplicate_rate)

* completeness: mean per record of the share of APPLICABLE fields present. Applicable = the record
  key plus every field Step 4 matched to an Entity Specification attribute. Fields with no
  counterpart (dates, campaign, plan, subject) are not counted. None of the sources carries a
  RECORD_TYPE field, so every record is scored against the features that apply to any type.
* present: not missing, not None, not empty or whitespace-only (Step 6's presence test).
* format_consistency: share of populated values matching their field's dominant observed pattern,
  over the fields where a pattern is meaningful (phones, dates, postal codes, state codes, casing of
  names and emails).
* duplicate_rate: share of records whose (DATA_SOURCE, RECORD_ID) pair is not unique.
Writes data/temp/quality_assessment.json.
"""

import csv
import json
import re
from collections import Counter
from pathlib import Path

ORG_SUFFIXES = {"LLP", "LP", "PLC", "LIMITED", "LTD", "LLC", "INC", "CORP", "CORPORATION", "GMBH"}

SOURCES = {
    "CRM_SYSTEM": {
        "file": "data/raw/crm_system.csv", "key": "crm_id",
        "applicable": {"full_name": "NAME_FULL", "address": "ADDR_FULL", "phone": "PHONE_NUMBER",
                       "email": "EMAIL_ADDRESS"},
        "patterns": {"full_name": "casing", "phone": "shape", "email": "casing", "created_date": "shape"},
        "unmatched": ["created_date", "campaign"], "name_fields": ["full_name"], "type_field": None,
    },
    "BILLING_SYSTEM": {
        "file": "data/raw/billing_system.csv", "key": "billing_id",
        "applicable": {"first_name": "NAME_FIRST", "last_name": "NAME_LAST", "street": "ADDR_LINE1",
                       "city": "ADDR_CITY", "state": "ADDR_STATE", "zip": "ADDR_POSTAL_CODE",
                       "phone": "PHONE_NUMBER"},
        "patterns": {"first_name": "casing", "last_name": "casing", "state": "shape", "zip": "shape",
                     "phone": "shape", "last_invoice_date": "shape"},
        "unmatched": ["plan", "last_invoice_date"], "name_fields": ["first_name", "last_name"], "type_field": None,
    },
    "SUPPORT_TICKETS": {
        "file": "data/raw/support_tickets.csv", "key": "ticket_id",
        "applicable": {"name": "NAME_FULL", "email": "EMAIL_ADDRESS"},
        "patterns": {"name": "casing", "email": "casing", "opened_date": "shape"},
        "unmatched": ["subject", "opened_date"], "name_fields": ["name"], "type_field": None,
    },
}


def present(v):
    if v is None:
        return False
    if isinstance(v, str):
        return v.strip() != ""
    if isinstance(v, (list, dict, set, tuple)):
        return len(v) > 0 and any(present(x) for x in (v.values() if isinstance(v, dict) else v))
    return True


def shape(v):
    return re.sub(r"[a-z]", "a", re.sub(r"[A-Z]", "A", re.sub(r"\d", "9", v)))


def casing(v):
    letters = [c for c in v if c.isalpha()]
    if letters and all(c.isupper() for c in letters):
        return "UPPER"
    if letters and all(c.islower() for c in letters):
        return "lower"
    return "Mixed"


def band(s):
    return ">=80" if s >= 80 else "70-79" if s >= 70 else "<70"


# Score the file each source's registry entry points at (an improved file replaces the original there).
try:
    import yaml
    _registry = yaml.safe_load(open("config/data_sources.yaml", encoding="utf-8"))["sources"]
    for _name, _cfg in SOURCES.items():
        _cfg["file"] = _registry.get(_name, {}).get("file_path") or _cfg["file"]
except Exception:
    pass

results = {}
for name, cfg in SOURCES.items():
    rows = list(csv.DictReader(open(cfg["file"], encoding="utf-8")))
    n = len(rows)
    fields = [cfg["key"]] + list(cfg["applicable"])
    per_field = {f: round(100 * sum(present(r[f]) for r in rows) / n, 1) for f in fields}
    completeness = 100 * sum(sum(present(r[f]) for f in fields) / len(fields) for r in rows) / n

    match = pop = 0
    fmt = {}
    for f, kind in cfg["patterns"].items():
        vals = [(shape if kind == "shape" else casing)(r[f].strip()) for r in rows if present(r[f])]
        if not vals:
            continue
        top, cnt = Counter(vals).most_common(1)[0]
        fmt[f] = {"dominant": top, "matching_pct": round(100 * cnt / len(vals), 1), "off_pattern": len(vals) - cnt}
        match += cnt
        pop += len(vals)
    fc = 100 * match / pop if pop else 100.0

    keys = Counter((name, r[cfg["key"]].strip()) for r in rows)
    dup = 100 * sum(c for c in keys.values() if c > 1) / n
    score = round(0.70 * completeness + 0.25 * fc + 0.05 * (100 - dup), 1)

    # type/name check (Phase 1 Step 6): only records typed PERSON are tested; no source has a type field
    person_typed = 0 if cfg["type_field"] is None else sum(r[cfg["type_field"]] == "PERSON" for r in rows)
    candidates = []
    if cfg["type_field"]:
        for r in rows:
            if r[cfg["type_field"]] == "PERSON":
                full = " ".join(r[f] for f in cfg["name_fields"] if present(r[f])).split()
                if full and full[-1].rstrip(".,;:").upper() in ORG_SUFFIXES:
                    candidates.append(full)

    worst = sorted(((v, f) for f, v in per_field.items() if f != cfg["key"]))[:3]
    results[name] = {
        "file": cfg["file"], "records": n, "applicable_fields": fields, "per_field_presence_pct": per_field,
        "records_without_record_type": n, "completeness": round(completeness, 1),
        "format_consistency": round(fc, 1), "format_by_field": fmt, "duplicate_rate": round(dup, 1),
        "quality_score": score, "band": band(score),
        "weakest_fields": [{"field": f, "presence_pct": v} for v, f in worst],
        "type_name_check": {"person_typed": person_typed, "candidates": len(candidates)},
        "sample_values": {f: next((r[f] for r in rows if present(r[f])), None) for f in fields},
    }

Path("data/temp").mkdir(parents=True, exist_ok=True)
Path("data/temp/quality_assessment.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
print(json.dumps({k: {x: v[x] for x in ("records", "per_field_presence_pct", "completeness", "format_consistency",
                                        "duplicate_rate", "quality_score", "band", "type_name_check")}
                  for k, v in results.items()}, indent=1))
print(json.dumps({k: v["format_by_field"] for k, v in results.items()}, indent=1))
