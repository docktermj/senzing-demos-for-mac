# CRM_CONTACTS mapping

Field mappings and decisions: see [crm_contacts_mapper.md](crm_contacts_mapper.md).

## Logic

`TransformCrmContacts` reads each CSV row with the shared reader `Records` (values kept exactly as in the source; quoted addresses with commas handled), skips rows with no `contact_id`, and writes one Senzing JSON record per row: DATA_SOURCE, RECORD_ID, a FEATURES list (RECORD_TYPE, NAME_FULL, EMAIL, PHONE, ADDR_FULL; blanks omitted) and the `created_date` payload. The command line and feature-object building are shared with the other mappers through `Mapper`.

## Quality

4,000 of 4,000 records written. Analyzer, verbatim and routing checks passed; see [crm_contacts_mapper.md](crm_contacts_mapper.md#quality-notes) and docs/visualizations/mapping_crm_contacts_quality.html.

## How to run

```bash
bash -c 'source src/scripts/senzing-env.sh && "$SENZING_JAVA_HOME/bin/javac" -d src/build/classes -cp "$SENZING_SDK_JAR:src/build/classes" src/server/Json.java src/transform/Records.java src/transform/Mapper.java src/transform/transform_crm_contacts.java'
src/scripts/run-java.sh TransformCrmContacts data/raw/crm_contacts.csv -o data/senzing-ready/crm_contacts.jsonl
```

Add `--sample N` to map only the first N rows.
