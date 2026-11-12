# Production Migration Checklist

⚠️ Some production topics (performance, security, monitoring, deployment) are not covered in depth
during the bootcamp: complete these items before deploying.

## Database

- [ ] Confirm the database backend and connection settings for the intended workload.
- [ ] Establish tested backup, restore, retention, and recovery procedures.
- [ ] Keep database files and credentials out of source control.
- [ ] Replace the SQLite development/test configuration with a production database backend. Senzing's
  current SDK guidance says SQLite is for development and testing, not production; evaluate
  PostgreSQL, MySQL, or Microsoft SQL Server for the intended workload.
- [ ] Establish operational concurrency, capacity, backup, and recovery requirements for the
  selected database.

## Security

- [ ] ⚠️ Review access controls, network exposure, and host hardening.
- [ ] ⚠️ Store secrets and license material in an approved secret-management system.
- [ ] ⚠️ Define access logging, audit retention, and incident-response ownership.

## Licensing

- [ ] Obtain and configure a production license for the intended deployment.
- [ ] Confirm the effective license configuration and renewal or expiry process.
- [ ] Do not commit license files, encoded keys, or credentials.

## Performance

- [ ] ⚠️ Review every `*_DEFAULT_FLAGS` composite in `production/src/` and replace it with explicit
  `SZ_*` flags after confirming the fields every downstream consumer requires. The current
  `production/src/query/QueryResults.java` export uses `SZ_EXPORT_DEFAULT_FLAGS`, whose current
  composite includes `SZ_EXPORT_INCLUDE_ALL_ENTITIES` and `SZ_ENTITY_DEFAULT_FLAGS`. The SDK
  guidance says default composites are for getting started and exploration; their membership may
  change between versions and they can request more data than needed. Test any change against the
  complete output consumed by the application.
- [ ] ⚠️ Load-test with representative production volume and concurrency.
- [ ] ⚠️ Define resource limits, throughput targets, and operational monitoring.

## Data

- [ ] Replace the evaluation JSONL inputs with approved production data.
- [ ] Verify source identifiers, formats, and registry paths before loading.
- [ ] Define retention, privacy, and data-quality review procedures.
- [ ] Validate the resolved output with authoritative test cases before relying on it.

## Deployment — local / on-premises

- [ ] ⚠️ Provision and patch the production host and its Java / SDK runtime.
- [ ] ⚠️ Define protected storage, database backup scheduling, and restore ownership.
- [ ] ⚠️ Document the deployment procedure, rollback plan, and service monitoring.
