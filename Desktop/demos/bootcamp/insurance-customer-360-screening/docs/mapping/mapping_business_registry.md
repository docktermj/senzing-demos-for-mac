# BUSINESS_REGISTRY mapping

The full specification, decisions and quality notes are in [business_registry_mapper.md](business_registry_mapper.md).

## Logic in one paragraph

Each source line is one record. DATA_SOURCE is BUSINESS_REGISTRY and RECORD_ID is copied from the source. RECORD_TYPE
comes from the record's own RECORD_TYPE. Every feature object is copied verbatim; the root INCORPORATED year is emitted
as a REGISTRATION_DATE feature; REGISTRY_ROLE and REGISTRY_STATUS go to the root as payload.

## Quality

67.8 (completeness 58.3, format consistency 87.9, no duplicate keys). Page:
`docs/visualizations/mapping_business_registry_quality.html`.

## How to run

```bash
src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_business_registry.java
src/scripts/sz-java.sh run transform.TransformBusinessRegistry data/raw/business_registry.jsonl -o data/senzing-ready/business_registry.jsonl
```

Validate:

```bash
/opt/homebrew/bin/python3 src/scripts/mapping/sz_json_analyzer.py data/senzing-ready/business_registry.jsonl
```
