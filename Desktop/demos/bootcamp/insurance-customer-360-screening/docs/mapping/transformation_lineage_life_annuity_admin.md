# Transformation Lineage: LIFE_ANNUITY_ADMIN

## Source

- File: `data/raw/life_annuity_admin_extract.jsonl` (JSONL, 75 records, 38,343 bytes)
- SHA-256: `860ebee2d6161ee210cc2289e22b45cc62288cfa469a6ba30c7651f698e3a56f`
- Provenance: own

## Transformation program

- `src/transform/transform_life_annuity_admin.java` (class `transform.TransformLifeAnnuityAdmin`): this source's
  settings, run by the shared `src/transform/FeatureListTransformer.java`. Standard library plus `src/server/Json.java`.

## Output

- File: `data/senzing-ready/life_annuity_admin.jsonl` (75 records)
- SHA-256: `62d35b3b3a63cd4d9345f688dc1fa05bc46750a5f1f7029e67826dc562aff0c1`
- Sample: `data/mapping/life_annuity_admin_sample.jsonl` (first 25 records)

## Field mappings

See [life_annuity_admin_mapper.md](life_annuity_admin_mapper.md#field-mappings): 34 feature fields under their own
Senzing attribute names, 10 contract fields to payload, DATA_SOURCE/RECORD_ID/FEATURES container not emitted as features.

## Format changes

None. Every emitted value is the source value, unchanged (verbatim check: pass).

## Filters

- No records filtered: 75 in, 75 out.
- Dropped values: 2 label-only `PHONE_TYPE` objects (records 1098, 1100).

## Quality before and after

| | Records | Quality | Completeness | Format consistency | Duplicate keys |
|---|---|---|---|---|---|
| Raw source (Phase 1) | 75 | 63.0 | 53.7 | 81.5 | 0% |
| Mapped output | 75 | 62.6 | 53.3 | 81.3 | 0% |

The small drop comes from removing the two label-only phone objects. Mapping cannot add missing values.
