# Schema hints

## pc_policy_admin_extract (data/raw/pc_policy_admin_extract.jsonl)
- One JSON record per line; already in Senzing record shape.
- Root: DATA_SOURCE (PC_POLICY_ADMIN), RECORD_ID (unique), FEATURES (list), plus policy fields.
- FEATURES: list of feature objects; each object holds one feature's attributes
  (e.g. {NAME_FIRST, NAME_LAST}, {ADDR_TYPE, ADDR_LINE1, ...}, {SSN_NUMBER}).
- Record type: FEATURES[].RECORD_TYPE (PERSON 86, ORGANIZATION 15, absent on 3).
- Org records carry NAME_ORG; person records carry NAME_FIRST/NAME_LAST or NAME_FULL.
- Root business fields: POLICY_NUMBER, LINE_OF_BUSINESS, INSURED_ROLE, WRITTEN_PREMIUM,
  EFFECTIVE_DATE, EXPIRATION_DATE, POLICY_STATUS, PRODUCER_CODE, CANCELLATION_REASON, CUSTOMER_TIER.
- Mapper language: Java. Source: data/raw/pc_policy_admin_extract.jsonl
