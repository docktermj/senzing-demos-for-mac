**File Type:** jsonl
**Root Records:** 104
**Total Fields:** 48

## Structure Variations

**Distinct field patterns:** 4 (across 104 records)
**Common fields:** DATA_SOURCE, EFFECTIVE_DATE, EXPIRATION_DATE, FEATURES, INSURED_ROLE, LINE_OF_BUSINESS, POLICY_NUMBER, POLICY_STATUS, PRODUCER_CODE, RECORD_ID, WRITTEN_PREMIUM
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 95 (91%) | — (only common fields) |
| 2 | 6 (6%) | CUSTOMER_TIER |
| 3 | 2 (2%) | CANCELLATION_REASON |
| 4 | 1 (1%) | CANCELLATION_REASON, CUSTOMER_TIER |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: pc_policy_admin_extract

**Record Count:** 104
**List Elements (Tables):** 1
**Field Count:** 48

**Data Elements (All Lists):**
- FEATURES: 101 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 104 | 100.0% | 1 | 1.0% | root (104) | PC_POLICY_ADMIN (104) |  |  |  |  |
| 2 | RECORD_ID | str | 104 | 100.0% | 104 | 100.0% | root (104) | 1001 (1) | 1003 (1) | 1005 (1) | 1009 (1) | 1011 (1) |
| 3 | FEATURES | list | 104 |  |  |  | root (104) | 4 items (32) | 5 items (21) | 6 items (21) | 3 items (19) | 7 items (11) |
| 4 | FEATURES.RECORD_TYPE | str | 101 | 100.0% | 2 | 2.0% | FEATURES (101) | PERSON (86) | ORGANIZATION (15) |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 80 | 79.2% | 56 | 70.0% | FEATURES (101) | Smith (9) | Kusha (5) | Anderson (5) | Thompson (2) | Toulouse (2) |
| 6 | FEATURES.NAME_FIRST | str | 79 | 78.2% | 71 | 89.9% | FEATURES (101) | Susan (3) | Mary  (2) | Mark (2) | Maria Luis (2) | John (2) |
| 7 | FEATURES.DATE_OF_BIRTH | str | 61 | 60.4% | 52 | 85.2% | FEATURES (101) | 1/7/80 (3) | 12/11/1978 (2) | 9/28/97 (2) | 11/21/73 (2) | 10/10/70 (2) |
| 8 | FEATURES.ADDR_TYPE | str | 63 | 62.4% | 5 | 7.9% | FEATURES (101) | HOME (42) | BUSINESS (12) | MAILING (5) | PRIMARY (3) | REGISTERED (1) |
| 9 | FEATURES.ADDR_LINE1 | str | 68 | 67.3% | 59 | 86.8% | FEATURES (101) | 1304 Poppy Hills Dr (4) | 3212 W. 32nd St Palm Harbor, FL 60527 (4) | 9304 W. 15th St La Blanca, FL 60527 (3) | 1824 AspenOak Way (2) | 123 Main Street, Las Vegas NV 89132 (1) |
| 10 | FEATURES.PHONE_TYPE | str | 30 | 29.7% | 2 | 6.7% | FEATURES (101) | MOBILE (22) | HOME (8) |  |  |  |
| 11 | FEATURES.PHONE_NUMBER | str | 31 | 30.7% | 28 | 90.3% | FEATURES (101) | 0352 6553537 (2) | 206-555-0106 (2) | 312-555-0108 (2) | 702-919-1300 (1) | 512-353-8633 (1) |
| 12 | FEATURES.NAME_MIDDLE | str | 2 | 2.0% | 2 | 100.0% | FEATURES (101) | J (1) | Ann (1) |  |  |  |
| 13 | FEATURES.EMAIL_ADDRESS | str | 29 | 28.7% | 17 | 58.6% | FEATURES (101) | info@ca-state.gov (9) | Kusha123@hmail.com (4) | sthomp45@fmail.com (2) | bsmith@work.com (1) | Maria Sentosa<msentosa@fmail.com> (1) |
| 14 | FEATURES.ADDR_CITY | str | 50 | 49.5% | 35 | 70.0% | FEATURES (101) | Blacklick (4) | Elmwood Park (4) | Seattle (4) | Sacramento (3) | Tucson (3) |
| 15 | FEATURES.ADDR_STATE | str | 49 | 48.5% | 19 | 38.8% | FEATURES (101) | CA (9) | OH (7) | WA (7) | AZ (4) | NV (3) |
| 16 | FEATURES.ADDR_POSTAL_CODE | str | 51 | 50.5% | 41 | 80.4% | FEATURES (101) | 43004 (4) | 95865 (4) | 95823 (3) | 98101 (2) | 83702 (2) |
| 17 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 6 | 5.9% | 6 | 100.0% | FEATURES (101) | 112233 (1) | 93939211 (1) | 8923322 (1) | 73423499 (1) | 234234455 (1) |
| 18 | FEATURES.DRIVERS_LICENSE_STATE | str | 6 | 5.9% | 5 | 83.3% | FEATURES (101) | MN (2) | NV (1) | CA (1) | OR (1) | TX (1) |
| 19 | FEATURES.SSN_NUMBER | str | 28 | 27.7% | 26 | 92.9% | FEATURES (101) | 293-90-9090 (2) | 633-12-4455 (2) | 294-66-9999 (1) | 201-77-7719 (1) | 521212123 (1) |
| 20 | FEATURES.GENDER | str | 25 | 24.8% | 5 | 20.0% | FEATURES (101) | F (13) | M (8) | U (2) | Male (1) | Female (1) |
| 21 | FEATURES.PASSPORT_NUMBER | str | 11 | 10.9% | 10 | 90.9% | FEATURES (101) | 483290175 (2) | 10251111 (1) | 10252222 (1) | 72129291 (1) | 1231345345 (1) |
| 22 | FEATURES.PASSPORT_COUNTRY | str | 9 | 8.9% | 4 | 44.4% | FEATURES (101) | US (5) | CA (2) | USA (1) | DE (1) |  |
| 23 | FEATURES.NAME_FULL | str | 11 | 10.9% | 11 | 100.0% | FEATURES (101) | 王杰 (1) | 王伟 (1) | 张伟 (1) | 张秀英 (1) | 刘杰 (1) |
| 24 | FEATURES.NATIONAL_ID_NUMBER | str | 2 | 2.0% | 2 | 100.0% | FEATURES (101) | 832721 (1) | 7123833 (1) |  |  |  |
| 25 | FEATURES.NATIONAL_ID_COUNTRY | str | 1 | 1.0% | 1 | 100.0% | FEATURES (101) | China (1) |  |  |  |  |
| 26 | FEATURES.ADDR_FULL | str | 3 | 3.0% | 3 | 100.0% | FEATURES (101) | No 321, Street 128 Phnom Penh Cambodia (1) | 133 New Bridge Road, Chinatown Point, Singapore 05 (1) | 78, DELHI FUND BLDG, DELHI 110021 (1) |  |  |
| 27 | FEATURES.NAME_ORG | str | 17 | 16.8% | 17 | 100.0% | FEATURES (101) | Mullenkrants  (1) | Autoworks (1) | Univrsl Export Inc (1) | Worldwide Exports  (1) | Singapore exports (1) |
| 28 | FEATURES.NAME_TYPE | str | 2 | 2.0% | 2 | 100.0% | FEATURES (101) | AKA (1) | PRIMARY (1) |  |  |  |
| 29 | FEATURES.ADDR_COUNTRY | str | 2 | 2.0% | 2 | 100.0% | FEATURES (101) | Germany (1) | Singapore (1) |  |  |  |
| 30 | FEATURES.TAX_ID_NUMBER | str | 10 | 9.9% | 7 | 70.0% | FEATURES (101) | 77-7000001 (2) | 77-7000002 (2) | 77-7000003 (2) | DE123456789 (1) | 91-5550001 (1) |
| 31 | FEATURES.TAX_ID_COUNTRY | str | 8 | 7.9% | 2 | 25.0% | FEATURES (101) | US (7) | DE (1) |  |  |  |
| 32 | FEATURES.TAX_ID_TYPE | str | 7 | 6.9% | 1 | 14.3% | FEATURES (101) | EIN (7) |  |  |  |  |
| 33 | FEATURES.NPI_NUMBER | str | 3 | 3.0% | 2 | 66.7% | FEATURES (101) | 1234567893 (2) | 1987654320 (1) |  |  |  |
| 34 | FEATURES.REL_ANCHOR_DOMAIN | str | 3 | 3.0% | 1 | 33.3% | FEATURES (101) | CUSTOMERS (3) |  |  |  |  |
| 35 | FEATURES.REL_ANCHOR_KEY | str | 3 | 3.0% | 3 | 100.0% | FEATURES (101) | 1310 (1) | 1311 (1) | 1312 (1) |  |  |
| 36 | FEATURES.REL_POINTER_DOMAIN | str | 2 | 2.0% | 2 | 100.0% | FEATURES (101) | CUSTOMERS (1) | REFERENCE (1) |  |  |  |
| 37 | FEATURES.REL_POINTER_KEY | str | 2 | 2.0% | 2 | 100.0% | FEATURES (101) | 1310 (1) | 3048 (1) |  |  |  |
| 38 | FEATURES.REL_POINTER_ROLE | str | 2 | 2.0% | 2 | 100.0% | FEATURES (101) | PARENT_OF (1) | Carer (1) |  |  |  |
| 39 | POLICY_NUMBER | str | 104 | 100.0% | 104 | 100.0% | root (104) | PP88214918 (1) | PP20148160 (1) | PP41871315 (1) | PP37185615 (1) | PP64040875 (1) |
| 40 | LINE_OF_BUSINESS | str | 104 | 100.0% | 8 | 7.7% | root (104) | Personal Auto (38) | Homeowners (33) | Personal Umbrella (10) | Businessowners (8) | Renters (7) |
| 41 | INSURED_ROLE | str | 104 | 100.0% | 2 | 1.9% | root (104) | Named Insured (103) | Additional Named Insured (1) |  |  |  |
| 42 | WRITTEN_PREMIUM | float | 104 | 100.0% | 104 | 100.0% | root (104) | 3127.72 (1) | 1561.36 (1) | 205.29 (1) | 2634.33 (1) | 3667.59 (1) |
| 43 | EFFECTIVE_DATE | str | 104 | 100.0% | 82 | 78.8% | root (104) | 2018-01-10 (3) | 2018-01-16 (3) | 2018-01-18 (3) | 2018-01-20 (3) | 2018-01-28 (3) |
| 44 | EXPIRATION_DATE | str | 104 | 100.0% | 87 | 83.7% | root (104) | 2019-01-16 (3) | 2019-01-18 (3) | 2018-07-29 (3) | 2019-01-30 (3) | 2016-10-08 (2) |
| 45 | POLICY_STATUS | str | 104 | 100.0% | 3 | 2.9% | root (104) | In Force (101) | Cancelled (2) | Lapsed (1) |  |  |
| 46 | PRODUCER_CODE | str | 104 | 100.0% | 6 | 5.8% | root (104) | AG-1177 (24) | AG-1042 (19) | AG-2210 (18) | AG-2385 (17) | AG-3460 (13) |
| 47 | CANCELLATION_REASON | str | 3 | 2.9% | 2 | 66.7% | root (104) | Material Misrepresentation (2) | Non-Payment (1) |  |  |  |
| 48 | CUSTOMER_TIER | str | 7 | 6.7% | 4 | 57.1% | root (104) | Platinum (3) | Silver (2) | Gold (1) | Bronze (1) |  |
