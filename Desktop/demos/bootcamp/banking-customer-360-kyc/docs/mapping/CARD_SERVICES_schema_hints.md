# Schema hints: card_cardholder_master (CARD_SERVICES)
- File: data/raw/card_cardholder_master.jsonl (JSONL). Mapper language: Java.
- Already Senzing JSON: root DATA_SOURCE (CARD_SERVICES), RECORD_ID (unique), FEATURES list of one-feature objects.
- RECORD_TYPE per record inside FEATURES (PERSON 67, ORGANIZATION 12, absent on 3: 2215, 3022, 3024).
- Root business columns (card facts): CARD_ACCOUNT_ID, PAN_LAST4, CARDHOLDER_ROLE, PRODUCT, CREDIT_LIMIT, CURRENT_BALANCE, OPEN_DATE, ACCOUNT_STATUS, DELINQUENCY_BUCKET.
