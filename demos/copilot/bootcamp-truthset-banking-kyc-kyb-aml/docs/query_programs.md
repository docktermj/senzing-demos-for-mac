# Query, Visualize and Discover: query programs

The Java programs in `src/query/` use the loaded data in `data/senzing-ready/` and the Senzing
SDK. Run them from the project root; the launcher compiles the programs and initializes the
existing project environment:

```sh
bash src/query/run.sh query.SearchEntities '<search-criteria-json>'
bash src/query/run.sh query.Customer360 <DATA_SOURCE> <RECORD_ID>
bash src/query/run.sh query.FindDuplicates
bash src/query/run.sh query.ScreeningLeads
bash src/query/run.sh query.QueryResults
```

`SearchEntities` accepts one JSON criteria object. `Customer360` accepts a loaded record's source
and record ID. `FindDuplicates` walks the three load-ready source files, folds duplicate file rows
by `(DATA_SOURCE, RECORD_ID)`, and looks up each distinct record through the SDK. It does not guess
entity-ID ranges. `Customer360` reports the resolved records, source counts, related entities, and
disclosed relationship match keys. When the SDK response includes record JSON, it also shows source
relationship roles; related-entity match keys connect those roles to their resolved entities.
`ScreeningLeads` searches each watchlist feature group, deduplicates leads by entity ID, and writes
`data/query/screening_leads.jsonl`; it reports candidates for human review, never a screening
verdict. `QueryResults` writes the resolved-entity export to `data/query/master_list.jsonl` and a
count summary to `data/query/master_list_summary.txt`.

The `actual_truthset_key.csv` file is absent from this checkout, so the master list cannot be
compared with an authoritative answer key and precision, recall, F1, and absolute match accuracy
remain unmeasured.

These are bootcamp exploration programs and use the SDK's `*_DEFAULT_FLAGS` composites to make
the results inspectable. Those composites can change what they return silently after an SDK
upgrade. Before copying any of these query programs into production, replace exploration defaults
with the exact flags the application consumes and verify the corresponding response schemas; the
graduation migration checklist should retain that action item.
