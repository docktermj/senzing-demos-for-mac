# Security and Compliance Notes

## Data in this project

- All three sources (`CRM_SYSTEM`, `BILLING_SYSTEM`, `SUPPORT_TICKETS`) are **synthetic**. Names, emails, phone numbers and addresses are invented; phone numbers use the fictional 555-01XX range.
- When you bring real customer data later, treat it as PII: it may include names, addresses, phone numbers and email addresses.

## Handling rules applied

- `.gitignore` excludes `data/raw/*`, `data/samples/*`, `data/ground_truth/*`, `data/temp/*`, the SQLite database, `config/engine_config*.json`, `config/license.json`, `licenses/` and `.env`.
- Nothing from the data files is sent to the Senzing MCP server: it receives questions and code requests, not records.
- Anonymize or generate stand-in data for any testing that leaves your machine.

## For production

- Decide retention and access controls for customer records before moving beyond the bootcamp (see the graduation migration checklist).
