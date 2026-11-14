# Mapping journal

## SANCTIONS_PEP — step 1 (profile)
- Mode: interactive (bootcamp concise presentation); mapper language: Java
- sanctions_pep: 4 records, 18 fields (9 inside FEATURES incl. RECORD_TYPE, 6 list fields, DATA_SOURCE, RECORD_ID, FEATURES)
- Non-Latin names (Arabic 2052, Cyrillic 2062): search_docs(category='globalization') confirms culture-aware comparison for
  Arabic/Pakistani and East Slavic/Ukrainian names (shared-script culture identification new in v4.4; installed 4.4.4).
  Names kept verbatim; cross-script matching is checked after load.

## SANCTIONS_PEP — steps 2-3 (plan, map)
- One master, DATA_SOURCE SANCTIONS_PEP, RECORD_ID from RECORD_ID, all PERSON; RECORD_TYPE derived from FEATURES.RECORD_TYPE.
- 8 FEATURES attributes as-is (NAME_FULL, parsed address, phone, EMPLOYER); 6 list fields → payload; 4 ignored. 8+6+4 = 18.
- Decision (0.85): PEP_COUNTRY payload, not NATIONALITY/CITIZENSHIP (country of office, not stated as the person's).
- Cross-source check: EMPLOYER also in BUSINESS_REGISTRY under the same attribute and meaning; no differing-field pairs.

## SANCTIONS_PEP — step 4 (generate/validate)
- Transformer src/transform/transform_sanctions_pep.java (shared FeatureListTransformer): 4 read / 4 written, nothing dropped.
- Phase A sample = all 4 records (each a distinct code path: Arabic name + country, Cyrillic name, PEP with state, PEP name-only).
- Analyzer exit 0; verbatim pass; routing: nothing dropped. Output equals the source when parsed (bytes differ only because the
  source writes non-Latin characters as \u escapes and the output writes UTF-8).
- Notice: EMPLOYER uniqueness 50% (both PEPs name Universal Exports): expected.
- QA verdict approve: 4 = 4; 5 families present. Quality 71.9 (completeness 68.8, format 75.0).
- Step 5: skip (bootcamper chose to proceed to loading; the real load is Data processing). Load-ready data/senzing-ready/sanctions_pep.jsonl (4).
