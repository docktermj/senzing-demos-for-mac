# Banking Customer 360 and KYC/KYB - synthetic ingredient set

Five synthetic source systems from a **US retail and business bank**, built for the
[Customer 360 and KYC/KYB for a Retail Bank](../../recipes/banking-customer-360-kyc.md) recipe.

**Synthetic - no real people, no PII.** Every person and business comes from Senzing's published
entity resolution truth set, re-cut into the feeds a bank's customer data hub would receive. Account
numbers, balances, limits and card numbers are generated; card numbers carry only their last four
digits.

| File | Rows | What it is |
|---|---|---|
| `core_cif_extract.jsonl` | 97 | CORE_BANKING: the core banking system's customer information file (CIF) extract. Deposit accounts (checking, savings, money market, CDs) with ownership type, status, balance, branch and relationship tier. |
| `card_cardholder_master.jsonl` | 82 | CARD_SERVICES: the card platform's cardholder master. Card account, last four digits of the card, primary or authorized user, product, credit limit, balance, status (including charged off) and delinquency. |
| `credit_header.jsonl` | 30 | CREDIT_BUREAU: credit header records used for identity verification. |
| `business_registry.jsonl` | 22 | BUSINESS_REGISTRY: business registrations, their owners, officers and parent companies, linked by disclosed relationships with ownership percentages. |
| `screening_list.jsonl` | 17 | WATCHLIST: the screening list. `LIST_TYPE` is Sanctions, PEP or Internal Fraud (customers exited for fraud). |

The files are already in Senzing JSON format, one record per line, so there is no mapping to do.
The same person is often in both customer systems, sometimes twice in one. Seeded in the data: a
customer on the sanctions list, a business owned outright by a sanctioned person, a company group
owned by two PEPs, customers exited for fraud, and one SSN presented by three different customers,
alongside common names (several John and Robert Smiths) that should go to an analyst rather than be
flagged outright.
