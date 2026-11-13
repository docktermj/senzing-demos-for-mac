# Stakeholder Summary: Banking KYC/KYB/AML Demo

## Problem

A fictional US bank needs a trusted, unified view of customers and their business connections to support KYC, KYB, and AML work. Screening results should be evidence-backed leads, not verdicts.

## Proposed approach

Use the customer/account, business-registry, and watchlist sources to build customer views, examine ownership and officer relationships, and compare screening outcomes with the supplied answer key. The bootcamp will collect the sources, assess and map their data, process them, and inspect the resolved results. The planned deployment is local/on-premises, with no downstream software integrations specified.

## Data sources

| Source | Brief description | Approximate records |
|---|---|---:|
| `BANK_CUSTOMERS` | Customer and account records for people and businesses | 120 |
| `BUSINESS_REGISTRY` | Business, ownership, and officer records | 22 |
| `AML_WATCHLIST` | Sanctions, PEP, and internal fraud-list records | 17 |

The brief describes varied names, aliases, alternate scripts, old addresses, and partial or missing identifiers. It also calls out business ownership/control links and a requirement to keep similar-but-distinct people separate.

## Key findings

- The three sources total approximately 159 records.
- The desired result is a unified customer view with relevant business connections and evidence-backed screening leads.
- The supplied answer key will be used to compare results and report differences.
- Integration targets and operational update frequency have not been specified.

## Next steps

1. Collect the three source files and preserve their record values and specified attribute names.
2. Assess and map each source.
3. Process the data locally.
4. Inspect customer matches, business relationships, and screening leads.
5. Compare the results with the supplied answer key and report any differences.

## ROI considerations

Senzing's published KYC guidance identifies more complete customer views, improved KYC and enhanced due diligence, fewer watchlist false positives, and ongoing due diligence as potential value areas. These are evaluation goals, not guaranteed outcomes. The brief provides no financial baseline, so quantify impact only after measuring review effort, false-positive workload, and result quality during the demo.

*Sourced from Senzing docs via the MCP server.*
