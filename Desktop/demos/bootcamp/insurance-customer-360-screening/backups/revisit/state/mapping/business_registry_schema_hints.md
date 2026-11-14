# Schema hints

## business_registry (data/raw/business_registry.jsonl)
- One JSON record per line; already in Senzing record shape.
- Root: DATA_SOURCE (BUSINESS_REGISTRY), RECORD_ID, FEATURES, REGISTRY_ROLE, REGISTRY_STATUS, INCORPORATED (year, ORGs only).
- FEATURES.RECORD_TYPE on every record: PERSON 16, ORGANIZATION 6. ORGs carry NAME_ORG (+ AKA NAME_ORG); persons carry
  NAME_FULL or NAME_FIRST/NAME_LAST. No record has both kinds.
- REL network, domain REFERENCE: anchors on 2012 (key 2011), 2041, 2071, 2074; 14 pointers from people/subsidiaries
  (roles Owns NN%, President, Principal, Global Parent).
- Persons may carry EMPLOYER (organization name as text).
- Mapper language: Java. Source: data/raw/business_registry.jsonl
