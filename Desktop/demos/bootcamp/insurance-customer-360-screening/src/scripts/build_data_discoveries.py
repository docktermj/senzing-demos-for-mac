"""Build docs/bootcamp_data_discoveries.md from this project's SDK-derived query outputs.

Inputs (all produced through the Senzing SDK, never SQL):
  data/query/app_merges.json        /api/merges of the visualization app (export stream)
  data/query/app_graph.json         /api/graph of the visualization app (entities + relationships)
  data/query/customer_resolution.json, data/query/screening_alerts.json   (src/query programs)
  data/temp/er_stats.json           export statistics from Data processing (src/validate/ExportStats.java)
The worked examples (whyRecords, whyEntities, howEntity, findNetwork, findPath) are the outputs of
src/query/discover_demo.java and entity_evidence.java captured during the Discover phase.

Latin-script only (the PDF fonts are Latin-1): non-Latin entity names are replaced by the Latin-script
name the loaded data itself carries for that entity, listed in LATIN_NAMES with its source record.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
Q = ROOT / "data" / "query"

# entity_id -> (Latin name confirmed in the loaded data, the record carrying it)
LATIN_NAMES = {
    400003: ("Alexander Pavlovich Vasiliev", "PC_POLICY_ADMIN 2063"),
    400001: ("Faisal Siddiqui", "BUSINESS_REGISTRY 2051"),
    500016: ("Dara Kim", "LIFE_ANNUITY_ADMIN 2032"),
}


def name(eid, fallback):
    if eid in LATIN_NAMES:
        return LATIN_NAMES[eid][0]
    return fallback


def esc(s):
    return str(s).replace("|", "/")


def main():
    merges = json.load(open(Q / "app_merges.json"))["entities"]
    graph = json.load(open(Q / "app_graph.json"))
    res = json.load(open(Q / "customer_resolution.json"))
    scr = json.load(open(Q / "screening_alerts.json"))
    stats = json.load(open(ROOT / "data" / "temp" / "er_stats.json"))
    nodes = {n["entity_id"]: n for n in graph["nodes"]}
    out = []
    w = out.append

    w("# Data Discoveries: Insurance Customer 360 and Screening")
    w("")
    w("What Senzing found in your six sources (all synthetic test data). Every figure below comes from the Senzing SDK "
      "(getEntity, the export stream, whyRecords, whyEntities, howEntity, findNetwork, findPath), never from SQL. "
      "Names written in non-Latin scripts are shown by the Latin-script name your own data carries for the same entity: "
      + "; ".join(f"entity {k} as {v[0]} (from {v[1]})" for k, v in LATIN_NAMES.items()) + ".")
    w("")

    # 1
    w("## Headline numbers, interpreted")
    w("")
    w(f"- **{stats['records']} records resolved into {stats['entities']} entities.** {len(merges)} entities hold more than one record, "
      f"and {graph['total'] and sum(1 for n in graph['nodes'] if len(n['data_sources']) > 1)} of them combine records from more than one source. "
      f"Senzing also found {len(graph['edges'])} relationships between entities that it did not merge.")
    w(f"- **{res['customer_records']} customer records are {res['customers']} real customers.** {res['records_collapsed']} customer records "
      f"were duplicates of someone already known: across the two policy systems that share no customer number, and inside each system "
      f"({res['customers_held_more_than_once_in_one_system']['PC_POLICY_ADMIN']} customers held more than once in P&C, "
      f"{res['customers_held_more_than_once_in_one_system']['LIFE_ANNUITY_ADMIN']} in life and annuity).")
    w(f"- **{res['holds_both_pc_and_life']} customers hold both P&C cover and a life or annuity contract.** Neither system could see this. "
      f"{res['pc_only']} are P&C only (life prospects) and {res['life_only']} are life or annuity only (P&C prospects).")
    sev = scr["alerts_by_severity"]
    w(f"- **The screening queue holds {scr['alerts_total']} alerts:** {sev['PROHIBITED']} Prohibited, {sev['HIGH']} High, "
      f"{sev['MEDIUM']} Medium, {sev['LOW']} Low, each with its records, match key or disclosed relationship, and list entry or SIU case.")
    w(f"- **{res['customers_with_a_record_outside_customer_systems']} customers also have a record outside the customer systems** "
      "(credit bureau, business registry, SIU or screening list). That is the overlap the screening queue is built on.")
    w("")

    # 2
    w("## Merges and match keys")
    w("")
    w("Every multi-record entity, with the match key that brought each record in (+ means the feature agreed, - means it "
      "disagreed but other evidence outweighed it). The first record of an entity has no match key.")
    w("")
    w("| Entity | Name | Records | Sources | Match keys that joined its records |")
    w("|---|---|---|---|---|")
    for e in merges:
        keys = sorted({r["match_key"] for r in e["records"] if r.get("match_key")})
        w(f"| {e['entity_id']} | {esc(name(e['entity_id'], e['entity_name']))} | {e['record_count']} | "
          f"{', '.join(e['data_sources'])} | {esc('; '.join(keys))} |")
    w("")

    # 3
    w("## Review queue")
    w("")
    ps = [x for x in graph["edges"] if x["relationship_type"] == "POSSIBLY_SAME"]
    cross = []
    for x in ps:
        a, b = nodes[x["source_entity_id"]], nodes[x["target_entity_id"]]
        if len(set(a["data_sources"]) | set(b["data_sources"])) >= 2:
            cross.append((a, b, x["match_key"]))
    w(f"Senzing marked {len(ps)} entity pairs as possibly the same; {len(cross)} of them span more than one source. Each row "
      "is one analyst decision away from being acted on: merge, keep apart, or route to the screening queue. Pairs marked "
      "Ambiguous could belong to more than one entity.")
    w("")
    w("| Entity A | Entity B | Match key | What to decide |")
    w("|---|---|---|---|")
    for a, b, mk in cross:
        if "SSN" in mk and "-DOB" in mk:
            hint = "Shared SSN, conflicting date of birth: identity verification"
        elif "TAX_ID" in mk and "NAME" not in mk:
            hint = "Shared tax ID, different names: reused or mis-keyed EIN"
        elif mk.replace(" (Ambiguous)", "") in ("+NAME", "+PNAME"):
            hint = "Name only: never a hit by itself; likely a common name"
        elif "-DOB" in mk or "-GENERATION" in mk:
            hint = "Same name and address, different generation or birth date: family member"
        else:
            hint = "Strong overlap but not conclusive: analyst decision"
        w(f"| {a['entity_id']} {esc(name(a['entity_id'], a['entity_name']))} ({', '.join(a['data_sources'])}) | "
          f"{b['entity_id']} {esc(name(b['entity_id'], b['entity_name']))} ({', '.join(b['data_sources'])}) | {esc(mk)} | {hint} |")
    w("")

    # 4
    w("## Why and how: worked examples")
    w("")
    w("**Why Bob Smith and Robert Smith are one customer (whyRecords, entity 400016):** life record 1002 (Bob Smith, "
      "11/12/1978, 702-919-1300) and P&C record 1001 (Robert Smith, 12/11/1978, same phone) resolved on why key "
      "+NAME+DOB+PHONE, rule CNAME_CFF_CEXCL. Name scored 97 (CLOSE: Bob is a recognized form of Robert), date of birth "
      "95 (CLOSE: day and month transposed), phone 100 (SAME). The addresses scored 42 (NO_CHANCE) because he moved, and "
      "three agreeing features outweighed it.")
    w("")
    w("**Near-miss (the one that teaches more):**")
    w("Robert Smith 400016 and the SIU subject Robert Smith 400044 (SIU record 1008) share only a name. whyEntities returns "
      "why key +NAME, rule SNAME, level POSSIBLY_SAME: NAME 100 (SAME) but EMAIL 0 (NO_CHANCE: bsmith@work.com vs "
      "robert.smith@email.com) and nothing else to compare. Senzing does not merge a common name on its own, so the pair "
      "goes to an analyst as a possible match and never becomes an SIU hit on the policyholder.")
    w("")
    w("**How Maria Luis Medina Sentosa was built (howEntity, entity 400030, 7 records):** six resolution steps. Two P&C "
      "records joined on +PNAME+DOB+ADDRESS+EMAIL; the two SIU cases joined each other on the same features at a Salem, OR "
      "address; a life and a P&C record joined on +NAME+DOB+ADDRESS; those pairs merged on name 96, date of birth 96 "
      "(11/12 vs 11/21) and address 100; life record 3026 joined on five identical features despite a different passport "
      "(-PASSPORT); and finally the SIU pair joined on +PNAME+DOB+EMAIL with the address scoring 3. That last step is the "
      "weakest link and the one an analyst should review first, because it is what puts this customer's two Substantiated "
      "SIU cases (a High alert) on her profile. The final state is settled: no re-evaluation pending, one virtual entity.")
    w("")
    w("**A cross-script match:** P&C record 2031 carries the name in Khmer script; life record 2032 carries Dara Kim at a "
      "Phnom Penh address. Senzing joined them on +NAME+ADDRESS (entity 500016).")
    w("")
    w("**A transliterated sanctions hit:** entity 400003 joins the OFAC entry 2062 (Russian Cyrillic), a business registry "
      "owner record 2061 (Belarusian Cyrillic, +NAME+ADDRESS+PHONE) and P&C customer 2063, Alexander Pavlovich Vasiliev "
      "(+NAME+PHONE). Name screening on the Latin name alone would not have matched the list entry.")
    w("")

    # 5
    w("## Relationship networks")
    w("")
    w("**Beneficial ownership (findNetwork + findPath):** two PEPs, Howard Hughes (400004) and Margaret Charney (400002), "
      "each own 50% of Universal Exports Worldwide (400007, a P&C customer), whose subsidiary Universal Exports India "
      "(500011) holds a life contract. findPath from Howard Hughes to the India subsidiary is 2 degrees, both links "
      "disclosed in the business registry:")
    w("")
    w("```")
    w("Howard Hughes (PEP, 400004)")
    w("    |  OWNS 50%")
    w("    v")
    w("Universal Exports Worldwide (400007, P&C customer)")
    w("    |  GLOBAL PARENT of")
    w("    v")
    w("Universal Exports India (500011, life customer)")
    w("```")
    w("")
    w("No single record states that a PEP stands behind a life contract; the path does. The screening queue rolls the "
      "subsidiaries into one High alert on the parent for exactly this reason.")
    w("")
    w("**Sanctioned ownership:** Alexander Pavlovich Vasiliev (400003, OFAC SDN) owns 100% of Müllenkranz "
      "Autowerkz, which the P&C system holds as Mullenkrants Autoworks (400011, a P&C customer), and Faisal Siddiqui (400001, OFAC SDN) is its president. The business itself "
      "appears on no list; the OFAC 50% rule makes it Prohibited.")
    w("")
    w("**The Willow Glen cluster (findNetwork from 300003):** Mildred Thorncastle (a P&C customer) and seven credit bureau "
      "files share 1500 Willow Glen Rd, San Jose: eight people born between 1933 and 1937, 28 links, every one "
      "+ADDRESS with a conflicting date of birth. It looks like a care home. Address alone is never a hit under the "
      "screening rules, so nothing was raised, but it is the shape an SIU would want to see if elder-fraud questions arise.")
    w("")
    w("**A two-hop chain of weak links (hub method):** SIU subject Robert Smith (400044) -> Robert Smith (500118) on "
      "+NAME only -> Eddie Kusha (400015) on +ADDRESS-DOB. Two weak links do not make an identity, which is why none of it "
      "is a hit.")
    w("")

    # 6
    sb = stats["suppressor_buckets"]
    w("## What was not found, and why")
    w("")
    w("**Measurement:**")
    w(f"{stats['cross_source_entities']} of {stats['entities']} entities span more than one source, and {res['records_collapsed']} of "
      f"{res['customer_records']} customer records collapsed into existing customers. Identifiers are sparse in the policy systems "
      "(in P&C, date of birth is populated on 60.4% of records, SSN on 27.7%, phone on 30.7%; surnames are 70% unique). "
      "Recall could not be measured: no answer key was imported into this project.")
    w("")
    w("**Which case applies:** neither low overlap nor an underperforming pipeline. The data had substantial overlap and "
      "Senzing found it. What remains unmerged is the possible-match queue above, which is driven by sparse identifiers "
      "and by identifiers deliberately shared between different people; remapping would not change it.")
    w("")
    w("**Match-key audit, by bucket (from Data processing):**")
    w("")
    w(f"- Cross-source merged records ({sb['cross_source_per_record']['denominator']} comparisons): almost no disagreement, "
      "-GENDER 1 and -PASSPORT 1. Records were merged despite one conflicting feature twice (the passport case is entity 400030).")
    w(f"- Single-source merged records ({sb['single_source_per_record']['denominator']} comparisons): no disagreement.")
    rel = sb["relationship"]
    w(f"- Relationships not merged ({rel['denominator']}): -DOB {rel['suppressors'].get('DOB', 0)}, -GENDER "
      f"{rel['suppressors'].get('GENDER', 0)}, -SSN {rel['suppressors'].get('SSN', 0)}. Here the conflict is why Senzing "
      "declined to merge: households sharing an address, family members, and people sharing an SSN or tax ID. That is "
      "restraint working, and the shared-SSN cases are surfaced as identity-verification alerts.")
    w("")
    w("**Not found by design:** no hit on a common name (John and Robert Smith), a shared household address, or a person "
      "who moved. Name-only matches go to an analyst; 20 address, household and employer links between list or SIU "
      "entities and customers were deliberately not raised.")
    w("")

    path = ROOT / "docs" / "bootcamp_data_discoveries.md"
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"WROTE {path} ({len(out)} lines, {len(merges)} merges, {len(cross)} review pairs)")


if __name__ == "__main__":
    main()
