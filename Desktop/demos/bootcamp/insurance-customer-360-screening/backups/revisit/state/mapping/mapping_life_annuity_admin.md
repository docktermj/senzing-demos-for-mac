# LIFE_ANNUITY_ADMIN mapping

The full specification, decisions and quality notes are in [life_annuity_admin_mapper.md](life_annuity_admin_mapper.md).

## Logic in one paragraph

Each source line is one record. DATA_SOURCE is set to LIFE_ANNUITY_ADMIN and RECORD_ID copied from the source.
RECORD_TYPE comes from the source's own RECORD_TYPE feature. Every other feature object is copied attribute by
attribute, verbatim; blank values and label-only objects are skipped. The ten contract fields go to the record root
as payload. The logic is the shared `FeatureListTransformer`; this source's program only holds its settings.

## Quality

Mapped output scores 62.6 (completeness 53.3, format consistency 81.3, no duplicate keys). Page:
`docs/visualizations/mapping_life_annuity_admin_quality.html`.

## How to run

From the project root:

```bash
src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_life_annuity_admin.java
src/scripts/sz-java.sh run transform.TransformLifeAnnuityAdmin data/raw/life_annuity_admin_extract.jsonl -o data/senzing-ready/life_annuity_admin.jsonl
```

Add `--sample N` to transform only the first N records. Validate with:

```bash
/opt/homebrew/bin/python3 src/scripts/mapping/sz_json_analyzer.py data/senzing-ready/life_annuity_admin.jsonl
```
