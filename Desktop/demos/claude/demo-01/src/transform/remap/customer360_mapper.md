# Customer 360 mapper (remap run, 2026-11-10)
Produced by the Senzing mapping_workflow. Code: `customer360_mapper.py`. Spec: `mapping_spec.json`.

| Source | DATA_SOURCE | RECORD_ID | Features (one object each) | Payload | Ignored |
|---|---|---|---|---|---|
| crm_system.csv | CRM_SYSTEM | crm_id | NAME_FULL; ADDR_FULL; PHONE_NUMBER; EMAIL_ADDRESS | campaign | created_date |
| billing_system-regenerated.csv | BILLING_SYSTEM | billing_id | NAME_FIRST+NAME_LAST; ADDR_LINE1+CITY+STATE+POSTAL_CODE; PHONE_NUMBER | plan | last_invoice_date |
| support_tickets.csv | SUPPORT_TICKETS | ticket_id | NAME_FULL; EMAIL_ADDRESS | subject | opened_date |

All records carry `{"RECORD_TYPE":"PERSON"}` (single-type sources). Blank values are omitted; values are routed verbatim.
Decisions: category columns payload / date columns ignored, following the Bootcamper's CRM_SYSTEM decisions.
Sample: see all_sample.jsonl.
