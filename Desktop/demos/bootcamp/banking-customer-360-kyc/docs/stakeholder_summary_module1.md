# Stakeholder Summary: Module 1, Discover the Business Problem

**Project:** banking-customer-360-kyc
**Date:** 2026-11-14

## Problem

A US regional bank holds its customers in two systems (core banking and the card platform) that share no customer number. Neither system can tell when a depositor is also a cardholder, or when it holds the same customer twice. Relationship managers need a customer 360, and compliance needs KYC/KYB screening. Today those are two projects with two answers.

## Approach

Resolve all five data sources once with Senzing and serve both teams from the same resolved entities. The customer 360 view reads the resolved customer records. The KYC/KYB view reads the same entities for watchlist matches, identity conflicts against credit bureau files, and beneficial ownership through registry relationships. The two views stay separate: no watchlist, bureau or registry data in the 360, and no balances or credit limits in the compliance view. Work proceeds locally first.

## Data Sources

- CORE_BANKING: 97 records (deposit customers and accounts)
- CARD_SERVICES: 82 records (cardholders and cards)
- CREDIT_BUREAU: 30 records (identity verification)
- BUSINESS_REGISTRY: 22 records (owners, officers, parent companies)
- WATCHLIST: 17 records (Sanctions, PEP, Internal Fraud)

Total: 248 synthetic records, already in Senzing JSON format and under the built-in 500-record evaluation limit.

## Key Findings

- The data is deliberately hard: transliterated names, nicknames, missing identifiers, shared SSNs, and businesses linked to sanctioned people only through ownership records.
- No custom licence is needed for this volume.
- Common names, shared household addresses and people who have moved must not be flagged as hits; a name-only match goes to an analyst as a possible hit.

## Next Steps

1. Set up the Senzing SDK (Java, native on this machine).
2. Verify the installation, then collect and check the five source files.
3. Load all five sources together, then build the Customer 360 and KYC/KYB views and the three-tab analyst web app.

## ROI Considerations

- Senzing's published estimate for the annual cost of mismatched identity records in Financial Services is roughly $79.5B to $136.5B across the sector (from the Senzing whitepaper, via the MCP server).
- One resolution serving both teams avoids running two separate projects and vendors.
- Catching hidden ownership links and synthetic identities that name screening misses reduces compliance and fraud exposure; a quantified figure for this bank is not yet available.
