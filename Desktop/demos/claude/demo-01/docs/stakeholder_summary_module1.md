# Stakeholder Summary: Discover the Business Problem

> 🤖 Bootcamp-generated business case (synthetic data)

**Date**: 2026-10-06

## Problem

A retailer holds the same customers across several systems under different spellings, formats and levels of completeness, so it cannot get a trustworthy customer count or see one customer's full activity. Use case category: Customer 360.

## Approach

Use Senzing entity resolution to resolve customer records from three sources into single customer entities, then deliver a clean master list, an API and reports. Development proceeds locally first.

## Data sources

- **CRM_CONTACTS**: about 4,000 records; one full-name field, free-text address.
- **WEB_ORDERS**: about 4,500 records; parsed name parts, address parts.
- **SUPPORT_TICKETS**: about 1,500 records; sparse, with misspellings and nicknames.

All three are synthetic and generated for the bootcamp.

## Key findings

- The three sources describe names and addresses in different shapes, so mapping to Senzing's format is real per-source work.
- Missing and off-pattern values are spread across all three sources.
- No Senzing sample dataset fits a customer-style scenario, so the data is synthesized.

## Next steps

1. Install and configure the Senzing SDK (Java).
2. Generate the three sources and check licensing before loading.
3. Assess quality and map each source to the Senzing Entity Specification.
4. Load, resolve, then query for the master list, API and reports.

## ROI considerations

- Success is measured by the resolved customer count against roughly 10,000 input records, cross-source overlap, and being able to explain any match.
- The Senzing docs' economic-cost article estimates the Marketing, Sales & CRM cost of mismatched identity data at $130B to $250B a year across all sectors; this scenario's own savings are not quantified.
