# Senzing Bootcamp Plugin Feedback

Feedback captured during the Senzing Bootcamp. Every entry is saved here, whatever it turns
out to be about. Entries routed `mcp-server` may **also** have been forwarded to Senzing —
only ever with your explicit yes, and with identifying details stripped; each entry's
`Upstream:` field records what happened.

**Started:** 2026-11-09

## Your Feedback

## Improvement: Suppress internal turn-check messages

**Date:** 2026-11-09
**Module:** Data collection
**Priority:** High
**Source:** bootcamper-reported
**Routing:** host — the internal turn-check message appears to come from the conversation harness, outside the bootcamp plugin and Senzing MCP server.
**Upstream:** not applicable

### What happened

An internal bootcamp turn-check message was shown to the Bootcamper. They asked for messages like that to be suppressed.

### Why it matters

The message is confusing to the Bootcamper.

### Suggested fix

Do not show messages that are not important to the Bootcamper.

### Context when reported

- **Time:** 2026-11-09 18:41 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS, Apple Silicon (arm64); OS version unavailable
- **Model / effort:** Unknown
- **Context size:** Unknown
- **Module / step:** `data_collection` / `null`; completed modules: `entity_resolution_concepts`, `business_problem`, `sdk_setup`, `system_verification`, `truthset_visualization`, `data_collection`
- **Recent questions:** "Are you ready to move on to the next module: Data Quality, Mapping, and Transformation?"; "Why does it matter to you?"; "What fix would you suggest for messages like that?"; "What priority would you give this? Reply with a number:"
- **Bootcamper responses:** "It is confusing to the Bootcamper."; "just don't show messages that are not important to the Bootcamper."; "High"
- **Behind the scenes:** Module 4 Step 9 had completed and presented its transition question. The feedback workflow was entered to capture the report; no Senzing MCP behavior was involved.
- **Observed problem:** The Bootcamper saw internal turn-check instructions in the conversation.
- **Expected behavior:** Internal control instructions should not be displayed as Bootcamper-facing messages; only the module's prescribed transition question should be visible.
- **Divergence:** The internal message was surfaced by the conversation interface/harness rather than produced by the module's Bootcamper-facing content.

## Improvement: Automatically recover missing MCP tools without bootcamper troubleshooting

**Date:** 2026-11-09
**Module:** Data Quality, Mapping, and Transformation
**Priority:** High
**Source:** bootcamper-reported
**Routing:** host — the configured server answered direct MCP requests successfully while this conversation exposed no tools; repairing the app-managed connection requires host support. The plugin also needs a bounded recovery protocol rather than repetitive catalog searches.
**Upstream:** not applicable

### What happened

The bootcamper reported: "The Senzing MCP Server connectivity issue may occur during the Bootcamp. Devise a way to automatically correct the problem so that the Bootcamper won't experience what I just did."

During Module 5, the conversation initially exposed 13 server tools, then a smaller subset, then none. The guide could no longer discover `mapping_workflow`, `get_capabilities`, or `submit_feedback`. Repeated requests to diagnose and fix the issue resulted in essentially identical discovery retries and blocked responses instead of a recovery.

A subsequent direct test against the endpoint in the installed plugin's MCP configuration succeeded: initialization returned HTTP 200, the initialized notification returned HTTP 202, the tool listing returned HTTP 200 with all 13 tools, and `get_capabilities` returned HTTP 200 without an MCP error. The server identified itself as `sz-mcp-coworker` 1.46.0. Conversation-level discovery still returned zero tools after the bootcamper reported restarting the app.

### Why it matters

The bootcamper wants future participants protected from the repeated interruptions, troubleshooting requests, and loss of momentum experienced here. A healthy server should not leave a bootcamp blocked because the host lost its tool catalog.

### Suggested fix

The bootcamper requested automatic correction. Proposed maintainer design (not implemented by this feedback entry):

1. Add a reusable, bounded recovery guard around required MCP operations. Trigger on a previously available required tool disappearing, an empty catalog, or a transport/session failure. Classify authentication, transport, tool-discovery, and tool-result failures separately.
2. Before recovery, persist the exact bootcamp checkpoint, current source, pending question and answer state, preferences, and any opaque mapping workflow state. Do not mark an unstarted mapping as in progress. Do not repeat answered questions, regenerate source data, or replay mutating requests blindly.
3. Perform one discovery refresh and a capabilities check when callable. If the catalog is empty, test the configured endpoint with an isolated MCP initialize/tools-list/capabilities diagnostic using only protocol metadata. Read the actual configured endpoint; do not hard-code it or send records, credentials, or diagnostic logs. Honor existing authentication and network policy; do not bypass authentication. A successful direct probe diagnoses server health, not restoration of the host connection.
4. Implement a supported host-level reconnect operation that disposes only this session's failed MCP client, reconnects the configured server, refreshes the tool catalog, and atomically rebinds tools to the running conversation. Make it single-flight and idempotent so concurrent checks cannot race or duplicate clients. The plugin should call this supported operation when available, never private app endpoints, database edits, process termination, or plugin file modifications.
5. Retry transient recovery at most twice with a short backoff and an overall deadline. Stop repeated identical searches once they produce no new evidence. Authentication failures require the appropriate sign-in UI; a healthy endpoint with no host reconnect capability requires one precise host-recovery instruction, not a claim of server downtime or an endless retry loop.
6. Require proof of recovery through the conversation's normal tool path: rediscover `get_capabilities`, call it successfully, and confirm the next required tool is exposed. Then resume the saved step silently. A direct HTTP success alone must never be reported as a repaired conversation.
7. Preserve a minimal local diagnostic trail: failure classification, tool counts, reconnect attempts, protocol/HTTP outcomes, timestamps, and recovery result. Keep sensitive settings and session tokens out of logs. Any external report requires explicit consent under the feedback workflow.
8. Add regression coverage for catalogs shrinking from 13 tools to a subset to zero; a healthy endpoint with a stale host binding; failed transport; authentication expiration; unavailable reconnect support; repeated failure after reconnect; concurrent recovery requests; and interruption during a mapping workflow. Verify checkpoint and opaque-state preservation, no duplicated mutations or questions, bounded attempts, and successful normal-path tool invocation before resuming.

The host reconnect capability is a prerequisite for fully automatic repair of this failure class. Plugin guidance can improve diagnosis and fallback behavior but cannot guarantee reattachment of tools through instructions alone.

### Context when reported

- **Time:** 2026-11-09 22:19:31 -05:00 (feedback received)
- **Plugin version:** 0.6.1 (provided by the running plugin; no manifest lookup performed)
- **Workstation:** macOS, arm64; OS version Unknown
- **Model / effort:** Auto mode; concrete model and reasoning effort Unknown
- **Context size:** Unknown
- **Module / step:** `data_quality_mapping` / `8`; completed modules: `entity_resolution_concepts`, `business_problem`, `sdk_setup`, `system_verification`, `truthset_visualization`, `data_collection`
- **Recent questions:** "Before we start mapping, which mode would you like?"; "Which data source would you like to map first?"; confirmation of the exact proposed connectivity report; request to restart the app and check the connection; feedback priority question.
- **Bootcamper responses:** Concise mode; BANK_CUSTOMERS; confirmed the exact report if the feedback tool became available; "I restarted GitHub Copilot; check the connection"; High.
- **Behind the scenes:** Module 5 Phase 2 was paused before starting BANK_CUSTOMERS mapping. `mapping_verbosity: concise` was saved; source mapping remained pending. The guide repeatedly searched the deferred tool catalog before inspecting the configured plugin endpoint and local app logs. No repository MCP configuration was found; the installed plugin defined the server. Direct protocol diagnostics then succeeded. The feedback hook requested this local workflow; no new module work was started.
- **Observed problem:** Zero tools discoverable in the conversation despite a working configured server endpoint; repetitive unsuccessful recovery guidance required bootcamper involvement.
- **Expected behavior:** Required MCP grounding must be maintained; checkpointed progress must survive interruptions; available recovery should be automatic and bounded, with verification and a precise explanation when host intervention is required.
- **Divergence:** The guide initially treated repeated catalog discovery as sufficient troubleshooting. Direct testing later distinguished server health from conversation tool exposure. App logs contained successful client initialization and cancellation events near session/configuration refreshes, but some cancellations belonged to temporary MCP status sessions; they do not prove the cause of this conversation's failure. The exact host defect remains Unknown. No supported host reconnect operation was exposed to this conversation.
- **Resume point:** Module 5 step 8, BANK_CUSTOMERS first, concise mode. No unanswered bootcamp choice remains; mapping is blocked on restoration of the normal tool path. This feedback entry changes neither progress nor source files.

## Improvement: Include the visualization URL at the stop-server gate

**Date:** 2026-11-10
**Module:** Query, Visualize and Discover
**Priority:** High
**Source:** bootcamper-reported
**Routing:** plugin — Module 7's visualization handoff instructions require giving the running app URL, but it was omitted before the stop-server question.
**Upstream:** not applicable

### What happened

The bootcamper said, "You didn't tell me the URL of the visualization server. Tell the Bootcamper the URL of the visualization server." The server was running on port 8080, but its URL had not been provided before the teardown question. The URL was then verified at `http://127.0.0.1:8080/` (HTTP 200) and given to the bootcamper.

### Why it matters

Without the address, the bootcamper could not open the interactive visualization. After receiving it, the bootcamper said, "I now see how I can use Senzing."

### Suggested fix

Give the verified visualization URL to the bootcamper before asking whether to stop the server, and leave the server running until they answer.

### Context when reported

- **Time:** 2026-11-10 11:10 local
- **Plugin version:** 0.6.1
- **Workstation:** macOS; OS version and architecture Unknown
- **Model / effort:** Auto mode; concrete model and reasoning effort Unknown
- **Context size:** Unknown
- **Module / step:** `query_visualize_discover` / `4d`; completed modules: `entity_resolution_concepts`, `business_problem`, `sdk_setup`, `system_verification`, `truthset_visualization`, `data_collection`, `data_quality_mapping`, `data_processing`
- **Recent questions:** "Ready for me to stop the visualization server?"; "What impact did that have for you?"; "What priority would you give this feedback? Reply with a number:"
- **Bootcamper responses:** "You didn't tell me the URL of the visualization server. Tell the Bootcamper the URL of the visualization server."; "I now see how I can use Senzing."; "1. High"
- **Behind the scenes:** Module 7's Discover phase and Query Completeness Gate were complete. The visualization handoff/teardown gate was active; checkpoint `module_7_query.m7_visualizations` recorded port 8080 and PID 75357. The running process and HTTP 200 response were verified. The stop decision remains pending; no server process was stopped.
- **Observed problem:** The stop-server prompt omitted the visualization URL, so the bootcamper asked for it.
- **Expected behavior:** Module 7 Phase 1 says to hand the running visualization to the bootcamper with its `http://localhost:<port>` URL and to leave it running. The teardown question should follow that handoff, with the server stopped only after consent.
- **Divergence:** The URL was available from the saved port and running server, but the assistant omitted it when asking the teardown question. This was an execution omission against the plugin's handoff guidance, not an MCP-server problem.

## Improvement: Use the specified completeness method in quality scoring

**Date:** 2026-11-10
**Module:** Data Quality, Mapping, and Transformation
**Priority:** High
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the quality assessment initially averaged feature-family coverage instead of applying the module's specified per-record applicable-field completeness method.
**Upstream:** not applicable

### What happened

While reviewing the Module 5 artifacts, I found that the initial quality scoring used an average of feature-family coverage rather than the completeness calculation specified for the assessment. I corrected the method during the module and documented the earlier BUSINESS_REGISTRY score of 72.4 as not directly comparable to the later assessment.

### Why it matters

Quality scores inform source-readiness decisions. A mismatch between the specified method and the calculation can make those decisions unreliable, even when later reports correctly document the correction.

### Suggested fix

Implement and test the stated per-record applicability and presence rules in the quality-assessment path. When recalculating a score, clearly supersede earlier results and avoid comparing values produced by different methods or different source versions.

### Context when reported

- **Time:** 2026-11-10 11:28 EST
- **Plugin version:** 0.6.1
- **Workstation:** macOS, arm64; OS version unavailable
- **Model / effort:** Auto mode; concrete model and reasoning effort Unknown
- **Context size:** Unknown
- **Module / step:** `query_visualize_discover` / `null`; graduation retrospective
- **Recent questions:** "Would you like to switch to Opus 5.5 at high reasoning effort for graduation?"
- **Bootcamper responses:** "No, keep my current model and effort."
- **Behind the scenes:** Graduation Step 0 compared the Module 5 recap and source-evaluation report against the feedback file. The prior score-method correction was not already recorded as self-observed feedback.
- **Observed problem:** The initial completeness calculation did not use the assessment's specified per-record applicable-field method.
- **Expected behavior:** Quality assessment should apply the method stated in its instructions consistently before scores guide source-readiness decisions.
- **Divergence:** The first scoring pass used an average of feature-family coverage; the method was corrected later in the module, and the earlier BUSINESS_REGISTRY result was retained as not directly comparable.

## Improvement: Align production checklist guidance with current SDK references

**Date:** 2026-11-10
**Module:** Bootcamp graduation
**Priority:** Medium
**Source:** self-observed (assistant retrospective)
**Routing:** plugin — the graduation checklist instructions carried an inaccurate export-flags exception and did not clearly identify SQLite as development/test-only.
**Upstream:** not applicable

### What happened

While reviewing the generated migration checklist, I found that its export guidance treated
`SZ_ENTITY_DEFAULT_FLAGS` as an exception even though the current SDK reference warns against
default-flag composites in production and identifies `SZ_EXPORT_DEFAULT_FLAGS` as the export
composite containing `SZ_ENTITY_DEFAULT_FLAGS`. The checklist also only asked readers to review
SQLite against operational requirements, rather than stating the current SDK guidance that SQLite
is for development and testing, not production.

### Why it matters

The checklist is part of the production handoff. Ambiguous or contradictory guidance could lead a
team to retain an exploratory flag composite or use the SQLite starter configuration in production.

### Suggested fix

Generate checklist guidance from the current SDK references: make the export composite subject to
the same production review as other default composites, and explicitly direct teams to choose a
production database backend instead of SQLite.

### Context when reported

- **Time:** 2026-11-10 (local time unavailable)
- **Plugin version:** 0.6.1
- **Workstation:** macOS, arm64; OS version unavailable
- **Model / effort:** Auto mode; concrete model and reasoning effort Unknown
- **Context size:** Unknown
- **Module / step:** `query_visualize_discover` / `null`; graduation retrospective
- **Recent questions:** "Would you like to switch to Opus 5.5 at high reasoning effort for graduation?"
- **Bootcamper responses:** "No, keep my current model and effort."
- **Behind the scenes:** Graduation retrospective review found a mismatch between the migration checklist wording and current Senzing MCP SDK references for export flags and database guidance. The affected project documentation was corrected.
- **Observed problem:** The generated production checklist contained contradictory export-flag advice and did not clearly state SQLite's development/test-only status.
- **Expected behavior:** Production handoff guidance should reflect current SDK references and clearly distinguish development configuration from production requirements.
- **Divergence:** The graduation instructions prescribed an export-flag exception that conflicted with the current SDK reference, while the SQLite migration item was only indirect.
