#! /usr/bin/env python3
"""Render the Phase 1 quality assessment as a self-contained, offline HTML page.

Reads data/temp/quality_assessment.json (written by assess_quality.py) and writes
docs/visualizations/data_quality_assessment.html. Plain HTML/CSS bars, no scripts, no network.
Colors and type come from the plugin's brand_tokens.py, with an inlined fallback.
Usage: python3 src/quality/quality_page.py [path-to-plugin-scripts-dir]
"""

import html
import json
import sys
from pathlib import Path

if len(sys.argv) > 1:
    sys.path.insert(0, sys.argv[1])
try:
    import brand_tokens as bt
    T = {k: getattr(bt, k) for k in ("DEEP", "EMBER_CORE", "EMBER_HOT", "EMBER_SOFT", "WARM_OFF_WHITE",
                                     "DARK_INK", "BODY_INK", "WARM_LINE", "WHITE", "FONT_STACK", "CODE_FONT_STACK")}
except Exception:  # offline-safe fallback, same values
    T = {"DEEP": "#18160F", "EMBER_CORE": "#F57826", "EMBER_HOT": "#FF4E1F", "EMBER_SOFT": "#FDEEE3",
         "WARM_OFF_WHITE": "#FAF8F3", "DARK_INK": "#18160F", "BODY_INK": "#4A4640", "WARM_LINE": "#E5DFD3",
         "WHITE": "#FFFFFF", "FONT_STACK": "Roboto, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif",
         "CODE_FONT_STACK": "'Fira Code', 'Courier New', Courier, monospace"}

e = html.escape
q = json.loads(Path("data/temp/quality_assessment.json").read_text(encoding="utf-8"))


def label(band):
    return {">=80": "Strong — continue to mapping", "70-79": "Acceptable — some gaps",
            "<70": "Recommend fixing before mapping"}[band]


def bar(name, pct, sub=""):
    return (f'<div class="row"><div class="fname">{e(name)}</div>'
            f'<div class="track"><div class="fill" style="width:{pct:.1f}%"></div></div>'
            f'<div class="pct">{pct:.1f}%{(" " + e(sub)) if sub else ""}</div></div>')


cards = []
for src, r in q.items():
    comp_rows = "".join(bar(f, v) for f, v in r["per_field_presence_pct"].items())
    fmt_rows = "".join(bar(f, v["matching_pct"], f"match “{v['dominant']}”") for f, v in r["format_by_field"].items())
    weak = r["band"] != ">=80"
    cards.append(f"""
<section class="card{' weak' if weak else ''}">
  <h2>{e(src)}</h2>
  <div class="score"><span class="big">{r['quality_score']:.1f}%</span>
    <span class="tag{' tagweak' if weak else ''}">{e(label(r['band']))}</span></div>
  <p class="inputs">{r['records']:,} records · completeness {r['completeness']:.1f}% ·
     format consistency {r['format_consistency']:.1f}% · duplicate rate {r['duplicate_rate']:.1f}%</p>
  <h3>Field completeness (applicable fields)</h3>{comp_rows}
  <h3>Format consistency</h3>{fmt_rows}
</section>""")

page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Data Quality Assessment</title>
<style>
body{{margin:0;background:{T['WARM_OFF_WHITE']};color:{T['BODY_INK']};font-family:{T['FONT_STACK']}}}
header{{background:{T['DEEP']};color:#fff;padding:18px 24px;border-bottom:3px solid {T['EMBER_HOT']}}}
header h1{{margin:0;font-size:22px}} header p{{margin:6px 0 0;color:rgba(255,255,255,.6);font-size:14px}}
main{{padding:20px 24px;display:grid;grid-template-columns:repeat(auto-fit,minmax(360px,1fr));gap:18px}}
.card{{background:{T['WHITE']};border:1px solid {T['WARM_LINE']};border-radius:8px;padding:16px 18px}}
.card h2{{margin:0;color:{T['DARK_INK']};font-size:18px}} .card h3{{font-size:13px;color:{T['DARK_INK']};margin:16px 0 6px}}
.score{{margin:8px 0 2px;display:flex;align-items:center;gap:10px}} .big{{font-size:30px;font-weight:700;color:{T['EMBER_CORE']}}}
.tag{{background:{T['WARM_OFF_WHITE']};border:1px solid {T['WARM_LINE']};border-radius:12px;padding:2px 10px;font-size:12px}}
.tagweak{{background:{T['EMBER_SOFT']};border-color:{T['EMBER_CORE']};color:{T['DARK_INK']}}}
.inputs{{font-size:12px;margin:4px 0 0}}
.row{{display:grid;grid-template-columns:120px 1fr 150px;align-items:center;gap:8px;margin:4px 0;font-size:12px}}
.fname{{font-family:{T['CODE_FONT_STACK']};color:{T['DARK_INK']}}}
.track{{background:{T['WARM_LINE']};height:12px;border-radius:6px;overflow:hidden}} .fill{{background:{T['EMBER_CORE']};height:100%}}
.pct{{white-space:nowrap}} footer{{padding:0 24px 20px;font-size:12px}}
</style></head><body>
<header><h1>Data Quality Assessment</h1>
<p>Customer 360 scenario · score = 0.70 × completeness + 0.25 × format consistency + 0.05 × (100 − duplicate rate)</p></header>
<main>{''.join(cards)}</main>
<footer>All three sources are synthetic, generated for the bootcamp; their gaps are deliberate.
No source carries a record type, so every record is scored against features that apply to any type.</footer>
</body></html>"""
out = Path("docs/visualizations/data_quality_assessment.html")
out.write_text(page, encoding="utf-8")
print(out, len(page))
