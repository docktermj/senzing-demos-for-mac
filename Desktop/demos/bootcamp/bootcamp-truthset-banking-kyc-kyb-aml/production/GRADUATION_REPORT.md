# Graduation Report

**Completed:** 2026-11-10T12:00:16-05:00
**Path:** Core
**Programming language:** Java
**Database:** SQLite
**Deployment target:** Local / on-premises

## Modules Completed

- Entity Resolution Concepts
- Discover the Business Problem
- SDK setup
- System verification
- Truth Set visualization
- Data collection
- Data Quality, Mapping, and Transformation
- Data processing
- Query, Visualize and Discover

## Files Generated

| Path | Purpose |
|---|---|
| `production/config/data_sources.yaml` | Minimal production registry projection |
| `production/.env.example` | Environment and configuration placeholders |
| `production/docker-compose.yml` | Local Java workspace and SQLite volume scaffold |
| `production/.gitignore` | Java and local-secret exclusions |
| `production/README.md` | Project setup and usage |
| `production/MIGRATION_CHECKLIST.md` | Production readiness actions |
| `production/src/scripts/` | Environment and Java runtime launch helpers |
| `production/src/load/run-orchestrator.sh` | Runtime registry adapter for the copied loader |

## Files Excluded

| Path or category | Reason |
|---|---|
| Bootcamp progress, preferences, and recap files | Session-specific state and documentation |
| `data/raw/`, `data/samples/`, and `data/subsets/` | Raw, sample, and evaluation-only data |
| `logs/`, `backups/`, and `docs/feedback/` | Local operational artifacts and feedback |
| `config/engine_config.json`, license files, and the evaluation database | Environment-specific settings, secrets, and evaluation state |

## Issues Encountered

- The copied loader requires `mapping_status` and `load_status`, while the required registry
  projection excludes those fields. The new launcher adds them only to a temporary runtime copy;
  the checked-in registry remains the required minimal projection.
- The launchers depend on environment and JSON-P support files that were not in the prescribed copy
  set. Graduation added the required scripts and JSON-P dependency support path.

## Next Steps

1. Replace example data with approved production inputs.
2. Fill in environment-specific configuration and secrets.
3. Obtain and configure a production license.
4. Work through `MIGRATION_CHECKLIST.md`.
5. Configure CI/CD and test with representative production data.
