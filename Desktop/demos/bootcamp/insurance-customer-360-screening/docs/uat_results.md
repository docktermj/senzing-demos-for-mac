# UAT Results — Data processing

**Run:** 2026-11-14T15:15:16-05:00 · **Tester:** guide-directed (the bootcamper chose not to involve business users) · **Data:** all six sources,
248 records, loaded and redo-drained.

## Test cases from the business problem's success criteria

| # | Success criterion | Evidence | Result |
|---|---|---|---|
| 1 | One resolution of all six sources | 248 records from all six sources in one repository, 157 entities, 63 spanning sources | ✅ Pass |
| 2 | The 179 customer records collapse into the real customers, with a count of customers holding both lines | 179 PC_POLICY_ADMIN + LIFE_ANNUITY_ADMIN records → 127 customers; 38 hold both P&C and life/annuity | ✅ Pass |
| 3 | Screening catches what name screening alone misses (a business owned by a sanctioned person) | Entity 400003 (OFAC BELARUS, Cyrillic name) = business-registry owner of 100% of Müllenkranz (400011) = a P&C policyholder; Müllenkranz is itself a P&C customer | ✅ Pass |
| 4 | No false hits for common names | Robert Smith, John Smith, Patricia Smith, Jay Jones records with name-only agreement stay separate entities, linked as possible matches (+NAME) for an analyst | ✅ Pass |
| 5 | Every alert can carry its evidence | Each entity returns its records, per-record match key and rule; relationships carry match level and key (disclosed roles included) | ✅ Pass (presentation built in Query, Visualize and Discover) |

**5 of 5 test cases pass.**

## Spot-check: 10 cross-source entities

Records shown from the load files; match keys from Senzing.

| Entity | Records | Evidence | Verdict |
|---|---|---|---|
| 400003 ВАСІЛЬЕЎ Аляксандр Паўлавіч | SANCTIONS_PEP 2062, BUSINESS_REGISTRY 2061, PC_POLICY_ADMIN 2063 | +NAME+ADDRESS+PHONE (same Smolensk address, same phone digits); +NAME+PHONE to the romanized "Alexander Pavlovich Vasiliev" | Correct — sanctions hit on a policyholder |
| 400001 فيصل صديقي | SANCTIONS_PEP 2052, BUSINESS_REGISTRY 2051 | +NAME+ADDRESS+PHONE (Lahore address, same phone); Arabic script ↔ "Faisal Siddiqui" | Correct — cross-script |
| 400030 Maria Luis Medina Sentosa | 7 records: PC ×3, LIFE ×2, SIU ×2 | +NAME+DOB+ADDRESS, +DOB+EMAIL+PNAME; one key -PASSPORT (P71590264 vs P48213977) | Correct, flagged: two passport numbers for one person — relevant to the SIU |
| 400015 Eddie Kusha | PC 1009, PC 1011, LIFE 1010, SIU 1012, SIU 1014 | +NAME+DOB+ADDRESS; SIU 1014 "Eddie Knight" joins on +DOB+SSN (294-66-9999 = PC 1009) | Correct, flagged: alias "Knight" with a second address, tied by SSN |
| 400016 Robert Smith | LIFE 1002, PC 1001, PC 1003, LIFE 1004 | +NAME+DOB+PHONE, +NAME+DOB+EMAIL, +NAME+DOB+ADDRESS (Bob/Robert, DOB day-month transposed) | Correct |
| 500049 Susan Mooney | 6 records PC/LIFE | +NAME+DRLIC, +NAME+PASSPORT, +NAME+SSN (521212123 = 521-21-2123), +NAME+ADDRESS | Correct |
| 200004 Marcus Ellery | CREDIT_BUREAU 3007, LIFE 3008 | +NAME+DOB+SSN | Correct |
| 400007 Universal Exports Worldwide | BUSINESS_REGISTRY 2074, PC 2073 "Worldwide Exports" | +NAME+ADDRESS (405 Lexington Avenue, 10174) | Correct |
| 500022 WONDERLAND | PC 3025, LIFE 3024 | +NAME+DOB+TAX_ID (AMELV9997R); PC 3025 typed ORGANIZATION by the bootcamper's mapping decision | Correct |
| 400002 Margaret Charney | SANCTIONS_PEP 2092 "Charney, Peggie", BUSINESS_REGISTRY 2091 | +NAME+EMPLOYER (Universal Exports / Universal Exports Worldwide) | Plausible, flagged: name variant and employer only |

**10 of 10 correct**, 3 flagged for analyst attention (a duplicate passport, an alias, a thin PEP match). None is a wrong merge.

## Wider review

All 63 cross-source entities were reviewed against their records: 63 plausible, 4 flagged (the three above plus
400004 Howard Hughes, also +NAME+EMPLOYER). All 36 possible-match pairs were reviewed: none shows a mapping-driven
miss; pairs are held apart by real conflicts (different DOB under a shared SSN, Sr/Jr, name-only or tax-ID-only).

## Issues

- None blocking. Shared-SSN pairs with different birth dates (e.g. 200005/200006, 500081/500090, 200001/500049) are
  screening signals for identity verification, not resolution defects.
