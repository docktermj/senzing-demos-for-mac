# Results Validation — Data processing

**Validated:** 2026-11-14T15:15:16-05:00 · **Repository:** SQLite `database/G2C.db` · **Senzing SDK:** 4.4.4

## Load totals (reconciled)

| Source | Loaded | Load input | Collected | Reconciliation |
|---|---|---|---|---|
| CREDIT_BUREAU | 30 | 30 | 30 | pass |
| SANCTIONS_PEP | 4 | 4 | 4 | pass |
| BUSINESS_REGISTRY | 22 | 22 | 22 | pass |
| PC_POLICY_ADMIN | 104 | 104 | 104 | pass |
| LIFE_ANNUITY_ADMIN | 75 | 75 | 75 | pass |
| SIU_CASES | 13 | 13 | 13 | pass |
| **Total** | **248** | **248** | **248** | 0 failed, error log empty |

Redo: 5 processed after the multi-source load, 0 failed, queue empty. Records counted in the export: 248 (equals the
records loaded).

## Entity resolution

- **Entities:** 157 from 248 records. Sizes: 88 single-record, 58 two-record, 9 with 3–5, 2 with 6–10.
- **Entities per source / compression:** PC_POLICY_ADMIN 95 (8.7%), LIFE_ANNUITY_ADMIN 70 (6.7%), CREDIT_BUREAU 30 (0%),
  BUSINESS_REGISTRY 17 (22.7%), SIU_CASES 10 (23.1%), SANCTIONS_PEP 4 (0%).
- **Cross-source entities:** 63 (40% of entities). Pairs: LIFE+PC 38, CREDIT+PC 11, BUSINESS+PC 7, PC+SIU 5,
  BUSINESS+LIFE 4, BUSINESS+SANCTIONS 4, LIFE+SIU 3, CREDIT+LIFE 2, PC+SANCTIONS 1.
- **Relationships (deduplicated):** 140 — 36 possible matches, 86 possibly related, 18 disclosed.
- **Top match keys:** +NAME+DOB+ADDRESS (11), +NAME+ADDRESS (10), +NAME+DOB+SSN (7), +NAME+DOB (6),
  +NAME+DOB+PHONE+SSN (5), +NAME+EMAIL (4).
- **Customer 360:** 179 customer records → 127 customers; 38 hold both P&C and life/annuity.
- **Screening:** all 4 watchlist entries resolved to other sources — 2 sanctions entries to business-registry officers
  and owners (one also a P&C policyholder, the 100% owner of a P&C customer), 2 PEPs to 50% owners of Universal Exports
  Worldwide. 5 SIU case parties resolved to policyholders.

## Match quality

| Measure | Result | Basis |
|---|---|---|
| UAT test cases | 5 of 5 pass (100%) | `docs/uat_results.md` |
| Spot-check accuracy | 10 of 10 correct (100%) | `docs/uat_results.md` |
| Cross-source review | 63 of 63 plausible; 4 flagged for analyst attention | every cross-source entity reviewed against its records |
| False positives found | 0 | same review |
| False negatives found | 0 mapping-driven | all 36 possible-match pairs reviewed |
| True/false rates | precision on the reviewed set 100%; recall not measured | no answer key is loaded in this project, so missed matches can only be estimated from the possible-match review |

## Match-key audit

Outcome: **finding (relationship bucket only).**

| Bucket | Comparisons | Suppressors |
|---|---|---|
| Single-source merged records | 6 | none |
| Cross-source merged records | 84 | -GENDER 1 (1.2%), -PASSPORT 1 (1.2%) |
| Relationships (not merged) | 140 | -DOB 71 (50.7%), -GENDER 25 (17.9%), -SSN 21 (15.0%), -PASSPORT 3, -TAX_ID 3, -GENERATION 2, -RECORD_TYPE 1 |

The relationship share means Senzing **declined** to merge because date of birth (and sometimes SSN or gender)
disagreed. The features raising those pairs are mostly a shared ADDRESS (households, e.g. the Kusha and Smith families),
a shared SSN, a shared EMPLOYER and a shared TAX_ID. That is restraint working, not a mis-mapping. The question for the
business is whether those raising features are distinguishing enough: shared SSNs with different birth dates are
identity-verification signals worth a screening rule. Cross-source merged records show almost no disagreement, so no
mapping change is indicated. The reader was checked: export rows carry RELATED_ENTITIES and all 140 relationships were
read.

## Business validation and sign-off

- Business users were not involved (the bootcamper's choice); UAT was guide-directed against the business problem's
  success criteria.
- Sign-off: **not obtained from business stakeholders**; recorded as guide-validated. Resolution plan: carry the four
  flagged entities and the shared-SSN pairs into the screening queue built in Query, Visualize and Discover.

## How-state audit

- **Outcome:** no finding
- **Checked:** 69 of 69 multi-record entities (every one; `howEntity` with default flags)
- **0 unsettled** — no entity reported NEED_REEVALUATION, and none had more than one virtual entity.

This document is the validation baseline for Query, Visualize and Discover.
