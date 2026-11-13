# Revisiting your Senzing bootcamp

## Quick start when you return

```bash
cd /path/to/this/project
source src/scripts/senzing-env.sh
cp backups/revisit/database/G2C.db database/G2C.db
src/scripts/run-java.sh CustomerReports
src/scripts/run-java.sh VizServer --export --title "Customer 360 — resolved customers" --dataset "your CRM_CONTACTS, WEB_ORDERS and SUPPORT_TICKETS data" --port 8080
```

Run the restore line only if `database/G2C.db` is missing or damaged. The last line starts the interactive visualization at `http://localhost:8080`; stop it with Ctrl+C.

## What you accomplished

- **Entity Resolution Concepts:** how records become entities, and how matches, possible matches and relationships differ.
- **Discover the Business Problem:** a Customer 360 problem across three systems, with a master list, an API and reports as the desired outputs.
- **SDK setup:** Senzing SDK 4.3.2 for Java on Apple Silicon, a SQLite repository and a primed engine.
- **System verification:** all eight installation checks passed against synthetic records.
- **Truth Set visualization:** 159 Truth Set records resolved into 85 entities in an interactive Java app.
- **Data collection:** three synthetic sources, 10,000 records, with your license applied.
- **Data Quality, Mapping, and Transformation:** quality scores of 93.4, 89.5 and 75.5, and a Java mapper per source producing 10,000 validated Senzing JSON records.
- **Data processing:** all 10,000 records loaded with zero errors into 7,134 entities; 99.9% precision and 95.1% recall against the answer key.
- **Query, Visualize and Discover:** Java query programs for the six business questions, a six-tab interactive app, and the why, how and network demonstrations.

## Your business problem and data sources

A retailer holds the same customers in a CRM, a web store and a support desk, under different spellings and formats, and cannot get a trustworthy customer count. See `docs/business_problem.md` (and `docs/business_problem.pdf`).

| Source | Records | Load-ready file |
|---|---|---|
| CRM_CONTACTS | 4,000 | `data/senzing-ready/crm_contacts.jsonl` |
| WEB_ORDERS | 4,500 | `data/senzing-ready/web_orders.jsonl` |
| SUPPORT_TICKETS | 1,500 | `data/senzing-ready/support_tickets.jsonl` |

## Restore the database

The resolved repository was backed up at graduation. To restore it (SQLite):

```bash
cp backups/revisit/database/G2C.db database/G2C.db
```

## Re-initialize and re-run

1. Re-source the environment in each new terminal: `source src/scripts/senzing-env.sh`.
2. Re-compile if `src/build/` is gone:

   ```bash
   bash -c 'source src/scripts/senzing-env.sh && "$SENZING_JAVA_HOME/bin/javac" -d src/build/classes -cp "$SENZING_SDK_JAR" src/server/*.java src/transform/*.java src/load/*.java src/query/*.java src/validate/*.java src/scripts/*.java'
   ```

3. Reload from scratch (only into a fresh repository): `src/scripts/run-java.sh RegisterDataSources`, then `src/scripts/run-java.sh LoadCustomerData data/senzing-ready/crm_contacts.jsonl`, then `src/scripts/run-java.sh Orchestrator`, then `src/scripts/run-java.sh ProcessRedo`.
4. Re-run the queries: `CustomerReports`, `CustomerSearch`, `ExplainMatch`, `ExplainHow` and `ExplainNetwork` (see `docs/query_results.md`).

## License

Your license file is `licenses/g2.lic` (an evaluation license with no record cap), wired into `config/engine_config.json` as `LICENSEFILE`. It expires on 2027-03-12.

## Where things are

- `backups/revisit/`: the database backup (`database/G2C.db`), a snapshot of your configuration and mapping docs (`state/`), and `RESUME_STATE.json` indexing them.
- `docs/bootcamp_recap.pdf`: your recap and Certificate of Completion (source: `docs/bootcamp_recap.md`).
- `docs/bootcamp_recap.mp4`: your 2-minute graduation video (storyboard: `docs/video/storyboard.json`).
- `docs/bootcamp_data_discoveries.pdf`: what Senzing found in your data.
- `docs/visualizations/`: the saved visualizations and their screenshots.
- `production/`: the production project, starting with `production/GRADUATION_REPORT.md`.
