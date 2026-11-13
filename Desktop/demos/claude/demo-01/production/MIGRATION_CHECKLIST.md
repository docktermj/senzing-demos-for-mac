# Migration checklist

⚠️ Some production topics (performance, security, monitoring, deployment) are not covered in depth during the bootcamp: complete these items before deploying.

## Database

- [ ] Move the repository from SQLite to PostgreSQL (SQLite does not support concurrent writes and is for development and small datasets only).
- [ ] Apply the Senzing PostgreSQL schema before first use, then register the default configuration and the three data source codes (`RegisterDataSources`).
- [ ] Update the `SQL.CONNECTION` value in the engine configuration to the PostgreSQL connection string.
- [ ] Schedule database backups and test a restore.
- [ ] ⚠️ Disable synchronous commit during bulk loads and re-enable it afterwards.

## Security

- [ ] ⚠️ Keep the engine configuration, database password and license out of source control; supply them from a secret store into `SENZING_ENGINE_CONFIGURATION_JSON`.
- [ ] ⚠️ Restrict file permissions on the license file and any local `config/engine_config.json`.
- [ ] ⚠️ Treat `data/senzing-ready/`, `data/results/` and logs as personal data: access control, retention and encryption at rest.

## Licensing

- [ ] Obtain a production Senzing license sized for your record volume (the bootcamp ran on an evaluation license).
- [ ] Add it to `PIPELINE` as `LICENSEFILE` (a `.lic` path) or `LICENSESTRINGBASE64`, and confirm the record limit with `SzProduct.getLicense()` after deployment.
- [ ] Track the license expiry date.

## Performance

- [ ] ⚠️ Replace `*_DEFAULT_FLAGS` composites in `src/` with the explicit `SZ_*` flags whose output your code actually consumes — **except the export call**, where `SZ_ENTITY_DEFAULT_FLAGS` is the documented choice and hand-assembling `SZ_ENTITY_INCLUDE_*` members has been observed to drop `RELATED_ENTITIES` entirely, with no error. Why: Senzing describes DEFAULT composites as intended for getting started and exploration; their membership may change between Senzing versions, so pinned code can silently change what it returns after an upgrade with no error, and they over-fetch, costing engine work and response size. The query programs were written in exploration style, so this applies to them directly.
- [ ] ⚠️ Raise the loader's `--threads` on PostgreSQL (start at 2–8 workers per CPU core) and measure throughput.
- [ ] ⚠️ Shuffle input files that arrive sorted by name, address or postal code before loading (sorted input causes lock contention).
- [ ] ⚠️ Run Senzing's repository performance check on the production database and storage before the first large load.
- [ ] ⚠️ Run redo as a continuous process alongside incremental loads (the bootcamp drained it as a batch).

## Data

- [ ] Supply the raw exports under `data/raw/` and re-run the mappers whenever the source formats change.
- [ ] Switch from full reloads to incremental loads driven by source change dates (order and ticket dates; CRM contacts need change data capture or periodic full reloads).
- [ ] Capture email or phone and full addresses on support tickets: the sparse ticket records are where most missed matches came from.
- [ ] Review the cross-system "possibly the same" queue (name-only links) as a data-stewardship task.

## Deployment

Target: local / on-premises (single host).

- [ ] ⚠️ Provision the host: Senzing SDK runtime, JDK 21+, PostgreSQL client libraries.
- [ ] ⚠️ Build an image from Senzing's official runtime base image if you deploy with containers, and replace the placeholder image in `docker-compose.yml`.
- [ ] ⚠️ Add monitoring for load errors (`logs/loading_errors.json`), redo queue depth and database health.
- [ ] ⚠️ Set up CI to run `src/scripts/build.sh` and a small sample load on every change.
