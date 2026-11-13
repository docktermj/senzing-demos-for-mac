# Business Problem Statement

**Date**: 2026-11-11
**Project**: pc-insurance-claims-fraud
**Design Pattern**: Custom

## Problem Description

A fictional U.S. property-and-casualty insurer needs a trusted view of policyholders and their connections to claim-paid businesses and watchlist subjects, so legitimate claims can be handled quickly and potential fraud can be referred to the special investigations unit (SIU) with supporting evidence.

> "A fictional US property and casualty insurer wants one trusted view of every policyholder."

## Use Case Category

Insurance / Fraud Detection

## Design Pattern Reference

Custom business case; no gallery pattern was selected.

## Data Sources

1. **POLICYHOLDERS**: Policy and claims records; approximately 120 records; people and businesses; update frequency and access method not yet determined.
2. **VENDOR_REGISTRY**: Repair shops, medical providers, and other businesses paid on claims, including owner, director, and parent-company information; approximately 22 records; businesses and associated people; update frequency and access method not yet determined.
3. **SIU_WATCHLIST**: Known claims-fraud suspects and sanctioned or politically exposed persons; approximately 17 records; people; update frequency and access method not yet determined.

**Total**: 159 records across three sources. The brief says all source records are synthetic and requires reviewer approval before loading; approval was confirmed before the test and full source loads on 2026-11-11.

## Entity Types

People and organizations.

## Key Matching Criteria

- **Person names and variants** (priority not yet determined): the brief describes nicknames, misspellings, and names in other alphabets.
- **Identifiers, addresses, and contact details** (priority not yet determined): records may contain partial identifiers and changed addresses.
- **Vendor ownership and organizational relationships** (priority not yet determined): owners, directors, and parent companies may connect policyholders to businesses paid on claims.
- **Watchlist identity details** (priority not yet determined): potential SIU leads depend on connecting policyholders and vendor owners to listed subjects.

## Success Criteria

- Consolidate repeated policyholder records into a trusted view while keeping distinct, similar-looking people separate.
- Surface relevant connections among policyholders, claim-paid businesses, and watchlist subjects with evidence for SIU review; present matches as leads, not verdicts.
- Support faster handling of legitimate claims and earlier review of suspicious claims. Baselines and quantitative targets are not yet determined.

> "That way the people and AI agents who serve customers know who they're dealing with and who each person is connected to, and the insurer can pay honest claims quickly and stop fraudulent ones."

## Desired Output

**Format**: Clean master list; API; reports
**Use case**: Not yet determined (one-time, ongoing, or real-time)
**Integration**: Standalone; no downstream integration targets identified

## Integration Requirements

**Downstream systems**: None identified
**Integration method**: Not applicable
**Systems mentioned**: None

## Deployment Target

**Platform**: Local / on-premises
**Category**: Local
Development proceeds locally first; infrastructure is a production follow-up covered by the graduation production project and migration checklist.

## Timeline

**Target completion**: Not yet determined
**Key milestones**: Not yet determined

## Notes

The source brief was marked as a draft for human review and prohibited loading its synthetic data until approval. Reviewer approval was confirmed before loading; VENDOR_REGISTRY is now loaded.
