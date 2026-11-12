# UAT Results — Data processing

**Date:** 2026-11-09
**Scenario:** bootcamp-generated Customer 360 (no business users to involve; UAT self-directed against the scenario's answer key `data/ground_truth/scenario_ground_truth.csv`).

## Headline figures

- **UAT: 20 of 20 spot-checked cross-source entities correct (100%)** — step 25's cross-source spot-check, which replaces step 22's single-source figure (10 of 10 multi-record entities correct, 100%).
- **Per-record match accuracy: 48 of 52 reviewed records correct (92%)** — sample: the 7 records of step 21's expectation check (judged against the Bootcamper's stated expectations; 3 of 3 examples agreed) plus the 45 records of the 20 cross-source entities sampled at step 24 (judged against the answer key; a record is correct only when its entity holds exactly its person's records). The 4 incorrect records are two CRM+Billing pairs (CRM-000045/BIL-000045, CRM-000138/BIL-000138) whose person also has a Support ticket that stayed in a separate entity: correct merges, incomplete ones.
- **Findings (not routing): pairwise precision 100.0%, recall 80.9%, F1 89.5%** against the answer key — 4,371 of 4,371 resolved pairs correct; 5,402 true pairs; 0 wrong merges; 646 of 6,000 invented people still split across entities.

## Step 21 — expectation check (Bootcamper-directed)

| Example | Bootcamper expected | Senzing decided | Agreement |
|---|---|---|---|
| Jenna Rockman (CRM-000047, BIL-000047, TKT-000047) | CRM + Support one customer, Billing separate | CRM + Support entity 403401 (+NAME+EMAIL); Billing entity 802383, possibly same on +NAME | agreed |
| Andrea Dalewick (CRM-002411, BIL-002311) | two customers, linked as possibly same | entities 400912 and 801318, possibly same on +NAME; phone scored 0 (NO_CHANCE) | agreed |
| Timothy Thornstead (TKT-000412, TKT-001652) | one customer | entity 700970, +NAME+EMAIL | agreed |

## Step 22 — single-source spot-check (10 multi-record entities, seed 6)

| Entity | Records | Result |
|---|---|---|
| 400495 | CRM_SYSTEM:CRM-001240, BILLING_SYSTEM:BIL-001240 | correct |
| 402356 | CRM_SYSTEM:CRM-001978, CRM_SYSTEM:CRM-003817, BILLING_SYSTEM:BIL-001978 | correct |
| 402708 | CRM_SYSTEM:CRM-002365, SUPPORT_TICKETS:TKT-000865 | correct |
| 401542 | CRM_SYSTEM:CRM-000192, SUPPORT_TICKETS:TKT-000192, SUPPORT_TICKETS:TKT-001959, BILLING_SYSTEM:BIL-000192 | correct |
| 402969 | CRM_SYSTEM:CRM-002058, BILLING_SYSTEM:BIL-002058 | correct |
| 400502 | CRM_SYSTEM:CRM-001649, BILLING_SYSTEM:BIL-001649 | correct |
| 401174 | CRM_SYSTEM:CRM-001656, BILLING_SYSTEM:BIL-001656 | correct |
| 401820 | CRM_SYSTEM:CRM-001554, BILLING_SYSTEM:BIL-001554 | correct |
| 400524 | CRM_SYSTEM:CRM-000309, BILLING_SYSTEM:BIL-000309, SUPPORT_TICKETS:TKT-000309, SUPPORT_TICKETS:TKT-001659 | correct |
| 400195 | CRM_SYSTEM:CRM-000434, SUPPORT_TICKETS:TKT-000434, BILLING_SYSTEM:BIL-000434 | correct |

## Step 25 — cross-source spot-check (20 entities, seed 6)

| Entity | Records | Result |
|---|---|---|
| 400471 | CRM_SYSTEM:CRM-000643, BILLING_SYSTEM:BIL-000643 | correct |
| 403447 | CRM_SYSTEM:CRM-001830, BILLING_SYSTEM:BIL-001830 | correct |
| 401758 | CRM_SYSTEM:CRM-002163, SUPPORT_TICKETS:TKT-000663 | correct |
| 400161 | CRM_SYSTEM:CRM-001586, BILLING_SYSTEM:BIL-001586 | correct |
| 300002 | CRM_SYSTEM:CRM-001036, BILLING_SYSTEM:BIL-001036 | correct |
| 400926 | CRM_SYSTEM:CRM-000540, SUPPORT_TICKETS:TKT-000540, BILLING_SYSTEM:BIL-000540 | correct |
| 403337 | CRM_SYSTEM:CRM-002079, BILLING_SYSTEM:BIL-002079 | correct |
| 402619 | CRM_SYSTEM:CRM-002232, SUPPORT_TICKETS:TKT-001920, SUPPORT_TICKETS:TKT-000732 | correct |
| 402202 | CRM_SYSTEM:CRM-002048, BILLING_SYSTEM:BIL-002048 | correct |
| 400058 | CRM_SYSTEM:CRM-000138, BILLING_SYSTEM:BIL-000138 | correct |
| 401830 | CRM_SYSTEM:CRM-000736, BILLING_SYSTEM:BIL-000736 | correct |
| 403482 | CRM_SYSTEM:CRM-000770, BILLING_SYSTEM:BIL-000770 | correct |
| 401289 | CRM_SYSTEM:CRM-000423, SUPPORT_TICKETS:TKT-000423, BILLING_SYSTEM:BIL-000423 | correct |
| 402922 | CRM_SYSTEM:CRM-001158, BILLING_SYSTEM:BIL-001158 | correct |
| 400569 | CRM_SYSTEM:CRM-001712, CRM_SYSTEM:CRM-003888, BILLING_SYSTEM:BIL-001712 | correct |
| 401253 | CRM_SYSTEM:CRM-001099, BILLING_SYSTEM:BIL-001099 | correct |
| 401785 | CRM_SYSTEM:CRM-000045, BILLING_SYSTEM:BIL-000045 | correct |
| 400523 | CRM_SYSTEM:CRM-001214, BILLING_SYSTEM:BIL-001214 | correct |
| 402993 | CRM_SYSTEM:CRM-000818, BILLING_SYSTEM:BIL-000818 | correct |
| 402319 | CRM_SYSTEM:CRM-000351, BILLING_SYSTEM:BIL-000351, SUPPORT_TICKETS:TKT-000351 | correct |
