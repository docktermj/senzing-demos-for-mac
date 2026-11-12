# Data Collection Checklist

## Data Inventory

| Data source | File | Format | Records | Provenance | Validation |
|---|---|---|---:|---|---|
| BANK_CUSTOMERS | `data/raw/BANK_CUSTOMERS.jsonl` | JSONL | 120 | Senzing synthetic demo Truth Set | Passed |
| BUSINESS_REGISTRY | `data/raw/BUSINESS_REGISTRY.jsonl` | JSONL | 22 | Senzing synthetic demo Truth Set | Passed |
| AML_WATCHLIST | `data/raw/AML_WATCHLIST.jsonl` | JSONL | 17 | Senzing synthetic demo Truth Set | Passed |

## Validation Checklist

- [x] Each file is readable, non-empty, UTF-8 JSONL.
- [x] Measured record counts match the Senzing MCP source counts.
- [x] Every record has a `RECORD_ID` and the expected `DATA_SOURCE` label.
- [x] Record IDs, attribute names, and values are unchanged apart from the `DATA_SOURCE` relabel.
- [x] Raw files under `data/raw/` are excluded by `.gitignore`.
- [ ] Bootcamper reviewed the inventory and confirmed these are the intended source files before Module 5.
