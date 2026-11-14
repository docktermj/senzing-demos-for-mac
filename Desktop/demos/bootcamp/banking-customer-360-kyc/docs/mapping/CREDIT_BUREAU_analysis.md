# Senzing JSON Analysis Report

**Records:** 30 | **Config:** default config
**Data Sources:** CREDIT_BUREAU (30)

## Feature Analysis

| Feature | Freq | Records | Pop% | Unique | Uniq% | Top Values |
|---------|------|---------|------|--------|-------|------------|
| **RECORD_TYPE** | FVM | 30 | 100.0% | 1 | 3.3% | PERSON (30) |
| **NAME** | NAME | 30 | 100.0% | 30 | 100.0% | Margaret Ellis (2), Whitfield Gloria Ann (1), Reyes Gloria A (1) |
| NAME_LAST | | 31 | 103.3% | 28 | 90.3% | Pruitt (2), Farraday (2), Ellis (2) |
| NAME_FIRST | | 31 | 103.3% | 27 | 87.1% | Gloria (2), Marcus (2), Rosalind (2) |
| NAME_MIDDLE | | 2 | 6.7% | 2 | 100.0% | Ann (1), A (1) |
| **GENDER** | FVM | 20 | 66.7% | 2 | 10.0% | F (13), M (7) |
| **DOB** | FM | 30 | 100.0% | 30 | 100.0% | 2/14/1961 (1), 3/9/1958 (1), 7/4/1955 (1) |
| DATE_OF_BIRTH | | 30 | 100.0% | 30 | 100.0% | 2/14/1961 (1), 3/9/1958 (1), 7/4/1955 (1) |
| **ADDRESS** | FF | 30 | 100.0% | 24 | 80.0% | 1500 Willow Glen Rd San Jose CA 95125 (7), 12 Beacon St Boston MA 02108 (2), 4412 E Larkspur Ave Tucson AZ 85712 (1) |
| ADDR_LINE1 | | 31 | 103.3% | 24 | 77.4% | 1500 Willow Glen Rd (7), 12 Beacon St (2), 4412 E Larkspur Ave (1) |
| ADDR_CITY | | 31 | 103.3% | 22 | 71.0% | San Jose (7), Fresno (2), Albany (2) |
| ADDR_STATE | | 31 | 103.3% | 15 | 48.4% | CA (11), AZ (3), ME (2) |
| ADDR_POSTAL_CODE | | 31 | 103.3% | 22 | 71.0% | 95125 (7), 93701 (2), 12207 (2) |
| **PHONE** | FF | 23 | 76.7% | 24 | 104.3% | 520-555-0177 (1), 208-555-0141 (1), 207-555-0161 (1) |
| PHONE_NUMBER | | 24 | 80.0% | 24 | 100.0% | 520-555-0177 (1), 208-555-0141 (1), 207-555-0161 (1) |
| **SSN** | F1 | 24 | 80.0% | 23 | 95.8% | 677-42-3131 (2), 521-21-2123 (1), 612-44-1001 (1) |
| SSN_NUMBER | | 24 | 80.0% | 23 | 95.8% | 677-42-3131 (2), 521-21-2123 (1), 612-44-1001 (1) |
| **REL_ANCHOR** | NONE | 1 | 3.3% | 1 | 100.0% | REFERENCE 3048 (1) |
| REL_ANCHOR_DOMAIN | | 1 | 3.3% | 1 | 100.0% | REFERENCE (1) |
| REL_ANCHOR_KEY | | 1 | 3.3% | 1 | 100.0% | 3048 (1) |

## Payload Attributes

| Attribute | Records | Pop% | Unique | Top Values |
|-----------|---------|------|--------|------------|
| FILE_SINCE | 11 | 36.7% | 2 | 1998-01 (10), 1979-03 (1) |
| FILE_STATUS | 30 | 100.0% | 1 | Active (30) |

## Warnings

- Output has REL_ANCHOR(s) but ZERO REL_POINTER anywhere — a relationship needs BOTH the link (REL_POINTER) and an addressable target (REL_ANCHOR). Only one type present means the relationship structure is incomplete: anchor every participating record (REL_ANCHOR_KEY = RECORD_ID) and emit a REL_POINTER for each link. ACCEPTED with explanation: Anchor target of the cross-source relationship: CORE_BANKING 3050 holds REL_POINTER REFERENCE/3048 (Carer) in data/senzing-ready/core_banking.jsonl
