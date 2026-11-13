# UAT results — Data processing

The business case is bootcamp-generated, so there are no business users to convene; this UAT was self-directed against the generator's ground truth (`data/ground_truth/record_to_customer.csv`, which invented customer each record describes).

## Cross-source spot-check (10 entities)

Each entity below holds records from 2 or more sources. "Customers" is how many distinct invented customers its records belong to: 1 means correct.

| Entity | Records (source: match key) | Customers | Verdict |
|---|---|---|---|
| 200011 | CRM_CONTACTS (first); SUPPORT_TICKETS: +NAME+PHONE+EMAIL | 1 | ✅ |
| 200039 | CRM_CONTACTS (first); CRM_CONTACTS: +NAME+ADDRESS+EMAIL; WEB_ORDERS: +NAME+ADDRESS+EMAIL; SUPPORT_TICKETS: +NAME+PHONE+EMAIL | 1 | ✅ (CRM second contact merged too) |
| 200046 | CRM_CONTACTS (first); WEB_ORDERS: +NAME+ADDRESS+EMAIL | 1 | ✅ |
| 200053 | CRM_CONTACTS (first); WEB_ORDERS: +NAME+ADDRESS | 1 | ✅ (full vs parsed address) |
| 200060 | CRM_CONTACTS (first); WEB_ORDERS: +NAME+ADDRESS+PHONE | 1 | ✅ |
| 200067 | CRM_CONTACTS (first); WEB_ORDERS: +NAME+PHONE+EMAIL | 1 | ✅ (phone in two formats) |
| 200081 | CRM_CONTACTS (first); CRM_CONTACTS: +EXACTLY_SAME; WEB_ORDERS: +NAME+ADDRESS+PHONE+EMAIL | 1 | ✅ (ZIP+4 vs ZIP) |
| 200095 | CRM_CONTACTS (first); WEB_ORDERS: +NAME+PHONE+EMAIL | 1 | ✅ |
| 300006 | CRM_CONTACTS (first); WEB_ORDERS: +NAME+PHONE+EMAIL | 1 | ✅ |
| 300034 | CRM_CONTACTS (first); WEB_ORDERS: +NAME+ADDRESS+EMAIL; SUPPORT_TICKETS: +NAME+EMAIL | 1 | ✅ |

**Result: 10 of 10 correct (100%).** Record-level detail for each entity is in `data/results/evaluation.json` (`cross_source_samples`).

## Over-merges found (2 entities)

| Entity | Records | Match key | Cause seen in the records |
|---|---|---|---|
| 300393 | CRM_CONTACTS C002326, WEB_ORDERS W004008 | +NAME+PHONE (CNAME_CFF) | Same name; phone numbers differ in one digit; different emails; the order has no address |
| 301424 | CRM_CONTACTS C003448, C002765 | +NAME+PHONE (CNAME_CFF) | Same name; phone numbers differ in one digit (one in a different format); different emails and addresses |

These come from the synthetic data: the generator's collision check compared exact values, so near-identical phone numbers on same-named people slipped through. They are a realistic hard case, not a mapping defect.

## Missed merges (136 customers split across entities)

- 127 relationships connect pieces of the same customer, 122 of them POSSIBLY_SAME on +NAME alone: Senzing related the records but did not merge them, because no other feature was shared.
- 122 of the 136 involve SUPPORT_TICKETS, the sparse source (phone ~51%, city/postal ~50%, no street, nicknames and typos).
