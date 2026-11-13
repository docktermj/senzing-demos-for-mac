# VENDOR_REGISTRY Structural Hints

- Mapper language: Java; input: `data/raw/VENDOR_REGISTRY-improved.jsonl`.
- One JSONL schema (22 records, 36 fields); each root record has a `FEATURES` array of single-purpose feature objects and stable `RECORD_ID`.
- `FEATURES.RECORD_TYPE` discriminates PERSON (16) and ORGANIZATION (6). Root `DATE` is present in only one of two field patterns; this is optional-field variation, not a separate entity schema.
- Feature objects contain names, addresses, identifiers, contact values, employer names, and relationship anchor/pointer fields. Check repeated employer names against organization-name records; check relationship keys before mapping.
