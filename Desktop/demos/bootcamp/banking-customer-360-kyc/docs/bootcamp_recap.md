# Senzing Bootcamp Recap

**Bootcamper:** Senzing Bootcamper
**Started:** 2026-11-13T17:28:05-05:00
**Completed:** 2026-11-14
**Programming language:** Java
**Path:** Core
**Plugin version:** 0.6.1
**Operating system:** macOS 27.2.1 (arm64)
**Python version:** Python 3.9.6
**Language runtime:** Java (OpenJDK 21.0.13)
**Senzing SDK:** 4.4.4
**Database:** SQLite

---

## Entity Resolution Concepts — 2026-11-13T17:28:05-05:00

### Information Shared

- Entity resolution is determining when different records refer to the same real-world entity, and when they don't.
- Failure modes: false negatives (same entity split) and false positives, including ambiguous matches and invisible false positives (the George Foreman example); a well-designed system holds an ambiguous record as a possible match.
- Conceptual pipeline: ingestion and standardization, candidate selection, comparison and scoring, classification, entity clustering.
- Disclosed relationships (stated in the data) vs. discovered relationships (detected from shared attributes such as address or phone).
- Senzing's approach: principle-based rather than rule-based, real-time and entity-centric, no training or tuning required. (Sourced from Senzing docs via the MCP server.)

### Questions & Responses

- **Q:** Do you have any questions about entity resolution before we continue?
    - **R:** No.
- **Q:** Would you like a few quick questions to help the concepts stick?
    - **R:** Yes.
- **Q:** What should a well-designed system do with a record that fits two existing entities equally well?
    - **R:** 3 — hold it as a possible match until a distinguishing attribute arrives (correct).
- **Q:** What happens when the same SSN starts showing up on many different people?
    - **R:** 3 — Senzing labels it generic and re-evaluates prior records (correct).
- **Q:** Which of these is a discovered relationship?
    - **R:** 2 — two differently named people sharing an address and phone (correct).
- **Q:** Are you ready to move on to the next module: Discover the Business Problem?
    - **R:** Yes.

### Actions Taken

- Presented the entity resolution primer and ran the optional three-question knowledge check; this preamble creates no project files.

### End-of-Module Summary

**What you accomplished:**

- Learned what entity resolution is and how false negatives, false positives and ambiguous matches differ.
- Walked through the conceptual resolution pipeline and the difference between disclosed and discovered relationships.
- Learned how Senzing's principle-based, real-time approach differs from rule-based matching, and answered all three knowledge-check questions correctly.

**Files produced:**

- (no files — conceptual primer)

**Why it matters:** These concepts are the vocabulary for a banking customer 360 and KYC project, where one customer appears across many records and relationships between customers carry real risk.

---

## Discover the Business Problem — 2026-11-14T08:35:24-05:00

### Information Shared

- Data privacy reminder: confirm permission to use the data and anonymize PII for testing.
- Pattern gallery of common problems (Know Your Customer, Customer 360, Fraud Detection, Vendor MDM integration, assets and claims linking) with Senzing's published business-value estimates. (Sourced from Senzing docs via the MCP server.)
- Solution approach: resolve all five sources once and serve both the customer 360 and KYC/KYB from the same resolved entities; Senzing matches records to entities and keeps related entities as relationships.
- Record-count check: 248 records is under the built-in 500-record evaluation limit, so no licence guidance is needed.

### Questions & Responses

- **Q:** Would you like to see examples of common business problems that entity resolution can solve?
    - **R:** Yes.
- **Q:** Do any of these patterns match your situation?
    - **R:** No.
- **Q:** How would you like to define the business problem?
    - **R:** Described a real business case in a scenario document (Customer 360 and KYC/KYB for a retail bank).
- **Q:** Does that summary capture your situation accurately?
    - **R:** Yes.
- **Q:** Do you have any diagrams of your data architecture and flows you'd like to share?
    - **R:** No.
- **Q:** Will your entity-resolution results need to interface with other software?
    - **R:** No.
- **Q:** Where do you plan to deploy the final solution?
    - **R:** 3 — Local / on-premises.
- **Q:** Does this accurately capture your problem and approach?
    - **R:** Yes.

### Actions Taken

- Read the bootcamper's scenario documents and inferred five sources (248 records), both people and organizations, with Customer 360 and KYC/KYB as the use case.
- Wrote `docs/business_problem.md`, `config/data_sources.yaml` and `docs/stakeholder_summary_module1.md`, and filled in the Overview and Business Problem sections of `README.md`.
- Saved the integration and deployment answers to `config/bootcamp_preferences.yaml`.

### End-of-Module Summary

**What you accomplished:**

- Defined a clear business problem: resolve a bank's customers once and serve both a customer 360 and KYC/KYB screening.
- Identified five data sources (248 synthetic records) and the matching criteria that matter most.
- Set success criteria, including the 179 customer records collapsing into real customers and an analyst queue with evidence for every alert.

**Files produced:**

- `docs/business_problem.md` — the confirmed business problem statement
- `config/data_sources.yaml` — the five data sources and their record counts
- `docs/stakeholder_summary_module1.md` — a shareable summary of the problem, approach and next steps
- `README.md` — project overview and business problem

**Why it matters:** A documented problem, sources and success criteria give every later module a target, so loading, resolving and querying the data can be judged against what the bank actually needs.

---

## SDK setup — 2026-11-14T08:42:23-05:00

### Information Shared

- Recommended Opus 5.5 at high reasoning effort for this code-heavy module.
- Senzing SDK 4.4.4 (build 4.4.4.26314) was already installed through the Homebrew cask on macOS Apple Silicon, so installation and the licence agreement were skipped.
- The built-in evaluation licence is active with a 500-record limit (expires 2027-11-11), confirmed again once the engine configuration was in force; the 248-record scenario fits.
- SQLite was chosen as the database: one file inside the project, suited to learning and evaluation. (Sourced from Senzing docs via the MCP server.)

### Questions & Responses

- **Q:** Would you like to switch to Opus 5.5 at high reasoning effort for this module?
    - **R:** Yes.
- **Q:** Are you done modifying the model and effort?
    - **R:** Yes.
- **Q:** Which database would you like to use?
    - **R:** 1 — SQLite.

### Actions Taken

- Wrote src/scripts/senzing-env.sh (project-local environment; no global shell changes) and src/scripts/run-java.sh (sources it, then starts the JDK).
- Found that /usr/bin/java drops DYLD_LIBRARY_PATH on this Mac; the launcher uses the JDK binary directly.
- Fetched the official Senzing snippets GetVersion, GetLicense, InitDefaultConfig and EnginePriming (checksums verified) into src/verify/, compiled and ran them.
- Created database/G2C.db and applied the Senzing SQLite schema; wrote config/engine_config.json with an absolute database path.
- Seeded the default Senzing configuration and primed the engine successfully.
- Update check skipped: the Homebrew lock directory is not writable by this account.

### End-of-Module Summary

**What you accomplished:**

- Verified the existing Senzing SDK 4.4.4 install works from Java on your Mac.
- Set up a project-local environment and a Java launcher that work around the macOS library-path issue.
- Created a SQLite database, engine configuration and default Senzing configuration, and proved the engine starts.

**Files produced:**

- `src/scripts/senzing-env.sh` — project-local Senzing environment script
- `src/scripts/run-java.sh` — launcher that runs Java programs with Senzing available
- `config/engine_config.json` — engine configuration (paths and database connection)
- `config/license.json` — the active licence reading
- `database/G2C.db` — the Senzing SQLite database
- `src/verify/` — the version, licence, config-seeding and engine-test programs

**Why it matters:** Every later module loads and queries your five banking sources through this engine and database, so a proven working setup now removes a whole class of failures later.

---

## System verification — 2026-11-14T08:46:54-05:00

### Information Shared

- This module checks the whole chain end to end with four synthetic test records, never touching real data or the Senzing Truth Set.
- Recommended Sonnet 5.5 at high effort for this module (a step down from Opus 5.5, a cost saving rather than a capability need); the bootcamper switched to Sonnet 5.5.
- The test records were built so the outcome is known in advance: three records for one synthetic person that must resolve into one entity, and one different person that must stay on their own.
- Senzing code came from the official snippets served by the MCP server, with only the JSON-reading lines replaced because the extra JSON library is not shipped with the SDK. (Sourced from Senzing docs via the MCP server.)

### Questions & Responses

- **Q:** Would you like to switch to Sonnet 5.5 at high reasoning effort for this module?
    - **R:** Yes (switched to Sonnet 5.5 before replying).

### Actions Taken

- Checked MCP connectivity and engine start-up; both passed.
- Generated `src/system_verification/verification_data.jsonl` (4 synthetic records, data source VERIFY, expected result 2 entities with one of 3 records).
- Built and compiled five Java programs in `src/system_verification/` from the official snippets: engine init, data source registration, loading, results and database checks, and cleanup.
- Registered the VERIFY data source and loaded 4 of 4 records, none failed.
- Results validation: 2 entities from 4 records, as predicted. The three records for one person resolved to one entity (match key +NAME+DOB+ADDRESS+PHONE) and the other person stayed a singleton.
- Database operations passed: write count (4 of 4), read by entity ID, and search by name and address.
- Purged the VERIFY records through the SDK and confirmed zero remain.

### End-of-Module Summary

**What you accomplished:**

- Verified your Senzing install works end to end on your Mac: code generation, compiling, loading, resolving, reading and searching.
- Confirmed Senzing resolved the test records exactly as designed (2 entities from 4 records), with name variants, address abbreviations and phone formats treated as the same person.
- Cleaned up so the database holds no test data going into the next module.

**Files produced:**

- `src/system_verification/verification_data.jsonl` — the four synthetic test records
- `src/system_verification/verify_init.java` — engine start-up check
- `src/system_verification/register_data_sources.java` — registers the test data source
- `src/system_verification/verify_pipeline.java` — loads the test records
- `src/system_verification/verify_results.java` — results validation and database operations checks
- `src/system_verification/cleanup_verify_data.java` and `verify_purged.java` — remove the test records and confirm
- `src/system_verification/scaffold/` — the unmodified official snippets, kept for reference

**Why it matters:** Your five banking sources will load through this same code path, so a proven end-to-end chain means any problem later is about the data, not the setup.

---

## Truth Set visualization — 2026-11-14T09:10:03-05:00

### Information Shared

- Recommended Opus 5.5 at high effort for this module, because it builds a web server; the bootcamper switched to Opus 5.5.
- The Senzing demo Truth Set is synthetic data with known answers: 159 records across CUSTOMERS (120), REFERENCE (22) and WATCHLIST (17), fetched through the MCP server's download URLs. (Sourced from Senzing docs via the MCP server.)
- Guided tour of the six tabs: Entity Graph, Merge Statistics, Match Keys, Feature Scores, Cross-Source, and Search / Probe. Searching "Robert Smith" returned three separate entities, showing that a common name alone does not merge people.
- Before cleanup, explained what the saved copy keeps (every tab built from embedded data) and loses (live Why?, How? and search).

### Questions & Responses

- **Q:** Would you like to switch to Opus 5.5 for this module?
    - **R:** Yes.
- **Q:** Are you ready to continue?
    - **R:** Yes.
- **Q:** Ready for me to stop the visualization server and clean up the Truth Set data?
    - **R:** Yes.

### Actions Taken

- Downloaded the Truth Set to `src/system_verification/truthset_data.jsonl` and verified 159 of 159 records (per-source counts matched).
- Registered CUSTOMERS, REFERENCE and WATCHLIST; loaded 159 records with 0 failures; processed 7 redo records.
- Built a Java visualization server (`src/server/TruthsetVizServer.java`, `src/server/MiniJson.java`) modeled on the bootcamp reference; its page, brand tokens and vendored D3 are generated into `src/server/assets/` by `src/server/generate_assets.py`. The Java color allocation matched the reference exactly.
- Wrote the standalone snapshot `docs/visualizations/truthset_verification.html` and ran the live app on port 8080. All ten API endpoints verified. Result: 85 entities, 54 multi-record, 17 cross-source, 65 relationships.
- Encoding self-check passed: the source legend showed 4 combination rows, matching 4 combination keys. Deep-linking (`?tab=` and `?q=`) verified, and the snapshot's tab set matched the live app's.
- Screenshots not captured: the capture tool found no headless browser on this machine (exit 2). The HTML snapshot is the retained artifact.
- Stopped the server (port confirmed free), deleted all 159 Truth Set records through the SDK, processed the follow-on redo, and confirmed 0 records remain.

### End-of-Module Summary

**What you accomplished:**

- Watched Senzing resolve the Truth Set on your own machine: 159 records into 85 entities, 17 of them spanning more than one source.
- Explored the result interactively: entity graph, merge statistics, match keys, feature scores, cross-source overlap and live search with Why? and How? explanations.
- Built the visualization server in Java, ready to be pointed at your own banking data later.

**Files produced:**

- `docs/visualizations/truthset_verification.html` — standalone snapshot of the Truth Set visualization (works offline)
- `src/server/TruthsetVizServer.java` — the Java visualization server
- `src/server/MiniJson.java` — dependency-free JSON reader/writer
- `src/server/generate_assets.py` and `src/server/assets/` — page template, brand tokens and vendored D3
- `src/system_verification/truthset_data.jsonl` — the downloaded Truth Set records
- `src/system_verification/register_truthset_sources.java`, `load_truthset.java`, `process_redo.java`, `verify_records_purged.java` — registration, load, redo and purge-check programs

**Why it matters:** You have now seen what entity resolution looks like on data with known answers, and the same visualization server can show your customer 360 and KYC results in Query, Visualize and Discover.

---

## Data collection — 2026-11-14T09:16:40-05:00

### Information Shared

- Recommended Sonnet 5.5 at medium effort for this module (a step down from Opus 5.5 at high effort, a cost saving); the bootcamper switched to Sonnet 5.5.
- The scenario's five files were already in Senzing JSON format, so collecting them meant copying, checking and documenting. Mapping is confirmed in the next module.
- Licence check: the active evaluation licence allows 500 records and the scenario totals 248, so no licence key was needed and no sampling was required.
- SQLite load-time check: Senzing's published guidance recommends SQLite for small, local testing; at 248 records no load-time warning applies. (Sourced from Senzing docs via the MCP server.)

### Questions & Responses

- **Q:** Would you like to switch to Sonnet 5.5 at medium reasoning effort for this module?
    - **R:** Yes (switched to Sonnet 5.5 before replying).
- **Q:** Are you done modifying the model and effort?
    - **R:** Yes.

### Actions Taken

- Copied the five source files from the scenario folder into `data/raw/` through a staging area, validating each before it was moved into place.
- Validated every file: readable UTF-8 without a byte-order mark, valid JSON on every line, a single data source code, unique record IDs, and record counts equal to the stated counts (97, 82, 30, 22, 17 = 248).
- Recorded each source (counts, sizes, checksums, entity types, provenance `own`, validation results) in `config/data_sources.yaml`.
- Wrote `docs/data_source_locations.md`, `docs/data_collection_checklist.md` and `docs/security_compliance.md`, and created `.gitignore` so raw data, the database, licence material and engine configuration stay out of version control.
- Left the answer key (`banking_truthset_key.csv`) in its source folder: it is for validating results later, not data to load.

### End-of-Module Summary

**What you accomplished:**

- Collected all five banking sources (248 records) into your project and confirmed every file is complete and intact.
- Documented where each source came from and what it holds, with a checklist for the next module.
- Confirmed the evaluation licence covers the volume and set up data handling so raw data stays out of version control.

**Files produced:**

- `data/raw/core_cif_extract.jsonl`, `card_cardholder_master.jsonl`, `credit_header.jsonl`, `business_registry.jsonl`, `screening_list.jsonl` — the five source files
- `config/data_sources.yaml` — the source registry with counts, checksums and validation results
- `docs/data_source_locations.md` — where each source came from
- `docs/data_collection_checklist.md` — inventory table and validation checklist
- `docs/security_compliance.md` — data handling notes
- `.gitignore` — keeps raw data, the database and licence material out of version control

**Why it matters:** Your sources are now safely inside the project with checksums and counts on record, so the quality and mapping checks, and later the load, can be compared against exactly what you started with.

---

## Data Quality, Mapping, and Transformation — 2026-11-14T10:27:53-05:00

### Information Shared

- Recommended Opus 5.5 at high effort for this module; bootcamper switched.
- All five sources use Senzing's FEATURES layout; every feature attribute resolves to the Entity Specification. Root business columns (balances, account numbers, list types) still need a mapping disposition.
- Quality method: 0.70 completeness + 0.25 format consistency + 0.05 (100 - duplicate rate), per-record completeness over features applicable to each RECORD_TYPE, identifiers scored as one group.
- Mapping ran through Senzing's mapping_workflow once per source (profile, plan, map, generate and validate), in concise mode; every source already arrived as Senzing JSON, so each transformation routes values verbatim rather than rewriting them, and Senzing's verbatim check confirmed it for all five.
- A root key ending in a reserved attribute name (ACCOUNT_NUMBER) is read as a feature, not payload, so payload columns must avoid those names.
- A whole address in ADDR_LINE1 is acceptable (Senzing's quickstart shows the same form); single-string names stay in NAME_FULL unparsed, including Arabic and Cyrillic names, since Senzing is UTF-8 with cross-script name comparison.
- Disclosed relationships link a REL_POINTER to a REL_ANCHOR by domain and key, so a link can cross sources (CORE_BANKING 3050 points to CREDIT_BUREAU 3048); the analyzer checks one file at a time and accepts such an anchor with an explanation.
- REGISTRATION_DATE is the organization's incorporation date, so BUSINESS_REGISTRY's INCORPORATED year became a matching feature rather than payload.

### Questions & Responses

- **Q:** Would you like to switch to Opus 5.5 at high reasoning effort for this module?
    - **R:** Yes.
- **Q:** Are you done modifying the model and effort?
    - **R:** Yes.
- **Q:** Would you like a visual of the quality assessment (coverage bars and per-field completeness)?
    - **R:** Yes.
- **Q:** WATCHLIST (62.6, <70): Your data quality needs attention before mapping will produce good results. What would you like to do?
    - **R:** 1 — Work on improving the data first.
- **Q:** WATCHLIST (63.7, nothing mechanical left): What would you like to do?
    - **R:** 1 — Return to Data collection for this source.
- **Q:** How would you like to provide the data for this source? (WATCHLIST return)
    - **R:** The data is described by data-location.md in the scenario folder.
- **Q:** WATCHLIST (63.7, unchanged): What would you like to do?
    - **R:** 1 — Return to Data collection for this source (second return).
- **Q:** How would you like to provide the data for this source? (second WATCHLIST return)
    - **R:** The ingredients folder under ~/Desktop/Senzing Demos (the same folder, identical file).
- **Q:** WATCHLIST (63.7, unchanged): What would you like to do?
    - **R:** 1 — Return to Data collection for this source (third return).
- **Q:** How would you like to provide the data for this source? (third WATCHLIST return)
    - **R:** 2 — Provide a URL or file path.
- **Q:** What's the URL or file path of the screening list?
    - **R:** The same ingredients folder (identical file; third return found no new data).
- **Q:** WATCHLIST (63.7, unchanged): What would you like to do?
    - **R:** 1 — Return to Data collection for this source (fourth return).
- **Q:** Do you have a screening list other than the 17-record one in the scenario's ingredients folder?
    - **R:** No.
- **Q:** WATCHLIST (63.7, unchanged): What would you like to do?
    - **R:** 1 — Return to Data collection for this source (fifth return).
- **Q:** How would you like to provide the data for this source? (fifth WATCHLIST return)
    - **R:** The same ingredients folder (identical file).
- **Q:** WATCHLIST (63.7, unchanged): What would you like to do?
    - **R:** 1 — Return to Data collection for this source (sixth return).
- **Q:** How would you like to provide the data for this source? (sixth WATCHLIST return)
    - **R:** The same ingredients folder (identical file).
- **Q:** WATCHLIST (63.7, unchanged): What would you like to do?
    - **R:** 1 — Return to Data collection for this source (seventh return).
- **Q:** How would you like to provide the data for this source? (seventh WATCHLIST return)
    - **R:** The same ingredients folder (identical file).
- **Q:** All of the data I have is in the ingredients folder. What is the problem? (bootcamper question)
    - **R:** Explained: the gate's return option fetches a fuller copy of the source; with no other data it loops, so proceeding is the way forward.
- **Q:** WATCHLIST (63.7): What would you like to do?
    - **R:** 2 — Proceed anyway, knowing the results may be limited.
- **Q:** BUSINESS_REGISTRY, CORE_BANKING, CARD_SERVICES (70-79): What would you like to do?
    - **R:** 2 — Continue to mapping now.
- **Q:** Before we start mapping, which mode would you like?
    - **R:** 2 — Concise mode.
- **Q:** How should CORE_BANKING record 3025 be typed?
    - **R:** 1 — Organization (WONDERLAND primary, PRAVENVGUPTA alias).
- **Q:** Would you like a web page showing the quality analysis (coverage charts and the field mapping summary)? (CORE_BANKING)
    - **R:** Yes.
- **Q:** Quality for CORE_BANKING is acceptable. What would you like to do?
    - **R:** 1 — Move on to the next source, CARD_SERVICES.
- **Q:** Would you like a web page showing the quality analysis? (CARD_SERVICES)
    - **R:** Yes.
- **Q:** Quality for CARD_SERVICES is acceptable. What would you like to do?
    - **R:** 1 — Move on to the next source, WATCHLIST.
- **Q:** Would you like a web page showing the quality analysis? (WATCHLIST)
    - **R:** Yes.
- **Q:** Quality for WATCHLIST needs improvement before loading will produce meaningful results. What would you like to do?
    - **R:** 2 — Move on to CREDIT_BUREAU anyway, knowing results may be limited.
- **Q:** Would you like a web page showing the quality analysis? (CREDIT_BUREAU)
    - **R:** Yes.
- **Q:** Quality looks strong for CREDIT_BUREAU. Ready to map the next source, BUSINESS_REGISTRY?
    - **R:** 1 — Yes, map BUSINESS_REGISTRY.
- **Q:** Would you like a web page showing the quality analysis? (BUSINESS_REGISTRY)
    - **R:** Yes.
- **Q:** Quality is acceptable (BUSINESS_REGISTRY, last source). What would you like to do?
    - **R:** 1 — Proceed to loading now.

### Actions Taken

- Verified the five files unchanged since collection (counts and checksums).
- Saved the Entity Specification to docs/reference/senzing_entity_specification.md (size and hash verified).
- Scored quality with src/scripts/assess_quality.py: CREDIT_BUREAU 92.1, BUSINESS_REGISTRY 75.4, CORE_BANKING 73.8, CARD_SERVICES 72.4, WATCHLIST 62.6. Type/name check: 0 candidates in every source.
- Visual: docs/visualizations/data_quality_assessment.html (bootcamper accepted the offer); screenshot skipped, no headless browser.
- Wrote docs/data_source_evaluation.md with measured cross-source overlaps.
- Improved WATCHLIST: DATE_OF_BIRTH normalized to ISO (7 values, 2-digit years expanded only where unambiguous) into data/raw/screening_list-improved.jsonl; original kept. Score 62.6 -> 63.7. Phones left unchanged (would need an inferred country code).
- Return to Data collection for WATCHLIST: the file named by data-location.md is identical to the collected copy, so no fuller export exists; re-check at Step 4 found no shape change and the score stays 63.7.
- Phase 1 complete: all five sources Entity Specification-compliant; gate decisions recorded in config/data_sources.yaml and docs/data_source_evaluation.md.
- CORE_BANKING mapping_workflow steps 1-3 done (profile 97 records/45 fields; plan PERSON master on RECORD_ID with per-record RECORD_TYPE; 34 features, 8 payload, 3 ignored). Shared Java transformer src/transform/SenzingJsonPassThrough.java + transform_core_banking.java: verbatim check passes. Analyzer findings fixed: ACCOUNT_NUMBER payload renamed DEPOSIT_ACCOUNT_ID (a root key ending in a reserved attribute is read as a feature); qualifier-only PHONE_TYPE objects dropped. Record 3025 re-typed ORGANIZATION per bootcamper. Workflow step 4 approved (97 records, analyzer clean).
- docs/visualizations/mapping_core_banking_quality.html written and checked in the browser (screenshot skipped: no headless browser).
- CORE_BANKING complete: data/senzing-ready/core_banking.jsonl (97), docs/mapping/CORE_BANKING_mapper.md, mapping_core_banking.md, transformation_lineage_core_banking.md; sandbox test load skipped (more sources to map).
- CARD_SERVICES mapping_workflow steps 1-4 approved: 82 records, 35 feature / 9 payload / 3 ignored, analyzer clean first pass, verbatim passes; transform_card_services.java.
- docs/visualizations/mapping_card_services_quality.html written and checked (NPI coverage bar added after the first render missed it).
- CARD_SERVICES complete: data/senzing-ready/card_services.jsonl (82) and its mapper, transformation and lineage docs.
- WATCHLIST mapping_workflow steps 1-4 approved: 17 records, 19 feature / 3 payload / 3 ignored, analyzer clean first pass, verbatim passes; transform_watchlist.java reads the DOB-normalized file.
- docs/visualizations/mapping_watchlist_quality.html written and checked in the browser.
- WATCHLIST complete: data/senzing-ready/watchlist.jsonl (17) and its mapper, transformation and lineage docs.
- CREDIT_BUREAU mapping_workflow steps 1-4 approved: 30 records, 17 feature / 2 payload / 3 ignored, verbatim passes; analyzer's anchor-without-pointer error accepted with explanation (pointer is CORE_BANKING 3050); transform_credit_bureau.java.
- docs/visualizations/mapping_credit_bureau_quality.html written and checked in the browser.
- CREDIT_BUREAU complete: data/senzing-ready/credit_bureau.jsonl (30) and its mapper, transformation and lineage docs.
- BUSINESS_REGISTRY mapping_workflow steps 1-4 approved: 22 records (PERSON 16, ORGANIZATION 6), 31 feature / 2 payload / 3 ignored; INCORPORATED mapped to REGISTRATION_DATE via a new root-to-feature option in the shared transformer (earlier four outputs regenerate byte-identical); analyzer clean, verbatim passes.
- docs/visualizations/mapping_business_registry_quality.html written and checked in the browser (REGISTRATION_DATE coverage bar added to the page renderer).
- BUSINESS_REGISTRY complete: data/senzing-ready/business_registry.jsonl (22) and its mapper, transformation and lineage docs; optional sandbox test load skipped for every source.
- Completion gate: all five files in data/senzing-ready/ (248 records) have docs/mapping/{SOURCE}_mapper.md; every source's mapping_status is complete and no mapping-state checkpoint remains.

### End-of-Module Summary

**What you accomplished:**

- Scored all five sources for quality: CREDIT_BUREAU 92.1, BUSINESS_REGISTRY 75.4, CORE_BANKING 73.8, CARD_SERVICES 72.4, WATCHLIST 63.7 (after normalizing its dates of birth).
- Mapped every source with Senzing's mapping workflow and built a Java transformation program for each, all sharing one pass-through engine.
- Produced 248 load-ready records whose values are all verbatim from the source, with record types, aliases, identifiers and ownership links intact.
- Settled two data questions along the way: CORE_BANKING record 3025 typed as an organization, and the incorporation year used as a registration date.

**Files produced:**

- `data/senzing-ready/core_banking.jsonl` — 97 load-ready records
- `data/senzing-ready/card_services.jsonl` — 82 load-ready records
- `data/senzing-ready/watchlist.jsonl` — 17 load-ready records
- `data/senzing-ready/credit_bureau.jsonl` — 30 load-ready records
- `data/senzing-ready/business_registry.jsonl` — 22 load-ready records
- `src/transform/` — the five transformation programs and the shared SenzingJsonPassThrough.java
- `docs/mapping/` — per-source mapping specification, transformation notes, lineage, analyzer report, profile and journal
- `docs/data_source_evaluation.md` — quality scores, gate decisions and measured cross-source overlaps
- `docs/visualizations/data_quality_assessment.html` — quality assessment page
- `docs/visualizations/mapping_*_quality.html` — five mapping quality pages, one per source

**Why it matters:** Your Customer 360 and KYC questions depend on Senzing seeing the same people and companies across all five systems; every source now speaks Senzing's format with nothing reworded, so the next module can load them and let entity resolution find those connections.

---

## Data processing — 2026-11-14T10:40:24-05:00

### Information Shared

- Recommended Opus 5.5 at high effort, which matches what is in force (no switch needed).
- None of the five sources was test-loaded during Data Quality, Mapping, and Transformation, so a brief test load is owed before the full load (Phase B).
- Senzing's loading anti-patterns: shuffle input rather than loading it sorted; load with a thread pool (2-8 workers per core, tuned to the database); SQLite is for development only, does not support concurrent writes, and suits datasets under about 100K records; always drain the redo queue, looping on get_redo_record rather than count_redo_records.
- The production-volume answer selects the loader's architecture: the medium tier gets Senzing's threaded LoadViaFutures pattern; the worker count follows the database, so SQLite (no concurrent writes) runs one writer, with 2-8 per CPU core as the PostgreSQL starting point.
- Senzing's redo snippet is a streaming processor that waits forever on an empty queue; a batch load needs a drain that stops when getRedoRecord returns nothing.
- Validation followed Senzing's evaluation guidance: one export pass with SZ_EXPORT_DEFAULT_FLAGS for statistics, relationships deduplicated by entity pair, evidence from specific entities, plus a howEntity check of every multi-record entity.
- Match-key suppressors mean opposite things by bucket: on a merged record, a disagreeing feature joined anyway; on a relationship, Senzing declined to merge because the feature disagreed.

### Questions & Responses

- **Q:** In production, how many records do you expect to load?
    - **R:** 3 — More than 500,000, up to 10,000,000 (medium production).
- **Q:** Are there load-order dependencies between your data sources?
    - **R:** No.
- **Q:** Which loading strategy would you like?
    - **R:** 1 — Sequential.

### Actions Taken

- Pre-load checks: test-load status read from config/data_sources.yaml (none tested); own data, so no CORD freshness reminder; anti-pattern guidance read.
- Recorded production_volume {tier: medium, raw_value: null} in config/bootcamp_preferences.yaml.
- Built src/load/load_source.java from Senzing's LoadViaFutures snippet (threaded pattern; JSON via MiniJson instead of javax.json; one writer thread because SQLite does not support concurrent writes), src/load/register_data_sources.java (RegisterDataSources snippet, re-runnable) and src/load/process_redo.java (RedoContinuous snippet adapted to stop on an empty queue). Compiled with no warnings.
- Backed up database/G2C.db to backups/G2C_before_data_processing.db; registered CORE_BANKING, CARD_SERVICES, CREDIT_BUREAU, BUSINESS_REGISTRY (WATCHLIST already present); re-run registered nothing.
- SQLite volume check: 248 loadable records, far below Senzing's ~100K SQLite guidance, so no prompt.
- First source CREDIT_BUREAU (reference before transactional; highest quality among reference sources): test load 10/10, full load 30/30 with 0 errors, redo 0, reconciliation pass. docs/loading_strategy.md written (first-source choice, how to run, incremental strategy).
- No load-order dependencies recorded; remaining order BUSINESS_REGISTRY, WATCHLIST, CORE_BANKING, CARD_SERVICES (reference before transactional, then quality) in docs/loading_strategy.md.
- Orchestrator src/load/orchestrator.java: each source in its own JVM, retry with backoff only for unfinished loads (fixed after its self-test retried a bad-data failure), per-source reconciliation and health summaries. Test: 10 records per source, 40/40; error self-test logged SENZ2207 and a malformed line without crashing.
- Full run: BUSINESS_REGISTRY 22/22, WATCHLIST 17/17, CORE_BANKING 97/97, CARD_SERVICES 82/82, 0 failed; redo 5 processed, queue empty. All five sources reconciled (stage 1 and stage 2 pass) in config/data_sources.yaml.
- Validation: src/validate/export_entities.java (export + howEntity) and src/validate/evaluate_results.py. 248 records -> 157 entities, 63 cross-source, 140 relationships (18 disclosed). Against the scenario's answer key: 147 of 156 entities exact, pair precision 97.8%, recall 92.3%. Match-key audit: per-record buckets clean, relationship bucket -DOB 50.7% (finding). How-state audit: 69 of 69 checked, 0 unsettled.
- Wrote docs/uat_results.md (self-directed UAT, 9 of 10 tests pass, the tenth 90.5%), docs/results_validation.md, docs/stakeholder_summary_module6.md and the final-load section of docs/loading_strategy.md. Decision gate: results strong.

### End-of-Module Summary

**What you accomplished:**

- Loaded all five sources into Senzing: 248 records, 0 failed, every source reconciled against its file and its collected count.
- Built a take-home loading toolkit in Java from Senzing's code: a threaded loader, data-source registration, a redo drain and a multi-source orchestrator with retries and error isolation.
- Validated the results against the scenario's answer key: 94.2% of real-world entities exact, 97.8% precision, 92.3% recall, with every difference explained.
- Surfaced the first Customer 360 and KYC findings: 127 customers behind 179 records, 38 with both a deposit account and a card, and 9 screening-list entries linked to customers or business parties.

**Files produced:**

- `src/load/load_source.java` — threaded loader (one writer on SQLite)
- `src/load/register_data_sources.java` — re-runnable data-source registration
- `src/load/process_redo.java` — redo drain that stops on an empty queue
- `src/load/orchestrator.java` — sequential multi-source orchestrator
- `src/validate/export_entities.java` and `src/validate/evaluate_results.py` — export, construction-history check and evaluation
- `docs/loading_strategy.md` — load order, how to run, statistics, incremental strategy
- `docs/results_validation.md` — entity statistics, accuracy, match-key and how-state audits
- `docs/uat_results.md` — self-directed UAT with evidence for every difference
- `docs/stakeholder_summary_module6.md` — one-page summary

**Why it matters:** The bank's five systems are now resolved once, in one place, which is exactly what both the relationship managers' Customer 360 and the compliance team's KYC/KYB screening need to draw on.

---

## Query, Visualize and Discover — 2026-11-14T11:11:40-05:00

### Information Shared

- Why analysis: whyRecords compares two records, whyEntities two entities; WHY_KEY / WHY_ERRULE_CODE / WHY_KEY_DETAILS are the why-side names, with FEATURE_SCORES giving each feature's score and bucket (SAME, CLOSE, PLAUSIBLE, NO_CHANCE).
- Matching concepts revisited on this data: features and match keys (+ contributed, - detracted), possible matches as near-misses held apart, cross-source entities.
- Recommended Opus 5.5 at high effort, which matches what is in force (no switch needed).
- Ten query requirements derived from the success criteria and desired output in docs/business_problem.md.
- How analysis: howEntity replays an entity's construction step by step (MATCH_KEY, ERRULE_CODE and MATCH_KEY_DETAILS on the how side; VIRTUAL_ENTITY_1 / VIRTUAL_ENTITY_2 per step) and FINAL_STATE says whether the history is settled.
- Relationship networks: findNetwork and findPath; declared relationships come from the data, discovered ones from shared features; degrees of separation count the hops.
- Senzing cautions that *_DEFAULT_FLAGS composites suit exploration; production code should request exactly the flags it uses, because a composite can change on upgrade without any error.

### Questions & Responses

- **Q:** Is there anything you'd like to adjust? (ten derived query requirements)
    - **R:** No.
- **Q:** Would you like an interactive visualization of your resolved data — entity graph with its relationship view, merge statistics, cross-source overlap, and match/feature analysis, all in one app?
    - **R:** Yes.
- **Q:** Would you like to explore Senzing's advanced discover capabilities using examples from your own data?
    - **R:** Yes.
- **Q:** What would you like to do next? (after Why Analysis)
    - **R:** 1 — Continue to How Analysis.
- **Q:** What would you like to do next? (after How Analysis)
    - **R:** 1 — Continue to Relationship Networks.
- **Q:** Would you like to continue to module completion now?
    - **R:** Yes.
- **Q:** Ready for me to stop the visualization server?
    - **R:** Yes.

### Actions Taken

- Read docs/business_problem.md and derived the query requirements.
- Query programs (Java, from Senzing's getEntity pattern; flags SZ_ENTITY_DEFAULT_FLAGS plus SZ_ENTITY_INCLUDE_RELATED_RECORD_DATA, response paths from response_schemas): src/query/ResolvedEntities.java (shared entity view, owns the environment), customer_360.java, kyc_alerts.java.
- Customer 360: 179 records -> 127 customers; 38 with both, 50 deposits only, 39 card only; duplicates inside CORE_BANKING 7, CARD_SERVICES 4. Outputs data/results/customer_360.json, docs/results/customer_master_list.csv.
- KYC/KYB queue: 36 alerts (3 Prohibited, 9 High, 24 Medium): 9 screening hits, 9 possible screening hits, 16 identity verification, 2 beneficial ownership (Mullenkranz Prohibited; Universal Exports Worldwide High with 3 subsidiaries rolled up). Outputs data/results/kyc_alerts.json, docs/results/kyc_alert_queue.md.
- Quality (reporting_guide quality + evaluation): entity/record ratio 0.633, possible matches 33.8% of entities (Poor band), cross-source 40.1%. Finding, not mapping-actionable: no dominant possible-match key (+NAME 7, +TAX_ID 6, +SSN-DOB 5 of 36), 34 of 36 pairs are different entities per the answer key; the three largest entities (7, 6, 5 records) each match one answer-key entity.
- Visualization: extended src/server/TruthsetVizServer.java with --from-export (model built from exportJsonEntityReport with SZ_EXPORT_INCLUDE_ALL_ENTITIES + SZ_ENTITY_DEFAULT_FLAGS, merged as Java Set composites). Live app on 127.0.0.1:8081 (pid 9682; identity confirmed by nonce); model 248 records, 157 entities, 69 merged, 63 cross-source, 140 relationships. All six tabs render; encoding self-check passed and exercised (10 combination rows = 10 combination keys); live Why/How answer. Snapshot docs/visualizations/results_visualization.html (offline, D3 inlined, labelled with the five banking sources). Screenshots skipped: capture helper exit 2, no headless browser.
- Discover (src/query/discover.java). Patterns: 11 entities with 3+ records, all 11 cross-source; 122 entities with relationships. Why (whyRecords / whyEntities with SZ_INCLUDE_FEATURE_SCORES, SZ_ENTITY_INCLUDE_ENTITY_NAME, SZ_INCLUDE_MATCH_KEY_DETAILS + SZ_ENTITY_INCLUDE_ALL_RELATIONS; WHY_KEY_DETAILS returned): WATCHLIST/1024 vs CORE_BANKING/1022 resolved on +NAME+DOB (name 98 CLOSE 'Mhd Antoun' vs 'Mohamed Antoun', DOB 100 SAME across formats, rule SNAME_SSTAB); CREDIT_BUREAU/3015 vs CORE_BANKING/3016 resolved on +NAME+DOB+SSN with the SSN scored 90 CLOSE despite transposed digits and the address 15 NO_CHANCE (the answer key separates them); entity 600001 vs 500001 POSSIBLY_SAME on +NAME+ADDRESS-DOB (DOB 59 NO_CHANCE kept them apart). Raw responses in data/results/discover/.
- How (howEntity with the same flag set; MATCH_KEY_DETAILS returned) on entity 500010 Maria Luis Medina Sentosa, 7 records, 6 steps: two core banking records joined on +PNAME+DOB+ADDRESS+EMAIL; the two screening-list entries joined each other; a card and a core record joined on +NAME+DOB+ADDRESS; those customer groups merged despite a day/month swap in DOB (96 CLOSE); a second card record joined on name, DOB, address, phone and email; finally the screening-list pair joined the customer on +PNAME+DOB+EMAIL (DOB 95 CLOSE across formats). FINAL_STATE NEED_REEVALUATION 0, one virtual entity (settled). Raw response data/results/discover/how_entity_500010.json.
- Networks (findNetwork with SZ_FIND_NETWORK_DEFAULT_FLAGS, links read from ENTITY_NETWORK_LINKS[] by MIN/MAX_ENTITY_ID; findPath with SZ_FIND_PATH_DEFAULT_FLAGS, links from ENTITY_PATH_LINKS[]): network around 400006 and 400008 returned two separate ownership groups (Mullenkranz with its sanctioned owner and sanctioned president, linked also by a shared employer; Universal Exports Worldwide with two PEP owners, its USA parent link and Singapore and India subsidiaries), no path between them within 3 degrees. Hub method found no 2+ degree pair among the top three hubs' neighbours (one fully connected cluster); find_path shown on Howard Hughes -> Universal Exports Worldwide -> Universal Exports India (2 degrees: OWNS 50%, GLOBAL PARENT). Hub cluster 300013: 8 people born in the 1930s sharing one address, 28 discovered links (+ADDRESS with -DOB), including two Margaret Ellis records held apart as POSSIBLY_SAME. Discover phase completed.
- Data discoveries: src/validate/write_discoveries.py wrote docs/bootcamp_data_discoveries.md (headline numbers, 69 merges with match keys, 27 cross-source review pairs, why/how worked examples with a near-miss, relationship networks, what was not found with its measurement); docs/bootcamp_data_discoveries.pdf rendered (5 pages) and its extracted text checked for every section and key finding. Non-Latin names written with the Latin-script names the same entities' own records carry.
- Query Completeness Gate: programs run, visualization offered and built, Discover completed, discoveries produced.
- Stopped the visualization server by its recorded pid (9682) and confirmed port 8081 free; also stopped the earlier static file server on port 8765. Loaded data left in place. No quality iterations were run (module_7_query.quality_iterations is empty).

### End-of-Module Summary

**What you accomplished:**

- Turned your business problem into ten query requirements and built Java query programs that answer them from Senzing's resolved entities.
- Produced the Customer 360: 127 customers from 179 records, 38 holding both a deposit account and a card, with duplicates, cross-sell gaps and household links.
- Produced the KYC/KYB alert queue: 36 alerts with evidence, including a Prohibited business owned by a sanctioned customer and a PEP-owned group with its subsidiaries rolled up.
- Explored the results in an interactive six-tab visualization and toured why, how and relationship networks on your own entities.

**Files produced:**

- `src/query/ResolvedEntities.java` — shared entity view (getEntity per record)
- `src/query/customer_360.java` — Customer 360 program
- `src/query/kyc_alerts.java` — KYC/KYB alert queue program
- `src/query/discover.java` — patterns, why, how, network and path demonstrations
- `docs/results/customer_master_list.csv` and `data/results/customer_360.json` — one profile per customer
- `docs/results/kyc_alert_queue.md` and `data/results/kyc_alerts.json` — the alert queue with evidence
- `docs/visualizations/results_visualization.html` — saved interactive visualization (offline)
- `src/server/TruthsetVizServer.java` — visualization server, now with an export-stream build for your data
- `docs/bootcamp_data_discoveries.md` and `docs/bootcamp_data_discoveries.pdf` — what Senzing found in your data
- `data/results/discover/` — raw why, how, network and path responses

**Why it matters:** Relationship managers and compliance analysts now draw on the same single resolution: one profile per customer for the 360, and screening, identity and ownership alerts that come with the evidence an analyst or auditor needs.

---
