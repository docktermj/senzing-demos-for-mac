# Mapping journal: CREDIT_BUREAU

- Mode: interactive (concise). Mapper language: Java (shared SenzingJsonPassThrough).
- Schema credit_header: 30 records, 22 fields. Structure variation = optional FILE_SINCE only, not a separate record type.
- Plan: credit_header = master, CREDIT_BUREAU, PERSON, RECORD_ID; no support schemas (0.95).
- Mapping: 17 feature / 2 payload / 3 ignored, all >= 0.90; source REL_ANCHOR on 3048 kept (CORE_BANKING 3050 points to it); DOB M/D/YYYY kept verbatim.
- Step 4: src/transform/transform_credit_bureau.java -> data/mapping/credit_bureau_quality.jsonl, 30 records. Verbatim passes. Analyzer's one structural error (anchor with no pointer in this file) accepted via --rel-explanation: the pointer is CORE_BANKING 3050 (REFERENCE/3048, Carer). Verdict approve.
- Note: SSN 677-42-3131 appears on two different people (3009 Natalie Brandt, 3010 Oscar Delgado); kept verbatim.
