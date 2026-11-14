# Transformation Lineage: SANCTIONS_PEP

## Source

- File: `data/raw/sanctions_pep.jsonl` (JSONL, 4 records, 1,648 bytes)
- SHA-256: `798076dadebdb45f0b77f66610ed1589ee60bbb63612b5d97f8bc68de630d739`
- Provenance: own

## Transformation program

- `src/transform/transform_sanctions_pep.java` (class `transform.TransformSanctionsPep`), run by the shared
  `src/transform/FeatureListTransformer.java`.

## Output

- File: `data/senzing-ready/sanctions_pep.jsonl` (4 records)
- SHA-256: `53880c24b4a82f58681f6ba373f99c6e79398fbe068830c4bb0e25731f78816e`
- Sample: `data/mapping/sanctions_pep_sample.jsonl` (all 4 records)

## Field mappings

See [sanctions_pep_mapper.md](sanctions_pep_mapper.md#field-mappings).

## Format changes

None to values. The file encoding of non-Latin characters changes from JSON `\u` escapes to UTF-8; the parsed records are
identical.

## Filters

None: 4 in, 4 out, no values dropped.

## Quality before and after

| | Records | Quality | Completeness | Format consistency | Duplicate keys |
|---|---|---|---|---|---|
| Raw source (Phase 1) | 4 | 71.9 | 68.8 | 75.0 | 0% |
| Mapped output | 4 | 71.9 | 68.8 | 75.0 | 0% |
