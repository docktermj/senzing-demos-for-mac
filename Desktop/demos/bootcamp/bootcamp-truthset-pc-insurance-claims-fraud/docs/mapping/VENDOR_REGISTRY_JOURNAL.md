# Mapping Review Journal

## Start and profile
- Mode: interactive; concise. Mapper language: Java.
- `VENDOR_REGISTRY-improved`: 22 records, 36 fields, one nested JSONL schema.
- Root `RECORD_ID` is the stable key; nested `FEATURES.RECORD_TYPE` distinguishes 16 PERSON and 6 ORGANIZATION records. Root `DATE` is optional and creates the only field-pattern variation.
- Nested features include employer names and relationship anchor/pointer fields; repeated employer values overlap organization-name values, and relationship key coverage needs review before planning.

## Plan and mapping
- Planned the parent source as a mixed PERSON/ORGANIZATION master keyed by `RECORD_ID`, with `FEATURES.RECORD_TYPE` as discriminator. The 9 PERSON employer values have 3 distinct values, all also present as source organization names, so they are modeled as an embedded ORGANIZATION master linked with `EMPLOYED_BY`.
- The 18 source relationship keys all occur in collected sources; 15 match VENDOR_REGISTRY IDs and 3 match POLICYHOLDERS IDs. Preserve the explicit `REFERENCE` relationship domain and keys; do not infer cross-source targets from ID collisions.
- Keep generic root `DATE` as payload because its specific meaning is not established. Preserve all other source feature values and source entity types.

## Generate and validate
- The Java mapper emits 22 source records and three deduplicated embedded employer records (25 total). The sample emits 10 source records and two embedded employers.
- The analyzer passed with zero structural errors and eight advisory warnings. Seven source-labeled ORGANIZATION records were flagged by name-plausibility heuristics; preserve their explicit source types pending source evidence.
- Verbatim fidelity passed, and the routing report found no dropped source values.
- Optional sandbox validation is skipped while SIU_WATCHLIST remains unmapped; no records have been loaded.
- The user's iteration choice led to one safe format correction: a display-name/address wrapper in `EMAIL_ADDRESS` was reduced to its source-supported address. The original collected file and all 22 record keys remain unchanged. The updated quality score is 57.0% (38.5% completeness, 100.0% format consistency, 0.0% duplicate rate).
