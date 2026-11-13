# Transformation lineage: SUPPORT_TICKETS

| | |
|---|---|
| Source file | data/raw/support_tickets.jsonl (JSON Lines, flat objects, 8 keys, sparse, 1,500 records, 286,352 bytes) |
| Program | src/transform/transform_support_tickets.java (class TransformSupportTickets) + src/transform/Records.java + src/transform/Mapper.java |
| Output file | data/senzing-ready/support_tickets.jsonl (Senzing JSON, one record per line, 1,500 records) |
| Records before / after | 1,500 / 1,500 (0 skipped for missing ticket_id) |

## Field mappings

See [support_tickets_mapper.md](support_tickets_mapper.md). 5 fields become matching features, ticket_id becomes RECORD_ID, topic and opened_on ride along as payload.

## Format changes

None. Values are copied verbatim (verified by sz_verbatim_check.py); absent values are omitted rather than emitted empty.

## Filters

Records without a ticket_id would be skipped; none were.

## Quality improvements

None applied in the transform. Known source variation (nicknames and typos in names, three phone formats, two opened_on formats) is passed through.
