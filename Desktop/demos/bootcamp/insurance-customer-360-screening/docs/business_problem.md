# Business Problem Statement

**Date**: 2026-11-14
**Project**: insurance-customer-360-screening
**Design Pattern**: Custom

## Problem Description

A US insurer's customers live in two policy systems (P&C and life and annuity) that share no customer numbers, so the same customer is invisible across the two lines and sometimes duplicated within one; distribution and service need a customer 360 while compliance and the SIU need screening and due diligence, and both should be served from one resolution of the data.

> "Our customers live in two policy systems that don't share customer numbers. The P&C policy administration system holds our insureds; the life and annuity administration system holds our contract parties. We run the two lines as separate businesses, so neither system knows when a homeowners customer also owns one of our annuities, or when it holds the same customer twice."

## Use Case Category

Insurance (spanning Customer 360 and screening / compliance)

## Design Pattern Reference

None selected. This is a custom, bootcamper-described case.

## Data Sources

All data is synthetic test data; there are no real people in it. Six sources, 248 records in total.

1. **PC_POLICY_ADMIN**: P&C policy administration system (personal and commercial lines) / ~104 records / People and organizations / Update frequency: not specified / Access: to be determined in Data collection
2. **LIFE_ANNUITY_ADMIN**: life and annuity administration system (term, whole and universal life, key person cover, annuities) / ~75 records / People and organizations / Update frequency: not specified / Access: to be determined in Data collection
3. **CREDIT_BUREAU**: credit header records, used to verify identity / ~30 records / People / Update frequency: not specified / Access: to be determined in Data collection
4. **BUSINESS_REGISTRY**: business registrations with owners, officers and parent companies, including ownership percentages / ~22 records / Organizations and people / Update frequency: not specified / Access: to be determined in Data collection
5. **SIU_CASES**: the special investigations unit's case file (fraud type, role in case, line of business, case disposition) / ~13 records / People and organizations / Update frequency: not specified / Access: to be determined in Data collection
6. **SANCTIONS_PEP**: the screening vendor's list (Sanctions (OFAC SDN) or PEP) / ~4 records / People and organizations / Update frequency: not specified / Access: to be determined in Data collection

## Entity Types

Both (people and organizations)

## Key Matching Criteria

- **Name** (High priority): the data includes transliterated names and nicknames, and several common names (such as John and Robert Smith).
- **SSN** (High priority): links customer records to credit bureau files; some people share an SSN and some records are missing it.
- **Date of birth** (High priority): a date of birth that conflicts with a bureau file signals an identity problem.
- **Address** (Medium priority): households share addresses and people move, so address alone must not produce a hit.
- **Ownership and officer links** (High priority): a business's only link to a sanctioned person may be an ownership record in the business registry, including ownership percentages.

## Success Criteria

- One resolution of all six sources, serving both the customer 360 and screening teams.
- The 179 customer records (PC_POLICY_ADMIN plus LIFE_ANNUITY_ADMIN) collapse into the real customers behind them, with a count of how many hold both P&C cover and a life or annuity contract.
- An analyst screening queue that applies each line of business's rules to the same resolved people and catches what name screening alone misses (such as a business owned by a sanctioned person), with no false hits for common names.
- Every alert carries its evidence: the records, the match key or disclosed relationship, and the list entry or SIU case behind it.

> "One resolution of all six sources, serving both teams. The 179 customer records (PC_POLICY_ADMIN plus LIFE_ANNUITY_ADMIN) collapse into the real customers behind them, and we can see how many hold both P&C cover and a life or annuity contract. An analyst's screening queue that applies each line of business's rules to the same resolved people and catches what name screening alone misses, such as a business owned by a sanctioned person, with no false hits for common names."

## Desired Output

**Format**: Master list (one profile per real customer), Reports (screening and due diligence alerts), a web app with three tabs (Overview, Customer 360, Screening & Due Diligence)
**Use case**: One-time (an exercise on a fixed synthetic dataset)  **Integration**: Standalone

> "A web app with three tabs, **Overview**, **Customer 360** and **Screening & Due Diligence**, where analysts can see why Senzing matched or related records and record their dispositions (True match, False positive, Refer to SIU). Dispositions are stored by the app, never written to Senzing."

## Integration Requirements

**Downstream systems**: None
**Integration method**: Not applicable
**Systems mentioned**: None (`integration_targets` is empty in `config/bootcamp_preferences.yaml`)

## Deployment Target

Platform: Local / on-premises. Category: Local. Development proceeds locally first; infrastructure is a production follow-up covered by the graduation production project and migration checklist.

## Timeline

**Target completion**: Not specified
**Key milestones**: Not specified

## Notes

- The customer 360 never shows SIU, screening list, credit bureau or business registry data; where one of those records is part of a customer, it says only that a record outside the customer systems exists. The screening view never shows premiums, face amounts or account values.
- Screening rules differ by line of business: sanctions are Prohibited; substantiated or law-enforcement-referred SIU history is High; PEPs are High only for customers holding a life or annuity contract; possible matches are Medium and go to an analyst; identity verification conflicts and ownership screening (including the 50% rule) follow the rules in the original description.
- Innocent explanations that must not be flagged as hits: common names, households sharing an address, and people who have moved. A name-only match is never a hit by itself.

> "A name-only match goes to an analyst as a possible match. It is never a hit by itself."
