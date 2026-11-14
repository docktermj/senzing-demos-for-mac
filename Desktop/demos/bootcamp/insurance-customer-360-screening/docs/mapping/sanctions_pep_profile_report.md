**File Type:** jsonl
**Root Records:** 4
**Total Fields:** 18

## Structure Variations

**Distinct field patterns:** 2 (across 4 records)
**Common fields:** DATA_SOURCE, FEATURES, LISTED_DATE, LIST_NAME, LIST_TYPE, RECORD_ID
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 2 (50%) | SANCTIONS_PROGRAM |
| 2 | 2 (50%) | PEP_COUNTRY, PEP_POSITION |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: sanctions_pep

**Record Count:** 4
**List Elements (Tables):** 1
**Field Count:** 18

**Data Elements (All Lists):**
- FEATURES: 4 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 4 | 100.0% | 1 | 25.0% | root (4) | SANCTIONS_PEP (4) |  |  |  |  |
| 2 | RECORD_ID | str | 4 | 100.0% | 4 | 100.0% | root (4) | 2052 (1) | 2062 (1) | 2082 (1) | 2092 (1) |  |
| 3 | FEATURES | list | 4 |  |  |  | root (4) | 4 items (3) | 3 items (1) |  |  |  |
| 4 | FEATURES.RECORD_TYPE | str | 4 | 100.0% | 1 | 25.0% | FEATURES (4) | PERSON (4) |  |  |  |  |
| 5 | FEATURES.NAME_FULL | str | 4 | 100.0% | 4 | 100.0% | FEATURES (4) | فيصل صديقي (1) | ВАСИЛЬЕВ, Александр Павлович (1) | Hughes, Howie (1) | Charney, Peggie (1) |  |
| 6 | FEATURES.ADDR_TYPE | str | 2 | 50.0% | 2 | 100.0% | FEATURES (4) | PRIMARY (1) | HOME (1) |  |  |  |
| 7 | FEATURES.ADDR_LINE1 | str | 3 | 75.0% | 3 | 100.0% | FEATURES (4) | Jia Musa Shahdara Sheikhupura Road (1) | Tolmacheva Ul., bld. 8, appt. 71  (1) | 1231 Las Vegas Blvd, Penthouse Suite (1) |  |  |
| 8 | FEATURES.ADDR_CITY | str | 3 | 75.0% | 3 | 100.0% | FEATURES (4) | Lahore (1) | Smolensk (1) | Las Vegas (1) |  |  |
| 9 | FEATURES.ADDR_COUNTRY | str | 1 | 25.0% | 1 | 100.0% | FEATURES (4) | Pakistan (1) |  |  |  |  |
| 10 | FEATURES.PHONE_NUMBER | str | 2 | 50.0% | 2 | 100.0% | FEATURES (4) | 92-42-7925774 (1) | (4812)85-62-34 (1) |  |  |  |
| 11 | FEATURES.ADDR_STATE | str | 1 | 25.0% | 1 | 100.0% | FEATURES (4) | NV (1) |  |  |  |  |
| 12 | FEATURES.EMPLOYER | str | 2 | 50.0% | 1 | 50.0% | FEATURES (4) | Universal Exports (2) |  |  |  |  |
| 13 | LIST_TYPE | str | 4 | 100.0% | 2 | 50.0% | root (4) | Sanctions (2) | PEP (2) |  |  |  |
| 14 | LIST_NAME | str | 4 | 100.0% | 2 | 50.0% | root (4) | OFAC SDN (2) | PEP database (2) |  |  |  |
| 15 | LISTED_DATE | str | 4 | 100.0% | 3 | 75.0% | root (4) | 2019-04-03 (2) | 2002-02-22 (1) | 2014-04-14 (1) |  |  |
| 16 | SANCTIONS_PROGRAM | str | 2 | 50.0% | 2 | 100.0% | root (4) | SDGT (1) | BELARUS (1) |  |  |  |
| 17 | PEP_POSITION | str | 2 | 50.0% | 2 | 100.0% | root (4) | State Senator (1) | Federal Judge (1) |  |  |  |
| 18 | PEP_COUNTRY | str | 2 | 50.0% | 1 | 50.0% | root (4) | US (2) |  |  |  |  |
