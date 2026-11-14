**File Type:** jsonl
**Root Records:** 82
**Total Fields:** 47

## Schema: card_cardholder_master

**Record Count:** 82
**List Elements (Tables):** 1
**Field Count:** 47

**Data Elements (All Lists):**
- FEATURES: 79 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 82 | 100.0% | 1 | 1.2% | root (82) | CARD_SERVICES (82) |  |  |  |  |
| 2 | RECORD_ID | str | 82 | 100.0% | 82 | 100.0% | root (82) | 1002 (1) | 1004 (1) | 1010 (1) | 1016 (1) | 1018 (1) |
| 3 | FEATURES | list | 82 |  |  |  | root (82) | 4 items (32) | 5 items (19) | 3 items (13) | 6 items (8) | 7 items (7) |
| 4 | FEATURES.RECORD_TYPE | str | 79 | 100.0% | 2 | 2.5% | FEATURES (79) | PERSON (67) | ORGANIZATION (12) |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 66 | 83.5% | 51 | 77.3% | FEATURES (79) | Smith (5) | Kusha (3) | Jones (3) | Mooney (2) | Wang (2) |
| 6 | FEATURES.NAME_FIRST | str | 66 | 83.5% | 57 | 86.4% | FEATURES (79) | Marie (3) | Maria Luis (2) | Pat (2) | Susan (2) | Jie (2) |
| 7 | FEATURES.DATE_OF_BIRTH | str | 43 | 54.4% | 40 | 93.0% | FEATURES (79) | 4/12/82 (3) | 3/12/87 (2) | 11/12/1978 (1) | 11/12/1979 (1) | Mar 1 1970 (1) |
| 8 | FEATURES.ADDR_TYPE | str | 46 | 58.2% | 5 | 10.9% | FEATURES (79) | HOME (29) | BUSINESS (9) | MAILING (4) | PRIMARY (3) | REGISTERED (1) |
| 9 | FEATURES.ADDR_LINE1 | str | 55 | 69.6% | 46 | 83.6% | FEATURES (79) | 1304 Poppy Hills Dr (3) | 9304 W. 15th St La Blanca, FL 60527 (2) | 3212 W. 32nd St Palm Harbor, FL 60527 (2) | 12396 Austin Rd (2) | 1824 AspenOak Way (2) |
| 10 | FEATURES.ADDR_CITY | str | 39 | 49.4% | 20 | 51.3% | FEATURES (79) | Sacramento (5) | Elmwood Park (5) | Las Vegas (3) | Seattle (3) | Tucson (3) |
| 11 | FEATURES.ADDR_STATE | str | 36 | 45.6% | 12 | 33.3% | FEATURES (79) | CA (12) | WA (6) | NV (3) | AZ (3) | IL (2) |
| 12 | FEATURES.ADDR_POSTAL_CODE | str | 38 | 48.1% | 23 | 60.5% | FEATURES (79) | 95823 (6) | 95865 (4) | 89111 (2) | 43004 (2) | 98101 (2) |
| 13 | FEATURES.PHONE_TYPE | str | 16 | 20.3% | 2 | 12.5% | FEATURES (79) | MOBILE (10) | HOME (6) |  |  |  |
| 14 | FEATURES.PHONE_NUMBER | str | 17 | 21.5% | 15 | 88.2% | FEATURES (79) | +39 0352 6553537 (2) | 503-555-0155 (2) | 702-919-1300 (1) | 512-353-8633 (1) | 321-3212 (1) |
| 15 | FEATURES.EMAIL_ADDRESS | str | 35 | 44.3% | 18 | 51.4% | FEATURES (79) | info@ca-state.gov (13) | guptapra55@Yahoo.com (2) | kob.pdx@fmail.com (2) | castillo.home@fmail.com (2) | whitcombe.family@fmail.com (2) |
| 16 | FEATURES.PASSPORT_NUMBER | str | 9 | 11.4% | 9 | 100.0% | FEATURES (79) | 10251111 (1) | 10252222 (1) | 483290175 (1) | 72129291 (1) | 1231345345 (1) |
| 17 | FEATURES.PASSPORT_COUNTRY | str | 8 | 10.1% | 5 | 62.5% | FEATURES (79) | US (3) | USA (2) | CAN (1) | Germany (1) | Canada (1) |
| 18 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 5 | 6.3% | 5 | 100.0% | FEATURES (79) | 93939211 (1) | 8923322 (1) | 73423499 (1) | 234234455 (1) | 823123 (1) |
| 19 | FEATURES.DRIVERS_LICENSE_STATE | str | 5 | 6.3% | 4 | 80.0% | FEATURES (79) | MN (2) | CA (1) | OR (1) | Texas (1) |  |
| 20 | FEATURES.SSN_NUMBER | str | 10 | 12.7% | 10 | 100.0% | FEATURES (79) | 521-21-2123 (1) | 3241 (1) | 655-31-2020 (1) | 179-44-2938 (1) | 512-38-1170 (1) |
| 21 | FEATURES.GENDER | str | 19 | 24.1% | 5 | 26.3% | FEATURES (79) | M (7) | F (6) | Male (2) | Female (2) | Unknown (2) |
| 22 | FEATURES.NATIONAL_ID_NUMBER | str | 2 | 2.5% | 2 | 100.0% | FEATURES (79) | 832721 (1) | 7123833 (1) |  |  |  |
| 23 | FEATURES.NATIONAL_ID_COUNTRY | str | 2 | 2.5% | 2 | 100.0% | FEATURES (79) | Hong Kong (1) | China (1) |  |  |  |
| 24 | FEATURES.ADDR_COUNTRY | str | 5 | 6.3% | 4 | 80.0% | FEATURES (79) | Germany (2) | CAN (1) | Cambodia (1) | Singapore (1) |  |
| 25 | FEATURES.NAME_MIDDLE | str | 1 | 1.3% | 1 | 100.0% | FEATURES (79) | Marie (1) |  |  |  |  |
| 26 | FEATURES.NAME_ORG | str | 14 | 17.7% | 14 | 100.0% | FEATURES (79) | Mullenkrants  (1) | Autoworks (1) | Univrsl Export Inc (1) | Worldwide Exports  (1) | Singapore exports (1) |
| 27 | FEATURES.NAME_TYPE | str | 1 | 1.3% | 1 | 100.0% | FEATURES (79) | AKA (1) |  |  |  |  |
| 28 | FEATURES.ADDR_FULL | str | 3 | 3.8% | 2 | 66.7% | FEATURES (79) | 78, DELHI FUND BLDG, DELHI 110021 (2) | 133 New Bridge Road, Chinatown Point, Singapore 05 (1) |  |  |  |
| 29 | FEATURES.TAX_ID_NUMBER | str | 7 | 8.9% | 5 | 71.4% | FEATURES (79) | 77-7000002 (2) | 77-7000003 (2) | 91-5550001 (1) | AAKY6799J (1) | AMELV9997R (1) |
| 30 | FEATURES.TAX_ID_TYPE | str | 5 | 6.3% | 1 | 20.0% | FEATURES (79) | EIN (5) |  |  |  |  |
| 31 | FEATURES.TAX_ID_COUNTRY | str | 5 | 6.3% | 1 | 20.0% | FEATURES (79) | US (5) |  |  |  |  |
| 32 | FEATURES.NPI_NUMBER | str | 4 | 5.1% | 2 | 50.0% | FEATURES (79) | 1234567893 (2) | 1987654320 (2) |  |  |  |
| 33 | FEATURES.REL_ANCHOR_DOMAIN | str | 2 | 2.5% | 1 | 50.0% | FEATURES (79) | CUSTOMERS (2) |  |  |  |  |
| 34 | FEATURES.REL_ANCHOR_KEY | str | 2 | 2.5% | 2 | 100.0% | FEATURES (79) | 1312 (1) | 3063 (1) |  |  |  |
| 35 | FEATURES.NAME_FULL | str | 2 | 2.5% | 2 | 100.0% | FEATURES (79) | MEENU J GUPTA (1) | GUPTA MAMTA J (1) |  |  |  |
| 36 | FEATURES.REL_POINTER_DOMAIN | str | 1 | 1.3% | 1 | 100.0% | FEATURES (79) | CUSTOMERS (1) |  |  |  |  |
| 37 | FEATURES.REL_POINTER_KEY | str | 1 | 1.3% | 1 | 100.0% | FEATURES (79) | 3063 (1) |  |  |  |  |
| 38 | FEATURES.REL_POINTER_ROLE | str | 1 | 1.3% | 1 | 100.0% | FEATURES (79) | Spouse (1) |  |  |  |  |
| 39 | CARD_ACCOUNT_ID | str | 82 | 100.0% | 82 | 100.0% | root (82) | CA34365926 (1) | CA36733949 (1) | CA35043460 (1) | CA83005851 (1) | CA10963410 (1) |
| 40 | PAN_LAST4 | str | 82 | 100.0% | 82 | 100.0% | root (82) | 1209 (1) | 1325 (1) | 7735 (1) | 3497 (1) | 3418 (1) |
| 41 | CARDHOLDER_ROLE | str | 82 | 100.0% | 2 | 2.4% | root (82) | Primary (81) | Authorized User (1) |  |  |  |
| 42 | PRODUCT | str | 82 | 100.0% | 4 | 4.9% | root (82) | Rewards (36) | Cash Back (27) | Business (13) | Secured (6) |  |
| 43 | CREDIT_LIMIT | int | 82 | 100.0% | 9 | 11.0% | root (82) | 10000 (15) | 15000 (12) | 1000 (10) | 5000 (10) | 20000 (10) |
| 44 | CURRENT_BALANCE | float | 79 | 96.3% | 79 | 100.0% | root (82) | 1545.29 (1) | 2422.38 (1) | 2508.29 (1) | 3811.67 (1) | 2470.03 (1) |
| 45 | OPEN_DATE | str | 82 | 100.0% | 66 | 80.5% | root (82) | 2018-01-13 (4) | 2018-01-17 (3) | 2018-01-11 (2) | 2018-01-03 (2) | 2018-01-05 (2) |
| 46 | ACCOUNT_STATUS | str | 82 | 100.0% | 3 | 3.7% | root (82) | Open (78) | Closed (3) | Charged Off (1) |  |  |
| 47 | DELINQUENCY_BUCKET | str | 82 | 100.0% | 4 | 4.9% | root (82) | Current (67) | 30 DPD (7) | 60 DPD (5) | 90+ DPD (3) |  |
