# Data Collection Checklist

Review the prefilled inventory rows and complete this checklist before Data Quality, Mapping, and Transformation.

## Data Inventory

| Business source | Truth Set source | File path | Format | Measured / expected records | Validation |
|---|---|---|---|---:|---|
| POLICYHOLDERS | CUSTOMERS | `data/raw/POLICYHOLDERS.jsonl` | JSONL | 120 / 120 | Passed |
| VENDOR_REGISTRY | REFERENCE | `data/raw/VENDOR_REGISTRY.jsonl` | JSONL | 22 / 22 | Passed |
| SIU_WATCHLIST | WATCHLIST | `data/raw/SIU_WATCHLIST.jsonl` | JSONL | 17 / 17 | Passed |

## Validation Checklist

- [x] Each collected file exists, is readable, non-empty, UTF-8, and valid JSONL.
- [x] Each measured record count matches the count reported by the Truth Set catalog.
- [x] `data/raw/*` is excluded from version control.
- [x] Review and confirm the source-to-business-source assignments.
- [x] Confirm the collection and validation details are complete before continuing.

## Load Gate

- [ ] Obtain reviewer approval before loading any of these records. They remain unloaded.

## Data Source Collection Status

- ✅ POLICYHOLDERS — Collected (`data/raw/POLICYHOLDERS.jsonl`)
- ✅ VENDOR_REGISTRY — Collected (`data/raw/VENDOR_REGISTRY.jsonl`)
- ✅ SIU_WATCHLIST — Collected (`data/raw/SIU_WATCHLIST.jsonl`)
