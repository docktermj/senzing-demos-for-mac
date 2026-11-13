# Insurance Claims Fraud: Data Discoveries

Evidence comes from the SDK-generated query reports and the source-overlap assessment. Entity IDs and source-record keys are retained so each observation can be checked; candidate relationships are review leads, not fraud findings.

## Headline numbers, interpreted

The three collected files contain 159 records: 120 POLICYHOLDERS, 22 VENDOR_REGISTRY, and 17 SIU_WATCHLIST. The mapped load contains 162 engine records because the VENDOR_REGISTRY mapping emits three embedded employer records. The SDK-backed export reconciled those 162 records into 90 entities. The How audit found 72 resolution steps across all 52 multi-record entities; these steps show how records joined engine entities, not 72 adjudicated identity truths. Fifteen entities span multiple sources, and the export reports 70 unique relationships. Eleven entities contain at least three records.

These are resolution outcomes and review signals, not measured accuracy. No adjudicated truth labels are available, so precision, recall, and F1 are not measured.

## Merges and match keys

The table lists every resolution step returned by the SDK-backed How audit. Each side is the set of records in a virtual entity immediately before that step; repeated records are expected when a group grows through successive merges.

| Entity | Step | Virtual entity 1 records | Virtual entity 2 records | Match key |
| ---: | ---: | --- | --- | --- |
| 200001 | 1 | VENDOR_REGISTRY/2012 | POLICYHOLDERS/2011 | +NAME+ADDRESS |
| 200002 | 1 | POLICYHOLDERS/1069 | POLICYHOLDERS/1070 | +NAME+DOB+ADDRESS+NATIONAL_ID |
| 200002 | 2 | VENDOR_REGISTRY/2013 | POLICYHOLDERS/1069; POLICYHOLDERS/1070 | +NAME+DOB |
| 200003 | 1 | POLICYHOLDERS/1071 | POLICYHOLDERS/1072 | +NAME+DOB+ADDRESS+NATIONAL_ID |
| 200003 | 2 | VENDOR_REGISTRY/2014 | POLICYHOLDERS/1071; POLICYHOLDERS/1072 | +NAME+DOB |
| 200004 | 1 | VENDOR_REGISTRY/2041 | POLICYHOLDERS/2042 | +NAME+ADDRESS |
| 200005 | 1 | VENDOR_REGISTRY/2051 | SIU_WATCHLIST/2052 | +NAME+ADDRESS+PHONE |
| 200006 | 1 | VENDOR_REGISTRY/2061 | SIU_WATCHLIST/2062 | +NAME+ADDRESS+PHONE |
| 200006 | 2 | VENDOR_REGISTRY/2061; SIU_WATCHLIST/2062 | POLICYHOLDERS/2063 | +NAME+PHONE |
| 200007 | 1 | VENDOR_REGISTRY/2071 | POLICYHOLDERS/2072 | +LIKELY_NAME+ADDRESS+PHONE |
| 200008 | 1 | VENDOR_REGISTRY/2074 | POLICYHOLDERS/2073 | +NAME+ADDRESS |
| 300001 | 1 | VENDOR_REGISTRY/2101 | VENDOR_REGISTRY/2102 | +PNAME+PHONE+EMAIL |
| 300003 | 1 | VENDOR_REGISTRY/2111 | VENDOR_REGISTRY/2112 | +NAME+ADDRESS |
| 300005 | 1 | VENDOR_REGISTRY/2121 | VENDOR_REGISTRY/2122 | +NAME+PHONE |
| 300007 | 1 | VENDOR_REGISTRY/2131 | VENDOR_REGISTRY/2132 | +NAME+ADDRESS |
| 300009 | 1 | VENDOR_REGISTRY/2141 | POLICYHOLDERS/2142 | +NAME+ADDRESS |
| 300010 | 1 | VENDOR_REGISTRY/2151 | POLICYHOLDERS/2152 | +NAME+PHONE |
| 300011 | 1 | VENDOR_REGISTRY/2161 | VENDOR_REGISTRY/2162 | +NAME+ADDRESS+EMAIL |
| 400001 | 1 | SIU_WATCHLIST/1006 | POLICYHOLDERS/1005 | +NAME+DRLIC |
| 400004 | 1 | SIU_WATCHLIST/1014 | POLICYHOLDERS/1009 | +PNAME+DOB+SSN |
| 400004 | 2 | SIU_WATCHLIST/1014; POLICYHOLDERS/1009 | POLICYHOLDERS/1010 | +NAME+DOB+ADDRESS |
| 400004 | 3 | SIU_WATCHLIST/1012 | SIU_WATCHLIST/1014; POLICYHOLDERS/1009; POLICYHOLDERS/1010 | +NAME+DOB+ADDRESS |
| 400004 | 4 | SIU_WATCHLIST/1012; SIU_WATCHLIST/1014; POLICYHOLDERS/1009; POLICYHOLDERS/1010 | POLICYHOLDERS/1011 | +NAME+DOB+ADDRESS |
| 400006 | 1 | SIU_WATCHLIST/1021 | POLICYHOLDERS/1020 | +ADDRESS+SSN+EMAIL |
| 400007 | 1 | SIU_WATCHLIST/1024 | POLICYHOLDERS/1022 | +NAME+DOB |
| 400007 | 2 | SIU_WATCHLIST/1024; POLICYHOLDERS/1022 | POLICYHOLDERS/1023 | +NAME+DOB |
| 400010 | 1 | SIU_WATCHLIST/1037 | SIU_WATCHLIST/1038 | +PNAME+DOB+ADDRESS+EMAIL |
| 400010 | 2 | POLICYHOLDERS/1035 | POLICYHOLDERS/1036 | +NAME+DOB+ADDRESS |
| 400010 | 3 | POLICYHOLDERS/1034 | POLICYHOLDERS/1035; POLICYHOLDERS/1036 | +NAME+DOB+ADDRESS |
| 400010 | 4 | SIU_WATCHLIST/1037; SIU_WATCHLIST/1038 | POLICYHOLDERS/1034; POLICYHOLDERS/1035; POLICYHOLDERS/1036 | +PNAME+DOB+EMAIL |
| 500001 | 1 | POLICYHOLDERS/1002 | POLICYHOLDERS/1004 | +NAME+DOB+ADDRESS |
| 500001 | 2 | POLICYHOLDERS/1002; POLICYHOLDERS/1004 | POLICYHOLDERS/1003 | +NAME+DOB+EMAIL |
| 500001 | 3 | POLICYHOLDERS/1001 | POLICYHOLDERS/1002; POLICYHOLDERS/1003; POLICYHOLDERS/1004 | +NAME+DOB+PHONE |
| 500009 | 1 | POLICYHOLDERS/1015 | POLICYHOLDERS/1016 | +NAME+DOB+ADDRESS |
| 500009 | 2 | POLICYHOLDERS/1015; POLICYHOLDERS/1016 | POLICYHOLDERS/1017 | +NAME+SSN |
| 500009 | 3 | POLICYHOLDERS/1015; POLICYHOLDERS/1016; POLICYHOLDERS/1017 | POLICYHOLDERS/1018 | +NAME+DOB+PHONE |
| 600002 | 1 | SIU_WATCHLIST/1041 | SIU_WATCHLIST/1042 | +NAME+ADDRESS |
| 700010 | 1 | POLICYHOLDERS/1030 | POLICYHOLDERS/1031 | +NAME+DOB+ADDRESS |
| 700012 | 1 | POLICYHOLDERS/1032 | POLICYHOLDERS/1033 | +NAME+DOB+PHONE |
| 700019 | 1 | POLICYHOLDERS/1043 | POLICYHOLDERS/1045 | +NAME+ADDRESS+PASSPORT |
| 700020 | 1 | POLICYHOLDERS/1044 | POLICYHOLDERS/1046 | +NAME+ADDRESS+PASSPORT |
| 700023 | 1 | POLICYHOLDERS/1047 | POLICYHOLDERS/1048 | +NAME+EMAIL |
| 700023 | 2 | POLICYHOLDERS/1047; POLICYHOLDERS/1048 | POLICYHOLDERS/1049 | +NAME+EMAIL |
| 700026 | 1 | POLICYHOLDERS/1050 | POLICYHOLDERS/1051 | +NAME+DOB+PASSPORT |
| 700026 | 2 | POLICYHOLDERS/1050; POLICYHOLDERS/1051 | POLICYHOLDERS/1052 | +NAME+PASSPORT |
| 700029 | 1 | POLICYHOLDERS/1053 | POLICYHOLDERS/1055 | +NAME+ADDRESS+PASSPORT |
| 700030 | 1 | POLICYHOLDERS/1054 | POLICYHOLDERS/1056 | +PNAME+DOB+ADDRESS+DRLIC |
| 700035 | 1 | POLICYHOLDERS/1059 | POLICYHOLDERS/1060 | +NAME+PHONE |
| 700037 | 1 | POLICYHOLDERS/1061 | POLICYHOLDERS/1062 | +NAME+PHONE |
| 700039 | 1 | POLICYHOLDERS/1066 | POLICYHOLDERS/1067 | +NAME+SSN |
| 700039 | 2 | POLICYHOLDERS/1065 | POLICYHOLDERS/1066; POLICYHOLDERS/1067 | +NAME+DRLIC |
| 700039 | 3 | POLICYHOLDERS/1064 | POLICYHOLDERS/1065; POLICYHOLDERS/1066; POLICYHOLDERS/1067 | +NAME+PASSPORT |
| 700039 | 4 | POLICYHOLDERS/1063 | POLICYHOLDERS/1064; POLICYHOLDERS/1065; POLICYHOLDERS/1066; POLICYHOLDERS/1067 | +NAME+DOB |
| 700039 | 5 | POLICYHOLDERS/1063; POLICYHOLDERS/1064; POLICYHOLDERS/1065; POLICYHOLDERS/1066; POLICYHOLDERS/1067 | POLICYHOLDERS/1068 | +NAME+ADDRESS |
| 700049 | 1 | POLICYHOLDERS/1073 | POLICYHOLDERS/1074 | +NAME+DOB+ADDRESS |
| 700051 | 1 | POLICYHOLDERS/1075 | POLICYHOLDERS/1076 | +NAME+DOB+ADDRESS |
| 700053 | 1 | POLICYHOLDERS/1077 | POLICYHOLDERS/1078 | +NAME+DOB+ADDRESS |
| 700055 | 1 | POLICYHOLDERS/1079 | POLICYHOLDERS/1080 | +NAME+DOB |
| 700057 | 1 | POLICYHOLDERS/1081 | POLICYHOLDERS/1082 | +NAME+DOB+ADDRESS+PASSPORT |
| 700059 | 1 | POLICYHOLDERS/1083 | POLICYHOLDERS/1084 | +NAME+DOB+ADDRESS+PASSPORT |
| 700061 | 1 | POLICYHOLDERS/1085 | POLICYHOLDERS/1086 | +NAME+DOB+PHONE+EMAIL |
| 700063 | 1 | POLICYHOLDERS/1087 | POLICYHOLDERS/1088 | +NAME+DOB+PHONE+EMAIL-GENDER |
| 700067 | 1 | POLICYHOLDERS/1091 | POLICYHOLDERS/1092 | +NAME+DOB+PHONE+EMAIL |
| 700069 | 1 | POLICYHOLDERS/1093 | POLICYHOLDERS/1094 | +PNAME+DOB+DRLIC |
| 700071 | 1 | POLICYHOLDERS/1095 | POLICYHOLDERS/1096 | +NAME+DOB+DRLIC |
| 700073 | 1 | POLICYHOLDERS/1097 | POLICYHOLDERS/1098 | +NAME+ADDRESS+EMAIL |
| 700075 | 1 | POLICYHOLDERS/1099 | POLICYHOLDERS/1100 | +NAME+EMAIL |
| 700077 | 1 | POLICYHOLDERS/1101 | POLICYHOLDERS/1102 | +NAME+EMAIL |
| 700079 | 1 | POLICYHOLDERS/1103 | POLICYHOLDERS/1104 | +NAME+DRLIC |
| 700082 | 1 | POLICYHOLDERS/2031 | POLICYHOLDERS/2032 | +NAME+ADDRESS |
| 700095 | 1 | POLICYHOLDERS/2192 | POLICYHOLDERS/2193 | +NAME+DOB+ADDRESS |
| 700103 | 1 | POLICYHOLDERS/2207 | POLICYHOLDERS/2213 | +NAME+ADDRESS |

The report contains 72 steps for 52 entities, and each step has a returned match key. The complete JSON audit, including rule codes, is `reports/query/how_merge_audit.json`.

## Review queue

The SDK scan covered 90 entities and returned 14 distinct cross-source POSSIBLY_SAME pairs. Two pairs have the ambiguity flag set; the other twelve do not. Every pair is a human-review candidate, not an asserted missed match or a fraud determination.

| Entity pair | Sources in entity A | Sources in entity B | Match key | Ambiguous flag |
| --- | --- | --- | --- | ---: |
| 200004 / 300013 | VENDOR_REGISTRY, POLICYHOLDERS | VENDOR_REGISTRY | +NAME | 0 |
| 200007 / 300015 | VENDOR_REGISTRY, POLICYHOLDERS | VENDOR_REGISTRY | +NAME | 0 |
| 200008 / 300014 | VENDOR_REGISTRY, POLICYHOLDERS | VENDOR_REGISTRY | +NAME | 0 |
| 200009 / 600006 | VENDOR_REGISTRY | SIU_WATCHLIST | +NAME | 0 |
| 200010 / 600007 | VENDOR_REGISTRY | SIU_WATCHLIST | +NAME | 0 |
| 400001 / 400003 | SIU_WATCHLIST, POLICYHOLDERS | SIU_WATCHLIST | +NAME | 0 |
| 400001 / 500001 | SIU_WATCHLIST, POLICYHOLDERS | POLICYHOLDERS | +NAME+ADDRESS-DOB | 0 |
| 400002 / 700019 | SIU_WATCHLIST | POLICYHOLDERS | +NAME | 0 |
| 400002 / 700020 | SIU_WATCHLIST | POLICYHOLDERS | +NAME | 0 |
| 400003 / 500001 | SIU_WATCHLIST | POLICYHOLDERS | +NAME | 0 |
| 400008 / 700007 | SIU_WATCHLIST | POLICYHOLDERS | +NAME+DOB | 0 |
| 400009 / 700009 | SIU_WATCHLIST | POLICYHOLDERS | +NAME+ADDRESS-GENERATION | 0 |
| 600002 / 700017 | SIU_WATCHLIST | POLICYHOLDERS | +NAME+ADDRESS (Ambiguous) | 1 |
| 600002 / 700018 | SIU_WATCHLIST | POLICYHOLDERS | +NAME+ADDRESS (Ambiguous) | 1 |

Names are omitted from this compact queue to keep identifiers and review keys prominent. The JSON report includes the entity names and the same source membership: `reports/query/cross_source_review_queue.json`.

## Why and how: worked examples

**Why (resolved example):**
    POLICYHOLDERS/1011 and SIU_WATCHLIST/1014 are both in Entity 400004. The SDK returned RESOLVED with `+NAME+DOB+ADDRESS`. The supporting comparisons were name `Ed Knight` / `Eddie Knight` (97, CLOSE), DOB `3/1/70` / `3/1/70` (100, SAME), and address `1602 Brenville Pl San Francisco CA 94105` / `160 Brenville Pl San Francisco CA 94105` (95, CLOSE). These are the reported feature comparisons for this resolution, not an independent adjudication.

**How (merge sequence):**
    Entity 400004 was built in four steps: SIU_WATCHLIST/1014 joined POLICYHOLDERS/1009 on `+PNAME+DOB+SSN`; POLICYHOLDERS/1010 joined that group on `+NAME+DOB+ADDRESS`; SIU_WATCHLIST/1012 joined the group on `+NAME+DOB+ADDRESS`; and POLICYHOLDERS/1011 joined on `+NAME+DOB+ADDRESS`. The SDK's final How state contains all five records in one virtual entity with no reevaluation pending. See `reports/query/how_analysis.json` for the step evidence.

**Near-miss (the one that teaches more):**
    Entities 400001 and 500001 remain a POSSIBLY_SAME review lead under `+NAME+ADDRESS-DOB`, not a resolved entity. The comparison shows `Robbie Smith` / `Robert Smith` at 97 (CLOSE) and `123 Main St, Las Vegas` / `123 Main Street, Las Vegas NV 89132` at 99 (SAME), but DOB `03/31/1954` / `12/11/1978` at 59 (NO_CHANCE). This is concrete conflicting evidence; it does not establish whether the records are the same person. Details are in `reports/query/quality_samples.json`.

## Relationship networks

The SDK found a two-hop path from Entity 200008 (Universal Exports Worldwide; VENDOR_REGISTRY and POLICYHOLDERS) through Entity 200007 (Universal Exports USA; VENDOR_REGISTRY and POLICYHOLDERS) to Entity 300001 (Jones Kaylee; VENDOR_REGISTRY):

```text
Entity 200008 -> Entity 200007 -> Entity 300001
```

The first disclosed link is supported by reference key 2074 (`Global Parent`) between VENDOR_REGISTRY/2071 and VENDOR_REGISTRY/2074. The second is supported by reference key 2071 (`Principal`) between VENDOR_REGISTRY/2071 and VENDOR_REGISTRY/2101. This is a path across explicit source relationships, not a single record's statement that the endpoints are directly connected. The complete network and path response is `reports/query/relationship_networks.json`.

## What was not found, and why

**Measurement:**
    Distinct-value overlap measured from the improved source files: POLICYHOLDERS x VENDOR_REGISTRY shared 1 phone value and 0 first-name, last-name, DOB, and email values. POLICYHOLDERS x SIU_WATCHLIST shared 6 last names, 5 first names, 3 DOBs, 2 SSNs, 1 email, and 1 phone value. VENDOR_REGISTRY x SIU_WATCHLIST shared 0 phone, first-name, last-name, email, and DOB values. These are counts of distinct values on the named fields, not counts of matching records or entities; see `docs/data_source_evaluation.md`.

The available evidence points to **the data had little overlap to find**, especially for VENDOR_REGISTRY x SIU_WATCHLIST and POLICYHOLDERS x VENDOR_REGISTRY; POLICYHOLDERS x SIU_WATCHLIST does have measured shared values. The cross-source entity review corroborated 8 of 15 sampled entities and left 7 for human follow-up. This evidence does not establish absolute pipeline accuracy or prove that every possible pair should resolve.

The match-key suppressor audit must be read by its original bucket: single-source per-record comparisons had 1 suppressor in 47 comparisons (GENDER); cross-source per-record comparisons had 0 in 24; relationship comparisons had 15 with suppressors in 70 comparisons (DOB 13, SSN 3, GENERATION 2, PASSPORT 1). Feature incidences can overlap, so those feature counts are not additive. A relationship suppressor records a declined entity merge; it is not the same as a per-record suppressor in records that were merged. The audit is documented in `docs/results_validation.md` and `config/bootcamp_progress.json`.

Additional constraints: the three source quality scores remain below the project's 70% threshold, with completeness of 33.2% for POLICYHOLDERS, 38.5% for VENDOR_REGISTRY, and 39.5% for SIU_WATCHLIST. Missing values were not inferred or imputed. Absolute accuracy remains unmeasured because there are no adjudicated truth labels.
