# BUSINESS_REGISTRY Mapping

## Source and target

- Source: `data/raw/BUSINESS_REGISTRY-regenerated.jsonl` (22 records)
- Schema: `BUSINESS_REGISTRY-regenerated` (one master schema; 16 PERSON and 6 ORGANIZATION records)
- Output data source: `BUSINESS_REGISTRY`
- Record key: source `RECORD_ID`
- Mapper: `src/transform/transform_BUSINESS_REGISTRY.java`
- Output: `data/senzing-ready/BUSINESS_REGISTRY.jsonl`
- Support schemas: none

## Mapping logic

The mapper preserves the nested PERSON/ORGANIZATION discriminator and maps type-specific names. Root `DATE` is mapped to `REGISTRATION_DATE` only for ORGANIZATION records, as selected by the bootcamper. Structured address components are preferred when present; `ADDR_FULL` is used only when structured parts are absent. Phone qualifiers remain with phone numbers, and relationship attributes are preserved in their existing feature objects. `CATEGORY` and `STATUS` remain root payload.

## Quality and validation

- Source quality: 80.6 (72.2 completeness, 100.0 format consistency, 0.0 duplicate rate).
- The mapper emitted all 22 records. The verbatim check passed and routing found no dropped source value-entries.
- The analyzer reported one structural finding on record 2151, where two distinct addresses share the `REGISTERED` type. The bootcamper chose to preserve both source addresses unchanged, and the finding remains documented.
- Static checks do not establish actual entity-matching behavior.

## How to run

From the repository root:

```sh
mkdir -p data/mapping/.build data/senzing-ready
javac -Xlint:all -d data/mapping/.build \
  src/transform/Jsonl.java src/transform/transform_BUSINESS_REGISTRY.java
java -cp data/mapping/.build transform_BUSINESS_REGISTRY \
  data/raw/BUSINESS_REGISTRY-regenerated.jsonl \
  -o data/senzing-ready/BUSINESS_REGISTRY.jsonl
```

See `docs/mapping/BUSINESS_REGISTRY_mapper.md` for the field-by-field specification and `docs/mapping/transformation_lineage_BUSINESS_REGISTRY.md` for input/output provenance.
