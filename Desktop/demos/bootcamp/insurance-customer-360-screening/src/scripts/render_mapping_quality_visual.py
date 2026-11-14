#!/usr/bin/env python3
"""Render the per-source mapping quality page for Data Quality, Mapping, and Transformation:
feature coverage of the mapped output and the field mapping summary.

Usage (from the project root):
    python3 src/scripts/render_mapping_quality_visual.py <plugin-scripts-dir> <SOURCE> <mapped.jsonl> \
        <mapping_spec.json> [<mapping_state.json>]

Writes docs/visualizations/mapping_<source lower>_quality.html

Every number on the page is computed here from the mapped output, the mapping spec and the
Phase 1 quality formula (src/scripts/assess_quality.py), so the page cannot drift from the files.
Brand colors and fonts come from the bootcamp's brand_tokens.py (same fallback as the Phase 1
page). No CDN, no web font, no script: plain HTML/CSS bars. Every value that came from the data
is HTML-escaped.
"""
import collections
import html
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import assess_quality  # noqa: E402
from render_quality_visual import tokens, band, bar  # noqa: E402  (shared brand + bar helpers)

FAMILY = {  # attribute -> feature family, for the attributes this project's sources use
    "NAME_FIRST": "NAME", "NAME_LAST": "NAME", "NAME_MIDDLE": "NAME", "NAME_FULL": "NAME",
    "NAME_ORG": "NAME", "NAME_TYPE": "NAME", "DATE_OF_BIRTH": "DOB", "GENDER": "GENDER",
    "PHONE_NUMBER": "PHONE", "PHONE_TYPE": "PHONE", "EMAIL_ADDRESS": "EMAIL",
    "SSN_NUMBER": "SSN", "PASSPORT_NUMBER": "PASSPORT", "PASSPORT_COUNTRY": "PASSPORT",
    "DRIVERS_LICENSE_NUMBER": "DRLIC", "DRIVERS_LICENSE_STATE": "DRLIC",
    "NATIONAL_ID_NUMBER": "NATIONAL_ID", "NATIONAL_ID_COUNTRY": "NATIONAL_ID", "NATIONAL_ID_TYPE": "NATIONAL_ID",
    "OTHER_ID_NUMBER": "OTHER_ID", "OTHER_ID_TYPE": "OTHER_ID", "OTHER_ID_COUNTRY": "OTHER_ID",
    "TAX_ID_NUMBER": "TAX_ID", "TAX_ID_COUNTRY": "TAX_ID", "TAX_ID_TYPE": "TAX_ID",
    "NPI_NUMBER": "NPI_NUMBER", "REL_ANCHOR_DOMAIN": "REL_ANCHOR", "REL_ANCHOR_KEY": "REL_ANCHOR",
    "REL_POINTER_DOMAIN": "REL_POINTER", "REL_POINTER_KEY": "REL_POINTER",
    "REL_POINTER_ROLE": "REL_POINTER", "RECORD_TYPE": "RECORD_TYPE",
    "LEI_NUMBER": "LEI_NUMBER", "DUNS_NUMBER": "DUNS_NUMBER", "EMPLOYER": "EMPLOYER",
    "REGISTRATION_DATE": "REGISTRATION_DATE", "REGISTRATION_COUNTRY": "REGISTRATION_COUNTRY",
}


def family(attr):
    if attr in FAMILY:
        return FAMILY[attr]
    if attr.startswith("ADDR_"):
        return "ADDRESS"
    return attr.split("_")[0]


def esc(v):
    return html.escape(str(v), quote=True)


def main(argv):
    if len(argv) < 5:
        print(__doc__, file=sys.stderr)
        return 2
    scripts_dir, source, mapped, spec_path = argv[1:5]
    state_path = argv[5] if len(argv) > 5 else None
    t = tokens(scripts_dir)

    recs = [json.loads(l) for l in open(mapped, encoding="utf-8") if l.strip()]
    n = len(recs)
    fam_recs = collections.Counter()
    types = collections.Counter()
    for r in recs:
        seen = set()
        rt = "(none)"
        for f in r.get("FEATURES", []):
            for a in f:
                seen.add(family(a))
            if "RECORD_TYPE" in f:
                rt = f["RECORD_TYPE"]
        types[rt] += 1
        fam_recs.update(seen - {"RECORD_TYPE"})

    q = assess_quality.assess(source, mapped, assess_quality.catalog())
    spec = json.load(open(spec_path, encoding="utf-8"))
    sm = spec["schema_mappings"][0]
    state = json.load(open(state_path, encoding="utf-8")) if state_path and os.path.exists(state_path) else {}

    rows, counts = [], collections.Counter()
    for m in sm["field_mappings"]:
        d = m["disposition"]
        if d == "derived":
            src = m.get("source", "(constant)")
            rows.append((src, "derived", m["derived_as"], m.get("value", "from " + src)))
            continue
        counts[d] += 1
        target = f'{m["feature"]} / {m["attribute"]}' if d == "feature" else ""
        rows.append((m["source_field"], d, target, m.get("reason", m.get("ref", ""))))
    td = sm.get("type_discriminator")
    if td:
        counts["feature"] += 1
        rows.append((td["field"], "type discriminator", "RECORD_TYPE",
                     "sets the record type per record: " + ", ".join(td["types"])))
        over = {}
        for ty, spec_t in td["types"].items():
            for f, tgt in spec_t["field_overrides"].items():
                over.setdefault(f, []).append(f'{tgt["attribute"]} when {ty}')
        for f, desc in over.items():
            counts["feature"] += 1
            rows.append((f, "feature (by type)", "NAME", "; ".join(desc)))

    _, cls = band(q["quality_score"])
    label = {"strong": "✅ Strong", "ok": "⚠ Acceptable, some gaps",
             "weak": "⚠ Below 70: source data gaps"}[cls]
    cov = "".join(
        f'<tr><td class="name">{esc(fam)}</td><td>{bar(100.0 * c / n)}</td>'
        f'<td class="num">{esc(c)} of {esc(n)}</td></tr>'
        for fam, c in sorted(fam_recs.items(), key=lambda kv: (-kv[1], kv[0])))
    maprows = "".join(
        f'<tr><td><code>{esc(s)}</code></td><td>{esc(d)}</td><td>{esc(tg)}</td><td class="sub">{esc(note)}</td></tr>'
        for s, d, tg, note in rows)
    typeline = ", ".join(f"{esc(k)} {esc(v)}" for k, v in types.most_common())
    notes = [f'<li><b>{esc(d["field"])}</b>: {esc(d["chosen"])}</li>' for d in spec.get("decisions", [])]
    for rid, txt in state.get("decisions", {}).items():
        label = f"Record {rid}" if str(rid).replace("/", "").isdigit() else rid
        notes.append(f"<li><b>{esc(label)}</b>: {esc(txt)}</li>")
    for what, txt in state.get("analyzer_notices", {}).items():
        notes.append(f"<li>Analyzer notice <b>{esc(what)}</b>: {esc(txt)}</li>")
    for why, c in state.get("last_run", {}).get("dropped", {}).items():
        notes.append(f"<li>Skipped {esc(c)} × {esc(why)} (a label with no value to qualify)</li>")

    page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mapping Quality: {esc(source)}</title>
<style>
:root{{--bg:{t['WARM_OFF_WHITE']};--surface:{t['WHITE']};--deep:{t['DEEP']};--ink:{t['DARK_INK']};
--muted:{t['BODY_INK']};--ember:{t['EMBER_CORE']};--hot:{t['EMBER_HOT']};--soft:{t['EMBER_SOFT']};--line:{t['WARM_LINE']}}}
*{{box-sizing:border-box}}body{{margin:0;font-family:{t['FONT_STACK']};color:var(--ink);background:var(--bg)}}
header{{background:var(--deep);color:#fff;padding:14px 24px;border-bottom:3px solid var(--ember)}}
header h1{{margin:0;font-size:20px}}main{{max-width:1100px;margin:0 auto;padding:20px 16px}}
.sub{{color:var(--muted);font-size:12px}}table{{width:100%;border-collapse:collapse}}
th{{text-align:left;font-size:12px;color:var(--muted);font-weight:600;padding:6px 8px;border-bottom:1px solid var(--line)}}
td{{padding:7px 8px;border-bottom:1px solid var(--line);vertical-align:top;font-size:13px}}
td.name{{font-weight:600;white-space:nowrap}}td.num{{white-space:nowrap;color:var(--muted)}}
.bar{{position:relative;background:var(--soft);border-radius:4px;height:18px;min-width:120px}}
.fill{{background:var(--ember);height:100%;border-radius:4px}}.fill.ok{{background:#E8A05C}}.fill.weak{{background:var(--hot)}}
.val{{position:absolute;right:6px;top:1px;font-size:11px;color:var(--ink)}}
.card{{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:14px 16px;margin:16px 0}}
.card h3{{margin:0 0 6px}}code{{background:var(--soft);padding:0 4px;border-radius:3px;font-size:12px}}
.kpis{{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px}}
.kpi{{background:var(--surface);border:1px solid var(--line);border-radius:8px;padding:10px 12px}}
.kpi b{{display:block;font-size:22px}}ul{{margin:4px 0 0 18px;padding:0;font-size:13px}}
.wrap{{overflow-x:auto}}
@media (max-width:700px){{td,th{{font-size:11px}}.bar{{min-width:60px}}}}
</style></head><body>
<header><h1>Mapping Quality: {esc(source)}</h1></header>
<main>
<div class="kpis">
<div class="kpi"><span class="sub">Overall quality</span><b>{esc(q['quality_score'])}</b><span class="sub">{esc(label)}</span></div>
<div class="kpi"><span class="sub">Completeness</span><b>{esc(q['completeness'])}%</b></div>
<div class="kpi"><span class="sub">Format consistency</span><b>{esc(q['format_consistency'])}%</b></div>
<div class="kpi"><span class="sub">Records mapped</span><b>{esc(n)}</b><span class="sub">{typeline}</span></div>
<div class="kpi"><span class="sub">Source fields</span><b>{esc(sum(counts.values()))}</b>
<span class="sub">{esc(counts['feature'])} feature · {esc(counts['payload'])} payload · {esc(counts['ignore'])} ignored</span></div>
</div>
<p class="sub">Quality = 0.70 × completeness + 0.25 × format consistency + 0.05 × (100 − duplicate rate), the same
formula as the data quality assessment, applied to the mapped output. Mapping routes values without changing them,
so it cannot add data the source does not hold. These checks show the output is well-formed; whether each field
means what Senzing assumes is checked after loading, in Data processing.</p>
<section class="card"><h3>Feature coverage</h3>
<p class="sub">Share of the {esc(n)} mapped records that carry each Senzing feature.</p>
<table><tr><th>Feature</th><th>Records carrying it</th><th></th></tr>{cov}</table></section>
<section class="card"><h3>Decisions and notes</h3><ul>{''.join(notes) or '<li>None.</li>'}</ul></section>
<section class="card"><h3>Field mapping summary</h3><div class="wrap">
<table><tr><th>Source field</th><th>Disposition</th><th>Senzing target</th><th>Note</th></tr>{maprows}</table></div></section>
</main></body></html>
"""
    os.makedirs("docs/visualizations", exist_ok=True)
    out = f"docs/visualizations/mapping_{source.lower()}_quality.html"
    open(out, "w", encoding="utf-8").write(page)
    print("wrote", out, len(page), "bytes;", "fields", dict(counts), "families", len(fam_recs))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
