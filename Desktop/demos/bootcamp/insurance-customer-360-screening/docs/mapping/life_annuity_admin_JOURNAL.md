# Mapping journal

## LIFE_ANNUITY_ADMIN — step 1 (profile)
- Mode: interactive (bootcamp concise presentation); mapper language: Java
- life_annuity_admin_extract: 75 records, 47 fields (35 inside FEATURES incl. RECORD_TYPE, 10 root contract fields, DATA_SOURCE, RECORD_ID)
- Workflow resources reused from src/scripts/mapping and docs/reference; all hashes match the server's.

## LIFE_ANNUITY_ADMIN — step 2 (plan)
- One master schema, DATA_SOURCE LIFE_ANNUITY_ADMIN, RECORD_ID from RECORD_ID; type varies per record → type discriminator.

## LIFE_ANNUITY_ADMIN — step 3 (map)
- Same shape as PC_POLICY_ADMIN: 28 FEATURES attributes as-is, plus 5 name fields under a type_discriminator on
  FEATURES.RECORD_TYPE. 10 root contract fields → payload; DATA_SOURCE, RECORD_ID, FEATURES container → ignore.
  34 + 10 + 3 = 47.
- Untyped 2215 (only the generic info@ca-state.gov email, no name), 3022 (person names), 3024 (NAME_ORG only):
  no RECORD_TYPE emitted, names kept as stated. No record states both name kinds.
- CONTRACT_NUMBER payload (0.85); ACCOUNT_VALUE is a money amount, payload.
- Cross-source check vs PC_POLICY_ADMIN: both sources carry the same Senzing attribute names for the same
  quantities, so there are no differing-field pairs to ask about.
- REL: 1313 → CUSTOMERS 1312 SUBSIDIARY_OF points to an anchor in PC_POLICY_ADMIN (cross-source); 3065 → 3063 Spouse.

## LIFE_ANNUITY_ADMIN — step 4 (generate/validate)
- Transformation logic moved to shared src/transform/FeatureListTransformer.java; per-source settings in
  src/transform/transform_life_annuity_admin.java. PC_POLICY_ADMIN output re-checked: same SHA-256.
- Phase A sample (7 records: person, org + REL pointer, untyped no-name 2215, untyped person 3022, untyped org
  3024, anchor 3063, Spouse pointer 3065): analyzer exit 0.
- Full run 75 read / 75 written; 2 label-only PHONE_TYPE objects skipped (1098, 1100). Analyzer exit 0; verbatim
  pass; routing (with --payload-fields): nothing dropped.
- TAX_ID_TYPE "custom/unrecognized": false positive, checked against search_docs (Feature: TAX_ID) and the
  installed g2config.json template.
- QA verdict approve: 75 = 75; all 14 families present; populations match profile (DOB 43, SSN 10, PASSPORT 9);
  NAME 74/75 because 2215 has no name in the source. Quality 62.6 (completeness 53.3, format 81.3).
- Step 5: skip (sources remain; real load in Data processing). Load-ready data/senzing-ready/life_annuity_admin.jsonl
  (75, identical to QA run). Docs in docs/mapping/.
