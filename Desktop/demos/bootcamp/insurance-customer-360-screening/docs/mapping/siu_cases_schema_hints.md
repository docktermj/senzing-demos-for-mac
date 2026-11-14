# Schema hints

## siu_cases (data/raw/siu_cases.jsonl)
- One JSON record per line; already in Senzing record shape.
- Root: DATA_SOURCE (SIU_CASES), RECORD_ID, FEATURES, plus case fields SIU_CASE_NUMBER, REFERRAL_DATE, FRAUD_TYPE,
  ROLE_IN_CASE, LINE_OF_BUSINESS, CASE_DISPOSITION (all 100%).
- FEATURES.RECORD_TYPE PERSON on all 13; parsed person names; no NAME_ORG; no REL features.
- 1021 has no name (address, email, SSN only).
- Mapper language: Java. Source: data/raw/siu_cases.jsonl
