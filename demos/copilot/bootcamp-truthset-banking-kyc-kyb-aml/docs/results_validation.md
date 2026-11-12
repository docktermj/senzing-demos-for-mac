# Data Processing Results Validation

## Reconciled load and export totals

The full-load summaries reconciled all 159 attempts to 159 successful loads with zero failures.
The SDK entity export also contained 159 records, matching the load summaries and source registry.

| Data source | Expected records | Attempted | Loaded | Failed | Export records | Entities containing source records | Compression |
|---|---:|---:|---:|---:|---:|---:|---:|
| AML_WATCHLIST | 17 | 17 | 17 | 0 | 17 | 16 | 5.9% |
| BUSINESS_REGISTRY | 22 | 22 | 22 | 0 | 22 | 22 | 0.0% |
| BANK_CUSTOMERS | 120 | 120 | 120 | 0 | 120 | 91 | 24.2% |
| **Total** | **159** | **159** | **159** | **0** | **159** | **120 overall** | — |

Per-source entity counts overlap for cross-source entities; they therefore sum to 129 while the
repository contains 120 distinct entities.

## Entity and relationship statistics

- 120 entities contain 159 records; 30 entities have multiple records.
- Entity-size distribution: 90 singletons, 24 pairs, and 6 entities with 3–5 records; none have
  more than 5 records.
- Nine entities span multiple sources: 1 AML_WATCHLIST + BANK_CUSTOMERS entity and 8
  BANK_CUSTOMERS + BUSINESS_REGISTRY entities. No AML_WATCHLIST + BUSINESS_REGISTRY entity was
  present.
- The export contained 153 unique related-entity pairs: 49 `POSSIBLY_SAME`, 90
  `POSSIBLY_RELATED`, and 14 `DISCLOSED`.
- The first exported row carried both `RESOLVED_ENTITY` and `RELATED_ENTITIES`, so the single
  export pass supplied both per-record and relationship match keys. Relationship pairs were
  counted once, using the lower entity ID's row as the canonical view. Mirrored disclosed-role
  notation was directional and was not counted twice.

Source-profile context: the original BANK_CUSTOMERS profile reported 55.1% unique last names,
75.4% unique birth dates, 94.2% unique phone values, and 73.3% unique email values. The
AML_WATCHLIST profile reported 88.2% unique birth dates. These profiles provide context for the
observed compression rates; they are not ground-truth accuracy targets.

## Match-key suppressor audit

Suppressors were parsed from signed match keys after removing parenthesized relationship roles and
respecting escaped separators. Each bucket uses its own denominator; per-record and relationship
keys describe different outcomes and are not pooled.

| Bucket | Keys reviewed | Keys with at least one suppressor | Share | Most frequent suppressed feature |
|---|---:|---:|---:|---|
| Single-source per-record | 25 | 1 | 4.0% | `GENDER`: 1/25 |
| Cross-source per-record | 14 | 0 | 0.0% | None |
| Relationships (deduplicated pairs) | 153 | 131 | 85.6% | `DOB`: 124/153 (81.0%) |

Other relationship suppressors were `GENDER` (5), `SSN` (5), `PASSPORT` (4),
`REGISTRATION_DATE` (3), `GENERATION` (2), and `LEI_NUMBER` (1). The high relationship share is
a finding about candidate pairs that were not merged due to conflicting features; it is not a
per-record over-merge rate and does not by itself indicate poor match accuracy.

Top cross-source per-record principles were `CNAME_CFF +NAME+ADDRESS` (4 records),
`CNAME_CFF_CEXCL +NAME+DOB+ADDRESS` (2), and `SNAME_SSTAB +NAME+DOB` (2). The full export and
sampled explanations are retained under `logs/module6-validation-8752739269744880264/`.

## Record lookups, possible matches, and construction history

- Six record-key lookups (two per source) matched the corresponding entity IDs in the export;
  no lookup failed. This verifies reader consistency, not correctness against a truth set.
- The possible-match queue contained 49 `POSSIBLY_SAME` pairs. Five were sampled for SDK
  explanations; all five calls succeeded. The examples include close names/addresses or a shared
  driver-license value alongside a conflicting birth date. See `docs/uat_results.md`.
- The how-state audit called the SDK for all 30 multi-record entities. All 30 responses contained
  `FINAL_STATE`, `NEED_REEVALUATION`, and `VIRTUAL_ENTITIES`; none had a non-zero
  `NEED_REEVALUATION` value or more than one virtual entity. **Outcome: no finding, checked 30 of
  30; 0 unsettled.**
- Four larger merged entities are candidates for manual review due to conflicting names or
  birth-date values: 200005, 700001, 700030, and 700050. They are not confirmed false positives.
  A targeted review of entity 200005 found no transformation defect; the unresolved identity
  question and supporting match keys are documented in `docs/uat_results.md`.

## Accuracy limits and stakeholder validation

`actual_truthset_key.csv` is absent from the checkout. Precision, recall, F1, and an absolute
match-accuracy percentage therefore could not be measured. `docs/uat_results.md` records the
self-directed review and its evidence. The scenario records no real business stakeholders, so
stakeholder sign-off was not available.

## Audit artifacts

The SDK audit and its JSONL outputs are in
`logs/module6-validation-8752739269744880264/`. The audit reads entity data through the Java SDK;
it does not query the database with SQL.
