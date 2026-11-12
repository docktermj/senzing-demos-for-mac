# Data Source Evaluation Report

**Date:** 2026-11-09
**Project:** test-claude-plugin-35 (Customer 360, synthetic)

## Summary

- Total data sources: 3
- Entity Specification-compliant: 0
- Needs mapping: 3
- Needs enrichment: 0

No source carries a record type, a date of birth or an identifier; matching will rest on name, address, phone and email.

## Data Source Details

### Data Source 1: CRM_SYSTEM

**Status:** Needs mapping
**Location:** `data/raw/crm_system.csv`
**Records:** 4,000
**Fields:** 7 columns
**Quality:** 94.8% (completeness 94.7%, format consistency 94.0%, duplicate rate 0.0%) — band >=80

**Evaluation:**

- `crm_id` is the record key (`RECORD_ID`); `DATA_SOURCE` is derived as `CRM_SYSTEM`.
- `full_name` is a single name field, so it corresponds to `NAME_FULL` (the specification says not to parse it).
- `address` is a single free-text field, so it corresponds to `ADDR_FULL`.
- `phone` corresponds to `PHONE_NUMBER`; `email` to `EMAIL_ADDRESS`.
- `created_date` and `campaign` have no specification counterpart (payload candidates).
- 300 records are campaign duplicates of another CRM record, each with its own key.

**Reason:** Field names differ from the Entity Specification and the records are flat CSV rows, not Senzing JSON.

**Record type check:** 0 of 0 PERSON-typed records are type/name candidates (no record-type field). Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 2: BILLING_SYSTEM

**Status:** Needs mapping
**Location:** `data/raw/billing_system-regenerated.csv` (original: `data/raw/billing_system.csv`)
**Records:** 4,000
**Fields:** 10 columns
**Quality:** 90.2% (completeness 86.0%, format consistency 100.0%, duplicate rate 0.0%) — band >=80. History: 76.0 as collected; 77.7 after Step 7a format normalization; 90.2 after regeneration on the return to Data collection.

**Evaluation:**

- `billing_id` is the record key; `DATA_SOURCE` is derived as `BILLING_SYSTEM`.
- `first_name` / `last_name` are parsed name parts (`NAME_FIRST` / `NAME_LAST`).
- `street`, `city`, `state`, `zip` are parsed address parts (`ADDR_LINE1`, `ADDR_CITY`, `ADDR_STATE`, `ADDR_POSTAL_CODE`).
- `phone` corresponds to `PHONE_NUMBER`, in four formats.
- `plan` and `last_invoice_date` have no specification counterpart (payload candidates).
- As collected, the address block was absent on about 45% of records and phone on about 61%. The regenerated file narrows that to about 18% and 36%.

**Reason:** Field names differ from the Entity Specification and the records are flat CSV rows, not Senzing JSON.

**Record type check:** 0 of 0 PERSON-typed records are type/name candidates (no record-type field). Decision: none needed

**Next step:** Phase 2 (mapping)

### Data Source 3: SUPPORT_TICKETS

**Status:** Needs mapping
**Location:** `data/raw/support_tickets.csv`
**Records:** 2,000
**Fields:** 5 columns
**Quality:** 95.7% (completeness 96.3%, format consistency 93.0%, duplicate rate 0.0%) — band >=80

**Evaluation:**

- `ticket_id` is the record key; `DATA_SOURCE` is derived as `SUPPORT_TICKETS`.
- `name` is a single name field (`NAME_FULL`), with inconsistent casing and occasional typos.
- `email` corresponds to `EMAIL_ADDRESS`, with occasional typos.
- `subject` and `opened_date` have no specification counterpart (payload candidates).
- 400 records are repeat tickets from someone who already filed one.

**Reason:** Field names differ from the Entity Specification and the records are flat CSV rows, not Senzing JSON.

**Record type check:** 0 of 0 PERSON-typed records are type/name candidates (no record-type field). Decision: none needed

**Next step:** Phase 2 (mapping)

## Mapping Priority

1. CRM_SYSTEM — strongest source with the widest feature set (name, address, phone, email); it links to Billing by phone and name and to Support by email.
2. SUPPORT_TICKETS — strong but sparse; its email links it to CRM.
3. BILLING_SYSTEM — regenerated to 90.2; parsed name and address parts, so the mapping is mostly direct.

## Cross-Source Outlook

Distinct values compared after light normalization (phone: last 10 digits; email: lowercase; name: lowercase letters, single-letter initials dropped). Exact-value overlap is a floor: nicknames, typos and formatting variants that Senzing can still match are not counted. Names include 20 deliberate pairs of different people who share a name.

- CRM_SYSTEM × BILLING_SYSTEM — **measured**
  - Shared attribute: phone
  - Distinct values shared: 1,226 (of 3,299 in CRM_SYSTEM, 2,546 in BILLING_SYSTEM)
- CRM_SYSTEM × BILLING_SYSTEM — **measured**
  - Shared attribute: name
  - Distinct values shared: 2,020 (of 3,700 in CRM_SYSTEM, 3,713 in BILLING_SYSTEM)
- CRM_SYSTEM × SUPPORT_TICKETS — **measured**
  - Shared attribute: email
  - Distinct values shared: 728 (of 3,351 in CRM_SYSTEM, 1,518 in SUPPORT_TICKETS)
- CRM_SYSTEM × SUPPORT_TICKETS — **measured**
  - Shared attribute: name
  - Distinct values shared: 848 (of 3,700 in CRM_SYSTEM, 1,604 in SUPPORT_TICKETS)
- BILLING_SYSTEM × SUPPORT_TICKETS — **measured**
  - Shared attribute: name
  - Distinct values shared: 717 (of 3,713 in BILLING_SYSTEM, 1,604 in SUPPORT_TICKETS)
