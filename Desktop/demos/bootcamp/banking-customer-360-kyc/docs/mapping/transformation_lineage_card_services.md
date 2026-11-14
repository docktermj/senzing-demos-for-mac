# Transformation Lineage: CARD_SERVICES

| | File | Records | sha256 |
|---|---|---|---|
| Source | data/raw/card_cardholder_master.jsonl | 82 | 9dbdbcf805f59b7c… |
| Output | data/senzing-ready/card_services.jsonl | 82 | 99aebf49eccb387e… |

- **Program:** src/transform/transform_card_services.java (shared SenzingJsonPassThrough.java), 2026-11-14
- **Field mappings:** see docs/mapping/CARD_SERVICES_mapper.md (35 feature, 9 payload, 3 ignored)
- **Format changes:** none; every emitted value is verbatim from the source
- **Renames:** none
- **Filters:** 2 qualifier-only PHONE_TYPE objects dropped
- **Corrections:** none
- **Records in / out / rejected:** 82 / 82 / 0
