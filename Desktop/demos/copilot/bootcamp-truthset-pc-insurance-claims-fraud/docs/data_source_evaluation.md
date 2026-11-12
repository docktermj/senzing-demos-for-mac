# Data Source Evaluation Report

**Date:** 2026-11-11
**Project:** pc-insurance-claims-fraud

## Summary

- Total data sources: 3
- Mapped: 3
- Needs mapping: 0
- Needs enrichment: 1

All sources remain below the 70% quality threshold after safe format normalization, and all three are mapped. The user chose to proceed with SIU_WATCHLIST as-is after review found no further safe normalization; its 56.5% score and completeness limitations are accepted for now. No duplicate `(DATA_SOURCE, RECORD_ID)` pairs or PERSON-name type candidates were found. One SIU_WATCHLIST record has no name attribute; this row-level gap is noted below. Original files are preserved; the registry points to derived `-improved.jsonl` mapping inputs. Ambiguous slash dates remain verbatim in the improved files.

The quality visualization and screenshot are the original Step 6 baseline, before normalization; the updated scores and gate below are authoritative for the improved files.

## Data Source Details

### Data Source 1: POLICYHOLDERS

**Status:** Mapped
**Location:** `data/raw/POLICYHOLDERS-improved.jsonl`
**Original:** `data/raw/POLICYHOLDERS.jsonl`
**Records:** 120
**Fields:** 26 distinct `FEATURES` attributes; 6 root fields, including `DATA_SOURCE` and `RECORD_ID`

**Evaluation:**

- Contains 114 PERSON and 6 ORGANIZATION records. The shared-field type-applicability check passed.
- Quality changed from 51.2% to 51.5% after consistent casing was applied to mixed coded values: completeness 33.2%, format consistency 92.9%, and duplicate-key rate 0.0%.
- `FEATURES` attributes match the current Entity Specification. Root fields `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` are preserved as payload in the mapped output.
- The source uses `DATA_SOURCE=CUSTOMERS`; the mapped output uses the registered project code `POLICYHOLDERS`.
- Explicit or unambiguous `DATE` and `DATE_OF_BIRTH` values, phone punctuation, one complete SSN format, outer whitespace, and mixed casing in coded values were normalized. Ambiguous slash dates, identifier lengths, and phone lengths/country-code notation were preserved.

**Reason:** Mapping is complete, but the score remains below 70% because of source-data completeness gaps.

**Record type check:** 0 of 114 PERSON-typed records are type/name candidates. Decision: none needed.

**At the time of this mapping assessment:** SIU_WATCHLIST mapping remained to be completed; no records had yet been loaded. Reviewer approval was later confirmed before Module 6 loads.

### Data Source 2: VENDOR_REGISTRY

**Status:** Mapped
**Location:** `data/raw/VENDOR_REGISTRY-improved.jsonl`
**Original:** `data/raw/VENDOR_REGISTRY.jsonl`
**Records:** 22
**Fields:** 30 distinct `FEATURES` attributes; 5 root fields, including `DATA_SOURCE` and `RECORD_ID`

**Evaluation:**

- Contains 16 PERSON and 6 ORGANIZATION records. The shared-field type-applicability check passed.
- The initial formatting pass changed quality from 56.3% to 56.4% after address-country casing was standardized (38.5% completeness, 97.7% format consistency, 0.0% duplicate rate). A later mapping-review iteration normalized one display-name/address wrapper in `EMAIL_ADDRESS` to its enclosed source-supported address; the original collected file remains unchanged. The updated score is 57.0% (38.5% completeness, 100.0% format consistency, 0.0% duplicate rate).
- `FEATURES` attributes match the current Entity Specification. Root `CATEGORY`, `DATE`, and `STATUS` remain payload.
- The input uses `DATA_SOURCE=REFERENCE`; the mapped output uses the registered project code `VENDOR_REGISTRY`.
- Recognizable date-of-birth layouts, phone punctuation, address-country casing, outer whitespace, and one email wrapper were normalized. Postal codes, country aliases, tax IDs, phone lengths, and LEIs were left unchanged because their differing values may be substantive.

**Reason:** Mapping is complete, but the score remains below 70% because of source-data completeness gaps.

**Record type check:** 0 of 16 PERSON-typed records are type/name candidates. Decision: none needed.

**At the time of this mapping assessment:** All three sources were mapped and the VENDOR_REGISTRY output was ready. The later Module 6 load is documented in `docs/loading_strategy.md`.

### Data Source 3: SIU_WATCHLIST

**Status:** Mapped; proceeding with documented quality limitations
**Location:** `data/senzing-ready/SIU_WATCHLIST.jsonl`
**Original:** `data/raw/SIU_WATCHLIST.jsonl`
**Mapping input:** `data/raw/SIU_WATCHLIST-improved.jsonl`
**Records:** 17
**Fields:** 19 distinct `FEATURES` attributes; 5 root fields, including `DATA_SOURCE` and `RECORD_ID`

**Evaluation:**

- Contains 17 PERSON records. The shared-field type-applicability check passed.
- Quality changed from 56.2% to 56.5% after category casing was standardized: completeness 39.5%, format consistency 95.3%, and duplicate-key rate is 0.0%.
- `FEATURES` attributes match the current Entity Specification. Root fields `CATEGORY`, `DATE`, and `STATUS` are preserved as payload.
- The source label `WATCHLIST` is aligned with the registered project code `SIU_WATCHLIST` in the mapped output.
- One of 17 records has no name attribute. Explicit or unambiguous dates, phone punctuation, category casing, and outer whitespace were normalized; ambiguous slash dates, differing year widths, and phone lengths were preserved.
- The validated 17-record mapping output was ready at the location above before Module 6; the later load is documented in `docs/loading_strategy.md`.

**Reason:** Mapping is complete, but quality remains below 70% because of sparse source data. The single record without a name is a data gap, not a type/name candidate. No further safe normalization was identified; do not impute values.

**Record type check:** 0 of 17 PERSON-typed records are type/name candidates. Decision: none needed.

**Next step at assessment time:** Continue to Data processing with the current mapped output. The 56.5% score and missing-name gap were documented; the source was later loaded as-is at the bootcamper's direction.

## Mapping Priority

Completed mapping order; this is not approval to load records.

1. **POLICYHOLDERS** — establishes the insurer's core policyholder population and is the largest source.
2. **VENDOR_REGISTRY** — connects claim-paid businesses with associated people and organizational relationships.
3. **SIU_WATCHLIST** — adds the screening identities used to surface potential SIU leads; preserve and explicitly handle the one record with no name.

## Cross-Source Outlook

Counts below are exact distinct-value overlaps on the named attributes, not counts of matching records or resolved entities.
They were remeasured from the improved copies after normalization.

- **POLICYHOLDERS × VENDOR_REGISTRY — measured**
  - `PHONE_NUMBER`: 1 distinct value shared (of 14 in POLICYHOLDERS, 7 in VENDOR_REGISTRY).
  - `NAME_FIRST`: 0 shared (of 79, 2); `NAME_LAST`: 0 (of 59, 2); `DATE_OF_BIRTH`: 0 (of 37, 2); `EMAIL_ADDRESS`: 0 (of 13, 2).
- **POLICYHOLDERS × SIU_WATCHLIST — measured**
  - `NAME_LAST`: 6 distinct values shared (of 59 in POLICYHOLDERS, 9 in SIU_WATCHLIST); `NAME_FIRST`: 5 (of 79, 8); `DATE_OF_BIRTH`: 3 (of 37, 5); `SSN_NUMBER`: 2 (of 5, 2); `EMAIL_ADDRESS`: 1 (of 13, 4).
  - `PHONE_NUMBER`: 1 shared (of 14, 2).
- **VENDOR_REGISTRY × SIU_WATCHLIST — measured**
  - `PHONE_NUMBER`: 0 distinct values shared (of 7 in VENDOR_REGISTRY, 2 in SIU_WATCHLIST); `NAME_FIRST`: 0 (of 2, 8); `NAME_LAST`: 0 (of 2, 9); `EMAIL_ADDRESS`: 0 (of 2, 4); `DATE_OF_BIRTH`: 0 (of 2, 5).

## Quality Gate

Re-scoring uses `quality_score = 0.70 × completeness + 0.25 × format_consistency + 0.05 × (100 - duplicate_rate)`. The mapping-stage results are 51.5% for POLICYHOLDERS, 57.0% for VENDOR_REGISTRY, and 56.5% for SIU_WATCHLIST. Each remains below 70%. Completeness gaps were not filled; no values were invented. The improved files retain all 159 source records and original keys. All three sources were subsequently loaded; validation is documented in `docs/results_validation.md`.

The weakest coverage fields recorded in the existing assessment remain completeness gaps: POLICYHOLDERS (`NAME_TYPE` 1/120, `NAME_MIDDLE` 2/114, `NATIONAL_ID_COUNTRY` 3/120); VENDOR_REGISTRY (`NATIONAL_ID_TYPE`, `NATIONAL_ID_NUMBER`, and `NATIONAL_ID_COUNTRY`, each 2/22); SIU_WATCHLIST (`NAME_MIDDLE`, `GENDER`, and `DRIVERS_LICENSE_NUMBER`, each 1/17, tied with other fields). These missing values cannot be safely filled in this module.

The initial Step 7b pass found no further safe mechanical normalization at that point. A later VENDOR_REGISTRY review identified one source-supported email-format correction. The current sample-data catalog confirms these collected sources match the fixed Senzing Demo Truth Set (159 records across CUSTOMERS, REFERENCE, and WATCHLIST). For SIU_WATCHLIST, no additional safe correction remained; the user chose to proceed with the current mapped data despite its quality limitations. Remaining completeness gaps were not filled by inference.

## Safe Normalization Applied

- Trimmed outer whitespace without altering internal characters.
- Standardized only explicit or unambiguous dates to `MM/DD/YY` or `MM/DD/YYYY`, preserving whether the original year had two or four digits. Two-digit years were not expanded to a guessed century; ambiguous slash dates were restored verbatim, and unambiguous day-first values were converted without changing the date.
- Removed phone-number punctuation while preserving digits and an existing leading `+`; did not infer missing digits or country codes.
- Standardized casing in mixed coded-value fields while preserving spelling and aliases.
- Normalized one email display-name/address wrapper to the enclosed address, which is independently source-supported; the original collected file remains unchanged.
- Formatted complete nine-digit SSNs with standard separators. Did not pad or truncate identifiers, unify country aliases, or fill missing values.
- Kept original fetched files unchanged and retained their fetch validation metadata in the registry; those validation fields refer only to each `original_file_path`.
- Remeasured format consistency with the same field-dominant-pattern method used for the initial scores. All record counts and `(DATA_SOURCE, RECORD_ID)` keys are unchanged.
- Audited the final copies: all changed values are formatting-only; ambiguous slash dates remain exactly as collected.
