# Mapping journal: CARD_SERVICES
- Mode: interactive (bootcamp concise mapping verbosity). Mapper language: Java.
- card_cardholder_master: 82 records, 47 fields (profiler). Already Senzing JSON (FEATURES list).
- Step 2: master card_cardholder_master -> CARD_SERVICES, RECORD_TYPE per record (type_discriminator on FEATURES.RECORD_TYPE), RECORD_ID natural key.
- Step 3: 35 feature (incl. NPI_NUMBER), 9 payload (card facts; no reserved-name collision), 3 ignored.
- Step 4: mapper src/transform/transform_card_services.java (shared SenzingJsonPassThrough). Output data/mapping/card_services_quality.jsonl, 82 records. Analyzer clean, verbatim passes. 2 qualifier-only PHONE_TYPE objects dropped. Verdict: approve.
