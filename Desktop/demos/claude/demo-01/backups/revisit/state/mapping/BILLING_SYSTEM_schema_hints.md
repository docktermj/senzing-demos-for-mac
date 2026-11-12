# Schema hints — BILLING_SYSTEM

- Mapper language: Python. Source: data/raw/billing_system-regenerated.csv (current registry file).
- Schema billing_system: flat CSV, header row, 10 columns, 4,000 rows; one row per billing account.
- billing_id: unique natural key (BIL-NNNNNN).
- first_name / last_name: parsed person name parts (keep together in one NAME object).
- street / city / state / zip: parsed address parts (keep together in one ADDRESS object); ~18% of rows have none.
- phone: 999-999-9999; ~36% empty.
- plan: 5 categories. last_invoice_date: ISO dates.
- Some customers have a second billing account (separate row, own billing_id).
