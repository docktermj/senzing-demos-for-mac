# Data Source Evaluation Report

**Date:** 2026-11-09
**Project:** Banking KYC/KYB/AML Demo

## Summary

- Total data sources: 3
- Entity Specification-compliant: 0
- Needs mapping: 3
- Needs enrichment: 0

Completeness follows Phase 1 Step 6: for each record, it is the share of applicable source
fields that are present, with applicability checked by `RECORD_TYPE`; the source-level
completeness is the mean across records. Structural keys count, unrecognized root fields
without a specification counterpart do not, and identifier attributes count as one family
when any member is present. Presence uses the Step 6 test (not truthiness or key existence).
The visualization's per-field bars are diagnostics, not substitutes for the per-record
calculation.

## Data Source Details

### Data Source 1: BANK_CUSTOMERS

**Status:** Needs mapping
**Location:** `data/raw/BANK_CUSTOMERS-regenerated.jsonl` (improved base: `data/raw/BANK_CUSTOMERS-improved.jsonl`; collected original: `data/raw/BANK_CUSTOMERS.jsonl`)
**Records:** 120
**Fields:** 32

**Evaluation:**

- Quality score: 81.9 (74.7 completeness, 98.5 format consistency, 0.0 duplicate rate).
- The source is ready for mapping; remaining low-coverage features include PERSON gender (17.5%) and identifiers (31.6% for PERSON, 0.0% for ORGANIZATION).
- The synthesized regeneration adds structured addresses to close address-field gaps. All 120 record IDs and improved-base values are preserved; generated email and phone values are unique, with no cross-source collisions. Identifier numbers were not generated.
- The unrecognized root fields are `AMOUNT`, `CATEGORY`, `DATE`, and `STATUS`.

**Reason:** The source contains recognized feature attributes and required record keys, but the unrecognized root fields still need a mapping disposition.

**Record type check:** 0 of 114 PERSON-typed records are candidates. Decision: none needed.

**Next step:** Phase 2 mapping after the remaining source quality gates.

### Data Source 2: BUSINESS_REGISTRY

**Status:** Needs mapping
**Location:** `data/raw/BUSINESS_REGISTRY-regenerated.jsonl` (improved base: `data/raw/BUSINESS_REGISTRY-improved.jsonl`; collected original: `data/raw/BUSINESS_REGISTRY.jsonl`)
**Records:** 22
**Fields:** 35

**Evaluation:**

- Quality score: 80.6 (72.2 completeness, 100.0 format consistency, 0.0 duplicate rate). The improved copy was previously shown as 72.4 under an earlier scoring pass; that figure is not directly comparable because the completeness method was later corrected.
- The regenerated copy preserves all 22 record IDs and every value from the improved base, and adds synthetic dates of birth, email addresses, phone numbers, and structured addresses. Email and phone values are unique, with no cross-source collisions; identifier numbers were not generated.
- The identifier family is 0.0% for PERSON and 100.0% for ORGANIZATION; sample records confirm the split (a PERSON record has no identifier attributes, while an ORGANIZATION record contains a TAX_ID). Other low-coverage fields include PERSON first/last name components (12.5%), EMPLOYER (56.2%), and PHONE_TYPE (18.8% for PERSON, 0.0% for ORGANIZATION).
- The unrecognized root fields are `CATEGORY`, `DATE`, and `STATUS`.

**Reason:** The source contains recognized feature attributes and required record keys, but the unrecognized root fields still need a mapping disposition.

**Record type check:** 0 of 16 PERSON-typed records are candidates. Decision: none needed.

**Next step:** Phase 2 mapping after the remaining source quality gate.

### Data Source 3: AML_WATCHLIST

**Status:** Needs mapping
**Location:** `data/raw/AML_WATCHLIST-regenerated.jsonl` (improved base: `data/raw/AML_WATCHLIST-improved.jsonl`; collected original: `data/raw/AML_WATCHLIST.jsonl`)
**Records:** 17
**Fields:** 24

**Evaluation:**

- Quality score: 88.7 (83.9 completeness, 100.0 format consistency, 0.0 duplicate rate). The regenerated file preserves all 17 record IDs and every feature value from the improved base.
- The return-to-collection regeneration fills address, date-of-birth, email, employer, gender, and phone coverage; generated emails and phones introduce no cross-source collisions, and no identifier numbers were generated.
- The address and NAME families are present on every record. The identifier family remains at 17.6%; individual name-field coverage is NAME_FIRST 70.6%, NAME_LAST 70.6%, NAME_FULL 29.4%, and NAME_MIDDLE 5.9%.
- The unrecognized root fields are `CATEGORY`, `DATE`, and `STATUS`.

**Reason:** The source contains recognized feature attributes and required record keys, but the unrecognized root fields still need a mapping disposition.

**Record type check:** 0 of 17 PERSON-typed records are candidates. Decision: none needed.

**Next step:** Phase 2 mapping; the regenerated source meets the `>=80` quality target.

## Mapping Priority

1. **BANK_CUSTOMERS** — primary customer records for the fictional bank's unified customer view.
2. **BUSINESS_REGISTRY** — ownership and officer links support the business-relationship goal.
3. **AML_WATCHLIST** — supports evidence-backed screening leads after customer and business records are mapped.

## Cross-Source Outlook

- **BANK_CUSTOMERS × BUSINESS_REGISTRY — measured**
  - Shared attribute: `ADDR_POSTAL_CODE`
  - Distinct values shared: 2 (of 60 in BANK_CUSTOMERS, 6 in BUSINESS_REGISTRY)
- **BANK_CUSTOMERS × AML_WATCHLIST — measured**
  - Shared attribute: `NAME_LAST`
  - Distinct values shared: 6 (of 59 in BANK_CUSTOMERS, 9 in AML_WATCHLIST)
- **BUSINESS_REGISTRY × AML_WATCHLIST — measured**
  - Shared attribute: `ADDR_LINE1`
  - Distinct values shared: 1 (of 9 in BUSINESS_REGISTRY, 28 in AML_WATCHLIST)

Shared values are candidate overlap signals, not match predictions. Counts use
distinct, trimmed, case-insensitive values from the same named attribute.
