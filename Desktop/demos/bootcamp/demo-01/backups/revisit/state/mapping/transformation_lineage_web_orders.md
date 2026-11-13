# Transformation lineage: WEB_ORDERS

| | |
|---|---|
| Source file | data/raw/web_orders.csv (CSV, header row, 11 columns, 4,500 rows, 489,915 bytes) |
| Program | src/transform/transform_web_orders.java (class TransformWebOrders) + src/transform/Records.java |
| Output file | data/senzing-ready/web_orders.jsonl (Senzing JSON, one record per line, 4,500 records) |
| Records before / after | 4,500 / 4,500 (0 skipped for missing order_id) |

## Field mappings

See [web_orders_mapper.md](web_orders_mapper.md). 8 fields become matching features, order_id becomes RECORD_ID, 2 fields ride along as payload.

## Format changes

None. Values are copied verbatim (verified by sz_verbatim_check.py); blank values are omitted rather than emitted empty.

## Filters

Rows without an order_id would be skipped; none were.

## Quality improvements

None applied in the transform. Known source variation (lowercase state codes ~6%, ZIP+4 ~5%, three phone formats) is passed through for Senzing's own standardization.
