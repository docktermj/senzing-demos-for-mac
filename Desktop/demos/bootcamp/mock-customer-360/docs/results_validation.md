# Results validation — Data processing

Baseline for Query, Visualize and Discover. All figures come from one export of the repository (`src/validate/export_and_audit.java`, MCP export pattern with `SZ_EXPORT_DEFAULT_FLAGS`) and are evaluated by `src/validate/evaluate_results.java`; details in `data/results/evaluation.json`.

## Load

| Source | Load input | Loaded | Failed | Reconciliation |
|---|---|---|---|---|
| CRM_CONTACTS | 4,000 | 4,000 | 0 | pass (stage 1 and stage 2 equal) |
| WEB_ORDERS | 4,500 | 4,500 | 0 | pass |
| SUPPORT_TICKETS | 1,500 | 1,500 | 0 | pass |
| **Total** | **10,000** | **10,000** | **0** | export holds exactly 10,000 records |

Redo: 0 after the first source, 39 after the other two; queue empty.

## Entities

- 10,000 records resolved into **7,134 entities** (ground truth: 7,000 invented customers).
- Per source: CRM_CONTACTS 4,000 records → 3,700 entities (7.5% compression; the ~300 second contacts); WEB_ORDERS 4,500 → 4,201 (6.6%; repeat orders); SUPPORT_TICKETS 1,500 → 1,500 (0%).
- **2,090 entities span 2 or more sources.** Source pairs: CRM × WEB 1,489; WEB × SUPPORT 522; CRM × SUPPORT 433.
- Entity sizes: 4,739 single-record, 1,974 two-record, 421 with 3–5 records, none larger.
- Top match principles: +NAME+ADDRESS+PHONE+EMAIL (806), +NAME+PHONE+EMAIL (609), +NAME+EMAIL (578), +NAME+ADDRESS+EMAIL (529).
- Relationships (deduplicated): 4,567 POSSIBLY_SAME (all on +NAME), 25 POSSIBLY_RELATED.

## Match quality against ground truth

| Measure | Value |
|---|---|
| Pairwise precision | **99.9%** (3,388 of 3,390 merged pairs correct) |
| Pairwise recall | **95.1%** (3,388 of 3,561 true pairs merged) |
| Pairwise F1 | 97.5% |
| Over-merged entities | 2 (see `docs/uat_results.md`) |
| Customers split across entities | 136 (127 relationships link their pieces, 122 of them POSSIBLY_SAME +NAME) |
| Multi-source customers fully resolved | 2,066 of 2,200 (93.9%) |

Of the 4,567 POSSIBLY_SAME +NAME relationships, 4,445 link *different* customers who share a name (the deliberate hard negatives); Senzing correctly kept them apart.

## UAT

Self-directed (bootcamp-generated scenario): 10 of 10 cross-source entities correct. See `docs/uat_results.md`.

## Sign-off

No real stakeholders: the scenario is bootcamp-generated. Validated against ground truth instead; status **accepted (self-validated)**.

## Match-key audit

- Reader check: the export rows carry `RELATED_ENTITIES`, and 4,592 deduplicated relationship match keys were read.
- Single-source per-record: 322 comparisons, 0 suppressors. Cross-source per-record: 2,544 comparisons, 0 suppressors. Relationship: 4,592 comparisons, 0 suppressors.
- **Outcome: no finding.** No feature detracted from any merge or relationship.

## How-state audit

- Outcome: **no finding** — checked 2,395 of 2,395 multi-record entities.
- 0 unsettled (no entity with NEED_REEVALUATION set or more than one virtual entity).
