# Mapping Review Journal

## Start and profile
- Mode: interactive; concise. Mapper language: Java.
- `SIU_WATCHLIST-improved`: 17 records, 25 fields, one nested JSONL schema.

## Entity plan
- Master: `SIU_WATCHLIST-improved` → `SIU_WATCHLIST`, PERSON, keyed by `RECORD_ID`; all records are source-typed PERSON.
- No child, lookup, embedded-master, or relationship schemas; no join fields were found.

## Field mapping
- Keep parsed name components together and map `NAME_FULL` separately; preserve original-script text as supplied, without transliteration.
- Map identifiers and contact/address values to their corresponding features; keep root `CATEGORY`, generic `DATE`, and `STATUS` as payload.
- Preserve `FEATURES.RECORD_TYPE` as provenance for each record's root type; no records have been loaded.

## Generate and validate
- Mapper: `src/transform/transform_SIU_WATCHLIST.java`; workflow copy: `data/mapping/SIU_WATCHLIST_mapper.java`.
- Full output: `data/mapping/SIU_WATCHLIST_output.jsonl` (17 records). Analyzer validation and verbatim fidelity passed.
- Routing review found only the 17 values each of `CATEGORY`, `DATE`, and `STATUS` absent from feature output; all are intentionally retained as root payload, not entity features.
- Sparse DRLIC, SSN, PHONE, and EMPLOYER warnings match the source profile; no records have been loaded. QA verdict: approve.
