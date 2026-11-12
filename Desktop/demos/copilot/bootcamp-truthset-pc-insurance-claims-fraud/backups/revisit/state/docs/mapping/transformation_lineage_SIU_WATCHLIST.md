# SIU_WATCHLIST Transformation Lineage

| Stage | Artifact | Records | Notes |
|---|---|---:|---|
| Original collected input | `data/raw/SIU_WATCHLIST.jsonl` | 17 | Retained unchanged |
| Formatting-normalized input | `data/raw/SIU_WATCHLIST-improved.jsonl` | 17 | Improved copy; ambiguous values remain unchanged |
| Mapping implementation | `src/transform/transform_SIU_WATCHLIST.java` | — | Uses shared `src/transform/JsonSupport.java` |
| QA output | `data/mapping/SIU_WATCHLIST_output.jsonl` | 17 | Analyzer and verbatim checks passed |
| Load-ready output | `data/senzing-ready/SIU_WATCHLIST.jsonl` | 17 | Same validated output; not loaded |

## Value and structure changes

- All 17 input records are emitted; no record is filtered.
- Source `RECORD_ID` and per-record `FEATURES.RECORD_TYPE` values are preserved.
- Root `DATA_SOURCE` changes from `WATCHLIST` to the registered `SIU_WATCHLIST` code.
- Source name, address, identifier, and contact values are grouped by their mapped feature
  family and otherwise passed through verbatim. Full names in non-Latin scripts are unchanged.
- Root `CATEGORY`, generic `DATE`, and `STATUS` remain payload. No values are imputed.
- The analyzer reported the expected 17 PERSON records and all mapped feature families.
  Verbatim fidelity passed; routing omissions were the three intentional payload fields.
- The quality score is 56.5% (39.5% completeness, 95.3% format consistency, 0.0% duplicates).
  No records have been loaded.
