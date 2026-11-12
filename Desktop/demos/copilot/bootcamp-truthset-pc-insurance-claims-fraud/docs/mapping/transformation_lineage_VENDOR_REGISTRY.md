# VENDOR_REGISTRY Transformation Lineage

| Stage | Artifact | Records | Notes |
|---|---|---:|---|
| Original collected input | `data/raw/VENDOR_REGISTRY.jsonl` | 22 | Retained unchanged |
| Formatting-normalized input | `data/raw/VENDOR_REGISTRY-improved.jsonl` | 22 | Address-country casing, dates, phone punctuation and whitespace normalized; one display-name/address email wrapper reduced to its source-supported address |
| Mapping implementation | `src/transform/transform_VENDOR_REGISTRY.java` | — | Uses shared `src/transform/JsonSupport.java` |
| QA output | `data/mapping/VENDOR_REGISTRY_quality.jsonl` | 25 | 22 source records plus three deduplicated embedded employers |
| Load-ready output | `data/senzing-ready/VENDOR_REGISTRY.jsonl` | 25 | Generated; not loaded |

## Value and structure changes

- All 22 input records were transformed; no source record was filtered.
- Parent `RECORD_ID` values and source relationship values are preserved.
- `DATA_SOURCE` is replaced with the registered value `VENDOR_REGISTRY`.
- Root `CATEGORY`, generic `DATE`, and `STATUS` remain payload; nested attributes are routed
  to matching feature families.
- Three distinct employer names are emitted as embedded organization records with
  deterministic IDs and matching relationship anchors/pointers.
- The improved input changes one `EMAIL_ADDRESS` string's display-name/address formatting;
  the enclosed email address is source-supported, and the original collected file remains
  unchanged. No missing values are imputed.
- The output contains 25 records from 22 source records. Structural validation reported zero
  errors; verbatim fidelity and routing checks passed.
- The source quality score is 57.0% (38.5% completeness, 100.0% format consistency,
  0.0% duplicate rate). No records were loaded.
