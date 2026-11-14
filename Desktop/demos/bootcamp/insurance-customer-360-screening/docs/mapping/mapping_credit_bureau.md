# CREDIT_BUREAU mapping

The full specification, decisions and quality notes are in [credit_bureau_mapper.md](credit_bureau_mapper.md).

## Logic in one paragraph

Each source line is one record. DATA_SOURCE is CREDIT_BUREAU and RECORD_ID is copied from the source. RECORD_TYPE comes
from the record's own RECORD_TYPE. Every feature object is copied verbatim; FILE_SINCE and FILE_STATUS go to the root as
payload. The source is already exactly in Senzing format, so the output is byte-identical to the input; the transformer
still runs so the file passes through the same checks as every other source.

## Quality

89.8 (completeness 87.2, format consistency 94.9, no duplicate keys). Page:
`docs/visualizations/mapping_credit_bureau_quality.html`.

## How to run

```bash
src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_credit_bureau.java
src/scripts/sz-java.sh run transform.TransformCreditBureau data/raw/credit_header.jsonl -o data/senzing-ready/credit_bureau.jsonl
```

Validate (the explanation covers the cross-source relationship):

```bash
/opt/homebrew/bin/python3 src/scripts/mapping/sz_json_analyzer.py --rel-explanation "anchor 3048 is the target of PC_POLICY_ADMIN 3050's pointer" data/senzing-ready/credit_bureau.jsonl
```
