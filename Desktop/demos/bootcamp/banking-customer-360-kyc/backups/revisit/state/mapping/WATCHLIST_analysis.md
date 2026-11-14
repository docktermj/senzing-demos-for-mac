# Senzing JSON Analysis Report

**Records:** 17 | **Config:** default config
**Data Sources:** WATCHLIST (17)

## Feature Analysis

| Feature | Freq | Records | Pop% | Unique | Uniq% | Top Values |
|---------|------|---------|------|--------|-------|------------|
| **RECORD_TYPE** | FVM | 17 | 100.0% | 1 | 5.9% | PERSON (17) |
| **NAME** | NAME | 16 | 94.1% | 15 | 93.8% | Smith John (2), Smith Sr Robert E (1), Smith Patricia (1) |
| NAME_FULL | | 4 | 23.5% | 4 | 100.0% | فيصل صديقي (1), ВАСИЛЬЕВ, Александр Павлович (1), Hughes, Howie (1) |
| NAME_LAST | | 12 | 70.6% | 9 | 75.0% | Smith (4), Smith Sr (1), Kusha (1) |
| NAME_FIRST | | 12 | 70.6% | 8 | 66.7% | Robert (2), Eddie (2), Maria (2) |
| NAME_MIDDLE | | 1 | 5.9% | 1 | 100.0% | E (1) |
| **GENDER** | FVM | 1 | 5.9% | 1 | 100.0% | M (1) |
| **DOB** | FM | 7 | 41.2% | 4 | 57.1% | 1970-03-01 (2), 1980-01-07 (2), 1973-12-11 (2) |
| DATE_OF_BIRTH | | 7 | 41.2% | 4 | 57.1% | 1970-03-01 (2), 1980-01-07 (2), 1973-12-11 (2) |
| **ADDRESS** | FF | 13 | 76.5% | 11 | 84.6% | 638 Downey St, Salem, OR (2), 3212 W. 32nd St Palm Harbor, FL 60527 (2), 123 Main St, Las Vegas  (1) |
| ADDR_LINE1 | | 13 | 76.5% | 11 | 84.6% | 638 Downey St, Salem, OR (2), 3212 W. 32nd St Palm Harbor, FL 60527 (2), 123 Main St, Las Vegas  (1) |
| ADDR_CITY | | 7 | 41.2% | 6 | 85.7% | San Francisco (2), Blacklick (1), Arlington (1) |
| ADDR_STATE | | 4 | 23.5% | 3 | 75.0% | CA (2), OH (1), NV (1) |
| ADDR_POSTAL_CODE | | 5 | 29.4% | 4 | 80.0% | 94105 (2), 89132 (1), 43004 (1) |
| ADDR_COUNTRY | | 1 | 5.9% | 1 | 100.0% | Pakistan (1) |
| **PHONE** | FF | 2 | 11.8% | 2 | 100.0% | 92-42-7925774 (1), (4812)85-62-34 (1) |
| PHONE_NUMBER | | 2 | 11.8% | 2 | 100.0% | 92-42-7925774 (1), (4812)85-62-34 (1) |
| **EMAIL** | F1 | 5 | 29.4% | 4 | 80.0% | msentosa@fmail.com (2), psmith@email.com (1), robert.smith@email.com (1) |
| EMAIL_ADDRESS | | 5 | 29.4% | 4 | 80.0% | msentosa@fmail.com (2), psmith@email.com (1), robert.smith@email.com (1) |
| **SSN** | F1 | 2 | 11.8% | 2 | 100.0% | 294-66-9999 (1), 201-77-7719 (1) |
| SSN_NUMBER | | 2 | 11.8% | 2 | 100.0% | 294-66-9999 (1), 201-77-7719 (1) |
| **DRLIC** | F1 | 1 | 5.9% | 1 | 100.0% | 112233 NV (1) |
| DRIVERS_LICENSE_NUMBER | | 1 | 5.9% | 1 | 100.0% | 112233 (1) |
| DRIVERS_LICENSE_STATE | | 1 | 5.9% | 1 | 100.0% | NV (1) |
| **EMPLOYER** | FF | 2 | 11.8% | 1 | 50.0% | Universal Exports (2) |

## Payload Attributes

| Attribute | Records | Pop% | Unique | Top Values |
|-----------|---------|------|--------|------------|
| LISTED_DATE | 17 | 100.0% | 14 | 3/5/19 (2), 5/7/15 (2), 4/3/2019 (2) |
| LIST_STATUS | 17 | 100.0% | 3 | Active (11), Current (4), Inactive (2) |
| LIST_TYPE | 17 | 100.0% | 3 | Internal Fraud (13), Sanctions (2), PEP (2) |

## Warnings

- DRLIC population is 5.9% (< 25%)
- SSN population is 11.8% (< 25%)
- PHONE population is 11.8% (< 25%)
- EMPLOYER population is 11.8% (< 25%)
- EMPLOYER uniqueness is 50.0% (expected > 60% for FF)
