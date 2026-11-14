# Graduation Report

**Completed:** 2026-11-14T16:38:38-05:00
**Path:** Core (all modules)
**Modules completed:** Entity Resolution Concepts; Discover the Business Problem; SDK setup; System verification; Truth Set visualization; Data collection; Data Quality, Mapping, and Transformation; Data processing; Query, Visualize and Discover
**Programming language:** Java
**Database type:** sqlite
**Deployment target:** local / on-premises

## Files generated

| File | What it is |
|---|---|
| production/README.md | Project overview, installation, configuration and usage |
| production/MIGRATION_CHECKLIST.md | Database, security, licensing, performance, data and deployment items |
| production/GRADUATION_REPORT.md | This report |
| production/.env.example | Environment template with placeholder values |
| production/docker-compose.yml | Single-service SQLite composition (target: local / on-premises) |
| production/.gitignore | Ignores secrets, databases, build output and logs |
| production/config/data_sources.yaml | Data-source registry projection: name, file_path and format per source |
| production/config/engine_config.example.json | Engine settings template (PIPELINE paths, license, SQL connection) |
| production/src/scripts/senzing-env.sh | Production environment script (macOS and Linux) |
| production/database/.gitkeep | Empty database directory placeholder |

## Files copied

| From | To |
|---|---|
| src/transform/ | production/src/transform/ |
| src/load/ | production/src/load/ |
| src/query/ | production/src/query/ |
| src/validate/ | production/src/validate/ (required: the orchestrator uses ExportStats) |
| src/server/ | production/src/server/ (required: Json helper used by every program; the web app) |
| src/setup/ | production/src/setup/ (engine configuration and connection checks) |
| src/scripts/sz-java.sh | production/src/scripts/sz-java.sh |
| data/senzing-ready/ | production/data/senzing-ready/ |

## Files excluded

| Item | Why |
|---|---|
| config/bootcamp_progress.json, config/bootcamp_preferences.yaml | Training-run state |
| config/engine_config.json | Contains this workstation's absolute database path; replaced by engine_config.example.json |
| docs/bootcamp_recap.md, docs/bootcamp_notes.md, docs/feedback/ | Training-run records |
| data/raw/ | Source extracts; supply them from the source systems (the transformers in src/transform/ read them) |
| data/samples/, data/subsets/, data/temp/, data/query/, data/app/ | Evaluation data and run outputs |
| database/G2C.db | The evaluation database is never copied |
| logs/, backups/ | Run artifacts |
| src/system_verification/, src/resources/, src/scripts/*.py | Verification and documentation tooling |

## Next steps

1. Fill in secrets: copy config/engine_config.example.json and .env.example, and set paths, license and connection.
2. Obtain a production Senzing license sized for your record count.
3. Work through MIGRATION_CHECKLIST.md.
4. Configure CI/CD to compile and run the query programs against a test database.
5. Test with production data, then review the screening queue and possible-match queue with analysts.

## ⚠️ Issues Encountered

- src/scripts/senzing-env.sh was rewritten for production: the training version required a training-run state file.
- No Maven or Gradle manifest exists; the code compiles with the JDK and the Senzing SDK jar alone (src/scripts/sz-java.sh).
- The license is configured through PIPELINE.LICENSEFILE or LICENSESTRINGBASE64 (confirmed in Senzing's guidance). SENZING_LICENSE_FILE in .env.example is for your own deployment scripts only; the engine does not read it.
