# Mapping journal (remap run)
## Step 1 — 2026-11-10
- Mode: interactive. Mapper language: Python.
- crm_system: 4,000 records, 7 fields
- billing_system-regenerated: 4,000 records, 10 fields
- support_tickets: 2,000 records, 5 fields
## Step 2 — plan
- crm_system: master, CRM_SYSTEM, PERSON, RECORD_ID=crm_id (0.97)
- billing_system-regenerated: master, BILLING_SYSTEM, PERSON, RECORD_ID=billing_id (0.97)
- support_tickets: master, SUPPORT_TICKETS, PERSON, RECORD_ID=ticket_id (0.95)
- No lookups/children/relationships; no type discriminator field. No open questions.
## Step 3 — map
- CRM: full_name NAME_FULL, address ADDR_FULL, phone, email; campaign payload; created_date ignored (Bootcamper decisions).
- BILLING: first/last in one NAME object; street/city/state/zip one ADDRESS object; phone; plan payload; last_invoice_date ignored.
- SUPPORT: name NAME_FULL, email; subject payload; opened_date ignored.
- Caveat: ~3% Billing rows have first_name only (no last) — kept as emitted; source of the "Brad" noise links.
## Step 4 — generate & validate
- Mapper: customer360_mapper.py (Python). Outputs: crm_system/billing_system/support_tickets_output.jsonl; combined customer360_output.jsonl (10,000 records).
- Analyzer exit 0 for all three, no critical errors/warnings; verbatim check passed for all three.
- Routing: only dropped values are RECORD_ID sources, payload columns and the ignored date columns (by decision).
- Output is feature-identical to data/senzing-ready/*.jsonl (0 differing records).
- QA verdict: approve.
