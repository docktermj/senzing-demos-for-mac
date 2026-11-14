# Transformation Lineage: SIU_CASES

## Source

- File: `data/raw/siu_cases.jsonl` (JSONL, 13 records, 6,032 bytes)
- SHA-256: `9a4ec1f697cecb624d13f876e25c2e3fb6f1aa9a411f55de50538d778b9d690c`
- Provenance: own

## Transformation program

- `src/transform/transform_siu_cases.java` (class `transform.TransformSiuCases`), run by the shared
  `src/transform/FeatureListTransformer.java`.

## Output

- File: `data/senzing-ready/siu_cases.jsonl` (13 records)
- SHA-256: `9a4ec1f697cecb624d13f876e25c2e3fb6f1aa9a411f55de50538d778b9d690c` — **identical to the source**: the raw file
  was already exactly in Senzing record format.
- Sample: `data/mapping/siu_cases_sample.jsonl` (all 13 records)

## Field mappings

See [siu_cases_mapper.md](siu_cases_mapper.md#field-mappings).

## Format changes

None.

## Filters

None: 13 in, 13 out, no values dropped.

## Quality before and after

| | Records | Quality | Completeness | Format consistency | Duplicate keys |
|---|---|---|---|---|---|
| Raw source (Phase 1) | 13 | 62.1 | 48.7 | 92.0 | 0% |
| Mapped output | 13 | 62.1 | 48.7 | 92.0 | 0% |
