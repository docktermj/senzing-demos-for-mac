# Schema hints

## life_annuity_admin_extract (data/raw/life_annuity_admin_extract.jsonl)
- One JSON record per line; already in Senzing record shape.
- Root: DATA_SOURCE (LIFE_ANNUITY_ADMIN), RECORD_ID (unique), FEATURES (list), plus contract fields.
- FEATURES: list of feature objects, one feature's attributes per object.
- Record type: FEATURES[].RECORD_TYPE (PERSON 67, ORGANIZATION 5, absent on 3: 2215, 3022, 3024).
- Org records carry NAME_ORG; person records carry NAME_FIRST/NAME_LAST or NAME_FULL. No record has both kinds.
- Root business fields: CONTRACT_NUMBER, PRODUCT_TYPE, PARTY_ROLE, ISSUE_DATE, CONTRACT_STATUS, FACE_AMOUNT,
  MODAL_PREMIUM, PREMIUM_MODE, ACCOUNT_VALUE (annuities), CUSTOMER_TIER.
- REL: 1313 → CUSTOMERS 1312 (anchored in PC_POLICY_ADMIN) SUBSIDIARY_OF; 3065 → 3063 Spouse.
- Mapper language: Java. Source: data/raw/life_annuity_admin_extract.jsonl
