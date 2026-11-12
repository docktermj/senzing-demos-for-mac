# POLICYHOLDERS Transformation

## Purpose

Transform the normalized POLICYHOLDERS JSONL source into Senzing JSONL while preserving the
source record identifiers and nonblank feature and payload values. The mapper changes the
registered data-source code and reorganizes source fields into target feature families; it
does not impute values or reclassify entities.

## Inputs and outputs

- Input: `data/raw/POLICYHOLDERS-improved.jsonl` (120 records)
- Original input: `data/raw/POLICYHOLDERS.jsonl`
- Mapper: `src/transform/transform_POLICYHOLDERS.java`
- Shared support: `src/transform/JsonSupport.java`
- Load-ready output: `data/senzing-ready/POLICYHOLDERS.jsonl` (120 records)
- QA output: `data/mapping/POLICYHOLDERS_quality.jsonl` (120 records)
- Per-source map: `docs/mapping/POLICYHOLDERS_mapper.md`
- Detailed field map: `docs/mapping/POLICYHOLDERS_mapping_spec.json`

## Transformation decisions

`DATA_SOURCE` is set to the registered code `POLICYHOLDERS`; `RECORD_ID` is retained. The
root fields `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` remain payload. `FEATURES.RECORD_TYPE`
is preserved as the target record-type discriminator. Nested entity features are mapped to
their matching families, with person and organization name fields selected by the source
record type.

Source feature objects remain separate. A phone-type-only value is retained as the
non-feature payload `SOURCE_PHONE_QUALIFIER`. No source-provided entity type is changed based
only on analyzer heuristics.

## Iteration and validation

The requested iteration rechecked the fixed Truth Set source. No additional safe formatting
or duplicate corrections were available, and missing values were not filled. The quality
score therefore remains 51.5%: completeness 33.2%, format consistency 92.9%, and duplicate
rate 0.0%.

The all-record analyzer run reported advisory sparsity and type-review warnings, with no
blocking structural errors. Four source-labeled ORGANIZATION records remain flagged for
review; the explicit source types were retained. No records were loaded.

## Reproducibility

From the repository root:

```sh
javac -Xlint:all -d /tmp/policyholders-transform-build \
  src/transform/JsonSupport.java src/transform/transform_POLICYHOLDERS.java
java -cp /tmp/policyholders-transform-build TransformPolicyholders \
  data/raw/POLICYHOLDERS-improved.jsonl data/senzing-ready/POLICYHOLDERS.jsonl
```
