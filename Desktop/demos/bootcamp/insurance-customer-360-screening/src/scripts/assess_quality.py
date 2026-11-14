#!/usr/bin/env python3
"""Data Quality, Mapping, and Transformation - Phase 1 (steps 4, 6): compare each collected
source with the Senzing Entity Specification and score its quality.

Usage (from the project root):
    python3 src/scripts/assess_quality.py > data/temp/quality_assessment.json

Method (Module 5 Phase 1 step 6):
  quality = 0.70 * completeness + 0.25 * format_consistency + 0.05 * (100 - duplicate_rate)
  - completeness (identifiers counted as one group, as the specification groups them): mean over records of the share of APPLICABLE features present in that record.
    Features are the Entity Specification features the source carries (step 4's counterparts).
    Applicability is per RECORD_TYPE: DOB and GENDER apply to PERSON only (the specification
    describes DOB as "Person date of birth"); every other feature applies to the record types on
    which the source actually carries it, so a feature that structurally cannot exist on a type
    (a 100%/0% split) is not counted against that type. Records with no RECORD_TYPE are scored
    against the features that apply to both types, and counted.
  - presence: a value is present unless missing, null, an empty/whitespace string, or an empty
    container (or a container of only empty values). false / 0 count as present.
  - format_consistency: share of populated values matching their field's dominant shape
    (digits -> 9, letters -> A/a, punctuation kept), over coded/patterned fields only.
  - duplicate_rate: share of records whose (DATA_SOURCE, RECORD_ID) pair is not unique.
  - type/name check: PERSON-typed records whose name's final whole token is an organization
    suffix (LLP LP PLC LIMITED LTD LLC INC CORP CORPORATION GMBH) - the plugin's own heuristic.
"""
import collections
import json
import re
import sys

SPEC = "docs/reference/senzing_entity_specification.md"
SOURCES = [
    ("PC_POLICY_ADMIN", "data/raw/pc_policy_admin_extract.jsonl"),
    ("LIFE_ANNUITY_ADMIN", "data/raw/life_annuity_admin_extract.jsonl"),
    ("CREDIT_BUREAU", "data/raw/credit_header.jsonl"),
    ("BUSINESS_REGISTRY", "data/raw/business_registry.jsonl"),
    ("SIU_CASES", "data/raw/siu_cases.jsonl"),
    ("SANCTIONS_PEP", "data/raw/sanctions_pep.jsonl"),
]
STRUCTURAL = {"DATA_SOURCE", "RECORD_ID", "FEATURES"}
ORG_SUFFIX = {"LLP", "LP", "PLC", "LIMITED", "LTD", "LLC", "INC", "CORP", "CORPORATION", "GMBH"}

# attribute -> feature family, for completeness and applicability
FAMILY_RULES = [
    (r"^NAME_", "NAME"), (r"^ADDR_", "ADDRESS"), (r"^PHONE_", "PHONE"),
    (r"^DATE_OF_BIRTH$", "DOB"), (r"^GENDER$", "GENDER"), (r"^EMAIL_ADDRESS$", "EMAIL"),
    # Identifiers are ONE group (the specification's "Identifiers" section): present when the
    # record carries ANY identifier. Per-member presence is still reported separately below.
    (r"^PASSPORT_", "IDENTIFIER"), (r"^DRIVERS_LICENSE_", "IDENTIFIER"), (r"^SSN_", "IDENTIFIER"),
    (r"^NATIONAL_ID_", "IDENTIFIER"), (r"^TAX_ID_", "IDENTIFIER"), (r"^NPI_NUMBER$", "IDENTIFIER"),
    (r"^LEI_NUMBER$", "IDENTIFIER"), (r"^EMPLOYER$", "EMPLOYER"),
]
NOT_COMPLETENESS = {"RECORD_TYPE", "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY", "REL_POINTER_DOMAIN",
                    "REL_POINTER_KEY", "REL_POINTER_ROLE"}
PERSON_ONLY = {"DOB", "GENDER"}
PATTERNED = ["DATE_OF_BIRTH", "PHONE_NUMBER", "ADDR_POSTAL_CODE", "ADDR_STATE", "SSN_NUMBER",
             "GENDER", "ADDR_COUNTRY", "PASSPORT_COUNTRY", "DRIVERS_LICENSE_STATE",
             "NATIONAL_ID_COUNTRY", "TAX_ID_COUNTRY", "TAX_ID_TYPE", "NATIONAL_ID_TYPE",
             "PHONE_TYPE", "ADDR_TYPE"]


def catalog():
    """Attribute names from the FIRST column of the specification's tables (plain text)."""
    names = set()
    for line in open(SPEC, encoding="utf-8"):
        m = re.match(r"^\|\s*`?([A-Z][A-Z0-9_]+)`?\s*\|", line)
        if m:
            names.add(m.group(1))
    return names


def present(v):
    if v is None:
        return False
    if isinstance(v, str):
        return v.strip() != ""
    if isinstance(v, (list, tuple, set, dict)):
        vals = v.values() if isinstance(v, dict) else v
        return len(v) > 0 and any(present(x) for x in vals)
    return True


def family(attr):
    for pat, fam in FAMILY_RULES:
        if re.match(pat, attr):
            return fam
    return None


FIXED_SHAPE = {"DATE_OF_BIRTH", "PHONE_NUMBER", "ADDR_POSTAL_CODE", "SSN_NUMBER"}
CODE_OR_NAME = {"ADDR_COUNTRY", "PASSPORT_COUNTRY", "NATIONAL_ID_COUNTRY", "TAX_ID_COUNTRY",
                "DRIVERS_LICENSE_STATE", "ADDR_STATE"}
LABELS = {"PHONE_TYPE", "ADDR_TYPE", "TAX_ID_TYPE", "NATIONAL_ID_TYPE"}


def shape(v, fld=None):
    """The format class of a value. Only genuine format differences count: a date or phone
    written two ways, a code beside a spelled-out name, a label in two casings. Different
    VALUES of a label (HOME vs MAILING) or different country names are not format issues."""
    s = str(v)
    if fld in CODE_OR_NAME:
        if re.fullmatch(r"[A-Z]{2,3}", s):
            return "CODE"
        if s != s.strip():
            return "WHITESPACE"
        return "NAME"
    if fld == "DATE_OF_BIRTH":
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", s):
            return "YYYY-MM-DD"
        if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{2}", s):
            return "M/D/YY"
        if re.fullmatch(r"\d{1,2}/\d{1,2}/\d{4}", s):
            return "M/D/YYYY"
        return "OTHER"
    if fld == "GENDER":
        return "CODE" if re.fullmatch(r"[A-Z]", s) else "WORD"
    if fld in LABELS:
        if s != s.strip():
            return "WHITESPACE"
        return "UPPER" if s.isupper() else ("lower" if s.islower() else "Mixed")
    s = re.sub(r"[0-9]", "9", s)
    s = re.sub(r"[A-Z]", "A", s)
    return re.sub(r"[a-z]", "a", s)


def assess(code, path, cat):
    recs = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    top_unrec = collections.Counter()
    feat_attrs = collections.Counter()
    feat_unrec = collections.Counter()
    mixing = collections.Counter()
    per_rec = []
    for r in recs:
        for k in r:
            if k not in STRUCTURAL and k not in cat:
                top_unrec[k] += 1
        rtype, fams, values = None, set(), collections.defaultdict(list)
        for obj in r.get("FEATURES", []):
            keys = set(obj)
            if "ADDR_FULL" in keys and keys & {"ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE"}:
                mixing["ADDR_FULL with parsed address in one object"] += 1
            if "NAME_FULL" in keys and keys & {"NAME_FIRST", "NAME_LAST", "NAME_MIDDLE"}:
                mixing["NAME_FULL with parsed name in one object"] += 1
            if "NAME_ORG" in keys and keys & {"NAME_FIRST", "NAME_LAST", "NAME_MIDDLE"}:
                mixing["NAME_ORG with parsed name in one object"] += 1
            for k, v in obj.items():
                feat_attrs[k] += 1
                if k not in cat:
                    feat_unrec[k] += 1
                if k == "RECORD_TYPE":
                    rtype = v
                values[k].append(v)
                f = family(k)
                if f and present(v):
                    fams.add(f)
        per_rec.append((rtype, fams, values, r))

    # applicability: which record types carry each family at all
    carried = collections.defaultdict(set)
    for rtype, fams, _, _ in per_rec:
        for f in fams:
            carried[f].add(rtype or "-")
    fams_all = sorted(carried)

    def applicable(f, rtype):
        if f in PERSON_ONLY:
            return rtype == "PERSON"
        if rtype is None:
            types = carried[f] - {"-"}
            return types >= {"PERSON", "ORGANIZATION"} or not types
        return rtype in carried[f]

    comp_vals, breakdown, no_type = [], {}, 0
    type_counts = collections.Counter((t or "-") for t, _, _, _ in per_rec)
    for rtype, fams, _, _ in per_rec:
        if rtype is None:
            no_type += 1
        app = [f for f in fams_all if applicable(f, rtype)]
        if app:
            comp_vals.append(100.0 * sum(1 for f in app if f in fams) / len(app))
    for f in fams_all:
        breakdown[f] = {t: round(100.0 * sum(1 for rt, fs, _, _ in per_rec if (rt or "-") == t and f in fs)
                                 / type_counts[t], 1) for t in type_counts}
    completeness = round(sum(comp_vals) / len(comp_vals), 1) if comp_vals else None

    # format consistency
    fmt_total = fmt_ok = 0
    fmt_fields = {}
    for fld in PATTERNED:
        vals = [v for _, _, values, _ in per_rec for v in values.get(fld, []) if present(v)]
        if len(vals) < 2:
            continue
        shapes = collections.Counter(shape(v, fld) for v in vals)
        dom, n = shapes.most_common(1)[0]
        fmt_total += len(vals)
        fmt_ok += n
        if n < len(vals):
            fmt_fields[fld] = {"dominant": dom, "on_pattern": n, "populated": len(vals),
                               "pct": round(100.0 * n / len(vals), 1),
                               "other_shapes": dict(shapes.most_common()[1:4])}
    fmt = round(100.0 * fmt_ok / fmt_total, 1) if fmt_total else 100.0

    keys = collections.Counter((r.get("DATA_SOURCE"), r.get("RECORD_ID")) for r in recs)
    dup_rate = round(100.0 * sum(c for c in keys.values() if c > 1) / len(recs), 1)

    # type/name check
    persons, cands = 0, []
    for rtype, _, values, r in per_rec:
        if rtype != "PERSON":
            continue
        persons += 1
        name = None
        if values.get("NAME_FULL"):
            name = values["NAME_FULL"][0]
        elif values.get("NAME_ORG"):
            name = values["NAME_ORG"][0]
        elif values.get("NAME_LAST") or values.get("NAME_FIRST"):
            name = " ".join(x for x in [(values.get("NAME_FIRST") or [""])[0],
                                        (values.get("NAME_MIDDLE") or [""])[0],
                                        (values.get("NAME_LAST") or [""])[0]] if x)
        if name:
            tok = name.split()[-1].rstrip(".,;:").upper() if name.split() else ""
            if tok in ORG_SUFFIX:
                cands.append({"record_id": r["RECORD_ID"], "name": name})

    score = round(0.70 * completeness + 0.25 * fmt + 0.05 * (100 - dup_rate), 1) if completeness is not None else None
    return {
        "source": code, "records": len(recs), "record_types": dict(type_counts),
        "records_without_record_type": no_type,
        "features_unrecognized_by_spec": dict(feat_unrec),
        "top_level_non_spec_keys": dict(top_unrec),
        "object_mixing_issues": dict(mixing),
        "families": fams_all, "family_presence_by_type": breakdown,
        "completeness": completeness, "format_consistency": fmt, "format_fields": fmt_fields,
        "duplicate_rate": dup_rate, "quality_score": score,
        "type_name_check": {"person_records": persons, "candidates": cands},
    }


def main():
    cat = catalog()
    out = {"catalog_size": len(cat),
           "catalog_has_NAME_ORG": "NAME_ORG" in cat, "catalog_has_ADDR_LINE1": "ADDR_LINE1" in cat,
           "catalog_has_PHONE_NUMBER": "PHONE_NUMBER" in cat,
           "sources": [assess(c, p, cat) for c, p in SOURCES]}
    json.dump(out, sys.stdout, indent=1)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
