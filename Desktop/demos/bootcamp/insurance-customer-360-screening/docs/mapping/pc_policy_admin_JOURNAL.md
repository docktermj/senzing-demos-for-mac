# Mapping journal

## PC_POLICY_ADMIN — step 1 (profile)
- Mode: interactive (bootcamp concise presentation); mapper language: Java
- pc_policy_admin_extract: 104 records, 48 fields (38 inside FEATURES, 10 root business fields)

## PC_POLICY_ADMIN — step 2 (plan)
- One master schema, DATA_SOURCE PC_POLICY_ADMIN, RECORD_ID from RECORD_ID; no children, no embedded masters.
- Entity type varies per record (PERSON 86, ORGANIZATION 15, untyped 3), so step 3 uses a type discriminator.

## PC_POLICY_ADMIN — step 3 (map)
- FEATURES.* attributes already carry Senzing attribute keys: 29 mapped as-is, plus the 5 name fields under a
  type_discriminator on FEATURES.RECORD_TYPE (PERSON → NAME_FIRST/LAST/MIDDLE/FULL, ORGANIZATION → NAME_ORG).
- Decision (0.75): untyped records 3021/3023/3025 keep the attribute the source states and emit no RECORD_TYPE
  (3025 carries both NAME_ORG and NAME_FULL). Alternative rejected: forcing PERSON.
- Decision (0.85): POLICY_NUMBER is payload, not ACCOUNT: unique per record and in no other source.
- 10 root policy fields → payload; DATA_SOURCE, RECORD_ID, FEATURES container → ignore. 35 + 10 + 3 = 48.
- Caveats carried forward: REL domains CUSTOMERS/REFERENCE kept as stated (1311 → 1310 PARENT_OF is in-source;
  3050 → REFERENCE 3048 points outside this project); shared info@ca-state.gov on 9 records; one Name<addr> email.

## PC_POLICY_ADMIN — step 4 (generate/validate), in progress
- Phase A sample (6 records, one per code path): data/mapping/pc_policy_admin_sample.jsonl.
- Transformer: src/transform/transform_pc_policy_admin.java (reuses src/server/Json.java). Full run: 104 read, 104 written.
- Fixed in code: 2 label-only {"PHONE_TYPE": "MOBILE"} objects (1097, 1099) are skipped; they qualify no phone.
- Open: analyzer structural error on 3025 (source states NAME_ORG "PRAVENVGUPTA" + NAME_FULL "WONDERLAND", no
  RECORD_TYPE). The analyzer says the bootcamper must decide the record's kind; asked.
- Verbatim check: pass. Routing report (with --payload-fields): no dropped source values.
- Analyzer G8 warning (9 ORGANIZATION names "look PERSON", e.g. "Worldwide Exports") read as detector false
  positives: the source states ORGANIZATION and the names are business names. No change.
- 3025 resolved: bootcamper chose ORGANIZATION; transformer emits both names as NAME_ORG, values unchanged.
- Final step-4 run: 104 read / 104 written; analyzer exit 0; verbatim pass; routing: nothing dropped.
- QA verdict: approve. records_output 104, output data/mapping/pc_policy_admin_quality.jsonl.
  Checks: count 104 = 104; all 15 mapped families present; populations match the profile (DOB 61, SSN 28,
  PASSPORT 11, DRLIC 6); no payload field should be a feature; RECORD_TYPE PERSON 86 / ORGANIZATION 16 / none 2.
- Quality of the mapped output (Phase 1 formula): 66.8 (completeness 56.8, format 88.3, duplicates 0%).
- Step 5 (detect_environment): skip. Sandbox test load not needed; sources remain and the real load is in Data processing.
- Load-ready: data/senzing-ready/pc_policy_admin.jsonl (104 records, identical to the QA run). Docs: docs/mapping/
  pc_policy_admin_mapper.md, mapping_pc_policy_admin.md, transformation_lineage_pc_policy_admin.md.
- Correction (during CREDIT_BUREAU profiling): 3050 → REFERENCE 3048 points to the anchor on CREDIT_BUREAU record 3048, not outside the project.
