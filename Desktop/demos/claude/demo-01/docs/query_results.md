# Query results — Query, Visualize and Discover

Programs in `src/query/` (Java; shared `SenzingSession.java` holds the Senzing environment for each program's whole run). Outputs in `data/results/` (kept out of version control: they hold record data).

| # | Requirement (source) | Program | Result |
|---|---|---|---|
| 1 | Resolved customer count vs input (success criterion 1) | `CustomerReports` | 10,000 records → **7,134 customers** (1.40 records per customer). CRM_CONTACTS 4,000 → 3,700; WEB_ORDERS 4,500 → 4,201; SUPPORT_TICKETS 1,500 → 1,500 |
| 2 | Customers in more than one system (success criterion 2) | `CustomerReports` | **2,090** customers in 2+ sources: CRM × WEB 1,489; WEB × SUPPORT 522; CRM × SUPPORT 433 (`cross_source_customers.csv`) |
| 3 | Why two records did or did not match (success criterion 3) | `ExplainMatch` | Works both ways — see the three worked pairs below |
| 4 | Customer master list (output "Master list") | `CustomerReports` | `customer_master_list.csv`: 7,134 rows, record IDs per source |
| 5 | Customer search and 360 view (output "API") | `CustomerSearch` | Search by name, email and/or phone; full profile with every source record and related (unmerged) customers |
| 6 | Duplicates within each system (output "Reports") | `CustomerReports` | CRM_CONTACTS: 279 customers hold 579 records; WEB_ORDERS: 288 customers hold 587 records; SUPPORT_TICKETS: none (`within_source_duplicates.csv`) |

## Worked match explanations (`ExplainMatch`)

- **CRM_CONTACTS C000727 + SUPPORT_TICKETS T000289 → same customer (entity 200011).** Why key +NAME+PHONE+EMAIL, rule SF1_PNAME_CFF; name, phone and email each scored 100 (SAME).
- **CRM_CONTACTS C000027 + WEB_ORDERS W002988 → different customers (200005 and 500650), related as POSSIBLY_SAME.** Why key +NAME only: the names are identical (100) but email and phone score 0 and the addresses 29 (all NO_CHANCE). Two different people sharing a name — kept apart correctly.
- **CRM_CONTACTS C002843 + SUPPORT_TICKETS T001133 → different entities (200019 and 504431), POSSIBLY_SAME on +NAME.** The ticket carries a name and nothing else to compare; by the ground truth this is the same customer, so it is one of the 136 split customers. Nothing more can be concluded from a name alone.

## Customer 360 example (`CustomerSearch --name "Carol Lopez" --email ...`)

Entity 200039 holds 4 records: 2 CRM_CONTACTS (a second contact), 1 WEB_ORDERS and 1 SUPPORT_TICKETS, joined by +NAME+ADDRESS+EMAIL and +NAME+PHONE+EMAIL.
