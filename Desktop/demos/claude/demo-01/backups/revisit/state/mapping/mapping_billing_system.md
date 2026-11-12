# BILLING_SYSTEM Mapping

- **Program:** `src/transform/transform_billing_system.py`
- **Run:** `python3 src/transform/transform_billing_system.py data/raw/billing_system-regenerated.csv data/senzing-ready/billing_system.jsonl` (add `--sample N` to test on the first N rows)
- **Input:** CSV with header: billing_id, first_name, last_name, street, city, state, zip, phone, plan, last_invoice_date
- **Output:** Senzing JSON, one record per line: `DATA_SOURCE`, `RECORD_ID`, `FEATURES` (RECORD_TYPE; NAME_FIRST+NAME_LAST; ADDR_LINE1+ADDR_CITY+ADDR_STATE+ADDR_POSTAL_CODE; PHONE_NUMBER) and root payload `plan`
- **Logic:** values are routed verbatim; a feature's parts share one object; empty parts are omitted; a row without `billing_id` is rejected and reported on stderr.
- **Quality:** 90.2; 4,000 records in, 4,000 out, 0 rejected.
- **Field mappings:** see `docs/mapping/BILLING_SYSTEM_mapper.md`.
