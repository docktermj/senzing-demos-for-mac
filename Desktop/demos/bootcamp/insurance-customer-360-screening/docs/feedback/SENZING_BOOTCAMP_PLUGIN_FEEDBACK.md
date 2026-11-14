# Senzing Bootcamp Plugin Feedback

Feedback captured during the Senzing Bootcamp. Every entry is saved here, whatever it turns
out to be about. Entries routed `mcp-server` may **also** have been forwarded to Senzing —
only ever with your explicit yes, and with identifying details stripped; each entry's
`Upstream:` field records what happened.

**Started:** 2026-11-14

## Your Feedback

## Improvement: Screenshot capture has no backend on a stock Mac, and cannot capture extra app tabs

**Date:** 2026-11-14
**Module:** Query, Visualize and Discover
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the bundled capture_screenshots.py and its tab allow-list are plugin code
**Upstream:** not applicable

### What happened

capture_screenshots.py reported no headless capability: it looks only for Chrome, Chromium or Edge, none of which were installed. The fallback that worked was the Claude Desktop built-in browser pane (navigate to ?tab=<id>, screenshot, convert with sips). The tool's TABS allow-list also accepts only the six standard tab ids, so the three business tabs the bootcamper asked for in the same app (Overview, Customer 360, Screening & Due Diligence) could not be captured or recorded by the tool, and generate_recap_pdf.py --check then reported 'the manifest undercounts' until the manifest was hand-edited to list them.

### Why it matters

On macOS without Chrome every visualization capture falls back to an undocumented route, and any app that legitimately carries extra tabs fails the recap's coverage check.

### Suggested fix

Document the built-in-browser capture route as a supported fallback, and let the manifest and --check accept app-declared extra tab ids (for example, from the nav buttons) instead of a fixed list.

### Context when reported

- **Time:** 2026-11-14 16:09 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / Unknown
- **Context size:** Unknown (the session crossed at least one compaction boundary)
- **Module / step:** graduation / Step 0 (filed at graduation)
- **Recent questions:** Ready for me to stop the visualization server? / Would you like to graduate now and generate your production project and recap?
- **Bootcamper responses:** yes / Yes, graduate
- **Behind the scenes:** graduation skill, Step 0 session retrospective
- **Observed problem:** capture_screenshots.py exits 'No headless screenshot capability' on macOS without Chrome; --tabs rejects unknown ids; --check flags extra PNGs as an undercount.
- **Expected behavior:** Capture succeeds or a documented fallback is named; extra app tabs can be captured and counted.
- **Divergence:** The backend list and the tab allow-list both assume the reference six-tab app on a machine with Chrome.

## Improvement: Visualization contract allows exactly six tabs, but the business problem asked for three business tabs in the app

**Date:** 2026-11-14
**Module:** Query, Visualize and Discover
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the conflict is between two plugin documents: the visualization contract and Module 1's desired-output capture
**Upstream:** not applicable

### What happened

docs/business_problem.md recorded the desired output as a web app with Overview, Customer 360 and Screening & Due Diligence tabs, with analyst dispositions. Phase 1 step 3c and visualization-api-reference.md say the app's six tabs are 'the whole set' and forbid adding tabs. I resolved it by injecting the three business tabs into the same single app, ahead of the six Senzing tabs, without editing the generated page template.

### Why it matters

The bootcamper's own stated deliverable and the contract point in opposite directions, and nothing tells the guide which wins.

### Suggested fix

Add a sanctioned extension point to the contract for bootcamper-requested business tabs, with the ordering and capture rules that apply to them.

### Context when reported

- **Time:** 2026-11-14 16:09 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / Unknown
- **Context size:** Unknown (the session crossed at least one compaction boundary)
- **Module / step:** graduation / Step 0 (filed at graduation)
- **Recent questions:** Ready for me to stop the visualization server? / Would you like to graduate now and generate your production project and recap?
- **Bootcamper responses:** yes / Yes, graduate
- **Behind the scenes:** graduation skill, Step 0 session retrospective
- **Observed problem:** Contract text forbids additional tabs; the bootcamper's recorded desired output requires them.
- **Expected behavior:** A single documented rule for business tabs requested in Module 1.
- **Divergence:** The contract was written for the Truth Set reference app and does not consider Module 1's desired output.

## Improvement: business_problem.md points to 'rules in the original description' that were never brought into the project

**Date:** 2026-11-14
**Module:** Discover the Business Problem
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — Module 1 wrote a document that depends on text it did not capture
**Upstream:** not applicable

### What happened

The Notes section says identity verification and ownership screening 'follow the rules in the original description'. That description (with the 50% rule, SIU severities, PEP Low for P&C-only, synthetic identity and roll-up) lived only in the bootcamper's source folder outside the project. I had to find and read it there in Query, Visualize and Discover, then copy it into docs/reference/screening_rules.md.

### Why it matters

The query programs and screening queue depend on exact rules; a project that references text it does not contain cannot be rebuilt or audited from its own files.

### Suggested fix

When the bootcamper supplies a long business description, Module 1 should copy the rule-bearing sections verbatim into the project (for example docs/reference/), not summarize and refer back.

### Context when reported

- **Time:** 2026-11-14 16:09 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / Unknown
- **Context size:** Unknown (the session crossed at least one compaction boundary)
- **Module / step:** graduation / Step 0 (filed at graduation)
- **Recent questions:** Ready for me to stop the visualization server? / Would you like to graduate now and generate your production project and recap?
- **Bootcamper responses:** yes / Yes, graduate
- **Behind the scenes:** graduation skill, Step 0 session retrospective
- **Observed problem:** Rule details missing from the project; recovered from an external file.
- **Expected behavior:** All rules the later modules implement are inside the project.
- **Divergence:** Module 1's refinement summarized the rules and kept only a pointer to the original.

## Improvement: mapping_workflow analyzer flags TAX_ID_TYPE as custom or unrecognized

**Date:** 2026-11-14
**Module:** Data Quality, Mapping, and Transformation
**Priority:** Low
**Source:** self-observed (assistant retrospective)
**Routing:** mcp-server — the analyzer's notice contradicts the installed configuration and the documentation; a perfect plugin would still see it
**Upstream:** submitted 2026-11-14

### What happened

During mapping, the mapping_workflow analyzer reported TAX_ID_TYPE as a custom or unrecognized attribute. search_docs and the installed g2config.json both show TAX_ID_TYPE as a standard attribute of the TAX_ID feature, so the notice was a false positive and was recorded as such.

### Why it matters

A false 'unrecognized attribute' notice invites a bootcamper to drop or rename a correct mapping.

### Suggested fix

Align the analyzer's attribute list with the Entity Specification (TAX_ID_TYPE is a TAX_ID attribute).

### Context when reported

- **Time:** 2026-11-14 16:09 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / Unknown
- **Context size:** Unknown (the session crossed at least one compaction boundary)
- **Module / step:** graduation / Step 0 (filed at graduation)
- **Recent questions:** Ready for me to stop the visualization server? / Would you like to graduate now and generate your production project and recap?
- **Bootcamper responses:** yes / Yes, graduate
- **Behind the scenes:** graduation skill, Step 0 session retrospective
- **Observed problem:** Analyzer notice: TAX_ID_TYPE custom/unrecognized.
- **Expected behavior:** No notice for a documented attribute.
- **Divergence:** The analyzer's attribute table appears to lag the documented feature definition.

## Improvement: mapping_workflow analyzer rejects an anchor-only source as REL_ANCHOR with zero REL_POINTER

**Date:** 2026-11-14
**Module:** Data Quality, Mapping, and Transformation
**Priority:** Low
**Source:** self-observed (assistant retrospective)
**Routing:** mcp-server — the analyzer judges relationship completeness per file, but the pointers legitimately live in other sources
**Upstream:** submitted 2026-11-14

### What happened

CREDIT_BUREAU's mapped file carries REL_ANCHOR features and no REL_POINTER, because the pointing records are in other sources. The analyzer failed it with 'REL_ANCHOR but ZERO REL_POINTER'. Re-running with --rel-explanation let it pass. The relationships resolved correctly once all sources were loaded.

### Why it matters

A correct multi-source relationship design fails validation, and a bootcamper may remove the anchors to make it pass.

### Suggested fix

Treat anchor-only files as valid when the explanation is supplied, or downgrade the check to a warning that names the cross-source case.

### Context when reported

- **Time:** 2026-11-14 16:09 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / Unknown
- **Context size:** Unknown (the session crossed at least one compaction boundary)
- **Module / step:** graduation / Step 0 (filed at graduation)
- **Recent questions:** Ready for me to stop the visualization server? / Would you like to graduate now and generate your production project and recap?
- **Bootcamper responses:** yes / Yes, graduate
- **Behind the scenes:** graduation skill, Step 0 session retrospective
- **Observed problem:** Analyzer failure: REL_ANCHOR but ZERO REL_POINTER.
- **Expected behavior:** Anchor-only sources accepted in a multi-source design.
- **Divergence:** The check assumes anchors and pointers sit in the same file.

## Improvement: zsh on macOS does not word-split unquoted variables in generated shell loops

**Date:** 2026-11-14
**Module:** General
**Priority:** Low
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the ground rules could warn that the macOS shell is zsh; neither Senzing nor the host is at fault
**Upstream:** not applicable

### What happened

Twice the generated commands assumed bash word-splitting: a list of entity IDs in $ids, and 'for p in "a b" ...; $p' pairs. zsh passed each as one argument, the Java programs failed with NumberFormatException, and the commands were rewritten with ${=ids} and 'for a b in ...'.

### Why it matters

Every macOS bootcamper runs commands in zsh, so the same retry recurs.

### Suggested fix

Note in ground-rules that the default macOS shell is zsh and give the two portable forms.

### Context when reported

- **Time:** 2026-11-14 16:09 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (arm64)
- **Model / effort:** claude-opus-5-5 / Unknown
- **Context size:** Unknown (the session crossed at least one compaction boundary)
- **Module / step:** graduation / Step 0 (filed at graduation)
- **Recent questions:** Ready for me to stop the visualization server? / Would you like to graduate now and generate your production project and recap?
- **Bootcamper responses:** yes / Yes, graduate
- **Behind the scenes:** graduation skill, Step 0 session retrospective
- **Observed problem:** NumberFormatException on a space-joined argument.
- **Expected behavior:** Arguments split as intended.
- **Divergence:** Bash habits applied in a zsh shell.
