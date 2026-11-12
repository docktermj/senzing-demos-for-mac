# Schema Hints: BANK_CUSTOMERS

## BANK_CUSTOMERS-regenerated

- Source file: `data/raw/BANK_CUSTOMERS-regenerated.jsonl`
- 120 JSONL root records with `DATA_SOURCE`, `RECORD_ID`, and a `FEATURES` list.
- `RECORD_TYPE` is nested in `FEATURES`; it distinguishes 114 PERSON and 6 ORGANIZATION records.
- Optional root `AMOUNT`, `DATE`, `STATUS`, and `CATEGORY` create field-pattern variations but do not distinguish separate entity schemas.
- Address feature objects may contain `ADDR_FULL` or structured components. Some phone objects contain `PHONE_TYPE` without `PHONE_NUMBER`.
- Mapper language: Java.
