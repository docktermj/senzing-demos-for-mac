# Insurance Customer 360 and Screening - synthetic ingredient set

Six synthetic source systems from a **US personal and commercial insurer**, built for the
[Customer 360 and Screening for an Insurer](../../recipes/insurance-customer-360-screening.md)
recipe.

**Synthetic - no real people, no PII.** Every person and business comes from Senzing's published
entity resolution truth set, re-cut into the feeds an insurer's customer data hub would receive.
Policy and contract numbers, premiums, face amounts and SIU case details are generated.

| File | Rows | What it is |
|---|---|---|
| `pc_policy_admin_extract.jsonl` | 104 | PC_POLICY_ADMIN: the property and casualty policy administration system. Personal lines (auto, homeowners, renters, umbrella) and commercial lines (businessowners, commercial auto, general liability, workers compensation), with insured role, written premium, term dates, status and producer. |
| `life_annuity_admin_extract.jsonl` | 75 | LIFE_ANNUITY_ADMIN: the life and annuity administration system. Term, whole and universal life, key person cover and annuities, with party role, face amount, premium or account value, and status. |
| `credit_header.jsonl` | 30 | CREDIT_BUREAU: credit header records used for identity verification. |
| `business_registry.jsonl` | 22 | BUSINESS_REGISTRY: business registrations, their owners, officers and parent companies, linked by disclosed relationships with ownership percentages. |
| `siu_cases.jsonl` | 13 | SIU_CASES: the special investigations unit's case file. Case number, referral date, fraud type, role in the case, line of business and disposition. |
| `sanctions_pep.jsonl` | 4 | SANCTIONS_PEP: the screening vendor's list. Sanctions entries carry the list and program; PEP entries carry the position and country. |

The files are already in Senzing JSON format, one record per line, so there is no mapping to do.
The same person is often in both policy systems, sometimes twice in one. Seeded in the data: a
customer on the sanctions list, a business owned outright by a sanctioned person, a company group
owned by two PEPs, customers with substantiated, open and unsubstantiated SIU cases, and one SSN
presented by three different customers, alongside common names (several John and Robert Smiths)
that should go to an analyst rather than be flagged outright.
