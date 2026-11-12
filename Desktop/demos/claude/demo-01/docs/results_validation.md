# Results Validation — Data processing

**Date:** 2026-11-09

## Load

| Source | Records loaded | Input records | Reconciliation | Entities containing it | Compression |
|---|---|---|---|---|---|
| BILLING_SYSTEM | 4,000 | 4,000 | matches input | 3,923 | 1.9% |
| CRM_SYSTEM | 4,000 | 4,000 | matches input | 3,702 | 7.4% |
| SUPPORT_TICKETS | 2,000 | 2,000 | matches input | 1,688 | 15.6% |

- **Total:** 10,000 records → **6,671 entities**; 0 failed records; redo queue drained to empty after each phase.
- **Cross-source:** 2,191 entities span 2+ systems, 451 span all three. Pairs: CRM_SYSTEM–BILLING_SYSTEM 1,914; CRM_SYSTEM–SUPPORT_TICKETS 728; BILLING_SYSTEM–SUPPORT_TICKETS 451.
- **Entity sizes:** 1 record 4,211; 2 records 1,750; 3–5 records 710; none larger.
- **Relationships:** 737 possibly same, 10 possibly related.
- **Top match keys:** +NAME+ADDRESS+PHONE (1,064), +NAME+EMAIL (921), +NAME+ADDRESS (764), +NAME+PHONE (263), +EXACTLY_SAME (157).

## Match quality

- UAT 20/20 cross-source entities correct (100%); per-record match accuracy 48/52 (92%) — details in `docs/uat_results.md`.
- Against the scenario's answer key: precision 100.0%, recall 80.9%, F1 89.5%; **0 false merges**; 646 people split (false negatives), typically where the only shared evidence is a name (Senzing links these as possibly same rather than merging).
- Business validation: bootcamp-generated scenario, no stakeholders; UAT self-directed. Sign-off: Bootcamper-directed expectation check agreed on 3 of 3 examples.
- Issues and resolution plan: no critical issues. The possibly-same links (737) are the review queue for the master list; adding a shared identifier (for example a customer number common to the systems) would turn many into merges.

## Match-key audit

- Outcome: **no finding.** The export carries RELATED_ENTITIES (reader proven on entity 300005).
- Suppressed features: single-source record keys 0 of 269; cross-source record keys 0 of 3,060; relationships 0 of 747.

## How-state audit

- Outcome: **no finding** — checked 2,460 of 2,460 multi-record entities.
- 0 unsettled.
