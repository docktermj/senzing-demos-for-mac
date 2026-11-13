// explain_match.java -- Query, Visualize and Discover: why two records were, or were not, resolved
// to the same customer.
//
// whyRecords(recordKey1, recordKey2, flags) (signature from get_sdk_reference, language='java').
// Flags: SZ_WHY_RECORDS_DEFAULT_FLAGS (= SZ_INCLUDE_FEATURE_SCORES, the per-feature comparison) plus
// SZ_ENTITY_INCLUDE_ENTITY_NAME (the default composite carries no name flag) plus
// SZ_INCLUDE_MATCH_KEY_DETAILS with SZ_ENTITY_INCLUDE_ALL_RELATIONS (the details flag depends on a
// relations flag). Response fields per get_sdk_reference(topic='response_schemas', filter='why_records'):
// WHY_RESULTS[].ENTITY_ID / ENTITY_ID_2 and MATCH_INFO.{MATCH_LEVEL_CODE, WHY_KEY, WHY_ERRULE_CODE,
// FEATURE_SCORES, WHY_KEY_DETAILS (conditional)} -- the WHY_* names, not the entity-side MATCH_KEY.
//
// Usage: src/scripts/run-java.sh ExplainMatch <DATA_SOURCE> <RECORD_ID> <DATA_SOURCE> <RECORD_ID> [--raw]
import java.util.*;
import com.senzing.sdk.*;

import static com.senzing.sdk.SzFlag.*;

class ExplainMatch {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: ExplainMatch <DS1> <ID1> <DS2> <ID2> [--raw]");
            System.exit(2);
        }
        boolean raw = args.length > 4 && args[4].equals("--raw");
        SzRecordKey k1 = SzRecordKey.of(args[0], args[1]), k2 = SzRecordKey.of(args[2], args[3]);
        Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
        flags.addAll(SZ_WHY_RECORDS_DEFAULT_FLAGS);
        flags.add(SZ_ENTITY_INCLUDE_ENTITY_NAME);
        flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
        flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        try (SenzingSession s = new SenzingSession(ExplainMatch.class.getSimpleName())) {
            String resp = s.engine.whyRecords(k1, k2, flags);
            if (raw) { System.out.println("RAW: " + resp); }
            Map<String, Object> doc = Json.obj(Json.parse(resp));
            Map<Long, String> names = new HashMap<>();
            for (Object o : Json.arr(doc.get("ENTITIES"))) {
                Map<String, Object> re = Json.obj(Json.obj(o).get("RESOLVED_ENTITY"));
                names.put(Json.lng(re.get("ENTITY_ID")), Json.str(re.get("ENTITY_NAME"), "(no name returned)"));
            }
            for (Object o : Json.arr(doc.get("WHY_RESULTS"))) {
                Map<String, Object> w = Json.obj(o);
                long e1 = Json.lng(w.get("ENTITY_ID")), e2 = Json.lng(w.get("ENTITY_ID_2"));
                Map<String, Object> mi = Json.obj(w.get("MATCH_INFO"));
                String level = Json.str(mi.get("MATCH_LEVEL_CODE"), "");
                String key = Json.str(mi.get("WHY_KEY"), "");
                System.out.println(k1 + " is in customer " + e1 + " (" + names.getOrDefault(e1, "?") + ")");
                System.out.println(k2 + " is in customer " + e2 + " (" + names.getOrDefault(e2, "?") + ")");
                System.out.println(e1 == e2
                    ? "RESOLVED: the two records are the same customer."
                    : "NOT RESOLVED: the two records are different customers" + (level.isEmpty() ? "." : " (relationship: " + level + ")."));
                System.out.println("why key: " + (key.isEmpty() ? "(none: nothing in common worth relating)" : key)
                    + "   rule: " + Json.str(mi.get("WHY_ERRULE_CODE"), "(none)"));
                System.out.println("feature comparison:");
                Map<String, Object> scores = mi.get("FEATURE_SCORES") == null ? Map.of() : Json.obj(mi.get("FEATURE_SCORES"));
                if (scores.isEmpty()) { System.out.println("   (no feature scores returned)"); }
                for (Map.Entry<String, Object> f : scores.entrySet()) {
                    for (Object so : Json.arr(f.getValue())) {
                        Map<String, Object> sc = Json.obj(so);
                        System.out.printf("   %-12s %-40s vs %-40s score %3s  %s%n", f.getKey(),
                            Json.str(sc.get("INBOUND_FEAT_DESC"), "-"), Json.str(sc.get("CANDIDATE_FEAT_DESC"), "-"),
                            sc.get("SCORE") == null ? "-" : String.valueOf(Json.lng(sc.get("SCORE"))), Json.str(sc.get("SCORE_BUCKET"), ""));
                    }
                }
                Object details = mi.get("WHY_KEY_DETAILS");
                if (details == null) {
                    System.out.println("why-key breakdown: not returned for these flags (feature comparison above is the fallback)");
                } else {
                    List<Object> conf = Json.arr(Json.obj(details).get("CONFIRMATIONS"));
                    System.out.println("why-key breakdown: " + conf.size() + " confirmation(s)"
                        + (conf.isEmpty() ? " (present but empty: nothing confirmed the relationship)" : ""));
                    for (Object c : conf) {
                        Map<String, Object> cm = Json.obj(c);
                        System.out.println("   + " + Json.str(cm.get("FTYPE_CODE"), "?") + " " + Json.str(cm.get("TOKEN"), "") + " ("
                            + Json.str(cm.get("SCORE_BUCKET"), "") + ")");
                    }
                }
            }
        }
    }
}
