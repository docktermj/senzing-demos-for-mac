**File Type:** jsonl
**Root Records:** 75
**Total Fields:** 47

## Structure Variations

**Distinct field patterns:** 3 (across 75 records)
**Common fields:** CONTRACT_NUMBER, CONTRACT_STATUS, DATA_SOURCE, FEATURES, ISSUE_DATE, PARTY_ROLE, PRODUCT_TYPE, RECORD_ID
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 56 (75%) | FACE_AMOUNT, MODAL_PREMIUM, PREMIUM_MODE |
| 2 | 16 (21%) | ACCOUNT_VALUE |
| 3 | 3 (4%) | CUSTOMER_TIER, FACE_AMOUNT, MODAL_PREMIUM, PREMIUM_MODE |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: life_annuity_admin_extract

**Record Count:** 75
**List Elements (Tables):** 1
**Field Count:** 47

**Data Elements (All Lists):**
- FEATURES: 72 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 75 | 100.0% | 1 | 1.3% | root (75) | LIFE_ANNUITY_ADMIN (75) |  |  |  |  |
| 2 | RECORD_ID | str | 75 | 100.0% | 75 | 100.0% | root (75) | 1002 (1) | 1004 (1) | 1010 (1) | 1016 (1) | 1018 (1) |
| 3 | FEATURES | list | 75 |  |  |  | root (75) | 4 items (26) | 5 items (18) | 3 items (12) | 6 items (9) | 7 items (7) |
| 4 | FEATURES.RECORD_TYPE | str | 72 | 100.0% | 2 | 2.8% | FEATURES (72) | PERSON (67) | ORGANIZATION (5) |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 66 | 91.7% | 51 | 77.3% | FEATURES (72) | Smith (5) | Kusha (3) | Jones (3) | Mooney (2) | Wang (2) |
| 6 | FEATURES.NAME_FIRST | str | 66 | 91.7% | 57 | 86.4% | FEATURES (72) | Marie (3) | Maria Luis (2) | Pat (2) | Susan (2) | Jie (2) |
| 7 | FEATURES.DATE_OF_BIRTH | str | 43 | 59.7% | 40 | 93.0% | FEATURES (72) | 4/12/82 (3) | 3/12/87 (2) | 11/12/1978 (1) | 11/12/1979 (1) | Mar 1 1970 (1) |
| 8 | FEATURES.ADDR_TYPE | str | 39 | 54.2% | 4 | 10.3% | FEATURES (72) | HOME (29) | MAILING (4) | BUSINESS (4) | PRIMARY (2) |  |
| 9 | FEATURES.ADDR_LINE1 | str | 47 | 65.3% | 38 | 80.9% | FEATURES (72) | 1304 Poppy Hills Dr (3) | 9304 W. 15th St La Blanca, FL 60527 (2) | 3212 W. 32nd St Palm Harbor, FL 60527 (2) | 12396 Austin Rd (2) | 1824 AspenOak Way (2) |
| 10 | FEATURES.ADDR_CITY | str | 32 | 44.4% | 17 | 53.1% | FEATURES (72) | Sacramento (5) | Elmwood Park (5) | Tucson (3) | Las Vegas (2) | Blacklick (2) |
| 11 | FEATURES.ADDR_STATE | str | 28 | 38.9% | 11 | 39.3% | FEATURES (72) | CA (12) | AZ (3) | NV (2) | IL (2) | ID (2) |
| 12 | FEATURES.ADDR_POSTAL_CODE | str | 29 | 40.3% | 17 | 58.6% | FEATURES (72) | 95823 (6) | 95865 (4) | 43004 (2) | 85719 (2) | 83702 (2) |
| 13 | FEATURES.PHONE_TYPE | str | 16 | 22.2% | 2 | 12.5% | FEATURES (72) | MOBILE (10) | HOME (6) |  |  |  |
| 14 | FEATURES.PHONE_NUMBER | str | 17 | 23.6% | 15 | 88.2% | FEATURES (72) | +39 0352 6553537 (2) | 503-555-0155 (2) | 702-919-1300 (1) | 512-353-8633 (1) | 321-3212 (1) |
| 15 | FEATURES.EMAIL_ADDRESS | str | 35 | 48.6% | 18 | 51.4% | FEATURES (72) | info@ca-state.gov (13) | guptapra55@Yahoo.com (2) | kob.pdx@fmail.com (2) | castillo.home@fmail.com (2) | whitcombe.family@fmail.com (2) |
| 16 | FEATURES.PASSPORT_NUMBER | str | 9 | 12.5% | 9 | 100.0% | FEATURES (72) | 10251111 (1) | 10252222 (1) | 483290175 (1) | 72129291 (1) | 1231345345 (1) |
| 17 | FEATURES.PASSPORT_COUNTRY | str | 8 | 11.1% | 5 | 62.5% | FEATURES (72) | US (3) | USA (2) | CAN (1) | Germany (1) | Canada (1) |
| 18 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 5 | 6.9% | 5 | 100.0% | FEATURES (72) | 93939211 (1) | 8923322 (1) | 73423499 (1) | 234234455 (1) | 823123 (1) |
| 19 | FEATURES.DRIVERS_LICENSE_STATE | str | 5 | 6.9% | 4 | 80.0% | FEATURES (72) | MN (2) | CA (1) | OR (1) | Texas (1) |  |
| 20 | FEATURES.SSN_NUMBER | str | 10 | 13.9% | 10 | 100.0% | FEATURES (72) | 521-21-2123 (1) | 3241 (1) | 655-31-2020 (1) | 179-44-2938 (1) | 512-38-1170 (1) |
| 21 | FEATURES.GENDER | str | 19 | 26.4% | 5 | 26.3% | FEATURES (72) | M (7) | F (6) | Male (2) | Female (2) | Unknown (2) |
| 22 | FEATURES.NATIONAL_ID_NUMBER | str | 2 | 2.8% | 2 | 100.0% | FEATURES (72) | 832721 (1) | 7123833 (1) |  |  |  |
| 23 | FEATURES.NATIONAL_ID_COUNTRY | str | 2 | 2.8% | 2 | 100.0% | FEATURES (72) | Hong Kong (1) | China (1) |  |  |  |
| 24 | FEATURES.ADDR_COUNTRY | str | 5 | 6.9% | 5 | 100.0% | FEATURES (72) | Germany (1) | CAN (1) | Singapore (1) | Cambodia (1) | Sweden (1) |
| 25 | FEATURES.NAME_MIDDLE | str | 1 | 1.4% | 1 | 100.0% | FEATURES (72) | Marie (1) |  |  |  |  |
| 26 | FEATURES.NAME_ORG | str | 6 | 8.3% | 6 | 100.0% | FEATURES (72) | Hajah Mamunah (Jln Pisang) (1) | India Exports (1) | Universal Exports Nordics (1) | Kestrel Marine Supply (1) | Redstone Auto Parts (1) |
| 27 | FEATURES.ADDR_FULL | str | 4 | 5.6% | 3 | 75.0% | FEATURES (72) | 78, DELHI FUND BLDG, DELHI 110021 (2) | #01-11, HillV2, 4 Hillview Rise, 667979 (1) | Mullanpara Road, Old Vythiri, Vythiri, Wayanad, 67 (1) |  |  |
| 28 | FEATURES.TAX_ID_NUMBER | str | 5 | 6.9% | 5 | 100.0% | FEATURES (72) | 91-5550001 (1) | 77-7000002 (1) | 77-7000003 (1) | AAKY6799J (1) | AMELV9997R (1) |
| 29 | FEATURES.TAX_ID_TYPE | str | 3 | 4.2% | 1 | 33.3% | FEATURES (72) | EIN (3) |  |  |  |  |
| 30 | FEATURES.TAX_ID_COUNTRY | str | 3 | 4.2% | 1 | 33.3% | FEATURES (72) | US (3) |  |  |  |  |
| 31 | FEATURES.NPI_NUMBER | str | 1 | 1.4% | 1 | 100.0% | FEATURES (72) | 1987654320 (1) |  |  |  |  |
| 32 | FEATURES.REL_ANCHOR_DOMAIN | str | 2 | 2.8% | 1 | 50.0% | FEATURES (72) | CUSTOMERS (2) |  |  |  |  |
| 33 | FEATURES.REL_ANCHOR_KEY | str | 2 | 2.8% | 2 | 100.0% | FEATURES (72) | 1313 (1) | 3063 (1) |  |  |  |
| 34 | FEATURES.REL_POINTER_DOMAIN | str | 2 | 2.8% | 1 | 50.0% | FEATURES (72) | CUSTOMERS (2) |  |  |  |  |
| 35 | FEATURES.REL_POINTER_KEY | str | 2 | 2.8% | 2 | 100.0% | FEATURES (72) | 1312 (1) | 3063 (1) |  |  |  |
| 36 | FEATURES.REL_POINTER_ROLE | str | 2 | 2.8% | 2 | 100.0% | FEATURES (72) | SUBSIDIARY_OF (1) | Spouse (1) |  |  |  |
| 37 | FEATURES.NAME_FULL | str | 2 | 2.8% | 2 | 100.0% | FEATURES (72) | MEENU J GUPTA (1) | GUPTA MAMTA J (1) |  |  |  |
| 38 | CONTRACT_NUMBER | str | 75 | 100.0% | 75 | 100.0% | root (75) | LF19909052 (1) | LF20861093 (1) | LF57606183 (1) | AN83005851 (1) | LF74221713 (1) |
| 39 | PRODUCT_TYPE | str | 75 | 100.0% | 6 | 8.0% | root (75) | Term Life (29) | Universal Life (16) | Fixed Annuity (15) | Whole Life (8) | Key Person Term Life (6) |
| 40 | PARTY_ROLE | str | 75 | 100.0% | 2 | 2.7% | root (75) | Owner (74) | Beneficiary (1) |  |  |  |
| 41 | ISSUE_DATE | str | 75 | 100.0% | 59 | 78.7% | root (75) | 2018-01-13 (3) | 2018-01-17 (3) | 2018-01-31 (3) | 2018-01-11 (2) | 2018-01-03 (2) |
| 42 | CONTRACT_STATUS | str | 75 | 100.0% | 2 | 2.7% | root (75) | In Force (71) | Lapsed (4) |  |  |  |
| 43 | FACE_AMOUNT | int | 59 | 78.7% | 7 | 11.9% | root (75) | 100000 (13) | 500000 (13) | 250000 (12) | 750000 (9) | 1000000 (7) |
| 44 | MODAL_PREMIUM | float | 59 | 78.7% | 59 | 100.0% | root (75) | 12.51 (1) | 12.45 (1) | 31.19 (1) | 25.57 (1) | 38.03 (1) |
| 45 | PREMIUM_MODE | str | 59 | 78.7% | 2 | 3.4% | root (75) | Monthly (38) | Annual (21) |  |  |  |
| 46 | ACCOUNT_VALUE | float | 16 | 21.3% | 16 | 100.0% | root (75) | 133785.88 (1) | 56013.38 (1) | 50903.26 (1) | 277840.48 (1) | 71660.96 (1) |
| 47 | CUSTOMER_TIER | str | 3 | 4.0% | 3 | 100.0% | root (75) | Platinum (1) | Silver (1) | Gold (1) |  |  |
