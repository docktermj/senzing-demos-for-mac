#!/usr/bin/env python3
"""Routing report — a MEASUREMENT tool (not a gate) over a mapper's source + output.

The panel conclusion: mapping QUALITY (coverage, specificity) can't be hard-gated without
becoming the mapper or holding an answer key. So instead of gating, we MEASURE and feed the
result back to the model as self-critique — the feedback a silent if/elif mapper never gets.

This reports three things from (source, output):
  1. Feature emission histogram — how many values landed in each Senzing feature.
  2. Catch-all / payload usage — OTHER_ID count (and OTHER_ID_TYPE=UNKNOWN), payload root keys.
  3. DROPPED source value-entries — entries present in source fields that contributed NOTHING to
     the output (an entry is "covered" if any of its pieces appears among the output's values).
     These are UNDER-MAPPING CANDIDATES, not errors: a free-text note legitimately maps to nothing.

Always exits 0 — it is informational. Usage:
  sz_routing_report.py <source.jsonl> <output.jsonl> [--payload-fields a,b,c]
output[i] aligns positionally with source[i]; auto-discovers phase1_manifest.json next to the
output for its payload_fields (to suppress free-text noise) unless --payload-fields is given.

Measurement only — always exits 0. Pure stdlib, runs client-side over the
client's own files.
"""

import json
import re
import sys
from collections import Counter
from pathlib import Path

NEVER_FROM_SOURCE = {"DATA_SOURCE", "RECORD_ID"}


def collect_strings(obj, out):
    if isinstance(obj, str):
        out.append(obj)
    elif isinstance(obj, list):
        for x in obj:
            collect_strings(x, out)
    elif isinstance(obj, dict):
        for v in obj.values():
            collect_strings(v, out)
    return out


def pieces(s, delims="|;"):
    """Whole string + pipe/semicolon segments + whitespace tokens, all stripped."""
    out = {s.strip()}
    for seg in re.split(f"[{re.escape(delims)}]", s):
        out.add(seg.strip())
    out.update(s.split())
    return {x for x in out if x}


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


def emitted_values(record):
    vals = set()
    for feat in record.get("FEATURES", []):
        if isinstance(feat, dict):
            for v in feat.values():
                if isinstance(v, str) and v.strip():
                    vals.add(v.strip())
    return vals


def routing_report(source_records, output_records, payload_fields=()):
    """Return (histogram: Counter, otherid: dict, payload_keys: set, dropped: Counter)."""
    histogram = Counter()
    otherid = Counter()  # {"OTHER_ID_total": n, "OTHER_ID_unknown": n}
    payload_keys = set()
    dropped = Counter()  # "field 'label'" -> count of dropped entries
    exempt_fields = NEVER_FROM_SOURCE | set(payload_fields)
    per_record = len(source_records) == len(output_records)

    for out in output_records:
        for feat in out.get("FEATURES", []):
            if not isinstance(feat, dict):
                continue
            for k in feat:
                histogram[k] += 1
            if "OTHER_ID_TYPE" in feat or "OTHER_ID_NUMBER" in feat:
                otherid["OTHER_ID_total"] += 1
                if str(feat.get("OTHER_ID_TYPE", "")).strip().upper() in {"UNKNOWN", ""}:
                    otherid["OTHER_ID_unknown"] += 1
        for k in out:
            if k not in ("DATA_SOURCE", "RECORD_ID", "FEATURES"):
                payload_keys.add(k)

    if per_record:
        for src, out in zip(source_records, output_records):
            present = emitted_values(out)
            for field, value in src.items():
                if field in exempt_fields:
                    continue
                entries = value if isinstance(value, list) else [value]
                for entry in entries:
                    if not isinstance(entry, str) or not entry.strip():
                        continue
                    ps = pieces(entry)
                    covered = any(p in present or any(p in pv for pv in present) for p in ps)
                    if not covered:
                        parts = [p.strip() for p in entry.split("|")]
                        label = parts[0] if len(parts) > 1 and parts[0] else field
                        dropped[f"{field}: {label!r}"] += 1
    return histogram, otherid, payload_keys, dropped


def discover_payload_fields(output_path, override):
    if override:
        return [f.strip() for f in override.split(",") if f.strip()]
    manifest = Path(output_path).parent / "phase1_manifest.json"
    if manifest.exists():
        try:
            return list(json.loads(manifest.read_text()).get("payload_fields", []))
        except (json.JSONDecodeError, OSError):
            pass
    return []


def render(histogram, otherid, payload_keys, dropped):
    lines = ["ROUTING REPORT (measurement — reconsider, do not just dump)", ""]
    lines.append("Feature emission histogram (output):")
    for k, n in histogram.most_common():
        lines.append(f"  {k}: {n}")
    lines.append("")
    lines.append(
        f"Catch-all usage: OTHER_ID objects = {otherid.get('OTHER_ID_total', 0)} "
        f"(OTHER_ID_TYPE=UNKNOWN/blank: {otherid.get('OTHER_ID_unknown', 0)})"
    )
    lines.append(f"Payload root keys: {sorted(payload_keys) or '(none)'}")
    lines.append("")
    if dropped:
        total = sum(dropped.values())
        lines.append(
            f"DROPPED source value-entries ({total}) — present in the source, absent from the "
            f"output. For EACH, map it to its most specific Senzing feature, or state why it is "
            f"NOT a feature (free-text note). Do NOT dump it into OTHER_ID/payload to silence this."
        )
        for k, n in dropped.most_common():
            lines.append(f"  {k}: {n} dropped")
    else:
        lines.append("DROPPED source value-entries: none — every source value is carried through.")
    return "\n".join(lines)


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    override = None
    if "--payload-fields" in argv:
        i = argv.index("--payload-fields")
        if i + 1 < len(argv):
            override = argv[i + 1]
    if len(args) < 2:
        print(
            "usage: sz_routing_report.py <source.jsonl> <output.jsonl> [--payload-fields a,b]",
            file=sys.stderr,
        )
        return 2
    try:
        src, out = load_jsonl(args[0]), load_jsonl(args[1])
    except NotJsonlError as e:
        print(e, file=sys.stderr)
        return 2
    payload_fields = discover_payload_fields(args[1], override)
    print(render(*routing_report(src, out, payload_fields)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
