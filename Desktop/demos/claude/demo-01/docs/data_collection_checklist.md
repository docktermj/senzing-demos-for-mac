# Data Collection Checklist

## Data Inventory

| Source | File | Format | Records | Validation |
|---|---|---|---|---|
| CRM_SYSTEM | `data/raw/crm_system.csv` | csv | 4,000 | passed |
| BILLING_SYSTEM | `data/raw/billing_system.csv` | csv | 4,000 | passed |
| SUPPORT_TICKETS | `data/raw/support_tickets.csv` | csv | 2,000 | passed |

Total: 10,000 records.

## Validation Checklist

- [x] **CRM_SYSTEM**: listed in `data/raw/`; first lines shown; readable; non-empty; header present; record count matches the scenario (4,000); record keys unique (`validation_status: passed`).
- [x] **BILLING_SYSTEM**: listed in `data/raw/`; first lines shown; readable; non-empty; header present; record count matches the scenario (4,000); record keys unique (`validation_status: passed`).
- [x] **SUPPORT_TICKETS**: listed in `data/raw/`; first lines shown; readable; non-empty; header present; record count matches the scenario (2,000); record keys unique (`validation_status: passed`).
