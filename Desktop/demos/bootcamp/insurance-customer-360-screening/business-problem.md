# Business Problem: Customer 360 and Screening for an Insurer

## Who we are

We are a US insurer selling personal and commercial property and casualty (P&C) cover and life and
annuity products. All data in this exercise is synthetic test data: there are no real people in it.

## The problem

Our customers live in two policy systems that don't share customer numbers. The P&C policy
administration system holds our insureds; the life and annuity administration system holds our
contract parties. We run the two lines as separate businesses, so neither system knows when a
homeowners customer also owns one of our annuities, or when it holds the same customer twice.

Two teams ask "who is this?" for opposite reasons, and today that means two projects, two vendors
and two answers:

- **Distribution and service** want a **customer 360**: every policy and contract a customer holds
  with us.
- **Compliance and the SIU** (special investigations unit) want **screening and due diligence**:
  every customer checked against sanctions, PEP and fraud history, identities verified, and business
  customers' owners and officers known.

We want to resolve our customer data **once** and serve both teams from the same resolved entities.

## Our data

Six sources, 248 records in total:

1. **PC_POLICY_ADMIN** (104 records): the P&C policy administration system. Personal lines (auto,
   homeowners, renters, umbrella) and commercial lines (businessowners, commercial auto, general
   liability, workers compensation), with insured role, written premium, term dates, status (in
   force, lapsed, cancelled) and producer.
2. **LIFE_ANNUITY_ADMIN** (75 records): the life and annuity administration system. Term, whole and
   universal life, key person cover and annuities, with party role, face amount, premium or account
   value, and status.
3. **CREDIT_BUREAU** (30 records): credit header records, which we use to verify identity.
4. **BUSINESS_REGISTRY** (22 records): business registrations with their owners, officers and parent
   companies, including ownership percentages.
5. **SIU_CASES** (13 records): our special investigations unit's case file. `FRAUD_TYPE`,
   `ROLE_IN_CASE` (claimant, insured, witness, medical provider, attorney), `LINE_OF_BUSINESS` and
   `CASE_DISPOSITION` (Substantiated, Referred to Law Enforcement / DOI, Open, Unsubstantiated).
6. **SANCTIONS_PEP** (4 records): our screening vendor's list. `LIST_TYPE` is Sanctions (OFAC SDN)
   or PEP (politically exposed person).

The data is hard on purpose: transliterated names, nicknames, missing identifiers, people who share
an SSN, and businesses whose only link to a sanctioned person is an ownership record in the
registry.

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

## What success looks like

- One resolution of all six sources, serving both teams.
- The 179 customer records (PC_POLICY_ADMIN plus LIFE_ANNUITY_ADMIN) collapse into the real
  customers behind them, and we can see how many hold both P&C cover and a life or annuity contract.
- An analyst's screening queue that applies each line of business's rules to the same resolved
  people and catches what name screening alone misses, such as a business owned by a sanctioned
  person, with no false hits for common names.
- A web app with three tabs, **Overview**, **Customer 360** and **Screening & Due Diligence**, where
  analysts can see why Senzing matched or related records and record their dispositions (True match,
  False positive, Refer to SIU). Dispositions are stored by the app, never written to Senzing.
