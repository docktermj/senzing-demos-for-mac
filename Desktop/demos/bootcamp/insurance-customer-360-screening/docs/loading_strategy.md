# Loading Strategy

## First-source choice

- **Source:** CREDIT_BUREAU
- **Deciding heuristic:** reference before transactional (CREDIT_BUREAU, BUSINESS_REGISTRY and SANCTIONS_PEP are
  reference sources; the policy, annuity and investigation systems are transactional), then quality-first among the
  reference sources: CREDIT_BUREAU scored 89.8, the strongest baseline.
- Recorded 2026-11-14T14:56:29-05:00.

## Loader

- `src/load/SenzingLoader.java` (shared logic) and `src/load/load_source.java` (one source per run), from
  `sdk_guide(topic='load', language='java')` → `LoadViaFutures.java`, with JSON handled by `src/server/Json.java`.
- Production tier: medium (range chosen, no count). Thread-pool pattern with a bounded backlog, checkpoint/resume and
  throughput reporting; one worker thread because the datastore is SQLite (no concurrent writes). Raise `--threads`
  on PostgreSQL.
- Redo: `src/load/RedoDrainer.java`, a batch drain adapted from the streaming `RedoContinuousViaFutures.java`.
- Data source codes registered first by `src/load/register_data_sources.java` (config 2956483729).

## How to run

From the project root (the environment script and JDK are handled by `src/scripts/sz-java.sh`):

```bash
src/scripts/sz-java.sh compile src/server/Json.java src/load/*.java
src/scripts/sz-java.sh run load.RegisterDataSources          # once per new source code; safe to re-run
src/scripts/sz-java.sh run load.LoadSource CREDIT_BUREAU data/senzing-ready/credit_bureau.jsonl --shuffle
src/scripts/sz-java.sh run load.RedoDrainer                  # after loading; stops when the queue is empty
```

Options: `--limit N` (first N records, e.g. a test load), `--threads N` (keep 1 on SQLite), `--shuffle`
(in-memory; shuffle large files upstream instead), `--resume` (skip lines already finished, from
`logs/<file>.checkpoint`). Prerequisites: the Senzing SDK (Homebrew), a JDK, and `SENZING_ENGINE_CONFIGURATION_JSON`
set by `src/scripts/senzing-env.sh`. Failed records: `logs/loading_errors.json`; retryable ones:
`logs/retry_<source>.jsonl`.

## First source loaded

| Source | Test load | Full load | Failed | Throughput | Redo |
|---|---|---|---|---|---|
| CREDIT_BUREAU | 10 of 10 | 30 of 30 | 0 | 95 records/s | 0 processed, queue empty |

Reconciliation: 30 loaded = 30 records in `data/senzing-ready/credit_bureau.jsonl` = the 30 collected (`pass`).

## Incremental loading (production)

- **Full reload** (what the bootcamp does): load every record each time. Simple; slow at volume.
- **Incremental load** (the production pattern): load only records that are new or changed since the last run,
  detected by timestamps, sequence numbers or change data capture in each source system.
- **Upsert:** adding a record with the same DATA_SOURCE and RECORD_ID replaces it, and Senzing re-resolves the
  affected entities, so an incremental feed needs no separate update call. Deletions need an explicit delete.
- **Choice for this project:** full reload for the bootcamp; incremental upserts per source in production, with the
  redo processor running continuously alongside.

## Dependencies

- None. The bootcamper confirmed there are no load-order dependencies between the six sources (2026-11-14T15:03:59-05:00). Cross-source
  relationship pointers (PC_POLICY_ADMIN 3050 → CREDIT_BUREAU 3048, LIFE_ANNUITY_ADMIN 1313 → PC_POLICY_ADMIN 1312) do
  not impose an order: a pointer resolves to its anchor whenever both records are loaded.

## Load order (remaining sources)

CREDIT_BUREAU is already loaded (chosen by reference-before-transactional, then quality-first). Remaining order, by
the same heuristics (reference before transactional, then quality-first):

1. SANCTIONS_PEP — reference (watchlist), quality 71.9
2. BUSINESS_REGISTRY — reference (registry), quality 67.8
3. PC_POLICY_ADMIN — transactional, quality 66.8
4. LIFE_ANNUITY_ADMIN — transactional, quality 62.6
5. SIU_CASES — transactional, quality 62.1

## Strategy and orchestration

- Strategy: **sequential** (bootcamper's choice). Orchestrator: `src/load/orchestrator.java` — one Senzing environment,
  sources in the order above, per-source error isolation, retry with backoff (1s, 2s, 4s, 8s) on retryable Senzing
  errors, per-source counters, reconciliation, a health line after each source, and one redo drain at the end.
- Pre-load checklist passed: input files present and non-empty, RECORD_IDs unique, codes registered, backup taken,
  disk space ample. License capacity (500) measured against the repository through the export: 30 records before the
  test run, 74 before the full run; every remaining source loaded in full.
- Test run (10 records per source): 44 of 44, 0 failed, counts reconciled per source.

## Per-source load results (reconciled)

| Source | Load input | Loaded | Failed | Collected | Reconciliation | Throughput |
|---|---|---|---|---|---|---|
| CREDIT_BUREAU | 30 | 30 | 0 | 30 | pass | 95 records/s |
| SANCTIONS_PEP | 4 | 4 | 0 | 4 | pass | — (under 0.01 s) |
| BUSINESS_REGISTRY | 22 | 22 | 0 | 22 | pass | 102 records/s |
| PC_POLICY_ADMIN | 104 | 104 | 0 | 104 | pass | 98 records/s |
| LIFE_ANNUITY_ADMIN | 75 | 75 | 0 | 75 | pass | 99 records/s |
| SIU_CASES | 13 | 13 | 0 | 13 | pass | 118 records/s |
| **Total** | **248** | **248** | **0** | **248** | per-source sums agree | |

Repository after the run: 248 records (measured through the export). Redo after all sources: 5 processed, 0 failed,
queue empty. Error log `logs/loading_errors.json`: empty.

## Cross-source results (baseline)

157 entities from 248 records; 63 cross-source entities; 140 relationships. Details: `docs/results_validation.md`.

## Issues and resolutions

- The loader snippet's JSON library (javax.json) is not in Java SE or the Senzing install: replaced with the project's
  dependency-free reader; Senzing calls unchanged.
- The redo snippet never terminates (streaming pattern): adapted to stop when the queue is empty.
- No load errors, retries or license limits were hit.

## Recommendations for future loads

- Move to PostgreSQL for production volumes (medium tier), then raise `--threads` and consider parallel sources.
- Run redo as a separate always-running process; keep the batch drainer for one-off loads.
- Load incrementally (upserts by DATA_SOURCE + RECORD_ID) from change feeds in each source system.
- Shuffle large inputs upstream before loading.
