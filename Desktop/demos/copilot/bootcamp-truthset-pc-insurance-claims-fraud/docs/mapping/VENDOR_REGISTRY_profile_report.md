**File Type:** jsonl
**Root Records:** 22
**Total Fields:** 36

## Structure Variations

**Distinct field patterns:** 2 (across 22 records)
**Common fields:** CATEGORY, DATA_SOURCE, FEATURES, RECORD_ID, STATUS
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 16 (73%) | — (only common fields) |
| 2 | 6 (27%) | DATE |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: VENDOR_REGISTRY-improved

**Record Count:** 22
**List Elements (Tables):** 1
**Field Count:** 36

**Data Elements (All Lists):**
- FEATURES: 22 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 22 | 100.0% | 1 | 4.5% | root (22) | REFERENCE (22) |  |  |  |  |
| 2 | RECORD_ID | str | 22 | 100.0% | 22 | 100.0% | root (22) | 2012 (1) | 2013 (1) | 2014 (1) | 2041 (1) | 2051 (1) |
| 3 | FEATURES | list | 22 |  |  |  | root (22) | 4 items (7) | 6 items (6) | 5 items (3) | 7 items (3) | 3 items (2) |
| 4 | FEATURES.RECORD_TYPE | str | 22 | 100.0% | 2 | 9.1% | FEATURES (22) | PERSON (16) | ORGANIZATION (6) |  |  |  |
| 5 | FEATURES.NAME_ORG | str | 11 | 50.0% | 8 | 72.7% | FEATURES (22) | Universal Exports (4) | Hajah Maimunah (1) | Müllenkranz (1) | Autowerkz (1) | Universal Exports, USA (1) |
| 6 | FEATURES.ADDR_TYPE | str | 15 | 68.2% | 4 | 26.7% | FEATURES (22) | PRIMARY (8) | REGISTERED (4) | HOME (2) | BUSINESS (1) |  |
| 7 | FEATURES.ADDR_LINE1 | str | 9 | 40.9% | 9 | 100.0% | FEATURES (22) | 4 Hillview Rise (1) | Hardenbergstraße 87 (1) | Jia Musa Shahdara Sheikhupura Road (1) | 405 Lexington Avenue (1) | Fieldstrasse 10, FL-2198 Triesen (1) |
| 8 | FEATURES.ADDR_CITY | str | 8 | 36.4% | 7 | 87.5% | FEATURES (22) | Athens (2) | SINGAPORE (1) | Rheinland-Pfalz (1) | Lahore (1) | Manhattan (1) |
| 9 | FEATURES.ADDR_POSTAL_CODE | str | 7 | 31.8% | 6 | 85.7% | FEATURES (22) | 30009 (2) | 667979 (1) | 66879 (1) | 10174 (1) | 10012 (1) |
| 10 | FEATURES.ADDR_COUNTRY | str | 6 | 27.3% | 5 | 83.3% | FEATURES (22) | SINGAPORE (2) | GERMANY (1) | PAKISTAN (1) | RUS (1) | LICHTENSTEIN (1) |
| 11 | FEATURES.NATIONAL_ID_NUMBER | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | 201012345A (1) | 200912346B (1) |  |  |  |
| 12 | FEATURES.NATIONAL_ID_TYPE | str | 2 | 9.1% | 1 | 50.0% | FEATURES (22) | UEN (2) |  |  |  |  |
| 13 | FEATURES.NATIONAL_ID_COUNTRY | str | 2 | 9.1% | 1 | 50.0% | FEATURES (22) | SG (2) |  |  |  |  |
| 14 | FEATURES.REL_ANCHOR_DOMAIN | str | 4 | 18.2% | 1 | 25.0% | FEATURES (22) | REFERENCE (4) |  |  |  |  |
| 15 | FEATURES.REL_ANCHOR_KEY | str | 4 | 18.2% | 4 | 100.0% | FEATURES (22) | 2011 (1) | 2041 (1) | 2071 (1) | 2074 (1) |  |
| 16 | FEATURES.NAME_FULL | str | 14 | 63.6% | 14 | 100.0% | FEATURES (22) | Wang Jie (1) | Wang Wei (1) | Faisal Siddiqui (1) | ВАСІЛЬЕЎ, Аляксандр Паўлавіч (1) | Howard Hughes (1) |
| 17 | FEATURES.DATE_OF_BIRTH | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | 09/14/1993 (1) | 09/14/1997 (1) |  |  |  |
| 18 | FEATURES.REL_POINTER_DOMAIN | str | 14 | 63.6% | 1 | 7.1% | FEATURES (22) | REFERENCE (14) |  |  |  |  |
| 19 | FEATURES.REL_POINTER_KEY | str | 14 | 63.6% | 4 | 28.6% | FEATURES (22) | 2074 (5) | 2071 (5) | 2011 (2) | 2041 (2) |  |
| 20 | FEATURES.REL_POINTER_ROLE | str | 14 | 63.6% | 7 | 50.0% | FEATURES (22) | Principal (5) | Global Parent (3) | Owns 50% (2) | Owns 60% (1) | Owns 40% (1) |
| 21 | FEATURES.NAME_TYPE | str | 5 | 22.7% | 1 | 20.0% | FEATURES (22) | AKA (5) |  |  |  |  |
| 22 | FEATURES.TAX_ID_NUMBER | str | 4 | 18.2% | 4 | 100.0% | FEATURES (22) | DE123456789 (1) | 84-1234567 (1) | 13-7654321 (1) | 32AABCU9603R1ZM (1) |  |
| 23 | FEATURES.TAX_ID_TYPE | str | 4 | 18.2% | 3 | 75.0% | FEATURES (22) | EIN (2) | VAT (1) | GSTIN (1) |  |  |
| 24 | FEATURES.TAX_ID_COUNTRY | str | 4 | 18.2% | 3 | 75.0% | FEATURES (22) | US (2) | DE (1) | IN (1) |  |  |
| 25 | FEATURES.PHONE_NUMBER | str | 8 | 36.4% | 7 | 87.5% | FEATURES (22) | 3203922137 (2) | +92427925774 (1) | +74812856234 (1) | 8001111234 (1) | 8188922818 (1) |
| 26 | FEATURES.EMPLOYER | str | 9 | 40.9% | 3 | 33.3% | FEATURES (22) | Universal Exports, USA (5) | Müllenkranz (2) | Universal Exports Worldwide (2) |  |  |
| 27 | FEATURES.ADDR_FULL | str | 7 | 31.8% | 7 | 100.0% | FEATURES (22) | Tolmacheva Ul., bld. 8, appt. 71 Smolensk (1) | Hughes Plaza, 100 Howard Hughes Way, Las Vegas, NV (1) | Fieldstrasse 10, FL-2198 Triesen, Lichtenstein (1) | 18 Danver Place, Loughborough, Leicestershire, LE1 (1) | 18 Danver Place, Loughborough, Leicestershire, Uni (1) |
| 28 | FEATURES.PHONE_TYPE | str | 3 | 13.6% | 2 | 66.7% | FEATURES (22) | MOBILE (2) | PRIMARY (1) |  |  |  |
| 29 | FEATURES.ADDR_STATE | str | 5 | 22.7% | 3 | 60.0% | FEATURES (22) | NY (2) | GA (2) | CA (1) |  |  |
| 30 | FEATURES.LEI_NUMBER | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | 5493001KJTIIGC8Y1R12 (1) | 549300HKVFVS8QGCBR57 (1) |  |  |  |
| 31 | FEATURES.EMAIL_ADDRESS | str | 4 | 18.2% | 3 | 75.0% | FEATURES (22) | kjones@universal.com (2) | Nastassia<patak@universal.com> (1) | patak@universal.com (1) |  |  |
| 32 | FEATURES.NAME_LAST | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | Thomas (1) | Alexopoulos (1) |  |  |  |
| 33 | FEATURES.NAME_FIRST | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | Rosemary A (1) | Anastassia (1) |  |  |  |
| 34 | CATEGORY | str | 22 | 100.0% | 7 | 31.8% | root (22) | Owner (5) | Director (5) | Contact (5) | Corporation (4) | Proprietorship (1) |
| 35 | DATE | str | 6 | 27.3% | 5 | 83.3% | root (22) | 1990 (2) | 2010 (1) | 2009 (1) | 1994 (1) | 1998 (1) |
| 36 | STATUS | str | 22 | 100.0% | 3 | 13.6% | root (22) | Current (11) | Active (10) | Inactive (1) |  |  |
