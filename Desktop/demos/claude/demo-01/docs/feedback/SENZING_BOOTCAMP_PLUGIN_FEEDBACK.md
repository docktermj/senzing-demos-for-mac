# Senzing Bootcamp Plugin Feedback

Feedback captured during the Senzing Bootcamp. Every entry is saved here, whatever it turns
out to be about. Entries routed `mcp-server` may **also** have been forwarded to Senzing —
only ever with your explicit yes, and with identifying details stripped; each entry's
`Upstream:` field records what happened.

**Started:** 2026-10-07

## Your Feedback

## Improvement: sz_json_analyzer.py fails on the default macOS Python (needs 3.10+)

**Date:** 2026-10-07
**Module:** Data Quality, Mapping, and Transformation
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** mcp-server — the analyzer script is delivered by mapping_workflow, and its step-4 command runs it with plain `python3`
**Upstream:** submitted 2026-10-07

### What happened

Running `python3 sz_json_analyzer.py <sample>.jsonl` exactly as mapping_workflow step 4 instructs failed with `SyntaxError: invalid syntax` at a `match family:` statement. The macOS system `python3` is 3.9.6; `match` needs Python 3.10+. Re-running with a Homebrew Python 3.12 worked.

### Why it matters

macOS ships Python 3.9 as `python3`, so every macOS bootcamper following the workflow's command hits a crash in the primary validation gate before any mapping is checked.

### Suggested fix

Either avoid `match` (keep the script 3.8+ compatible like its siblings) or state the minimum Python version in the step-4 instructions and the script's header, failing with a clear version message.

### Context when reported

- **Time:** 2026-10-07 10:26
- **Plugin version:** 0.6.1
- **Workstation:** macOS (Darwin 25.6.0), arm64
- **Model / effort:** claude-opus-5-5 / high
- **Context size:** Unknown
- **Module / step:** data_quality_mapping / 12-15 (filed at graduation)
- **Recent questions:** Mapping mode (verbose / concise)?
- **Bootcamper responses:** 2 (concise)
- **Behind the scenes:** mapping_workflow step 4 (generate_validate), Phase A sample lint
- **Observed problem:** analyzer SyntaxError on the documented command
- **Expected behavior:** the documented command runs on the platform's default python3
- **Divergence:** the script uses Python 3.10 syntax; the workflow does not state a version floor

## Improvement: sz_routing_report.py reports the RECORD_ID source field as dropped

**Date:** 2026-10-07
**Module:** Data Quality, Mapping, and Transformation
**Priority:** Low
**Source:** self-observed (assistant retrospective)
**Routing:** mcp-server — the routing report is an MCP-delivered script; its dropped list ignores the step-2 record_id_source
**Upstream:** submitted 2026-10-07

### What happened

On all three sources the routing report listed the record-key field (e.g. `order_id: 4500 dropped`, `contact_id: 4000 dropped`, `ticket_id: 1500 dropped`) as a dropped source value, although the mapper emits it as the root RECORD_ID exactly as the validated mapping prescribes (field disposition `ignore` + derived RECORD_ID). Payload fields were handled correctly once `--payload-fields` was passed.

### Why it matters

Step 4 tells the reader to reconsider every dropped entry; a field that is correctly used as RECORD_ID showing up as "dropped" on 100% of records invites a wrong fix (mapping the key into a feature such as OTHER_ID).

### Suggested fix

Exempt the record_id_source field (its values appear as RECORD_ID in the output) from the dropped list, the same way DATA_SOURCE/RECORD_ID attributes are exempt in the verbatim check.

### Context when reported

- **Time:** 2026-10-07 10:26
- **Plugin version:** 0.6.1
- **Workstation:** macOS (Darwin 25.6.0), arm64
- **Model / effort:** claude-opus-5-5 / high
- **Context size:** Unknown
- **Module / step:** data_quality_mapping / 15 (filed at graduation)
- **Recent questions:** Would you like a web page showing the quality analysis?
- **Bootcamper responses:** Yes
- **Behind the scenes:** mapping_workflow step 4 Phase C routing report, run with --payload-fields
- **Observed problem:** RECORD_ID source field listed as dropped on every record
- **Expected behavior:** a field consumed as RECORD_ID is not reported as dropped
- **Divergence:** the report checks feature/payload placement only, not the root RECORD_ID

## Improvement: Match-key audit reads only "-" components, which this data never produced despite conflicting features

**Date:** 2026-10-07
**Module:** Data processing
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the audit's method (count "-FEATURE" components in MATCH_KEY) is the plugin's design; the engine output itself was consistent
**Upstream:** not applicable

### What happened

The Phase D match-key audit found zero suppressors in all three buckets (322 / 2,544 / 4,592 comparisons) and reported "no finding". Later, a why_records call on a POSSIBLY_SAME pair whose relationship MATCH_KEY was "+NAME" showed email 0, phone 0 and address 29, all NO_CHANCE — clear disagreement that never appeared as a "-" component in any match key in this load.

### Why it matters

An audit whose only signal is "-" in the match key reports "no finding" on data where conflicting features exist but are not rendered as "-". The outcome was harmless here (the conflicts were genuine different people), but the audit cannot distinguish "no conflicts" from "conflicts not surfaced in the key".

### Suggested fix

Corroborate the key-string count with a structured source: MATCH_KEY_DETAILS.DENIALS[] (with SZ_INCLUDE_MATCH_KEY_DETAILS and a relations flag) or a why sample's FEATURE_SCORES, and report "could not measure" when the key carries no "-" but a sampled why shows NO_CHANCE features.

### Context when reported

- **Time:** 2026-10-07 10:26
- **Plugin version:** 0.6.1
- **Workstation:** macOS (Darwin 25.6.0), arm64
- **Model / effort:** claude-opus-5-5 / high
- **Context size:** Unknown
- **Module / step:** data_processing / Phase D match-key audit (found in query_visualize_discover step 3a)
- **Recent questions:** Is there anything you'd like to adjust? (query requirements)
- **Bootcamper responses:** No
- **Behind the scenes:** phaseD-validation.md match-key audit; later explain_match.java (whyRecords with FEATURE_SCORES)
- **Observed problem:** audit "no finding" while why showed NO_CHANCE email/phone on related pairs
- **Expected behavior:** an audit that can see feature disagreement
- **Divergence:** the audit relies on a key notation this engine output did not use for these conflicts

## Improvement: Generated-scenario collision check misses near-identical phone numbers

**Date:** 2026-10-07
**Module:** Data collection
**Priority:** Low
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the identifier-collision self-check (INV-239) is the plugin's specification for generated data
**Upstream:** not applicable

### What happened

The generator's collision check (no identifier value shared by two invented people) passed with 0 collisions, but two pairs of same-named customers received phone numbers one digit apart (e.g. 305-202-1173 vs 305-209-1173). Senzing merged both pairs on +NAME+PHONE, producing the only 2 over-merges in the run (precision 99.9%).

### Why it matters

The over-merges are artifacts of data generation, not of mapping or Senzing, yet they land in the bootcamper's accuracy figures and UAT write-up and need explaining.

### Suggested fix

Extend the self-check to near-duplicates for identifiers that Senzing compares fuzzily (phones by digits with an edit distance of 1, emails by local part), at least among people who share a name.

### Context when reported

- **Time:** 2026-10-07 10:26
- **Plugin version:** 0.6.1
- **Workstation:** macOS (Darwin 25.6.0), arm64
- **Model / effort:** claude-opus-5-5 / high
- **Context size:** Unknown
- **Module / step:** data_collection / 2 (found in data_processing Phase D)
- **Recent questions:** The generated sources have no load-order dependencies ... shall I proceed on both?
- **Bootcamper responses:** Yes
- **Behind the scenes:** ground-truth evaluation in data_processing Phase D
- **Observed problem:** 2 over-merged entities on +NAME+PHONE
- **Expected behavior:** generated customers do not share near-identical identifiers with a same-named customer
- **Divergence:** the check compares exact values only

## Improvement: Certificate name not detected during Bootcamp preparation

**Date:** 2026-10-07
**Module:** Bootcamp preparation
**Priority:** Low
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — Bootcamp preparation owns name detection (INV-134)
**Upstream:** not applicable

### What happened

`config/bootcamp_preferences.yaml` reached graduation with no `name` key, and the recap header read "**Bootcamper:** Bootcamper", so graduation had to ask for the certificate name.

### Why it matters

The pre-check handled it, but every recap page rendered before graduation carried the placeholder, and the detection gap is silent.

### Suggested fix

Record in progress whether name detection ran and what it found, so a miss is visible at the first recap write rather than at graduation.

### Context when reported

- **Time:** 2026-10-07 10:26
- **Plugin version:** 0.6.1
- **Workstation:** macOS (Darwin 25.6.0), arm64
- **Model / effort:** claude-opus-5-5 / high
- **Context size:** Unknown
- **Module / step:** graduation / pre-checks
- **Recent questions:** What name would you like printed on your Certificate of Completion?
- **Bootcamper responses:** (a full name)
- **Behind the scenes:** graduation pre-check 4
- **Observed problem:** no detected name in preferences
- **Expected behavior:** a detected display name, or a recorded reason none was found
- **Divergence:** detection left no trace
