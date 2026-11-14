# Senzing Bootcamp Plugin Feedback

Feedback captured during the Senzing Bootcamp. Every entry is saved here, whatever it turns
out to be about. Entries routed `mcp-server` may **also** have been forwarded to Senzing —
only ever with your explicit yes, and with identifying details stripped; each entry's
`Upstream:` field records what happened.

**Started:** 2026-11-14

## Your Feedback

## Improvement: Discoveries PDF cannot print the non-Latin names the generated KYC scenario contains

**Date:** 2026-11-14
**Module:** Query, Visualize and Discover
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the bootcamp generated the multi-script names and its own bundled PDF renderer (Latin-1 core fonts) cannot set them; no Senzing MCP tool is involved
**Upstream:** not applicable

### What happened

The bootcamp-generated banking Customer 360 / KYC scenario deliberately includes people whose names
appear in Cyrillic, Arabic and Hangul script (realistic for watchlist and KYC data). When
`scripts/generate_discoveries_pdf.py` rendered `docs/bootcamp_data_discoveries.pdf`, those names
could not be set in the Latin-1 core fonts and would have been dropped. The workaround was to cite,
for each affected entity, a Latin-script name that the same resolved entity also carries in another
source record — which only works when such a record exists.

### Why it matters

Cross-script name matching is one of the most compelling discoveries in a KYC scenario, and it is
exactly the content the keepsake PDF cannot show. Any generated or real dataset with non-Latin names
hits this, and the scenario generator itself produces such data.

### Suggested fix

Either embed a Unicode TTF (e.g. DejaVu Sans / Noto) in the document and recap renderers when
`fpdf2` is available, or have the scenario generator keep a Latin-script alias on every entity whose
name it writes in a non-Latin script, and document the substitution pattern in the discoveries step.

### Context when reported

- **Time:** 2026-11-14
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / high
- **Context size:** Unknown (session crossed a compaction boundary)
- **Module / step:** query_visualize_discover / discoveries PDF (filed at graduation)
- **Recent questions:** Would you like to graduate now and generate your production project and recap?
- **Bootcamper responses:** yes
- **Behind the scenes:** Module 7 discoveries document rendered with the bundled generate_discoveries_pdf.py
- **Observed problem:** Non-Latin entity names would be dropped from the PDF
- **Expected behavior:** The keepsake shows the names the discoveries document cites
- **Divergence:** Renderer uses Latin-1 core fonts; scenario generator writes multi-script names without guaranteeing a Latin alias

## Improvement: Graduation says sdk_guide names SENZING_LICENSE_FILE, but it does not

**Date:** 2026-11-14
**Module:** Bootcamp graduation
**Priority:** Low
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the graduation skill's sourcing instruction does not match what the MCP tool returns; the tool's own license guidance is internally consistent
**Upstream:** not applicable

### What happened

Graduation Step 3 requires `.env.example` to carry `SENZING_LICENSE_FILE` and says to confirm that
name in `sdk_guide(topic='load', language=…, record_count=<above the default limit>)`
`compatibility_notes`. Called with `language='java'`, `record_count=100000`, the response's
compatibility notes and engine-config notes name only the engine-configuration keys
`LICENSESTRINGBASE64` and `LICENSEFILE` inside `PIPELINE`; no environment variable for the license
appears. To stay truthful, the production project's own env script reads `SENZING_LICENSE_FILE` and
writes it into the engine configuration as `PIPELINE.LICENSEFILE`.

### Why it matters

The skill presents `SENZING_LICENSE_FILE` as an MCP-confirmed variable. Shipped as-is in a
`.env.example`, a reader would expect the engine to read it, and an unread variable fails later as a
capacity error.

### Suggested fix

Either point the instruction at the call that does return the variable, or state that the variable
is project-level and must be wired into `PIPELINE.LICENSEFILE` by the project's own scripts.

### Context when reported

- **Time:** 2026-11-14
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / high
- **Context size:** Unknown
- **Module / step:** graduation / Step 3
- **Recent questions:** May I install the Piper neural voice so the narration sounds natural?
- **Bootcamper responses:** Yes, install Piper
- **Behind the scenes:** graduation Step 3 production configuration files
- **Observed problem:** sdk_guide returned no SENZING_LICENSE_FILE
- **Expected behavior:** the skill's cited call names the variable
- **Divergence:** skill text and tool output disagree
