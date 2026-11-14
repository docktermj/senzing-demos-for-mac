# Schema hints: WATCHLIST

- Source: data/raw/screening_list-improved.jsonl (DOB-normalized copy of screening_list.jsonl); mapper language Java.
- Already Senzing JSON: root DATA_SOURCE (WATCHLIST), RECORD_ID (unique), FEATURES list of feature objects.
- FEATURES.RECORD_TYPE = PERSON on all 17; no discriminator needed.
- Names: components (NAME_FIRST/LAST/MIDDLE) on 12, NAME_FULL on 4 (Arabic, Cyrillic, "Last, First" Latin); record 1021 has no name.
- Address: ADDR_LINE1 often holds a whole unparsed address; ADDR_TYPE qualifies its object.
- Payload at root: LIST_TYPE, LIST_STATUS, LISTED_DATE (mixed M/D/YY date formats).
