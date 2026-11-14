# Schema hints: core_cif_extract (CORE_BANKING)
- File: data/raw/core_cif_extract.jsonl (JSONL, one record per line). Mapper language: Java.
- Already Senzing JSON: root DATA_SOURCE (CORE_BANKING), RECORD_ID (unique), FEATURES list.
- FEATURES: list of feature objects; each object holds one feature's attributes (NAME_*, DATE_OF_BIRTH, ADDR_*, PHONE_*, EMAIL_ADDRESS, SSN_NUMBER, PASSPORT_*, DRIVERS_LICENSE_*, NATIONAL_ID_*, TAX_ID_*, GENDER, REL_ANCHOR_*, REL_POINTER_*, RECORD_TYPE).
- RECORD_TYPE is per record inside FEATURES (PERSON 86, ORGANIZATION 8, absent on 3).
- Root business columns (account facts, not identity): ACCOUNT_NUMBER, PRODUCT_TYPE, OWNERSHIP_TYPE, ACCOUNT_STATUS, OPEN_DATE, CURRENT_BALANCE, BRANCH, RELATIONSHIP_TIER.
