# Stakeholder Summary: Discover the Business Problem

**Project**: insurance-customer-360-screening
**Date**: 2026-11-14

## Problem

Our P&C policy system and our life and annuity system share no customer numbers, so neither knows when a homeowners customer also owns one of our annuities, or when it holds the same customer twice. Distribution and service want a customer 360. Compliance and the SIU want screening and due diligence. Today that means two projects, two vendors, and two answers.

## Approach

Resolve all customer data once with Senzing entity resolution, and serve both teams from the same resolved entities. The customer 360 shows every policy and contract per real customer. The screening view applies each line of business's rules to the same people, covering sanctions, SIU history, PEPs, possible matches, identity verification, and ownership screening. Every alert carries its evidence. The work is built locally in Java, with no downstream integrations.

## Data sources

Six synthetic sources, 248 records in total:

- PC_POLICY_ADMIN (104 records)
- LIFE_ANNUITY_ADMIN (75 records)
- CREDIT_BUREAU (30 records)
- BUSINESS_REGISTRY (22 records)
- SIU_CASES (13 records)
- SANCTIONS_PEP (4 records)

## Key findings

- The 179 customer records (PC_POLICY_ADMIN plus LIFE_ANNUITY_ADMIN) are expected to collapse into fewer real customers; the exact number is a result to be measured later.
- The data is hard on purpose: transliterated names, nicknames, missing identifiers, shared SSNs, and businesses linked to a sanctioned person only through an ownership record.
- Senzing holds ambiguous records (such as common names) as possible matches instead of assigning them arbitrarily, and tracks both disclosed and discovered relationships (via Senzing docs).
- The customer 360 and screening views must stay separate: the customer 360 never shows SIU, screening, bureau, or registry data, and the screening view never shows premiums, face amounts, or account values.

## Next steps

1. SDK setup: install and configure the Senzing SDK for Java.
2. Data collection: bring in the six source files.
3. Data Quality, Mapping, and Transformation: assess quality and map each source to the Senzing Entity Specification.
4. Data processing: load and validate.
5. Query, Visualize and Discover: build the Overview, Customer 360, and Screening & Due Diligence views.

## ROI considerations

- Senzing's published estimate puts the annual cost of mismatched identity records in the Insurance sector at $38B to $73B (an industry-wide estimate, not a figure for this organization).
- Resolving once serves two teams, replacing two separate projects and vendors.
- Success measures for this project: the number of customers who hold both P&C and life or annuity cover (cross-sell visibility), and a screening queue that catches ownership-based hits that name screening misses, with no false hits for common names.
