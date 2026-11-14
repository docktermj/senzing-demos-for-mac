#!/usr/bin/env python3
"""Render the Data Quality, Mapping, and Transformation quality assessment as a self-contained,
offline HTML page (coverage bars per source and per-feature completeness by record type).

Usage (from the project root):
    python3 src/scripts/render_quality_visual.py <plugin-scripts-dir>

Reads  data/temp/quality_assessment.json  (written by src/scripts/assess_quality.py)
Writes docs/visualizations/data_quality_assessment.html

Brand: colors and fonts come from the bootcamp's brand_tokens.py (falling back to the same values
if it cannot be imported). No CDN, no web font, no script: plain HTML/CSS bars. Every value that
came from the data is HTML-escaped.
"""
import html
import json
import os
import sys

FALLBACK = {"WARM_OFF_WHITE": "#FAF8F3", "WHITE": "#FFFFFF", "DEEP": "#18160F", "DARK_INK": "#18160F",
            "BODY_INK": "#4A4640", "EMBER_CORE": "#F57826", "EMBER_HOT": "#FF4E1F",
            "EMBER_SOFT": "#FDEEE3", "WARM_LINE": "#E5DFD3",
            "FONT_STACK": "Roboto, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"}


def tokens(scripts_dir):
    t = dict(FALLBACK)
    try:
        sys.path.insert(0, scripts_dir)
        import brand_tokens as bt  # noqa: E402
        for k in t:
            t[k] = getattr(bt, k, t[k])
    except Exception:
        pass
    return t


def esc(v):
    return html.escape(str(v), quote=True)


def band(score):
    if score >= 80:
        return "✅ Strong", "strong"
    if score >= 70:
        return "⚠ Acceptable — some gaps", "ok"
    return "⚠ Recommend fixing before mapping", "weak"


def bar(pct, cls=""):
    pct = max(0.0, min(100.0, float(pct)))
    return (f'<div class="bar"><div class="fill {cls}" style="width:{pct:.1f}%"></div>'
            f'<span class="val">{pct:.1f}%</span></div>')


def main(argv):
    scripts_dir = argv[1] if len(argv) > 1 else ""
    t = tokens(scripts_dir)
    data = json.load(open("data/temp/quality_assessment.json", encoding="utf-8"))
    srcs = sorted(data["sources"], key=lambda s: -s["quality_score"])

    rows = []
    for s in srcs:
        label, cls = band(s["quality_score"])
        rows.append(
            f'<tr><td class="name">{esc(s["source"])}<div class="sub">{esc(s["records"])} records</div></td>'
            f'<td>{bar(s["quality_score"], cls)}<div class="sub">{esc(label)}</div></td>'
            f'<td>{bar(s["completeness"])}</td><td>{bar(s["format_consistency"])}</td>'
            f'<td>{bar(100 - s["duplicate_rate"])}</td></tr>')

    cards = []
    for s in srcs:
        types = [t_ for t_ in s["record_types"] if s["record_types"][t_]]
        head = "".join(f'<th>{esc("no RECORD_TYPE" if ty == "-" else ty)} ({esc(s["record_types"][ty])})</th>'
                       for ty in types)
        body = []
        for fam, by in sorted(s["family_presence_by_type"].items()):
            cells = "".join(f'<td>{bar(by.get(ty, 0))}</td>' for ty in types)
            body.append(f'<tr><td class="name">{esc(fam)}</td>{cells}</tr>')
        fmt = "".join(
            f'<li><b>{esc(f)}</b>: {esc(v["on_pattern"])} of {esc(v["populated"])} on the dominant format '
            f'<code>{esc(v["dominant"])}</code>; others {esc(", ".join(f"{k} ×{n}" for k, n in v["other_shapes"].items()))}</li>'
            for f, v in s["format_fields"].items()) or "<li>No format differences found.</li>"
        notes = []
        if s["records_without_record_type"]:
            notes.append(f'{esc(s["records_without_record_type"])} records carry no RECORD_TYPE.')
        notes.append(f'Type/name check: {len(s["type_name_check"]["candidates"])} of '
                     f'{esc(s["type_name_check"]["person_records"])} PERSON-typed records are candidates.')
        cards.append(
            f'<section class="card"><h3>{esc(s["source"])}</h3>'
            f'<p class="sub">Per-feature completeness by record type (identifiers counted as one group). '
            f'A feature a record type never carries is not counted against it.</p>'
            f'<table class="feat"><tr><th>Feature</th>{head}</tr>{"".join(body)}</table>'
            f'<h4>Format consistency</h4><ul>{fmt}</ul><p class="sub">{" ".join(notes)}</p></section>')

    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Data Quality Assessment</title>
<style>
:root{{--bg:{t['WARM_OFF_WHITE']};--surface:{t['WHITE']};--deep:{t['DEEP']};--ink:{t['DARK_INK']};
--muted:{t['BODY_INK']};--ember:{t['EMBER_CORE']};--hot:{t['EMBER_HOT']};--soft:{t['EMBER_SOFT']};--line:{t['WARM_LINE']}}}
*{{box-sizing:border-box}}body{{margin:0;font-family:{t['FONT_STACK']};color:var(--ink);background:var(--bg)}}
header{{background:var(--deep);color:#fff;padding:14px 24px;border-bottom:3px solid var(--ember)}}
header h1{{margin:0;font-size:20px}}main{{max-width:1100px;margin:0 auto;padding:20px 16px}}
.sub{{color:var(--muted);font-size:12px}}table{{width:100%;border-collapse:collapse}}
th{{text-align:left;font-size:12px;color:var(--muted);font-weight:600;padding:6px 8px;border-bottom:1px solid var(--line)}}
td{{padding:8px;border-bottom:1px solid var(--line);vertical-align:top}}td.name{{font-weight:600;white-space:nowrap}}
.bar{{position:relative;background:var(--soft);border-radius:4px;height:18px;min-width:120px}}
.fill{{background:var(--ember);height:100%;border-radius:4px}}.fill.strong{{background:var(--ember)}}
.fill.ok{{background:#E8A05C}}.fill.weak{{background:var(--hot)}}
.val{{position:absolute;right:6px;top:1px;font-size:11px;color:var(--ink)}}
.card{{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:14px 16px;margin:16px 0}}
.card h3{{margin:0 0 4px}}h4{{margin:14px 0 4px;font-size:13px}}code{{background:var(--soft);padding:0 4px;border-radius:3px}}
ul{{margin:4px 0 0 18px;padding:0;font-size:13px}}.formula{{font-size:13px;color:var(--muted)}}
@media (max-width:700px){{td,th{{font-size:11px}}.bar{{min-width:60px}}}}
</style></head><body>
<header><h1>Data Quality Assessment</h1></header>
<main>
<p class="formula">Quality = 0.70 × completeness + 0.25 × format consistency + 0.05 × (100 − duplicate rate).
Bands: ≥80 strong, 70-79 acceptable, below 70 recommend fixing before mapping. The score says the data is clean
enough to map; it does not say the mapping will be correct.</p>
<section class="card"><h3>Coverage by source</h3>
<table><tr><th>Source</th><th>Overall quality</th><th>Completeness</th><th>Format consistency</th><th>Unique keys</th></tr>
{"".join(rows)}</table></section>
{"".join(cards)}
</main></body></html>
"""
    os.makedirs("docs/visualizations", exist_ok=True)
    out = "docs/visualizations/data_quality_assessment.html"
    open(out, "w", encoding="utf-8").write(page)
    print("wrote", out, len(page), "bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
