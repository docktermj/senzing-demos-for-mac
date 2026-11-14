# Schema hints

## credit_header (data/raw/credit_header.jsonl)
- One JSON record per line; already in Senzing record shape.
- Root: DATA_SOURCE (CREDIT_BUREAU), RECORD_ID (unique), FEATURES (list), FILE_SINCE (YYYY-MM, 37%), FILE_STATUS.
- FEATURES: one feature per object. RECORD_TYPE PERSON on all 30 records; no NAME_ORG anywhere.
- Names: parsed NAME_FIRST/LAST/MIDDLE; two records carry PRIMARY + AKA names (NAME_TYPE in the same object).
- Addresses parsed (ADDR_LINE1/CITY/STATE/POSTAL_CODE) with ADDR_TYPE HOME/MAILING.
- REL_ANCHOR REFERENCE/3048 on record 3048 — target of PC_POLICY_ADMIN 3050's pointer (Carer).
- Mapper language: Java. Source: data/raw/credit_header.jsonl
