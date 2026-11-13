# Schema Hints

## BUSINESS_REGISTRY-regenerated

- Source file: `data/raw/BUSINESS_REGISTRY-regenerated.jsonl`
- 22 JSONL root records; `FEATURES` contains per-feature objects and a nested `RECORD_TYPE` discriminator (16 PERSON, 6 ORGANIZATION).
- `NAME_FULL` and name components vary by record type; address objects may have both `ADDR_FULL` and structured parts. Optional root `DATE` occurs in 6 records; keep one logical schema.
- Relationship attributes are nested in `FEATURES`: `REL_ANCHOR_DOMAIN`/`REL_ANCHOR_KEY` and `REL_POINTER_DOMAIN`/`REL_POINTER_KEY`/`REL_POINTER_ROLE` reference the `REFERENCE` domain. Other nested families include EMPLOYER, TAX_ID, NATIONAL_ID, and LEI.
- Mapper language: Java.

- Source file: `data/raw/BANK_CUSTOMERS-regenerated.jsonl`
- Mapper language: Java
- `BANK_CUSTOMERS-regenerated` contains 120 JSONL root records with `DATA_SOURCE`, `RECORD_ID`, and a `FEATURES` list.
- `RECORD_TYPE` is nested in `FEATURES`; it distinguishes 114 PERSON and 6 ORGANIZATION records.
- Three field patterns differ by the optional root fields `AMOUNT`, `DATE`, `STATUS`, and `CATEGORY`. Keep this as one logical source schema unless a root field is shown to distinguish record meaning.
- Some address objects contain both `ADDR_FULL` and structured parts; some phone objects contain `PHONE_TYPE` without `PHONE_NUMBER`.
