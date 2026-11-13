# Graduation report

**Completed:** 2026-10-07T10:36:32-0400
**Path:** Core
**Programming language:** Java
**Database:** SQLite (development); PostgreSQL recommended for production
**Deployment target:** local / on-premises
**Integration targets:** none recorded (standalone)

## Modules completed

1. Entity Resolution Concepts
2. Discover the Business Problem
3. SDK setup
4. System verification
5. Truth Set visualization
6. Data collection
7. Data Quality, Mapping, and Transformation
8. Data processing
9. Query, Visualize and Discover

Results at graduation: 10,000 records resolved into 7,134 customers, 2,090 of them in more than one system; pairwise precision 99.9% and recall 95.1% against the generated answer key.

## Files generated

| File | What it is |
|---|---|
| `src/transform/` | Mappers for CRM_CONTACTS, WEB_ORDERS, SUPPORT_TICKETS, with the shared Records reader and Mapper helper |
| `src/load/` | RecordLoader engine, LoadCustomerData, Orchestrator, ProcessRedo, RegisterDataSources |
| `src/query/` | CustomerReports, CustomerSearch, ExplainMatch, ExplainHow, ExplainNetwork, DiscoverPatterns, SenzingSession |
| `src/server/Json.java` | Dependency-free JSON reader/writer the loader and queries depend on (copied unchanged) |
| `src/scripts/run-java.sh` | JVM launcher (copied unchanged) |
| `src/scripts/senzing-env.sh` | Environment script, generated for this project (the evaluation copy depended on workspace-only files) |
| `src/scripts/build.sh` | Compiles all sources with plain javac (generated) |
| `data/senzing-ready/` | Load-ready Senzing JSON Lines: 4,000 + 4,500 + 1,500 records |
| `config/data_sources.yaml` | Source registry projection: name, file_path and format per source |
| `database/.gitkeep` | Placeholder for the SQLite repository |
| `.env.example` | Configuration template (placeholders only) |
| `docker-compose.yml` | Single-service SQLite compose file (development) |
| `.gitignore` | Keeps secrets, databases, logs and results out of git |
| `README.md` | Project documentation |
| `MIGRATION_CHECKLIST.md` | Steps before production |

The project was compiled from this directory alone (22 classes, no warnings) before this report was written.

## Files excluded

| Excluded | Why |
|---|---|
| `data/raw/` (crm_contacts.csv, web_orders.csv, support_tickets.jsonl) | Raw source exports are yours to place; the mappers read them from `data/raw/` (see README → Configuration) |
| `database/G2C.db` | The evaluation repository is never copied; build a fresh one (PostgreSQL for production) |
| `config/engine_config.json`, `config/license.json`, `licenses/` | Secrets and machine-specific paths; supply through `.env` |
| `config/bootcamp_progress.json`, `config/bootcamp_preferences.yaml` | Bootcamp state |
| `docs/` (recap, notes, feedback, visualizations, reports) | Bootcamp artifacts |
| `src/validate/`, `src/system_verification/`, `src/setup/`, `src/server/VizServer.java` | Evaluation, verification and visualization tooling, not part of the production pipeline |
| `data/samples/`, `data/subsets/`, `data/results/`, `logs/`, `backups/` | Evaluation working data |

## Next steps

1. Fill in `.env` from `.env.example` with real values from your secret store.
2. Obtain a production Senzing license sized for your volume.
3. Work through `MIGRATION_CHECKLIST.md`, starting with the move to PostgreSQL.
4. Configure CI to run `src/scripts/build.sh` and a sample load.
5. Test with production data, then review the cross-system "possibly the same" queue.

## ⚠️ Issues Encountered

- The license environment variable name `SENZING_LICENSE_FILE` could not be confirmed against Senzing's documentation at generation time. `.env.example` therefore makes the engine configuration's `PIPELINE` (`LICENSEFILE` or `LICENSESTRINGBASE64`, both documented) the source of truth.
