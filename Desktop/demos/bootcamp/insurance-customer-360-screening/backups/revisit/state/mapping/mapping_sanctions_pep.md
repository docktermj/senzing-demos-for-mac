# SANCTIONS_PEP mapping

The full specification, decisions and quality notes are in [sanctions_pep_mapper.md](sanctions_pep_mapper.md).

## Logic in one paragraph

Each source line is one record. DATA_SOURCE is SANCTIONS_PEP and RECORD_ID is copied from the source. RECORD_TYPE comes
from the record's own RECORD_TYPE. Every feature object is copied verbatim, names in every script included; the six list
fields go to the root as payload.

## Quality

71.9 (completeness 68.8, format consistency 75.0, no duplicate keys). Page:
`docs/visualizations/mapping_sanctions_pep_quality.html`.

## How to run

```bash
src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_sanctions_pep.java
src/scripts/sz-java.sh run transform.TransformSanctionsPep data/raw/sanctions_pep.jsonl -o data/senzing-ready/sanctions_pep.jsonl
```

Validate:

```bash
/opt/homebrew/bin/python3 src/scripts/mapping/sz_json_analyzer.py data/senzing-ready/sanctions_pep.jsonl
```
