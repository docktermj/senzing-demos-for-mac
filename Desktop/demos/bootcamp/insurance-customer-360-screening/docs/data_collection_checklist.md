# Data Collection Checklist

## Data inventory

| Source | File | Records | Expected | Validated |
|---|---|---|---|---|
| PC_POLICY_ADMIN | `data/raw/pc_policy_admin_extract.jsonl` | 104 | 104 | yes |
| LIFE_ANNUITY_ADMIN | `data/raw/life_annuity_admin_extract.jsonl` | 75 | 75 | yes |
| CREDIT_BUREAU | `data/raw/credit_header.jsonl` | 30 | 30 | yes |
| BUSINESS_REGISTRY | `data/raw/business_registry.jsonl` | 22 | 22 | yes |
| SIU_CASES | `data/raw/siu_cases.jsonl` | 13 | 13 | yes |
| SANCTIONS_PEP | `data/raw/sanctions_pep.jsonl` | 4 | 4 | yes |
| **Total** | | **248** | **248** | |

## Validation checklist

- [x] Every file is readable UTF-8 without a byte-order mark
- [x] Every line is valid JSON
- [x] Record count matches the figure in the business problem, per source
- [x] Every record carries the expected `DATA_SOURCE` code
- [x] Every record has a present, unique `RECORD_ID` within its source
- [x] Registry `config/data_sources.yaml` updated for all six sources
- [x] Locations documented in `docs/data_source_locations.md`
- [x] Raw data excluded from version control (`.gitignore`)
- [ ] Quality assessed and mapping confirmed (next module: Data Quality, Mapping, and Transformation)
