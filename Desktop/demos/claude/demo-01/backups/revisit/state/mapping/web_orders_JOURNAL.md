# Mapping journal

## WEB_ORDERS — step 1 (profile)
- Mode: interactive (concise presentation). Mapper language: Java.
- web_orders: 4,500 records, 11 fields. No embedded entity, no child tables.

## WEB_ORDERS — step 2 (plan)
- web_orders: master, data source WEB_ORDERS, record type PERSON, RECORD_ID from order_id. No children, lookups, relationships or embedded entities.

## WEB_ORDERS — step 3 (map)
- first/last name -> NAME_FIRST/NAME_LAST (one NAME object); email -> EMAIL_ADDRESS; phone -> PHONE_NUMBER; street/city/state/zip -> ADDR_LINE1/CITY/STATE/POSTAL_CODE (one ADDRESS object).
- order_date, order_total -> payload. order_id -> RECORD_ID. RECORD_TYPE constant PERSON with justification.
- First source mapped: no cross-source pairs yet.

## WEB_ORDERS — step 4 (generate & validate)
- Mapper: src/transform/transform_web_orders.java (class TransformWebOrders) + shared reader src/transform/Records.java.
- Sample: data/mapping/web_orders_sample.jsonl (100) -> analyzer exit 0, no errors or warnings.
- Full run: data/mapping/web_orders_quality.jsonl, 4,500 of 4,500 records -> analyzer exit 0 (data/mapping/web_orders_analysis.md); verbatim check exit 0; routing report: only order_id "dropped" (it is RECORD_ID, not a feature).
- Verbatim/routing scripts read JSONL only: ran the unmodified scripts on a CSV->JSONL copy (data/mapping/web_orders_source.jsonl); routing run with --payload-fields order_date,order_total.
- Analyzer needs Python 3.10+ (match statement): ran with /opt/homebrew/bin/python3.12; system python3 is 3.9.6.
- Population matches source presence exactly: NAME 100%, EMAIL 97.8%, ADDRESS 80.2%, PHONE 69.3%.
- QA verdict: approve (records_output 4500). Step 5 menu held for module step 18a.

## WEB_ORDERS — step 5 (environment)
- Decision: skip (optional sandbox test load). More sources remain; the real load happens in Data processing.
- Load-ready output: data/senzing-ready/web_orders.jsonl (4,500 records, byte-identical to the validated run).
