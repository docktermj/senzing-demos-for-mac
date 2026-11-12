# BANK_CUSTOMERS Mapping

## Source and target

- Source: `data/raw/BANK_CUSTOMERS-regenerated.jsonl` (120 records)
- Schema: `BANK_CUSTOMERS-regenerated` (one master schema; 114 PERSON and 6 ORGANIZATION records)
- Output data source: `BANK_CUSTOMERS`
- Record key: source `RECORD_ID`, copied verbatim
- Mapper: `data/mapping/BANK_CUSTOMERS_mapper.java`
- Output: `data/senzing-ready/BANK_CUSTOMERS.jsonl`
- Support schemas and joins: none

## Mapping logic

The mapper keeps `RECORD_TYPE` in its own feature object and maps name fields according to PERSON or ORGANIZATION type. It maps demographic, address, phone, email, driver's-license, passport, SSN, and national-ID attributes into their respective feature objects. Structured address parts take precedence over `ADDR_FULL` within the same source feature object. `PHONE_TYPE` is emitted only when its phone number is present. Root `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` remain payload. Source values are copied verbatim.

## Quality and validation

- Source quality: 81.9 (74.7 completeness, 98.5 format consistency, 0.0 duplicate rate).
- The mapper emitted all 120 records. The analyzer and verbatim check passed; routing found no dropped source value-entries.
- The analyzer flagged four ORGANIZATION names for manual type review. EMAIL uniqueness is 73.3%, and identifier families have low population.
- These are source-quality or type-review findings, not evidence that static validation has tested actual matching.

## How to run

From the repository root:

```sh
javac -Xlint:all -cp src/server/target/dependency/javax.json-1.1.4.jar \
  -d data/mapping/.build data/mapping/BANK_CUSTOMERS_mapper.java
java -cp data/mapping/.build:src/server/target/dependency/javax.json-1.1.4.jar \
  BANK_CUSTOMERS_mapper data/raw/BANK_CUSTOMERS-regenerated.jsonl \
  -o data/senzing-ready/BANK_CUSTOMERS.jsonl
```

The sample is `data/mapping/BANK_CUSTOMERS_sample.jsonl`. See `docs/mapping/BANK_CUSTOMERS_mapper.md` for the field-by-field specification and `docs/mapping/transformation_lineage_BANK_CUSTOMERS.md` for input/output provenance.
