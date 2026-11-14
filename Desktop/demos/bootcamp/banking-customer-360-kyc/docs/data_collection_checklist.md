# Data Collection Checklist

## Data Inventory

| # | Data source | File in `data/raw/` | Records | Entity types | Collected | Validated |
|---|---|---|---|---|---|---|
| 1 | CORE_BANKING | `core_cif_extract.jsonl` | 97 | PERSON 86, ORGANIZATION 8 | ✅ | ✅ |
| 2 | CARD_SERVICES | `card_cardholder_master.jsonl` | 82 | PERSON 67, ORGANIZATION 12 | ✅ | ✅ |
| 3 | CREDIT_BUREAU | `credit_header.jsonl` | 30 | PERSON 30 | ✅ | ✅ |
| 4 | BUSINESS_REGISTRY | `business_registry.jsonl` | 22 | ORGANIZATION 6, PERSON 16 | ✅ | ✅ |
| 5 | WATCHLIST | `screening_list.jsonl` | 17 | PERSON 17 | ✅ | ✅ |
| | **Total** | | **248** | | | |

## Validation Checklist

Applied to every source file (results recorded per source in `config/data_sources.yaml`):

- [x] File exists in `data/raw/` and is non-empty
- [x] Readable as UTF-8, with no byte-order mark
- [x] Every line is valid JSON
- [x] Every record carries the file's single `DATA_SOURCE` code
- [x] Every record has a `RECORD_ID`, and the IDs are unique within the file
- [x] Record count equals the count in the business problem (248 in total)
- [x] Data provenance recorded (`own`: supplied by the bootcamper)
- [x] Data privacy reviewed (synthetic data; see `docs/security_compliance.md`)
- [x] Raw data excluded from version control (`.gitignore`)
- [x] Total (248) is within the active licence limit (500)
- [ ] Quality assessed and mapping confirmed (next module)
- [ ] Loaded into Senzing (Data processing)
