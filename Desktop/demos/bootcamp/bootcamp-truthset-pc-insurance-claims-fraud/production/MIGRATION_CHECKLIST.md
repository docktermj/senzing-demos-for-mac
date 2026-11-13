# Migration Checklist

⚠️ Some production topics (performance, security, monitoring, deployment) are not covered in depth during the bootcamp: complete these items before deploying.

## Database

- [ ] ⚠️ Replace SQLite with a production database backend and update the engine configuration. Senzing guidance limits SQLite to development and testing.
- [ ] Define and test backup, restore, retention, and recovery procedures.
- [ ] Confirm the database path and connection settings used by the engine match the deployment environment.

## Security

- [ ] ⚠️ Store engine settings and license material in an approved secret manager; do not commit `.env` or license files.
- [ ] ⚠️ Review filesystem permissions, network exposure, and access controls for the database and application host.
- [ ] ⚠️ Define log retention and redact sensitive record values from operational logs.

## Licensing

- [ ] Obtain and validate a license sized for the expected production workload.
- [ ] Supply the license through `PIPELINE.LICENSEFILE` or `PIPELINE.LICENSESTRINGBASE64` in the engine configuration, and keep it out of source control.
- [ ] Document license renewal and expiration monitoring.

## Performance

- [ ] ⚠️ Replace `*_DEFAULT_FLAGS` composites in `production/src/` with the explicit `SZ_*` flags whose output your code actually consumes—**except the export call**, where `SZ_ENTITY_DEFAULT_FLAGS` is the documented choice and hand-assembling `SZ_ENTITY_INCLUDE_*` members has been observed to drop `RELATED_ENTITIES` entirely, with no error. Default composites are intended for exploration, may change membership between SDK versions, can silently change returned data after an upgrade, and may over-fetch data at additional engine and response-size cost.
- [ ] ⚠️ Benchmark representative production volumes and tune loading, query, and storage resources.
- [ ] ⚠️ Define a source change-detection strategy and incremental-load schedule.

## Data

- [ ] Confirm the three production source paths, formats, and data-source codes with each data owner.
- [ ] Remove or provision the four test-only registry entries that refer to excluded `src/system_verification/` files.
- [ ] Validate source quality, record identifiers, mappings, and human-review procedures before operational use.
- [ ] Define retention, refresh, and disposition rules for source data and resolved results.

## Deployment

- [ ] ⚠️ Target: local / on-premises. Provision and harden the host, Java runtime, Senzing SDK, storage, and network access.
- [ ] ⚠️ Configure an image registry and CI/CD pipeline, then test deployment and rollback.
- [ ] ⚠️ Establish monitoring, alerting, and an on-call process for load failures and database health.
