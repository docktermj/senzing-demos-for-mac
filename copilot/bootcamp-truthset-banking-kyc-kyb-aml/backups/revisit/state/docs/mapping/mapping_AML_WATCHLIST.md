# AML_WATCHLIST Mapping

## Source and target

- Source: `data/raw/AML_WATCHLIST-regenerated.jsonl` (17 records)
- Schema: `AML_WATCHLIST-regenerated` (one PERSON master schema)
- Output data source: `AML_WATCHLIST`
- Record key: source `RECORD_ID`, copied verbatim
- Mapper: `src/transform/transform_AML_WATCHLIST.java`
- Output: `data/senzing-ready/AML_WATCHLIST.jsonl`
- Support schemas and joins: none

## Mapping logic

The mapper requires the configured source label, a string record ID, a FEATURES array, and a PERSON record type. It emits the record type in its own feature object, groups nested attributes into NAME, DOB, ADDRESS, DRLIC, EMAIL, PHONE, EMPLOYER, GENDER, and SSN features, and keeps each input feature object's values together. Root `CATEGORY`, `DATE`, and `STATUS` remain payload. Source strings are copied without normalization; unexpected fields and orphan address/license qualifiers fail explicitly.

## Quality and validation

- Source quality: 88.7 (83.9 completeness, 100.0 format consistency, 0.0 duplicate rate).
- All 17 input records produce output records. The analyzer and verbatim checks passed, and routing found no dropped feature values.
- SSN is present on 2/17 records; driver's-license data is present on 1/17; EMPLOYER uniqueness is 29.4%.
- These identifier gaps are source limitations. Static checks do not validate actual entity matching.

## How to run

From the repository root:

```sh
mkdir -p data/mapping/.build data/senzing-ready
javac -Xlint:all -d data/mapping/.build \
  src/transform/Jsonl.java src/transform/transform_AML_WATCHLIST.java
java -cp data/mapping/.build transform_AML_WATCHLIST \
  data/raw/AML_WATCHLIST-regenerated.jsonl \
  -o data/senzing-ready/AML_WATCHLIST.jsonl
```

The three-record sample is `data/mapping/AML_WATCHLIST_sample.jsonl`. The per-source field specification is `docs/mapping/AML_WATCHLIST_mapper.md`; transformation provenance is in `docs/mapping/transformation_lineage_AML_WATCHLIST.md`.
