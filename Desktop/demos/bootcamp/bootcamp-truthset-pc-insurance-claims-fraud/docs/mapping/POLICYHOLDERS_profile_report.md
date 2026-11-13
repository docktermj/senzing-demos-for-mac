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

## Schema: POLICYHOLDERS-improved

**Record Count:** 120
**List Elements (Tables):** 1
**Field Count:** 33

**Data Elements (All Lists):**
- FEATURES: 120 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 120 | 100.0% | 1 | 0.8% | root (120) | CUSTOMERS (120) |  |  |  |  |
| 2 | RECORD_ID | str | 120 | 100.0% | 120 | 100.0% | root (120) | 1001 (1) | 1002 (1) | 1003 (1) | 1004 (1) | 1005 (1) |
| 3 | FEATURES | list | 120 |  |  |  | root (120) | 4 items (46) | 5 items (33) | 3 items (30) | 6 items (10) | 7 items (1) |
| 4 | FEATURES.RECORD_TYPE | str | 120 | 100.0% | 2 | 1.7% | FEATURES (120) | PERSON (114) | ORGANIZATION (6) |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 107 | 89.2% | 59 | 55.1% | FEATURES (120) | Smith (13) | Kusha (8) | Anderson (7) | Mooney (4) | Aguilar (4) |
| 6 | FEATURES.NAME_FIRST | str | 106 | 88.3% | 79 | 74.5% | FEATURES (120) | Susan (5) | Marie (4) | Ellie (4) | Mark (3) | Maria Luis (3) |
| 7 | FEATURES.DATE_OF_BIRTH | str | 64 | 53.3% | 37 | 57.8% | FEATURES (120) | 1/7/80 (4) | 3/12/87 (4) | 4/12/82 (4) | 12/11/1978 (2) | 10/27/76 (2) |
| 8 | FEATURES.ADDR_TYPE | str | 56 | 46.7% | 5 | 8.9% | FEATURES (120) | HOME (39) | MAILING (9) | PRIMARY (5) | BUSINESS (2) | REGISTERED (1) |
| 9 | FEATURES.ADDR_LINE1 | str | 69 | 57.5% | 45 | 65.2% | FEATURES (120) | 1304 Poppy Hills Dr (6) | 3212 W. 32nd St Palm Harbor, FL 60527 (6) | 1824 AspenOak Way (4) | 9304 W. 15th St La Blanca, FL 60527 (3) | 12396 Austin Rd (3) |
| 10 | FEATURES.PHONE_TYPE | str | 19 | 15.8% | 2 | 10.5% | FEATURES (120) | HOME (13) | MOBILE (6) |  |  |  |
| 11 | FEATURES.PHONE_NUMBER | str | 21 | 17.5% | 14 | 66.7% | FEATURES (120) | 7029191300 (2) | 5123538633 (2) | 9714218250 (2) | 8074229031 (2) | 03526553537 (2) |
| 12 | FEATURES.ADDR_CITY | str | 38 | 31.7% | 14 | 36.8% | FEATURES (120) | Elmwood Park (9) | Sacramento (8) | Blacklick (5) | Las Vegas (3) | Chicago (3) |
| 13 | FEATURES.ADDR_STATE | str | 34 | 28.3% | 7 | 20.6% | FEATURES (120) | CA (19) | NV (4) | OH (4) | IL (4) | OHIO (1) |
| 14 | FEATURES.ADDR_POSTAL_CODE | str | 37 | 30.8% | 13 | 35.1% | FEATURES (120) | 95823 (9) | 95865 (8) | 43004 (5) | 89111 (2) | 89132 (2) |
| 15 | FEATURES.NAME_MIDDLE | str | 2 | 1.7% | 2 | 100.0% | FEATURES (120) | J (1) | Marie (1) |  |  |  |
| 16 | FEATURES.EMAIL_ADDRESS | str | 45 | 37.5% | 13 | 28.9% | FEATURES (120) | info@ca-state.gov (21) | Kusha123@hmail.com (4) | sthomp45@fmail.com (3) | bsmith@work.com (2) | dellie@fmail.com (2) |
| 17 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 11 | 9.2% | 6 | 54.5% | FEATURES (120) | 93939211 (2) | 8923322 (2) | 73423499 (2) | 234234455 (2) | 823123 (2) |
| 18 | FEATURES.DRIVERS_LICENSE_STATE | str | 11 | 9.2% | 6 | 54.5% | FEATURES (120) | MN (4) | CA (2) | OR (2) | NV (1) | TX (1) |
| 19 | FEATURES.SSN_NUMBER | str | 8 | 6.7% | 5 | 62.5% | FEATURES (120) | 293-90-9090 (2) | 521-21-2123 (2) | 3241 (2) | 294-66-9999 (1) | 201-77-7719 (1) |
| 20 | FEATURES.GENDER | str | 20 | 16.7% | 6 | 30.0% | FEATURES (120) | M (5) | F (5) | MALE (3) | FEMALE (3) | UNKNOWN (2) |
| 21 | FEATURES.PASSPORT_NUMBER | str | 15 | 12.5% | 7 | 46.7% | FEATURES (120) | 483290175 (3) | 10251111 (2) | 10252222 (2) | 72129291 (2) | 1231345345 (2) |
| 22 | FEATURES.PASSPORT_COUNTRY | str | 15 | 12.5% | 7 | 46.7% | FEATURES (120) | US (6) | USA (3) | CA (2) | CAN (1) | DE (1) |
| 23 | FEATURES.NAME_FULL | str | 6 | 5.0% | 6 | 100.0% | FEATURES (120) | 王杰 (1) | 王伟 (1) | 张伟 (1) | 张秀英 (1) | 刘杰 (1) |
| 24 | FEATURES.NATIONAL_ID_NUMBER | str | 4 | 3.3% | 2 | 50.0% | FEATURES (120) | 832721 (2) | 7123833 (2) |  |  |  |
| 25 | FEATURES.NATIONAL_ID_COUNTRY | str | 3 | 2.5% | 2 | 66.7% | FEATURES (120) | China (2) | Hong Kong (1) |  |  |  |
| 26 | FEATURES.ADDR_COUNTRY | str | 6 | 5.0% | 4 | 66.7% | FEATURES (120) | GERMANY (2) | SINGAPORE (2) | CAN (1) | CAMBODIA (1) |  |
| 27 | FEATURES.NAME_ORG | str | 7 | 5.8% | 7 | 100.0% | FEATURES (120) | Hajah Mamunah (Jln Pisang) (1) | Mullenkrants (1) | Autoworks (1) | Univrsl Export Inc (1) | Worldwide Exports (1) |
| 28 | FEATURES.ADDR_FULL | str | 4 | 3.3% | 4 | 100.0% | FEATURES (120) | #01-11, HillV2, 4 Hillview Rise, 667979 (1) | No 321, Street 128 Phnom Penh Cambodia (1) | 133 New Bridge Road, Chinatown Point, Singapore 05 (1) | Mullanpara Road, Old Vythiri, Vythiri, Wayanad, 67 (1) |  |
| 29 | FEATURES.NAME_TYPE | str | 1 | 0.8% | 1 | 100.0% | FEATURES (120) | AKA (1) |  |  |  |  |
| 30 | AMOUNT | str | 90 | 75.0% | 9 | 10.0% | root (120) | 100 (10) | 200 (10) | 300 (10) | 400 (10) | 500 (10) |
| 31 | DATE | str | 99 | 82.5% | 44 | 44.4% | root (120) | 01/31/18 (4) | 1/2/18 (3) | 1/7/18 (3) | 1/10/18 (3) | 1/11/18 (3) |
| 32 | STATUS | str | 99 | 82.5% | 3 | 3.0% | root (120) | Active (92) | Inactive (5) | Terminated (2) |  |  |
| 33 | CATEGORY | str | 9 | 7.5% | 3 | 33.3% | root (120) | Platinum (4) | Silver (3) | Gold (2) |  |  |
