**File Type:** jsonl
**Root Records:** 13
**Total Fields:** 24

## Schema: siu_cases

**Record Count:** 13
**List Elements (Tables):** 1
**Field Count:** 24

**Data Elements (All Lists):**
- FEATURES: 13 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 13 | 100.0% | 1 | 7.7% | root (13) | SIU_CASES (13) |  |  |  |  |
| 2 | RECORD_ID | str | 13 | 100.0% | 13 | 100.0% | root (13) | 1006 (1) | 1007 (1) | 1008 (1) | 1012 (1) | 1014 (1) |
| 3 | FEATURES | list | 13 |  |  |  | root (13) | 3 items (5) | 5 items (4) | 4 items (4) |  |  |
| 4 | FEATURES.RECORD_TYPE | str | 13 | 100.0% | 1 | 7.7% | FEATURES (13) | PERSON (13) |  |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 12 | 92.3% | 9 | 75.0% | FEATURES (13) | Smith (4) | Smith Sr (1) | Kusha (1) | Knight (1) | Antoun (1) |
| 6 | FEATURES.NAME_FIRST | str | 12 | 92.3% | 8 | 66.7% | FEATURES (13) | Robert (2) | Eddie (2) | Maria (2) | John (2) | Patricia (1) |
| 7 | FEATURES.NAME_MIDDLE | str | 1 | 7.7% | 1 | 100.0% | FEATURES (13) | E (1) |  |  |  |  |
| 8 | FEATURES.DATE_OF_BIRTH | str | 7 | 53.8% | 5 | 71.4% | FEATURES (13) | 1/7/80 (2) | 12/11/73 (2) | 3/31/1954 (1) | 3/1/1970 (1) | 3/1/70 (1) |
| 9 | FEATURES.ADDR_TYPE | str | 8 | 61.5% | 2 | 25.0% | FEATURES (13) | HOME (5) | MAILING (3) |  |  |  |
| 10 | FEATURES.ADDR_LINE1 | str | 10 | 76.9% | 8 | 80.0% | FEATURES (13) | 638 Downey St, Salem, OR (2) | 3212 W. 32nd St Palm Harbor, FL 60527 (2) | 123 Main St, Las Vegas  (1) | 1515 Adela Ln, LV, NV (1) | 1602 Brenville Pl, San Francisco, CA 94105 (1) |
| 11 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 1 | 7.7% | 1 | 100.0% | FEATURES (13) | 112233 (1) |  |  |  |  |
| 12 | FEATURES.DRIVERS_LICENSE_STATE | str | 1 | 7.7% | 1 | 100.0% | FEATURES (13) | NV (1) |  |  |  |  |
| 13 | FEATURES.ADDR_POSTAL_CODE | str | 5 | 38.5% | 4 | 80.0% | FEATURES (13) | 94105 (2) | 89132 (1) | 43004 (1) | 23208 (1) |  |
| 14 | FEATURES.EMAIL_ADDRESS | str | 5 | 38.5% | 4 | 80.0% | FEATURES (13) | msentosa@fmail.com (2) | psmith@email.com (1) | robert.smith@email.com (1) | Kusha123@hmail.com (1) |  |
| 15 | FEATURES.ADDR_CITY | str | 4 | 30.8% | 3 | 75.0% | FEATURES (13) | San Francisco (2) | Blacklick (1) | Arlington (1) |  |  |
| 16 | FEATURES.ADDR_STATE | str | 3 | 23.1% | 2 | 66.7% | FEATURES (13) | CA (2) | OH (1) |  |  |  |
| 17 | FEATURES.SSN_NUMBER | str | 2 | 15.4% | 2 | 100.0% | FEATURES (13) | 294-66-9999 (1) | 201-77-7719 (1) |  |  |  |
| 18 | FEATURES.GENDER | str | 1 | 7.7% | 1 | 100.0% | FEATURES (13) | M (1) |  |  |  |  |
| 19 | SIU_CASE_NUMBER | str | 13 | 100.0% | 13 | 100.0% | root (13) | SIU-2017-10582 (1) | SIU-2020-18537 (1) | SIU-2019-19023 (1) | SIU-2019-10197 (1) | SIU-2015-13011 (1) |
| 20 | REFERRAL_DATE | str | 13 | 100.0% | 11 | 84.6% | root (13) | 2019-03-05 (2) | 2015-05-07 (2) | 2017-01-03 (1) | 2020-02-04 (1) | 2019-04-06 (1) |
| 21 | FRAUD_TYPE | str | 13 | 100.0% | 5 | 38.5% | root (13) | Staged Accident (3) | Inflated Claim (3) | Application Misrepresentation (3) | Premium Fraud (2) | Provider Billing Fraud (2) |
| 22 | ROLE_IN_CASE | str | 13 | 100.0% | 5 | 38.5% | root (13) | Claimant (5) | Insured (4) | Witness (2) | Medical Provider (1) | Attorney (1) |
| 23 | LINE_OF_BUSINESS | str | 13 | 100.0% | 3 | 23.1% | root (13) | Personal Auto (8) | Homeowners (3) | Workers Compensation (2) |  |  |
| 24 | CASE_DISPOSITION | str | 13 | 100.0% | 4 | 30.8% | root (13) | Substantiated (6) | Open (3) | Referred to Law Enforcement / DOI (2) | Unsubstantiated (2) |  |
