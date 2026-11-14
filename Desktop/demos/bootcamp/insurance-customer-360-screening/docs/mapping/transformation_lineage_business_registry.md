# Transformation Lineage: BUSINESS_REGISTRY

## Source

- File: `data/raw/business_registry.jsonl` (JSONL, 22 records, 9,969 bytes)
- SHA-256: `1d336f533a07aac96df05335e764c84f3db3e87cba18c57623366b7259490192`
- Provenance: own

## Transformation program

- `src/transform/transform_business_registry.java` (class `transform.TransformBusinessRegistry`), run by the shared
  `src/transform/FeatureListTransformer.java` (root-field feature option added for this source).

## Output

- File: `data/senzing-ready/business_registry.jsonl` (22 records)
- SHA-256: `1a8489046d3e46b5100bcfea26ad1ee38a2b54ff2acd0d086d83e55e3f230388`
- Sample: `data/mapping/business_registry_sample.jsonl` (all 22 records; the source has fewer than 25)

## Field mappings

See [business_registry_mapper.md](business_registry_mapper.md#field-mappings).

## Format changes

None to values. One structural change: the root `INCORPORATED` value is moved into the FEATURES list as
`{"REGISTRATION_DATE": "<year>"}` on the 6 organization records.

## Filters

None: 22 in, 22 out, no values dropped.

## Quality before and after

| | Records | Quality | Completeness | Format consistency | Duplicate keys |
|---|---|---|---|---|---|
| Raw source (Phase 1) | 22 | 67.8 | 58.3 | 87.9 | 0% |
| Mapped output | 22 | 67.8 | 58.3 | 87.9 | 0% |
