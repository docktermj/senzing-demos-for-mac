# Mapping journal: CORE_BANKING
- Mode: interactive (bootcamp concise mapping verbosity). Mapper language: Java.
- core_cif_extract: 97 records, 45 fields (profiler). Source is already Senzing JSON (FEATURES list).
- Step 2: master core_cif_extract -> CORE_BANKING, RECORD_TYPE per record (type_discriminator on FEATURES.RECORD_TYPE), RECORD_ID natural key.
- Step 3: 34 feature, 8 payload, 3 ignored. ACCOUNT_NUMBER kept as payload, emitted as DEPOSIT_ACCOUNT_ID (a root key ending in a reserved attribute is read as a feature).
- Step 4: mapper src/transform/transform_core_banking.java (shared SenzingJsonPassThrough). Output data/mapping/core_banking_quality.jsonl, 97 records. Analyzer clean, verbatim check passes. Qualifier-only PHONE_TYPE objects dropped (2). Record 3025 re-typed ORGANIZATION per bootcamper (names swapped). Verdict: approve.
