# Loading strategy

## First-source choice

- **First source:** CREDIT_BUREAU
- **Deciding heuristic:** reference data before transactional data (load-order heuristic 1), and among the reference sources the highest quality score (heuristic 2: CREDIT_BUREAU 92.1, BUSINESS_REGISTRY 75.4, WATCHLIST 63.7), for a strong entity baseline.
- Recorded 2026-11-14T10:33:35-05:00.

## Loader

- `src/load/load_source.java` (threaded pattern for the medium production tier; one worker thread on SQLite, raise to 2-8 per CPU core on PostgreSQL).
- `src/load/register_data_sources.java` registers CORE_BANKING, CARD_SERVICES, CREDIT_BUREAU, BUSINESS_REGISTRY, WATCHLIST (re-runnable).
- `src/load/process_redo.java` drains the redo queue and exits.

## How to run

```bash
source src/scripts/senzing-env.sh
javac -cp "$SZ_SDK_JAR" -d data/temp/load_classes src/server/MiniJson.java src/load/*.java
src/scripts/run-java.sh data/temp/load_classes RegisterBankingDataSources      # once, re-runnable
src/scripts/run-java.sh data/temp/load_classes LoadSource data/senzing-ready/<source>.jsonl [--limit N] [--threads N]
src/scripts/run-java.sh data/temp/load_classes ProcessRedoQueue                # after loading; stops when the queue is empty
```

Prerequisites: the Senzing SDK (Homebrew cask), a JDK, `config/engine_config.json` (read by `senzing-env.sh`). No third-party Java libraries: JSON is handled by `src/server/MiniJson.java`. Back up `database/G2C.db` before a load (`backups/G2C_before_data_processing.db` was taken before this module's first load). Failed records go to `logs/loading_errors.json`; retryable ones to `logs/retry_<SOURCE>.jsonl`.

## CREDIT_BUREAU (first source)

- Test load: first 10 records, 10 loaded, 0 errors.
- Full load: 30 attempted, 30 loaded, 0 failed, 0.87 s (about 35 records/s on one SQLite writer).
- Redo: 0 processed; queue reached empty.
- Reconciliation: stage 1, 30 loaded = 30 in `data/senzing-ready/credit_bureau.jsonl`; stage 2, load input 30 = collected `record_count` 30 -> `load_count_matches_source: pass`.

## Incremental loading (production)

- **Full reload** (what this bootcamp does): load every record each time. Simple; slow at production volume.
- **Incremental load:** load only records that are new or changed since the last run, found with a change-detection mechanism (source timestamps, sequence numbers, change data capture).
- **Upsert:** `addRecord` with an existing DATA_SOURCE + RECORD_ID replaces that record and Senzing re-evaluates its entity, so a changed record is simply re-sent.
- **Choice for this project:** full reload for the bootcamp. For the medium-tier production system, incremental loads keyed on each source's RECORD_ID with upsert, a redo processor running continuously alongside, and PostgreSQL instead of SQLite.

## Load-order dependencies

- None, per the bootcamper (2026-11-14). Observed in the data: CORE_BANKING 3050 points to the CREDIT_BUREAU 3048 anchor (already loaded); BUSINESS_REGISTRY's relationships are internal to that source.

## Load order for the remaining sources

1. BUSINESS_REGISTRY (reference data; quality 75.4)
2. WATCHLIST (reference data; quality 63.7)
3. CORE_BANKING (customer data; quality 73.8)
4. CARD_SERVICES (customer data; quality 72.4)

Heuristics in priority order: reference before transactional, then quality-first. CREDIT_BUREAU was already loaded first.

## Final load (2026-11-14)

- **Order and rationale:** CREDIT_BUREAU first (Phase B), then BUSINESS_REGISTRY, WATCHLIST, CORE_BANKING, CARD_SERVICES (reference before transactional, then quality). Strategy: Sequential (bootcamper's choice). No load-order dependencies.
- **Orchestrator:** `src/load/orchestrator.java` runs each source's load in its own JVM (clean per-source counters, error isolation), retries a load that does not finish with backoff (1s, 2s, 4s), and does not retry a load that finished with record-level failures. Tested on 10 records per source (40 of 40) and with a deliberately bad file (malformed JSON line and an unregistered data source, SENZ2207; both logged, failure reported without a crash; self-test log kept at `logs/error_handling_selftest.json`).
- **Per-source statistics (reconciled, stage 1 and stage 2 pass):** CREDIT_BUREAU 30/30, BUSINESS_REGISTRY 22/22, WATCHLIST 17/17, CORE_BANKING 97/97, CARD_SERVICES 82/82; 0 failed; total 248. Throughput on one SQLite writer about 35-45 records/s, including JVM start-up per source.
- **Redo:** 0 after CREDIT_BUREAU, 5 after the other four; queue empty.
- **Cross-source summary:** 63 cross-source entities; largest overlap CARD_SERVICES + CORE_BANKING (38 customers with both a card and a deposit account); 18 disclosed relationships.
- **Issues and resolutions:** orchestrator retried a bad-data failure during its self-test; fixed to retry only unfinished loads.
- **Recommendations for future loads:** shuffle input files at production volume; move to PostgreSQL and raise `--threads` to 2-8 per CPU core; run the redo processor continuously; load incrementally keyed on RECORD_ID.
