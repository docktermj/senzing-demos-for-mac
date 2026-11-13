# Data Collection Checklist

## Data Inventory

| Data source | File | Format | Records | Entity type | Collected | Validated |
|---|---|---|---|---|---|---|
| CRM_CONTACTS | `data/raw/crm_contacts.csv` | CSV | 4,000 | People | ✅ | ✅ |
| WEB_ORDERS | `data/raw/web_orders.csv` | CSV | 4,500 | People | ✅ | ✅ |
| SUPPORT_TICKETS | `data/raw/support_tickets.jsonl` | JSONL | 1,500 | People | ✅ | ✅ |

## Validation Checklist

- [x] Every file is readable and non-empty.
- [x] Every file parses in its format (CSV or JSON Lines).
- [x] Record counts match what the generator reported (4,000 / 4,500 / 1,500; 10,000 in all).
- [x] Every record has a unique, non-blank key (`contact_id`, `order_id`, `ticket_id`).
- [x] Sources are registered in `config/data_sources.yaml`.
- [x] Locations are documented in `docs/data_source_locations.md`.
- [x] `data/raw/*` is excluded from version control.
- [x] License capacity for ~10,000 records settled (custom license applied; measured: no record cap).
- [x] Ready for Data Quality, Mapping, and Transformation.
