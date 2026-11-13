# Mapping journal: CRM_CONTACTS

## Step 1 (profile)
- Mode: interactive (bootcamp concise mapping mode). Mapper language: Java.
- crm_contacts: 4,000 records, 6 fields. No structure variations; no second entity in any column.

## Step 2 (plan)
- crm_contacts: master, data source CRM_CONTACTS, record type PERSON, RECORD_ID from contact_id. No children, lookups, relationships or embedded entities.

## Step 3 (map)
- full_name -> NAME_FULL (single field, not split); email -> EMAIL_ADDRESS; phone -> PHONE_NUMBER; address -> ADDR_FULL (single string, not split).
- created_date -> payload. contact_id -> RECORD_ID. RECORD_TYPE constant PERSON with justification.
- Cross-source pairs vs WEB_ORDERS (all exempt, recorded): NAME full_name vs first_name+last_name (parsed vs full, record subject); ADDRESS address vs street/city/state/zip (parsed vs full, same subject, no usage type); EMAIL email vs email and PHONE phone vs phone (same quantity, same field name).

## Step 4 (generate & validate)
- Mapper: src/transform/transform_crm_contacts.java (class TransformCrmContacts), using shared src/transform/Records.java + Mapper.java (Mapper also adopted by TransformWebOrders; its output re-verified byte-identical).
- Sample: data/mapping/crm_contacts_sample.jsonl (100) -> analyzer exit 0.
- Full run: data/mapping/crm_contacts_quality.jsonl, 4,000 of 4,000 -> analyzer exit 0, no errors or warnings; verbatim exit 0 (on CSV->JSONL copy); routing: only contact_id "dropped" (it is RECORD_ID).
- Population equals source presence: NAME 100%, EMAIL 95.1%, ADDRESS 90.1%, PHONE 88.2%.
- QA verdict: approve (records_output 4000). Step 5 menu held for module step 18a.

## Step 5 (environment)
- Decision: skip (optional sandbox test load). SUPPORT_TICKETS remains; the real load happens in Data processing.
- Load-ready output: data/senzing-ready/crm_contacts.jsonl (4,000 records, byte-identical to the validated run).
