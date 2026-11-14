# SIU_CASES mapping

The full specification, decisions and quality notes are in [siu_cases_mapper.md](siu_cases_mapper.md).

## Logic in one paragraph

Each source line is one record. DATA_SOURCE is SIU_CASES and RECORD_ID is copied from the source. RECORD_TYPE comes from
the record's own RECORD_TYPE. Every feature object is copied verbatim; the six case fields go to the root as payload. The
source is already exactly in Senzing format, so the output is byte-identical to the input.

## Quality

62.1 (completeness 48.7, format consistency 92.0, no duplicate keys). Page:
`docs/visualizations/mapping_siu_cases_quality.html`.

## How to run

```bash
src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_siu_cases.java
src/scripts/sz-java.sh run transform.TransformSiuCases data/raw/siu_cases.jsonl -o data/senzing-ready/siu_cases.jsonl
```

Validate:

```bash
/opt/homebrew/bin/python3 src/scripts/mapping/sz_json_analyzer.py data/senzing-ready/siu_cases.jsonl
```
