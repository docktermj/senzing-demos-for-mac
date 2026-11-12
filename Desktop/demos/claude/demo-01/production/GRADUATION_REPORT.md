# Graduation Report

**Completed:** 2026-11-09T23:29:35-05:00
**Path:** Core
**Modules completed:** Entity Resolution Concepts, Discover the Business Problem, SDK setup, System verification, Truth Set visualization, Data collection, Data Quality, Mapping, and Transformation, Data processing, Query, Visualize and Discover
**Programming language:** Python
**Database:** sqlite
**Deployment target:** local (local or on-premises, platform not stated)

## Files generated

| File | Purpose |
|---|---|
| production/src/transform/ | Transformation programs |
| production/src/load/ | Loaders, orchestrator, registration, redo |
| production/src/query/ | Query, review and audit programs |
| production/data/senzing-ready/ | Load-ready JSONL (10,000 records) |
| production/config/data_sources.yaml | Data-source registry (projection) |
| production/database/.gitkeep | Database placeholder |
| production/.env.example | Environment template |
| production/docker-compose.yml | Container template |
| production/.gitignore | Ignore rules |
| production/README.md | Project guide |
| production/MIGRATION_CHECKLIST.md | Steps before deploying |

## Files excluded

| Excluded | Why |
|---|---|
| config/bootcamp_progress.json, config/bootcamp_preferences.yaml | Bootcamp state |
| docs/bootcamp_recap.md, docs/feedback/, docs/bootcamp_notes.md | Bootcamp records |
| data/raw/ | Raw source exports: supply data/raw/crm_system.csv, data/raw/support_tickets.csv and data/raw/billing_system-regenerated.csv as the transforms' input arguments |
| data/samples/, data/subsets/, data/backups/ | Evaluation-only data |
| database/G2C.db | Evaluation database (never copied) |
| logs/, backups/ | Run logs and backups |

## Next steps

1. Fill in secrets in .env from .env.example.
2. Obtain a production Senzing license.
3. Work through MIGRATION_CHECKLIST.md.
4. Configure CI/CD.
5. Test with production data.
