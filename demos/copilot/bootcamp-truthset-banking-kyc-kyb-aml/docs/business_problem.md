# Business Problem Statement

**Date**: 2026-11-09
**Project**: Banking KYC/KYB/AML Demo
**Design Pattern**: Custom

## Problem Description

A fictional US bank needs a trusted, unified view of customers and their relevant business connections to support KYC, KYB, and AML work.

> "A fictional US bank wants one trusted view of every customer."

## Use Case Category

KYC (with KYB and AML screening)

## Design Pattern Reference

Not applicable; no design pattern was selected.

## Data Sources

1. **BANK_CUSTOMERS**: Customer and account records / ~120 records / people and businesses / update frequency and access not specified in the brief.
2. **BUSINESS_REGISTRY**: Business registry, ownership, and officer records / ~22 records / people and organizations / update frequency and access not specified in the brief.
3. **AML_WATCHLIST**: Sanctions, PEP, and internal fraud-list records / ~17 records / people / update frequency and access not specified in the brief.

## Entity Types

Both people and organizations.

## Key Matching Criteria

- **Names and aliases** (High priority): account for spelling variations and alternate scripts.
- **Identifiers** (High priority): some are partial or missing.
- **Addresses and contact details** (Medium priority): account for older or shared details without merging distinct people.
- **Ownership and officer relationships** (High priority): connect business records to owners, directors, principals, and parent companies.

## Success Criteria

- Build a unified customer view while keeping similar-but-distinct people separate.
- Connect business ownership and control records to resolved customers.
- Present screening results as evidence-backed leads, then compare them with the supplied answer key and report differences.

> "Compare the results with `actual_truthset_key.csv` and report any differences."

## Desired Output

**Format**: Master list; Reports
**Use case**: One-time evaluation; operational frequency not specified
**Integration**: Standalone

> "Each match is shown as a lead with evidence, not a verdict."

## Integration Requirements

**Downstream systems**: None specified
**Integration method**: Standalone
**Systems mentioned**: None

> "No."

## Deployment Target

Local / on-premises. Category: Local. Development proceeds locally first; infrastructure is a production follow-up covered by the graduation production project and migration checklist.

## Timeline

**Target completion**: Not specified
**Key milestones**: Complete the bootcamp workflow and compare the results with the supplied answer key.

## Notes

Use the source labels described in the brief. Preserve record values and the specified attribute names; compare results with the answer key only after loading.

> "Apply the labels below. Change labels only. Keep every record value, `RECORD_ID` and Senzing attribute name as it is."
