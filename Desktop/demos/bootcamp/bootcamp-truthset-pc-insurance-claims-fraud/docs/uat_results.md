# Module 6 User Acceptance Testing

**Date:** 2026-11-11
**Scope:** Self-directed review of the loaded synthetic insurance-fraud data. The business case is fictional and has no real stakeholders, so no external user testing or sign-off was possible.

## Load and reconciliation checks

The SDK-backed validation reconciled 162 loaded records to 90 resolved entities. Source counts are POLICYHOLDERS 120/120, VENDOR_REGISTRY 25/25, and SIU_WATCHLIST 17/17. The VENDOR_REGISTRY 22-to-25 difference is expected: its mapping emits three deduplicated embedded employer organizations, as documented in `docs/mapping/VENDOR_REGISTRY_mapping_spec.json`. All three loads had zero failures; redo processing completed with an empty queue.

## Cross-source sample

All 15 cross-source entities were reviewed against the mapped source records, exceeding the requested 10-entity UAT sample. Eight had corroborating identity or organization evidence in addition to a shared name; seven require human review because the evidence was sparse, conflicting, or depended on a potentially generic organization name. These are provisional evidence-review dispositions, not adjudicated truth labels or a measured accuracy rate.

| Entity | Sources | Evidence observed | UAT disposition |
|---|---|---|---|
| 200001 | POLICYHOLDERS, VENDOR_REGISTRY | Organization-name variants and shared postal/country details | Corroborated for sample review |
| 200002 | POLICYHOLDERS, VENDOR_REGISTRY | Policy records share name, DOB, address, and national-ID features; vendor record aligns on person name and DOB | Corroborated for sample review |
| 200003 | POLICYHOLDERS, VENDOR_REGISTRY | Policy records share name, DOB, address, and national-ID features; vendor record aligns on person name and DOB | Corroborated for sample review |
| 200004 | POLICYHOLDERS, VENDOR_REGISTRY | Organization-name variants and postal code align, but organization names and street details differ | Human review |
| 200005 | SIU_WATCHLIST, VENDOR_REGISTRY | Transliteration variants, shared address, and phone | Corroborated for sample review |
| 200006 | POLICYHOLDERS, SIU_WATCHLIST, VENDOR_REGISTRY | Transliteration variants and shared phone across policyholder/watchlist records; vendor record adds less direct evidence | Corroborated for sample review |
| 200007 | POLICYHOLDERS, VENDOR_REGISTRY | Organization name and phone align, but names differ | Human review |
| 200008 | POLICYHOLDERS, VENDOR_REGISTRY | Similar organization names and postal details, with different address details | Human review |
| 300009 | POLICYHOLDERS, VENDOR_REGISTRY | Similar organization names and country, but street details differ | Human review |
| 300010 | POLICYHOLDERS, VENDOR_REGISTRY | Similar organization names, but address and phone details conflict | Human review |
| 400001 | POLICYHOLDERS, SIU_WATCHLIST | Driver-license number aligns, while person-name and street-address details differ | Human review |
| 400004 | POLICYHOLDERS, SIU_WATCHLIST | Five-record entity has conflicting names, DOBs, and address details across records | Human review |
| 400006 | POLICYHOLDERS, SIU_WATCHLIST | Shared address, email, and SSN features | Corroborated for sample review |
| 400007 | POLICYHOLDERS, SIU_WATCHLIST | Shared DOB and surname across records with differing first names | Corroborated for sample review |
| 400010 | POLICYHOLDERS, SIU_WATCHLIST | Watchlist records share DOB, address, and email; policyholder records in the entity contain differing identity details | Corroborated for sample review |

**Result:** 8/15 (53.3%) had corroborating evidence in this sample; 7/15 (46.7%) need human follow-up. “Corroborated” means only that this sample showed multiple aligned features; it does not assert ground-truth identity.

## Single-source spot checks

Eight multi-record entities from the export were checked against their source records. Evidence was mixed: entity 500001 had overlapping name, DOB, phone, and email evidence across some records; entity 500009 had aligned SSN/DOB evidence for part of the group but conflicting names/DOBs elsewhere; entity 600002 had aligned name and address evidence. Entities 300001, 300003, 300005, 300007, and 300011 also contained feature conflicts or sparse agreement and remain review candidates. The sample flags potential follow-up, not confirmed false merges.

## UAT outcome and limitations

- Records reconciled: 162/162 across three sources.
- Cross-source entities in the full export: 15; cross-source sample reviewed: 15.
- Cross-source sample with corroborating evidence: 8/15; human follow-up: 7/15.
- Single-source multi-record sample reviewed: 8 entities; mixed evidence, with cases retained for follow-up.
- External business-user testing and sign-off: not applicable; no real stakeholders are recorded for this fictional scenario.
- Precision, recall, F1, and absolute match accuracy: not measured. No adjudicated truth labels exist.
- The source quality scores remain below 70% (POLICYHOLDERS 51.5%, VENDOR_REGISTRY 57.0%, SIU_WATCHLIST 56.5%); SIU_WATCHLIST was used as-is at the bootcamper's direction.

Follow up on the seven cross-source review cases and the single-source candidates before treating these matches as operational decisions. Potential watchlist connections are leads for investigation, not fraud determinations.
