# Business Problem: Customer 360 and KYC/KYB for a Retail Bank

## Who we are

We are a US regional bank with retail and business customers. All data in this exercise is
synthetic test data: there are no real people in it.

## The problem

Our customers live in two systems that don't share customer numbers. The core banking system holds
deposit customers; the card platform holds cardholders. Neither system knows when a depositor is
also a cardholder, or when it holds the same customer twice.

Two teams ask "who is this?" for opposite reasons, and today that means two projects, two vendors
and two answers:

- **Relationship managers** want a **customer 360**: everything a customer holds with us.
- **Compliance** wants **KYC and KYB**: whether we should hold anything for that customer at all.

We want to resolve our customer data **once** and serve both teams from the same resolved entities.

## Our data

Five sources, 248 records in total:

1. **CORE_BANKING** (97 records): the core banking system's customer information file (CIF)
   extract. Deposit accounts (checking, savings, money market, CDs) with ownership type, status,
   balance, branch and relationship tier.
2. **CARD_SERVICES** (82 records): the card platform's cardholder master. Card account, last four
   digits of the card, primary or authorized user, product, credit limit, balance, status (including
   charged off) and delinquency.
3. **CREDIT_BUREAU** (30 records): credit header records, which we use to verify identity.
4. **BUSINESS_REGISTRY** (22 records): business registrations with their owners, officers and parent
   companies, including ownership percentages.
5. **WATCHLIST** (17 records): our screening list. `LIST_TYPE` is Sanctions, PEP (politically exposed
   person) or Internal Fraud (customers we exited for fraud).

The data is hard on purpose: transliterated names, nicknames, missing identifiers, people who share
an SSN, and businesses whose only link to a sanctioned person is an ownership record in the
registry.

## What the customer 360 needs

One profile per real customer, showing:

- every deposit account and every card
- total deposits, card credit and card balance
- household and ownership links to other customers
- the duplicates inside each system
- the cross-sell gaps: deposits but no card, or a card but no deposit account

## What KYC/KYB looks for

1. **Screening hits.** A customer who resolves to a watchlist entry. Sanctions is **Prohibited**;
   Internal Fraud and PEP are **High**.
2. **Possible screening hits.** A customer who might be a watchlist entry, but not closely enough to
   be sure. **Medium**: an analyst decides true match or false positive.
3. **Identity verification.** A customer whose SSN lines up with a credit bureau file while their
   date of birth or name conflicts with it. Several customers on one bureau file is a synthetic
   identity signal and **High**; one customer is **Medium**.
4. **Beneficial ownership (KYB).** A business customer whose owners or officers, directly or through
   its parent company, are on the watchlist. Sanctioned owners holding 50% or more make the business
   **Prohibited** (the OFAC 50% rule); anything else is **High**. Subsidiaries roll up into the
   parent's alert.

Every alert must come with its evidence: the records, the match key or disclosed relationship, and
the watchlist entry behind it.

## Innocent explanations we don't want flagged as hits

- common names (we have several John and Robert Smiths)
- households sharing an address
- people who have moved

A name-only match goes to an analyst as a possible hit. It is never a hit by itself.

## Keep the two views apart

- The customer 360 never shows watchlist, credit bureau or business registry data. Where one of
  those records is part of a customer, it says only that a record outside the customer systems
  exists.
- The compliance view never shows balances or credit limits.

## What success looks like

- One resolution of all five sources, serving both teams.
- The 179 customer records (CORE_BANKING plus CARD_SERVICES) collapse into the real customers behind
  them, and we can see how many hold both a deposit account and a card.
- An analyst's KYC/KYB queue that catches what name screening alone misses, such as a business owned
  by a sanctioned person, with no false hits for common names.
- A web app with three tabs, **Overview**, **Customer 360** and **KYC / KYB**, where analysts can see
  why Senzing matched or related records and record their dispositions (True match, False positive,
  Escalate to EDD). Dispositions are stored by the app, never written to Senzing.
