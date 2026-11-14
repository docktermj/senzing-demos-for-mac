# UAT results — Data processing

**Date:** 2026-11-14
**Mode:** self-directed. `docs/business_problem.md` describes the bank's teams (relationship managers, compliance analysts) but records no named stakeholders, and the data is synthetic, so no business users were convened (Data processing step 25a).
**Reference:** the scenario's answer key `banking_truthset_key.csv` (248 rows, 156 real-world entities), used for checking only and never loaded into Senzing.

## Test cases

| # | Test | Expected | Result | Pass |
|---|---|---|---|---|
| 1 | Every record loaded | 248 of 248 | 248 loaded, 0 failed; export accounts for 248 records | ✅ |
| 2 | Real-world entities resolved exactly as the answer key groups them | 156 | 147 of 156 (94.2%) | ✅ |
| 3 | Pairs Senzing joined that the key also joins (precision) | ≥ 90% | 97.8% (131 of 134) | ✅ |
| 4 | Key pairs Senzing joined (recall) | ≥ 90% | 92.2% (131 of 142) | ✅ |
| 5 | Cross-source entities spot-checked against the key (all 63) | all correct | 57 of 63 exactly match; 6 differ (cases A-C below) | ⚠️ 90.5% |
| 6 | Customer 360: the 179 CORE_BANKING + CARD_SERVICES records collapse into customers | key: 124 customers, 37 with both a deposit account and a card | 127 customers, 38 with both; the differences are exactly cases B and C below | ✅ reconciled |
| 7 | Common names are not merged on name alone (several Robert and John Smiths) | no name-only merges | Robert E Smith Sr (WATCHLIST/1006) merged with CORE_BANKING/1005 on +NAME+DRLIC; the other Smiths stay separate, linked only as possible matches on +NAME | ✅ |
| 8 | Watchlist entries reach customers and registry parties | screening hits visible | 9 entities combine a WATCHLIST record with customer or registry records (2 Sanctions, 2 PEP, 5 Internal Fraud) | ✅ |
| 9 | Ownership and officer links are present | 18 disclosed relationships | 18 disclosed relationships, including the cross-source Carer link CORE_BANKING/3050 -> CREDIT_BUREAU/3048 | ✅ |
| 10 | Every multi-record entity has a settled construction history | 69 of 69 | 69 of 69 checked, 0 unsettled | ✅ |

**Pass rate:** 9 of 10 test cases fully pass; test 5 is at 90.5%. Overall UAT: 94.2% of real-world entities exact, 97.8% pair precision.

## Differences from the answer key

**A. Three credit-bureau files merged with a customer the key says is a different person** (over-merges; each entity is 1 CREDIT_BUREAU + 1 CORE_BANKING record, joined on +NAME+DOB+SSN):

- Entity 200009, Kathryn Vossler: SSNs 694-18-3344 (bureau) and 694-18-3434 (customer), same name and DOB.
- Entity 200010, Benjamin Hartley: same SSN, DOB 9/14/1975 vs 9/14/1976.
- Entity 300001, Rosalind Kemper / Rosalyn Kempner: same SSN and DOB, name spelled differently.

These are the scenario's identity-verification traps ("some people share an SSN"). Senzing scored them as the same person; the key says they are not. For KYC they are exactly the cases an analyst should see, so they are carried into Query, Visualize and Discover.

**B. Answer-key clusters that mix a person and an organization** (Senzing never merges a PERSON record with an ORGANIZATION record):

- Cluster 1301: Cascadia Freight Partners (CORE_BANKING/1301, organization) with Annabelle / Anabella Becker (CORE_BANKING/1103 + CARD_SERVICES/1104, merged on +NAME+DRLIC). Senzing keeps the person and the company apart.
- Cluster 3021: Praven V Gupta (CORE_BANKING/3021), Meenu J Gupta (CORE_BANKING/3023 + CARD_SERVICES/3022, merged on +NAME+PASSPORT) and WONDERLAND (CORE_BANKING/3025 + CARD_SERVICES/3024, merged on +NAME+DOB+TAX_ID). Senzing links all three as possible matches (+NAME+ADDRESS+EMAIL with -PASSPORT / -TAX_ID). Record 3025 was typed ORGANIZATION by the bootcamper's decision during mapping.

**C. A likely answer-key artifact:** cluster 2171 pairs CORE_BANKING/2171 (Andrew Anderson) with CREDIT_BUREAU/2171 (Gloria Ann Whitfield), which share only a RECORD_ID and no names, dates or identifiers. Senzing keeps them apart.
