# Schema hints — SUPPORT_TICKETS

- Mapper language: Python. Source: data/raw/support_tickets.csv
- Schema support_tickets: flat CSV, header row, 5 columns, 2,000 rows; one row per ticket.
- ticket_id: unique natural key (TKT-NNNNNN).
- name: single name field; casing varies (Title/UPPER/lower), occasional surname typos; ~2.5% empty.
- email: lowercase, occasional typo; ~8.6% empty.
- subject: 15 free-text ticket categories. opened_date: ISO, ~3% MM/DD/YYYY.
- Repeat tickets from the same person are separate rows with their own ticket_id.
