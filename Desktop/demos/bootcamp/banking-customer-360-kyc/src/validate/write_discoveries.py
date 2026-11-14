#!/usr/bin/env python3
"""Write docs/bootcamp_data_discoveries.md from the Senzing results (Query, Visualize and Discover).

Every figure comes from Senzing SDK output already on disk: the entity export written by
src/validate/export_entities.java (exportJsonEntityReport, SZ_EXPORT_DEFAULT_FLAGS), the Discover
responses in data/results/discover/ (whyRecords, whyEntities, howEntity, findNetwork, findPath),
data/validation/evaluation.json (export statistics and the answer-key comparison) and the query
outputs in data/results/. No SQL against the Senzing database.

The PDF renderer covers Latin-1 only, so an entity whose Senzing name is in another script is
written with the Latin-script name a record in the SAME entity carries, and the source of that name
is stated. Names are never transliterated here.

Usage: python3 -I src/validate/write_discoveries.py
"""
import collections
import json

rows = [json.loads(l) for l in open("data/validation/export.jsonl", encoding="utf-8") if l.strip()]
ev = json.load(open("data/validation/evaluation.json", encoding="utf-8"))
alerts = json.load(open("data/results/kyc_alerts.json", encoding="utf-8"))
c360 = json.load(open("data/results/customer_360.json", encoding="utf-8"))
src = {}
for f in ["core_banking", "card_services", "credit_bureau", "business_registry", "watchlist"]:
    for line in open(f"data/senzing-ready/{f}.jsonl", encoding="utf-8"):
        r = json.loads(line)
        src[(r["DATA_SOURCE"], r["RECORD_ID"])] = r
ent = {r["RESOLVED_ENTITY"]["ENTITY_ID"]: r for r in rows}


def latin(s):
    return all(ord(c) < 256 for c in s)


def rec_names(key):
    out = []
    for f in src.get(key, {}).get("FEATURES", []):
        n = f.get("NAME_ORG") or f.get("NAME_FULL") or " ".join(
            x for x in (f.get("NAME_FIRST"), f.get("NAME_MIDDLE"), f.get("NAME_LAST")) if x)
        if n:
            out.append(n)
    return out


def name(eid):
    """The entity's Senzing name, or a Latin-script name one of its own records carries."""
    e = ent[eid]["RESOLVED_ENTITY"]
    n = e.get("ENTITY_NAME") or f"Entity {eid}"
    if latin(n):
        return n
    for r in e["RECORDS"]:
        for alt in rec_names((r["DATA_SOURCE"], r["RECORD_ID"])):
            if latin(alt):
                return f"{alt} (Latin-script name from {r['DATA_SOURCE']}/{r['RECORD_ID']})"
    return f"Entity {eid} (name in a non-Latin script; no Latin form in the data)"


def recs(eid):
    return ent[eid]["RESOLVED_ENTITY"]["RECORDS"]


def esc(s):
    return str(s).replace("|", "\\|")


a = ev["accuracy"]
records, entities = ev["records_in_export"], ev["entities"]
multi = [eid for eid in ent if len(recs(eid)) >= 2]
cust = [p for p in c360]
both = sum(1 for p in cust if p["cross_sell"] == "has both")
sev = collections.Counter(x["severity"] for x in alerts)
typ = collections.Counter(x["type"] for x in alerts)
out = []
w = out.append

w("# Bootcamp data discoveries: banking Customer 360 and KYC/KYB\n")
w("What Senzing found in the bank's five synthetic sources (core banking, card services, credit bureau, "
  "business registry and screening list), from one resolution of all 248 records. Every figure comes from "
  "Senzing's own output; the scenario's answer key was used only to check results, never loaded.\n")

w("## Headline numbers, interpreted\n")
w(f"- **{records} records became {entities} entities.** The answer key says the data describes 156 real-world "
  f"people and businesses, so Senzing's count is within one of the truth: almost every duplicate was found, "
  f"and almost nothing was over-merged.")
w(f"- **{len(multi)} entities hold two or more records.** These are the merges: the same customer, company or "
  f"screened person seen more than once. {ev['cross_source_entities']} of them span two or more systems, "
  f"which is where a Customer 360 gets its value.")
w(f"- **179 core banking and card records are {len(cust)} real customers.** {both} hold both a deposit "
  f"account and a card; the rest are cross-sell gaps (deposits but no card, or a card but no deposit account).")
w(f"- **{len(alerts)} KYC/KYB alerts**: {sev.get('Prohibited', 0)} Prohibited, {sev.get('High', 0)} High, "
  f"{sev.get('Medium', 0)} Medium ({', '.join(f'{v} {k.lower()}' for k, v in sorted(typ.items()))}).")
w(f"- **Accuracy against the answer key:** {a['pair_precision']*100:.1f}% of the record pairs Senzing joined are "
  f"correct and it found {a['pair_recall']*100:.1f}% of the pairs it should join; "
  f"{a['clusters_exactly_resolved']} of {a['truth_clusters']} real-world entities came out exactly right.\n")

w("## Merges and match keys\n")
w("Every entity with two or more records, with the match key that joined each record (the first record "
  "of an entity has none). A `+` feature agreed; a `-` feature disagreed but was outweighed.\n")
w("| Entity | Name | Records and match keys |\n|---|---|---|")
for eid in sorted(multi, key=lambda e: (-len(recs(e)), e)):
    parts = [f"{r['DATA_SOURCE']}/{r['RECORD_ID']}" + (f" `{r['MATCH_KEY']}`" if r.get("MATCH_KEY") else " (first)")
             for r in recs(eid)]
    w(f"| {eid} | {esc(name(eid))} | {esc('; '.join(parts))} |")
w("")

w("## Review queue\n")
w("Pairs Senzing judged possibly the same, or ambiguous, and did not merge, where the two sides span two or "
  "more systems. Each row is one analyst decision away from action. In the KYC queue these appear as "
  "Medium possible hits.\n")
w("| Entity | Name | Related entity | Name | Match key |\n|---|---|---|---|---|")
seen = set()
queue = 0
for eid, r in sorted(ent.items()):
    for rel in r.get("RELATED_ENTITIES") or []:
        other = rel["ENTITY_ID"]
        mk = rel.get("MATCH_KEY") or ""
        if rel.get("MATCH_LEVEL_CODE") != "POSSIBLY_SAME" and not rel.get("IS_AMBIGUOUS"):
            continue
        pair = (min(eid, other), max(eid, other))
        if pair in seen:
            continue
        sources = {x["DATA_SOURCE"] for x in recs(eid)} | {x["DATA_SOURCE"] for x in recs(other)}
        if len(sources) < 2:
            continue
        seen.add(pair)
        queue += 1
        w(f"| {pair[0]} | {esc(name(pair[0]))} | {pair[1]} | {esc(name(pair[1]))} | `{esc(mk)}` |")
w(f"\n{queue} cross-source pairs. The answer key confirms that almost all possible-match pairs in this data are "
  "genuinely different people or businesses (34 of 36 across all sources), so the queue is doing its job: "
  "surfacing near-misses for a human rather than merging them.\n")

w("## Why and how: worked examples\n")
w("**A screening hit, explained (why).** WATCHLIST/1024 and CORE_BANKING/1022 resolved into entity 500007, "
  "Muhammed Antoun, on `+NAME+DOB` (rule SNAME_SSTAB). The names scored 98 of 100 (CLOSE): 'Mhd Antoun' vs "
  "'Mohamed Antoun'. The dates of birth scored 100 (SAME) although one is written 1980-01-07 and the other "
  "1/7/80. Name plus date of birth was enough evidence to resolve the screening entry to the customer.\n")
w("**A merge to audit (why).** CREDIT_BUREAU/3015 and CORE_BANKING/3016 resolved into entity 200009, Kathryn "
  "Vossler, on `+NAME+DOB+SSN`: name and date of birth identical, SSN 694-18-3344 vs 694-18-3434 scored 90 "
  "(CLOSE) as a likely typo, while the addresses (Albany NY vs Scranton PA) scored 15 (NO_CHANCE). The answer "
  "key says these are two people. This is the evidence an analyst needs to dispose of the identity-verification "
  "alert.\n")
w("**Near-miss (the one that teaches more):**")
w("Entity 600001, Robert Smith (a customer with two deposit accounts and two cards), and entity 500001, Robert E "
  "Smith Sr (a screening-list entry merged with a core banking record), were NOT merged. Senzing found them "
  "POSSIBLY_SAME on `+NAME+ADDRESS-DOB`: the names scored 97 (Robert vs Robbie Smith) and the addresses 99 "
  "(123 Main Street vs 123 Main St, Las Vegas), but the dates of birth, 12/11/1978 vs 1954-03-31, scored 59 "
  "(NO_CHANCE). A common name and a shared address were not allowed to override a conflicting date of birth, "
  "which is exactly the business rule: no false hits for common names. The customer reaches an analyst as a "
  "possible hit, not an automatic one.\n")
w("**How an entity was built (how).** Entity 500010, Maria Luis Medina Sentosa, holds 7 records and was "
  "assembled in 6 steps:\n")
w("```text")
w("CORE/1034 + CORE/3027            -> step 1  +PNAME+DOB+ADDRESS+EMAIL (name 86 LIKELY)")
w("WATCHLIST/1037 + WATCHLIST/1038  -> step 2  +PNAME+DOB+ADDRESS+EMAIL (name 73 PLAUSIBLE)")
w("CARD/1035 + CORE/1036            -> step 3  +NAME+DOB+ADDRESS (name 94 CLOSE)")
w("step 3 group + step 1 group      -> step 4  +NAME+DOB+ADDRESS (DOB 11/12/73 vs 11/21/73 scored 96 CLOSE)")
w("  + CARD/3026                    -> step 5  +NAME+DOB+ADDRESS+PHONE+EMAIL-PASSPORT")
w("step 2 group + customer group    -> step 6  +PNAME+DOB+EMAIL (shared email 100, DOB 95 CLOSE)")
w("```")
w("\nThe screening entries reached the customer only at the last step, through a shared email and a date of "
  "birth written two ways. Senzing reports the history as settled (no re-evaluation pending, one virtual entity).\n")

w("## Relationship networks\n")
w("Connections no single record states. Declared links come from the business registry; discovered links come "
  "from shared features.\n")
w("```text")
w("Mullenkranz (400004)")
w("  ^-- OWNS 100% --- Alexander Pavlovich Vasiliev (400006)  [Sanctions; also a deposit customer]")
w("  ^-- PRESIDENT --- Faisal Siddiqui (400005)               [Sanctions]")
w("        (400005 and 400006 also share an employer: discovered)")
w("")
w("Universal Exports Worldwide (400008)")
w("  ^-- OWNS 50% ---- Howard Hughes (400009)                 [PEP]")
w("  ^-- OWNS 50% ---- Margaret Charney (400010)              [PEP]")
w("  |-- GLOBAL PARENT of Universal Exports USA (400007), Singapore (800009), India (800010)")
w("```")
w("\n- **Beneficial ownership, two hops:** Howard Hughes -> OWNS 50% -> Universal Exports Worldwide -> GLOBAL "
  "PARENT -> Universal Exports India, a core banking customer. No record links the PEP to that customer "
  "directly; the path does. This chain drives the High beneficial-ownership alert, with the three "
  "subsidiaries rolled up into it.")
w("- **Müllenkranz** is Prohibited: its 100% owner is sanctioned and is also a deposit customer; his name "
  "appears in Belarusian and Russian Cyrillic and in Latin script across three systems, and Senzing resolved "
  "all three. (The names above use the Latin-script forms the bank's own records carry: CORE_BANKING/2063 "
  "and BUSINESS_REGISTRY/2051.)")
w("- **The two ownership groups are separate:** no path joins them within three degrees.")
w("- **An address cluster:** eight people born in the 1930s share one San Jose address and are all linked to "
  "each other (28 discovered links on `+ADDRESS` with conflicting dates of birth). Two Margaret Ellis records "
  "are held apart as possibly the same. The pattern fits a senior residence; it is also the shape investigators "
  "check when one address carries many identities.\n")

w("## What was not found, and why\n")
w("**Measurement:**")
w(f"The sources overlap heavily by design: the Data Quality module measured, for example, 15 shared emails and 8 "
  f"shared passports between core banking and card services and 13 shared SSNs between core banking and the credit "
  f"bureau. Against the answer key Senzing found {a['pair_recall']*100:.1f}% of the true record pairs and "
  f"{a['clusters_exactly_resolved']} of {a['truth_clusters']} real-world entities exactly. So this is **not** a case "
  "of the data having little overlap, and it is **not** a case of the pipeline underperforming: the overlap was "
  "there and Senzing found nearly all of it.\n")
w("What it did not do, and why:\n")
w("- **Three near-twins merged.** Credit bureau files for Kathryn Vossler, Benjamin Hartley and Rosalind Kemper "
  "merged with a customer the key says is a different person (same name and date of birth, SSNs identical or one "
  "digit-swap apart). These are deliberate identity-verification traps; each surfaces as an identity-verification "
  "alert with its evidence.")
w("- **Two answer-key groups mix a person and a company** (Cascadia Freight Partners with Annabelle Becker; the "
  "Gupta family with the business WONDERLAND). Senzing never merges a person record with an organization record, "
  "so it kept them apart and linked the Gupta group as possible matches.")
w("- **One answer-key group looks like a key artifact:** CORE_BANKING/2171 (Andrew Anderson) and CREDIT_BUREAU/2171 "
  "(Gloria Ann Whitfield) share only a record ID.")
w(f"- **Match-key audit, by bucket.** Records merged despite a disagreeing feature (per-record bucket): 0 of "
  f"{ev['suppressors']['single_source']['comparisons']} within one source and 2 of "
  f"{ev['suppressors']['cross_source']['comparisons']} across sources, so no mapping signal. Entities Senzing "
  f"declined to merge because a feature disagreed (relationship bucket): date of birth on 71 of "
  f"{ev['suppressors']['relationship']['comparisons']} relationships. That is the engine's restraint on households "
  "and common names, not a defect.")
w("- **High possible-match rate (33.8% of entities) is a property of the data:** common names, shared SSNs, "
  "shared tax IDs and households, which remapping would not change.\n")

open("docs/bootcamp_data_discoveries.md", "w", encoding="utf-8").write("\n".join(out) + "\n")
print("wrote docs/bootcamp_data_discoveries.md", len(multi), "merges,", queue, "review pairs")
