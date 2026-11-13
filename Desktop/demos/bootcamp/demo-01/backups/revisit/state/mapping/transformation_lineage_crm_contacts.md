# Transformation lineage: CRM_CONTACTS

| | |
|---|---|
| Source file | data/raw/crm_contacts.csv (CSV, header row, quoted fields, 6 columns, 4,000 rows, 436,980 bytes) |
| Program | src/transform/transform_crm_contacts.java (class TransformCrmContacts) + src/transform/Records.java + src/transform/Mapper.java |
| Output file | data/senzing-ready/crm_contacts.jsonl (Senzing JSON, one record per line, 4,000 records) |
| Records before / after | 4,000 / 4,000 (0 skipped for missing contact_id) |

## Field mappings

See [crm_contacts_mapper.md](crm_contacts_mapper.md). 4 fields become matching features, contact_id becomes RECORD_ID, created_date rides along as payload.

## Format changes

None. Values are copied verbatim (verified by sz_verbatim_check.py); blank values are omitted rather than emitted empty. The single name and address strings are passed whole for Senzing to parse.

## Filters

Rows without a contact_id would be skipped; none were.

## Quality improvements

None applied in the transform. Known source variation (three phone formats, two created_date formats) is passed through.
