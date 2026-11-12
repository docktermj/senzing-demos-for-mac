# Schema Hints: AML_WATCHLIST

## AML_WATCHLIST-regenerated

- Source file: `data/raw/AML_WATCHLIST-regenerated.jsonl`
- 17 JSONL root records; `FEATURES` contains per-feature objects with nested `RECORD_TYPE=PERSON`.
- `RECORD_ID` is unique. Repeated `ADDR_TYPE` entries describe multiple addresses and must remain grouped with their own address components.
- Root `CATEGORY`, `DATE`, and `STATUS` are scalar watchlist metadata and remain payload.
- No support schema or joins are needed.
- Mapper language: Java.
