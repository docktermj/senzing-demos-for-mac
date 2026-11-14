# Transformation Lineage: PC_POLICY_ADMIN

## Source

- File: `data/raw/pc_policy_admin_extract.jsonl` (JSONL, 104 records, 58,104 bytes)
- SHA-256: `78a9a7ccc55802e35063b56b30123460fc14724169826da289b91308966417aa`
- Provenance: own (collected in Data collection from the bootcamper's raw-data folder)

## Transformation program

- `src/transform/transform_pc_policy_admin.java` (class `transform.TransformPcPolicyAdmin`): this source's settings,
  run by the shared `src/transform/FeatureListTransformer.java`, standard library only plus the project's
  `src/server/Json.java` reader. Built from the mapping that `mapping_workflow`
  validated (step 3) and approved (step 4).

## Output

- File: `data/senzing-ready/pc_policy_admin.jsonl` (104 records)
- SHA-256: `284d2385c352c42cfa50a7de83d53baf906116056d2f00199779243c2822c0af`
- Sample: `data/mapping/pc_policy_admin_sample.jsonl` (first 25 records)

## Field mappings

See [pc_policy_admin_mapper.md](pc_policy_admin_mapper.md#field-mappings). In short: 35 feature fields copied
under their own Senzing attribute names, 10 policy fields to payload, DATA_SOURCE/RECORD_ID/FEATURES container
not emitted as features.

## Format changes

None. Every emitted value is the source value, unchanged (verbatim check: pass). Mixed date, gender and phone
formats are left for Senzing to standardize.

## Filters

- No records filtered: 104 in, 104 out.
- Dropped values: 2 label-only `PHONE_TYPE` objects (records 1097, 1099). Blank values are dropped (none occurred).

## Typing changes

- Record 3025: no RECORD_TYPE in the source → ORGANIZATION (bootcamper decision); its NAME_FULL "WONDERLAND"
  emitted as NAME_ORG.

## Quality before and after

| | Records | Quality | Completeness | Format consistency | Duplicate keys |
|---|---|---|---|---|---|
| Raw source (Phase 1) | 104 | 67.1 | 57.1 | 88.4 | 0% |
| Mapped output | 104 | 66.8 | 56.8 | 88.3 | 0% |

The small drop comes from removing the two label-only phone objects and retyping 3025. Mapping cannot add
missing values.
