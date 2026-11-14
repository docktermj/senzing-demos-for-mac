# Schema hints: BUSINESS_REGISTRY

- Source: data/raw/business_registry.jsonl; mapper language Java.
- Already Senzing JSON: root DATA_SOURCE, RECORD_ID (unique), FEATURES list.
- FEATURES.RECORD_TYPE discriminates: ORGANIZATION 6 (NAME_ORG, AKA NAME_ORG with NAME_TYPE), PERSON 16 (NAME_FULL, or NAME_FIRST/LAST on 2).
- Disclosed relationships inside FEATURES, domain REFERENCE: anchors on 2012 (key 2011), 2041, 2071, 2074; 14 pointers with roles (Owns N%, President, Principal, Global Parent).
- Addresses: parsed parts or ADDR_FULL (+ADDR_COUNTRY), with ADDR_TYPE.
- Identifiers: TAX_ID (EIN/VAT/GSTIN), NATIONAL_ID (UEN, SG), LEI_NUMBER.
- Root: REGISTRY_ROLE, REGISTRY_STATUS (all); INCORPORATED (year, 6 ORGANIZATION records) = only structure variation.
