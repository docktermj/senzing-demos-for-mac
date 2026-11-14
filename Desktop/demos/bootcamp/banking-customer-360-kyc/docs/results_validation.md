# Results validation — Data processing

**Date:** 2026-11-14

## Load

| Source | Records loaded | Load input | Collected | Reconciliation |
|---|---|---|---|---|
| CREDIT_BUREAU | 30 | 30 | 30 | pass |
| BUSINESS_REGISTRY | 22 | 22 | 22 | pass |
| WATCHLIST | 17 | 17 | 17 | pass |
| CORE_BANKING | 97 | 97 | 97 | pass |
| CARD_SERVICES | 82 | 82 | 82 | pass |
| **Total** | **248** | 248 | 248 | per-source counts sum to the total |

0 failed records; redo queue drained after each load stage (0, then 5 processed) and reached empty.

## Entities

- **Entities created:** 157 from 248 records (the export accounts for every loaded record).
- **Entity sizes:** 88 single-record, 58 with 2 records, 9 with 3-5, 2 with 6-10.
- **Per source (records -> entities, compression):** BUSINESS_REGISTRY 22 -> 17 (22.7%); CARD_SERVICES 82 -> 77 (6.1%); CORE_BANKING 97 -> 88 (9.3%); CREDIT_BUREAU 30 -> 30 (0.0%); WATCHLIST 17 -> 14 (17.6%)
- **Cross-source entities:** 63. By source pair: CARD_SERVICES + CORE_BANKING 38; CORE_BANKING + CREDIT_BUREAU 11; BUSINESS_REGISTRY + CARD_SERVICES 6; CORE_BANKING + WATCHLIST 6; BUSINESS_REGISTRY + CORE_BANKING 5; BUSINESS_REGISTRY + WATCHLIST 4; CARD_SERVICES + WATCHLIST 3; CARD_SERVICES + CREDIT_BUREAU 2
- **Relationships (deduplicated pairs):** 140: POSSIBLY_SAME 36, POSSIBLY_RELATED 86, DISCLOSED 18.
- **Top match keys:** +NAME+DOB+ADDRESS (11); +NAME+ADDRESS (10); +NAME+DOB+SSN (7); +NAME+DOB (6); +NAME+DOB+PHONE+SSN (5); +NAME+EMAIL (4); +NAME+ADDRESS+PHONE (3); +NAME+PHONE (3)

## Match quality against the answer key

- Real-world entities resolved exactly: **147 of 156 (94.2%)**.
- Pair precision **97.8%**, pair recall **92.2%** (131 correct pairs of 134 found, 142 in the key).
- False positives: 3 entities (bureau file + customer the key separates). False negatives: clusters 1301 and 3021 (the key mixes a person and an organization, which Senzing never merges) and cluster 2171 (likely key artifact). Evidence in `docs/uat_results.md`.
- Cross-source match rate: 63 of 157 entities (40.1%) combine 2+ sources; 57 of 63 match the key exactly.

## UAT and sign-off

- UAT self-directed (no named stakeholders in `docs/business_problem.md`; synthetic data): 9 of 10 test cases pass, the tenth at 90.5% (`docs/uat_results.md`).
- Stakeholder sign-off: not applicable (no stakeholders recorded). Open item for a real deployment: compliance review of the three bureau-file merges.

## Match-key audit

- Read from the SDK export (`SZ_EXPORT_DEFAULT_FLAGS`); the first row carried `RELATED_ENTITIES`, so per-record and relationship keys came from one pass.
- **Single-source per-record:** 6 comparisons, 0 suppressors.
- **Cross-source per-record:** 83 comparisons, 2 suppressors (-PASSPORT 1, -GENDER 1; 2.4%). No mapping signal.
- **Relationship:** 140 deduplicated relationships; -DOB on 71 (50.7%), -GENDER 25, -SSN 21, -PASSPORT 3, -TAX_ID 3, -GENERATION 2, -RECORD_TYPE 1. Finding: Senzing declined to merge these pairs because date of birth disagreed. The candidates were raised by shared household features (surname, address, email) and common names, which the business problem itself says support household links but are not proof of identity. This is the engine's restraint, not a mapping error.
- **Outcome:** finding (relationship bucket only); per-record buckets clean.

## How-state audit

- **Outcome:** no finding.
- Checked 69 of 69 multi-record entities with `howEntity` (default flags); every response carried `HOW_RESULTS.FINAL_STATE`.
- 0 unsettled (no `NEED_REEVALUATION`, no entity with more than one virtual entity).
