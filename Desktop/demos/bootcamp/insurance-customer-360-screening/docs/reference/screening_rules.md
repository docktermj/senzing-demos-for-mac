# Screening and customer 360 rules

Copied from the original business description (business-problem.md in the source demo folder) on 2026-11-14, because docs/business_problem.md says identity verification and ownership screening "follow the rules in the original description". The query programs in src/query/ implement these rules.

## What the customer 360 needs

One profile per real customer, showing:

- every P&C policy and every life or annuity contract
- total P&C written premium, life face amount and annuity value
- household and ownership links to other customers
- the duplicates inside each system
- the cross-sell gaps: P&C but no life or annuity (a life prospect), or life or annuity but no P&C
  policy (a P&C prospect)

## What screening and due diligence looks for

The screening that matters differs by line of business: every line must check sanctions, P&C lives
and dies by past fraud, and PEPs matter mostly where money goes into a life or annuity contract.

1. **Sanctions.** A customer who resolves to a sanctions entry is **Prohibited**: we cannot issue a
   policy or pay a claim to them. Block, and report to OFAC.
2. **SIU history.** A customer who resolves to an SIU case. Substantiated, or Referred to Law
   Enforcement / DOI, is **High**: decline at underwriting and refer open claims to the SIU. Open or
   Unsubstantiated is **Medium**.
3. **PEPs.** **High** only when the customer holds a life or annuity contract (enhanced due
   diligence on the source of funds). For a P&C-only customer it is informational (**Low**).
4. **Possible matches.** A customer who might be on either list, but not closely enough to be sure.
   **Medium** (**Low** for a PEP on a P&C-only customer): an analyst decides true match or false
   positive. If the customer already has a hit, it folds into that alert.
5. **Identity verification.** A customer whose SSN lines up with a credit bureau file while their
   date of birth or name conflicts with it. Several customers on one bureau file is a synthetic
   identity signal and **High**; one customer is **Medium**.
6. **Ownership screening.** A business customer whose owners or officers, directly or through its
   parent company, are on either list. Sanctioned owners holding 50% or more make the business
   **Prohibited** (the OFAC 50% rule); other listed owners follow the rules above. Subsidiaries roll
   up into the parent's alert, which takes the most severe rating in the group.

Every alert must come with its evidence: the records, the match key or disclosed relationship, and
the list entry or SIU case behind it.

## Innocent explanations we don't want flagged as hits

- common names (we have several John and Robert Smiths)
- households sharing an address
- people who have moved

A name-only match goes to an analyst as a possible match. It is never a hit by itself.

## Keep the two views apart

- The customer 360 never shows SIU, screening list, credit bureau or business registry data. Where
  one of those records is part of a customer, it says only that a record outside the customer
  systems exists.
- The screening view never shows premiums, face amounts or account values.

