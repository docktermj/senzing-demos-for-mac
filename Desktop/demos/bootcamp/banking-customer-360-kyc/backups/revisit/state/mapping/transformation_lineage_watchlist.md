# Transformation Lineage: WATCHLIST

| | File | Records | sha256 |
|---|---|---|---|
| Source | data/raw/screening_list-improved.jsonl | 17 | 4de0815e738dd067… |
| Output | data/senzing-ready/watchlist.jsonl | 17 | 698f82ed6b9660ff… |

- **Program:** src/transform/transform_watchlist.java (shared SenzingJsonPassThrough.java), 2026-11-14
- **Field mappings:** see docs/mapping/WATCHLIST_mapper.md (19 feature, 3 payload, 3 ignored)
- **Format changes:** none; every emitted value is verbatim from the source
- **Renames:** none
- **Filters:** none
- **Corrections:** none
- **Records in / out / rejected:** 17 / 17 / 0
