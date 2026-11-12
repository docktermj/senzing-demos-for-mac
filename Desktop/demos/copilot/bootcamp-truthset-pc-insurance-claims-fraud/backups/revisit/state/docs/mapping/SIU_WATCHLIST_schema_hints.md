# SIU_WATCHLIST Structural Hints

- Mapper language: Java; input: `data/raw/SIU_WATCHLIST-improved.jsonl`.
- One JSONL schema with 17 records and 25 fields. Each root record contains a `FEATURES` array of single-purpose objects plus `DATA_SOURCE`, `RECORD_ID`, `CATEGORY`, `DATE`, and `STATUS`.
- `FEATURES.RECORD_TYPE` identifies all 17 records as PERSON. Nested fields include names, birth date, address parts, driver's license, email, SSN, gender, phone, and employer.
- `NAME_FULL` never co-occurs with parsed name fields; no feature attribute repeats within a record, and no relationship keys or secondary schemas are present.
