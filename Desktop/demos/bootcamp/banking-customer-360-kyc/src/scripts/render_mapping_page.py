#!/usr/bin/env python3
"""Render a source's mapping quality analysis as a self-contained, offline HTML page.

Usage: python3 -I src/scripts/render_mapping_page.py <DATA_SOURCE> <mapped_output.jsonl> <mapping_spec.json> <quality_score>

Reads the transformer's mapped output (Senzing JSON) and the source's mapping spec, takes colors and
fonts from the Senzing brand tokens (src/server/assets/brand.json), and writes
docs/visualizations/mapping_<data_source_lower>_quality.html. Plain HTML/CSS: no scripts, CDN or
web fonts. Every data-sourced string is HTML-escaped.
"""
import collections
import html
import json
import sys

ds, out_path, spec_path, quality = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4])
b = json.load(open("src/server/assets/brand.json"))["brand"]
spec = json.load(open(spec_path))
e = lambda s: html.escape(str(s), quote=True)
FAMILY = [("RECORD_TYPE", "RECORD_TYPE"), ("NAME_", "NAME"), ("DATE_OF_BIRTH", "DOB"), ("ADDR_", "ADDRESS"),
          ("PHONE_", "PHONE"), ("EMAIL_", "EMAIL"), ("SSN_", "SSN"), ("DRIVERS_LICENSE_", "DRLIC"),
          ("PASSPORT_", "PASSPORT"), ("NATIONAL_ID_", "NATIONAL_ID"), ("TAX_ID_", "TAX_ID"),
          ("LEI_", "LEI_NUMBER"), ("NPI_", "NPI_NUMBER"), ("REL_ANCHOR_", "REL_ANCHOR"), ("REL_POINTER_", "REL_POINTER"),
          ("GENDER", "GENDER"), ("EMPLOYER", "EMPLOYER"), ("REGISTRATION_DATE", "REGISTRATION_DATE")]
QUAL = {"ADDR_TYPE", "PHONE_TYPE", "NAME_TYPE"}


def family(obj):
    for k in obj:
        if k in QUAL:
            continue
        for p, f in FAMILY:
            if k.startswith(p):
                return f
    return None


recs = [json.loads(l) for l in open(out_path, encoding="utf-8") if l.strip()]
n = len(recs)
feat = collections.Counter()
payload = collections.Counter()
for r in recs:
    fams = {family(o) for o in r.get("FEATURES", [])} - {None}
    feat.update(fams)
    payload.update(k for k, v in r.items() if k not in ("DATA_SOURCE", "RECORD_ID", "FEATURES") and v is not None)


def bar(label, count, color):
    pct = 100.0 * count / n if n else 0.0
    return (f'<div class="row"><div class="lbl">{e(label)}</div><div class="track"><div class="fill" '
            f'style="width:{pct:.1f}%;background:{e(color)}"></div></div>'
            f'<div class="val">{pct:.1f}% <span class="muted">({count})</span></div></div>')


band = ("✅ Strong", b["green"]) if quality >= 80 else (("⚠ Acceptable — some gaps", b["accent"])
        if quality >= 70 else ("⚠ Needs attention", b["accent_hot"]))
rows = "".join(
    f"<tr><td><code>{e(m['source_field'])}</code></td><td>{e(m['disposition'])}</td>"
    f"<td>{e(m.get('feature', ''))}</td><td><code>{e(m.get('attribute', ''))}</code></td><td>{e(m.get('note', ''))}</td></tr>"
    for m in spec["fields"])
page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mapping Quality: {e(ds)}</title>
<style>
:root{{--bg:{b['bg']};--surface:{b['surface']};--ink:{b['ink']};--muted:{b['muted']};--line:{b['line']};--dark:{b['dark']};}}
body{{margin:0;background:var(--bg);color:var(--ink);font-family:{b['font']}}}
header{{background:var(--dark);color:#fff;padding:20px 24px;border-bottom:4px solid {b['accent']}}}
header h1{{margin:0;font-size:22px}} header p{{margin:6px 0 0;opacity:.85;font-size:14px}}
main{{max-width:980px;margin:0 auto;padding:16px}}
.card{{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:16px 18px;margin:16px 0}}
h2{{margin:0 0 8px;font-size:17px}} .badge{{color:#fff;font-size:12px;padding:3px 8px;border-radius:12px;margin-left:8px}}
.row{{display:flex;align-items:center;gap:10px;margin:4px 0;font-size:13px}}
.lbl{{width:170px;flex:none}} .val{{width:110px;text-align:right;flex:none;font-variant-numeric:tabular-nums}}
.track{{flex:1;height:12px;background:var(--line);border-radius:6px;overflow:hidden}} .fill{{height:100%}}
.muted{{color:var(--muted);font-size:12px}} code{{font-family:{b['code_font']};font-size:12px}}
table{{border-collapse:collapse;width:100%;font-size:13px}} th,td{{border-bottom:1px solid var(--line);padding:5px 6px;text-align:left;vertical-align:top}}
th{{color:var(--muted);font-weight:600}}
</style></head><body>
<header><h1>Mapping Quality: {e(ds)}</h1>
<p>{n} records mapped · quality {quality:.1f} <span class="badge" style="background:{e(band[1])}">{e(band[0])}</span> · output <code>{e(out_path)}</code></p></header>
<main>
<section class="card"><h2>Feature coverage (share of mapped records carrying each feature)</h2>
{''.join(bar(f, c, b['accent']) for f, c in sorted(feat.items(), key=lambda kv: -kv[1]))}</section>
<section class="card"><h2>Payload kept on each record (not used for matching)</h2>
{''.join(bar(k, c, b['muted']) for k, c in sorted(payload.items(), key=lambda kv: -kv[1]))}</section>
<section class="card"><h2>Field mapping summary</h2>
<table><tr><th>Source field</th><th>Disposition</th><th>Feature</th><th>Attribute</th><th>Note</th></tr>{rows}</table>
<p class="muted">{e(spec.get('summary', ''))}</p></section>
</main></body></html>
"""
dest = f"docs/visualizations/mapping_{ds.lower()}_quality.html"
open(dest, "w", encoding="utf-8").write(page)
print("wrote", dest, len(page), "bytes")
