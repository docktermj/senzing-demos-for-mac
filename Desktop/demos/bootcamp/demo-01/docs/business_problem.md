# Business Problem Statement

> 🤖 Bootcamp-generated business case

See [data architecture and data flow diagrams](data_architecture.md).

**Date**: 2026-10-06
**Project**: test-claude-26
**Design Pattern**: Custom (bootcamp-generated scenario)

## Problem Description

A retailer holds the same customers across several systems under different spellings, formats and levels of completeness, so it cannot get a trustworthy customer count or see one customer's full activity.

## Use Case Category

Customer 360

## Design Pattern Reference

None selected.

## Data Sources

1. **CRM_CONTACTS**: Synthetic CRM export / ~4,000 records / People / Static snapshot generated for the bootcamp / Local file under `data/raw/`. The name is a single full-name field and the address is free text.
2. **WEB_ORDERS**: Synthetic e-commerce orders export / ~4,500 records / People / Static snapshot generated for the bootcamp / Local file under `data/raw/`. The name is split into parts and the address is split into street, city, state and postal code.
3. **SUPPORT_TICKETS**: Synthetic support-ticket export / ~1,500 records / People / Static snapshot generated for the bootcamp / Local file under `data/raw/`. Records are sparse (many with no address or phone) with misspellings and nicknames.

## Entity Types

People

## Key Matching Criteria

- **Name** (High priority): the main attribute shared by all three sources, but written differently in each.
- **Email** (High priority): a strong identifier where present.
- **Phone** (Medium priority): useful corroboration, but missing from many support-ticket records.
- **Address** (Medium priority): free text in one source and parts in another, so it needs mapping before it can be compared.

Quality concerns: missing and off-pattern values are spread across all three sources, and the sources do not share the same shape for names and addresses.

## Success Criteria

- Report the number of resolved customers against the roughly 10,000 input records, so the duplication across systems is visible.
- Show cross-source overlap: which customers appear in more than one source.
- Be able to explain why any two records were, or were not, resolved to the same customer.

## Desired Output

**Format**: Master list, API, Reports
**Use case**: One-time local load for the bootcamp  **Integration**: Standalone

## Integration Requirements

**Downstream systems**: none (`integration_targets` is empty in `config/bootcamp_preferences.yaml`)

## Deployment Target

**Platform**: Local / on-premises  **Category**: Local. Development proceeds locally first; infrastructure is a production follow-up covered by the graduation production project and migration checklist.

## Timeline

**Target completion**: not specified  **Key milestones**: the bootcamp modules

## Notes

- The data is synthetic and generated for the bootcamp; no real customer data is involved.
- The scenario's roughly 10,000 records exceed the built-in evaluation capacity, so Data collection checks licensing before any load.
