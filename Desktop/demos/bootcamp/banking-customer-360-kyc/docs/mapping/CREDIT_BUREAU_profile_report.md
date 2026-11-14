**File Type:** jsonl
**Root Records:** 30
**Total Fields:** 22

## Structure Variations

**Distinct field patterns:** 2 (across 30 records)
**Common fields:** DATA_SOURCE, FEATURES, FILE_STATUS, RECORD_ID
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 19 (63%) | — (only common fields) |
| 2 | 11 (37%) | FILE_SINCE |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: credit_header

**Record Count:** 30
**List Elements (Tables):** 1
**Field Count:** 22

**Data Elements (All Lists):**
- FEATURES: 30 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 30 | 100.0% | 1 | 3.3% | root (30) | CREDIT_BUREAU (30) |  |  |  |  |
| 2 | RECORD_ID | str | 30 | 100.0% | 30 | 100.0% | root (30) | 2171 (1) | 3001 (1) | 3005 (1) | 3007 (1) | 3009 (1) |
| 3 | FEATURES | list | 30 |  |  |  | root (30) | 6 items (14) | 7 items (9) | 5 items (4) | 8 items (3) |  |
| 4 | FEATURES.RECORD_TYPE | str | 30 | 100.0% | 1 | 3.3% | FEATURES (30) | PERSON (30) |  |  |  |  |
| 5 | FEATURES.NAME_TYPE | str | 2 | 6.7% | 2 | 100.0% | FEATURES (30) | PRIMARY (1) | AKA (1) |  |  |  |
| 6 | FEATURES.NAME_LAST | str | 31 | 103.3% | 28 | 90.3% | FEATURES (30) | Pruitt (2) | Farraday (2) | Ellis (2) | Whitfield (1) | Reyes (1) |
| 7 | FEATURES.NAME_FIRST | str | 31 | 103.3% | 27 | 87.1% | FEATURES (30) | Gloria (2) | Marcus (2) | Rosalind (2) | Margaret (2) | Helen (1) |
| 8 | FEATURES.NAME_MIDDLE | str | 2 | 6.7% | 2 | 100.0% | FEATURES (30) | Ann (1) | A (1) |  |  |  |
| 9 | FEATURES.DATE_OF_BIRTH | str | 30 | 100.0% | 30 | 100.0% | FEATURES (30) | 2/14/1961 (1) | 3/9/1958 (1) | 7/4/1955 (1) | 5/21/1970 (1) | 8/8/1983 (1) |
| 10 | FEATURES.SSN_NUMBER | str | 24 | 80.0% | 23 | 95.8% | FEATURES (30) | 677-42-3131 (2) | 521-21-2123 (1) | 612-44-1001 (1) | 644-20-7788 (1) | 655-31-2020 (1) |
| 11 | FEATURES.ADDR_TYPE | str | 31 | 103.3% | 2 | 6.5% | FEATURES (30) | HOME (30) | MAILING (1) |  |  |  |
| 12 | FEATURES.ADDR_LINE1 | str | 31 | 103.3% | 24 | 77.4% | FEATURES (30) | 1500 Willow Glen Rd (7) | 12 Beacon St (2) | 4412 E Larkspur Ave (1) | 90 Sanborn Rd (1) | 2210 Birchwood Ln (1) |
| 13 | FEATURES.ADDR_CITY | str | 31 | 103.3% | 22 | 71.0% | FEATURES (30) | San Jose (7) | Fresno (2) | Albany (2) | Boston (2) | Tucson (1) |
| 14 | FEATURES.ADDR_STATE | str | 31 | 103.3% | 15 | 48.4% | FEATURES (30) | CA (11) | AZ (3) | ME (2) | NY (2) | NV (2) |
| 15 | FEATURES.ADDR_POSTAL_CODE | str | 31 | 103.3% | 22 | 71.0% | FEATURES (30) | 95125 (7) | 93701 (2) | 12207 (2) | 02108 (2) | 85712 (1) |
| 16 | FEATURES.PHONE_TYPE | str | 24 | 80.0% | 2 | 8.3% | FEATURES (30) | MOBILE (13) | HOME (11) |  |  |  |
| 17 | FEATURES.PHONE_NUMBER | str | 24 | 80.0% | 24 | 100.0% | FEATURES (30) | 520-555-0177 (1) | 208-555-0141 (1) | 207-555-0161 (1) | 559-555-0110 (1) | 608-555-0142 (1) |
| 18 | FEATURES.GENDER | str | 20 | 66.7% | 2 | 10.0% | FEATURES (30) | F (13) | M (7) |  |  |  |
| 19 | FEATURES.REL_ANCHOR_DOMAIN | str | 1 | 3.3% | 1 | 100.0% | FEATURES (30) | REFERENCE (1) |  |  |  |  |
| 20 | FEATURES.REL_ANCHOR_KEY | str | 1 | 3.3% | 1 | 100.0% | FEATURES (30) | 3048 (1) |  |  |  |  |
| 21 | FILE_SINCE | str | 11 | 36.7% | 2 | 18.2% | root (30) | 1998-01 (10) | 1979-03 (1) |  |  |  |
| 22 | FILE_STATUS | str | 30 | 100.0% | 1 | 3.3% | root (30) | Active (30) |  |  |  |  |
