# POLICYHOLDERS Transformation Lineage

| Stage | Artifact | Records | Notes |
|---|---|---:|---|
| Original collected input | `data/raw/POLICYHOLDERS.jsonl` | 120 | Retained unchanged |
| Formatting-normalized input | `data/raw/POLICYHOLDERS-improved.jsonl` | 120 | Existing source copy; no semantic value changes |
| Mapping implementation | `src/transform/transform_POLICYHOLDERS.java` | — | Uses shared `src/transform/JsonSupport.java` |
| QA output | `data/mapping/POLICYHOLDERS_quality.jsonl` | 120 | Analyzer and value-preservation checks |
| Load-ready output | `data/senzing-ready/POLICYHOLDERS.jsonl` | 120 | Generated; not loaded |

## Value and structure changes

- Filters: none; all 120 input records were transformed.
- `DATA_SOURCE` is replaced with the registered value `POLICYHOLDERS`.
- `RECORD_ID` is preserved unchanged.
- Root payload fields remain payload; nested feature attributes are grouped into target
  feature objects without combining distinct source feature objects.
- A `PHONE_TYPE` without `PHONE_NUMBER` is retained under the payload name
  `SOURCE_PHONE_QUALIFIER`.
- Blank feature values are omitted. No missing value is imputed, and source-provided
  `RECORD_TYPE` values are preserved.

The iteration requested at the quality review found no further safe mechanical normalization:
duplicate rate is 0.0%, and remaining completeness gaps cannot be corrected without source
evidence. The source quality score remains 51.5% (33.2% completeness, 92.9% format
consistency). Mapping adds a Senzing-ready structure but does not raise the source quality score.
The before/after record counts are 120 and 120. Analyzer warnings remain advisory; the four
type-review flags did not justify changing the source types. No records were loaded.
