# Transformation Lineage: CREDIT_BUREAU

## Source

- File: `data/raw/credit_header.jsonl` (JSONL, 30 records, 13,029 bytes)
- SHA-256: `6de2f56fdc6fe69719771c16a1b26859f1f0a120bd6a340efcc112795bf520f6`
- Provenance: own

## Transformation program

- `src/transform/transform_credit_bureau.java` (class `transform.TransformCreditBureau`), run by the shared
  `src/transform/FeatureListTransformer.java`.

## Output

- File: `data/senzing-ready/credit_bureau.jsonl` (30 records)
- SHA-256: `6de2f56fdc6fe69719771c16a1b26859f1f0a120bd6a340efcc112795bf520f6` — **identical to the source**: the raw file
  was already exactly in Senzing record format, so the transformation changed nothing.
- Sample: `data/mapping/credit_bureau_sample.jsonl` (first 25 records)

## Field mappings

See [credit_bureau_mapper.md](credit_bureau_mapper.md#field-mappings).

## Format changes

None.

## Filters

None: 30 in, 30 out, no values dropped.

## Quality before and after

| | Records | Quality | Completeness | Format consistency | Duplicate keys |
|---|---|---|---|---|---|
| Raw source (Phase 1) | 30 | 89.8 | 87.2 | 94.9 | 0% |
| Mapped output | 30 | 89.8 | 87.2 | 94.9 | 0% |
