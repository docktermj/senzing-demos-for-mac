# Loading Strategy

## First-source choice

- **Source:** AML_WATCHLIST
- **Deciding heuristic:** Quality-first among the reference sources. AML_WATCHLIST has a quality score of 88.7, higher than BUSINESS_REGISTRY at 80.6; both are reference sources and load before the transactional BANK_CUSTOMERS source.

## Source dependencies and orchestration

- **Dependencies:** None among the three synthesized sources.
- **Strategy:** Sequential, approved by the bootcamper.
- **Remaining order:** BUSINESS_REGISTRY, then BANK_CUSTOMERS. AML_WATCHLIST is already loaded and must not be reloaded.

## Load budget

The current repository count is 17 records, measured with the SDK entity export. The evaluation license capacity is 500 records.

| Source | Planned records | Projected total after source | Plan |
| --- | ---: | ---: | --- |
| BUSINESS_REGISTRY | 22 | 39 / 500 | Full load |
| BANK_CUSTOMERS | 120 | 159 / 500 | Full load |

Both sources fit the remaining capacity of 483 records; after both full loads, 341 records of capacity remain. Re-measure after the test load and before the full loads.

## Loading program

`src/load/LoadRecords.java` is the Java loader. It reads one JSONL file, uses the engine configuration initialized by `src/scripts/senzing-env.sh`, and writes per-record failures to `logs/loading_errors.json`. The launcher archives an existing error log before each run.

From the project root, compile and run it with:

```bash
source src/scripts/senzing-env.sh
mkdir -p src/load/.build
javac -cp "$SENZING_ROOT/sdk/java/sz-sdk.jar:src/server/target/dependency/javax.json-1.1.4.jar" \
  -d src/load/.build src/load/LoadRecords.java
src/scripts/senzing-java.sh \
  'src/load/.build:src/server/target/dependency/javax.json-1.1.4.jar' \
  loading.LoadRecords data/senzing-ready/AML_WATCHLIST.jsonl
```

The selected source code must already be registered, and the input path must name its load-ready JSONL file. Back up `database/G2C.db` before each load. Review the loader summary and error log to reconcile attempted, successful, and failed records.

`src/load/LoadOrchestrator.java` runs each source in its own process, in the order supplied on the command line. It reads source paths and any `load_subset` plan from `config/data_sources.yaml`, checks per-source counts, logs each attempt, and retries only when every failure is classified as retryable. Retry delays use capped exponential backoff. `--test-count=N` loads the first N records per source without changing registry load status.

Compile and test the remaining sources with 10 records each:

```bash
source src/scripts/senzing-env.sh
mkdir -p src/load/.build
javac -cp "$SENZING_ROOT/sdk/java/sz-sdk.jar:src/server/target/dependency/javax.json-1.1.4.jar" \
  -d src/load/.build src/load/LoadRecords.java src/load/LoadOrchestrator.java
src/scripts/senzing-java.sh \
  'src/load/.build:src/server/target/dependency/javax.json-1.1.4.jar' \
  loading.LoadOrchestrator BUSINESS_REGISTRY BANK_CUSTOMERS --test-count=10
```

After re-measuring repository records and creating a fresh database backup, run the full loads:

```bash
src/scripts/senzing-java.sh \
  'src/load/.build:src/server/target/dependency/javax.json-1.1.4.jar' \
  loading.LoadOrchestrator BUSINESS_REGISTRY BANK_CUSTOMERS
```

The orchestrator defaults to four retries (`--max-retries=N` overrides this). Review its per-source and total reconciliations before updating registry load statuses.

`src/load/ProcessRedo.java` is the terminating batch redo processor. Compile and run it from the project root after a load:

```bash
source src/scripts/senzing-env.sh
javac -cp "$SENZING_ROOT/sdk/java/sz-sdk.jar" \
  -d src/load/.build src/load/ProcessRedo.java
src/scripts/senzing-java.sh src/load/.build loading.ProcessRedo
```

It exits when the queue is empty and reports how many redo records it processed; it is not the always-running production processor.

## Incremental strategy

- **Initial load:** use a full-file load to establish the starting repository.
- **Routine production updates:** for the medium-scale target, prefer incremental loads of new or changed records rather than reloading every source file. This requires a reliable source-side change detector, such as a timestamp, sequence number, or change-data-capture feed; the specific mechanism is not selected yet.
- **Upserts:** the source code and `RECORD_ID` together identify a record. Sending an existing key replaces its current record without increasing the data-source-record count, and loading the new record performs entity resolution.
- **Full reloads:** reserve these for initial setup or a deliberate rebuild, not routine updates.

## Final load and validation results

The full load order was AML_WATCHLIST, BUSINESS_REGISTRY, then BANK_CUSTOMERS. Each source's
expected count reconciled to its attempted, loaded, and exported records; all 159 records loaded
with zero failures. The 9-record redo queue was drained. The final SDK export contained 159
records across 120 entities.

| Data source | Expected | Attempted | Loaded | Failed | Exported records | Entities containing source records |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| AML_WATCHLIST | 17 | 17 | 17 | 0 | 17 | 16 |
| BUSINESS_REGISTRY | 22 | 22 | 22 | 0 | 22 | 22 |
| BANK_CUSTOMERS | 120 | 120 | 120 | 0 | 120 | 91 |

The exported repository contained 30 multi-record entities and 9 cross-source entities (1
AML_WATCHLIST + BANK_CUSTOMERS; 8 BANK_CUSTOMERS + BUSINESS_REGISTRY). It also contained 153
unique relationship pairs. The match-key audit found no cross-source per-record suppressors and
one single-source per-record `-GENDER` key among 25. Relationship suppressors were frequent
(131/153 keys, chiefly `-DOB`); this is a separate candidate-review signal, not a per-record
over-merge rate.

All 30 multi-record entities were checked through the SDK how-state audit; all responses carried
the expected final-state fields and none showed either audited sign. Manual review flagged
entities 200005, 700001, 700030, and 700050 for closer inspection. These are possible over-merges,
not confirmed errors. The source files are not recorded as randomly sampled or reduced.

The requested `actual_truthset_key.csv` is absent, so absolute accuracy, precision, recall, and
F1 could not be measured. See `docs/results_validation.md` and `docs/uat_results.md` for the
reconciled metrics, sampled evidence, and limitations.
