# SUPPORT_TICKETS mapping

Field mappings and decisions: see [support_tickets_mapper.md](support_tickets_mapper.md).

## Logic

`TransformSupportTickets` reads each JSON line with the shared reader `Records` (parsed by the shared `Json` reader; values kept exactly as in the source), skips lines with no `ticket_id`, and writes one Senzing JSON record per ticket: DATA_SOURCE, RECORD_ID, a FEATURES list (RECORD_TYPE, NAME_FULL, EMAIL, PHONE, one ADDRESS of city and postal code; absent values omitted) and the `topic` and `opened_on` payload. The command line and feature-object building are shared through `Mapper`.

## Quality

1,500 of 1,500 records written. Analyzer, verbatim and routing checks passed; see [support_tickets_mapper.md](support_tickets_mapper.md#quality-notes) and docs/visualizations/mapping_support_tickets_quality.html.

## How to run

```bash
bash -c 'source src/scripts/senzing-env.sh && "$SENZING_JAVA_HOME/bin/javac" -d src/build/classes -cp "$SENZING_SDK_JAR:src/build/classes" src/server/Json.java src/transform/Records.java src/transform/Mapper.java src/transform/transform_support_tickets.java'
src/scripts/run-java.sh TransformSupportTickets data/raw/support_tickets.jsonl -o data/senzing-ready/support_tickets.jsonl
```

Add `--sample N` to map only the first N records.
