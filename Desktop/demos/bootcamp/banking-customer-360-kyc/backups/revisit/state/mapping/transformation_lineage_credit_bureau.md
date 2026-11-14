# Transformation Lineage: CREDIT_BUREAU

| | File | Records | sha256 |
|---|---|---|---|
| Source | data/raw/credit_header.jsonl | 30 | 6de2f56fdc6fe697… |
| Output | data/senzing-ready/credit_bureau.jsonl | 30 | a6191962f71b1301… |

- **Program:** src/transform/transform_credit_bureau.java (shared SenzingJsonPassThrough.java), 2026-11-14
- **Field mappings:** see docs/mapping/CREDIT_BUREAU_mapper.md (17 feature, 2 payload, 3 ignored)
- **Format changes:** none; every emitted value is verbatim from the source
- **Renames:** none
- **Filters:** none
- **Corrections:** none
- **Records in / out / rejected:** 30 / 30 / 0
