# Data Source Evaluation Report

**Date:** 2026-10-06
**Project:** test-claude-26 (Customer 360, bootcamp-generated synthetic data)

## Summary

- Total data sources: 3
- Entity Specification-compliant: 0
- Needs mapping: 3
- Needs enrichment: 0

## Data Source Details

### Data Source 1: CRM_CONTACTS

**Status:** Needs mapping
**Location:** `data/raw/crm_contacts.csv`
**Records:** 4,000
**Fields:** 6 columns

**Evaluation:**

- `contact_id` -> `RECORD_ID`; `full_name` -> `NAME_FULL` (one name field, so a direct mapping); `email` -> `EMAIL_ADDRESS`; `phone` -> `PHONE_NUMBER`; `address` -> `ADDR_FULL` (one free-text address field).
- `created_date` has no Entity Specification counterpart (a candidate for payload or ignore).
- No `RECORD_TYPE` field; `DATA_SOURCE` is derivable (CRM_CONTACTS).
- Quality 93.4 (completeness 93.3, format 92.2, duplicates 0.0).

**Reason:** Raw export with non-specification field names.

**Record type check:** 0 of 0 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 2: WEB_ORDERS

**Status:** Needs mapping
**Location:** `data/raw/web_orders.csv`
**Records:** 4,500
**Fields:** 11 columns

**Evaluation:**

- `order_id` -> `RECORD_ID`; `first_name`/`last_name` -> `NAME_FIRST`/`NAME_LAST`; `email` -> `EMAIL_ADDRESS`; `phone` -> `PHONE_NUMBER`; `street`/`city`/`state`/`zip` -> `ADDR_LINE1`/`ADDR_CITY`/`ADDR_STATE`/`ADDR_POSTAL_CODE`.
- `order_date` and `order_total` have no counterpart.
- No `RECORD_TYPE` field; `DATA_SOURCE` is derivable (WEB_ORDERS).
- Quality 89.5 (completeness 86.0, format 97.1, duplicates 0.0).

**Reason:** Raw export with non-specification field names.

**Record type check:** 0 of 0 PERSON-typed records are type/name candidates. Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 3: SUPPORT_TICKETS

**Status:** Needs mapping
**Location:** `data/raw/support_tickets.jsonl`
**Records:** 1,500
**Fields:** 8 keys

**Evaluation:**

- `ticket_id` -> `RECORD_ID`; `requester_name` -> `NAME_FULL`; `requester_email` -> `EMAIL_ADDRESS`; `phone` -> `PHONE_NUMBER`; `city` -> `ADDR_CITY`; `postal_code` -> `ADDR_POSTAL_CODE`.
- `topic` and `opened_on` have no counterpart.
- No `RECORD_TYPE` field; `DATA_SOURCE` is derivable (SUPPORT_TICKETS).
- Quality 75.5 (completeness 66.5, format 95.8, duplicates 0.0); the gaps are deliberate in this generated source.

**Reason:** Raw export with non-specification field names; sparse.

**Record type check:** 0 of 0 PERSON-typed records are type/name candidates. Decision: none needed

**Quality gate (70-79):** the Bootcamper chose to continue to mapping, accepting the documented gaps: postal_code missing in 46% of records, city 50%, phone 49% (deliberate in this generated source); off-pattern phone (71) and opened_on (109) values left as received.

**Next step:** Phase 2 (mapping)

## Mapping Priority

1. WEB_ORDERS - largest source, with parsed names and addresses.
2. CRM_CONTACTS - strong quality; exercises single-field name and address mapping.
3. SUPPORT_TICKETS - sparsest; benefits most from the other sources being loaded first.

## Cross-Source Outlook

- CRM_CONTACTS x WEB_ORDERS — **measured**
  - Shared attribute: `EMAIL_ADDRESS`
  - Distinct values shared: 1,404 (of 3,531 in A, 4,122 in B); phone digits shared: 929
- WEB_ORDERS x SUPPORT_TICKETS — **measured**
  - Shared attribute: `EMAIL_ADDRESS`
  - Distinct values shared: 481 (of 4,122 in A, 1,207 in B); phone digits shared: 222
- CRM_CONTACTS x SUPPORT_TICKETS — **measured**
  - Shared attribute: `EMAIL_ADDRESS`
  - Distinct values shared: 390 (of 3,531 in A, 1,207 in B); phone digits shared: 224

Quality visual: `docs/visualizations/data_quality_assessment.html`
