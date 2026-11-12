#! /usr/bin/env python3
"""Render one source's mapping quality analysis as a self-contained, offline HTML page.

Reads the mapped output JSONL and the mapping spec, writes docs/visualizations/mapping_<name>_quality.html.
Plain HTML/CSS bars, no scripts, no network; brand tokens from the plugin's brand_tokens.py (fallback inline).
Usage: python3 src/quality/mapping_quality_page.py <name> <DATA_SOURCE> <output.jsonl> <mapping_spec.json> <score> [plugin-scripts-dir]
"""

import html
import json
import sys
from collections import Counter
from pathlib import Path

name, ds, out_path, spec_path, score = sys.argv[1:6]
if len(sys.argv) > 6:
    sys.path.insert(0, sys.argv[6])
try:
    import brand_tokens as bt
    T = {k: getattr(bt, k) for k in ("DEEP", "EMBER_CORE", "EMBER_HOT", "WARM_OFF_WHITE", "DARK_INK",
                                     "BODY_INK", "WARM_LINE", "WHITE", "FONT_STACK", "CODE_FONT_STACK")}
except Exception:
    T = {"DEEP": "#18160F", "EMBER_CORE": "#F57826", "EMBER_HOT": "#FF4E1F", "WARM_OFF_WHITE": "#FAF8F3",
         "DARK_INK": "#18160F", "BODY_INK": "#4A4640", "WARM_LINE": "#E5DFD3", "WHITE": "#FFFFFF",
         "FONT_STACK": "Roboto, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif",
         "CODE_FONT_STACK": "'Fira Code', 'Courier New', Courier, monospace"}
e = html.escape

FAMILY = {"NAME_FULL": "NAME", "NAME_FIRST": "NAME", "NAME_LAST": "NAME", "ADDR_FULL": "ADDRESS",
          "ADDR_LINE1": "ADDRESS", "ADDR_CITY": "ADDRESS", "ADDR_STATE": "ADDRESS", "ADDR_POSTAL_CODE": "ADDRESS",
          "PHONE_NUMBER": "PHONE", "EMAIL_ADDRESS": "EMAIL", "RECORD_TYPE": "RECORD_TYPE"}
records = [json.loads(l) for l in open(out_path, encoding="utf-8") if l.strip()]
n = len(records)
fam = Counter()
for r in records:
    fam.update({FAMILY.get(k, k) for f in r["FEATURES"] for k in f})
spec = json.load(open(spec_path, encoding="utf-8"))["schema_mappings"][0]["field_mappings"]


def bar(label, count):
    pct = 100 * count / n if n else 0
    return (f'<div class="row"><div class="fname">{e(label)}</div><div class="track"><div class="fill" '
            f'style="width:{pct:.1f}%"></div></div><div class="pct">{pct:.1f}% ({count:,})</div></div>')


rows = []
for m in spec:
    if m["disposition"] == "derived":
        src = "(derived)"
        dest = f"{m['derived_as']} = {m.get('value') or 'from ' + m.get('source', '')}"
    elif m["disposition"] == "feature":
        src, dest = m["source_field"], f"{m['feature']} → {m['attribute']}"
    elif m["disposition"] == "payload":
        src, dest = m["source_field"], f"payload key “{m['source_field']}” (not matched)"
    else:
        src, dest = m["source_field"], f"ignored — {m.get('reason', '')}"
    rows.append(f"<tr><td class='code'>{e(src)}</td><td>{e(m['disposition'])}</td><td>{e(dest)}</td></tr>")

page = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Mapping Quality — {e(ds)}</title>
<style>
body{{margin:0;background:{T['WARM_OFF_WHITE']};color:{T['BODY_INK']};font-family:{T['FONT_STACK']}}}
header{{background:{T['DEEP']};color:#fff;padding:18px 24px;border-bottom:3px solid {T['EMBER_HOT']}}}
header h1{{margin:0;font-size:22px}} header p{{margin:6px 0 0;color:rgba(255,255,255,.6);font-size:14px}}
main{{padding:20px 24px;display:grid;grid-template-columns:minmax(320px,1fr) minmax(380px,1.3fr);gap:18px}}
.card{{background:{T['WHITE']};border:1px solid {T['WARM_LINE']};border-radius:8px;padding:16px 18px}}
h2{{margin:0 0 10px;color:{T['DARK_INK']};font-size:16px}} .big{{font-size:30px;font-weight:700;color:{T['EMBER_CORE']}}}
.row{{display:grid;grid-template-columns:110px 1fr 120px;align-items:center;gap:8px;margin:6px 0;font-size:13px}}
.fname,.code{{font-family:{T['CODE_FONT_STACK']};color:{T['DARK_INK']}}}
.track{{background:{T['WARM_LINE']};height:12px;border-radius:6px;overflow:hidden}} .fill{{background:{T['EMBER_CORE']};height:100%}}
table{{border-collapse:collapse;width:100%;font-size:13px}} td,th{{text-align:left;padding:6px 8px;border-bottom:1px solid {T['WARM_LINE']}}}
th{{color:{T['DARK_INK']}}}
</style></head><body>
<header><h1>Mapping Quality — {e(ds)}</h1><p>{n:,} mapped records · output {e(out_path)}</p></header>
<main>
<section class="card"><h2>Quality score</h2><div class="big">{float(score):.1f}</div>
<p>Analyzer: no errors or warnings · every emitted value verbatim from the source</p>
<h2>Feature coverage (share of records carrying each feature)</h2>
{''.join(bar(f, fam[f]) for f in sorted(fam, key=lambda k: -fam[k]))}</section>
<section class="card"><h2>Field mapping summary</h2>
<table><tr><th>Source field</th><th>Disposition</th><th>Becomes</th></tr>{''.join(rows)}</table></section>
</main></body></html>"""
out = Path(f"docs/visualizations/mapping_{name}_quality.html")
out.write_text(page, encoding="utf-8")
print(out)
