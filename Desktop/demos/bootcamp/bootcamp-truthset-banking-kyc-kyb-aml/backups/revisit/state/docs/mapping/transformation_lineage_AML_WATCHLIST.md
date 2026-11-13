# Transformation Lineage: AML_WATCHLIST

## Source

- **File:** `data/raw/AML_WATCHLIST-regenerated.jsonl`
- **Format:** UTF-8 JSON Lines
- **Records:** 17
- **Profile:** 25 fields in one schema; all 17 records are PERSON
- **Source quality score:** 88.7

## Transformation

- **Program:** `src/transform/transform_AML_WATCHLIST.java`
- **Shared JSON reader/writer:** `src/transform/Jsonl.java` (Java standard library only)
- **Run:** `mkdir -p data/mapping/.build data/senzing-ready && javac -Xlint:all -d data/mapping/.build src/transform/Jsonl.java src/transform/transform_AML_WATCHLIST.java && java -cp data/mapping/.build transform_AML_WATCHLIST data/raw/AML_WATCHLIST-regenerated.jsonl -o data/senzing-ready/AML_WATCHLIST.jsonl`
- **Mapping specification:** `docs/mapping/AML_WATCHLIST_mapper.md`

One output record is emitted for each input record. The mapper derives the configured data-source value, copies the record ID, maps the nested source attributes to their validated feature families, and retains `CATEGORY`, `DATE`, and `STATUS` as root payload. Address and license attributes remain grouped with their source feature object. No records are filtered and source values are not normalized.

## Output

- **File:** `data/senzing-ready/AML_WATCHLIST.jsonl`
- **Format:** UTF-8 JSON Lines
- **Records:** 17
- **Record-count change:** none (17 input, 17 output)
- **Sample:** `data/mapping/AML_WATCHLIST_sample.jsonl`

## Quality and caveats

- The full output analyzer and verbatim check passed; routing found no dropped feature values.
- Source quality is 88.7: completeness 83.9%, format consistency 100.0%, duplicate rate 0.0%.
- SSN occurs on 2 records and driver's-license data on 1; EMPLOYER uniqueness is 29.4%.
- Mapping validation is static and does not establish matching behavior. The optional sandbox test load was skipped; the user chose to proceed to Data processing.
