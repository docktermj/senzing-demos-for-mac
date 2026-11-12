# CRM_SYSTEM Mapping

- **Program:** `src/transform/transform_crm_system.py`
- **Run:** `python3 src/transform/transform_crm_system.py data/raw/crm_system.csv data/senzing-ready/crm_system.jsonl` (add `--sample N` to test on the first N rows)
- **Input:** CSV with header: crm_id, full_name, address, phone, email, created_date, campaign
- **Output:** Senzing JSON, one record per line: `DATA_SOURCE`, `RECORD_ID`, `FEATURES` (RECORD_TYPE, NAME_FULL, ADDR_FULL, PHONE_NUMBER, EMAIL_ADDRESS) and root payload `campaign`
- **Logic:** values are routed verbatim; empty values are omitted; a row without `crm_id` is rejected and reported on stderr.
- **Quality:** 94.8; 4,000 records in, 4,000 out, 0 rejected.
- **Field mappings:** see `docs/mapping/CRM_SYSTEM_mapper.md`.
