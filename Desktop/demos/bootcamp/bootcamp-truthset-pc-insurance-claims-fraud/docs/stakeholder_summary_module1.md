# Stakeholder Summary — Module 1

## Business Problem

A fictional U.S. property-and-casualty insurer wants a trusted view of policyholders and their connections to claim-paid businesses and SIU watchlist subjects. The goal is to support faster legitimate-claim handling and give investigators evidence-backed leads, not automated fraud verdicts.

## Proposed Approach

The proposed entity-resolution project will consolidate repeated policyholder records, examine relationships to vendor owners and parent companies, and compare relevant people with SIU watchlist entries. Its planned outputs are a clean master list, an API, and reports. The solution is expected to run locally / on-premises, with no downstream integrations identified.

Senzing documentation describes matching records to entities, discovering and managing relationships, and revisiting previous decisions as data is processed ([Entity Resolution Processes](https://senzing.com/er-processes-published/?nonitro)). Its Buyer’s Guide identifies risk and fraud detection and customer data management as entity-resolution use cases ([Entity Resolution Buyer’s Guide](https://senzing.com/entity-resolution-buyers-guide/?nonitro)). Whether this approach finds the intended links in this specific dataset remains to be validated; the brief's example relationships are not treated as loaded results.

## Data Sources

| Source | Records | Description |
|---|---:|---|
| `POLICYHOLDERS` | 120 | Policy and claims records for people and businesses |
| `VENDOR_REGISTRY` | 22 | Claim-paid businesses and related ownership information |
| `SIU_WATCHLIST` | 17 | Claims-fraud suspects and sanctioned or politically exposed persons |

The brief describes 159 synthetic records in total. It is marked as a draft requiring reviewer approval before loading; no data has been loaded.

## Key Findings

- The case spans both people and organizations and combines customer-view, vendor-relationship, and fraud-screening needs.
- The brief describes duplicate and variant policyholder identities, vendor ownership relationships, and watchlist links as investigation leads.
- Source refresh frequency, access methods, matching priorities, investigation cadence, and quantitative accuracy or time targets remain undetermined.
- Any candidate matches must be reviewed as evidence-backed leads, not treated as conclusions of fraud.

## Next Steps

1. Obtain reviewer approval before loading the synthetic source data.
2. Continue through SDK setup and system verification.
3. Then collect, assess, map, and process approved data before querying and validating the resulting entities and relationships.
4. Establish baseline and target measures for claim-handling time, SIU lead usefulness, false-positive review effort, and ongoing refresh needs.

## ROI Considerations

Potential benefits to measure include faster handling of legitimate claims, less manual effort to connect policyholders with claim-paid vendors, and earlier discovery of relevant watchlist relationships. No financial savings, performance baseline, or target has been quantified; those should be established before making an ROI claim.
