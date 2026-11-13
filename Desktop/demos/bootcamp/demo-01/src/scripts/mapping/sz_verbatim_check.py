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
Exit 0 if every emitted value is a verbatim source piece; exit 1 (offenders printed) otherwise.
output[i] aligns positionally with source[i] (the mapper applies to source rows in order); if the
record counts differ it falls back to a global value set and says so.

Provenance: promoted from the sz_entity_extract eval repo (branch
verified-mapper-gate, scripts/sz_verbatim_check.py), proven round 9. Pure
stdlib, runs client-side over the client's own files (no data leaves the host).
That repo is upstream; edit there and re-promote rather than diverging here.
"""

import json
import re
import sys
from pathlib import Path

# DATA_SOURCE/RECORD_ID are mapper-assigned; any *_TYPE attribute is a Senzing usage/classification
# LABEL (RECORD_TYPE, NAME_TYPE, OTHER_ID_TYPE, ...) the mapper assigns rather than copies. Grounded
# empirically: the verbatim-correct opus baseline emits non-source values ONLY in *_TYPE attributes.
EXEMPT_KEYS = {"DATA_SOURCE", "RECORD_ID"}


def is_exempt(attr):
    return attr in EXEMPT_KEYS or attr.endswith("_TYPE")


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
    pieces (exempting DATA_SOURCE/RECORD_ID/*_TYPE)."""
    per_record = len(source_records) == len(output_records)
    global_allowed = None if per_record else allowed_values(collect_strings(source_records, []))
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
                if v.strip() not in allowed:
                    violations.append(f"rec{i} {k}={v!r}")
    return violations


def load_jsonl(path):
    return [json.loads(ln) for ln in Path(path).read_text().splitlines() if ln.strip()]


def verify(source_path, output_path):
    """(ok, detail) over two files. ok=True iff every emitted value is verbatim from source."""
    src = load_jsonl(source_path)
    out = load_jsonl(output_path)
    violations = check_verbatim(src, out)
    if not violations:
        return True, "all emitted values are verbatim from source"
    scope = "" if len(src) == len(out) else " (global fallback: record counts differ)"
    shown = "; ".join(violations[:8])
    more = f" (+{len(violations) - 8} more)" if len(violations) > 8 else ""
    return False, (
        f"{len(violations)} emitted value(s) are NOT verbatim from the source{scope} — they were "
        f"transformed/normalized, which is forbidden. A mapper must emit each value EXACTLY as it "
        f"appears in the source (splitting a compound field is fine; reformatting dates, recoding "
        f"e.g. Male->M, or changing case is NOT). Offenders: {shown}{more}"
    )


def main(argv):
    if len(argv) < 3:
        print("usage: sz_verbatim_check.py <source.jsonl> <output.jsonl>", file=sys.stderr)
        return 2
    ok, detail = verify(argv[1], argv[2])
    print(detail)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
