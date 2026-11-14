---
title: Customer 360 and KYC/KYB for a Retail Bank
use_case: customer-360
difficulty: Intermediate
est_time: ~45m
video:
author: Jeff Butcher
---

# Customer 360 and KYC/KYB for a Retail Bank

**What you'll build:** one app that serves two teams from a single resolution of your customer data -
a customer 360 for the people who sell and service, and a KYC/KYB screening queue for the people who
keep the bank compliant. **In this case** a core banking CIF extract and a card platform's cardholder
master, where the same customer is two strangers, plus a credit bureau file, a business registry and a
screening list. Afterwards, one profile per real customer with every account they hold, and an
analyst's queue of the sanctions hits, fraud-list matches, borrowed SSNs and risky business owners
hiding among them.

**What it takes:** Intermediate · ~45 min once you're set up · runs on your machine · five data files to download and attach

![Banking customer hub overview - 248 records from five sources resolved into 157 entities, 179 customer records into 127 real customers, 38 of them in both systems, and 18 open KYC/KYB alerts](images/banking-customer-360-kyc-outcome.png)

*248 records from five sources become **157** entities. The 179 customer records are **127** real customers, **38** of them in both the core banking system and card services without either system knowing, and **18** KYC/KYB alerts land in the analyst's queue.*

## Chef's Note

Banks resolve their customers twice: once for the relationship side, who want to know everything a
customer holds, and once for compliance, who want to know whether they should hold anything at all.
Usually that is two projects, two vendors and two answers to "who is this?". This recipe does it
once, and serves both from the same resolved entities.

Like the patient fraud recipe, it starts with a plain-English brief rather than code. Here's the
one for a US regional bank:

> **The use case.** We are a US regional bank with retail and business customers. We want one
> resolution of our customer data to serve two teams. Relationship managers want a customer 360:
> every account a customer holds across our systems. Compliance wants KYC and KYB: every customer
> screened, identities checked, and business customers' owners and officers known.
>
> **Our data** (5 sources, 248 records, all synthetic test data):
>
> 1. **CORE_BANKING**: the core banking system's customer information file (CIF) extract. Deposit
>    accounts (checking, savings, money market, CDs) with ownership type, status, balance, branch
>    and relationship tier.
> 2. **CARD_SERVICES**: the card platform's cardholder master. Card account, last four digits of
>    the card, primary or authorized user, product, credit limit, balance, status (including charged
>    off) and delinquency.
> 3. **CREDIT_BUREAU**: credit header records, which we use to verify identity.
> 4. **BUSINESS_REGISTRY**: business registrations with their owners, officers and parent companies,
>    including ownership percentages.
> 5. **WATCHLIST**: our screening list: sanctions, politically exposed persons (PEPs), and customers
>    we exited for fraud (internal fraud).
>
> The two customer systems each keep their own customer numbers, so neither knows when a depositor
> is also a cardholder, or when it holds the same customer twice.
>
> **What the customer 360 needs:** one profile per real customer, with every deposit account and
> card, total deposits, card credit and balance, household and ownership links to other customers,
> the duplicates inside each system, and the cross-sell gaps (deposits but no card, a card but no
> deposit account).
>
> **What KYC/KYB looks for:**
>
> 1. **Screening hits.** A customer who resolves to a list entry. Sanctions is Prohibited; internal
>    fraud and PEP are High.
> 2. **Possible screening hits.** A customer who might be a list entry, but not closely enough to be
>    sure. Medium: an analyst decides true match or false positive.
> 3. **Identity verification.** A customer whose SSN lines up with a credit bureau file while their
>    date of birth or name conflicts with it. Several customers on one bureau file is a synthetic
>    identity signal and High; one customer is Medium.
> 4. **Beneficial ownership (KYB).** A business customer whose owners or officers, directly or
>    through its parent company, are on a list. Sanctioned owners holding 50% or more make the
>    business Prohibited (the OFAC 50% rule); anything else is High. Subsidiaries roll up into the
>    parent's alert.
>
> **Innocent explanations we don't want flagged as hits:** common names (we have several John and
> Robert Smiths), households sharing an address, and people who have moved. A name-only match goes
> to an analyst; it is never a hit by itself.
>
> **Keep the two views apart.** The customer 360 never shows screening lists, bureau or registry
> data. The compliance view never shows balances or limits.

The screening prompt in the Cook step is this brief, nearly word for word, and the app prompt
carries the rest.

The data is Senzing's own entity resolution truth set, re-cut into the feeds a bank's customer data
hub actually receives. That makes it a hard test: transliterated names, nicknames, missing
identifiers, people who share an SSN, and businesses whose only link to a sanctioned person is an
ownership record in the registry. Name screening alone misses that last one entirely, which is the
point of doing KYB on resolved entities rather than on names.

## Setup: What you'll need

- **Setup (one-time):** an AI coding assistant, the **Senzing MCP**, and your **Senzing license** - new to this? Start with **[Get Started](../getting-started.md)**.
- **Where it runs:** the prompts stand Senzing up on your own machine. Edit them if you want it somewhere else.
- **Senzing license:** optional for this recipe. The data is 248 records, under the built-in 500-record evaluation limit. If you have a license file, attach it to the chat now (or drop it in your assistant's working folder).
- **Ingredients:** five synthetic source files in [`ingredients/banking/`](../ingredients/banking/), already in Senzing JSON format - download all five, then **attach them to the chat now** (or drop them in your assistant's working folder):
  - **CORE_BANKING** - `core_cif_extract.jsonl`, 97 deposit customers from the core banking CIF.
  - **CARD_SERVICES** - `card_cardholder_master.jsonl`, 82 cardholders from the card platform.
  - **CREDIT_BUREAU** - `credit_header.jsonl`, 30 credit header records.
  - **BUSINESS_REGISTRY** - `business_registry.jsonl`, 22 businesses, owners and officers.
  - **WATCHLIST** - `screening_list.jsonl`, 17 sanctions, PEP and internal fraud entries.

## Before you begin

- **Use your most capable model** (e.g. Opus for Claude), not a fast or cheap one - these recipes do real, multi-step work.
- **Yours will look different.** Your assistant builds the result fresh each run, so the layout and features vary - a chart or the graph may sit on a different tab. The screenshot shows the idea, not an exact target.
- **On a Mac, expect Java.** The Senzing Python SDK is Linux-only, so on macOS your assistant will usually build the loader and app in Java. That's fine; the results are the same.
- **All data is synthetic.** Every person and business comes from Senzing's published truth set; account numbers, balances and card details are generated.
- **If something looks wrong, ask the assistant before starting over.** It built this and can inspect it. Say what you expected and what you got - "the customer 360 shows 179 customers, check whether records from the two systems actually resolved together" - and tell it to verify against Senzing rather than guess. Paste any error in full.

## Cook: Load the data and screen the customers

**Three prompts.** Stand up a local Senzing instance, load all five sources so Senzing resolves
everyone in one pass, then hand your assistant the compliance brief and let it build the alert
queue.

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

Now the five sources. They're already in Senzing JSON, so there's no mapping to do. Load them all
together so Senzing resolves everyone across every source at once.

**Then paste this:**

```
Goal: Using the existing local Senzing instance, load and resolve all five banking data sources together.

Hard rules:
- Use the Senzing MCP. Do not rely on general training.
- These files are synthetic test data (no real people), so you may read and validate them.
- The five files are already in Senzing JSON format. Do not re-map them. Validate them with the Senzing MCP analyzer before loading.
- Build the loader with the Senzing MCP sdk_guide.
- Register the data sources as CORE_BANKING, CARD_SERVICES, CREDIT_BUREAU, BUSINESS_REGISTRY and WATCHLIST.
- Use multithreading for loading. Load every record, then process all redo records.
- Do not use sleep/wakeups.
- I have provided the source files: core_cif_extract.jsonl, card_cardholder_master.jsonl, credit_header.jsonl, business_registry.jsonl and screening_list.jsonl are attached to this chat, or in your working folder. Do not guess a path - ask me if you can't find them.

Preferences:
- Provide me live status updates on the data ingestion.

Steps:
1. Validate, register and load all five files.
2. When complete, give me a short load summary: records loaded per source, resolved entities, how many customers appear in both CORE_BANKING and CARD_SERVICES, and relationships found.
```

**Expected outcome:** 248 records load with no errors and resolve into roughly 157 entities. The 179
customer records become about 127 customers, around 38 of them holding both a deposit account and a
card, joined across transliterated names, nicknames and missing identifiers.

This is where you hand over the compliance brief. Your assistant has never seen this data, so the
prompt carries everything: what each source is, the four things to look for, the severities, and
the lookalikes not to flag.

**Then paste this:**

```
Goal: Using the resolved data in the existing Senzing instance, screen every customer and write a KYC/KYB alert report.

Background:
- We are a US regional bank with retail and business customers. Customers come from two systems that don't share customer numbers: CORE_BANKING (deposit accounts from the core banking CIF) and CARD_SERVICES (the card platform's cardholder master).
- CREDIT_BUREAU holds credit header records we use to verify identity.
- BUSINESS_REGISTRY holds business registrations, their owners, officers and parent companies, linked to them by disclosed relationships that carry the role and ownership percentage.
- WATCHLIST is our screening list. LIST_TYPE is Sanctions, PEP, or Internal Fraud (customers we exited for fraud).

What to look for:
1. Screening hits: a customer who resolves to a WATCHLIST entry. Sanctions is Prohibited; Internal Fraud and PEP are High.
2. Possible screening hits: a customer Senzing relates to a WATCHLIST entry as a possible match but did not resolve. Medium: an analyst decides true match or false positive. If the customer already has a screening hit, fold this into that alert.
3. Identity verification: a customer whose SSN lines up with a CREDIT_BUREAU file while their date of birth or name conflicts with it. Several customers on one bureau file is a synthetic identity signal and High; one customer is Medium.
4. Beneficial ownership (KYB): a business customer whose owners or officers, directly or through its parent company, are on the WATCHLIST. Sanctioned owners holding 50% or more make the business Prohibited (the OFAC 50% rule); anything else is High. Roll subsidiaries that are only flagged through their parent into the parent's alert.

Innocent explanations not to flag as hits: common names, households sharing an address, and people who have moved. A name-only match is a possible hit for an analyst, never a hit by itself.

Hard rules:
- Keep all previous Hard Rules in force.
- Use the Senzing MCP, not general training.
- Base the screening on Senzing's resolved entities and relationships, using the record-level data where needed. Never query Senzing's internal engine tables directly.
- Every alert must come with evidence: the records, the match key or disclosed relationship, and the list entry behind it.

Steps:
1. For each of the four checks, design the approach and explain it in plain language.
2. Run it against the full repository and review every hit for the innocent explanations above.
3. Write the KYC/KYB alert report: one alert per customer or business group, with its type, severity, the people and businesses involved, the evidence, and a one-line reason. Include counts by severity and what you reviewed and cleared, and why.
```

**Expected outcome:** an alert report of around 18 alerts, each with evidence. You should see a
business card customer owned outright by a sanctioned person whose president is sanctioned too
(Prohibited), that sanctioned owner also banking with you as a deposit customer, a company group owned 50/50 by two
PEPs with three subsidiaries that bank with you, a handful of customers exited for fraud, three
customers presenting one bureau file's SSN, and several common-name possible matches for an analyst
to clear.

## Plate: Visualize the results

**One prompt.** Serve both teams from one app: an overview, a customer 360, and a KYC/KYB queue,
each on its own tab.

**Paste this into your AI assistant:**

```
Goal: Build a customer data hub web app on the resolved data in the existing Senzing instance, with three tabs: Overview, Customer 360 and KYC / KYB.

Hard rules:
- Keep all previous Hard Rules in force.
- Use the Senzing MCP, not general training.
- Only use a Senzing mart and/or the Senzing SDK to populate the UX. Never query Senzing's internal engine tables directly.
- Follow the Senzing MCP reporting_guide for query patterns, graph layouts, and why-match interfaces.
- Only present the network graph based on a selected entity. Never the whole graph at once.
- Build the reporting_guide's data mart for the overview and the customer list. Query Senzing directly (get entity, why, how, find network) for customer profiles and alert details.
- The Customer 360 tab reads only CORE_BANKING and CARD_SERVICES. It never shows WATCHLIST, CREDIT_BUREAU or BUSINESS_REGISTRY records; where one of those is part of an entity, say only that a record outside the customer systems exists.
- The KYC / KYB tab never shows balances or credit limits.
- Analyst dispositions are stored by the app, never written to Senzing. Confirm each one before saving it.
- Confirm the app responds before finishing.

Preferences:
- Look and feel: clean and modern, with light and dark modes. White cards with thin borders and rounded corners on a soft grey page, one teal accent for the active tab, links and focus, red and amber for severity, a deeper red for Prohibited. A clean sans-serif (such as IBM Plex Sans or Inter) for text and a monospace face for record IDs and match keys. A distinct colour per data source, used everywhere that source appears.
- Header: the app title, a one-line description of the sources, and headline counts (records, resolved entities, customers, open alerts). Below it, the three tabs, with the customer count on Customer 360 and the open alert count on KYC / KYB.
- Overview: big-number tiles for records to entities, customer records to real customers (and how many are in both systems), and open alerts by severity. A Customer 360 card with a stacked bar of customers in both systems, deposits only (card prospects) and cards only (deposit prospects), plus duplicate CIFs and duplicate cardholders. A KYC / KYB card with open alerts by type. Records by source, and where each pair of sources meets. Each card links to its tab.
- Customer 360: a searchable customer list on the left (name, account, phone) with filters for both systems, no card, no deposit account, duplicates, businesses and delinquent. The profile on the right: tier and flags, totals for deposits, open card credit and card balance, an accounts table across both systems, the source records with the match key that joined each one, the how report for any customer with more than one record, and household and relationship links to other customers.
- KYC / KYB: an alert queue on the left with filters for KYC, KYB and Prohibited, each alert showing its type, severity and subject. The alert on the right: the headline, why it was flagged, what the analyst decides, disposition buttons (True match, False positive, Escalate to EDD), the network of everyone in the alert, and every entity's records with its how report. Dispositioned alerts leave the queue and list underneath it.
- Network graphs: the selected customer or alert only. Label every line with its match key, or with the role for a disclosed relationship (such as OWNS 100% or PRESIDENT). Squares for businesses, circles for people; dashed lines for possible matches.
- Why: clicking a line, or its row in the relationship list under the graph, shows why Senzing related the two entities, as a side-by-side feature comparison with scores.
- How: for any entity with more than one record, the steps Senzing took to build it, each with its match key and, on demand, its feature scores.

Steps:
1. Build the data mart and the web app.
2. Wire the KYC / KYB queue to the alert report and the profiles and alerts to live Senzing queries.
3. Give me the local URL.
```

**Expected outcome:** a local URL that opens on the overview, with the headline numbers and both
teams' summaries side by side. In Customer 360, pick a customer in both systems and you see their
deposit accounts and cards in one table, with the match key that joined them and the how steps
behind it. In KYC / KYB, open the sanctioned-owner business: the graph shows it linked to two
sanctioned people, each line labelled with the ownership or officer role, and clicking a line shows
the disclosed relationship from the registry. Open a common-name possible match and the why table
shows the name agreeing while the date of birth disagrees, which is exactly what the analyst needs
to clear it.

## Wrap Up

In ~45 minutes you built a **customer data hub for a bank**: five sources resolved once into who is
who, a customer 360 that finds the customers your systems split in two, and a KYC/KYB queue that
catches what name screening misses. Swap in your own systems and your own screening policy (mind the
PII) and the same moves - cook, plate - carry over.

To show it to someone, about ten minutes covers it:

1. **The problem.** Two customer systems with their own customer numbers, and two teams asking
   "who is this?" for opposite reasons. Resolve once, serve both.
2. **The overview.** 179 customer records are about 127 real customers, and around 38 of them hold
   both a deposit account and a card without either system knowing.
3. **Customer 360.** Open a customer in both systems: every account in one table, the match key
   that joined the records, and the how steps behind it. Point at a cross-sell gap.
4. **KYB.** Open the sanctioned-owner business. Nothing about its name is suspicious; the registry's
   ownership record is what links it to a sanctioned person, and that link only exists after
   resolution.
5. **Identity verification.** Three customers present one bureau file's SSN with different dates of
   birth: a synthetic identity signal no single system could see.
6. **The analyst's job.** Open a common-name possible match, read the why table, and clear it as a
   false positive. Flagging only the right people is what makes a queue worth working.

**Next:** browse [the cookbook](../cookbook.md) for another use case.
