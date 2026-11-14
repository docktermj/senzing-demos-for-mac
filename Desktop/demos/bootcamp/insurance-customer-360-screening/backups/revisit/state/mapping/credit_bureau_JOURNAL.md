# Mapping journal

## CREDIT_BUREAU — step 1 (profile)
- Mode: interactive (bootcamp concise presentation); mapper language: Java
- credit_header: 30 records, 22 fields (18 inside FEATURES incl. RECORD_TYPE, FILE_SINCE, FILE_STATUS, DATA_SOURCE, RECORD_ID)
- Workflow resources reused from src/scripts/mapping and docs/reference; hashes match the server's.
- Notable: SSN 677-42-3131 is on two records (3009, 3010); kept verbatim, Senzing decides.

## CREDIT_BUREAU — steps 2-3 (plan, map)
- One master schema, DATA_SOURCE CREDIT_BUREAU, RECORD_ID from RECORD_ID, all PERSON.
- RECORD_TYPE: derived from FEATURES.RECORD_TYPE (PERSON on all 30); no type discriminator needed, no NAME_ORG.
- 16 FEATURES attributes as-is (names incl. NAME_TYPE PRIMARY/AKA, DOB, SSN, parsed address, phone, gender, REL_ANCHOR);
  FILE_SINCE, FILE_STATUS → payload; DATA_SOURCE, RECORD_ID, FEATURES container, FEATURES.RECORD_TYPE → ignore. 16+2+4 = 22.
- Cross-source check vs PC_POLICY_ADMIN and LIFE_ANNUITY_ADMIN: same attribute names for the same quantities; no pairs to ask.

## CREDIT_BUREAU — step 4 (generate/validate)
- Transformer src/transform/transform_credit_bureau.java (shared FeatureListTransformer). 30 read / 30 written, nothing dropped.
- Analyzer: one structural error, anchor with no pointer in this file. Intentional: the pointer is on PC_POLICY_ADMIN 3050.
  Re-run with --rel-explanation → exit 0 (Phase A sample and full output).
- Verbatim pass; routing (with --payload-fields): nothing dropped.
- QA verdict approve: 30 = 30; 7 families present; DOB 100%, SSN 80%, PHONE 77%, GENDER 67% match the profile.
  Quality 89.8 (completeness 87.2, format 94.9).
- Step 5: skip. Load-ready data/senzing-ready/credit_bureau.jsonl (30), byte-identical to the source file.
