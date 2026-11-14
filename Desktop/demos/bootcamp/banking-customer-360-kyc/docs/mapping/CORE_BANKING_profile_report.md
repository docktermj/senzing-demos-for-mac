**File Type:** jsonl
**Root Records:** 97
**Total Fields:** 45

## Structure Variations

**Distinct field patterns:** 2 (across 97 records)
**Common fields:** ACCOUNT_NUMBER, ACCOUNT_STATUS, BRANCH, CURRENT_BALANCE, DATA_SOURCE, FEATURES, OPEN_DATE, OWNERSHIP_TYPE, PRODUCT_TYPE, RECORD_ID
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 92 (95%) | — (only common fields) |
| 2 | 5 (5%) | RELATIONSHIP_TIER |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: core_cif_extract

**Record Count:** 97
**List Elements (Tables):** 1
**Field Count:** 45

**Data Elements (All Lists):**
- FEATURES: 94 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 97 | 100.0% | 1 | 1.0% | root (97) | CORE_BANKING (97) |  |  |  |  |
| 2 | RECORD_ID | str | 97 | 100.0% | 97 | 100.0% | root (97) | 1001 (1) | 1003 (1) | 1005 (1) | 1009 (1) | 1011 (1) |
| 3 | FEATURES | list | 97 |  |  |  | root (97) | 4 items (26) | 6 items (22) | 5 items (20) | 3 items (18) | 7 items (11) |
| 4 | FEATURES.RECORD_TYPE | str | 94 | 100.0% | 2 | 2.1% | FEATURES (94) | PERSON (86) | ORGANIZATION (8) |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 80 | 85.1% | 56 | 70.0% | FEATURES (94) | Smith (9) | Kusha (5) | Anderson (5) | Thompson (2) | Toulouse (2) |
| 6 | FEATURES.NAME_FIRST | str | 79 | 84.0% | 71 | 89.9% | FEATURES (94) | Susan (3) | Mary  (2) | Mark (2) | Maria Luis (2) | John (2) |
| 7 | FEATURES.DATE_OF_BIRTH | str | 61 | 64.9% | 52 | 85.2% | FEATURES (94) | 1/7/80 (3) | 12/11/1978 (2) | 9/28/97 (2) | 11/21/73 (2) | 10/10/70 (2) |
| 8 | FEATURES.ADDR_TYPE | str | 56 | 59.6% | 4 | 7.1% | FEATURES (94) | HOME (42) | BUSINESS (7) | MAILING (5) | PRIMARY (2) |  |
| 9 | FEATURES.ADDR_LINE1 | str | 60 | 63.8% | 51 | 85.0% | FEATURES (94) | 1304 Poppy Hills Dr (4) | 3212 W. 32nd St Palm Harbor, FL 60527 (4) | 9304 W. 15th St La Blanca, FL 60527 (3) | 1824 AspenOak Way (2) | 123 Main Street, Las Vegas NV 89132 (1) |
| 10 | FEATURES.PHONE_TYPE | str | 30 | 31.9% | 2 | 6.7% | FEATURES (94) | MOBILE (22) | HOME (8) |  |  |  |
| 11 | FEATURES.PHONE_NUMBER | str | 31 | 33.0% | 28 | 90.3% | FEATURES (94) | 0352 6553537 (2) | 206-555-0106 (2) | 312-555-0108 (2) | 702-919-1300 (1) | 512-353-8633 (1) |
| 12 | FEATURES.NAME_MIDDLE | str | 2 | 2.1% | 2 | 100.0% | FEATURES (94) | J (1) | Ann (1) |  |  |  |
| 13 | FEATURES.EMAIL_ADDRESS | str | 29 | 30.9% | 17 | 58.6% | FEATURES (94) | info@ca-state.gov (9) | Kusha123@hmail.com (4) | sthomp45@fmail.com (2) | bsmith@work.com (1) | Maria Sentosa<msentosa@fmail.com> (1) |
| 14 | FEATURES.ADDR_CITY | str | 43 | 45.7% | 31 | 72.1% | FEATURES (94) | Blacklick (4) | Elmwood Park (4) | Sacramento (3) | Tucson (3) | Chicago (2) |
| 15 | FEATURES.ADDR_STATE | str | 41 | 43.6% | 19 | 46.3% | FEATURES (94) | CA (9) | OH (6) | AZ (4) | IL (3) | NV (2) |
| 16 | FEATURES.ADDR_POSTAL_CODE | str | 42 | 44.7% | 33 | 78.6% | FEATURES (94) | 43004 (4) | 95865 (4) | 95823 (3) | 83702 (2) | 89132 (1) |
| 17 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 6 | 6.4% | 6 | 100.0% | FEATURES (94) | 112233 (1) | 93939211 (1) | 8923322 (1) | 73423499 (1) | 234234455 (1) |
| 18 | FEATURES.DRIVERS_LICENSE_STATE | str | 6 | 6.4% | 5 | 83.3% | FEATURES (94) | MN (2) | NV (1) | CA (1) | OR (1) | TX (1) |
| 19 | FEATURES.SSN_NUMBER | str | 28 | 29.8% | 26 | 92.9% | FEATURES (94) | 293-90-9090 (2) | 633-12-4455 (2) | 294-66-9999 (1) | 201-77-7719 (1) | 521212123 (1) |
| 20 | FEATURES.GENDER | str | 25 | 26.6% | 5 | 20.0% | FEATURES (94) | F (13) | M (8) | U (2) | Male (1) | Female (1) |
| 21 | FEATURES.PASSPORT_NUMBER | str | 11 | 11.7% | 10 | 90.9% | FEATURES (94) | 483290175 (2) | 10251111 (1) | 10252222 (1) | 72129291 (1) | 1231345345 (1) |
| 22 | FEATURES.PASSPORT_COUNTRY | str | 9 | 9.6% | 4 | 44.4% | FEATURES (94) | US (5) | CA (2) | USA (1) | DE (1) |  |
| 23 | FEATURES.NAME_FULL | str | 11 | 11.7% | 11 | 100.0% | FEATURES (94) | 王杰 (1) | 王伟 (1) | 张伟 (1) | 张秀英 (1) | 刘杰 (1) |
| 24 | FEATURES.NATIONAL_ID_NUMBER | str | 2 | 2.1% | 2 | 100.0% | FEATURES (94) | 832721 (1) | 7123833 (1) |  |  |  |
| 25 | FEATURES.NATIONAL_ID_COUNTRY | str | 1 | 1.1% | 1 | 100.0% | FEATURES (94) | China (1) |  |  |  |  |
| 26 | FEATURES.NAME_ORG | str | 9 | 9.6% | 9 | 100.0% | FEATURES (94) | Hajah Mamunah (Jln Pisang) (1) | India Exports (1) | Cascadia Freight Partners (1) | Universal Exports Nordics (1) | Cobalt Ridge Logistics LLC (1) |
| 27 | FEATURES.ADDR_FULL | str | 4 | 4.3% | 4 | 100.0% | FEATURES (94) | #01-11, HillV2, 4 Hillview Rise, 667979 (1) | No 321, Street 128 Phnom Penh Cambodia (1) | Mullanpara Road, Old Vythiri, Vythiri, Wayanad, 67 (1) | 78, DELHI FUND BLDG, DELHI 110021 (1) |  |
| 28 | FEATURES.ADDR_COUNTRY | str | 2 | 2.1% | 2 | 100.0% | FEATURES (94) | Singapore (1) | Sweden (1) |  |  |  |
| 29 | FEATURES.TAX_ID_NUMBER | str | 8 | 8.5% | 7 | 87.5% | FEATURES (94) | 77-7000001 (2) | DE123456789 (1) | 91-5550001 (1) | 77-7000002 (1) | 77-7000003 (1) |
| 30 | FEATURES.TAX_ID_COUNTRY | str | 6 | 6.4% | 2 | 33.3% | FEATURES (94) | US (5) | DE (1) |  |  |  |
| 31 | FEATURES.TAX_ID_TYPE | str | 5 | 5.3% | 1 | 20.0% | FEATURES (94) | EIN (5) |  |  |  |  |
| 32 | FEATURES.REL_ANCHOR_DOMAIN | str | 3 | 3.2% | 1 | 33.3% | FEATURES (94) | CUSTOMERS (3) |  |  |  |  |
| 33 | FEATURES.REL_ANCHOR_KEY | str | 3 | 3.2% | 3 | 100.0% | FEATURES (94) | 1310 (1) | 1311 (1) | 1313 (1) |  |  |
| 34 | FEATURES.REL_POINTER_DOMAIN | str | 3 | 3.2% | 2 | 66.7% | FEATURES (94) | CUSTOMERS (2) | REFERENCE (1) |  |  |  |
| 35 | FEATURES.REL_POINTER_KEY | str | 3 | 3.2% | 3 | 100.0% | FEATURES (94) | 1310 (1) | 1312 (1) | 3048 (1) |  |  |
| 36 | FEATURES.REL_POINTER_ROLE | str | 3 | 3.2% | 3 | 100.0% | FEATURES (94) | PARENT_OF (1) | SUBSIDIARY_OF (1) | Carer (1) |  |  |
| 37 | FEATURES.NAME_TYPE | str | 1 | 1.1% | 1 | 100.0% | FEATURES (94) | PRIMARY (1) |  |  |  |  |
| 38 | ACCOUNT_NUMBER | str | 97 | 100.0% | 97 | 100.0% | root (97) | 7263239476 (1) | 7925883800 (1) | 6314849379 (1) | 4012918620 (1) | 1149169065 (1) |
| 39 | PRODUCT_TYPE | str | 97 | 100.0% | 4 | 4.1% | root (97) | DDA (53) | SAV (28) | MMDA (9) | CD (7) |  |
| 40 | OWNERSHIP_TYPE | str | 97 | 100.0% | 3 | 3.1% | root (97) | Individual (67) | Joint (21) | Business (9) |  |  |
| 41 | ACCOUNT_STATUS | str | 97 | 100.0% | 3 | 3.1% | root (97) | Open (94) | Dormant (2) | Closed (1) |  |  |
| 42 | OPEN_DATE | str | 97 | 100.0% | 74 | 76.3% | root (97) | 2018-01-10 (3) | 2018-01-16 (3) | 2018-01-18 (3) | 2018-01-20 (3) | 2018-01-28 (3) |
| 43 | CURRENT_BALANCE | float | 96 | 99.0% | 96 | 100.0% | root (97) | 42226.99 (1) | 4232.0 (1) | 3447.49 (1) | 111016.12 (1) | 34476.13 (1) |
| 44 | BRANCH | str | 97 | 100.0% | 6 | 6.2% | root (97) | Westgate (22) | Riverside (18) | North Park (16) | Lakeview (15) | Main Street (15) |
| 45 | RELATIONSHIP_TIER | str | 5 | 5.2% | 3 | 60.0% | root (97) | Platinum (2) | Gold (2) | Bronze (1) |  |  |
