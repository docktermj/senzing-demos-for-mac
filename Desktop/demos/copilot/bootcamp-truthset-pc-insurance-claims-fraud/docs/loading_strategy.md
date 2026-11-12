# Loading Strategy

## First-source choice

- **Source:** VENDOR_REGISTRY
- **Reason:** Load reference sources before transactional sources; among the reference sources, VENDOR_REGISTRY has the higher quality score (57.0% versus 56.5% for SIU_WATCHLIST), establishing the strongest available baseline.
- **Input:** `data/senzing-ready/VENDOR_REGISTRY.jsonl` (25 mapped records)
- **Registered data-source code:** `VENDOR_REGISTRY`

## Cross-source dependencies and load order

- **Dependencies:** None, as confirmed by the bootcamper.
- **Proposed order:**
  1. **VENDOR_REGISTRY** — already loaded first as reference data, with the highest quality score among the reference sources.
  2. **SIU_WATCHLIST** — load the remaining reference source before transactional data.
  3. **POLICYHOLDERS** — transactional policy and claims records, after the reference sources.
- **Orchestration strategy:** Sequential, selected by the bootcamper.

## Phase C source budget

The measured license record limit is 500. Before the sample test, the SDK export reported 25 repository records, leaving 475. After the test, it reports 45, leaving 455; the 137 remaining mapped records still fit, so the budget is unchanged and both sources load in full. No per-source subset is needed.

| Source | Fill order | Limit |
|---|---:|---|
| SIU_WATCHLIST | 2 | Full (17 records) |
| POLICYHOLDERS | 3 | Full (120 records) |

## Production incremental-loading strategy

- **Selected strategy:** Incremental loads. Track source changes and load only new or changed records; the change-detection mechanism (such as timestamps, sequence numbers, or CDC) must be selected for each production feed.
- Senzing identifies records by `DATA_SOURCE` plus the source's unique `RECORD_ID`; sending the same key replaces the existing record. Keep these identifiers stable across refreshes. See [Data Source Records (DSRs) Explained](https://senzing.zendesk.com/hc/en-us/articles/115002897308-Data-Source-Records-DSRs-Explained).
- The current load was the initial bulk load. Production change detection remains a deployment requirement.

## Loading programs

- Compile from the repository root with `source src/scripts/senzing-env.sh`, then `javac -Xlint:all -cp "${SENZING_ROOT}/sdk/java/sz-sdk.jar" -d target/classes src/transform/JsonSupport.java src/load/LoadRecords.java src/load/RegisterDataSources.java src/load/ProcessRedo.java src/load/CountRepositoryRecords.java src/load/Orchestrator.java`.
- Register source codes with `src/scripts/run-java.sh configuration.RegisterDataSources`.
- Load a source with `src/scripts/run-java.sh LoadRecords <jsonl-path>`; use `--limit N` for a bounded test load and `--resume` to continue from that source's verified checkpoint.
- Drain redo records with `src/scripts/run-java.sh redo.ProcessRedo`.
- Count repository records through the SDK with `src/scripts/run-java.sh CountRepositoryRecords`.
- Test multi-source orchestration with `src/scripts/run-java.sh Orchestrator --limit 10 CODE=data/senzing-ready/CODE.jsonl ...`; run the complete sources by omitting `--limit`.
- The sequential orchestrator launches one loader process per source, makes a database backup before each source, keeps per-attempt logs and error details, and writes `logs/orchestration_summary.json`. Retryable-only failures and process failures receive exponential backoff (1, 2, 4, then 8 seconds), with five attempts by default.
- The loader uses the project-local `JsonSupport` parser instead of JSON-P, which is not included in the installed Java runtime; Senzing SDK calls remain from the MCP scaffold.
- SQLite writes are serialized to one worker. For a PostgreSQL deployment, tune the worker pool using the MCP guidance and database/storage throughput.

The multi-source orchestrator sample test loaded 10 records from each remaining source (20 total), with zero failures, zero retryable records, and empty per-source error logs.

The full sequential orchestration then loaded 17/17 SIU_WATCHLIST records and 120/120 POLICYHOLDERS records, with no failures or retryable records. Both stage-1 and stage-2 reconciliation passed, per-source error logs are empty, and the SDK repository count is 162.

After both sources loaded, the coordinated redo drain processed 5 records with no failures or retries and reached an empty queue.

The VENDOR_REGISTRY full load attempted and loaded 25 of 25 mapped records with no failures. The 22 collected records map to 25 load records because the mapping specification emits three deduplicated embedded employer organizations; see `docs/mapping/VENDOR_REGISTRY_mapping_spec.json`. Redo processing completed 54 records and stopped when the queue fetch returned empty.

## Validation outcome

The SDK-backed export reconciled 162 records to 90 entities, including 15 cross-source entities and 70 unique relationships. The cross-source sample reviewed all 15 entities; eight had corroborating evidence and seven need human follow-up. The entity and relationship match-key audit and how-state results, along with the sampling limitations, are documented in `docs/results_validation.md` and `docs/uat_results.md`.

The cross-source per-record match-key bucket had no suppressors; one of 47 single-source per-record comparisons had a GENDER suppressor. Fifteen of 70 deduplicated relationships had suppressors, mostly DOB. This relationship-level finding is distinct from records merged despite a conflict and does not establish poor match accuracy. The how-state audit checked all 52 multi-record entities with none unsettled.

The seven cross-source review cases are listed in `docs/uat_results.md`. Absolute accuracy, precision, recall, and F1 remain unmeasured because there are no adjudicated labels. For future production, retain the chosen incremental strategy, define a reliable source change-detection mechanism, review flagged cases before operational use, and obtain labeled outcomes if accuracy metrics are required.
