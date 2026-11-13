# Data processing — executive summary

**What was done:** All three customer sources (CRM contacts, web orders, support tickets; 10,000 records) were loaded into Senzing with zero errors and resolved into 7,134 customer entities.

**Headline results**

- 2,090 customers are now recognised across two or more systems — the single customer view the business problem asked for.
- Measured against the answer key built with the data, 99.9% of merges are correct and 95.1% of true matches were found.
- Duplicate records inside a system were consolidated: about 300 duplicate CRM contacts and about 300 repeat-order profiles.

**Risks and gaps**

- 2 incorrect merges, both same-named people with near-identical phone numbers.
- 136 customers remain split, mostly support-ticket requesters with sparse contact details; they are flagged as possible matches for review.

**Next:** query and visualise the resolved customers, and check the results against the business questions.
