# Mapping journal

## BUSINESS_REGISTRY — step 1 (profile)
- Mode: interactive (bootcamp concise presentation); mapper language: Java
- business_registry: 22 records, 36 fields (30 inside FEATURES incl. RECORD_TYPE, 3 root business fields, DATA_SOURCE, RECORD_ID, FEATURES)
- Workflow resources reused; hashes match the server's.

## BUSINESS_REGISTRY — steps 2-3 (plan, map)
- One master, DATA_SOURCE BUSINESS_REGISTRY, RECORD_ID from RECORD_ID; type discriminator on FEATURES.RECORD_TYPE
  (PERSON → NAME_FULL/FIRST/LAST, ORGANIZATION → NAME_ORG). No record has both kinds.
- 25 FEATURES attributes as-is (address, NATIONAL_ID UEN, TAX_ID EIN/VAT/GSTIN, LEI, DOB, phone, email, EMPLOYER,
  NAME_TYPE AKA, REL anchor/pointer) + root INCORPORATED → REGISTRATION_DATE (decision 0.9; orgs only).
- REGISTRY_ROLE, REGISTRY_STATUS → payload. 31 + 2 + 3 = 36.
- Cross-source check: REGISTRATION_DATE, EMPLOYER and LEI_NUMBER are new (no other source maps them); the other
  features share attribute names and quantities with earlier sources. No pairs to ask.

## BUSINESS_REGISTRY — step 4 (generate/validate)
- Shared FeatureListTransformer gained a root-field → feature option (INCORPORATED → REGISTRATION_DATE). The three earlier
  sources were re-run: outputs byte-identical to their load-ready files.
- Transformer src/transform/transform_business_registry.java: 22 read / 22 written, nothing dropped.
- Phase A sample (7: org with anchor, owner pointers, org with AKA, parent/subsidiary, parsed-name person) and full output:
  analyzer exit 0. Verbatim pass. Routing: nothing dropped.
- Notices: TAX_ID_TYPE "custom" (false positive, as before); EMPLOYER uniqueness 33% (9 people, 3 employers: expected).
- QA verdict approve: 22 = 22; 13 families present incl. REGISTRATION_DATE 6/22 (= the 6 orgs). Quality 67.8.
- Step 5: skip. Load-ready data/senzing-ready/business_registry.jsonl (22). Docs in docs/mapping/.
