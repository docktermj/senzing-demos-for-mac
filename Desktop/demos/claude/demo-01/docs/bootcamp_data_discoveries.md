# Bootcamp Data Discoveries

Customer 360 scenario: CRM_SYSTEM, BILLING_SYSTEM and SUPPORT_TICKETS, resolved with the Senzing SDK. Every figure below comes from the SDK (export stream, get_entity, why_records, how_entity, find_network, find_path) run by the programs in `src/query/`.

## Your question, answered

| Requirement | Answer | Where in this document |
|---|---|---|
| Which customers appear in all three systems, and what does their unified record look like? | 451 customers appear in all three systems; each unified record merges one name, address, phone and email from the three systems (e.g. Kyle Yorkbrook, entity 300009: BIL-000100, CRM-000100, CRM-003782 and TKT-000100). | Merges and match keys |
| Which records are likely duplicates of each other but weren't merged, so they need a human review? | 737 possibly-same pairs (1,336 entities), 679 of them across systems; every pair matched on name only (+NAME). Against the answer key 644 are the same person and 93 are different people. | Review queue |
| How many unique customers do we really have, compared with the raw record count in each system? | 6,671 unique customers behind 10,000 records (1.5 records per customer): CRM 4,000 records / 3,702 customers, Billing 4,000 / 3,923, Support 2,000 / 1,688. | Headline numbers, interpreted |

## Headline numbers, interpreted

- **10,000 records loaded, 6,671 entities resolved.** One customer in three has records in more than one place, so a third of the "customers" in any one system report is a duplicate view of someone else's.
- **2,460 entities hold more than one record; 2,191 span two or more systems; 451 span all three.** These are the customers the business could not see whole before.
- **Within-system duplicates:** CRM 298 (campaign re-entries), Support 312 (repeat tickets), Billing 77.
- **Accuracy against the scenario's answer key:** precision 100% (no wrong merges), recall 80.9%. Every merge Senzing made was right; about one true match in five was left as a possible match rather than merged.

## Merges and match keys

The match keys behind the merges (record joins, by rule):

| Match key | Rule | Records joined |
|---|---|---|
| +NAME+ADDRESS+PHONE | MFF_CNAME | 1,064 |
| +NAME+EMAIL | SF1_CNAME | 921 |
| +NAME+ADDRESS | CNAME_CFF | 764 |
| +NAME+PHONE | CNAME_CFF | 263 |
| +EXACTLY_SAME | (exact duplicate) | 157 |
| +NAME+ADDRESS+PHONE+EMAIL | SF1_PNAME_CFF | 79 |

Billing joins CRM mostly on name + address + phone; Support joins CRM on name + email. The full list of 451 three-system customers is in `data/query_results/customers_in_all_sources.json`.

**Headline entity:** Alan Whitevale (entity 400155), 5 records from all three systems: two CRM records sharing name, address, phone and email; two Support tickets joined on +NAME+EMAIL; one Billing record joined on +NAME+ADDRESS despite "Ln" vs "Lane".

## Review queue

737 possibly-same pairs, all on +NAME (full queue: `data/query_results/review_queue.json`). By system pair: Support vs Billing 204, CRM vs Billing 165, CRM+Billing vs Support 106, CRM vs Support 67, Support vs Support 41.

- **Robert Rainridge, entities 300005 and 801540:** CRM has street, phone and email; Billing BIL-001046 has only name and "Duluth, MN 55805". Same person, nothing to confirm it beyond the name.
- **Mary Huntcroft (300039) and "Mary" (801469):** Billing BIL-002893 carries only the first name "Mary" in Des Moines; Mary Huntcroft lives in Albany. Different people, correctly not merged.

Each row is one human decision away from being acted on; a shared customer number across systems would settle most of them automatically.

## Why and how: worked examples

**Why (why_records), Yvonne Vanceson, entity 300010:** compared directly, Support TKT-000383 (name, email) and Billing BIL-000383 (name, address, phone) share only the name: why key +NAME, rule SNAME, POSSIBLY_SAME, name score 100.

**Near-miss (the one that teaches more):** on their own, those two records would never merge, because a matching name is not enough evidence. They sit in one entity only because CRM-000383 carries all four features and bridges them.

**How (how_entity), same entity:** Step 1, CRM-000383 + BIL-000383 on +NAME+ADDRESS+PHONE (MFF_CNAME; address 99 for "Blvd" vs "Boulevard", phone 100). Step 2, TKT-000383 joins that pair on +NAME+EMAIL (SF1_CNAME; email 100). Final state: one settled entity, nothing to re-evaluate.

## Relationship networks

All relationships are discovered (possibly same 737, possibly related 10); none are disclosed in the source data.

- **The "Brad" cluster:** find_network from 400514 (Brad Crestson) and 401866 (Brad Highley) returned 8 entities and 13 possibly-same +NAME links. Both entities hold a Billing record with only NAME_FIRST "Brad" (BIL-000342, BIL-001739), so the bare first name links them to every other Brad.
- **A 2-degree path:** 400623 Harry Glenwell -> 400132 Harold Fairberry -> 400627 Harry Nashton, +NAME at each hop. Harold's Billing record is first-name-only, and "Harry" is a nickname of Harold, so the path is built on first names alone.

Finding: first-name-only Billing records create noisy links; they are a data-quality item for BILLING_SYSTEM, not real connections.

## What was not found, and why

**Measurement:** 646 of 6,000 invented people remain split across entities (recall 80.9%). Their records share only a name: BILLING_SYSTEM has no email field and, after regeneration, about 18% of its records lack an address and 36% a phone; SUPPORT_TICKETS carries only name and email. Data collection measured 40 invented people sharing a name with a different person (a lower bound).

Which case applies: **the data had little shared evidence to find**, not that the pipeline underperformed. The mapping carries every field the sources have (the cause check found the near-misses are not mapping-actionable), the match-key audit found no suppressed features (single-source 0 of 269 keys, cross-source 0 of 3,060, relationships 0 of 747), and the how-state audit found all 2,460 multi-record entities settled.
