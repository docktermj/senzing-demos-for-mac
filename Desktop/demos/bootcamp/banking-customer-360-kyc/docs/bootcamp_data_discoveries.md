# Bootcamp data discoveries: banking Customer 360 and KYC/KYB

What Senzing found in the bank's five synthetic sources (core banking, card services, credit bureau, business registry and screening list), from one resolution of all 248 records. Every figure comes from Senzing's own output; the scenario's answer key was used only to check results, never loaded.

## Headline numbers, interpreted

- **248 records became 157 entities.** The answer key says the data describes 156 real-world people and businesses, so Senzing's count is within one of the truth: almost every duplicate was found, and almost nothing was over-merged.
- **69 entities hold two or more records.** These are the merges: the same customer, company or screened person seen more than once. 63 of them span two or more systems, which is where a Customer 360 gets its value.
- **179 core banking and card records are 127 real customers.** 38 hold both a deposit account and a card; the rest are cross-sell gaps (deposits but no card, or a card but no deposit account).
- **36 KYC/KYB alerts**: 3 Prohibited, 9 High, 24 Medium (2 beneficial ownership, 16 identity verification, 9 possible screening hit, 9 screening hit).
- **Accuracy against the answer key:** 97.8% of the record pairs Senzing joined are correct and it found 92.2% of the pairs it should join; 147 of 156 real-world entities came out exactly right.

## Merges and match keys

Every entity with two or more records, with the match key that joined each record (the first record of an entity has none). A `+` feature agreed; a `-` feature disagreed but was outweighed.

| Entity | Name | Records and match keys |
|---|---|---|
| 500010 | Maria Luis Medina Sentosa | WATCHLIST/1037 (first); CARD_SERVICES/1035 (first); CORE_BANKING/1034 (first); WATCHLIST/1038 `+DOB+ADDRESS+EMAIL+PNAME`; CORE_BANKING/1036 `+NAME+DOB+ADDRESS`; CORE_BANKING/3027 `+DOB+ADDRESS+EMAIL+PNAME`; CARD_SERVICES/3026 `+NAME+DOB+ADDRESS+PHONE+EMAIL-PASSPORT` |
| 1000020 | Susan Mooney | CORE_BANKING/1067 (first); CORE_BANKING/1063 `+NAME+DOB`; CORE_BANKING/1065 `+NAME+DRLIC`; CARD_SERVICES/1064 `+NAME+PASSPORT`; CARD_SERVICES/1066 `+NAME+SSN`; CARD_SERVICES/1068 `+NAME+ADDRESS` |
| 500004 | Eddie Kusha | WATCHLIST/1014 (first); WATCHLIST/1012 `+NAME+DOB+ADDRESS`; CORE_BANKING/1009 `+DOB+SSN+PNAME`; CORE_BANKING/1011 `+NAME+DOB+ADDRESS`; CARD_SERVICES/1010 `+NAME+DOB+ADDRESS` |
| 600001 | Robert Smith | CARD_SERVICES/1002 (first); CORE_BANKING/1001 `+NAME+DOB+PHONE`; CORE_BANKING/1003 `+NAME+DOB+EMAIL`; CARD_SERVICES/1004 `+NAME+DOB+ADDRESS` |
| 600006 | Marie Kusha | CORE_BANKING/1015 (first); CORE_BANKING/1017 `+NAME+SSN`; CARD_SERVICES/1016 `+NAME+DOB+ADDRESS`; CARD_SERVICES/1018 `+NAME+DOB+PHONE` |
| 400002 | Jie Wang | BUSINESS_REGISTRY/2013 (first); CORE_BANKING/1069 `+NAME+DOB`; CARD_SERVICES/1070 `+NAME+DOB+ADDRESS+NATIONAL_ID` |
| 400003 | Wang Wei | BUSINESS_REGISTRY/2014 (first); CORE_BANKING/1071 `+NAME+DOB`; CARD_SERVICES/1072 `+NAME+DOB+ADDRESS+NATIONAL_ID` |
| 400006 | Alexander Pavlovich Vasiliev (Latin-script name from CORE_BANKING/2063) | BUSINESS_REGISTRY/2061 (first); WATCHLIST/2062 `+NAME+ADDRESS+PHONE`; CORE_BANKING/2063 `+NAME+PHONE` |
| 500007 | Muhammed Antoun | WATCHLIST/1024 (first); CORE_BANKING/1022 `+NAME+DOB`; CARD_SERVICES/1023 `+NAME+DOB` |
| 1000012 | Sahra Thompson | CORE_BANKING/1047 (first); CORE_BANKING/1049 `+NAME+EMAIL`; CARD_SERVICES/1048 `+NAME+EMAIL` |
| 1000014 | Leigh Toulouse | CORE_BANKING/1050 (first); CORE_BANKING/1052 `+NAME+PASSPORT`; CARD_SERVICES/1051 `+NAME+DOB+PASSPORT` |
| 200004 | Marcus Ellery | CREDIT_BUREAU/3007 (first); CARD_SERVICES/3008 `+NAME+DOB+SSN` |
| 200009 | Kathryn Vossler | CREDIT_BUREAU/3015 (first); CORE_BANKING/3016 `+NAME+DOB+SSN` |
| 200010 | Benjamin Hartley | CREDIT_BUREAU/3017 (first); CORE_BANKING/3018 `+NAME+DOB+SSN` |
| 300001 | Rosalind Kemper | CREDIT_BUREAU/3019 (first); CORE_BANKING/3020 `+NAME+DOB+SSN` |
| 300002 | Eleanor Pruitt | CREDIT_BUREAU/3028 (first); CORE_BANKING/3029 `+NAME+DOB+SSN` |
| 300005 | Ruth Abernathy-Coles | CREDIT_BUREAU/3034 (first); CORE_BANKING/3035 `+NAME+DOB+SSN` |
| 300006 | Florence Wickersham | CREDIT_BUREAU/3037 (first); CARD_SERVICES/3038 `+NAME+DOB+SSN` |
| 300007 | Imogen Halvorsen | CREDIT_BUREAU/3040 (first); CORE_BANKING/3041 `+NAME+DOB+PHONE+SSN` |
| 300008 | Beatrice Lindqvist | CREDIT_BUREAU/3042 (first); CORE_BANKING/3043 `+NAME+DOB+SSN` |
| 300009 | Gwendolyn Farraday | CREDIT_BUREAU/3045 (first); CORE_BANKING/3046 `+NAME+DOB+PHONE+SSN` |
| 300011 | Philippa Strand | CREDIT_BUREAU/3048 (first); CORE_BANKING/3049 `+NAME+DOB+PHONE+SSN` |
| 300012 | Cornelius Ashby | CREDIT_BUREAU/3051 (first); CORE_BANKING/3052 `+NAME+DOB+PHONE+SSN` |
| 300013 | Mildred Thorncastle | CREDIT_BUREAU/3054 (first); CORE_BANKING/3055 `+NAME+DOB+PHONE+SSN` |
| 400001 | Hajah Mamunah Jln Pisang | BUSINESS_REGISTRY/2012 (first); CORE_BANKING/2011 `+NAME+ADDRESS` |
| 400004 | Mullenkrants | BUSINESS_REGISTRY/2041 (first); CARD_SERVICES/2042 `+NAME+ADDRESS` |
| 400005 | Faisal Siddiqui (Latin-script name from BUSINESS_REGISTRY/2051) | BUSINESS_REGISTRY/2051 (first); WATCHLIST/2052 `+NAME+ADDRESS+PHONE` |
| 400007 | Universal Exports USA | BUSINESS_REGISTRY/2071 (first); CARD_SERVICES/2072 `+ADDRESS+PHONE+LIKELY_NAME` |
| 400008 | Universal Exports Worldwide | BUSINESS_REGISTRY/2074 (first); CARD_SERVICES/2073 `+NAME+ADDRESS` |
| 400009 | Howard Hughes | BUSINESS_REGISTRY/2081 (first); WATCHLIST/2082 `+NAME+EMPLOYER` |
| 400010 | Margaret Charney | BUSINESS_REGISTRY/2091 (first); WATCHLIST/2092 `+NAME+EMPLOYER` |
| 500001 | Robert E Smith Sr | WATCHLIST/1006 (first); CORE_BANKING/1005 `+NAME+DRLIC` |
| 500006 | Marsha Kusha | WATCHLIST/1021 (first); CORE_BANKING/1020 `+ADDRESS+SSN+EMAIL` |
| 700007 | Louis Garsky | CARD_SERVICES/1031 (first); CORE_BANKING/1030 `+NAME+DOB+ADDRESS` |
| 700008 | Daniella Shaw | CARD_SERVICES/1033 (first); CORE_BANKING/1032 `+NAME+DOB+PHONE` |
| 700010 | Patrick Smith | CARD_SERVICES/1045 (first); CORE_BANKING/1043 `+NAME+ADDRESS+PASSPORT` |
| 800001 | Jones Kaylee | BUSINESS_REGISTRY/2101 (first); BUSINESS_REGISTRY/2102 `+PHONE+EMAIL+PNAME` |
| 800003 | Susan Meyer Thomas | BUSINESS_REGISTRY/2111 (first); BUSINESS_REGISTRY/2112 `+NAME+ADDRESS` |
| 800005 | Kristen Salinger | BUSINESS_REGISTRY/2121 (first); BUSINESS_REGISTRY/2122 `+NAME+PHONE` |
| 800007 | Rosemary A Thomas | BUSINESS_REGISTRY/2131 (first); BUSINESS_REGISTRY/2132 `+NAME+ADDRESS` |
| 800009 | Universal Exports Singapore | BUSINESS_REGISTRY/2141 (first); CARD_SERVICES/2142 `+NAME+ADDRESS` |
| 800010 | Universal Exports India | BUSINESS_REGISTRY/2151 (first); CORE_BANKING/2152 `+NAME+ADDRESS+PHONE` |
| 800011 | Anastassia Alexopoulos | BUSINESS_REGISTRY/2161 (first); BUSINESS_REGISTRY/2162 `+NAME+ADDRESS+EMAIL` |
| 900002 | John Smith | WATCHLIST/1041 (first); WATCHLIST/1042 `+NAME+ADDRESS` |
| 1000011 | Patricia Smith | CORE_BANKING/1044 (first); CARD_SERVICES/1046 `+NAME+ADDRESS+PASSPORT` |
| 1000016 | Beau Smith | CORE_BANKING/1053 (first); CARD_SERVICES/1055 `+NAME+ADDRESS+PASSPORT` |
| 1000017 | Magdalena Jones | CORE_BANKING/1054 (first); CARD_SERVICES/1056 `+DOB+ADDRESS+DRLIC+PNAME` |
| 1000018 | Ray Roderick | CORE_BANKING/1059 (first); CARD_SERVICES/1060 `+NAME+PHONE` |
| 1000019 | Andreason | CORE_BANKING/1061 (first); CARD_SERVICES/1062 `+NAME+PHONE` |
| 1000025 | Wei Zhang | CORE_BANKING/1073 (first); CARD_SERVICES/1074 `+NAME+DOB+ADDRESS` |
| 1000026 | Xiu Ying Zhang | CORE_BANKING/1075 (first); CARD_SERVICES/1076 `+NAME+DOB+ADDRESS` |
| 1000027 | Jie Liu | CORE_BANKING/1077 (first); CARD_SERVICES/1078 `+NAME+DOB+ADDRESS` |
| 1000028 | Geoffrey Brown Jr | CORE_BANKING/1079 (first); CARD_SERVICES/1080 `+NAME+DOB` |
| 1000029 | George Frankens | CORE_BANKING/1081 (first); CARD_SERVICES/1082 `+NAME+DOB+ADDRESS+PASSPORT` |
| 1000030 | Morrie Klempsky | CORE_BANKING/1083 (first); CARD_SERVICES/1084 `+NAME+DOB+ADDRESS+PASSPORT` |
| 1000031 | Ellie Desqueir | CORE_BANKING/1085 (first); CARD_SERVICES/1086 `+NAME+DOB+PHONE+EMAIL` |
| 1000032 | George Weest | CORE_BANKING/1087 (first); CARD_SERVICES/1088 `+NAME+DOB+PHONE+EMAIL-GENDER` |
| 1000034 | Ellie Ohare | CORE_BANKING/1091 (first); CARD_SERVICES/1092 `+NAME+DOB+PHONE+EMAIL` |
| 1000035 | Amanda Anderson | CORE_BANKING/1093 (first); CARD_SERVICES/1094 `+DOB+DRLIC+PNAME` |
| 1000036 | Juann Aguilar | CORE_BANKING/1095 (first); CARD_SERVICES/1096 `+NAME+DOB+DRLIC` |
| 1000037 | Marie Sanchez Mendoza | CORE_BANKING/1097 (first); CARD_SERVICES/1098 `+NAME+ADDRESS+EMAIL` |
| 1000038 | Anna Maria Aguilar | CORE_BANKING/1099 (first); CARD_SERVICES/1100 `+NAME+EMAIL` |
| 1000039 | Mark Miller | CORE_BANKING/1101 (first); CARD_SERVICES/1102 `+NAME+EMAIL` |
| 1000040 | Annabelle Becker | CORE_BANKING/1103 (first); CARD_SERVICES/1104 `+NAME+DRLIC` |
| 1000042 | Dara Kim (Latin-script name from CARD_SERVICES/2032) | CORE_BANKING/2031 (first); CARD_SERVICES/2032 `+NAME+ADDRESS` |
| 1000047 | Jane Muir | CORE_BANKING/2192 (first); CARD_SERVICES/2193 `+NAME+DOB+ADDRESS` |
| 1000050 | Candace Kellar | CORE_BANKING/2207 (first); CARD_SERVICES/2213 `+NAME+ADDRESS` |
| 1000072 | MEENU J GUPTA | CORE_BANKING/3023 (first); CARD_SERVICES/3022 `+NAME+PASSPORT` |
| 1000073 | WONDERLAND | CORE_BANKING/3025 (first); CARD_SERVICES/3024 `+NAME+DOB+TAX_ID` |

## Review queue

Pairs Senzing judged possibly the same, or ambiguous, and did not merge, where the two sides span two or more systems. Each row is one analyst decision away from action. In the KYC queue these appear as Medium possible hits.

| Entity | Name | Related entity | Name | Match key |
|---|---|---|---|---|
| 200001 | Gloria Ann Whitfield | 1000020 | Susan Mooney | `+SSN-DOB` |
| 200001 | Gloria Ann Whitfield | 1000054 | Darnell Whitaker | `+SSN-DOB` |
| 200001 | Gloria Ann Whitfield | 1000061 | Gloria Ann Whitfield | `+NAME+ADDRESS-SSN` |
| 200002 | Helen Marquez | 1000062 | Dwayne Okafor | `+ADDRESS+SSN-DOB` |
| 200003 | Gerald Pruitt | 1000065 | Linda Pruitt | `+SURNAME+ADDRESS+PHONE+SSN-DOB-GENDER` |
| 500001 | Robert E Smith Sr | 500003 | Robert Smith | `+NAME` |
| 500001 | Robert E Smith Sr | 600001 | Robert Smith | `+NAME+ADDRESS-DOB` |
| 500002 | Patricia Smith | 700010 | Patrick Smith | `+NAME` |
| 500002 | Patricia Smith | 1000011 | Patricia Smith | `+NAME` |
| 500003 | Robert Smith | 600001 | Robert Smith | `+NAME` |
| 500003 | Robert Smith | 1100049 | Robert Smith | `+NAME` |
| 500009 | David Dobbins Senior | 1000003 | David Dobbins Jr | `+NAME+ADDRESS-GENERATION` |
| 700010 | Patrick Smith | 1000011 | Patricia Smith | `+NAME+ADDRESS-DOB-PASSPORT` |
| 900002 | John Smith | 1000008 | John Smith | `+NAME+ADDRESS (Ambiguous)` |
| 900002 | John Smith | 1000009 | John Smith | `+NAME+ADDRESS (Ambiguous)` |
| 1000020 | Susan Mooney | 1000054 | Darnell Whitaker | `+SSN-DOB` |
| 1000033 | Morris Klein I | 1100023 | Morris Klein II | `+NAME+DOB-GENERATION` |
| 1000045 | Andrew Anderson | 1100036 | Andy Anderson | `+NAME` |
| 1000056 | Universal Exports Nordics | 1100050 | Universal Exports Cascadia | `+TAX_ID` |
| 1000059 | Kestrel Marine Supply | 1100056 | Bluewater Salvage Co | `+TAX_ID` |
| 1000060 | Redstone Auto Parts | 1100057 | Halloway Marine Group | `+TAX_ID` |
| 1000060 | Redstone Auto Parts | 1100058 | Halloway Marine Pacific | `+TAX_ID` |
| 1000071 | GUPTA PRAVEN V | 1000072 | MEENU J GUPTA | `+NAME+ADDRESS+EMAIL-PASSPORT-TAX_ID` |
| 1000072 | MEENU J GUPTA | 1000073 | WONDERLAND | `+NAME+ADDRESS+EMAIL-TAX_ID` |
| 1000085 | Raymond Castillo | 1100067 | Raymond Castillo | `+NAME+ADDRESS+EMAIL-DOB-SSN` |
| 1000086 | Gerald Whitcombe | 1100069 | Gerald Whitcombe | `+NAME+ADDRESS+EMAIL-DOB-SSN` |
| 1000087 | Leonard Ashgrove | 1100071 | Leonard Ashgrove | `+NAME+ADDRESS+EMAIL-DOB-SSN` |

27 cross-source pairs. The answer key confirms that almost all possible-match pairs in this data are genuinely different people or businesses (34 of 36 across all sources), so the queue is doing its job: surfacing near-misses for a human rather than merging them.

## Why and how: worked examples

**A screening hit, explained (why).** WATCHLIST/1024 and CORE_BANKING/1022 resolved into entity 500007, Muhammed Antoun, on `+NAME+DOB` (rule SNAME_SSTAB). The names scored 98 of 100 (CLOSE): 'Mhd Antoun' vs 'Mohamed Antoun'. The dates of birth scored 100 (SAME) although one is written 1980-01-07 and the other 1/7/80. Name plus date of birth was enough evidence to resolve the screening entry to the customer.

**A merge to audit (why).** CREDIT_BUREAU/3015 and CORE_BANKING/3016 resolved into entity 200009, Kathryn Vossler, on `+NAME+DOB+SSN`: name and date of birth identical, SSN 694-18-3344 vs 694-18-3434 scored 90 (CLOSE) as a likely typo, while the addresses (Albany NY vs Scranton PA) scored 15 (NO_CHANCE). The answer key says these are two people. This is the evidence an analyst needs to dispose of the identity-verification alert.

**Near-miss (the one that teaches more):**
Entity 600001, Robert Smith (a customer with two deposit accounts and two cards), and entity 500001, Robert E Smith Sr (a screening-list entry merged with a core banking record), were NOT merged. Senzing found them POSSIBLY_SAME on `+NAME+ADDRESS-DOB`: the names scored 97 (Robert vs Robbie Smith) and the addresses 99 (123 Main Street vs 123 Main St, Las Vegas), but the dates of birth, 12/11/1978 vs 1954-03-31, scored 59 (NO_CHANCE). A common name and a shared address were not allowed to override a conflicting date of birth, which is exactly the business rule: no false hits for common names. The customer reaches an analyst as a possible hit, not an automatic one.

**How an entity was built (how).** Entity 500010, Maria Luis Medina Sentosa, holds 7 records and was assembled in 6 steps:

```text
CORE/1034 + CORE/3027            -> step 1  +PNAME+DOB+ADDRESS+EMAIL (name 86 LIKELY)
WATCHLIST/1037 + WATCHLIST/1038  -> step 2  +PNAME+DOB+ADDRESS+EMAIL (name 73 PLAUSIBLE)
CARD/1035 + CORE/1036            -> step 3  +NAME+DOB+ADDRESS (name 94 CLOSE)
step 3 group + step 1 group      -> step 4  +NAME+DOB+ADDRESS (DOB 11/12/73 vs 11/21/73 scored 96 CLOSE)
  + CARD/3026                    -> step 5  +NAME+DOB+ADDRESS+PHONE+EMAIL-PASSPORT
step 2 group + customer group    -> step 6  +PNAME+DOB+EMAIL (shared email 100, DOB 95 CLOSE)
```

The screening entries reached the customer only at the last step, through a shared email and a date of birth written two ways. Senzing reports the history as settled (no re-evaluation pending, one virtual entity).

## Relationship networks

Connections no single record states. Declared links come from the business registry; discovered links come from shared features.

```text
Mullenkranz (400004)
  ^-- OWNS 100% --- Alexander Pavlovich Vasiliev (400006)  [Sanctions; also a deposit customer]
  ^-- PRESIDENT --- Faisal Siddiqui (400005)               [Sanctions]
        (400005 and 400006 also share an employer: discovered)

Universal Exports Worldwide (400008)
  ^-- OWNS 50% ---- Howard Hughes (400009)                 [PEP]
  ^-- OWNS 50% ---- Margaret Charney (400010)              [PEP]
  |-- GLOBAL PARENT of Universal Exports USA (400007), Singapore (800009), India (800010)
```

- **Beneficial ownership, two hops:** Howard Hughes -> OWNS 50% -> Universal Exports Worldwide -> GLOBAL PARENT -> Universal Exports India, a core banking customer. No record links the PEP to that customer directly; the path does. This chain drives the High beneficial-ownership alert, with the three subsidiaries rolled up into it.
- **Müllenkranz** is Prohibited: its 100% owner is sanctioned and is also a deposit customer; his name appears in Belarusian and Russian Cyrillic and in Latin script across three systems, and Senzing resolved all three. (The names above use the Latin-script forms the bank's own records carry: CORE_BANKING/2063 and BUSINESS_REGISTRY/2051.)
- **The two ownership groups are separate:** no path joins them within three degrees.
- **An address cluster:** eight people born in the 1930s share one San Jose address and are all linked to each other (28 discovered links on `+ADDRESS` with conflicting dates of birth). Two Margaret Ellis records are held apart as possibly the same. The pattern fits a senior residence; it is also the shape investigators check when one address carries many identities.

## What was not found, and why

**Measurement:**
The sources overlap heavily by design: the Data Quality module measured, for example, 15 shared emails and 8 shared passports between core banking and card services and 13 shared SSNs between core banking and the credit bureau. Against the answer key Senzing found 92.2% of the true record pairs and 147 of 156 real-world entities exactly. So this is **not** a case of the data having little overlap, and it is **not** a case of the pipeline underperforming: the overlap was there and Senzing found nearly all of it.

What it did not do, and why:

- **Three near-twins merged.** Credit bureau files for Kathryn Vossler, Benjamin Hartley and Rosalind Kemper merged with a customer the key says is a different person (same name and date of birth, SSNs identical or one digit-swap apart). These are deliberate identity-verification traps; each surfaces as an identity-verification alert with its evidence.
- **Two answer-key groups mix a person and a company** (Cascadia Freight Partners with Annabelle Becker; the Gupta family with the business WONDERLAND). Senzing never merges a person record with an organization record, so it kept them apart and linked the Gupta group as possible matches.
- **One answer-key group looks like a key artifact:** CORE_BANKING/2171 (Andrew Anderson) and CREDIT_BUREAU/2171 (Gloria Ann Whitfield) share only a record ID.
- **Match-key audit, by bucket.** Records merged despite a disagreeing feature (per-record bucket): 0 of 6 within one source and 2 of 83 across sources, so no mapping signal. Entities Senzing declined to merge because a feature disagreed (relationship bucket): date of birth on 71 of 140 relationships. That is the engine's restraint on households and common names, not a defect.
- **High possible-match rate (33.8% of entities) is a property of the data:** common names, shared SSNs, shared tax IDs and households, which remapping would not change.
