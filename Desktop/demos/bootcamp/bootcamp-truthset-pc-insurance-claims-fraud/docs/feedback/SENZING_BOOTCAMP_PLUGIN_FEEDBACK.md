# Senzing Bootcamp Plugin Feedback

Feedback captured during the Senzing Bootcamp. Every entry is saved here, whatever it turns
out to be about. Entries routed `mcp-server` may **also** have been forwarded to Senzing —
only ever with your explicit yes, and with identifying details stripped; each entry's
`Upstream:` field records what happened.

**Started:** 2026-11-11

## Your Feedback

## Improvement: Internal turn-check text is visible

**Date:** 2026-11-11
**Module:** General
**Priority:** High
**Source:** bootcamper-reported
**Routing:** host — The internal turn-check was surfaced as a visible chat message; message visibility is controlled by the Copilot interface/harness, not Senzing MCP.
**Upstream:** not applicable

### What happened

The bootcamper reported that an internal bootcamp turn-check instruction appeared in the conversation and asked that it not be shown. The assistant then ended silently without repeating that text.

### Why it matters

It is confusing to the user. It is internal information; it is not information that helps with the Bootcamp journey.

### Suggested fix

Keep internal turn-check text hidden from the bootcamper.

### Context when reported

- **Time:** 2026-11-11 10:19 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (Darwin, arm64; OS version unavailable)
- **Model / effort:** Auto-selected model / effort unknown
- **Context size:** Unknown
- **Module / step:** No module recorded / no step recorded (`config/bootcamp_progress.json` was `{}`)
- **Recent questions:** "Do you have any questions before we get started?"; "Why does it matter to you?"; "What priority would you give this? Reply with a number:"
- **Bootcamper responses:** No answer to the bootcamp readiness question before reporting feedback; the text is confusing and does not help the bootcamp journey; priority 1 (High).
- **Behind the scenes:** Fresh onboarding scaffold was complete and the preface was waiting for questions/readiness. The bootcamper reported that an internal turn-check appeared in the conversation; the assistant followed it by ending silently. Bootcamp feedback was then captured before returning to the pending preface question.
- **Observed problem:** The bootcamper saw internal turn-check text in the conversation.
- **Expected behavior:** Internal turn-check instructions should remain hidden; the assistant should not expose them to the bootcamper.
- **Divergence:** The check arrived in the conversation as a visible user message, which points to the Copilot interface/harness rather than the bootcamp content or Senzing MCP server.

## Improvement: Internal turn-check should be hidden or collapsed

**Date:** 2026-11-11
**Module:** General
**Priority:** High
**Source:** bootcamper-reported
**Routing:** host — The internal turn-check was surfaced as a visible chat message; hiding or collapsing that message is controlled by the Copilot interface/harness, not Senzing MCP.
**Upstream:** not applicable

### What happened

The bootcamper reported another example of internal turn-check text appearing in the conversation. They asked that it not be shown, and if it must be shown, that it be collapsed after printing.

### Why it matters

It is confusing to the user. It is internal information; it is not information that helps with the Bootcamp journey.

### Suggested fix

Do not show internal turn-check text to the bootcamper; if it must appear, collapse it after printing.

### Context when reported

- **Time:** 2026-11-11 10:28 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS (Darwin, arm64; OS version unavailable)
- **Model / effort:** Auto-selected model / effort unknown
- **Context size:** Unknown
- **Module / step:** No module recorded / no step recorded (`config/bootcamp_progress.json` was `{}`)
- **Recent questions:** "Which bootcamp would you like? Reply with a number:"; "What priority would you give this? Reply with a number:"
- **Bootcamper responses:** No answer to the bootcamp-path question before reporting feedback; priority 1 (High).
- **Behind the scenes:** Bootcamp preparation was waiting for the bootcamp-path selection. The bootcamper reported a further visible internal turn-check example; feedback capture gathered its priority before returning to the pending path question.
- **Observed problem:** The bootcamper saw internal turn-check text in the conversation and requested that it be hidden, or collapsed if it must be shown.
- **Expected behavior:** Internal turn-check instructions should remain hidden from the bootcamper; if unavoidable, they should be collapsed.
- **Divergence:** The check arrived in the conversation as a visible user message, so controlling its visibility or collapse behavior lies with the Copilot interface/harness rather than the bootcamp content or Senzing MCP server.

## Improvement: Add a final source-backed normalization review

**Date:** 2026-11-12
**Module:** Data Quality, Mapping, and Transformation
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — A later mapping-review iteration identified a safe, source-supported formatting correction after the initial assessment had concluded no further safe normalization was available; this is a bootcamp review-workflow gap.
**Upstream:** not applicable

### What happened

The initial mapping-quality pass recorded no further safe formatting changes. A later user-requested iteration found one source-supported email-format correction that did not alter the underlying value; the improved copy was corrected and rescored.

### Why it matters

A bootcamper who accepts the first review may retain an avoidable formatting inconsistency and a lower source-quality score. This can recur when the workflow marks a source complete after a single pass.

### Suggested fix

Add a structured final pass over accepted mappings to check for source-supported formatting variants before declaring that no further safe normalization is available.

### Context when reported

- **Time:** 2026-11-12 11:22 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS 26.6.2 (arm64)
- **Model / effort:** Auto-selected model / effort unknown
- **Context size:** Unknown
- **Module / step:** Graduation / Step 0 (session retrospective)
- **Recent questions:** "Would you like to switch models for graduation?"; "What name would you like printed on your Certificate of Completion?"
- **Bootcamper responses:** No model switch; certificate name "Senzing Bootcamper."
- **Behind the scenes:** Graduation pre-checks and Step 0; reviewed completed mapping artifacts, the current evaluation, and existing feedback before PDF work.
- **Observed problem:** A source-supported formatting correction was discovered only during a later iteration, after the first pass had recorded no further safe corrections.
- **Expected behavior:** Mapping completion should follow a repeatable final review that surfaces safe source-supported corrections before the source is recorded as fully reviewed.
- **Divergence:** The first pass missed a safe correction that a later review found.

## Improvement: Select a compatible Python interpreter before mapping

**Date:** 2026-11-12
**Module:** Data Quality, Mapping, and Transformation
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — Multiple mapping-stage prerequisites used the default `python3` on macOS, where it was below the documented minimum despite a compatible versioned interpreter being available; interpreter selection is a bootcamp workflow issue, not an MCP-server issue.
**Upstream:** not applicable

### What happened

Across several source-mapping steps, `python3` resolved to Python 3.9.6 and failed the Python 3.10+ prerequisite. A compatible Python 3.12 executable was available, but had to be found and selected after those failures.

### Why it matters

Repeated prerequisite failures interrupt the workflow and can make a bootcamper think the required runtime is unavailable when a compatible version is already installed.

### Suggested fix

At the first Python prerequisite check, probe the default and versioned Python executables, select a compatible interpreter once, and give the supported setup path before invoking dependent scripts if none is available.

### Context when reported

- **Time:** 2026-11-12 11:22 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS 26.6.2 (arm64)
- **Model / effort:** Auto-selected model / effort unknown
- **Context size:** Unknown
- **Module / step:** Graduation / Step 0 (session retrospective)
- **Recent questions:** "Would you like to switch models for graduation?"; "What name would you like printed on your Certificate of Completion?"
- **Bootcamper responses:** No model switch; certificate name "Senzing Bootcamper."
- **Behind the scenes:** Graduation pre-checks and Step 0; reviewed session checkpoints and mapping-stage prerequisite outcomes.
- **Observed problem:** Several mapping steps failed their runtime check using the default Python 3.9.6 before switching to Python 3.12.
- **Expected behavior:** A step requiring Python 3.10 or later should select a compatible interpreter before starting dependent work.
- **Divergence:** The workflow tried the incompatible default interpreter repeatedly even though a compatible versioned executable was available.
