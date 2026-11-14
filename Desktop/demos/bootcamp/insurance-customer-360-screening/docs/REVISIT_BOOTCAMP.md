# Revisiting Your Senzing Bootcamp

## Quick start when you return

Run these from the project folder:

```bash
source src/scripts/senzing-env.sh
cp backups/revisit/database/G2C.db database/G2C.db
src/scripts/sz-java.sh compile src/server/*.java src/load/*.java src/validate/*.java src/query/*.java
src/scripts/sz-java.sh run query.CustomerResolution
src/scripts/sz-java.sh run server.VizServer --export --business --port 8091 --title "Insurance Customer 360 and Screening"
```

Only restore the database (the `cp` line) if `database/G2C.db` is missing or damaged. The app then runs at <http://localhost:8091>.

## What you accomplished

- **Entity Resolution Concepts:** learned how records become entities, ambiguous matches, and disclosed versus discovered relationships.
- **Discover the Business Problem:** defined an insurer's customer 360 and screening problem across six sources and 248 records.
- **SDK setup:** verified the Senzing SDK 4.4.4 install with Java on Apple Silicon and created the SQLite datastore.
- **System verification:** passed all eight installation checks with synthetic records.
- **Truth Set visualization:** watched the Truth Set resolve 159 records into 85 entities in a Java web app.
- **Data collection:** collected and validated six source files, 248 records.
- **Data Quality, Mapping, and Transformation:** scored and mapped all six sources into Senzing-ready JSONL with Java transformers.
- **Data processing:** loaded 248 of 248 records into 157 entities, drained redo, and validated the results with evidence.
- **Query, Visualize and Discover:** resolved 179 policy records into 127 customers (38 holding both lines), built a 19-alert screening queue and a nine-tab web app, and explored why, how and relationship networks.

## Your business problem and data sources

One Senzing resolution of six sources serves both a customer 360 (distribution and service) and a screening and due diligence queue (compliance and the SIU). See `docs/business_problem.md` and `docs/business_problem.pdf`.

| Source | Records | Role |
|---|---|---|
| PC_POLICY_ADMIN | 104 | P&C policy administration |
| LIFE_ANNUITY_ADMIN | 75 | Life and annuity administration |
| CREDIT_BUREAU | 30 | Identity verification |
| BUSINESS_REGISTRY | 22 | Owners, officers and parent companies |
| SIU_CASES | 13 | Special investigations unit cases |
| SANCTIONS_PEP | 4 | Sanctions and PEP screening list |

The registry is `config/data_sources.yaml`; the screening rules are in `docs/reference/screening_rules.md`.

## Restore the database

The database is SQLite. To restore the saved copy, copy it back:

```bash
cp backups/revisit/database/G2C.db database/G2C.db
```

## Re-initialize and re-run

1. Re-source the environment in each new terminal: `source src/scripts/senzing-env.sh`.
2. Compile: `src/scripts/sz-java.sh compile src/server/*.java src/load/*.java src/validate/*.java src/query/*.java src/transform/*.java`.
3. Re-run the queries: `query.CustomerResolution`, `query.Customer360`, `query.ScreeningQueue`, `query.SearchEntities "<name>"`, `query.DiscoverDemo how 400030`.
4. Start the web app with the `server.VizServer --export --business` command above.
5. To reload from scratch instead of restoring, register sources with `load.RegisterDataSources` and load with `load.Orchestrator`.

## License

No custom license file is installed (`licenses/` is empty), so Senzing runs on its built-in evaluation license, which allows 500 records. Your 248 records fit within it. The evaluation license has no expiry date recorded in this project.

## Where things are

- `backups/revisit/`: the database backup (`database/G2C.db`), the saved state (`state/`), and `RESUME_STATE.json`.
- `docs/bootcamp_recap.pdf`: your recap and Certificate of Completion. The source is `docs/bootcamp_recap.md`.
- `docs/bootcamp_recap.mp4`: your 2-minute narrated graduation video. Its storyboard is `docs/video/storyboard.json`.
- `docs/bootcamp_data_discoveries.pdf`: what Senzing found in your data.
- `docs/business_problem.pdf` and `docs/data_source_evaluation.pdf`: keepsake documents.
- `docs/visualizations/`: the saved web app snapshot (`results_visualization.html`), the Truth Set snapshot and all screenshots.
- `production/`: the production project. Start with `production/GRADUATION_REPORT.md`.
