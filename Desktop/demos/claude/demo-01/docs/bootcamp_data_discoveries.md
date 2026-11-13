# Bootcamp data discoveries: Customer 360

What Senzing found in your CRM_CONTACTS, WEB_ORDERS and SUPPORT_TICKETS data (synthetic, generated for the bootcamp). Every figure comes from Senzing through generated SDK code (export, get entity, why, how, network and path calls) and the evaluation method in the Senzing reporting guide; accuracy figures compare against the answer key the data generator saved.

## Headline numbers, interpreted

- **10,000 records resolved into 7,134 customers** (1.40 records per customer). About 29% of the records were repeats of a customer already seen somewhere else, which is the duplication the business problem wanted made visible.
- **2,395 customers are built from 2 or more records; 2,090 of them appear in more than one system.** Pairs: CRM x Web 1,489, Web x Support 522, CRM x Support 433. That is the cross-system overlap the retailer could not see before.
- **Inside single systems:** 279 CRM customers hold 579 contact records (customers entered twice), and 288 web customers hold 587 order profiles (repeat orders). Support tickets carry no internal duplicates.
- **Accuracy against the answer key:** 99.9% of merges are correct (3,388 of 3,390 merged record pairs) and 95.1% of true matches were found. 2,066 of the 2,200 customers who really appear in more than one system were brought fully together (93.9%).
- **Reading it:** the resolution is conservative. It almost never merges the wrong people, and what it misses is mostly support tickets that carry too little information to merge safely.

## Merges and match keys

Every merge is explained by its match key (which features agreed). Across all 2,866 record-to-entity merges:

| Match key | Merges | What it means |
|---|---|---|
| +NAME+ADDRESS+PHONE+EMAIL | 806 | Everything agreed |
| +NAME+PHONE+EMAIL | 609 | Address missing on one side |
| +NAME+EMAIL | 578 | Name and email alone (often a sparse support ticket) |
| +NAME+ADDRESS+EMAIL | 529 | No phone on one side |
| +EXACTLY_SAME | 117 | Identical records (repeat entries) |
| +NAME+PHONE | 94 | Name and phone, no email |
| +NAME+ADDRESS+PHONE | 72 | No email on one side |
| +NAME+ADDRESS | 39 | Name and address only |
| +EMAIL | 16 | Email alone (a ticket with no name) |
| +PHONE+EMAIL | 6 | Phone and email, no comparable name |

The full per-customer list with record IDs per system is `data/results/customer_master_list.csv`; each merge can be explained with `src/query/explain_match.java`. No merge carried a feature that disagreed (no "-" component in any match key).

Two merges are wrong by the answer key: entity 300393 (CRM C002326 + Web W004008) and entity 301424 (CRM C003448 + CRM C002765). Both joined on +NAME+PHONE: identical names with phone numbers one digit apart, a pattern the synthetic data generator did not guard against.

## Review queue

**3,661 cross-source pairs are linked as "possibly the same" without being merged** (`data/results/review_queue.csv`). Every one rests on the name alone (+NAME). By the answer key, 120 are truly the same customer and 3,541 are different people who share a name, so this queue is where a data steward would look, not a list of errors. Sample rows that are worth a human decision:

| Entity A | Entity B | Link | Answer key |
|---|---|---|---|
| 200019 Ashley Bennett (CRM) | 504431 Ashley Bennett (Support) | POSSIBLY_SAME +NAME | same person |
| 200072 Daniel Harris (CRM + Web) | 504858 Daniel Harris (Support) | POSSIBLY_SAME +NAME | same person |
| 300295 Thomas Reyes (CRM) | 505575 Tom Reyes (Support) | POSSIBLY_SAME +NAME | same person (nickname) |
| 200005 Richard Jimenez (CRM + Web) | 500650 Richard Jimenez (Support + Web) | POSSIBLY_SAME +NAME | different people |
| 200001 Charles Nguyen (CRM + Web) | 302481 Charles Nguyen (CRM + Web) | POSSIBLY_SAME +NAME | different people |

A steward resolves these by asking the customer or the support agent for one more identifier (email or phone); with it, Senzing would merge the true pairs on its own.

## Why and how: worked examples

**Why two records merged.** CRM C002363 and Web W002674 (customer 300804, Ahmed White) resolved on +NAME+ADDRESS+EMAIL. The CRM record stores the address as one line ("578 Pine Ave, Ashland, KY 41180") and the web order stores it in parts ("578 Pine Avenue", "Ashland", "KY", "41180"); the address still scored 99 of 100 (SAME). Name scored 100 (one field against first + last) and email 100.

**How a customer was built.** Customer 300804 grew in four steps: two CRM records joined on +NAME+ADDRESS+PHONE+EMAIL (the same person entered twice); a web order joined on +NAME+PHONE+EMAIL; a support ticket joined on +NAME+EMAIL (its "Ashland 41180" address scored only 75, LIKELY, and did not count); the second web order joined on +NAME+ADDRESS+EMAIL. The final state is settled (one construction, no re-evaluation needed).

**Near-miss (the one that teaches more):**
CRM C002843 and Support T001133 are both "Ashley Bennett" and, by the answer key, the same customer, but Senzing kept them apart and only linked them as possibly the same. The why result shows the reason: the ticket carries a name and nothing else to compare, so name (100, SAME) was the only evidence. A name alone is not enough to merge two people, because in this data 3,541 pairs of different customers share a name. Capturing an email or phone on that ticket is what would have merged them.

**Correctly kept apart.** CRM C000027 and Web W002988 are both "Richard Jimenez": name 100, but email 0, phone 0 and address 29, all NO_CHANCE. Two different people, linked for review and never merged.

## Relationship networks

All 4,592 relationships in this data were discovered by Senzing (none are stated in the sources): 4,567 "possibly the same" on name alone and 25 "possibly related", mostly on a shared address. A network search around Carol Nelson (entity 400145, a support ticket, the most-linked customer) returned 9 customers and 23 links:

```text
Carol Nelson x7 (CRM, Web, Support) -- all linked "possibly same" on +NAME
        |
   400145 Carol Nelson (Support)
        |  "possibly related" on +ADDRESS (postal code 29699 only)
        v
   500171 Melissa Alvarez (Web + Support) --+NAME-- 302861 Melissa Alvarez (CRM + Web)
```

The shortest path from Carol Nelson 302765 (CRM) to Melissa Alvarez 500171 is 2 degrees, through 400145. A second chain: Mark Morris (300696) -> shared postal code 22223 -> Christopher Mitchell (502541) -> same name -> Christopher Mitchell (300167). By the answer key every customer in these chains is a different person: the address links rest on a postal code alone, because support tickets record no street. Networks like this are leads for household or fraud review, not hidden duplicates.

## What was not found, and why

**Measurement:**
The answer key holds 7,000 invented customers; 2,200 of them appear in more than one system. Senzing produced 7,134 customers: 134 more than the truth, because 136 customers stayed split (and 2 pairs were merged wrongly). Of the 136 split customers, 122 involve SUPPORT_TICKETS, the source where phone is filled on 51% of records, city on 50%, postal code on 54%, and there is never a street.

Which case applies: **the data had limits, not the pipeline.** The overlap was there to find (1,404 shared emails between CRM and Web alone, measured in the data-quality assessment) and Senzing found 93.9% of the multi-system customers. What it did not find sits almost entirely on sparse tickets whose only comparable detail is a name, which Senzing correctly refuses to merge on.

- **No suppressed matches.** Data processing's match-key audit found no feature that disagreed in any merge (single-source per-record bucket: 0 of 322; cross-source per-record bucket: 0 of 2,544) and none in any relationship (relationship bucket: 0 of 4,592). Nothing in the mapping is pushing customers apart.
- **High possible-match rate is a data trait.** 70.1% of customers have a "possibly the same" link because the generator drew from a small name pool (4,836 of 7,134 customers share their name with another). This is not something a remapping would change.
- **What would find more:** capture email or phone on support tickets, and full addresses; both would turn name-only links into merges.
