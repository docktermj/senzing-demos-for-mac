# Loading Strategy

## First-source choice

- **Source:** CRM_SYSTEM
- **Deciding heuristic:** reference before transactional. CRM_SYSTEM is the customer system of record; BILLING_SYSTEM and SUPPORT_TICKETS are transactional systems built around those customers.

## Loader

- Programs: `src/load/load_crm_system.py`, `src/load/load_support_tickets.py`, `src/load/load_billing_system.py` (shared engine `src/load/loader_core.py`, from the Senzing threaded loading snippet).
- Production volume tier: medium (range chosen, no exact count). Threaded pattern; one writer on SQLite, 2-8 workers per CPU core to start on PostgreSQL.
- Data source codes registered before loading by `src/load/register_data_sources.py` (default config 2776857741).
- Repository backup before loading: `data/backups/G2C.db.pre-load`.

## Running the loaders (Python)

```bash
source src/scripts/senzing-env.sh
python3 src/load/register_data_sources.py                 # once, before any load (safe to re-run)
python3 src/load/load_crm_system.py data/senzing-ready/crm_system.jsonl            # --limit N for a test load, --resume to continue
python3 src/load/load_support_tickets.py data/senzing-ready/support_tickets.jsonl
python3 src/load/load_billing_system.py data/senzing-ready/billing_system.jsonl
python3 src/load/process_redo.py                          # drain the redo queue after loading
```

Prerequisites: the project environment script (`src/scripts/senzing-env.sh`), the SQLite repository at `database/G2C.db` with a default configuration registered. Per-record failures go to `logs/loading_errors.json`; checkpoints to `logs/load_checkpoint_<DATA_SOURCE>.json`.

## Incremental loading

- **Full reload** (what the bootcamp does): load every record each time. Simple, and fine at 10,000 records.
- **Incremental load**: track new or changed records since the last load and load only the deltas, using timestamps, sequence numbers or change data capture.
- **Upsert**: `add_record` with the same `RECORD_ID` replaces the earlier version and Senzing re-evaluates resolution automatically.

**This project:** all three systems are ongoing (CRM, Billing and Support keep changing; `docs/business_problem.md` says "Ongoing"). At the medium production tier, use incremental loading with upserts keyed on each system's stable ID (`crm_id`, `billing_id`, `ticket_id`), fed by each system's change records, plus a continuously running redo processor. Full reloads remain the recovery path.

## Multi-source orchestration

- **Dependencies:** none; the three sources were generated for the bootcamp.
- **Strategy:** Sequential.
- **Order:** CRM_SYSTEM (first, already loaded), then SUPPORT_TICKETS (quality 95.7), then BILLING_SYSTEM (quality 90.2): quality-first, since neither remaining source is reference data.
- **Program:** `src/load/orchestrator.py` runs each source's loader in its own process (clean per-source counts, error isolation), retries a failed source with 1/2/4/8 s backoff, and reconciles every count against its input.
- **Result:** 10,000 of 10,000 records loaded, 0 failed; redo drained after each phase.

## Results summary

- Final load order: CRM_SYSTEM, SUPPORT_TICKETS, BILLING_SYSTEM (reasons above).
- Per-source statistics (reconciled; loaded = input): CRM_SYSTEM 4,000; SUPPORT_TICKETS 2,000; BILLING_SYSTEM 4,000. 0 failed.
- Cross-source: 2,191 entities span 2+ systems; precision 100.0% and recall 80.9% against the answer key.
- Issues: none. Recommendations: move to PostgreSQL and raise loader workers for production volume; run redo continuously; load incrementally with upserts.
