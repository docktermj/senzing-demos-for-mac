# Schema hints

## sanctions_pep (data/raw/sanctions_pep.jsonl)
- One JSON record per line; already in Senzing record shape. 4 records.
- Root: DATA_SOURCE (SANCTIONS_PEP), RECORD_ID, FEATURES, LIST_TYPE (Sanctions/PEP), LIST_NAME, LISTED_DATE,
  SANCTIONS_PROGRAM (sanctions only), PEP_POSITION, PEP_COUNTRY (PEP only).
- FEATURES.RECORD_TYPE PERSON on all 4; NAME_FULL only (Arabic, Cyrillic, and "Last, First" Latin forms).
- Addresses parsed (LINE1/CITY/STATE/COUNTRY); PHONE_NUMBER without type; EMPLOYER on the two PEPs.
- Mapper language: Java. Source: data/raw/sanctions_pep.jsonl
