# Transformation Lineage: BUSINESS_REGISTRY

| | File | Records | sha256 |
|---|---|---|---|
| Source | data/raw/business_registry.jsonl | 22 | 1d336f533a07aac9… |
| Output | data/senzing-ready/business_registry.jsonl | 22 | ed26b92354f145c0… |

- **Program:** src/transform/transform_business_registry.java (shared SenzingJsonPassThrough.java), 2026-11-14
- **Field mappings:** see docs/mapping/BUSINESS_REGISTRY_mapper.md (31 feature, 2 payload, 3 ignored)
- **Format changes:** none; every emitted value is verbatim from the source (INCORPORATED moved from the root into a REGISTRATION_DATE feature object, value unchanged)
- **Renames:** none
- **Filters:** none
- **Corrections:** none
- **Records in / out / rejected:** 22 / 22 / 0
