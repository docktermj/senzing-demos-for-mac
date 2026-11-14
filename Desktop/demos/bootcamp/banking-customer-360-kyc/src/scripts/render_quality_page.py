#!/usr/bin/env python3
"""Render the data quality assessment as a self-contained, offline HTML page.

Reads data/temp/quality_assessment.json (written by assess_quality.py) and the Senzing brand
tokens generated into src/server/assets/brand.json, and writes
docs/visualizations/data_quality_assessment.html. Plain HTML/CSS bars: no scripts, no CDN, no web
fonts, so the page renders on an air-gapped machine. Every data-sourced string is HTML-escaped.
"""
import html
import json

QA = "data/temp/quality_assessment.json"
BRAND = "src/server/assets/brand.json"
OUT = "docs/visualizations/data_quality_assessment.html"

qa = json.load(open(QA))
b = json.load(open(BRAND))["brand"]
e = lambda s: html.escape(str(s), quote=True)


def band(score):
    if score >= 80:
        return "✅ Strong", b["green"]
    if score >= 70:
        return "⚠ Acceptable — some gaps", b["accent"]
    return "⚠ Recommend fixing before mapping", b["accent_hot"]


def bar(label, pct, color):
    return (f'<div class="row"><div class="lbl">{e(label)}</div>'
            f'<div class="track"><div class="fill" style="width:{max(0.0, min(100.0, pct)):.1f}%;'
            f'background:{e(color)}"></div></div><div class="val">{pct:.1f}%</div></div>')


cards = []
for ds, a in sorted(qa.items(), key=lambda kv: -kv[1]["quality"]):
    text, color = band(a["quality"])
    types = ", ".join(f"{e(k)} {v}" for k, v in a["record_types"].items())
    dims = (bar("Overall quality", a["quality"], color) + bar("Completeness", a["completeness"], b["muted"])
            + bar("Format consistency", a["format_consistency"], b["muted"])
            + bar("Unique record keys", 100 - a["duplicate_rate"], b["muted"]))
    feats = "".join(bar(f, pct, b["accent"]) for f, pct in
                    sorted(a["feature_coverage"].items(), key=lambda kv: -kv[1]))
    fmt = "".join(f"<li><code>{e(k)}</code>: {v['off_pattern']} of {v['of']} values differ from the "
                  f"dominant shape <code>{e(v['dominant'])}</code></li>" for k, v in a["format_detail"].items())
    tn = a["type_name_check"]
    cards.append(f"""
<section class="card">
  <h2>{e(ds)} <span class="badge" style="background:{e(color)}">{a['quality']:.1f} · {e(text)}</span></h2>
  <p class="muted">{a['records']} records · {types}</p>
  <h3>Score dimensions</h3>{dims}
  <h3>Per-field completeness (applicable records only)</h3>{feats}
  <h3>Format findings</h3><ul>{fmt or '<li>No off-pattern values.</li>'}</ul>
  <p class="muted">Type/name check: {tn['candidates']} of {tn['person_typed']} PERSON-typed records are candidates.</p>
</section>""")

page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Data Quality Assessment</title>
<style>
:root{{--bg:{b['bg']};--surface:{b['surface']};--ink:{b['ink']};--muted:{b['muted']};--line:{b['line']};--dark:{b['dark']};}}
body{{margin:0;background:var(--bg);color:var(--ink);font-family:{b['font']};}}
header{{background:var(--dark);color:#fff;padding:20px 24px;border-bottom:4px solid {b['accent']};}}
header h1{{margin:0;font-size:22px}} header p{{margin:6px 0 0;opacity:.85;font-size:14px}}
main{{max-width:980px;margin:0 auto;padding:16px}}
.card{{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:16px 18px;margin:16px 0}}
h2{{margin:0 0 4px;font-size:18px}} h3{{font-size:13px;text-transform:uppercase;letter-spacing:.04em;color:var(--muted);margin:16px 0 6px}}
.badge{{color:#fff;font-size:12px;padding:3px 8px;border-radius:12px;margin-left:8px;vertical-align:middle}}
.row{{display:flex;align-items:center;gap:10px;margin:4px 0;font-size:13px}}
.lbl{{width:170px;flex:none}} .val{{width:56px;text-align:right;flex:none;font-variant-numeric:tabular-nums}}
.track{{flex:1;height:12px;background:var(--line);border-radius:6px;overflow:hidden}} .fill{{height:100%}}
.muted{{color:var(--muted);font-size:13px}} code{{font-family:{b['code_font']};font-size:12px}}
ul{{margin:4px 0;padding-left:20px;font-size:13px}}
</style></head><body>
<header><h1>Data Quality Assessment</h1>
<p>Five banking sources · quality = 0.70 × completeness + 0.25 × format consistency + 0.05 × (100 − duplicate rate) · identifiers scored as one group</p></header>
<main>{''.join(cards)}</main></body></html>
"""
open(OUT, "w", encoding="utf-8").write(page)
print("wrote", OUT, len(page), "bytes")
