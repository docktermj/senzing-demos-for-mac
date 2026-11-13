# VENDOR_REGISTRY Transformation

## Purpose

Transform the normalized VENDOR_REGISTRY JSONL source into Senzing JSONL while preserving
source record IDs, relationship values, and nonblank feature/payload values. The mapper
replaces the source label with the registered code, routes source fields to features or
payload, and creates deduplicated embedded employer organizations.

## Inputs and outputs

- Input: `data/raw/VENDOR_REGISTRY-improved.jsonl` (22 records)
- Original input: `data/raw/VENDOR_REGISTRY.jsonl`
- Mapper: `src/transform/transform_VENDOR_REGISTRY.java`
- Shared support: `src/transform/JsonSupport.java`
- Sample output: `data/mapping/VENDOR_REGISTRY_mapper_sample.jsonl` (10 source records plus 2 embedded employers)
- QA output: `data/mapping/VENDOR_REGISTRY_quality.jsonl` (25 records)
- Load-ready output: `data/senzing-ready/VENDOR_REGISTRY.jsonl` (25 records)
- Mapping specification: `docs/mapping/VENDOR_REGISTRY_mapper.md`
- Full mapping configuration: `docs/mapping/VENDOR_REGISTRY_mapping_spec.json`

## Transformation decisions

`DATA_SOURCE` is set to `VENDOR_REGISTRY`; parent `RECORD_ID` and source relationship
domains/keys are preserved. `FEATURES.RECORD_TYPE` remains authoritative, with name fields
selected by type. The root `CATEGORY`, `DATE`, and `STATUS` remain payload. Nine PERSON
records contain three distinct employer values; those are emitted as embedded ORGANIZATION
records, deduplicated by exact employer string and linked with the `VENDOR_EMPLOYER` domain.

The improved source normalizes one display-name/address wrapper in `EMAIL_ADDRESS` to the
enclosed address. The inner address is independently present in the source; the original
collected file is unchanged. No missing value is imputed, and source-provided record types
are not changed based on name-shape heuristics.

## Iteration and validation

The quality score changed from 56.4% to 57.0% after the email-format correction:
completeness 38.5%, format consistency 100.0%, duplicate rate 0.0%. The remaining
completeness gap cannot be filled without source evidence. The analyzer completed with zero
structural errors and eight advisory warnings; seven source-labeled ORGANIZATION records
were flagged for name/type review, and their explicit types were retained. Verbatim-fidelity
and routing checks passed. No records have been loaded.

## Reproducibility

```sh
javac -Xlint:all -d /tmp/vendor-registry-transform-build \
  src/transform/JsonSupport.java src/transform/transform_VENDOR_REGISTRY.java
java -cp /tmp/vendor-registry-transform-build TransformVendorRegistry \
  data/raw/VENDOR_REGISTRY-improved.jsonl -o data/senzing-ready/VENDOR_REGISTRY.jsonl
```
