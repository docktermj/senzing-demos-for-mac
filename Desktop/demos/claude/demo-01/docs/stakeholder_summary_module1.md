# Stakeholder Summary — Discover the Business Problem

> 🤖 Bootcamp-generated business case

## Problem

Customer records are fragmented and duplicated across three systems — a CRM, a billing system, and support tickets — with inconsistent name and address formats and missing fields, making it hard to get a single, trustworthy view of each customer.

## Approach

Senzing entity resolution will ingest records from all three systems, standardize their differently-shaped name and address fields against the Senzing Entity Specification, and resolve matching records into unified customer entities using principle-based matching (no hand-written rules, no model training).

## Data Sources

- **CRM_SYSTEM** — ~4,000 records, joined name field, free-text address.
- **BILLING_SYSTEM** — ~4,000 records, parsed name (first/last), parsed address components.
- **SUPPORT_TICKETS** — ~2,000 records, sparse (name + email only), occasional typos.

## Key Findings

- The three sources diverge in shape for the same features (name, address), which is exactly the kind of cross-source inconsistency entity resolution is built to reconcile.
- Data quality varies by source (missing fields, inconsistent phone formats, typos) — handled in the upcoming Data Quality, Mapping, and Transformation module.

## Next Steps

1. SDK setup — install and configure the Senzing SDK.
2. Data collection — generate the source files for this scenario.
3. Data Quality, Mapping, and Transformation — map each source to the Entity Specification.
4. Data processing — load and resolve.
5. Query, Visualize and Discover — produce the master list, API, and reports.

## ROI Considerations

Per Senzing's published guidance, this falls under the Customer Data Management use case, where entity resolution increases customer-matching accuracy and insight within and across systems, without the lengthy rule-tuning cycle homegrown matching typically requires.
