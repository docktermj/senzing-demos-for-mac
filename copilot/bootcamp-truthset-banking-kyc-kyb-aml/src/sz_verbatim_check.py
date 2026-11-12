#!/usr/bin/env python3
"""Verbatim fidelity check — a SEPARATE validation tool from the Senzing analyzer.

The analyzer (sz_json_analyzer.py) sees ONLY the output and answers "is this valid Senzing
JSON?". This tool takes BOTH the source records and the mapper's output and answers a different
question: "are the output values FAITHFUL to the source?" A mapper ROUTES values; it must not
rewrite them. Splitting a compound field is fine (each emitted piece is still a literal substring
of the source); reformatting a date ("02 Jul 1988"->"1988-07-02"), recoding ("Male"->"M"), or
changing case is NOT.

Usage:
  sz_verbatim_check.py <source.jsonl> <output.jsonl>
Exit 0 if every emitted value is a verbatim source piece; exit 1 otherwise, with the offenders
grouped by attribute, fewest offenders first (so one real transform is never buried under a bulk
one). output[i] aligns positionally with source[i] (the mapper applies to source rows in order); if
the record counts differ it falls back to a global value set and says so.

Exempt (mapper-assigned, never copied): DATA_SOURCE, RECORD_ID, any *_TYPE label, the Entity Spec
codes ACCOUNT_DOMAIN / REL_ANCHOR_DOMAIN / REL_POINTER_DOMAIN / REL_POINTER_ROLE (by exact name),
and a REL_ANCHOR_KEY / REL_POINTER_KEY equal to a RECORD_ID this output emits. Everything else,
including values a mapper infers (e.g. ADDR_COUNTRY "US"), is checked.

Pure stdlib, runs client-side over the client's own files (no data leaves the host).
"""

import json
import re
import sys
from pathlib import Path

# DATA_SOURCE/RECORD_ID are mapper-assigned; any *_TYPE attribute is a Senzing usage/classification
# LABEL (RECORD_TYPE, NAME_TYPE, OTHER_ID_TYPE, ...) the mapper assigns rather than copies. Grounded
# empirically: the verbatim-correct opus baseline emits non-source values ONLY in *_TYPE attributes.
EXEMPT_KEYS = {"DATA_SOURCE", "RECORD_ID"}
# Namespace/role CODES the Entity Spec tells the mapper to ASSIGN, not copy: a *_DOMAIN is "a code
# for the data source or source field contributing" the key/account, and REL_POINTER_ROLE is a
# role code to "standardize for display and filtering". Named explicitly (not by suffix) so a
# custom attribute that merely ends in _DOMAIN/_ROLE is still checked.
LABEL_KEYS = {"ACCOUNT_DOMAIN", "REL_ANCHOR_DOMAIN", "REL_POINTER_DOMAIN", "REL_POINTER_ROLE"}
# A relationship key is the RECORD_ID of the anchored record. RECORD_ID is exempt (it may be a
# deterministic hash), so a key is exempt exactly when it equals a RECORD_ID this output emits.
REL_KEY_ATTRS = {"REL_ANCHOR_KEY", "REL_POINTER_KEY"}
EXAMPLES_PER_ATTR = 3


def is_exempt(attr):
    return attr in EXEMPT_KEYS or attr in LABEL_KEYS or attr.endswith("_TYPE")


def collect_strings(obj, out):
    """Flatten every string and int/float scalar (stringified) in a nested source record
    into `out`. Booleans are excluded even though isinstance(True, int) is True in Python —
    there is no unambiguous verbatim string form for a JSON boolean (Python str(True) is
    "True", not JSON's "true"), so extending the allowed set to booleans would let a case
    transform slip through rather than catch one."""
    if isinstance(obj, bool):
        pass
    elif isinstance(obj, str):
        out.append(obj)
    elif isinstance(obj, (int, float)):
        out.append(str(obj))
    elif isinstance(obj, list):
        for x in obj:
            collect_strings(x, out)
    elif isinstance(obj, dict):
        for v in obj.values():
            collect_strings(v, out)
    return out


def allowed_values(strings, delims="|;"):
    """Legitimate emitted values from a set of source strings: each whole string, each
    pipe/semicolon segment, and each whitespace token — all stripped. Equality against this set
    (not substring) allows structural splits while rejecting transforms and avoiding the "M" in
    "Male" trap."""
    allowed = set()
    for s in strings:
        s = s.strip()
        if not s:
            continue
        allowed.add(s)
        for seg in re.split(f"[{re.escape(delims)}]", s):
            seg = seg.strip()
            if seg:
                allowed.add(seg)
        for tok in s.split():
            allowed.add(tok)
    return allowed


def check_verbatim(source_records, output_records):
    """Return a list of violation strings: emitted feature values that are not verbatim source
    pieces (exempting DATA_SOURCE/RECORD_ID/*_TYPE, the LABEL_KEYS codes, and relationship keys
    that equal an emitted RECORD_ID)."""
    per_record = len(source_records) == len(output_records)
    global_allowed = None if per_record else allowed_values(collect_strings(source_records, []))
    record_ids = {str(r["RECORD_ID"]).strip() for r in output_records if r.get("RECORD_ID")}
    violations = []
    for i, rec in enumerate(output_records):
        allowed = (
            allowed_values(collect_strings(source_records[i], [])) if per_record else global_allowed
        )
        for feat in rec.get("FEATURES", []):
            if not isinstance(feat, dict):
                continue
            for k, v in feat.items():
                if is_exempt(k) or not isinstance(v, str) or not v:
                    continue
                if k in REL_KEY_ATTRS and v.strip() in record_ids:
                    continue
                if v.strip() not in allowed:
                    violations.append(f"rec{i} {k}={v!r}")
    return violations


class NotJsonlError(ValueError):
    """The file is not JSONL (one JSON object per line). Raised instead of a raw
    JSONDecodeError traceback, which is the hardest error for a model to recover from."""


def load_jsonl(path):
    """Records of a JSONL file. JSONL ONLY — CSV/TSV/other sources are deliberately not
    read: the mapping workflow converts them ONCE to <name>_source.jsonl (step 4, 0)."""
    records = []
    for n, ln in enumerate(Path(path).read_text().splitlines(), 1):
        if not ln.strip():
            continue
        try:
            rec = json.loads(ln)
        except json.JSONDecodeError as e:
            raise NotJsonlError(_not_jsonl(path, n, e.msg)) from None
        if not isinstance(rec, dict):
            raise NotJsonlError(_not_jsonl(path, n, f"a JSON {type(rec).__name__}, not an object"))
        records.append(rec)
    return records


def _not_jsonl(path, line, why):
    return (
        f"ERROR: {path} is not JSONL (line {line}: {why}). This script reads JSONL only: one "
        "JSON object per line. If the source is CSV, TSV or another format, convert it ONCE "
        "to <name>_source.jsonl (mapping_workflow step 4, point 0: one object per source "
        "row, in source order, every value kept as the exact source string) and pass that "
        "file instead."
    )


def verify(source_path, output_path):
    """(ok, detail) over two files. ok=True iff every emitted value is verbatim from source."""
    src = load_jsonl(source_path)
    out = load_jsonl(output_path)
    violations = check_verbatim(src, out)
    if not violations:
        return True, "all emitted values are verbatim from source"
    scope = "" if len(src) == len(out) else " (global fallback: record counts differ)"
    return False, (
        f"{len(violations)} emitted value(s) are NOT verbatim from the source{scope} — they were "
        f"transformed/normalized, which is forbidden. A mapper must emit each value EXACTLY as it "
        f"appears in the source (splitting a compound field is fine; reformatting dates, recoding "
        f"e.g. Male->M, or changing case is NOT). Fix EVERY attribute listed below — fewest "
        f"offenders first, so a single bad record is never buried under a bulk one:\n"
        + group_report(violations)
    )


def group_report(violations):
    """One line per attribute, ordered by offender count ASCENDING (ties by name): count,
    distinct values, and the first EXAMPLES_PER_ATTR offenders. Bounded by the number of
    attributes, not records, so every offending attribute is always visible."""
    groups = {}
    for v in violations:
        rec, kv = v.split(" ", 1)
        attr, val = kv.split("=", 1)
        groups.setdefault(attr, []).append((rec, val))
    lines = []
    for attr, hits in sorted(groups.items(), key=lambda kv: (len(kv[1]), kv[0])):
        distinct = len({val for _, val in hits})
        ex = "; ".join(f"{rec} {val}" for rec, val in hits[:EXAMPLES_PER_ATTR])
        lines.append(f"  {attr}: {len(hits)} offender(s), {distinct} distinct value(s) — e.g. {ex}")
    return "\n".join(lines)


def main(argv):
    if len(argv) < 3:
        print("usage: sz_verbatim_check.py <source.jsonl> <output.jsonl>", file=sys.stderr)
        return 2
    try:
        ok, detail = verify(argv[1], argv[2])
    except NotJsonlError as e:
        print(e, file=sys.stderr)
        return 2
    print(detail)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
