package query;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import server.Json;

/**
 * Query requirements 5-9: the screening and due diligence queue.
 *
 * Applies each line of business's rules (docs/reference/screening_rules.md) to the resolved entities:
 * sanctions, SIU history, PEPs, possible matches, identity verification against credit bureau files, and
 * ownership screening with the OFAC 50% rule and roll-up to the parent. Every finding carries its evidence:
 * the customer records, the match key or disclosed relationship, and the list entry or SIU case. Possible
 * matches also carry Senzing's whyEntities explanation. The queue never shows premiums, face amounts or
 * account values. Analyst dispositions (requirement 10) are stored by the web app, never in Senzing.
 *
 * Usage: src/scripts/sz-java.sh run query.ScreeningQueue
 * Writes data/query/screening_alerts.json.
 * The top-level class is package-private so it may live in this snake_case file.
 */
class ScreeningQueue {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        try (InsuranceModel m = InsuranceModel.open(ScreeningQueue.class.getSimpleName())) {
            Map<String, Object> s = m.screening();
            InsuranceModel.writeJson(Path.of("data", "query", "screening_alerts.json"), s);
            System.out.println("ALERTS=" + s.get("alerts_total") + " " + Json.write(s.get("alerts_by_severity")));
            for (Object o : (List<Object>) s.get("alerts")) {
                Map<String, Object> a = (Map<String, Object>) o;
                System.out.println(a.get("severity") + "\t" + a.get("subject_entity_id") + "\t" + a.get("subject_name")
                    + "\t(" + a.get("subject_kind") + ")");
                for (Object fo : (List<Object>) a.get("findings")) {
                    Map<String, Object> f = (Map<String, Object>) fo;
                    Map<String, Object> ev = Json.obj(f.get("evidence"));
                    Object key = ev.get("match_key") != null ? ev.get("match_key") : ev.get("relationship_match_key");
                    System.out.println("    " + f.get("type") + " " + f.get("severity")
                        + (Boolean.TRUE.equals(f.get("folded_into_hit")) ? " (folded)" : "")
                        + ": " + f.get("summary") + (key != null ? "  [" + key + "]" : ""));
                }
            }
            System.out.println("INNOCENT=" + Json.write(s.get("innocent_explanations")));
            System.out.println("WROTE data/query/screening_alerts.json");
        }
    }
}
