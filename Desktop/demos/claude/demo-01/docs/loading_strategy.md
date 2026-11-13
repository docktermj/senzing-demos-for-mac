# Loading strategy

## Production volume and loader architecture

- Production tier: **medium** (more than 500,000 up to 10,000,000 records; chosen as a range, no count given).
- Architecture: thread-pooled loader sharing one Senzing engine per process, bounded backlog, per-record error log, retry file, throughput reporting (`src/load/load_customer_data.java`, from the MCP `LoadViaFutures` scaffold).
- Concurrency: **1 worker** on the bootcamp's SQLite repository, which does not support concurrent writes. On PostgreSQL (recommended at this scale; see the graduation migration checklist) raise `--threads` toward 2-8 per CPU core and tune to database and storage throughput.

## First-source choice

- **CRM_CONTACTS loaded first** — heuristic 1, *reference before transactional*: it is the customer master list, while WEB_ORDERS and SUPPORT_TICKETS are transaction records (orders, tickets). Heuristic 2 (quality-first) agrees: it has the highest quality score (93.4).

## How to run

```bash
bash -c 'source src/scripts/senzing-env.sh && "$SENZING_JAVA_HOME/bin/javac" -d src/build/classes -cp "$SENZING_SDK_JAR:src/build/classes" src/server/Json.java src/load/*.java'
src/scripts/run-java.sh RegisterDataSources
src/scripts/run-java.sh LoadCustomerData data/senzing-ready/crm_contacts.jsonl
src/scripts/run-java.sh ProcessRedo
```

- `RegisterDataSources` is safe to re-run: it registers only codes the default config lacks.
- `LoadCustomerData` takes one or more JSON Lines files, `--threads N` and `--limit N` (first N records, for test loads). Failed records go to `logs/loading_errors.json` (one JSON line each), retryable ones to `logs/retry-<file>`.
- `ProcessRedo` drains the redo queue and stops when it is empty (batch drain).
- Prerequisites: the SDK environment script `src/scripts/senzing-env.sh`, the shared `src/server/Json.java`, and a backup of `database/G2C.db` (backups go to `backups/`).

## Load log

| Source | Load input | Loaded | Failed | Throughput | Redo after |
|---|---|---|---|---|---|
| CRM_CONTACTS (test) | first 100 | 100 | 0 | 95 rec/s | — |
| CRM_CONTACTS | 4,000 | 4,000 | 0 | 95.9 rec/s (1 worker, SQLite) | 0 processed, queue empty |
| WEB_ORDERS + SUPPORT_TICKETS (orchestrator test) | first 100 each | 100 + 100 | 0 | — | — |
| WEB_ORDERS | 4,500 | 4,500 | 0 | 95.9 rec/s | — |
| SUPPORT_TICKETS | 1,500 | 1,500 | 0 | 96.1 rec/s | 39 processed after both, queue empty |

Every count above is reconciled: loaded equals the load input, and the load input equals the collected `record_count` (`validation_checks.load_count_matches_source: pass` for all three).

## Incremental loading

- **Full reload** (what the bootcamp does): load every record each time. Simple, slow at volume.
- **Incremental** (production pattern): load only records new or changed since the last run, detected by a change timestamp, a sequence number or change data capture in the source system. WEB_ORDERS (`order_date`) and SUPPORT_TICKETS (`opened_on`) carry dates that can drive this; CRM_CONTACTS carries `created_date` but no update timestamp, so changed contacts need CDC or a periodic full reload.
- **Upsert**: adding a record with an existing DATA_SOURCE + RECORD_ID replaces it, and Senzing re-evaluates resolution automatically. The trial load relied on this: the full CRM_CONTACTS load replaced the 100 trial records rather than duplicating them.
- Recommendation for production: incremental loads driven by source change dates, with a continuously running redo processor.

## Final load order and rationale

1. CRM_CONTACTS — reference before transactional (customer master), and the highest quality score.
2. WEB_ORDERS — quality-first among the remaining sources (89.5), and the more attribute-dense (parsed name and address).
3. SUPPORT_TICKETS — lowest quality (75.5), sparse.

No load-order dependencies (generated sources); strategy **Sequential**, run by `src/load/orchestrator.java` (one environment, per-source tallies, retry with backoff, error isolation, per-source reconciliation).

## Cross-source result

2,090 entities span 2 or more sources; pairwise precision 99.9%, recall 95.1% against ground truth (`docs/results_validation.md`).

## Issues and resolutions

- Two over-merges on +NAME+PHONE: same-named synthetic customers whose phones differ by one digit. A data-generation artifact, recorded in `docs/uat_results.md`; no mapping change.
- 136 customers split, 122 of them involving the sparse SUPPORT_TICKETS; most of the pieces are linked as POSSIBLY_SAME on name, so they stay discoverable.

## Recommendations for future loads

- Move to PostgreSQL before production volumes and raise `--threads` (2-8 per CPU core, tuned to the database).
- Run redo as a continuous process in production; keep the batch drain for bulk loads.
- Load incrementally from source change dates; rely on the record-key upsert for changed records.
- Improve SUPPORT_TICKETS capture (phone, full address) to lift cross-source recall.
