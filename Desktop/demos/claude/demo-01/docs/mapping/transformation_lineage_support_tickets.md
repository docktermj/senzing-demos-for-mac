# Transformation Lineage: SUPPORT_TICKETS

| Item | Value |
|---|---|
| Source file | `data/raw/support_tickets.csv` (169,210 bytes, 2,000 records) |
| Transformation program | `src/transform/transform_support_tickets.py` |
| Output file | `data/senzing-ready/support_tickets.jsonl` (432,281 bytes, 2,000 records) |
| Records in / out / rejected | 2,000 / 2,000 / 0 |
| Filters | none |
| Format changes | none: every value is emitted verbatim |
| Fields dropped | `opened_date` |
| Quality | 95.7 before mapping; analyzer clean after |

Field mappings: `docs/mapping/SUPPORT_TICKETS_mapper.md`. Sample output: `data/mapping/support_tickets_sample.jsonl`.
