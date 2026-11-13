# Mapping journal: SUPPORT_TICKETS

## Step 1 (profile)
- Mode: interactive (bootcamp concise mapping mode). Mapper language: Java.
- support_tickets: 1,500 records, 8 fields. 30 structure variations are missing-key combinations of the same fields (common fields ticket_id, topic, opened_on), not distinct record types; no --group_by re-profile needed. No second entity in any column.

## Step 2 (plan)
- support_tickets: master, data source SUPPORT_TICKETS, record type PERSON (the requester), RECORD_ID from ticket_id. No children, lookups, relationships or embedded entities.

## Step 3 (map)
- requester_name -> NAME_FULL (single field, not split); requester_email -> EMAIL_ADDRESS; phone -> PHONE_NUMBER; city/postal_code -> ADDR_CITY/ADDR_POSTAL_CODE (one ADDRESS object).
- topic, opened_on -> payload. ticket_id -> RECORD_ID. RECORD_TYPE constant PERSON with justification.
- Cross-source pairs (all exempt, recorded): NAME requester_name vs CRM full_name (same quantity) and vs WEB first/last (parsed vs full); ADDRESS city/postal_code vs CRM address (parsed vs full) and vs WEB city/zip (same parts), all the record subject's location, no usage type; EMAIL requester_email vs email (the record subject's email); PHONE phone vs phone.

## Step 4 (generate & validate)
- Mapper: src/transform/transform_support_tickets.java (class TransformSupportTickets), shared Records + Mapper.
- Sample: data/mapping/support_tickets_sample.jsonl (100) -> analyzer exit 0.
- Full run: data/mapping/support_tickets_quality.jsonl, 1,500 of 1,500 -> analyzer exit 0, no errors or warnings; verbatim exit 0 (run directly on the JSONL source); routing: only ticket_id "dropped" (it is RECORD_ID).
- Population equals source presence: NAME 97.2%, EMAIL 80.5%, ADDRESS 76.7% (any part; city 50.1%, postal 53.7%), PHONE 51.1%.
- Caveats: 42 records (2.8%) carry no name; address is city/postal only (no street), so it is a weak matching signal.
- QA verdict: approve (records_output 1500). Step 5 menu held for module step 18a.

## Step 5 (environment)
- Decision: skip. Last source; the bootcamper chose to proceed to loading, so the optional sandbox test load is settled as skip; the real load happens in Data processing.
- Load-ready output: data/senzing-ready/support_tickets.jsonl (1,500 records, byte-identical to the validated run).
