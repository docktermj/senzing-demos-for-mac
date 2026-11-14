# Mapping journal

## SIU_CASES — step 1 (profile)
- Mode: interactive (bootcamp concise presentation); mapper language: Java
- siu_cases: 13 records, 24 fields (15 inside FEATURES incl. RECORD_TYPE, 6 case fields, DATA_SOURCE, RECORD_ID, FEATURES)
- Workflow resources reused; hashes match the server's.

## SIU_CASES — steps 2-3 (plan, map)
- One master, DATA_SOURCE SIU_CASES, RECORD_ID from RECORD_ID, all PERSON; RECORD_TYPE derived from FEATURES.RECORD_TYPE.
- 14 FEATURES attributes as-is; 6 case fields → payload (SIU_CASE_NUMBER 0.9: unique, in no other source);
  DATA_SOURCE, RECORD_ID, FEATURES container, FEATURES.RECORD_TYPE → ignore. 14+6+4 = 24.
- Cross-source check: same attribute names and quantities as earlier sources; no pairs to ask.

## SIU_CASES — step 4 (generate/validate)
- Transformer src/transform/transform_siu_cases.java (shared FeatureListTransformer): 13 read / 13 written, nothing dropped.
- Phase A sample (4: full person + DRLIC, SSN, nameless 1021, gender) and full output: analyzer exit 0, no structural errors.
- Verbatim pass; routing (with --payload-fields): nothing dropped. Output is byte-identical to the source file.
- QA verdict approve: 13 = 13; 7 families present; NAME 12/13 because 1021 has no name in the source. Quality 62.1.
- Step 5: skip. Load-ready data/senzing-ready/siu_cases.jsonl (13), identical to source. Docs in docs/mapping/.
