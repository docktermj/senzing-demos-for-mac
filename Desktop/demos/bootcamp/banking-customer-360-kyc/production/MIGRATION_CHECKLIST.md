# Migration Checklist

⚠️ Some production topics (performance, security, monitoring, deployment) are not covered in depth during the bootcamp: complete these items before deploying.

## Database

- [ ] Move the repository from SQLite to PostgreSQL for production volumes and concurrent loading; work with your DBA (Senzing's PostgreSQL Setup guide).
- [ ] Create the Senzing schema with `szcore-schema-postgresql-create.sql` and set `DATABASE_URL` to `postgresql://user:password@host:5432:g2`.
- [ ] Run `configuration.InitDefaultConfig` once, then `RegisterBankingDataSources`, on the new repository.
- [ ] ⚠️ Apply Senzing's database tuning guidance (disk IO, network latency, database parameters); during bulk loads consider disabling PostgreSQL `synchronous_commit` and re-enabling it afterwards.
- [ ] ⚠️ Schedule repository backups and test a restore.

## Security

- [ ] ⚠️ Keep `.env` and any license file out of version control (`.gitignore` covers `.env`); load secrets from a secret store in shared environments.
- [ ] ⚠️ Give the database account only the permissions Senzing needs, and restrict who can read the repository: it holds customer PII.
- [ ] ⚠️ Review `docs/results/` and `data/results/` outputs (customer lists, alert queues) as regulated data: access control and retention.

## Licensing

- [ ] Obtain a production Senzing license sized for the bank's real record volume (the built-in evaluation license stops at 500 records).
- [ ] Set `SENZING_LICENSE_FILE` in `.env` (written into the engine configuration as `PIPELINE.LICENSEFILE`), or put `LICENSESTRINGBASE64` in your own `SENZING_ENGINE_CONFIGURATION_JSON`.
- [ ] Record the license expiry date and set a renewal reminder.

## Performance

- [ ] ⚠️ Replace `*_DEFAULT_FLAGS` composites in `src/` with the explicit `SZ_*` flags whose output your code actually consumes — **except the export call**, where `SZ_ENTITY_DEFAULT_FLAGS` is the documented choice and hand-assembling `SZ_ENTITY_INCLUDE_*` members has been observed to drop `RELATED_ENTITIES` entirely, with no error. Why: Senzing states that DEFAULT composites are for getting started and exploration, not production; their membership may change between Senzing versions, so code pinned to one can silently change what it returns after an upgrade with no error raised, and they return more than most callers need, which costs engine work and response size. In this project: `SZ_ENTITY_DEFAULT_FLAGS` in `src/query/ResolvedEntities.java`, and `SZ_FIND_NETWORK_DEFAULT_FLAGS` / `SZ_FIND_PATH_DEFAULT_FLAGS` in `src/query/discover.java`.
- [ ] ⚠️ Raise the loader's thread count (`--threads N`) once on PostgreSQL; keep one thread on SQLite.
- [ ] ⚠️ Shuffle input files before large loads: sorted or grouped input causes lock contention.
- [ ] ⚠️ Run redo processing as a separate, continuous process rather than only after a load.
- [ ] ⚠️ Size hardware with Senzing's hardware sizing and capacity planning guidance before the first full load.
- [ ] ⚠️ `ResolvedEntities` looks up every loaded record one at a time; for the full customer base, switch the Customer 360 and alert builds to the export stream or to incremental updates.

## Data

- [ ] Point the transformers at the real extracts under `data/raw/` and re-run them for each source.
- [ ] Re-run the data-quality checks on real extracts; the evaluation data scored 63.7–92.1 per source.
- [ ] Keep the mapping decisions in step with the source systems (payload fields such as account numbers and list types).
- [ ] Re-validate resolution on a labelled sample of real data before relying on the alert queue.
- [ ] Decide how deletes and updates in the source systems reach Senzing (delete or re-add by `DATA_SOURCE` + `RECORD_ID`).

## Deployment (target: local workstation)

- [ ] ⚠️ Decide where the loader, redo processor and query jobs run beyond this workstation, and schedule them.
- [ ] ⚠️ Build a container image for the Java programs if you use `docker-compose.yml` (start from Senzing's published Dockerfile examples).
- [ ] ⚠️ Add monitoring: load error counts (`logs/loading_errors.json`), redo queue depth, and job failures.
- [ ] ⚠️ Set up CI to compile the programs against the Senzing SDK version you deploy.
