**File Type:** jsonl
**Root Records:** 17
**Total Fields:** 25

## Schema: SIU_WATCHLIST-improved

**Record Count:** 17
**List Elements (Tables):** 1
**Field Count:** 25

**Data Elements (All Lists):**
- FEATURES: 17 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 17 | 100.0% | 1 | 5.9% | root (17) | WATCHLIST (17) |  |  |  |  |
| 2 | RECORD_ID | str | 17 | 100.0% | 17 | 100.0% | root (17) | 1006 (1) | 1007 (1) | 1008 (1) | 1012 (1) | 1014 (1) |
| 3 | FEATURES | list | 17 |  |  |  | root (17) | 4 items (7) | 3 items (6) | 5 items (4) |  |  |
| 4 | FEATURES.RECORD_TYPE | str | 17 | 100.0% | 1 | 5.9% | FEATURES (17) | PERSON (17) |  |  |  |  |
| 5 | FEATURES.NAME_LAST | str | 12 | 70.6% | 9 | 75.0% | FEATURES (17) | Smith (4) | Smith Sr (1) | Kusha (1) | Knight (1) | Antoun (1) |
| 6 | FEATURES.NAME_FIRST | str | 12 | 70.6% | 8 | 66.7% | FEATURES (17) | Robert (2) | Eddie (2) | Maria (2) | John (2) | Patricia (1) |
| 7 | FEATURES.NAME_MIDDLE | str | 1 | 5.9% | 1 | 100.0% | FEATURES (17) | E (1) |  |  |  |  |
| 8 | FEATURES.DATE_OF_BIRTH | str | 7 | 41.2% | 5 | 71.4% | FEATURES (17) | 1/7/80 (2) | 12/11/73 (2) | 03/31/1954 (1) | 3/1/1970 (1) | 3/1/70 (1) |
| 9 | FEATURES.ADDR_TYPE | str | 10 | 58.8% | 3 | 30.0% | FEATURES (17) | HOME (6) | MAILING (3) | PRIMARY (1) |  |  |
| 10 | FEATURES.ADDR_LINE1 | str | 13 | 76.5% | 11 | 84.6% | FEATURES (17) | 638 Downey St, Salem, OR (2) | 3212 W. 32nd St Palm Harbor, FL 60527 (2) | 123 Main St, Las Vegas (1) | 1515 Adela Ln, LV, NV (1) | 1602 Brenville Pl, San Francisco, CA 94105 (1) |
| 11 | FEATURES.DRIVERS_LICENSE_NUMBER | str | 1 | 5.9% | 1 | 100.0% | FEATURES (17) | 112233 (1) |  |  |  |  |
| 12 | FEATURES.DRIVERS_LICENSE_STATE | str | 1 | 5.9% | 1 | 100.0% | FEATURES (17) | NV (1) |  |  |  |  |
| 13 | FEATURES.ADDR_POSTAL_CODE | str | 5 | 29.4% | 4 | 80.0% | FEATURES (17) | 94105 (2) | 89132 (1) | 43004 (1) | 23208 (1) |  |
| 14 | FEATURES.EMAIL_ADDRESS | str | 5 | 29.4% | 4 | 80.0% | FEATURES (17) | msentosa@fmail.com (2) | psmith@email.com (1) | robert.smith@email.com (1) | Kusha123@hmail.com (1) |  |
| 15 | FEATURES.ADDR_CITY | str | 7 | 41.2% | 6 | 85.7% | FEATURES (17) | San Francisco (2) | Blacklick (1) | Arlington (1) | Lahore (1) | Smolensk (1) |
| 16 | FEATURES.ADDR_STATE | str | 4 | 23.5% | 3 | 75.0% | FEATURES (17) | CA (2) | OH (1) | NV (1) |  |  |
| 17 | FEATURES.SSN_NUMBER | str | 2 | 11.8% | 2 | 100.0% | FEATURES (17) | 294-66-9999 (1) | 201-77-7719 (1) |  |  |  |
| 18 | FEATURES.GENDER | str | 1 | 5.9% | 1 | 100.0% | FEATURES (17) | M (1) |  |  |  |  |
| 19 | FEATURES.NAME_FULL | str | 4 | 23.5% | 4 | 100.0% | FEATURES (17) | فيصل صديقي (1) | ВАСИЛЬЕВ, Александр Павлович (1) | Hughes, Howie (1) | Charney, Peggie (1) |  |
| 20 | FEATURES.ADDR_COUNTRY | str | 1 | 5.9% | 1 | 100.0% | FEATURES (17) | Pakistan (1) |  |  |  |  |
| 21 | FEATURES.PHONE_NUMBER | str | 2 | 11.8% | 2 | 100.0% | FEATURES (17) | 92427925774 (1) | 4812856234 (1) |  |  |  |
| 22 | FEATURES.EMPLOYER | str | 2 | 11.8% | 1 | 50.0% | FEATURES (17) | Universal Exports (2) |  |  |  |  |
| 23 | CATEGORY | str | 17 | 100.0% | 3 | 17.6% | root (17) | FRAUD (13) | SANCTIONED (2) | PEP (2) |  |  |
| 24 | DATE | str | 17 | 100.0% | 14 | 82.4% | root (17) | 3/5/19 (2) | 5/7/15 (2) | 4/3/2019 (2) | 1/3/17 (1) | 2/4/20 (1) |
| 25 | STATUS | str | 17 | 100.0% | 3 | 17.6% | root (17) | Active (11) | Current (4) | Inactive (2) |  |  |
