// explain_how.java -- Query, Visualize and Discover, Step 4c: how one entity was built, step by step.
//
// howEntity(entityId, flags) (signature from get_sdk_reference, language='java'). Flags:
// SZ_HOW_ENTITY_DEFAULT_FLAGS (= SZ_INCLUDE_FEATURE_SCORES, scores at each step) plus
// SZ_INCLUDE_MATCH_KEY_DETAILS with SZ_ENTITY_INCLUDE_ALL_RELATIONS (the details flag depends on a
// relations flag); the breakdown is conditional, so its absence is stated and feature scores are shown.
// Response (get_sdk_reference topic='response_schemas', filter='how_entity_by_entity_id'):
//   HOW_RESULTS.RESOLUTION_STEPS[]: STEP, VIRTUAL_ENTITY_1 / VIRTUAL_ENTITY_2 (objects with
//   VIRTUAL_ENTITY_ID and MEMBER_RECORDS[].RECORDS[].{DATA_SOURCE, RECORD_ID}), RESULT_VIRTUAL_ENTITY_ID,
//   MATCH_INFO.{MATCH_KEY, ERRULE_CODE, FEATURE_SCORES, MATCH_KEY_DETAILS (conditional)};
//   HOW_RESULTS.FINAL_STATE.{NEED_REEVALUATION, VIRTUAL_ENTITIES[]}.
//
// Usage: src/scripts/run-java.sh ExplainHow <ENTITY_ID> [--raw]
import java.util.*;
import com.senzing.sdk.*;

import static com.senzing.sdk.SzFlag.*;

class ExplainHow {
    static String members(Map<String, Object> ve) {
        List<String> out = new ArrayList<>();
        for (Object m : Json.arr(ve.get("MEMBER_RECORDS"))) {
            for (Object r : Json.arr(Json.obj(m).get("RECORDS"))) {
                Map<String, Object> rec = Json.obj(r);
                out.add(Json.str(rec.get("DATA_SOURCE"), "?") + " " + Json.str(rec.get("RECORD_ID"), "?"));
            }
        }
        return String.join(", ", out);
    }

    public static void main(String[] args) throws Exception {
        long entityId = Long.parseLong(args[0]);
        boolean raw = args.length > 1 && args[1].equals("--raw");
        Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
        flags.addAll(SZ_HOW_ENTITY_DEFAULT_FLAGS);
        flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
        flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        try (SenzingSession s = new SenzingSession(ExplainHow.class.getSimpleName())) {
            String resp = s.engine.howEntity(entityId, flags);
            if (raw) { System.out.println("RAW: " + resp); }
            Map<String, Object> hr = Json.obj(Json.obj(Json.parse(resp)).get("HOW_RESULTS"));
            List<Object> steps = new ArrayList<>(Json.arr(hr.get("RESOLUTION_STEPS")));
            steps.sort(Comparator.comparingLong(o -> Json.lng(Json.obj(o).get("STEP"))));
            System.out.println("Entity " + entityId + ": " + steps.size() + " resolution step(s)");
            for (Object o : steps) {
                Map<String, Object> st = Json.obj(o);
                Map<String, Object> v1 = Json.obj(st.get("VIRTUAL_ENTITY_1")), v2 = Json.obj(st.get("VIRTUAL_ENTITY_2"));
                Map<String, Object> mi = Json.obj(st.get("MATCH_INFO"));
                System.out.println();
                System.out.println("Step " + Json.lng(st.get("STEP")) + ": [" + members(v1) + "] (" + Json.str(v1.get("VIRTUAL_ENTITY_ID"), "?")
                    + ") joined [" + members(v2) + "] (" + Json.str(v2.get("VIRTUAL_ENTITY_ID"), "?") + ") -> "
                    + Json.str(st.get("RESULT_VIRTUAL_ENTITY_ID"), "?"));
                System.out.println("   match key " + Json.str(mi.get("MATCH_KEY"), "(none)") + "   rule " + Json.str(mi.get("ERRULE_CODE"), "(none)"));
                System.out.println("   MATCH_INFO keys: " + new TreeSet<>(mi.keySet()));
                Map<String, Object> scores = mi.get("FEATURE_SCORES") == null ? Map.of() : Json.obj(mi.get("FEATURE_SCORES"));
                for (Map.Entry<String, Object> f : scores.entrySet()) {
                    for (Object so : Json.arr(f.getValue())) {
                        Map<String, Object> sc = Json.obj(so);
                        System.out.printf("     %-11s %-38s vs %-38s %3s %s%n", f.getKey(), Json.str(sc.get("INBOUND_FEAT_DESC"), "-"),
                            Json.str(sc.get("CANDIDATE_FEAT_DESC"), "-"), sc.get("SCORE") == null ? "-" : String.valueOf(Json.lng(sc.get("SCORE"))),
                            Json.str(sc.get("SCORE_BUCKET"), ""));
                    }
                }
                if (mi.get("MATCH_KEY_DETAILS") == null) {
                    System.out.println("   match-key breakdown not returned for these flags; feature scores above carry the evidence");
                } else {
                    List<String> c = new ArrayList<>();
                    for (Object x : Json.arr(Json.obj(mi.get("MATCH_KEY_DETAILS")).get("CONFIRMATIONS"))) {
                        Map<String, Object> cm = Json.obj(x);
                        c.add("+" + Json.str(cm.get("FTYPE_CODE"), "?") + " (" + Json.str(cm.get("SCORE_BUCKET"), "") + ")");
                    }
                    System.out.println("   confirmations: " + (c.isEmpty() ? "(present but empty)" : String.join(" ", c)));
                }
            }
            Map<String, Object> fs = hr.get("FINAL_STATE") == null ? null : Json.obj(hr.get("FINAL_STATE"));
            System.out.println();
            if (fs == null) {
                System.out.println("FINAL_STATE: not returned -- construction state not measured");
            } else {
                long need = fs.get("NEED_REEVALUATION") == null ? 0 : Json.lng(fs.get("NEED_REEVALUATION"));
                int virt = Json.arr(fs.get("VIRTUAL_ENTITIES")).size();
                System.out.println("FINAL_STATE: NEED_REEVALUATION=" + need + ", VIRTUAL_ENTITIES=" + virt
                    + (need == 0 && virt == 1 ? "  (one settled construction)" : "  (UNSETTLED: see Data processing's how-state audit)"));
            }
        }
    }
}
