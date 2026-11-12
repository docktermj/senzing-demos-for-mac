# SUPPORT_TICKETS Mapping

- **Program:** `src/transform/transform_support_tickets.py`
- **Run:** `python3 src/transform/transform_support_tickets.py data/raw/support_tickets.csv data/senzing-ready/support_tickets.jsonl` (add `--sample N` to test on the first N rows)
- **Input:** CSV with header: ticket_id, name, email, subject, opened_date
- **Output:** Senzing JSON, one record per line: `DATA_SOURCE`, `RECORD_ID`, `FEATURES` (RECORD_TYPE, NAME_FULL, EMAIL_ADDRESS) and root payload `subject`
- **Logic:** values are routed verbatim; empty values are omitted; a row without `ticket_id` is rejected and reported on stderr.
- **Quality:** 95.7; 2,000 records in, 2,000 out, 0 rejected.
- **Field mappings:** see `docs/mapping/SUPPORT_TICKETS_mapper.md`.
