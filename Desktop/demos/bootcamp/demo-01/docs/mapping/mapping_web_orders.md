# WEB_ORDERS mapping

Field mappings and decisions: see [web_orders_mapper.md](web_orders_mapper.md).

## Logic

`TransformWebOrders` reads each CSV row with the shared reader `Records` (values kept exactly as in the source), skips rows with no `order_id`, and writes one Senzing JSON record per row: DATA_SOURCE, RECORD_ID, a FEATURES list (RECORD_TYPE, one NAME, EMAIL, PHONE, one ADDRESS; blanks omitted) and the two payload keys.

## Quality

4,500 of 4,500 records written. Analyzer, verbatim and routing checks passed; see [web_orders_mapper.md](web_orders_mapper.md#quality-notes) and docs/visualizations/mapping_web_orders_quality.html.

## How to run

```bash
bash -c 'source src/scripts/senzing-env.sh && "$SENZING_JAVA_HOME/bin/javac" -d src/build/classes -cp "$SENZING_SDK_JAR:src/build/classes" src/server/Json.java src/transform/Records.java src/transform/transform_web_orders.java'
src/scripts/run-java.sh TransformWebOrders data/raw/web_orders.csv -o data/senzing-ready/web_orders.jsonl
```

Add `--sample N` to map only the first N rows.
