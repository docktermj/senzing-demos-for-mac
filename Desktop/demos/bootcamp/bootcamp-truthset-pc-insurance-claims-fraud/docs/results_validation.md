# Entity Resolution Results Validation

**Date:** 2026-11-11
**Environment:** Local Java SDK and SQLite repository
**Data:** Synthetic demo records mapped to POLICYHOLDERS, VENDOR_REGISTRY, and SIU_WATCHLIST

## Reconciled load results

| Source | Collected records | Mapped/load input | Loaded | Outcome |
|---|---:|---:|---:|---|
| POLICYHOLDERS | 120 | 120 | 120 | Reconciled; stage 1 and stage 2 passed |
| VENDOR_REGISTRY | 22 | 25 | 25 | Reconciled expected delta; stage 1 passed and stage 2 cites three deduplicated embedded employer organizations in `docs/mapping/VENDOR_REGISTRY_mapping_spec.json` |
| SIU_WATCHLIST | 17 | 17 | 17 | Reconciled; stage 1 and stage 2 passed |
| **Total** | **159 collected** | **162 mapped/load records** | **162** | All attempted records loaded; zero load failures |

The sequential load order was VENDOR_REGISTRY, SIU_WATCHLIST, then POLICYHOLDERS. Redo processing completed 54 records after the VENDOR_REGISTRY load and another 5 after the multi-source load; both queues were drained with no failures or retryable records.

## Entity and relationship statistics

The SDK export and record-ID queries reconcile to 162 records and 90 entities. Entity sizes: 38 singletons, 41 pairs, 10 entities with 3–5 records, and 1 entity with 6–10 records. The export contains 15 cross-source entities. Source-pair counts are POLICYHOLDERS/VENDOR_REGISTRY 9, POLICYHOLDERS/SIU_WATCHLIST 6, and SIU_WATCHLIST/VENDOR_REGISTRY 2; a tri-source entity contributes to multiple pair counts.

The export reported 70 unique relationships after deduplicating entity pairs: 23 DISCLOSED, 27 POSSIBLY_RELATED, and 20 POSSIBLY_SAME. Ten possible-match pairs were sampled and explained through the SDK; most sampled explanations relied on name features. These are candidate links for review, not confirmed missed matches.

## Match-key audit

| Bucket | Comparisons | With suppressors | Share | Outcome |
|---|---:|---:|---:|---|
| Single-source per-record | 47 | 1 | 2.1% | Finding: one GENDER suppressor |
| Cross-source per-record | 24 | 0 | 0% | No suppressors found |
| Relationships (deduplicated) | 70 | 15 | 21.4% | Finding: DOB 13, SSN 3, GENERATION 2, PASSPORT 1 |

Per-record suppressors describe records that resolved despite feature disagreement. Relationship suppressors describe candidate entities the engine did not resolve together despite disagreement; this is a distinct review signal and is not, by itself, evidence of poor accuracy. Counts by feature can overlap within a relationship.

## How-state audit

**Outcome: no finding.** Checked 52 of 52 multi-record entities; 0 unsettled entities and 0 unchecked. No flagged entities.

## UAT and accuracy limits

The self-directed UAT reviewed all 15 cross-source entities and 8 single-source multi-record entities. Eight cross-source samples had corroborating evidence; seven need human follow-up. The single-source sample had mixed evidence, including conflicting features in some entities. Entity-level evidence and dispositions are recorded in `docs/uat_results.md`.

These are proxy review results, not adjudicated labels. Absolute match accuracy, precision, recall, and F1 are **not measured**, because the project has no ground-truth assignments. No stakeholder sign-off was obtained or applicable: `docs/business_problem.md` describes a fictional scenario with no real stakeholders.

## Issues and follow-up

- Review cross-source entities 200004, 200007, 200008, 300009, 300010, 400001, and 400004 before operational use.
- Continue reviewing the single-source candidates identified in `docs/uat_results.md`; the sample does not establish false merges.
- Investigate sampled POSSIBLY_SAME candidates with name-only explanations when additional source evidence becomes available.
- Keep potential SIU connections as investigation leads, not fraud conclusions.
- Revisit quality gaps when richer source data or adjudicated labels become available; do not infer missing values.

## Match-quality gate summary

The cross-source UAT evidence threshold was not met in this sample (8/15 corroborated, 53.3%), and absolute match accuracy is unavailable. Recommend returning to Data Quality, Mapping, and Transformation to review the flagged cases and validate the mapping choices before operational use. The relationship match-key audit also found suppressors, while the cross-source per-record bucket had none and the how-state audit had no finding. These proxy checks do not justify a claim of measured accuracy.
