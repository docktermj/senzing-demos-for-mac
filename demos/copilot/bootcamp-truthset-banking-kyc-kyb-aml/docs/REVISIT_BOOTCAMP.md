# Return to Your Project

## Quick Start When You Return

From the project root:

```bash
source src/scripts/senzing-env.sh
cp backups/revisit/database/G2C.db database/G2C.db
bash src/scripts/senzing-java.sh \
  "src/load/.build:src/server/target/dependency/javax.json-1.1.4.jar" \
  loading.LoadOrchestrator BANK_CUSTOMERS BUSINESS_REGISTRY AML_WATCHLIST
bash src/query/run.sh query.QueryResults
mvn -f src/server/pom.xml package
java -Djava.library.path="$SENZING_ROOT/lib" \
  -cp "src/server/target/classes:src/server/target/dependency/*:$SENZING_ROOT/sdk/java/sz-sdk.jar" \
  TruthSetVizServer --port 8080
```

The query command regenerates `data/query/master_list.jsonl` and its summary. The visualization
server is available at `http://localhost:8080/` while that command is running.

## What You Accomplished

- **Entity Resolution Concepts:** Learned the distinctions among false positives, false negatives,
  and ambiguous matches, then completed the optional knowledge check.
- **Discover the Business Problem:** Defined a fictional bank's KYC/KYB/AML scenario, its three data
  sources, and the goal of producing evidence-backed screening leads.
- **SDK setup:** Verified the installed Java SDK, configured project-local launch scripts, and
  prepared the SQLite datastore and engine configuration.
- **System verification:** Ran the system checks and recorded the results before loading the project
  data.
- **Truth Set visualization:** Built and checked the separate interactive visualization for the
  sample Truth Set.
- **Data collection:** Collected and tracked the three source datasets.
- **Data Quality, Mapping, and Transformation:** Assessed all three sources, completed their
  mappings, and produced load-ready files with source-specific documentation.
- **Data processing:** Loaded and audited 159 records across 120 entities. The supplied answer key
  was unavailable, so precision, recall, F1, and absolute accuracy remain unmeasured.
- **Query, Visualize and Discover:** Built Java query and discovery programs, captured the six-tab
  results visualization, and completed Why, How, and relationship-network demonstrations.

## Your Business Problem and Data Sources

A fictional US bank needs a trusted unified view of customers and their relevant business
connections for KYC, KYB, and AML work. Screening results are evidence-backed leads for review, not
verdicts. The project is local/on-premises, with no downstream integration target recorded.

| Source | Load-ready file | Records |
|---|---|---:|
| `BANK_CUSTOMERS` | `data/senzing-ready/BANK_CUSTOMERS.jsonl` | 120 |
| `BUSINESS_REGISTRY` | `data/senzing-ready/BUSINESS_REGISTRY.jsonl` | 22 |
| `AML_WATCHLIST` | `data/senzing-ready/AML_WATCHLIST.jsonl` | 17 |

## Restore the Database

The SQLite database backup is `backups/revisit/database/G2C.db`. From the project root, restore
it with:

```bash
cp backups/revisit/database/G2C.db database/G2C.db
```

The saved bootcamp configuration and mapping state are under `backups/revisit/state/`.

## Re-initialize and Re-run

After restoring the database, load the project environment in the shell that will run the Java
programs:

```bash
source src/scripts/senzing-env.sh
```

From the project root, run the loader and query programs:

```bash
bash src/scripts/senzing-java.sh \
  "src/load/.build:src/server/target/dependency/javax.json-1.1.4.jar" \
  loading.LoadOrchestrator BANK_CUSTOMERS BUSINESS_REGISTRY AML_WATCHLIST
bash src/query/run.sh query.QueryResults
```

The saved registry marks these three sources as loaded, so the restored project skips them. If
changing or reloading source data, review the registry and database target first. The results
visualization can be rebuilt with `mvn -f src/server/pom.xml package` and launched with the
classpath shown in Quick Start. Its default page is `http://localhost:8080/`.

## License

The recorded configuration uses the built-in evaluation license, which expires on 2027-06-12.
For a custom license, this project uses `licenses/g2.lic` as its file location; explicitly set
`PIPELINE.LICENSEFILE` in `SENZING_ENGINE_CONFIGURATION_JSON`, or supply an encoded license with
`PIPELINE.LICENSESTRINGBASE64`. The launcher does not map `SENZING_LICENSE_FILE` automatically.
Do not include license material in source control.

## Where Things Are

- `backups/revisit/` — database backup, resume manifest, and copied state.
- `docs/bootcamp_recap.pdf` — the complete bootcamp recap.
- `docs/bootcamp_recap.mp4` — the narrated graduation video.
- `docs/visualizations/` — saved visualization pages, tab manifests, and screenshots.
- `docs/bootcamp_data_discoveries.md` and `.pdf` — the data-discoveries report and PDF.
