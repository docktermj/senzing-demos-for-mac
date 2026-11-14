# Schema hints: CREDIT_BUREAU

- Source: data/raw/credit_header.jsonl; mapper language Java.
- Already Senzing JSON: root DATA_SOURCE (CREDIT_BUREAU), RECORD_ID (unique), FEATURES list.
- FEATURES.RECORD_TYPE = PERSON on all 30; no discriminator.
- Names: parsed parts; record 2171 has two name objects (NAME_TYPE PRIMARY and AKA).
- Addresses parsed (LINE1/CITY/STATE/POSTAL) with ADDR_TYPE; 2171 has HOME + MAILING.
- PHONE_TYPE travels with PHONE_NUMBER in one object.
- DATE_OF_BIRTH in M/D/YYYY.
- Record 3048 carries REL_ANCHOR (domain REFERENCE, key 3048); CORE_BANKING 3050 points to it (role Carer).
- Payload at root: FILE_STATUS (all), FILE_SINCE (11 records, YYYY-MM). Structure variation is only FILE_SINCE being optional.
