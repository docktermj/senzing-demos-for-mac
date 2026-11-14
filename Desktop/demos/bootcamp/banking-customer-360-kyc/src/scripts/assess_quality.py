#!/usr/bin/env python3
"""Data quality assessment for the five banking sources (Module 5, Phase 1, steps 4-6).

Analysis helper only: it reads data/raw/*.jsonl and the saved Entity Specification
(docs/reference/senzing_entity_specification.md) and never touches Senzing or its database.

Method (Module 5 Phase 1 step 6, stated there):
  quality = 0.70 * completeness + 0.25 * format_consistency + 0.05 * (100 - duplicate_rate)
  completeness  per record, over the features APPLICABLE to that record's RECORD_TYPE, using the
                value-based presence test (false/0 count as present; empty containers do not).
                Counted fields: the structural keys DATA_SOURCE and RECORD_ID plus every Senzing
                feature that appears in the source. Root business columns (balances, account
                numbers, ...) have no Entity Specification counterpart and are not yet
                dispositioned, so they are excluded until mapping decides them.
  format        share of populated coded/structured values matching their field's dominant shape.
  duplicates    share of records whose (DATA_SOURCE, RECORD_ID) pair is not unique.
Writes data/temp/quality_assessment.json and prints a summary.
"""
import collections
import json
import re
import sys

SPEC = "docs/reference/senzing_entity_specification.md"
def _registry_paths():
    """(DATA_SOURCE, file_path) from config/data_sources.yaml, so an improved file is scored."""
    reg = open("config/data_sources.yaml", encoding="utf-8").read()
    return re.findall(r"^  (\w+):\n    name: \w+\n    file_path: (\S+)", reg, re.M)


SOURCES = _registry_paths()
STRUCTURAL = {"DATA_SOURCE", "RECORD_ID", "FEATURES"}
ORG_SUFFIXES = {"LLP", "LP", "PLC", "LIMITED", "LTD", "LLC", "INC", "CORP", "CORPORATION", "GMBH"}

# Feature a FEATURES object represents, from the attribute names it carries (Entity Specification
# feature tables). Order matters: the first prefix that matches wins.
FEATURE_OF = [
    ("RECORD_TYPE", "RECORD_TYPE"), ("NAME_", "NAME"), ("DATE_OF_BIRTH", "DOB"),
    ("ADDR_", "ADDRESS"), ("PHONE_", "PHONE"), ("EMAIL_", "EMAIL"), ("SSN_", "SSN"),
    ("DRIVERS_LICENSE_", "DRLIC"), ("PASSPORT_", "PASSPORT"), ("NATIONAL_ID_", "NATIONAL_ID"),
    ("TAX_ID_", "TAX_ID"), ("REL_ANCHOR_", "REL_ANCHOR"), ("REL_POINTER_", "REL_POINTER"),
    ("GENDER", "GENDER"), ("NATIONALITY", "NATIONALITY"), ("CITIZENSHIP", "CITIZENSHIP"),
    ("REGISTRATION_", "REGISTRATION"), ("LEI_", "LEI"), ("WEBSITE", "WEBSITE"),
    ("EMPLOYER", "EMPLOYER"), ("ACCOUNT_", "ACCOUNT"), ("OTHER_ID_", "OTHER_ID"),
]
# Applicability per the specification's "What features to map" wording.
PERSON_ONLY = {"DOB", "GENDER", "NATIONALITY", "CITIZENSHIP"}
ORG_ONLY = {"REGISTRATION"}
NOT_SCORED = {"RECORD_TYPE"}  # the type itself drives applicability; it is not a completeness slot
# GROUPED mode (the primary score): identifiers count as ONE slot -- "does this record carry an
# identifier at all" -- because a record carries one or two identifier types, never all of them;
# scoring each type separately marks a person "missing" a passport, licence, national ID and tax ID
# for data that would never exist together. Disclosed-relationship features (REL_ANCHOR /
# REL_POINTER) are linkage, present only on records that participate in a relationship, so they
# are reported separately and not scored as completeness.
IDENTIFIERS = {"SSN", "DRLIC", "PASSPORT", "NATIONAL_ID", "TAX_ID", "LEI", "OTHER_ID", "ACCOUNT"}
RELATIONSHIP = {"REL_ANCHOR", "REL_POINTER"}
GROUPED = "--strict" not in sys.argv
# Coded/structured attributes whose shape is checked for format consistency.
FORMAT_FIELDS = {"DATE_OF_BIRTH", "ADDR_POSTAL_CODE", "ADDR_STATE", "PHONE_NUMBER", "SSN_NUMBER",
                 "ADDR_TYPE", "PHONE_TYPE", "NAME_TYPE", "DRIVERS_LICENSE_STATE",
                 "NATIONAL_ID_TYPE", "NATIONAL_ID_COUNTRY", "PASSPORT_COUNTRY", "ADDR_COUNTRY",
                 "TAX_ID_TYPE", "TAX_ID_COUNTRY", "RECORD_TYPE"}


def present(v):
    """Value-based presence: absent/None, blank strings, and empty containers are absent."""
    if v is None:
        return False
    if isinstance(v, str):
        return v.strip() != ""
    if isinstance(v, (list, tuple, set, dict)):
        items = v.values() if isinstance(v, dict) else v
        return len(v) > 0 and any(present(x) for x in items)
    return True  # numbers and booleans, including 0 and False


def feature_of(obj):
    for key in obj:
        for prefix, feat in FEATURE_OF:
            if key.startswith(prefix):
                return feat
    return None


def shape(field, value):
    s = str(value).strip()
    if field == "DATE_OF_BIRTH":
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", s): return "YYYY-MM-DD"
        if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{4}", s): return "M/D/YYYY"
        if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{2}", s): return "M/D/YY"
        if re.fullmatch(r"\d{4}", s): return "YYYY"
        return "other"
    if field in ("ADDR_TYPE", "PHONE_TYPE", "NAME_TYPE", "NATIONAL_ID_TYPE", "TAX_ID_TYPE",
                 "RECORD_TYPE", "ADDR_STATE", "DRIVERS_LICENSE_STATE"):
        return "UPPER" if s == s.upper() else "mixed/lower"
    # digits -> 9, letters -> A, keep punctuation (exact shape, e.g. 999-999-9999)
    return re.sub(r"[A-Za-z]", "A", re.sub(r"\d", "9", s))


def assess(ds, path, catalog):
    recs = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    n = len(recs)
    root_unrecognized = collections.Counter()
    feat_unrecognized = collections.Counter()
    per_rec_feats, rtypes = [], []
    values = collections.defaultdict(list)
    names = []
    for r in recs:
        for k in r:
            if k not in STRUCTURAL and k not in catalog:
                root_unrecognized[k] += 1
        feats, rtype, name = {}, None, None
        for obj in r.get("FEATURES") or []:
            for k, v in obj.items():
                if k not in catalog:
                    feat_unrecognized[k] += 1
                if k in FORMAT_FIELDS and present(v):
                    values[k].append(v)
            f = feature_of(obj)
            if f == "RECORD_TYPE":
                rtype = obj.get("RECORD_TYPE")
            if f:
                feats[f] = feats.get(f, False) or any(present(v) for v in obj.values())
            if f == "NAME" and name is None:
                if present(obj.get("NAME_FULL")): name = obj["NAME_FULL"]
                elif present(obj.get("NAME_ORG")): name = obj["NAME_ORG"]
                else:
                    name = " ".join(str(obj[k]) for k in ("NAME_PREFIX", "NAME_FIRST", "NAME_MIDDLE",
                                    "NAME_LAST", "NAME_SUFFIX") if present(obj.get(k)))
        if GROUPED:
            ids = [k for k in feats if k in IDENTIFIERS]
            if ids:
                vals = [feats.pop(k) for k in ids]   # pop every member first (no short-circuit)
                feats["IDENTIFIER"] = any(vals)
            for k in RELATIONSHIP:
                feats.pop(k, None)
        per_rec_feats.append(feats)
        rtypes.append(rtype)
        names.append((r.get("RECORD_ID"), rtype, name))

    source_feats = sorted({f for fs in per_rec_feats for f in fs} - NOT_SCORED)
    type_count = collections.Counter(t or "(none)" for t in rtypes)

    def applies(feat, t):
        if t == "PERSON": return feat not in ORG_ONLY
        if t == "ORGANIZATION": return feat not in PERSON_ONLY
        return feat not in PERSON_ONLY and feat not in ORG_ONLY   # untyped: features for any type

    # Per-type presence for every feature, to catch an applicability error (a 0% on one type).
    breakdown, adjusted = {}, []
    for f in source_feats:
        row = {}
        for t in ("PERSON", "ORGANIZATION"):
            idx = [i for i, rt in enumerate(rtypes) if rt == t]
            if idx:
                row[t] = round(100 * sum(1 for i in idx if per_rec_feats[i].get(f)) / len(idx), 1)
        breakdown[f] = row
        both = f not in PERSON_ONLY and f not in ORG_ONLY
        if both and len(row) == 2:
            for t, other in (("PERSON", "ORGANIZATION"), ("ORGANIZATION", "PERSON")):
                if row[t] == 0 and row[other] > 0:
                    adjusted.append((f, t))
    excluded = set(adjusted)

    scores = []
    for fs, t in zip(per_rec_feats, rtypes):
        app = [f for f in source_feats if applies(f, t) and (f, t) not in excluded]
        slots = 2 + len(app)                       # + DATA_SOURCE, RECORD_ID (always present)
        have = 2 + sum(1 for f in app if fs.get(f))
        scores.append(100.0 * have / slots)
    completeness = sum(scores) / n

    fmt_total = fmt_ok = 0
    fmt_detail = {}
    for field, vals in values.items():
        shapes = collections.Counter(shape(field, v) for v in vals)
        dom, dom_n = shapes.most_common(1)[0]
        fmt_total += len(vals)
        fmt_ok += dom_n
        if dom_n < len(vals):
            fmt_detail[field] = {"dominant": dom, "off_pattern": len(vals) - dom_n, "of": len(vals),
                                 "shapes": dict(shapes)}
    fmt = 100.0 * fmt_ok / fmt_total if fmt_total else 100.0
    keys = collections.Counter((r.get("DATA_SOURCE"), r.get("RECORD_ID")) for r in recs)
    dup = 100.0 * sum(c for c in keys.values() if c > 1) / n
    quality = 0.70 * completeness + 0.25 * fmt + 0.05 * (100 - dup)

    feature_cov = {f: round(100 * sum(1 for fs, t in zip(per_rec_feats, rtypes)
                                   if applies(f, t) and (f, t) not in excluded and fs.get(f))
                         / max(1, sum(1 for t in rtypes if applies(f, t) and (f, t) not in excluded)), 1)
                   for f in source_feats}
    persons = [(rid, nm) for rid, t, nm in names if t == "PERSON"]
    candidates = [(rid, nm) for rid, nm in persons if nm and
                  re.sub(r"[^\w]+$", "", nm.split()[-1]).upper() in ORG_SUFFIXES]
    return {
        "records": n, "record_types": dict(type_count),
        "root_business_columns": dict(root_unrecognized),
        "unrecognized_feature_attributes": dict(feat_unrecognized),
        "features": source_feats, "feature_coverage": feature_cov,
        "per_type_presence": breakdown,
        "applicability_adjusted": [f"{f} not applicable to {t} (0% on {t})" for f, t in adjusted],
        "completeness": round(completeness, 1), "format_consistency": round(fmt, 1),
        "format_detail": fmt_detail, "duplicate_rate": round(dup, 1), "quality": round(quality, 1),
        "untyped_records": type_count.get("(none)", 0),
        "type_name_check": {"person_typed": len(persons), "candidates": len(candidates),
                            "examples": [nm for _, nm in candidates[:10]],
                            "candidate_ids": [rid for rid, _ in candidates]},
    }


def main():
    text = open(SPEC, encoding="utf-8").read()
    catalog = set(m.group(1) for m in re.finditer(r"^\|\s*`?([A-Z][A-Z0-9_]+)`?\s*\|", text, re.M))
    for must in ("NAME_ORG", "ADDR_LINE1", "PHONE_NUMBER"):
        if must not in catalog:
            sys.exit(f"catalog parse failed: {must} missing")
    out = {ds: assess(ds, path, catalog) for ds, path in SOURCES}
    json.dump(out, open("data/temp/quality_assessment%s.json" % ("" if GROUPED else "_strict"), "w"), indent=1)
    for ds, a in out.items():
        band = "OK (>=80)" if a["quality"] >= 80 else ("WARN (70-79)" if a["quality"] >= 70 else "FIX (<70)")
        print(f"\n== {ds}: quality {a['quality']} [{band}]  completeness {a['completeness']}  "
              f"format {a['format_consistency']}  duplicates {a['duplicate_rate']}")
        print("   types:", a["record_types"], " features:", a["feature_coverage"])
        print("   per-type presence:", a["per_type_presence"])
        if a["applicability_adjusted"]: print("   applicability:", a["applicability_adjusted"])
        print("   format off-pattern:", {k: (v['off_pattern'], v['of'], v['shapes']) for k, v in a['format_detail'].items()})
        print("   unrecognized feature attrs:", a["unrecognized_feature_attributes"])
        print("   root business columns:", sorted(a["root_business_columns"]))
        print("   type/name:", a["type_name_check"])


if __name__ == "__main__":
    main()
