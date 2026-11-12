# Revisit Your Senzing Bootcamp

## Quick start when you return

```bash
cd /path/to/this/project
source src/scripts/senzing-env.sh
cp backups/revisit/database/G2C.db database/G2C.db
python3 src/query/unique_customer_counts.py
python3 src/server/results_server.py --port 8081 --snapshot docs/visualizations/results_visualization.html
```

## What you accomplished

- **Entity Resolution Concepts:** learned how records become entities.
- **Discover the Business Problem:** defined a Customer 360 problem across CRM, Billing and Support.
- **SDK setup:** installed Senzing SDK 4.4.3 with a SQLite datastore.
- **System verification:** verified the pipeline end to end on synthetic records.
- **Truth Set visualization:** explored 159 Truth Set records resolved into 84 entities.
- **Data collection:** generated 10,000 deliberately messy records across three systems.
- **Data Quality, Mapping, and Transformation:** scored, improved (Billing 76.0 to 90.2) and mapped all three sources.
- **Data processing:** loaded 10,000 records into 6,671 entities; precision 100%, recall 80.9% against the answer key.
- **Query, Visualize and Discover:** answered three business questions, built the results app and the data-discoveries report.

## Decisions you made

- Discover the Business Problem: had the bootcamp generate a Customer 360 scenario, deliberately messy so mapping works hard.
- Discover the Business Problem: desired output: a clean master list, an API, and reports.
- Discover the Business Problem: standalone (no integration targets), deployed locally / on-premises.
- SDK setup: SQLite as the database.
- System verification: predicted the Mari record joins Marisol's entity (agreed).
- Data Quality, Mapping, and Transformation: BILLING_SYSTEM: improve the weakest fields first, then return to Data collection to regenerate it (76.0 to 90.2).
- Data Quality, Mapping, and Transformation: concise mapping mode; CRM_SYSTEM columns decided one at a time (created_date ignored, campaign as payload).
- Data processing: medium production volume (500,000 to 10,000,000 records); sequential load, no dependencies.
- Data processing: three expectation-check predictions, all agreed with Senzing.
- Query, Visualize and Discover: questions chosen: customers in all three systems, the review queue, unique-customer counts; the prediction exercise kept only in Data processing; no export.

## How you got it built

- **Discover the Business Problem:** you asked for a messy, generated Customer 360 problem; the AI wrote the business case and generated three synthetic sources with an answer key with the Senzing MCP server.
- **SDK setup:** you asked for SQLite; the AI installed and configured the Senzing SDK, environment script and database with the Senzing MCP server.
- **System verification:** you predicted the Mari outcome; the AI built and ran the verification pipeline on synthetic records with the Senzing MCP server.
- **Truth Set visualization:** you explored the Truth Set; the AI loaded it and served a six-tab interactive visualization with the Senzing MCP server.
- **Data Quality, Mapping, and Transformation:** you directed the quality fixes and column decisions; the AI scored, mapped and transformed all three sources with the Senzing MCP server.
- **Data processing:** you set volume and load order; the AI built the threaded loader, orchestrator and redo processor, loaded 10,000 records and validated them with the Senzing MCP server.
- **Query, Visualize and Discover:** you asked three business questions; the AI built the query programs, results visualization, Why/How/network demonstrations and the discoveries report with the Senzing MCP server.

## Next time

**The guide's draft (you passed):** "Help me map and load our real CRM, billing and support-ticket exports into Senzing, and give me a weekly review queue of customers that appear in more than one system but were not merged."

## Your business problem and data sources

Unify fragmented, inconsistently formatted customer records across three systems into one view of each customer (see `docs/business_problem.md`). Sources (see `config/data_sources.yaml`): CRM_SYSTEM (4,000 records), BILLING_SYSTEM (4,000) and SUPPORT_TICKETS (2,000).

## Restore the database

```bash
cp backups/revisit/database/G2C.db database/G2C.db
```

## Re-initialize and re-run

```bash
source src/scripts/senzing-env.sh
python3 src/load/process_redo.py
python3 src/query/customers_in_all_sources.py
python3 src/query/review_queue.py
python3 src/server/results_server.py --port 8081
```

To reload from scratch, register the sources (`python3 src/load/register_data_sources.py`), then run `python3 src/load/load_crm_system.py data/senzing-ready/crm_system.jsonl` and `python3 src/load/orchestrator.py`, then drain redo.

## License

The bootcamp ran on the license recorded in `config/license.json` (evaluation, no record cap), which expires on 2027-03-12. For production, put a production license in the engine configuration's `PIPELINE` section (`LICENSESTRINGBASE64` or `LICENSEFILE`).

## Where things are

- `backups/revisit/`: state snapshot, `RESUME_STATE.json` and the database backup.
- `docs/bootcamp_recap.pdf`: your recap and certificate.
- `docs/bootcamp_recap.mp4`: your graduation video.
- `docs/visualizations/`: saved visualizations and screenshots.
- `docs/bootcamp_data_discoveries.pdf`: what Senzing found.
- `production/`: the project to build on.
