# Data Source Evaluation Report

**Date:** 2026-11-14
**Project:** banking-customer-360-kyc

## Summary

- Total data sources: 5 (248 records)
- Entity Specification-compliant: 5 (every source uses the `FEATURES` layout and every feature attribute resolves to the Entity Specification; root business columns still need a mapping disposition)
- Needs mapping: 0 (Phase 2 confirms compliance and dispositions the business columns)
- Needs enrichment: 0

Quality score = 0.70 × completeness + 0.25 × format consistency + 0.05 × (100 - duplicate rate).
Completeness is measured per record against the features applicable to that record's `RECORD_TYPE`,
with identifiers (SSN, passport, driver's licence, national ID, tax ID, LEI) scored as one group and
disclosed-relationship features reported separately. Scored one identifier type at a time ("strict"),
the figures are lower; both are recorded in `config/data_sources.yaml`.

| Source | Records | Completeness | Format | Duplicates | Quality | Strict | Band |
|---|---|---|---|---|---|---|---|
| CREDIT_BUREAU | 30 | 90.4 | 95.2 | 0.0 | **92.1** | 85.3 | ✅ ≥80 |
| BUSINESS_REGISTRY | 22 | 69.1 | 88.2 | 0.0 | **75.4** | 71.9 | ⚠ 70-79 |
| CORE_BANKING | 97 | 66.3 | 89.6 | 0.0 | **73.8** | 60.2 | ⚠ 70-79 |
| CARD_SERVICES | 82 | 65.1 | 87.3 | 0.0 | **72.4** | 57.9 | ⚠ 70-79 |
| WATCHLIST | 17 | 48.8 | 98.0 | 0.0 | **63.7** (was 62.6) | 60.6 | ⚠ <70 |

## Data Source Details

### Data Source 1: CORE_BANKING

**Status:** Entity Specification-compliant
**Location:** `data/raw/core_cif_extract.jsonl`
**Records:** 97 (PERSON 86, ORGANIZATION 8, untyped 3)
**Fields:** Senzing features NAME, DOB, ADDRESS, PHONE, EMAIL, GENDER, SSN, PASSPORT, DRLIC, NATIONAL_ID, TAX_ID, REL_ANCHOR, REL_POINTER; root business columns ACCOUNT_STATUS, BRANCH, CURRENT_BALANCE, OPEN_DATE, OWNERSHIP_TYPE, PRODUCT_TYPE, RELATIONSHIP_TIER

**Evaluation:**

- Name on 100% of records; DOB 70% of persons; address 66%; any identifier 55%; phone 34%; email 33%.
- Date of birth appears in four layouts (M/D/YY 28, ISO 15, M/D/YYYY 14, and `15-Mar-92` style 4).
- 3 records carry no `RECORD_TYPE` (3021, 3023, 3025, the Gupta records, which mix a person name and the business name WONDERLAND).

**Reason:** Already Senzing JSON; only the business columns need a payload/ignore decision.

**Record type check:** 0 of 86 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping confirmation)

### Data Source 2: CARD_SERVICES

**Status:** Entity Specification-compliant
**Location:** `data/raw/card_cardholder_master.jsonl`
**Records:** 82 (PERSON 67, ORGANIZATION 12, untyped 3)
**Fields:** Senzing features NAME, DOB, ADDRESS, PHONE, EMAIL, GENDER, SSN, PASSPORT, DRLIC, NATIONAL_ID, TAX_ID, REL_ANCHOR, REL_POINTER; root business columns ACCOUNT_STATUS, CARDHOLDER_ROLE, CARD_ACCOUNT_ID, CREDIT_LIMIT, CURRENT_BALANCE, DELINQUENCY_BUCKET, OPEN_DATE, PAN_LAST4, PRODUCT

**Evaluation:**

- Name 99%; address 71%; DOB 63% of persons; email 50%; any identifier 38%; phone 23%.
- DOB in four layouts; phone in five; passport country as both codes and full names.
- 3 untyped records (3022, 3024, 2215). Record 2215 carries only an email address (`info@ca-state.gov`) and no name.

**Reason:** Already Senzing JSON; only the business columns need a payload/ignore decision.

**Record type check:** 0 of 67 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping confirmation)

### Data Source 3: CREDIT_BUREAU

**Status:** Entity Specification-compliant
**Location:** `data/raw/credit_header.jsonl`
**Records:** 30 (PERSON 30)
**Fields:** Senzing features NAME (with AKA names), DOB, ADDRESS, PHONE, GENDER, SSN, REL_ANCHOR; root business columns FILE_SINCE, FILE_STATUS

**Evaluation:**

- Name, address and DOB on every record; SSN 80%; phone 77%; gender 67%.
- DOB in two layouts (ISO 19, M/D/YYYY 11).

**Reason:** Already Senzing JSON; strongest source.

**Record type check:** 0 of 30 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping confirmation)

### Data Source 4: BUSINESS_REGISTRY

**Status:** Entity Specification-compliant
**Location:** `data/raw/business_registry.jsonl`
**Records:** 22 (ORGANIZATION 6, PERSON 16)
**Fields:** Senzing features NAME, ADDRESS, PHONE, EMAIL, DOB, EMPLOYER, NATIONAL_ID, TAX_ID, LEI, REL_ANCHOR, REL_POINTER; root business columns INCORPORATED, REGISTRY_ROLE, REGISTRY_STATUS

**Evaluation:**

- Organizations: name, address and an identifier on every record. Persons (owners and officers): name on all, address 63%, employer 56%, DOB 13%.
- Ownership and officer links are disclosed relationships (`REL_ANCHOR` / `REL_POINTER` with roles such as "Owns 60%").
- Phone numbers in six layouts; country names mixed.

**Reason:** Already Senzing JSON; carries the ownership links KYB depends on.

**Record type check:** 0 of 16 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping confirmation)

### Data Source 5: WATCHLIST

**Status:** Entity Specification-compliant
**Location:** `data/raw/screening_list-improved.jsonl` (original: `data/raw/screening_list.jsonl`)
**Records:** 17 (PERSON 17)
**Fields:** Senzing features NAME, ADDRESS, DOB, EMAIL, PHONE, GENDER, EMPLOYER, SSN, DRLIC; root business columns LIST_TYPE, LIST_STATUS, LISTED_DATE

**Evaluation:**

- Name 94%; address 77%; DOB 41%; email 29%; any identifier 18%; phone 12%.
- Record 1021 is a PERSON with an address and no name.
- Sparse attributes are typical of a screening list; the score reflects the source, not a defect that can be repaired here.
- Improved: DATE_OF_BIRTH normalized to ISO in `data/raw/screening_list-improved.jsonl` (original kept); score 62.6 → 63.7. Returns to Data collection confirmed no fuller list exists. Decision: proceed anyway.

**Reason:** Already Senzing JSON; `LIST_TYPE` (Sanctions, PEP, Internal Fraud) drives the KYC risk tiers.

**Record type check:** 0 of 17 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping confirmation)

## Quality Gate Decisions

- WATCHLIST (<70): proceed anyway after DOB normalization; remaining gaps are missing values.
- CORE_BANKING, CARD_SERVICES, BUSINESS_REGISTRY (70-79): continue to mapping; gaps documented above.
- CREDIT_BUREAU (≥80): no decision needed.

## Mapping Priority

1. CORE_BANKING - the largest customer system and the base of the customer 360.
2. CARD_SERVICES - the other customer system; together with CORE_BANKING it is the 179-record collapse the business problem measures.
3. WATCHLIST - drives every KYC screening alert.
4. BUSINESS_REGISTRY - carries the ownership links behind KYB and the OFAC 50% rule.
5. CREDIT_BUREAU - identity verification; already the cleanest source.

## Cross-Source Outlook

Distinct values shared on each named attribute (normalized: SSN and phone digits only, names and emails lower-cased; names compared as whole strings, so name variants are not counted).

- CORE_BANKING × CARD_SERVICES — **measured**
  - Shared: `EMAIL_ADDRESS` 15 (of 17 / 18), `PHONE_NUMBER` 8 (of 28 / 14), `PASSPORT_NUMBER` 8 (of 10 / 9), `DRIVERS_LICENSE_NUMBER` 5 (of 6 / 5), `TAX_ID_NUMBER` 4 (of 7 / 5), `SSN_NUMBER` 2 (of 25 / 10), `NATIONAL_ID_NUMBER` 2 (of 2 / 2), exact names 10
- CORE_BANKING × CREDIT_BUREAU — **measured**
  - Shared: `SSN_NUMBER` 13 (of 25 / 23), `PHONE_NUMBER` 7 (of 28 / 24), exact names 11
- CORE_BANKING × WATCHLIST — **measured**
  - Shared: `SSN_NUMBER` 2 (of 25 / 2), `EMAIL_ADDRESS` 2 (of 17 / 4), `PHONE_NUMBER` 1, `DRIVERS_LICENSE_NUMBER` 1, exact names 4
- CARD_SERVICES × CREDIT_BUREAU — **measured**
  - Shared: `SSN_NUMBER` 3 (of 10 / 23), exact names 2
- CARD_SERVICES × WATCHLIST — **measured**
  - Shared: `EMAIL_ADDRESS` 1 (of 18 / 4), exact names 2
- CORE_BANKING × BUSINESS_REGISTRY — **measured**
  - Shared: `PHONE_NUMBER` 2 (of 28 / 6), `TAX_ID_NUMBER` 1 (of 7 / 4)
- BUSINESS_REGISTRY × WATCHLIST — **measured**
  - Shared: `PHONE_NUMBER` 2 (of 6 / 2)
- CARD_SERVICES × BUSINESS_REGISTRY — **measured**
  - Shared: `PHONE_NUMBER` 1 (of 14 / 6)
- CREDIT_BUREAU × BUSINESS_REGISTRY and CREDIT_BUREAU × WATCHLIST — **measured**: no shared identifier, email, phone or exact name.

Shared values predict candidates, not matches: whether they resolve is decided by Senzing at load time,
and the ownership-based KYB links come from the registry's disclosed relationships rather than shared
values.
