# Schema Hints

## POLICYHOLDERS-improved
- Source: `data/raw/POLICYHOLDERS-improved.jsonl`; mapper language: Java.
- JSONL records have root `DATA_SOURCE`, `RECORD_ID`, and a `FEATURES` array of feature objects; optional root fields are `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS`.
- `FEATURES.RECORD_TYPE` is present in all 120 records: 114 PERSON and 6 ORGANIZATION.
- Root-field patterns vary within PERSON records: 90 have `AMOUNT`/`DATE`/`STATUS`, 3 have `CATEGORY`/`DATE`/`STATUS`, and 21 have none; all 6 ORGANIZATION records have `CATEGORY`/`DATE`/`STATUS`.
- Each source `FEATURES` object contains one feature family. Two ORGANIZATION records have full and parsed address objects with the same `ADDR_TYPE`; map those as separate objects and never combine `ADDR_FULL` with parsed parts.
- Four source objects contain `PHONE_TYPE` without `PHONE_NUMBER`; preserve those values as the non-matching `SOURCE_PHONE_QUALIFIER` payload.
- Preserve the source `RECORD_TYPE` despite the analyzer's heuristic type/name warning on four ORGANIZATION records; do not retype without stronger source evidence.
