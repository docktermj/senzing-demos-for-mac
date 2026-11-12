# Transformation Lineage: BILLING_SYSTEM

| Item | Value |
|---|---|
| Collected file | `data/raw/billing_system.csv` (4,000 records, quality 76.0) |
| Format-normalized file (Phase 1 Step 7a) | `data/raw/billing_system-improved.csv` (4,000 records, quality 77.7): phone, invoice date, state and ZIP formats normalized |
| Regenerated file (return to Data collection) | `data/raw/billing_system-regenerated.csv` (341,032 bytes, 4,000 records, quality 90.2): same records, gaps narrowed |
| Transformation program | `src/transform/transform_billing_system.py` |
| Output file | `data/senzing-ready/billing_system.jsonl` (1,133,922 bytes, 4,000 records) |
| Records in / out / rejected | 4,000 / 4,000 / 0 |
| Filters | none |
| Format changes in the mapper | none: every value is emitted verbatim |
| Fields dropped | `last_invoice_date` |

Field mappings: `docs/mapping/BILLING_SYSTEM_mapper.md`. Sample output: `data/mapping/billing_system_sample.jsonl`.
