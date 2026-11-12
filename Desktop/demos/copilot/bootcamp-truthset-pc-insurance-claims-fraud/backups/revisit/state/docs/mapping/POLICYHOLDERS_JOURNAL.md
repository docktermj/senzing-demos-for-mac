# Mapping Review Journal

## Start and profile
- Mode: interactive; concise.
- Mapper language: Java.
- `POLICYHOLDERS-improved`: 120 records, 33 fields.
- Root optional-field patterns vary within PERSON records. `FEATURES.RECORD_TYPE` is nested, so the profiler's root-level grouping did not isolate it; retain one source schema and use the discriminator during mapping.

## Entity plan
- Master: `POLICYHOLDERS-improved`, registered data source `POLICYHOLDERS`, dominant record type `PERSON`, stable key `RECORD_ID`; `FEATURES.RECORD_TYPE` distinguishes 114 PERSON and 6 ORGANIZATION records.
- No child, lookup, or relationship schemas were identified in this single flat source. Root `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` remain field-mapping dispositions.
- Plan confidence: 0.96; no open questions.

## Field mapping
- Directly map source feature attributes to their matching feature families; person-name fields and organization-name fields are conditional on `FEATURES.RECORD_TYPE`.
- Derive `DATA_SOURCE=POLICYHOLDERS` and `RECORD_ID` from the stable source key. Keep `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` as payload; ignore the source code and structural container.
- No unsupported code translations or semantic-value changes are introduced.

## Generate and validate
- Added a dependency-free Java transformer at `src/transform/transform_POLICYHOLDERS.java` and reusable JSONL parsing/serialization support at `src/transform/JsonSupport.java`.
- Generated `data/mapping/POLICYHOLDERS_sample.jsonl` (10 records) and `data/mapping/POLICYHOLDERS_quality.jsonl` (all 120 records).
- Replaced the source `DATA_SOURCE` with the registered `POLICYHOLDERS` code; retained each `RECORD_ID` and every nonblank feature and payload value exactly. No records were loaded.
- Kept source feature objects separate. Full and parsed address objects are not combined. Four standalone `PHONE_TYPE` values are preserved as root payload `SOURCE_PHONE_QUALIFIER` because no phone number accompanies them.
- The analyzer completed without structural errors. Feature coverage includes `NAME` 100%, `ADDRESS` 60.8%, `PHONE` 17.5%, `EMAIL` 37.5%, `SSN` 6.7%, `PASSPORT` 12.5%, `DRLIC` 9.2%, and `NATIONAL_ID` 3.3%.
- The source quality score remains 51.5%. The analyzer flagged four source-labeled ORGANIZATION records for name/type review; retain the explicit source discriminator and defer semantic verification to the later match-key review.
- Reconciled the mapping count representation: the workflow reports 19 direct feature mappings, while the local aggregate count of 26 includes six type-specific name overrides and the `RECORD_TYPE` discriminator. All 33 source fields have a disposition.

## Quality iteration and finalization
- Rechecked the fixed Truth Set source after the user's request to iterate. No additional safe formatting or duplicate corrections were available; completeness gaps were not filled or guessed. The quality score remains 51.5% (33.2% completeness, 92.9% format consistency, 0.0% duplicate rate).
- Re-ran the Senzing-provided analyzer on all 120 records. It reported advisory feature sparsity and four source-labeled ORGANIZATION records for type review, with no blocking structural errors. Source record types and nonblank feature/payload values remain preserved.
- Documented the source map, transformation, and lineage in `docs/mapping/`; the analyzer report and mapping notes are archived there before the next source workflow.
