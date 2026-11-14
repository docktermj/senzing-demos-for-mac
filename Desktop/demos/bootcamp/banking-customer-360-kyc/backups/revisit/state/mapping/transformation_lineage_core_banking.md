# Transformation Lineage: CORE_BANKING

| | File | Records | sha256 |
|---|---|---|---|
| Source | data/raw/core_cif_extract.jsonl | 97 | 175d5b16ba112c26… |
| Output | data/senzing-ready/core_banking.jsonl | 97 | bc51f4b53775e881… |

- **Program:** src/transform/transform_core_banking.java (shared SenzingJsonPassThrough.java), 2026-11-14
- **Field mappings:** see docs/mapping/CORE_BANKING_mapper.md (34 feature, 8 payload, 3 ignored)
- **Format changes:** none; every emitted value is verbatim from the source
- **Renames:** payload ACCOUNT_NUMBER → DEPOSIT_ACCOUNT_ID
- **Filters:** 2 qualifier-only PHONE_TYPE objects dropped; 0 records rejected
- **Corrections:** record 3025 re-typed ORGANIZATION (bootcamper decision)
- **Records in / out / rejected:** 97 / 97 / 0
