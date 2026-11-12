# Transformation Lineage: CRM_SYSTEM

| Item | Value |
|---|---|
| Source file | `data/raw/crm_system.csv` (517,810 bytes, 4,000 records) |
| Transformation program | `src/transform/transform_crm_system.py` |
| Output file | `data/senzing-ready/crm_system.jsonl` (1,162,558 bytes, 4,000 records) |
| Records in / out / rejected | 4,000 / 4,000 / 0 |
| Filters | none |
| Format changes | none: every value is emitted verbatim |
| Fields dropped | `created_date` (Bootcamper's decision) |
| Quality | 94.8 before mapping; analyzer clean after |

Field mappings: `docs/mapping/CRM_SYSTEM_mapper.md`. Sample output: `data/mapping/crm_system_sample.jsonl`.
