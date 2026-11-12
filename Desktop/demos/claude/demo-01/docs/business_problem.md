# Business Problem Statement

> 🤖 Bootcamp-generated business case

**Date**: 2026-11-09
**Project**: test-claude-plugin-35
**Design Pattern**: Custom

## Problem Description

Customer records are fragmented and duplicated across a CRM, a billing system, and support tickets, each with inconsistent name/address formats and missing fields, making it hard to get a single, trustworthy view of each customer.

See [data architecture and data flow diagrams](data_architecture.md).

## Use Case Category

Customer 360

## Design Pattern Reference

Not applicable: no design pattern was selected for this scenario.

## Data Sources

1. **CRM_SYSTEM**: Synthetic / ~4,000 records / People / Ongoing / Generated
2. **BILLING_SYSTEM**: Synthetic / ~4,000 records / People / Ongoing / Generated
3. **SUPPORT_TICKETS**: Synthetic / ~2,000 records / People / Ongoing / Generated

## Entity Types

People

## Key Matching Criteria

- **Name** (High priority): appears as one joined field in CRM, as separate first/last fields in Billing, and as a (sometimes mistyped) name in Support — the primary basis for linking records across sources.
- **Address** (Medium priority): free-text in CRM, parsed into components in Billing — needed to disambiguate same-name customers.
- **Phone** (Medium priority): inconsistent formats, sometimes missing — a secondary corroborating attribute.
- **Email** (Medium priority): present in CRM and Support, sometimes missing — another corroborating attribute, occasionally mistyped in Support.

## Success Criteria

- Correctly identify duplicate customer records across CRM, Billing, and Support systems despite inconsistent name/address formats and missing fields.
- Produce a single, unified view of each customer for use in reporting.
- Demonstrate the resolution working end-to-end on the generated, deliberately messy dataset.

## Desired Output

**Format**: Master list, API, Reports
**Use case**: Ongoing  **Integration**: Standalone

## Integration Requirements

**Downstream systems**: None — standalone for now.
**Integration method**: Not applicable.
**Systems mentioned**: None (`integration_targets: []` in `config/bootcamp_preferences.yaml`).

## Deployment Target

**Platform**: Not stated
**Category**: Local
**Note**: development proceeds locally first; infrastructure is a production follow-up covered by the graduation production project and migration checklist.

## Timeline

**Target completion**: Not applicable — bootcamp exercise.
**Key milestones**: Complete each bootcamp module in sequence.

## Notes

This is a bootcamp-generated scenario, sized to roughly 10,000 records total across the three sources, deliberately built with cross-source mapping divergence (joined vs. parsed names; free-text vs. parsed addresses) and data-quality variation (missing and off-pattern values) so Data Quality, Mapping, and Transformation has real work to do.
