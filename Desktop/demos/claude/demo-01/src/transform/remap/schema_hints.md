# Schema hints (remap run, 2026-11-10)
Mapper language: Python. Sources (relative to project root):
- data/raw/crm_system.csv
- data/raw/billing_system-regenerated.csv
- data/raw/support_tickets.csv

## crm_system (flat CSV, 1 row = 1 person)
- key crm_id; full_name joined (one field); address free-text "street, city, ST zip"
- phone, email optional; created_date, campaign non-identifying

## billing_system-regenerated (flat CSV, 1 row = 1 person)
- key billing_id; first_name/last_name split; some rows have only first_name
- address parsed: street/city/state/zip (~18% blank); phone mixed formats (~36% blank)
- plan, last_invoice_date non-identifying; no email

## support_tickets (flat CSV, 1 row = 1 ticket)
- key ticket_id; name joined, typos/casing; email
- subject, opened_date non-identifying; repeat tickets per customer = separate records
- No join keys between schemas; no relationship tables.
