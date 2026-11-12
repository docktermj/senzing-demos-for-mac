# Graduation Report

**Completed:** 2026-11-12T12:13:19-05:00
**Bootcamp path:** Core
**Modules completed:** Entity Resolution Concepts; Discover the Business Problem; SDK setup; System Verification; Truth Set visualization; Data collection; Data Quality, Mapping, and Transformation; Data processing; Query, Visualize and Discover
**Programming language:** Java
**Database type:** SQLite
**Deployment target:** local

## Files Generated

| Path | Description |
|---|---|
| `production/src/transform/` | Shared transformation and JSON support |
| `production/src/load/` | Registration, loading, redo, orchestration, and validation programs |
| `production/src/query/` | Query and review utilities |
| `production/src/scripts/` | Production environment and Java launch helpers |
| `production/data/senzing-ready/` | Three mapped source files |
| `production/config/data_sources.yaml` | Versioned projection of all registry entries with non-null file paths |
| `production/database/.gitkeep` | Empty database directory placeholder |
| `production/pom.xml` | Maven build configured for the copied source tree |
| `production/.env.example` | Placeholder environment and configuration values |
| `production/docker-compose.yml` | Single-service local SQLite template |
| `production/.gitignore` | Environment, database, build, and log exclusions |
| `production/README.md` | Project setup, configuration, usage, and structure |
| `production/MIGRATION_CHECKLIST.md` | Database, security, licensing, performance, data, and deployment actions |
| `production/GRADUATION_REPORT.md` | This handoff report |

## Files Excluded

| Path | Reason |
|---|---|
| `config/bootcamp_progress.json`, `config/bootcamp_preferences.yaml` | Session progress and personal preferences |
| `config/engine_config.json`, `config/license.json` | Environment-specific settings and license material |
| `docs/bootcamp_recap.md`, `docs/bootcamp_notes.md` (not present), `docs/feedback/` | Learning recap, notes, and feedback are not production content |
| `data/raw/`, `data/samples/`, `data/subsets/` | Source, sample, and evaluation-slice data |
| `src/system_verification/` | Test-only verification inputs |
| `logs/`, `backups/` | Runtime logs and evaluation database backups |
| `docs/mapping/` | Evaluation mapping artifacts, not needed by the copied runtime |

## Issues Encountered

- The source registry was a YAML list without a top-level schema version, and four test-only entries lacked `format`. The production registry is a `version: 1` mapping; the missing test-only formats are recorded as `JSONL`, derived from their `.jsonl` paths.
- The copied `Orchestrator` accepts explicit `CODE=JSONL_PATH` arguments and does not read the YAML registry. The registry remains descriptive until the loader is updated; its four test-only paths are excluded and must be removed or supplied before use.
- The selected SQLite backend is for local development and must be replaced with a production database before deployment.

## Next Steps

1. Fill in the local engine configuration and provide a properly sized production license without committing secrets.
2. Remove or provision the test-only registry entries, and align the orchestrator's input arguments with the remaining source paths.
3. Replace SQLite with a production database and work through `MIGRATION_CHECKLIST.md`.
4. Configure CI/CD, test the project against production-like data, and review potential matches with qualified human reviewers.
