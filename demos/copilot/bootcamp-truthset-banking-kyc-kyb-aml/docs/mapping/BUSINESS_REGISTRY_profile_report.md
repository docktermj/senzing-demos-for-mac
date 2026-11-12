**File Type:** jsonl
**Root Records:** 22
**Total Fields:** 36

## Structure Variations

**Distinct field patterns:** 2 (across 22 records)
**Common fields:** CATEGORY, DATA_SOURCE, FEATURES, RECORD_ID, STATUS
**Hint:** A 100% populated field with low cardinality (few unique values) likely indicates record type.

| # | Records | Fields unique to this pattern |
|---|---------|-------------------------------|
| 1 | 16 (73%) | — (only common fields) |
| 2 | 6 (27%) | DATE |

To profile each structure separately, re-run with: `--group_by <field>`

## Schema: BUSINESS_REGISTRY-regenerated

**Record Count:** 22
**List Elements (Tables):** 1
**Field Count:** 36

**Data Elements (All Lists):**
- FEATURES: 22 records

### Fields

| # | Field Name | Type | Records | Pop % | Unique | Unique % | Table Context | Sample 1 | Sample 2 | Sample 3 | Sample 4 | Sample 5 |
|---|------------|------|---------|-------|--------|----------|---------------|----------|----------|----------|----------|----------|
| 1 | DATA_SOURCE | str | 22 | 100.0% | 1 | 4.5% | root (22) | BUSINESS_REGISTRY (22) |  |  |  |  |
| 2 | RECORD_ID | str | 22 | 100.0% | 22 | 100.0% | root (22) | 2111 (1) | 2071 (1) | 2122 (1) | 2014 (1) | 2013 (1) |
| 3 | FEATURES | list | 22 |  |  |  | root (22) | 9 items (8) | 7 items (6) | 8 items (4) | 10 items (3) | 6 items (1) |
| 4 | FEATURES.RECORD_TYPE | str | 22 | 100.0% | 2 | 9.1% | FEATURES (22) | PERSON (16) | ORGANIZATION (6) |  |  |  |
| 5 | FEATURES.NAME_FULL | str | 14 | 63.6% | 14 | 100.0% | FEATURES (22) | Susan Meyer (1) | Salenger, Kristin (1) | Wang Wei (1) | Wang Jie (1) | Keeley Jones (1) |
| 6 | FEATURES.ADDR_TYPE | str | 37 | 168.2% | 4 | 10.8% | FEATURES (22) | REGISTERED (26) | PRIMARY (8) | HOME (2) | BUSINESS (1) |  |
| 7 | FEATURES.ADDR_LINE1 | str | 31 | 140.9% | 31 | 100.0% | FEATURES (22) | Fieldstrasse 10, FL-2198 Triesen (1) | 6001 Business Registry Demo Road (1) | 6002 Business Registry Demo Road (1) | 8321 Duke Street (1) | 6003 Business Registry Demo Road (1) |
| 8 | FEATURES.ADDR_COUNTRY | str | 28 | 127.3% | 6 | 21.4% | FEATURES (22) | United States (22) | Singapore (2) | Lichtenstein (1) | Germany (1) | Pakistan (1) |
| 9 | FEATURES.EMPLOYER | str | 9 | 40.9% | 3 | 33.3% | FEATURES (22) | Universal Exports, USA (5) | Universal Exports Worldwide (2) | Müllenkranz (2) |  |  |
| 10 | FEATURES.REL_POINTER_DOMAIN | str | 14 | 63.6% | 1 | 7.1% | FEATURES (22) | REFERENCE (14) |  |  |  |  |
| 11 | FEATURES.REL_POINTER_KEY | str | 14 | 63.6% | 4 | 28.6% | FEATURES (22) | 2071 (5) | 2074 (5) | 2011 (2) | 2041 (2) |  |
| 12 | FEATURES.REL_POINTER_ROLE | str | 14 | 63.6% | 7 | 50.0% | FEATURES (22) | Principal (5) | Global Parent (3) | Owns 50% (2) | Owns 40% (1) | Owns 60% (1) |
| 13 | FEATURES.DATE_OF_BIRTH | str | 16 | 72.7% | 16 | 100.0% | FEATURES (22) | 1971-12-06 (1) | 1982-11-17 (1) | 1997-09-14 (1) | 1993-09-14 (1) | 1961-02-23 (1) |
| 14 | FEATURES.EMAIL_ADDRESS | str | 22 | 100.0% | 20 | 90.9% | FEATURES (22) | kjones@universal.com (2) | patak@universal.com (2) | registry-2111@synthetic.invalid (1) | registry-2071@synthetic.invalid (1) | registry-2122@synthetic.invalid (1) |
| 15 | FEATURES.PHONE_NUMBER | str | 22 | 100.0% | 21 | 95.5% | FEATURES (22) | 320-392-2137 (2) | +1-555-100-2111 (1) | 800-111-1234 (1) | +1-555-100-2014 (1) | +1-555-100-2013 (1) |
| 16 | FEATURES.ADDR_CITY | str | 30 | 136.4% | 8 | 26.7% | FEATURES (22) | Registry Demo City (22) | Athens (2) | Los Angeles (1) | New York (1) | Singapore (1) |
| 17 | FEATURES.ADDR_STATE | str | 27 | 122.7% | 4 | 14.8% | FEATURES (22) | NV (22) | GA (2) | NY (2) | CA (1) |  |
| 18 | FEATURES.ADDR_POSTAL_CODE | str | 29 | 131.8% | 28 | 96.6% | FEATURES (22) | 30009 (2) | 89500 (1) | 89501 (1) | 90015 (1) | 89502 (1) |
| 19 | FEATURES.ADDR_FULL | str | 29 | 131.8% | 29 | 100.0% | FEATURES (22) | 6001 Business Registry Demo Road, Registry Demo Ci (1) | Hughes Plaza, 100 Howard Hughes Way, Las Vegas, NV (1) | 6002 Business Registry Demo Road, Registry Demo Ci (1) | 6003 Business Registry Demo Road, Registry Demo Ci (1) | 6004 Business Registry Demo Road, Registry Demo Ci (1) |
| 20 | FEATURES.NAME_ORG | str | 11 | 50.0% | 8 | 72.7% | FEATURES (22) | Universal Exports (4) | Universal Exports, USA (1) | Universal Exports Singapore (1) | Hajah Maimunah (1) | Universal Exports Worldwide (1) |
| 21 | FEATURES.NAME_TYPE | str | 5 | 22.7% | 1 | 20.0% | FEATURES (22) | AKA (5) |  |  |  |  |
| 22 | FEATURES.TAX_ID_NUMBER | str | 4 | 18.2% | 4 | 100.0% | FEATURES (22) | 84-1234567 (1) | 13-7654321 (1) | DE123456789 (1) | 32AABCU9603R1ZM (1) |  |
| 23 | FEATURES.TAX_ID_TYPE | str | 4 | 18.2% | 3 | 75.0% | FEATURES (22) | EIN (2) | VAT (1) | GSTIN (1) |  |  |
| 24 | FEATURES.TAX_ID_COUNTRY | str | 4 | 18.2% | 3 | 75.0% | FEATURES (22) | US (2) | DE (1) | IN (1) |  |  |
| 25 | FEATURES.REL_ANCHOR_DOMAIN | str | 4 | 18.2% | 1 | 25.0% | FEATURES (22) | REFERENCE (4) |  |  |  |  |
| 26 | FEATURES.REL_ANCHOR_KEY | str | 4 | 18.2% | 4 | 100.0% | FEATURES (22) | 2071 (1) | 2011 (1) | 2074 (1) | 2041 (1) |  |
| 27 | FEATURES.PHONE_TYPE | str | 3 | 13.6% | 2 | 66.7% | FEATURES (22) | MOBILE (2) | PRIMARY (1) |  |  |  |
| 28 | FEATURES.NAME_LAST | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | Alexopoulos (1) | Thomas (1) |  |  |  |
| 29 | FEATURES.NAME_FIRST | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | Anastassia (1) | Rosemary A (1) |  |  |  |
| 30 | FEATURES.NATIONAL_ID_NUMBER | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | 200912346B (1) | 201012345A (1) |  |  |  |
| 31 | FEATURES.NATIONAL_ID_TYPE | str | 2 | 9.1% | 1 | 50.0% | FEATURES (22) | UEN (2) |  |  |  |  |
| 32 | FEATURES.NATIONAL_ID_COUNTRY | str | 2 | 9.1% | 1 | 50.0% | FEATURES (22) | SG (2) |  |  |  |  |
| 33 | FEATURES.LEI_NUMBER | str | 2 | 9.1% | 2 | 100.0% | FEATURES (22) | 549300HKVFVS8QGCBR57 (1) | 5493001KJTIIGC8Y1R12 (1) |  |  |  |
| 34 | CATEGORY | str | 22 | 100.0% | 7 | 31.8% | root (22) | Director (5) | Contact (5) | Owner (5) | Corporation (4) | Proprietorship (1) |
| 35 | STATUS | str | 22 | 100.0% | 3 | 13.6% | root (22) | Current (11) | Active (10) | Inactive (1) |  |  |
| 36 | DATE | str | 6 | 27.3% | 5 | 83.3% | root (22) | 1990 (2) | 1994 (1) | 2010 (1) | 2009 (1) | 1998 (1) |
