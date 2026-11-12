# Mapping Review Journal: AML_WATCHLIST

## Start and plan

- Mapping mode: interactive; mapper language: Java.
- Source: `data/raw/AML_WATCHLIST-regenerated.jsonl`; 17 records, 25 fields, one schema.
- Plan: one PERSON master keyed by `RECORD_ID`; no support schemas.

## Mapping decisions

- All records explicitly identify as PERSON through `FEATURES.RECORD_TYPE`.
- Map nested name, birth date, address, driver's-license, email, phone, employer, gender, and SSN fields to their matching feature families.
- Keep `CATEGORY`, root `DATE`, and `STATUS` as payload.
- Preserve source strings and per-address grouping; reject unexpected fields and orphan address/license qualifiers.

## Validation and review

- The Java mapper emitted 17 records. Sample and full analyzers passed, as did the verbatim check.
- Routing found no dropped feature values; the three root payload fields are intentional.
- Source-quality warnings: SSN on 2/17 records, driver's-license data on 1/17, and EMPLOYER uniqueness of 29.4%.
- The bootcamper accepted the quality visualization and chose to proceed to loading. The optional sandbox test load was skipped; no environment or database was inspected or modified.
