# Mapping journal: WATCHLIST

- Mode: interactive (concise). Mapper language: Java (shared SenzingJsonPassThrough).
- Schema screening_list-improved: 17 records, 25 fields.
- Plan: screening_list-improved = master, WATCHLIST, PERSON, RECORD_ID; no support schemas (confidence 0.95).
- Mapping: 19 feature / 3 payload / 3 ignored, all >= 0.85 confidence; RECORD_TYPE derived from FEATURES.RECORD_TYPE.
- ADDR_LINE1 holding whole addresses kept as-is (Senzing quickstart shows the same form); NAME_FULL kept unparsed, incl. Arabic and Cyrillic (Senzing is UTF-8 with cross-script name comparison).
- Step 4: src/transform/transform_watchlist.java -> data/mapping/watchlist_quality.jsonl, 17 records. Analyzer clean (warnings only: sparse DRLIC/SSN/PHONE/EMPLOYER); verbatim passes; routing: only payload keys absent from features. Verdict approve.
