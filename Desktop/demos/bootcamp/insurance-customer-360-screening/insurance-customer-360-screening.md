---
title: Customer 360 and Screening for an Insurer
use_case: customer-360
difficulty: Intermediate
est_time: ~45m
video:
author: Jeff Butcher
---

# Customer 360 and Screening for an Insurer

**What you'll build:** one app that serves two teams from a single resolution of your customer data -
a customer 360 for the people who sell and service policies, and a screening and due diligence queue
for the people who keep the carrier compliant and out of fraud. **In this case** a P&C policy
administration system and a life and annuity administration system, where the same customer is two
strangers, plus a credit bureau file, a business registry, the SIU's case file and a sanctions and PEP
list. Afterwards, one profile per real customer with every policy and contract they hold, and an
analyst's queue of the sanctions hits, past fraudsters, borrowed SSNs and risky business owners
hiding among them.

**What it takes:** Intermediate · ~45 min once you're set up · runs on your machine · six data files to download and attach

![Insurance customer hub overview - 248 records from six sources resolved into 157 entities, 179 customer records into 127 real customers, 38 of them in both systems, and 18 open screening alerts](images/insurance-customer-360-screening-outcome.png)

*248 records from six sources become **157** entities. The 179 customer records are **127** real customers, **38** of them in both the P&C and the life and annuity systems without either knowing, and **18** screening alerts land in the analyst's queue.*

## Chef's Note

Insurers that sell both property and casualty and life cover usually run them as separate
businesses on separate systems, so the carrier rarely knows that its homeowners customer also owns
one of its annuities. And the screening that matters differs by line: every line must check
sanctions, P&C lives and dies by past fraud, and PEPs only really matter where money goes into a
life or annuity contract. This recipe resolves the customers once and serves both the customer 360
and the screening queue from the same entities.

It starts, like the patient fraud recipe, with a plain-English brief. Here's the one for a US
personal and commercial insurer:

> **The use case.** We are a US insurer selling personal and commercial P&C cover and life and
> annuity products. We want one resolution of our customer data to serve two teams. Distribution and
> service want a customer 360: every policy and contract a customer holds across our systems.
> Compliance and the SIU want screening and due diligence: every customer checked against sanctions,
> PEP and fraud history, identities verified, and business customers' owners and officers known.
>
> **Our data** (6 sources, 248 records, all synthetic test data):
>
> 1. **PC_POLICY_ADMIN**: the P&C policy administration system. Personal lines (auto, homeowners,
>    renters, umbrella) and commercial lines (businessowners, commercial auto, general liability,
>    workers compensation), with insured role, written premium, term dates, status (in force, lapsed,
>    cancelled) and producer.
> 2. **LIFE_ANNUITY_ADMIN**: the life and annuity administration system. Term, whole and universal
>    life, key person cover and annuities, with party role, face amount, premium or account value, and
>    status.
> 3. **CREDIT_BUREAU**: credit header records, which we use to verify identity.
> 4. **BUSINESS_REGISTRY**: business registrations with their owners, officers and parent companies,
>    including ownership percentages.
> 5. **SIU_CASES**: our special investigations unit's case file: fraud type, the person's role in the
>    case (claimant, insured, witness, medical provider, attorney), line of business and disposition.
> 6. **SANCTIONS_PEP**: our screening vendor's list of sanctioned persons (OFAC SDN) and politically
>    exposed persons (PEPs).
>
> The two policy systems each keep their own customer records, so neither knows when a P&C insured
> also owns a life contract, or when it holds the same customer twice.
>
> **What the customer 360 needs:** one profile per real customer, with every policy and contract,
> total P&C written premium, life face amount and annuity value, household and ownership links to
> other customers, the duplicates inside each system, and the cross-sell gaps (P&C but no life or
> annuity, and the reverse).
>
> **What screening and due diligence looks for:**
>
> 1. **Sanctions.** A customer who resolves to a sanctions entry is Prohibited: we cannot issue a
>    policy or pay a claim to them.
> 2. **SIU history.** A customer who resolves to an SIU case. A substantiated case, or one referred
>    to law enforcement or the state insurance department, is High: decline at underwriting and refer
>    open claims. An open or unsubstantiated case is Medium.
> 3. **PEPs.** High only when the customer holds a life or annuity contract (enhanced due diligence
>    on the source of funds). For a P&C-only customer it is informational.
> 4. **Possible matches.** A customer who might be on either list, but not closely enough to be sure.
>    Medium: an analyst decides true match or false positive.
> 5. **Identity verification.** A customer whose SSN lines up with a credit bureau file while their
>    date of birth or name conflicts with it. Several customers on one bureau file is a synthetic
>    identity signal and High; one customer is Medium.
> 6. **Ownership screening.** A business customer whose owners or officers, directly or through its
>    parent company, are on either list. Sanctioned owners holding 50% or more make the business
>    Prohibited (the OFAC 50% rule). Subsidiaries roll up into the parent's alert, which takes the
>    most severe rating in the group.
>
> **Innocent explanations we don't want flagged as hits:** common names (we have several John and
> Robert Smiths), households sharing an address, and people who have moved. A name-only match goes
> to an analyst; it is never a hit by itself.
>
> **Keep the two views apart.** The customer 360 never shows the SIU file, the screening list,
> bureau or registry data. The screening view never shows premiums or face amounts.

The screening prompt in the Cook step is this brief, nearly word for word, and the app prompt
carries the rest.

Insurers know the terms KYC and KYB, but mostly on the life and annuity side, where anti-money
laundering rules apply. P&C calls the same work underwriting, OFAC screening and SIU referral, so
this recipe calls its tab Screening & Due Diligence. The data is Senzing's own entity resolution
truth set, re-cut into the feeds an insurer's customer data hub actually receives: the same people
as the banking recipe, which makes the point that resolution carries across industries while the
policy on top of it does not.

## Setup: What you'll need

- **Setup (one-time):** an AI coding assistant, the **Senzing MCP**, and your **Senzing license** - new to this? Start with **[Get Started](../getting-started.md)**.
- **Where it runs:** the prompts stand Senzing up on your own machine. Edit them if you want it somewhere else.
- **Senzing license:** optional for this recipe. The data is 248 records, under the built-in 500-record evaluation limit. If you have a license file, attach it to the chat now (or drop it in your assistant's working folder).
- **Ingredients:** six synthetic source files in [`ingredients/insurance/`](../ingredients/insurance/), already in Senzing JSON format - download all six, then **attach them to the chat now** (or drop them in your assistant's working folder):
  - **PC_POLICY_ADMIN** - `pc_policy_admin_extract.jsonl`, 104 P&C insureds.
  - **LIFE_ANNUITY_ADMIN** - `life_annuity_admin_extract.jsonl`, 75 life and annuity contract parties.
  - **CREDIT_BUREAU** - `credit_header.jsonl`, 30 credit header records.
  - **BUSINESS_REGISTRY** - `business_registry.jsonl`, 22 businesses, owners and officers.
  - **SIU_CASES** - `siu_cases.jsonl`, 13 SIU case subjects.
  - **SANCTIONS_PEP** - `sanctions_pep.jsonl`, 4 sanctions and PEP entries.

## Before you begin

- **Use your most capable model** (e.g. Opus for Claude), not a fast or cheap one - these recipes do real, multi-step work.
- **Yours will look different.** Your assistant builds the result fresh each run, so the layout and features vary - a chart or the graph may sit on a different tab. The screenshot shows the idea, not an exact target.
- **On a Mac, expect Java.** The Senzing Python SDK is Linux-only, so on macOS your assistant will usually build the loader and app in Java. That's fine; the results are the same.
- **All data is synthetic.** Every person and business comes from Senzing's published truth set; policy numbers, premiums and SIU case details are generated.
- **If something looks wrong, ask the assistant before starting over.** It built this and can inspect it. Say what you expected and what you got - "the customer 360 shows 179 customers, check whether records from the two policy systems actually resolved together" - and tell it to verify against Senzing rather than guess. Paste any error in full.

## Cook: Load the data and screen the customers

**Three prompts.** Stand up a local Senzing instance, load all six sources so Senzing resolves
everyone in one pass, then hand your assistant the screening brief and let it build the alert queue.

**Paste this into your AI assistant:**

```
Goal: Stand up a local Senzing instance, ready to load data.

Hard rules:
- Use the Senzing MCP. Do not rely on general training.
- If I have provided a Senzing license file, it is attached to this chat or in your working folder; use it. If not, use the built-in evaluation license: the data is under 500 records.
- Deploy Senzing locally; a local SQLite datastore is fine for a POC.
- Confirm the instance is up and ready before finishing.
```

**Expected outcome:** a local Senzing instance, ready to load data into SQLite.

Now the six sources. They're already in Senzing JSON, so there's no mapping to do. Load them all
together so Senzing resolves everyone across every source at once.

**Then paste this:**

```
Goal: Using the existing local Senzing instance, load and resolve all six insurance data sources together.

Hard rules:
- Use the Senzing MCP. Do not rely on general training.
- These files are synthetic test data (no real people), so you may read and validate them.
- The six files are already in Senzing JSON format. Do not re-map them. Validate them with the Senzing MCP analyzer before loading.
- Build the loader with the Senzing MCP sdk_guide.
- Register the data sources as PC_POLICY_ADMIN, LIFE_ANNUITY_ADMIN, CREDIT_BUREAU, BUSINESS_REGISTRY, SIU_CASES and SANCTIONS_PEP.
- Use multithreading for loading. Load every record, then process all redo records.
- Do not use sleep/wakeups.
- I have provided the source files: pc_policy_admin_extract.jsonl, life_annuity_admin_extract.jsonl, credit_header.jsonl, business_registry.jsonl, siu_cases.jsonl and sanctions_pep.jsonl are attached to this chat, or in your working folder. Do not guess a path - ask me if you can't find them.

Preferences:
- Provide me live status updates on the data ingestion.

Steps:
1. Validate, register and load all six files.
2. When complete, give me a short load summary: records loaded per source, resolved entities, how many customers appear in both PC_POLICY_ADMIN and LIFE_ANNUITY_ADMIN, and relationships found.
```

**Expected outcome:** 248 records load with no errors and resolve into roughly 157 entities. The 179
customer records become about 127 customers, around 38 of them holding both P&C cover and a life or
annuity contract, joined across transliterated names, nicknames and missing identifiers.

This is where you hand over the screening brief. Your assistant has never seen this data, so the
prompt carries everything: what each source is, what to look for, the severities by line of
business, and the lookalikes not to flag.

**Then paste this:**

```
Goal: Using the resolved data in the existing Senzing instance, screen every customer and write a screening and due diligence alert report.

Background:
- We are a US insurer selling personal and commercial P&C cover and life and annuity products. Customers come from two systems that don't share customer numbers: PC_POLICY_ADMIN (P&C policies) and LIFE_ANNUITY_ADMIN (life and annuity contracts).
- CREDIT_BUREAU holds credit header records we use to verify identity.
- BUSINESS_REGISTRY holds business registrations, their owners, officers and parent companies, linked to them by disclosed relationships that carry the role and ownership percentage.
- SIU_CASES is our special investigations unit's case file, with FRAUD_TYPE, ROLE_IN_CASE, LINE_OF_BUSINESS and CASE_DISPOSITION.
- SANCTIONS_PEP is our screening vendor's list. LIST_TYPE is Sanctions or PEP.

What to look for:
1. Sanctions: a customer who resolves to a Sanctions entry is Prohibited. Recommend blocking policy issue and any claim payment, and reporting to OFAC.
2. SIU history: a customer who resolves to an SIU case. Substantiated, or Referred to Law Enforcement / DOI, is High: recommend declining at underwriting and referring open claims to the SIU. Open or Unsubstantiated is Medium.
3. PEPs: High only when the customer holds a LIFE_ANNUITY_ADMIN contract (recommend enhanced due diligence on the source of funds). For a customer with P&C only, it is informational (Low).
4. Possible matches: a customer Senzing relates to an SIU_CASES or SANCTIONS_PEP entry as a possible match but did not resolve. Medium (Low for a PEP on a P&C-only customer): an analyst decides true match or false positive. If the customer already has a hit, fold this into that alert.
5. Identity verification: a customer whose SSN lines up with a CREDIT_BUREAU file while their date of birth or name conflicts with it. Several customers on one bureau file is a synthetic identity signal and High; one customer is Medium.
6. Ownership screening: a business customer whose owners or officers, directly or through its parent company, are on either list. Sanctioned owners holding 50% or more make the business Prohibited (the OFAC 50% rule); other listed owners follow the rules above. Roll subsidiaries that are only flagged through their parent into the parent's alert, which takes the most severe rating in the group.

Innocent explanations not to flag as hits: common names, households sharing an address, and people who have moved. A name-only match is a possible match for an analyst, never a hit by itself.

Hard rules:
- Keep all previous Hard Rules in force.
- Use the Senzing MCP, not general training.
- Base the screening on Senzing's resolved entities and relationships, using the record-level data where needed. Never query Senzing's internal engine tables directly.
- Every alert must come with evidence: the records, the match key or disclosed relationship, and the list entry or SIU case behind it.

Steps:
1. For each of the six checks, design the approach and explain it in plain language.
2. Run it against the full repository and review every hit for the innocent explanations above.
3. Write the alert report: one alert per customer or business group, with its type, severity, recommended action, the people and businesses involved, the evidence, and a one-line reason. Include counts by severity and what you reviewed and cleared, and why.
```

**Expected outcome:** an alert report of around 18 alerts, each with evidence and a recommended
action. You should see a commercial customer owned outright by a sanctioned person whose president
is sanctioned too (Prohibited), that sanctioned owner also holding a policy, a company group owned
50/50 by two PEPs that is High because one of its subsidiaries holds a key person life policy,
customers with substantiated SIU cases (High) and one whose cases are open or unsubstantiated
(Medium), three customers presenting one bureau file's SSN, and several common-name possible matches
for an analyst to clear.

## Plate: Visualize the results

**One prompt.** Serve both teams from one app: an overview, a customer 360, and a screening and due
diligence queue, each on its own tab.

**Paste this into your AI assistant:**

```
Goal: Build a customer data hub web app on the resolved data in the existing Senzing instance, with three tabs: Overview, Customer 360 and Screening & Due Diligence.

Hard rules:
- Keep all previous Hard Rules in force.
- Use the Senzing MCP, not general training.
- Only use a Senzing mart and/or the Senzing SDK to populate the UX. Never query Senzing's internal engine tables directly.
- Follow the Senzing MCP reporting_guide for query patterns, graph layouts, and why-match interfaces.
- Only present the network graph based on a selected entity. Never the whole graph at once.
- Build the reporting_guide's data mart for the overview and the customer list. Query Senzing directly (get entity, why, how, find network) for customer profiles and alert details.
- The Customer 360 tab reads only PC_POLICY_ADMIN and LIFE_ANNUITY_ADMIN. It never shows SIU_CASES, SANCTIONS_PEP, CREDIT_BUREAU or BUSINESS_REGISTRY records; where one of those is part of an entity, say only that a record outside the customer systems exists.
- The Screening & Due Diligence tab never shows premiums, face amounts or account values.
- Analyst dispositions are stored by the app, never written to Senzing. Confirm each one before saving it.
- Confirm the app responds before finishing.

Preferences:
- Look and feel: clean and modern, with light and dark modes. White cards with thin borders and rounded corners on a soft grey page, one teal accent for the active tab, links and focus, red and amber for severity, a deeper red for Prohibited and muted grey for informational. A clean sans-serif (such as IBM Plex Sans or Inter) for text and a monospace face for record IDs and match keys. A distinct colour per data source, used everywhere that source appears.
- Header: the app title, a one-line description of the sources, and headline counts (records, resolved entities, customers, open alerts). Below it, the three tabs, with the customer count on Customer 360 and the open alert count on Screening & Due Diligence. The tab names must fit on one line; on a narrow screen, shorten the last one to Screening.
- Overview: big-number tiles for records to entities, customer records to real customers (and how many are in both systems), and open alerts by severity. A Customer 360 card with a stacked bar of customers in both systems, P&C only (life prospects) and life or annuity only (P&C prospects), plus duplicates in each system. A Screening & Due Diligence card with open alerts by type. Records by source, and where each pair of sources meets. Each card links to its tab.
- Customer 360: a searchable customer list on the left (name, policy or contract number, phone) with filters for both systems, no life or annuity, no P&C policy, duplicates, businesses, lapsed and cancelled. The profile on the right: tier and flags, totals for P&C written premium, life face amount and annuity value, a policies and contracts table across both systems, the source records with the match key that joined each one, the how report for any customer with more than one record, and household and relationship links to other customers.
- Screening & Due Diligence: an alert queue on the left with filters for Individual, Business and Prohibited, each alert showing its type, severity and subject. The alert on the right: the headline, why it was flagged (including the SIU case or list details), the recommended action, what the analyst decides, disposition buttons (True match, False positive, Refer to SIU), the network of everyone in the alert, and every entity's records with its how report. Dispositioned alerts leave the queue and list underneath it.
- Network graphs: the selected customer or alert only. Label every line with its match key, or with the role for a disclosed relationship (such as OWNS 100% or PRESIDENT). Squares for businesses, circles for people; dashed lines for possible matches.
- Why: clicking a line, or its row in the relationship list under the graph, shows why Senzing related the two entities, as a side-by-side feature comparison with scores.
- How: for any entity with more than one record, the steps Senzing took to build it, each with its match key and, on demand, its feature scores.

Steps:
1. Build the data mart and the web app.
2. Wire the screening queue to the alert report and the profiles and alerts to live Senzing queries.
3. Give me the local URL.
```

**Expected outcome:** a local URL that opens on the overview, with the headline numbers and both
teams' summaries side by side. In Customer 360, pick a customer in both systems and you see their
P&C policies and life contracts in one table, with the match key that joined them and the how steps
behind it. In Screening & Due Diligence, open the sanctioned-owner business: the graph shows it
linked to two sanctioned people, each line labelled with the ownership or officer role, and the
recommended action is to block. Open a customer with an open SIU case and see why it is Medium, not
High. Open a common-name possible match and the why table shows the name agreeing while the date of
birth disagrees, which is exactly what the analyst needs to clear it.

## Wrap Up

In ~45 minutes you built a **customer data hub for an insurer**: six sources resolved once into who
is who, a customer 360 that finds the customers your policy systems split in two, and a screening
queue that applies each line of business's rules to the same resolved people. Swap in your own
systems and your own screening policy (mind the PII) and the same moves - cook, plate - carry over.

To show it to someone, about ten minutes covers it:

1. **The problem.** Two policy systems that never talk, and two teams asking "who is this?" for
   opposite reasons. Resolve once, serve both.
2. **The overview.** 179 customer records are about 127 real customers, and around 38 of them hold
   both P&C cover and a life or annuity contract without either system knowing.
3. **Customer 360.** Open a customer in both systems: every policy and contract in one table, the
   match key that joined the records, and the how steps behind it. Point at a cross-sell gap.
4. **Ownership screening.** Open the sanctioned-owner business. Nothing about its name is
   suspicious; the registry's ownership record is what links it to a sanctioned person, and that
   link only exists after resolution.
5. **Rules by line of business.** The PEP-owned group is High only because a subsidiary holds a life
   policy; an open SIU case is Medium, a substantiated one High. Same people, insurance policy on
   top.
6. **The analyst's job.** Open a common-name possible match, read the why table, and clear it as a
   false positive. Flagging only the right people is what makes a queue worth working.

**Next:** browse [the cookbook](../cookbook.md) for another use case.
