# Transformation Lineage: BUSINESS_REGISTRY

## Source

- **File:** `data/raw/BUSINESS_REGISTRY-regenerated.jsonl`
- **Format:** UTF-8 JSON Lines
- **Records:** 22 source records
- **Profile:** 36 distinct fields; 16 PERSON and 6 ORGANIZATION records
- **Source quality score:** 80.6

## Transformation

- **Program:** `src/transform/transform_BUSINESS_REGISTRY.java`
- **Shared JSON reader/writer:** `src/transform/Jsonl.java` (Java standard library only)
- **Run:** `mkdir -p data/mapping/.build && javac -Xlint:all -d data/mapping/.build src/transform/Jsonl.java src/transform/transform_BUSINESS_REGISTRY.java && java -cp data/mapping/.build transform_BUSINESS_REGISTRY data/raw/BUSINESS_REGISTRY-regenerated.jsonl -o data/senzing-ready/BUSINESS_REGISTRY.jsonl`
- **Mapping specification:** `docs/mapping/BUSINESS_REGISTRY_mapper.md`

The transformation emits one mapped record for each source record. It derives the data-source
constant, copies `RECORD_ID`, maps nested attributes into the approved feature families, maps root
`DATE` to `REGISTRATION_DATE` for organizations only, and retains `CATEGORY` and `STATUS` as
root payload. It does not normalize source values or filter records.

## Output

- **File:** `data/senzing-ready/BUSINESS_REGISTRY.jsonl`
- **Format:** UTF-8 JSON Lines
- **Records:** 22 output records
- **Record-count change:** none (22 input, 22 output)

## Quality and known caveats

- The verbatim checker passed and the routing report found no dropped source value-entries.
- The analyzer reports one structural finding on record `2151`, where two distinct addresses share
  the source usage type `REGISTERED`. Per the bootcamper's choice, both entries remain unchanged;
  the analyzer finding is documented and remains unresolved.
- The analyzer labels `TAX_ID_TYPE` unrecognized, although the current Entity Specification
  includes it. The release notes identify the attribute as new in 4.4.0, while the saved SDK
  checkpoint records 4.3.2; confirm runtime/configuration compatibility before load.
- `EMPLOYER` uniqueness and identifier coverage are low. No source fields were dropped, and the
  report makes no claim that entity matching has been semantically validated.
