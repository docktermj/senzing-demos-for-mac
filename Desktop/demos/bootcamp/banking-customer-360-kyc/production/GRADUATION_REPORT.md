# Graduation Report

**Completed:** 2026-11-14
**Path:** Core
**Modules completed:** Entity Resolution Concepts; Discover the Business Problem; SDK setup; System verification; Truth Set visualization; Data collection; Data Quality, Mapping, and Transformation; Data processing; Query, Visualize and Discover
**Programming language:** Java
**Database type:** sqlite
**Deployment target:** local
**Senzing SDK:** 4.4.4

## Files generated

| File | Purpose |
|---|---|
| README.md | Overview, prerequisites, installation, configuration, usage and structure |
| MIGRATION_CHECKLIST.md | Work to finish before deploying (database, security, licensing, performance, data, deployment) |
| GRADUATION_REPORT.md | This report |
| .env.example | Environment template: DATABASE_URL, SENZING_LICENSE_FILE, LOG_LEVEL |
| docker-compose.yml | SQLite single-service container template |
| .gitignore | Keeps secrets, databases, logs and build output out of version control |
| config/data_sources.yaml | Data-source registry: name, file_path and format for the five sources |
| src/scripts/senzing-env.sh | Environment script that reads .env and builds the engine configuration |
| database/.gitkeep | Placeholder for the repository directory |

## Files copied

| From | To |
|---|---|
| src/transform/ | src/transform/ (six files) |
| src/load/ | src/load/ (four files) |
| src/query/ | src/query/ (four files) |
| data/senzing-ready/ | data/senzing-ready/ (five files, 248 records) |
| src/server/MiniJson.java | src/server/MiniJson.java (the JSON reader the programs share) |
| src/verify/InitDefaultConfig.java | src/verify/InitDefaultConfig.java (needed on a new repository) |
| src/scripts/run-java.sh | src/scripts/run-java.sh |

## Files excluded

| Item | Why |
|---|---|
| database/G2C.db | The evaluation repository is never carried into production |
| data/raw/ | Raw source extracts are yours to supply; the transformers read them from data/raw/ (core_cif_extract.jsonl, card_cardholder_master.jsonl, credit_header.jsonl, business_registry.jsonl, screening_list-improved.jsonl) |
| data/samples/, data/subsets/ | Evaluation-only data |
| config/bootcamp_progress.json, config/bootcamp_preferences.yaml | Training-session state |
| config/engine_config.json, config/license.json | Machine-specific engine settings and the evaluation license; replaced by .env |
| docs/bootcamp_recap.md, docs/feedback/ | Training-session records |
| logs/, backups/ | Run output and evaluation backups |
| src/system_verification/, src/server/ (except MiniJson.java), src/validate/, src/scripts/*.py | Install verification, the evaluation visualization server, answer-key validation and quality-assessment helpers |

## Next steps

1. Copy `.env.example` to `.env` and fill in the database connection and license file.
2. Obtain a production Senzing license sized for the bank's record volume.
3. Work through `MIGRATION_CHECKLIST.md`, starting with the database move and the DEFAULT-flags item.
4. Configure CI to compile the programs against the SDK version you deploy.
5. Test with production data: real extracts, a quality re-check, and a labelled sample to re-validate resolution.
