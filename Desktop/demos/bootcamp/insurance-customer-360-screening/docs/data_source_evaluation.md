# Data Source Evaluation Report

**Date:** 2026-11-14
**Project:** insurance-customer-360-screening

## Summary

- Total data sources: 6
- Entity Specification-compliant: 6 (structure and attribute names; each still needs a payload/ignore decision for its business fields)
- Needs mapping: 0 for structure; 6 go through mapping to confirm compliance and decide the business fields
- Needs enrichment: 0

Quality = 0.70 × completeness + 0.25 × format consistency + 0.05 × (100 - duplicate rate). Completeness is per record, against the features that apply to that record's type, with identifiers counted as one group. The score says the data is clean enough to map; it does not say the mapping will be correct, which only the load in Data processing can show.

## Data Source Details

### Data Source 1: PC_POLICY_ADMIN

**Status:** Entity Specification-compliant (structure)
**Location:** `data/raw/pc_policy_admin_extract.jsonl`
**Records:** 104 (PERSON 86, ORGANIZATION 15, - 3)
**Fields:** 7 Senzing features in use; 10 business fields outside FEATURES

**Evaluation:**

- Quality 67.1 — ⚠ Recommend fixing before mapping (<70); completeness 57.1, format consistency 88.4, duplicate rate 0.0
- Formats: DATE_OF_BIRTH 28/61 on dominant format; PHONE_NUMBER 28/31 on dominant format; ADDR_POSTAL_CODE 49/51 on dominant format; ADDR_STATE 48/49 on dominant format; SSN_NUMBER 26/28 on dominant format; GENDER 23/25 on dominant format
- Business fields outside FEATURES (payload/ignore decision needed): POLICY_NUMBER, LINE_OF_BUSINESS, INSURED_ROLE, WRITTEN_PREMIUM, EFFECTIVE_DATE, EXPIRATION_DATE, POLICY_STATUS, PRODUCER_CODE, CANCELLATION_REASON, CUSTOMER_TIER
- 3 records carry no RECORD_TYPE

**Reason:** records already use a FEATURES array with Entity Specification attribute names; mapping confirms this and decides the business fields.

**Record type check:** 0 of 86 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 2: LIFE_ANNUITY_ADMIN

**Status:** Entity Specification-compliant (structure)
**Location:** `data/raw/life_annuity_admin_extract.jsonl`
**Records:** 75 (PERSON 67, ORGANIZATION 5, - 3)
**Fields:** 7 Senzing features in use; 10 business fields outside FEATURES

**Evaluation:**

- Quality 63.0 — ⚠ Recommend fixing before mapping (<70); completeness 53.7, format consistency 81.5, duplicate rate 0.0
- Formats: DATE_OF_BIRTH 21/43 on dominant format; PHONE_NUMBER 10/17 on dominant format; ADDR_POSTAL_CODE 28/29 on dominant format; ADDR_STATE 27/28 on dominant format; SSN_NUMBER 9/10 on dominant format; GENDER 13/19 on dominant format; ADDR_COUNTRY 4/5 on dominant format; PASSPORT_COUNTRY 6/8 on dominant format; DRIVERS_LICENSE_STATE 4/5 on dominant format
- Business fields outside FEATURES (payload/ignore decision needed): CONTRACT_NUMBER, PRODUCT_TYPE, PARTY_ROLE, ISSUE_DATE, CONTRACT_STATUS, FACE_AMOUNT, MODAL_PREMIUM, PREMIUM_MODE, ACCOUNT_VALUE, CUSTOMER_TIER
- 3 records carry no RECORD_TYPE

**Reason:** records already use a FEATURES array with Entity Specification attribute names; mapping confirms this and decides the business fields.

**Record type check:** 0 of 67 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 3: CREDIT_BUREAU

**Status:** Entity Specification-compliant (structure)
**Location:** `data/raw/credit_header.jsonl`
**Records:** 30 (PERSON 30)
**Fields:** 6 Senzing features in use; 2 business fields outside FEATURES

**Evaluation:**

- Quality 89.8 — ✅ Strong (≥80); completeness 87.2, format consistency 94.9, duplicate rate 0.0
- Formats: DATE_OF_BIRTH 19/30 on dominant format
- Business fields outside FEATURES (payload/ignore decision needed): FILE_SINCE, FILE_STATUS

**Reason:** records already use a FEATURES array with Entity Specification attribute names; mapping confirms this and decides the business fields.

**Record type check:** 0 of 30 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 4: BUSINESS_REGISTRY

**Status:** Entity Specification-compliant (structure)
**Location:** `data/raw/business_registry.jsonl`
**Records:** 22 (ORGANIZATION 6, PERSON 16)
**Fields:** 7 Senzing features in use; 3 business fields outside FEATURES

**Evaluation:**

- Quality 67.8 — ⚠ Recommend fixing before mapping (<70); completeness 58.3, format consistency 87.9, duplicate rate 0.0
- Formats: PHONE_NUMBER 3/8 on dominant format; ADDR_POSTAL_CODE 6/7 on dominant format; ADDR_COUNTRY 5/6 on dominant format
- Business fields outside FEATURES (payload/ignore decision needed): REGISTRY_ROLE, REGISTRY_STATUS, INCORPORATED

**Reason:** records already use a FEATURES array with Entity Specification attribute names; mapping confirms this and decides the business fields.

**Record type check:** 0 of 16 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 5: SIU_CASES

**Status:** Entity Specification-compliant (structure)
**Location:** `data/raw/siu_cases.jsonl`
**Records:** 13 (PERSON 13)
**Fields:** 6 Senzing features in use; 6 business fields outside FEATURES

**Evaluation:**

- Quality 62.1 — ⚠ Recommend fixing before mapping (<70); completeness 48.7, format consistency 92.0, duplicate rate 0.0
- Formats: DATE_OF_BIRTH 5/7 on dominant format
- Business fields outside FEATURES (payload/ignore decision needed): SIU_CASE_NUMBER, REFERRAL_DATE, FRAUD_TYPE, ROLE_IN_CASE, LINE_OF_BUSINESS, CASE_DISPOSITION

**Reason:** records already use a FEATURES array with Entity Specification attribute names; mapping confirms this and decides the business fields.

**Record type check:** 0 of 13 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 6: SANCTIONS_PEP

**Status:** Entity Specification-compliant (structure)
**Location:** `data/raw/sanctions_pep.jsonl`
**Records:** 4 (PERSON 4)
**Fields:** 4 Senzing features in use; 6 business fields outside FEATURES

**Evaluation:**

- Quality 71.9 — ⚠ Acceptable — some gaps (70-79); completeness 68.8, format consistency 75.0, duplicate rate 0.0
- Formats: PHONE_NUMBER 1/2 on dominant format
- Business fields outside FEATURES (payload/ignore decision needed): LIST_TYPE, LIST_NAME, LISTED_DATE, SANCTIONS_PROGRAM, PEP_POSITION, PEP_COUNTRY

**Reason:** records already use a FEATURES array with Entity Specification attribute names; mapping confirms this and decides the business fields.

**Record type check:** 0 of 4 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

## Mapping Priority

1. PC_POLICY_ADMIN — largest customer source (104) and the anchor of the customer 360
2. LIFE_ANNUITY_ADMIN — the second customer system; the cross-sell question depends on it
3. CREDIT_BUREAU — identity verification; highest quality and strongest SSN overlap with the customer systems
4. BUSINESS_REGISTRY — carries the disclosed ownership relationships ownership screening needs
5. SIU_CASES — fraud history for screening
6. SANCTIONS_PEP — smallest; screening list

## Cross-Source Outlook

Measured as distinct exact, normalized values shared on the named attribute (names lower-cased with punctuation removed; SSN and phone digits; email lower-cased). This undercounts what Senzing will match: nicknames, transliterated names, partial values and disclosed relationships are not counted.

- PC_POLICY_ADMIN × LIFE_ANNUITY_ADMIN — **measured**
  - Shared attribute: EMAIL
  - Distinct values shared: 15 (of 17 in PC_POLICY_ADMIN, 18 in LIFE_ANNUITY_ADMIN); all attributes: {'NAME': 11, 'SSN': 2, 'PHONE': 8, 'EMAIL': 15}
- PC_POLICY_ADMIN × CREDIT_BUREAU — **measured**
  - Shared attribute: SSN
  - Distinct values shared: 13 (of 25 in PC_POLICY_ADMIN, 23 in CREDIT_BUREAU); all attributes: {'NAME': 11, 'SSN': 13, 'PHONE': 7, 'EMAIL': 0}
- PC_POLICY_ADMIN × SIU_CASES — **measured**
  - Shared attribute: NAME
  - Distinct values shared: 4 (of 98 in PC_POLICY_ADMIN, 11 in SIU_CASES); all attributes: {'NAME': 4, 'SSN': 2, 'PHONE': 0, 'EMAIL': 2}
- LIFE_ANNUITY_ADMIN × CREDIT_BUREAU — **measured**
  - Shared attribute: SSN
  - Distinct values shared: 3 (of 10 in LIFE_ANNUITY_ADMIN, 23 in CREDIT_BUREAU); all attributes: {'NAME': 2, 'SSN': 3, 'PHONE': 0, 'EMAIL': 0}
- LIFE_ANNUITY_ADMIN × SIU_CASES — **measured**
  - Shared attribute: NAME
  - Distinct values shared: 2 (of 72 in LIFE_ANNUITY_ADMIN, 11 in SIU_CASES); all attributes: {'NAME': 2, 'SSN': 0, 'PHONE': 0, 'EMAIL': 1}
- PC_POLICY_ADMIN × BUSINESS_REGISTRY — **measured**
  - Shared attribute: PHONE
  - Distinct values shared: 2 (of 28 in PC_POLICY_ADMIN, 6 in BUSINESS_REGISTRY); all attributes: {'NAME': 0, 'SSN': 0, 'PHONE': 2, 'EMAIL': 0}
- BUSINESS_REGISTRY × SANCTIONS_PEP — **measured**
  - Shared attribute: PHONE
  - Distinct values shared: 2 (of 6 in BUSINESS_REGISTRY, 2 in SANCTIONS_PEP); all attributes: {'NAME': 0, 'SSN': 0, 'PHONE': 2, 'EMAIL': 0}
- PC_POLICY_ADMIN × SANCTIONS_PEP — **measured**
  - Shared attribute: PHONE
  - Distinct values shared: 1 (of 28 in PC_POLICY_ADMIN, 2 in SANCTIONS_PEP); all attributes: {'NAME': 0, 'SSN': 0, 'PHONE': 1, 'EMAIL': 0}
- LIFE_ANNUITY_ADMIN × BUSINESS_REGISTRY — **measured**
  - Shared attribute: PHONE
  - Distinct values shared: 1 (of 14 in LIFE_ANNUITY_ADMIN, 6 in BUSINESS_REGISTRY); all attributes: {'NAME': 0, 'SSN': 0, 'PHONE': 1, 'EMAIL': 0}
- BUSINESS_REGISTRY × the customer sources — **candidate, overlap unmeasured** on names/identifiers (0 exact values shared); its links are expected through disclosed relationships (REL_POINTER/REL_ANCHOR) and fuzzy name matches, which this count cannot see
- SANCTIONS_PEP × the customer sources — **candidate, overlap unmeasured** for names written in non-Latin script, which the exact count drops; 1 phone value is shared with PC_POLICY_ADMIN and 2 with BUSINESS_REGISTRY
