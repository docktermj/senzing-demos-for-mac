# Revisiting Your Senzing Bootcamp

## Quick start when you return

Run these from the project folder (`banking-customer-360-kyc`):

```bash
source src/scripts/senzing-env.sh
cp backups/revisit/database/G2C.db database/G2C.db
javac -cp "$SZ_SDK_JAR" -d data/temp/query_classes src/server/MiniJson.java src/query/*.java
src/scripts/run-java.sh data/temp/query_classes Customer360
javac -cp "$SZ_SDK_JAR" -d data/temp/server_classes src/server/*.java
src/scripts/run-java.sh data/temp/server_classes TruthsetVizServer --from-export --title "Banking Customer 360 and KYC" --dataset "your CORE_BANKING, CARD_SERVICES, CREDIT_BUREAU, BUSINESS_REGISTRY and WATCHLIST data" --snapshot docs/visualizations/results_visualization.html --port 8081
```

Then open `http://localhost:8081` in a browser.

## What you accomplished

- **Entity Resolution Concepts:** learned how records become entities, the failure modes, and disclosed versus discovered relationships.
- **Discover the Business Problem:** defined a Customer 360 and KYC/KYB problem for a US regional bank across five sources (248 records), with success criteria.
- **SDK setup:** installed and verified Senzing SDK 4.4.4 from Java on macOS, with a SQLite repository.
- **System verification:** all eight installation checks passed on synthetic records, which were then purged.
- **Truth Set visualization:** watched the Senzing Truth Set resolve 159 records into 85 entities in an interactive visualization.
- **Data collection:** collected and validated the five banking sources, with checksums and record counts.
- **Data Quality, Mapping, and Transformation:** scored each source and mapped all five to Senzing JSON with Java transformers.
- **Data processing:** loaded all 248 records with no failures into 157 entities, drained redo, and validated against the answer key (97.8% pair precision).
- **Query, Visualize and Discover:** built the Customer 360 (127 customers) and a KYC/KYB alert queue (36 alerts), visualized the results, and explored why, how and network analysis.

## Your business problem and data sources

The bank's relationship managers need one view of each customer, and its compliance team needs screening, identity and ownership alerts with evidence. One resolution of five systems serves both. The full statement is in `docs/business_problem.md` and `docs/business_problem.pdf`.

| Data source | Records | Senzing-ready file |
|---|---|---|
| CORE_BANKING | 97 | `data/senzing-ready/core_banking.jsonl` |
| CARD_SERVICES | 82 | `data/senzing-ready/card_services.jsonl` |
| CREDIT_BUREAU | 30 | `data/senzing-ready/credit_bureau.jsonl` |
| BUSINESS_REGISTRY | 22 | `data/senzing-ready/business_registry.jsonl` |
| WATCHLIST | 17 | `data/senzing-ready/watchlist.jsonl` |

The registry with every detail is `config/data_sources.yaml`.

## Restore the database

The resolved repository was backed up at graduation. Copy it back to put the database exactly where you left it:

```bash
cp backups/revisit/database/G2C.db database/G2C.db
```

## Re-initialize and re-run

1. Load the environment in each new terminal with `source src/scripts/senzing-env.sh`. It sets the engine configuration, the library path and the Java SDK jar for this project only.
2. Compile the program group you need with `javac -cp "$SZ_SDK_JAR" -d data/temp/<group>_classes src/server/MiniJson.java src/<group>/*.java`, where the group is `load` or `query`.
3. Re-run the loader (`LoadOrchestrator`, then `ProcessRedoQueue`), the queries (`Customer360`, `KycAlerts`, `Discover <mode>`), or the visualization (above) with `src/scripts/run-java.sh`.
4. Your configuration snapshot is in `backups/revisit/state/`. Copy files back from there into `config/` if you need to roll back to the state at graduation.

## License

This bootcamp ran on Senzing's built-in evaluation license (no custom license file was installed): a 500-record limit, which expires on 2027-11-11. The project loaded 248 records. For more records, get a license and follow the Licensing section of `production/MIGRATION_CHECKLIST.md`.

## Where things are

- `backups/revisit/`: the database backup (`database/G2C.db`), the configuration snapshot (`state/`) and the index of both (`RESUME_STATE.json`).
- `docs/bootcamp_recap.pdf`: your recap and Certificate of Completion, from `docs/bootcamp_recap.md`.
- `docs/bootcamp_recap.mp4`: your 2-minute graduation video. Its storyboard is `docs/video/storyboard.json`.
- `docs/business_problem.pdf` and `docs/data_source_evaluation.pdf`: the keepsake problem statement and source evaluation.
- `docs/visualizations/`: the visualization snapshots (`results_visualization.html`, `truthset_verification.html`) and the mapping quality pages.
- `docs/results/`: the Customer 360 master list and the KYC/KYB alert queue.
- `production/`: the clean project to build on. Start with `production/GRADUATION_REPORT.md`.
