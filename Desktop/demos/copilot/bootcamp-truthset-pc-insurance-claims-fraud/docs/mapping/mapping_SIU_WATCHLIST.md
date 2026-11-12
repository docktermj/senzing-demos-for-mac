# SIU_WATCHLIST Transformation

## Purpose and files

Transform the normalized SIU_WATCHLIST JSONL into load-ready JSONL while preserving source
record IDs and every emitted feature or payload value. The mapper changes only the root
`DATA_SOURCE` to the registered code.

- Original input: `data/raw/SIU_WATCHLIST.jsonl`
- Mapping input: `data/raw/SIU_WATCHLIST-improved.jsonl` (17 records)
- Mapper: `src/transform/transform_SIU_WATCHLIST.java`
- Shared support: `src/transform/JsonSupport.java`
- Sample output: `data/mapping/SIU_WATCHLIST_sample.jsonl` (17 records)
- QA output: `data/mapping/SIU_WATCHLIST_output.jsonl` (17 records)
- Load-ready output: `data/senzing-ready/SIU_WATCHLIST.jsonl` (17 records)
- Mapping specification: `docs/mapping/SIU_WATCHLIST_mapper.md`
- Machine-readable field map: `docs/mapping/SIU_WATCHLIST_mapping_spec.json`

## Transformation decisions

The source contains one master schema of PERSON records keyed by `RECORD_ID`; it has no
child, lookup, or relationship tables. `FEATURES.RECORD_TYPE` is carried to the root for
each record. Name parts supplied separately are grouped into one `NAME` feature, while
`NAME_FULL` is kept as a separate whole-value name feature. Address and driver's-license
attributes are grouped by feature family. Sparse fields are omitted when absent.

The source's Arabic and Cyrillic name strings are preserved in UTF-8 without transliteration.
Root `CATEGORY`, generic `DATE`, and `STATUS` are retained as payload. No missing values are
imputed, and source dates, addresses, identifiers, and contact values are not rewritten.

## Validation

The analyzer validated 17 output records and reported all mapped feature families.
Verbatim fidelity passed. The routing report identified only `CATEGORY`, `DATE`, and
`STATUS` as non-feature values; each remains in the output payload. Sparse DRLIC, SSN,
PHONE, and EMPLOYER fields produced advisory warnings consistent with the source profile.
No records have been loaded.

## Reproducibility

```sh
javac -Xlint:all -d data/mapping/.build \
  src/transform/JsonSupport.java src/transform/transform_SIU_WATCHLIST.java
java -cp data/mapping/.build TransformSiuWatchlist \
  data/raw/SIU_WATCHLIST-improved.jsonl -o data/senzing-ready/SIU_WATCHLIST.jsonl
```
