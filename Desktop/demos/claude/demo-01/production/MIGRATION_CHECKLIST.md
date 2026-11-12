# Migration Checklist

⚠️ Some production topics (performance, security, monitoring, deployment) are not covered in depth during the bootcamp: complete these items before deploying.

## Database

- [ ] Move from SQLite to PostgreSQL for the planned medium volume (500,000 to 10,000,000 records).
- [ ] Create the Senzing schema and initialize the configuration in the production database.
- [ ] Set up backups and a restore test.

## Security

- [ ] ⚠️ Keep `.env` and license keys out of version control; use a secret store.
- [ ] ⚠️ Restrict database credentials to the loader and query services.
- [ ] ⚠️ Review who can read resolved customer data and the review queue.

## Licensing

- [ ] Obtain a Senzing production license sized for the expected record count.
- [ ] Put it in the engine configuration's `PIPELINE` section (`LICENSESTRINGBASE64` or `LICENSEFILE`).

## Performance

- [ ] ⚠️ Raise `MAX_WORKERS` in `src/load/loader_core.py` and `src/load/process_redo.py` once on PostgreSQL (start at 2-8 per CPU core, then tune).
- [ ] ⚠️ Run redo processing continuously as its own process rather than as a batch drain.
- [ ] ⚠️ Replace `*_DEFAULT_FLAGS` composites in `src/` with the explicit `SZ_*` flags your code actually consumes — except the export call, where `SZ_ENTITY_DEFAULT_FLAGS` is the documented choice and hand-assembling `SZ_ENTITY_INCLUDE_*` members has been observed to drop `RELATED_ENTITIES` entirely, with no error. DEFAULT composites are meant for getting started; their membership may change between Senzing versions, so pinned code can silently change what it returns after an upgrade, and they fetch more than most callers need.
- [ ] ⚠️ Replace full export scans in the query programs with incremental updates for large volumes.

## Data

- [ ] Supply the raw source exports the transforms read (see README, Configuration).
- [ ] Add a shared customer identifier across systems if one exists; it would turn many possible-duplicate pairs into merges.
- [ ] Fix first-name-only BILLING_SYSTEM records at the source; they create noisy possible-duplicate links.
- [ ] `src/load/loader_core.py` names `src/resources/scaffold/loading/add_futures.py`, which this project does not contain (a reference in a comment; check whether anything reads it).
- [ ] `src/load/orchestrator.py` names `docs/loading_strategy.md`, which this project does not contain; check whether it is read, and supply or remove it.
- [ ] `src/query/customers_in_all_sources.py` names `data/query_results/customers_in_all_sources.json`, which this project does not contain; the program creates and writes it, so confirm the output location suits you.
- [ ] `src/query/discover_patterns.py` names `data/query_results/discover_patterns.json`, which this project does not contain; the program creates and writes it.
- [ ] `src/query/review_queue.py` names `data/query_results/review_queue.json`, which this project does not contain; the program creates and writes it.
- [ ] `src/query/unique_customer_counts.py` names `data/query_results/unique_customer_counts.json`, which this project does not contain; the program creates and writes it.
- [ ] `src/transform/transform_billing_system.py` names `docs/mapping/BILLING_SYSTEM_mapper.md`, which this project does not contain; check whether it is read.
- [ ] `src/transform/transform_crm_system.py` names `docs/mapping/CRM_SYSTEM_mapper.md`, which this project does not contain; check whether it is read.
- [ ] `src/transform/transform_support_tickets.py` names `docs/mapping/SUPPORT_TICKETS_mapper.md`, which this project does not contain; check whether it is read.

## Deployment

Target: local or on-premises (not stated).

- [ ] ⚠️ Provision the host with the Senzing runtime and Python.
- [ ] ⚠️ Schedule loads, redo processing and the review-queue report.
- [ ] ⚠️ Set a backup schedule for the database and monitor disk space.
- [ ] ⚠️ Add logging and alerting for load failures (`logs/loading_errors.json`).
