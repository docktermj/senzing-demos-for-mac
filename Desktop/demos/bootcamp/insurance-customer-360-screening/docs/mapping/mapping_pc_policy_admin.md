# PC_POLICY_ADMIN mapping

The full field-by-field specification, decisions and quality notes are in
[pc_policy_admin_mapper.md](pc_policy_admin_mapper.md). This page says how to run the transformation.

## Logic in one paragraph

Each source line is one record. DATA_SOURCE is set to PC_POLICY_ADMIN and RECORD_ID copied from the source.
RECORD_TYPE comes from the source's own RECORD_TYPE feature (record 3025 is typed ORGANIZATION by decision).
Every other feature object is copied attribute by attribute, verbatim; blank values and label-only objects are
skipped. The ten policy fields are copied to the record root as payload.

## Quality

Mapped output scores 66.8 (completeness 56.8, format consistency 88.3, no duplicate keys). Page:
`docs/visualizations/mapping_pc_policy_admin_quality.html`.

## How to run

From the project root:

```bash
src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_pc_policy_admin.java
src/scripts/sz-java.sh run transform.TransformPcPolicyAdmin data/raw/pc_policy_admin_extract.jsonl -o data/senzing-ready/pc_policy_admin.jsonl
```

Add `--sample N` to transform only the first N records. The program prints READ, WRITTEN, SKIPPED, the
record-type counts and anything it dropped.

Validate the output with the mapping workflow's analyzer:

```bash
/opt/homebrew/bin/python3 src/scripts/mapping/sz_json_analyzer.py data/senzing-ready/pc_policy_admin.jsonl
```
