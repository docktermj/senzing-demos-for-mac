# Business Problem Statement

**Date**: 2026-11-14
**Project**: banking-customer-360-kyc
**Design Pattern**: Custom

## Problem Description

A US regional bank keeps its customers in two systems with no shared customer number, and wants to resolve them once to serve both a customer 360 for relationship managers and KYC/KYB screening for compliance.

> "We want to resolve our customer data **once** and serve both teams from the same resolved entities."

## Use Case Category

Customer 360 and KYC/KYB (KYC)

## Design Pattern Reference

None selected; the bootcamper described their own case in a scenario document.

## Data Sources

All data is synthetic test data (no real people), already in Senzing JSON format (JSONL, one record per line). Source: `~/Desktop/demos/recipes/banking-customer-360-kyc/ingredients/` (per the scenario's data-location note).

1. **CORE_BANKING**: Core banking CIF extract / ~97 records / People and organizations (deposit customers and accounts) / Update frequency: not stated / Access: file (`core_cif_extract.jsonl`)
2. **CARD_SERVICES**: Card platform cardholder master / ~82 records / Cardholders and cards / Update frequency: not stated / Access: file (`card_cardholder_master.jsonl`)
3. **CREDIT_BUREAU**: Credit header records, used to verify identity / ~30 records / People / Update frequency: not stated / Access: file (`credit_header.jsonl`)
4. **BUSINESS_REGISTRY**: Business registrations with owners, officers and parent companies, including ownership percentages / ~22 records / Organizations and their owners and officers / Update frequency: not stated / Access: file (`business_registry.jsonl`)
5. **WATCHLIST**: Screening list; `LIST_TYPE` is Sanctions, PEP or Internal Fraud / ~17 records / People and organizations / Update frequency: not stated / Access: file (`screening_list.jsonl`)

Total: 248 records, under the built-in 500-record evaluation limit. A separate answer key (`banking_truthset_key.csv`) exists for validating results only and is never loaded.

## Entity Types

Both (people and organizations)

## Key Matching Criteria

- **Name** (High priority): transliterated names, nicknames and common names (several John and Robert Smiths) make name-only matches unreliable; a name-only match goes to an analyst as a possible hit.
- **SSN** (High priority): links customers to credit bureau files and exposes synthetic identities; some people share an SSN and some are missing.
- **Date of birth** (High priority): a conflict between a customer and a bureau file signals an identity problem.
- **Address** (Medium priority): households share addresses and people move, so it supports household links but is not proof of identity.
- **Ownership and officer relationships** (High priority): the only link between a business and a sanctioned person may be a registry ownership record, including ownership percentage.

## Success Criteria

- One resolution of all five sources, serving both teams.
- The 179 customer records (CORE_BANKING plus CARD_SERVICES) collapse into the real customers behind them, with a count of those holding both a deposit account and a card.
- An analyst's KYC/KYB queue that catches what name screening alone misses (such as a business owned by a sanctioned person) with no false hits for common names. Every alert comes with its evidence: the records, the match key or disclosed relationship, and the watchlist entry behind it.
- A web app with three tabs, **Overview**, **Customer 360** and **KYC / KYB**, where analysts can see why Senzing matched or related records and record dispositions (True match, False positive, Escalate to EDD), stored by the app and never written to Senzing.

> "An analyst's KYC/KYB queue that catches what name screening alone misses, such as a business owned by a sanctioned person, with no false hits for common names."

## Desired Output

**Format**: Master list (one profile per real customer), Reports (KYC/KYB alert queue with evidence), plus a three-tab web app
**Use case**: One-time (demonstration)  **Integration**: Standalone

Customer 360 profile: every deposit account and card; total deposits, card credit and card balance; household and ownership links; duplicates inside each system; cross-sell gaps (deposits but no card, or a card but no deposit account).

KYC/KYB alerts: screening hits (Sanctions is Prohibited; Internal Fraud and PEP are High); possible screening hits (Medium, analyst decides); identity verification (several customers on one bureau file is High, one customer is Medium); beneficial ownership (sanctioned owners holding 50% or more make a business Prohibited, anything else High; subsidiaries roll up into the parent's alert).

Views stay apart: the customer 360 never shows watchlist, credit bureau or business registry data (it says only that such a record exists), and the compliance view never shows balances or credit limits.

> "One profile per real customer"

## Integration Requirements

**Downstream systems**: None. **Integration method**: Not applicable. **Systems mentioned**: none (`integration_targets: []` in `config/bootcamp_preferences.yaml`).

> "integration_targets: []"

## Deployment Target

Platform: Local / on-premises. Category: Local. Development proceeds locally first; infrastructure is a production follow-up covered by the graduation production project and migration checklist.

## Timeline

**Target completion**: Not stated. **Key milestones**: Not stated.

## Notes

- All data in this exercise is synthetic test data; there are no real people in it.
- The data is hard on purpose: transliterated names, nicknames, missing identifiers, people who share an SSN, and businesses whose only link to a sanctioned person is an ownership record in the registry.
- Innocent explanations not to flag as hits: common names, households sharing an address, and people who have moved.

> "A name-only match goes to an analyst as a possible hit. It is never a hit by itself."
