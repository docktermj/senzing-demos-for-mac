# Mapping Review Journal

## Start
- Mapping mode: interactive.
- Mapper language: Java.
- `BANK_CUSTOMERS-regenerated`: 120 records, 33 fields.

## Plan
- Master: `BANK_CUSTOMERS-regenerated` (`BANK_CUSTOMERS`); dominant type PERSON, with `FEATURES.RECORD_TYPE` distinguishing ORGANIZATION records.
- `RECORD_ID` is the stable record key; no support schemas were found.

## Mapping decisions
- Keep `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS` as root payload fields; they are scalar source context, not matching features.
- Use structured address parts when present and `ADDR_FULL` only when those parts are absent, as selected by the bootcamper.
- Map name fields conditionally by `FEATURES.RECORD_TYPE`; omit `PHONE_TYPE` when its feature object has no phone number.

## Field mapping validation
- The 33 profiled fields were dispositioned and the MCP mapping workflow validated the BANK_CUSTOMERS map.
- The workflow checkpoint is `generate_validate` (step 4); it reports 19 direct feature fields, plus the conditional type-discriminator name mappings.

## Mapper run
- Mapper: `data/mapping/BANK_CUSTOMERS_mapper.java`
- Output: `data/mapping/BANK_CUSTOMERS_output.jsonl` (120 records)
- QA verdict: approve; sample and output analyzer passed, and the verbatim check passed.
- Source-quality warnings: four ORGANIZATION names were flagged for manual type review; EMAIL uniqueness and sparse identifier populations remain low.

## BUSINESS_REGISTRY start
- The bootcamper selected `BUSINESS_REGISTRY` as the next source.
- Source: `data/raw/BUSINESS_REGISTRY-regenerated.jsonl`; profile measured 22 records and 36 fields.
- One master schema; optional root `DATE` is a structure variation, not a separate entity schema.
- Entity plan: one `BUSINESS_REGISTRY` master keyed by `RECORD_ID`; nested `RECORD_TYPE` discriminates PERSON/ORGANIZATION; no support tables.

## BUSINESS_REGISTRY field mapping
- The 36 source fields are dispositioned; the MCP mapping workflow validated the specification.
- The bootcamper confirmed `DATE` means organization registration/incorporation date; map it to `REGISTRATION_DATE` only for ORGANIZATION.
- Map nested `REL_ANCHOR` and `REL_POINTER` fields directly; apply the structured-address-first fallback rule.
- The Java mapper emitted all 22 records. Verbatim and routing checks passed; the analyzer reported one ambiguity for record `2151`, which has two distinct address entries both labeled `REGISTERED`. Per the bootcamper's decision, preserve both values unchanged and document the analyzer finding.
- Preserve `TAX_ID_TYPE` as mapped, but verify compatibility before load: the current Entity Specification includes it and the release notes identify it as new in 4.4.0, while the saved SDK checkpoint records 4.3.2.
- The bootcamper chose to map the remaining source next. Skip the optional sandbox preview for BUSINESS_REGISTRY; no environment or database was inspected or modified.
