# Data Discoveries: Insurance Customer 360 and Screening

What Senzing found in your six sources (all synthetic test data). Every figure below comes from the Senzing SDK (getEntity, the export stream, whyRecords, whyEntities, howEntity, findNetwork, findPath), never from SQL. Names written in non-Latin scripts are shown by the Latin-script name your own data carries for the same entity: entity 400003 as Alexander Pavlovich Vasiliev (from PC_POLICY_ADMIN 2063); entity 400001 as Faisal Siddiqui (from BUSINESS_REGISTRY 2051); entity 500016 as Dara Kim (from LIFE_ANNUITY_ADMIN 2032).

## Headline numbers, interpreted

- **248 records resolved into 157 entities.** 69 entities hold more than one record, and 63 of them combine records from more than one source. Senzing also found 140 relationships between entities that it did not merge.
- **179 customer records are 127 real customers.** 52 customer records were duplicates of someone already known: across the two policy systems that share no customer number, and inside each system (7 customers held more than once in P&C, 4 in life and annuity).
- **38 customers hold both P&C cover and a life or annuity contract.** Neither system could see this. 57 are P&C only (life prospects) and 32 are life or annuity only (P&C prospects).
- **The screening queue holds 19 alerts:** 2 Prohibited, 7 High, 10 Medium, 0 Low, each with its records, match key or disclosed relationship, and list entry or SIU case.
- **27 customers also have a record outside the customer systems** (credit bureau, business registry, SIU or screening list). That is the overlap the screening queue is built on.

## Merges and match keys

Every multi-record entity, with the match key that brought each record in (+ means the feature agreed, - means it disagreed but other evidence outweighed it). The first record of an entity has no match key.

| Entity | Name | Records | Sources | Match keys that joined its records |
|---|---|---|---|---|
| 400030 | Maria Luis Medina Sentosa | 7 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN, SIU_CASES | +DOB+ADDRESS+EMAIL+PNAME; +DOB+EMAIL+PNAME; +NAME+DOB+ADDRESS; +NAME+DOB+ADDRESS+PHONE+EMAIL-PASSPORT |
| 500049 | Susan Mooney | 6 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+ADDRESS; +NAME+DOB; +NAME+DRLIC; +NAME+PASSPORT; +NAME+SSN |
| 400015 | Eddie Kusha | 5 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN, SIU_CASES | +DOB+SSN+PNAME; +NAME+DOB+ADDRESS |
| 400016 | Robert Smith | 4 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS; +NAME+DOB+EMAIL; +NAME+DOB+PHONE |
| 400018 | Marie Kusha | 4 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS; +NAME+DOB+PHONE; +NAME+SSN |
| 400003 | Alexander Pavlovich Vasiliev | 3 | BUSINESS_REGISTRY, PC_POLICY_ADMIN, SANCTIONS_PEP | +NAME+ADDRESS+PHONE; +NAME+PHONE |
| 400013 | Jie Wang | 3 | BUSINESS_REGISTRY, LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB; +NAME+DOB+ADDRESS+NATIONAL_ID |
| 400014 | Wang Wei | 3 | BUSINESS_REGISTRY, LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB; +NAME+DOB+ADDRESS+NATIONAL_ID |
| 400019 | Muhammed Antoun | 3 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN, SIU_CASES | +NAME+DOB |
| 500029 | Leigh Toulouse | 3 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+PASSPORT; +NAME+PASSPORT |
| 500039 | Sahra Thompson | 3 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+EMAIL |
| 200004 | Marcus Ellery | 2 | CREDIT_BUREAU, LIFE_ANNUITY_ADMIN | +NAME+DOB+SSN |
| 200009 | Kathryn Vossler | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+SSN |
| 200010 | Benjamin Hartley | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+SSN |
| 300001 | Cornelius Ashby | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+PHONE+SSN |
| 300002 | Florence Wickersham | 2 | CREDIT_BUREAU, LIFE_ANNUITY_ADMIN | +NAME+DOB+SSN |
| 300003 | Mildred Thorncastle | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+PHONE+SSN |
| 300005 | Rosalind Kemper | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+SSN |
| 300006 | Philippa Strand | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+PHONE+SSN |
| 300008 | Imogen Halvorsen | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+PHONE+SSN |
| 300013 | Ruth Abernathy-Coles | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+SSN |
| 300017 | Beatrice Lindqvist | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+SSN |
| 300019 | Eleanor Pruitt | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+SSN |
| 300020 | Gwendolyn Farraday | 2 | CREDIT_BUREAU, PC_POLICY_ADMIN | +NAME+DOB+PHONE+SSN |
| 400001 | Faisal Siddiqui | 2 | BUSINESS_REGISTRY, SANCTIONS_PEP | +NAME+ADDRESS+PHONE |
| 400002 | Margaret Charney | 2 | BUSINESS_REGISTRY, SANCTIONS_PEP | +NAME+EMPLOYER |
| 400004 | Howard Hughes | 2 | BUSINESS_REGISTRY, SANCTIONS_PEP | +NAME+EMPLOYER |
| 400006 | Hajah Mamunah Jln Pisang | 2 | BUSINESS_REGISTRY, LIFE_ANNUITY_ADMIN | +NAME+ADDRESS |
| 400007 | Universal Exports Worldwide | 2 | BUSINESS_REGISTRY, PC_POLICY_ADMIN | +NAME+ADDRESS |
| 400011 | Mullenkrants | 2 | BUSINESS_REGISTRY, PC_POLICY_ADMIN | +NAME+ADDRESS |
| 400012 | Universal Exports USA | 2 | BUSINESS_REGISTRY, PC_POLICY_ADMIN | +ADDRESS+PHONE+LIKELY_NAME |
| 400020 | Marsha Kusha | 2 | PC_POLICY_ADMIN, SIU_CASES | +ADDRESS+SSN+EMAIL |
| 400024 | Robert E Smith Sr | 2 | PC_POLICY_ADMIN, SIU_CASES | +NAME+DRLIC |
| 400027 | Daniella Shaw | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+PHONE |
| 400029 | Patrick Smith | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+ADDRESS+PASSPORT |
| 400032 | Louis Garsky | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS |
| 500001 | Kristen Salinger | 2 | BUSINESS_REGISTRY | +NAME+PHONE |
| 500003 | Rosemary A Thomas | 2 | BUSINESS_REGISTRY | +NAME+ADDRESS |
| 500004 | Universal Exports Singapore | 2 | BUSINESS_REGISTRY, PC_POLICY_ADMIN | +NAME+ADDRESS |
| 500006 | Susan Meyer Thomas | 2 | BUSINESS_REGISTRY | +NAME+ADDRESS |
| 500007 | Anastassia Alexopoulos | 2 | BUSINESS_REGISTRY | +NAME+ADDRESS+EMAIL |
| 500010 | Jones Kaylee | 2 | BUSINESS_REGISTRY | +PHONE+EMAIL+PNAME |
| 500011 | Universal Exports India | 2 | BUSINESS_REGISTRY, LIFE_ANNUITY_ADMIN | +NAME+ADDRESS+PHONE |
| 500016 | Dara Kim | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+ADDRESS |
| 500020 | Morrie Klempsky | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS+PASSPORT |
| 500022 | WONDERLAND | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+TAX_ID |
| 500025 | Jane Muir | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS |
| 500034 | Patricia Smith | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+ADDRESS+PASSPORT |
| 500040 | Jie Liu | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS |
| 500042 | Xiu Ying Zhang | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS |
| 500047 | MEENU J GUPTA | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+PASSPORT |
| 500051 | Wei Zhang | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS |
| 500055 | Juann Aguilar | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+DRLIC |
| 500061 | Anna Maria Aguilar | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+EMAIL |
| 500063 | Andreason | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+PHONE |
| 500069 | Magdalena Jones | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +DOB+ADDRESS+DRLIC+PNAME |
| 500075 | George Weest | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+PHONE+EMAIL-GENDER |
| 500077 | Annabelle Becker | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DRLIC |
| 500078 | Candace Kellar | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+ADDRESS |
| 500086 | Ellie Ohare | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+PHONE+EMAIL |
| 500087 | George Frankens | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+ADDRESS+PASSPORT |
| 500088 | Beau Smith | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+ADDRESS+PASSPORT |
| 500094 | Geoffrey Brown Jr | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB |
| 500095 | Ray Roderick | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+PHONE |
| 500096 | Amanda Anderson | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +DOB+DRLIC+PNAME |
| 500099 | Mark Miller | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+EMAIL |
| 500103 | Ellie Desqueir | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+DOB+PHONE+EMAIL |
| 500106 | Marie Sanchez Mendoza | 2 | LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN | +NAME+ADDRESS+EMAIL |
| 500172 | John Smith | 2 | SIU_CASES | +NAME+ADDRESS |

## Review queue

Senzing marked 36 entity pairs as possibly the same; 28 of them span more than one source. Each row is one analyst decision away from being acted on: merge, keep apart, or route to the screening queue. Pairs marked Ambiguous could belong to more than one entity.

| Entity A | Entity B | Match key | What to decide |
|---|---|---|---|
| 200001 Gloria Ann Whitfield (CREDIT_BUREAU) | 500049 Susan Mooney (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | +SSN-DOB | Shared SSN, conflicting date of birth: identity verification |
| 200001 Gloria Ann Whitfield (CREDIT_BUREAU) | 500079 Gloria Ann Whitfield (PC_POLICY_ADMIN) | +NAME+ADDRESS-SSN | Strong overlap but not conclusive: analyst decision |
| 200001 Gloria Ann Whitfield (CREDIT_BUREAU) | 500100 Darnell Whitaker (PC_POLICY_ADMIN) | +SSN-DOB | Shared SSN, conflicting date of birth: identity verification |
| 200002 Helen Marquez (CREDIT_BUREAU) | 500073 Dwayne Okafor (PC_POLICY_ADMIN) | +ADDRESS+SSN-DOB | Shared SSN, conflicting date of birth: identity verification |
| 200003 Gerald Pruitt (CREDIT_BUREAU) | 500052 Linda Pruitt (PC_POLICY_ADMIN) | +SURNAME+ADDRESS+PHONE+SSN-DOB-GENDER | Shared SSN, conflicting date of birth: identity verification |
| 400016 Robert Smith (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | 400024 Robert E Smith Sr (PC_POLICY_ADMIN, SIU_CASES) | +NAME+ADDRESS-DOB | Same name and address, different generation or birth date: family member |
| 400016 Robert Smith (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | 400044 Robert Smith (SIU_CASES) | +NAME | Name only: never a hit by itself; likely a common name |
| 400024 Robert E Smith Sr (PC_POLICY_ADMIN, SIU_CASES) | 400044 Robert Smith (SIU_CASES) | +NAME | Name only: never a hit by itself; likely a common name |
| 400029 Patrick Smith (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | 400043 Patricia Smith (SIU_CASES) | +NAME | Name only: never a hit by itself; likely a common name |
| 400029 Patrick Smith (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | 500034 Patricia Smith (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | +NAME+ADDRESS-DOB-PASSPORT | Same name and address, different generation or birth date: family member |
| 400040 David Dobbins Senior (SIU_CASES) | 500083 David Dobbins Jr (PC_POLICY_ADMIN) | +NAME+ADDRESS-GENERATION | Same name and address, different generation or birth date: family member |
| 400043 Patricia Smith (SIU_CASES) | 500034 Patricia Smith (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | +NAME | Name only: never a hit by itself; likely a common name |
| 400044 Robert Smith (SIU_CASES) | 500118 Robert Smith (LIFE_ANNUITY_ADMIN) | +NAME | Name only: never a hit by itself; likely a common name |
| 500013 Bluewater Salvage Co (PC_POLICY_ADMIN) | 500136 Kestrel Marine Supply (LIFE_ANNUITY_ADMIN) | +TAX_ID | Shared tax ID, different names: reused or mis-keyed EIN |
| 500019 Raymond Castillo (PC_POLICY_ADMIN) | 500115 Raymond Castillo (LIFE_ANNUITY_ADMIN) | +NAME+ADDRESS+EMAIL-DOB-SSN | Shared SSN, conflicting date of birth: identity verification |
| 500022 WONDERLAND (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | 500047 MEENU J GUPTA (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | +NAME+ADDRESS+EMAIL-TAX_ID | Strong overlap but not conclusive: analyst decision |
| 500028 GUPTA PRAVEN V (PC_POLICY_ADMIN) | 500047 MEENU J GUPTA (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | +NAME+ADDRESS+EMAIL-PASSPORT-TAX_ID | Strong overlap but not conclusive: analyst decision |
| 500031 Rainier Sleep Clinic (PC_POLICY_ADMIN) | 500117 Alan Reyes (LIFE_ANNUITY_ADMIN) | +NPI_NUMBER-RECORD_TYPE | Strong overlap but not conclusive: analyst decision |
| 500037 Morris Klein I (PC_POLICY_ADMIN) | 500169 Morris Klein II (LIFE_ANNUITY_ADMIN) | +NAME+DOB-GENERATION | Same name and address, different generation or birth date: family member |
| 500041 Halloway Marine Group (PC_POLICY_ADMIN) | 500140 Redstone Auto Parts (LIFE_ANNUITY_ADMIN) | +TAX_ID | Shared tax ID, different names: reused or mis-keyed EIN |
| 500045 John Smith (PC_POLICY_ADMIN) | 500172 John Smith (SIU_CASES) | +NAME+ADDRESS (Ambiguous) | Strong overlap but not conclusive: analyst decision |
| 500046 Halloway Marine Pacific (PC_POLICY_ADMIN) | 500140 Redstone Auto Parts (LIFE_ANNUITY_ADMIN) | +TAX_ID | Shared tax ID, different names: reused or mis-keyed EIN |
| 500049 Susan Mooney (LIFE_ANNUITY_ADMIN, PC_POLICY_ADMIN) | 500100 Darnell Whitaker (PC_POLICY_ADMIN) | +SSN-DOB | Shared SSN, conflicting date of birth: identity verification |
| 500066 Gerald Whitcombe (PC_POLICY_ADMIN) | 500134 Gerald Whitcombe (LIFE_ANNUITY_ADMIN) | +NAME+ADDRESS+EMAIL-DOB-SSN | Shared SSN, conflicting date of birth: identity verification |
| 500072 Leonard Ashgrove (PC_POLICY_ADMIN) | 500164 Leonard Ashgrove (LIFE_ANNUITY_ADMIN) | +NAME+ADDRESS+EMAIL-DOB-SSN | Shared SSN, conflicting date of birth: identity verification |
| 500074 John Smith (PC_POLICY_ADMIN) | 500172 John Smith (SIU_CASES) | +NAME+ADDRESS (Ambiguous) | Strong overlap but not conclusive: analyst decision |
| 500082 Andrew Anderson (PC_POLICY_ADMIN) | 500162 Andy Anderson (LIFE_ANNUITY_ADMIN) | +NAME | Name only: never a hit by itself; likely a common name |
| 500092 Universal Exports Cascadia (PC_POLICY_ADMIN) | 500129 Universal Exports Nordics (LIFE_ANNUITY_ADMIN) | +TAX_ID | Shared tax ID, different names: reused or mis-keyed EIN |

## Why and how: worked examples

**Why Bob Smith and Robert Smith are one customer (whyRecords, entity 400016):** life record 1002 (Bob Smith, 11/12/1978, 702-919-1300) and P&C record 1001 (Robert Smith, 12/11/1978, same phone) resolved on why key +NAME+DOB+PHONE, rule CNAME_CFF_CEXCL. Name scored 97 (CLOSE: Bob is a recognized form of Robert), date of birth 95 (CLOSE: day and month transposed), phone 100 (SAME). The addresses scored 42 (NO_CHANCE) because he moved, and three agreeing features outweighed it.

**Near-miss (the one that teaches more):**
Robert Smith 400016 and the SIU subject Robert Smith 400044 (SIU record 1008) share only a name. whyEntities returns why key +NAME, rule SNAME, level POSSIBLY_SAME: NAME 100 (SAME) but EMAIL 0 (NO_CHANCE: bsmith@work.com vs robert.smith@email.com) and nothing else to compare. Senzing does not merge a common name on its own, so the pair goes to an analyst as a possible match and never becomes an SIU hit on the policyholder.

**How Maria Luis Medina Sentosa was built (howEntity, entity 400030, 7 records):** six resolution steps. Two P&C records joined on +PNAME+DOB+ADDRESS+EMAIL; the two SIU cases joined each other on the same features at a Salem, OR address; a life and a P&C record joined on +NAME+DOB+ADDRESS; those pairs merged on name 96, date of birth 96 (11/12 vs 11/21) and address 100; life record 3026 joined on five identical features despite a different passport (-PASSPORT); and finally the SIU pair joined on +PNAME+DOB+EMAIL with the address scoring 3. That last step is the weakest link and the one an analyst should review first, because it is what puts this customer's two Substantiated SIU cases (a High alert) on her profile. The final state is settled: no re-evaluation pending, one virtual entity.

**A cross-script match:** P&C record 2031 carries the name in Khmer script; life record 2032 carries Dara Kim at a Phnom Penh address. Senzing joined them on +NAME+ADDRESS (entity 500016).

**A transliterated sanctions hit:** entity 400003 joins the OFAC entry 2062 (Russian Cyrillic), a business registry owner record 2061 (Belarusian Cyrillic, +NAME+ADDRESS+PHONE) and P&C customer 2063, Alexander Pavlovich Vasiliev (+NAME+PHONE). Name screening on the Latin name alone would not have matched the list entry.

## Relationship networks

**Beneficial ownership (findNetwork + findPath):** two PEPs, Howard Hughes (400004) and Margaret Charney (400002), each own 50% of Universal Exports Worldwide (400007, a P&C customer), whose subsidiary Universal Exports India (500011) holds a life contract. findPath from Howard Hughes to the India subsidiary is 2 degrees, both links disclosed in the business registry:

```text
Howard Hughes (PEP, 400004)
    |  OWNS 50%
    v
Universal Exports Worldwide (400007, P&C customer)
    |  GLOBAL PARENT of
    v
Universal Exports India (500011, life customer)
```

No single record states that a PEP stands behind a life contract; the path does. The screening queue rolls the subsidiaries into one High alert on the parent for exactly this reason.

**Sanctioned ownership:** Alexander Pavlovich Vasiliev (400003, OFAC SDN) owns 100% of Müllenkranz Autowerkz, which the P&C system holds as Mullenkrants Autoworks (400011, a P&C customer), and Faisal Siddiqui (400001, OFAC SDN) is its president. The business itself appears on no list; the OFAC 50% rule makes it Prohibited.

**The Willow Glen cluster (findNetwork from 300003):** Mildred Thorncastle (a P&C customer) and seven credit bureau files share 1500 Willow Glen Rd, San Jose: eight people born between 1933 and 1937, 28 links, every one +ADDRESS with a conflicting date of birth. It looks like a care home. Address alone is never a hit under the screening rules, so nothing was raised, but it is the shape an SIU would want to see if elder-fraud questions arise.

**A two-hop chain of weak links (hub method):** SIU subject Robert Smith (400044) -> Robert Smith (500118) on +NAME only -> Eddie Kusha (400015) on +ADDRESS-DOB. Two weak links do not make an identity, which is why none of it is a hit.

## What was not found, and why

**Measurement:**
63 of 157 entities span more than one source, and 52 of 179 customer records collapsed into existing customers. Identifiers are sparse in the policy systems (in P&C, date of birth is populated on 60.4% of records, SSN on 27.7%, phone on 30.7%; surnames are 70% unique). Recall could not be measured: no answer key was imported into this project.

**Which case applies:** neither low overlap nor an underperforming pipeline. The data had substantial overlap and Senzing found it. What remains unmerged is the possible-match queue above, which is driven by sparse identifiers and by identifiers deliberately shared between different people; remapping would not change it.

**Match-key audit, by bucket (from Data processing):**

- Cross-source merged records (84 comparisons): almost no disagreement, -GENDER 1 and -PASSPORT 1. Records were merged despite one conflicting feature twice (the passport case is entity 400030).
- Single-source merged records (6 comparisons): no disagreement.
- Relationships not merged (140): -DOB 71, -GENDER 25, -SSN 21. Here the conflict is why Senzing declined to merge: households sharing an address, family members, and people sharing an SSN or tax ID. That is restraint working, and the shared-SSN cases are surfaced as identity-verification alerts.

**Not found by design:** no hit on a common name (John and Robert Smith), a shared household address, or a person who moved. Name-only matches go to an analyst; 20 address, household and employer links between list or SIU entities and customers were deliberately not raised.
