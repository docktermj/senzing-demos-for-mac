# Mapping Review Journal: BANK_CUSTOMERS

## Start and plan

- Mapping mode: interactive; mapper language: Java.
- Source: `data/raw/BANK_CUSTOMERS-regenerated.jsonl`; 120 records and 33 fields.
- Plan: one master schema keyed by `RECORD_ID`; `FEATURES.RECORD_TYPE` distinguishes PERSON and ORGANIZATION; no support schemas.

## Mapping decisions

- Keep `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` as root payload.
- Apply the selected structured-address-first rule, using `ADDR_FULL` only when structured parts are absent in the same source feature object.
- Emit `PHONE_TYPE` only when `PHONE_NUMBER` is present; retain identifier qualifiers with their corresponding identifier numbers.
- Preserve source feature values without normalization.

## Validation and review

- The mapper emitted all 120 records. The full analyzer and verbatim checks passed, and routing found no dropped source value-entries.
- Quality score: 81.9 (74.7 completeness, 98.5 format consistency, 0.0 duplicate rate).
- Four ORGANIZATION names were flagged for manual type review. EMAIL uniqueness and identifier coverage remain source-quality cautions.
