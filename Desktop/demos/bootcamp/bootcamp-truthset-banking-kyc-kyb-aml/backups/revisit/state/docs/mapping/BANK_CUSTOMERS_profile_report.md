**File Type:** jsonl
**Root Records:** 120
**Total Fields:** 33

## Structure Variations

**Distinct field patterns:** 3 (across 120 records)
**Common fields:** DATA_SOURCE, FEATURES, RECORD_ID
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 90 (75%) | AMOUNT, DATE, STATUS |
| 2 | 21 (18%) | — (only common fields) |
| 3 | 9 (8%) | CATEGORY, DATE, STATUS |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: BANK_CUSTOMERS-regenerated

**Record Count:** 120
**List Elements (Tables):** 1
**Field Count:** 33

**Data Elements (All Lists):**
- FEATURES: 120 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 120 | 100.0% | 1 | 0.8% | root (120) | BANK_CUSTOMERS (120) |  |  |  |  |
| 2 | RECORD_ID | str | 120 | 100.0% | 120 | 100.0% | root (120) | 1076 (1) | 1104 (1) | 2213 (1) | 1057 (1) | 1100 (1) |
| 3 | FEATURES | list | 120 |  |  |  | root (120) | 7 items (46) | 6 items (35) | 8 items (33) | 9 items (6) |  |
| 4 | FEATURES.RECORD_TYPE | str | 120 | 100.0% | 2 | 1.7% | FEATURES (120) | PERSON (114) | ORGANIZATION (6) |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 107 | 89.2% | 59 | 55.1% | FEATURES (120) | Smith (13) | Kusha (8) | Anderson (7) | Aguilar (4) | Mooney (4) |
| 6 | FEATURES.NAME_FIRST | str | 106 | 88.3% | 79 | 74.5% | FEATURES (120) | Susan (5) | Ellie (4) | Marie (4) | George (3) | Maria Luis (3) |
| 7 | FEATURES.GENDER | str | 20 | 16.7% | 6 | 30.0% | FEATURES (120) | F (5) | M (5) | FEMALE (3) | MALE (3) | UNKNOWN (2) |
| 8 | FEATURES.DATE_OF_BIRTH | str | 114 | 95.0% | 86 | 75.4% | FEATURES (120) | 01/07/80 (4) | 03/12/87 (4) | 04/12/82 (4) | 03/15/92 (2) | 1978-12-11 (2) |
| 9 | FEATURES.ADDR_TYPE | str | 176 | 146.7% | 5 | 2.8% | FEATURES (120) | HOME (159) | MAILING (9) | PRIMARY (5) | BUSINESS (2) | REGISTERED (1) |
| 10 | FEATURES.ADDR_LINE1 | str | 189 | 157.5% | 165 | 87.3% | FEATURES (120) | 3212 W. 32nd St Palm Harbor, FL 60527 (6) | 1304 Poppy Hills Dr (6) | 1824 AspenOak Way (4) | 9304 W. 15th St La Blanca, FL 60527 (3) | 12396 Austin Rd (3) |
| 11 | FEATURES.PHONE_NUMBER | str | 120 | 100.0% | 113 | 94.2% | FEATURES (120) | 7029191300 (2) | 03526553537 (2) | 7022212211 (2) | 8074229031 (2) | +3903526553537 (2) |
| 12 | FEATURES.EMAIL_ADDRESS | str | 120 | 100.0% | 88 | 73.3% | FEATURES (120) | info@ca-state.gov (21) | Kusha123@hmail.com (4) | sthomp45@fmail.com (3) | mouse@mmail.com (2) | pfranks@ishmail.com (2) |
| 13 | FEATURES.ADDR_CITY | str | 158 | 131.7% | 15 | 9.5% | FEATURES (120) | Customer Demo City (120) | Elmwood Park (9) | Sacramento (8) | Blacklick (5) | Chicago (3) |
| 14 | FEATURES.ADDR_STATE | str | 154 | 128.3% | 8 | 5.2% | FEATURES (120) | CA (138) | IL (4) | NV (4) | OH (4) | VA (1) |
| 15 | FEATURES.ADDR_POSTAL_CODE | str | 157 | 130.8% | 133 | 84.7% | FEATURES (120) | 95823 (9) | 95865 (8) | 43004 (5) | 56244 (2) | 89132 (2) |
| 16 | FEATURES.ADDR_COUNTRY | str | 126 | 105.0% | 5 | 4.0% | FEATURES (120) | United States (120) | GERMANY (2) | SINGAPORE (2) | CAMBODIA (1) | CAN (1) |
| 17 | FEATURES.ADDR_FULL | str | 124 | 103.3% | 124 | 100.0% | FEATURES (120) | 6001 Bank Customer Demo Road, Customer Demo City,  (1) | 6002 Bank Customer Demo Road, Customer Demo City,  (1) | 6003 Bank Customer Demo Road, Customer Demo City,  (1) | 6004 Bank Customer Demo Road, Customer Demo City,  (1) | 6005 Bank Customer Demo Road, Customer Demo City,  (1) |
| 18 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 11 | 9.2% | 6 | 54.5% | FEATURES (120) | 823123 (2) | 73423499 (2) | 8923322 (2) | 93939211 (2) | 234234455 (2) |
| 19 | FEATURES.DRIVERS_LICENSE_STATE | str | 11 | 9.2% | 6 | 54.5% | FEATURES (120) | MN (4) | OR (2) | CA (2) | TEXAS (1) | NV (1) |
| 20 | FEATURES.NAME_MIDDLE | str | 2 | 1.7% | 2 | 100.0% | FEATURES (120) | Marie (1) | J (1) |  |  |  |
| 21 | FEATURES.PHONE_TYPE | str | 19 | 15.8% | 2 | 10.5% | FEATURES (120) | HOME (13) | MOBILE (6) |  |  |  |
| 22 | FEATURES.PASSPORT_NUMBER | str | 15 | 12.5% | 7 | 46.7% | FEATURES (120) | 483290175 (3) | 234456456 (2) | 10251111 (2) | 72129291 (2) | 1231345345 (2) |
| 23 | FEATURES.PASSPORT_COUNTRY | str | 15 | 12.5% | 7 | 46.7% | FEATURES (120) | US (6) | USA (3) | CA (2) | DE (1) | GERMANY (1) |
| 24 | FEATURES.NAME_ORG | str | 7 | 5.8% | 7 | 100.0% | FEATURES (120) | Worldwide Exports (1) | Singapore exports (1) | Univrsl Export Inc (1) | India Exports (1) | Hajah Mamunah (Jln Pisang) (1) |
| 25 | FEATURES.SSN_NUMBER | str | 8 | 6.7% | 5 | 62.5% | FEATURES (120) | 521212123 (2) | 3241 (2) | 293909090 (2) | 201777719 (1) | 294669999 (1) |
| 26 | FEATURES.NAME_FULL | str | 6 | 5.0% | 6 | 100.0% | FEATURES (120) | Alexander Pavlovich Vasiliev (1) | 王杰 (1) | 张秀英 (1) | 张伟 (1) | 王伟 (1) |
| 27 | FEATURES.NATIONAL_ID_NUMBER | str | 4 | 3.3% | 2 | 50.0% | FEATURES (120) | 832721 (2) | 7123833 (2) |  |  |  |
| 28 | FEATURES.NAME_TYPE | str | 1 | 0.8% | 1 | 100.0% | FEATURES (120) | AKA (1) |  |  |  |  |
| 29 | FEATURES.NATIONAL_ID_COUNTRY | str | 3 | 2.5% | 2 | 66.7% | FEATURES (120) | China (2) | Hong Kong (1) |  |  |  |
| 30 | AMOUNT | str | 90 | 75.0% | 9 | 10.0% | root (120) | 800 (10) | 900 (10) | 700 (10) | 500 (10) | 400 (10) |
| 31 | DATE | str | 99 | 82.5% | 44 | 44.4% | root (120) | 1/31/18 (4) | 1/14/18 (3) | 1/27/18 (3) | 1/2/18 (3) | 1/20/18 (3) |
| 32 | STATUS | str | 99 | 82.5% | 3 | 3.0% | root (120) | Active (92) | Inactive (5) | Terminated (2) |  |  |
| 33 | CATEGORY | str | 9 | 7.5% | 3 | 33.3% | root (120) | Platinum (4) | Silver (3) | Gold (2) |  |  |
