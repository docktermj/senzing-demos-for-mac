# Transformation Lineage: BANK_CUSTOMERS

## Source

- **File:** `data/raw/BANK_CUSTOMERS-regenerated.jsonl`
- **Format:** UTF-8 JSON Lines
- **Records:** 120 (114 PERSON and 6 ORGANIZATION)
- **Profile:** 33 fields
- **Source quality score:** 81.9

## Transformation

- **Program:** `data/mapping/BANK_CUSTOMERS_mapper.java`
- **JSON-P dependency:** `src/server/target/dependency/javax.json-1.1.4.jar`
- **Run:** `javac -Xlint:all -cp src/server/target/dependency/javax.json-1.1.4.jar -d data/mapping/.build data/mapping/BANK_CUSTOMERS_mapper.java && java -cp data/mapping/.build:src/server/target/dependency/javax.json-1.1.4.jar BANK_CUSTOMERS_mapper data/raw/BANK_CUSTOMERS-regenerated.jsonl -o data/senzing-ready/BANK_CUSTOMERS.jsonl`
- **Mapping specification:** `docs/mapping/BANK_CUSTOMERS_mapper.md`

The transformation emits one mapped record per source record. It derives the configured data-source value, copies the record ID, maps nested attributes according to record type, preserves address grouping with the structured-address-first rule, and retains `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` as root payload. It does not filter records or normalize source values.

## Output

- **File:** `data/senzing-ready/BANK_CUSTOMERS.jsonl`
- **Format:** UTF-8 JSON Lines
- **Records:** 120 output records
- **File size:** 63,852 bytes
- **Record-count change:** none (120 input, 120 output)
- **Sample:** `data/mapping/BANK_CUSTOMERS_sample.jsonl`

## Quality and caveats

- The full analyzer and verbatim checks passed; routing found no dropped source value-entries.
- Source quality is 81.9: completeness 74.7%, format consistency 98.5%, duplicate rate 0.0%.
- Four ORGANIZATION names were flagged for manual type review; EMAIL uniqueness is 73.3% and identifier coverage is sparse.
- Static checks do not establish matching behavior.
