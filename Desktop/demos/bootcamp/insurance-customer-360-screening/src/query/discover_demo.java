package query;

import java.util.*;

import com.senzing.sdk.SzEntityIds;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.SzRecordKey;

import server.Json;

import static com.senzing.sdk.SzFlag.*;

/**
 * Discover phase demonstrations, through the SDK only.
 *
 * Modes:
 *   patterns
 *       Walks EVERY record loaded (all six sources, folded on DATA_SOURCE + RECORD_ID), asks Senzing for its
 *       entity with getEntity(SzRecordKey, SZ_ENTITY_DEFAULT_FLAGS + record JSON), and reports entities with
 *       3+ records, the cross-source ones among them, and entities with relationships (RELATED_ENTITIES[],
 *       disclosed when IS_DISCLOSED = 1, otherwise discovered by Senzing).
 *   why-records <DS1> <ID1> <DS2> <ID2>
 *       whyRecords(SzRecordKey, SzRecordKey, Set<SzFlag>) (get_sdk_reference, java). Flags:
 *       SZ_WHY_RECORDS_DEFAULT_FLAGS (= SZ_INCLUDE_FEATURE_SCORES) + SZ_ENTITY_INCLUDE_ENTITY_NAME +
 *       SZ_INCLUDE_MATCH_KEY_DETAILS, which depends on a relations flag, so SZ_ENTITY_INCLUDE_ALL_RELATIONS
 *       (a Set<SzFlag> composite in Java, merged with addAll). Prints MATCH_INFO's keys first, then
 *       WHY_KEY, WHY_ERRULE_CODE, WHY_KEY_DETAILS (if returned) and FEATURE_SCORES.
 *
 * Usage: src/scripts/sz-java.sh run query.DiscoverDemo patterns | why-records ...
 * The top-level class is package-private so it may live in this snake_case file.
 */
class DiscoverDemo {

    public static void main(String[] args) throws Exception {
        try (InsuranceModel m = InsuranceModel.open(DiscoverDemo.class.getSimpleName())) {
            switch (args[0]) {
                case "patterns": patterns(m); break;
                case "why-records": whyRecords(m, args[1], args[2], args[3], args[4]); break;
                case "how": how(m, Long.parseLong(args[1])); break;
                case "network": {
                    List<Long> ids = new ArrayList<>();
                    for (int i = 2; i < args.length; i++) ids.add(Long.parseLong(args[i]));
                    network(m, ids, Integer.parseInt(args[1]));
                    break;
                }
                case "path": path(m, Long.parseLong(args[1]), Long.parseLong(args[2]), Integer.parseInt(args[3])); break;
                case "hub-path": hubPath(m); break;
                default: throw new IllegalArgumentException("unknown mode " + args[0]);
            }
        }
    }

    static void patterns(InsuranceModel m) throws Exception {
        Map<Long, Map<String, Object>> seen = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(InsuranceModel.CUSTOMER_SOURCES);
        all.addAll(InsuranceModel.OUTSIDE_SOURCES);
        int records = 0;
        for (String s : all) {
            for (String id : m.recordIds(s)) {
                records++;
                Map<String, Object> e = m.entityByRecord(s, id);
                seen.putIfAbsent(InsuranceModel.entityId(e), e);
            }
        }
        List<Map<String, Object>> big = new ArrayList<>(), cross = new ArrayList<>(), rel = new ArrayList<>();
        for (Map<String, Object> e : seen.values()) {
            List<Map<String, Object>> recs = InsuranceModel.records(e);
            Set<String> srcs = new TreeSet<>();
            for (Map<String, Object> r : recs) srcs.add(Json.str(r.get("DATA_SOURCE")));
            int disclosed = 0, discovered = 0;
            for (Map<String, Object> r : InsuranceModel.related(e)) {
                if (InsuranceModel.num(r.get("IS_DISCLOSED")) == 1) disclosed++; else discovered++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity_id", InsuranceModel.entityId(e));
            row.put("name", InsuranceModel.entityName(e));
            row.put("records", recs.size());
            row.put("sources", srcs);
            row.put("disclosed", disclosed);
            row.put("discovered", discovered);
            if (recs.size() >= 3) {
                big.add(row);
                if (srcs.size() >= 2) cross.add(row);
            }
            if (disclosed + discovered > 0) rel.add(row);
        }
        Comparator<Map<String, Object>> byRecords = Comparator.comparingInt(r -> -((Integer) r.get("records")));
        big.sort(byRecords);
        rel.sort(Comparator.comparingInt(r -> -((Integer) r.get("disclosed") + (Integer) r.get("discovered"))));
        System.out.println("RECORDS_WALKED=" + records + " ENTITIES=" + seen.size());
        System.out.println("MULTI_RECORD_3PLUS=" + big.size() + " CROSS_SOURCE_AMONG_THEM=" + cross.size()
            + " ENTITIES_WITH_RELATIONSHIPS=" + rel.size());
        System.out.println("TOP_MULTI_RECORD:");
        for (Map<String, Object> r : big) System.out.println("  " + Json.write(r));
        System.out.println("TOP_RELATIONSHIPS:");
        for (Map<String, Object> r : rel.subList(0, Math.min(10, rel.size()))) System.out.println("  " + Json.write(r));
    }

    /**
     * howEntity(long, Set<SzFlag>) (get_sdk_reference, java). Flags: SZ_HOW_ENTITY_DEFAULT_FLAGS
     * (= SZ_INCLUDE_FEATURE_SCORES) + SZ_INCLUDE_MATCH_KEY_DETAILS + SZ_ENTITY_INCLUDE_ALL_RELATIONS (the
     * relations flag it depends on). Paths from response_schemas: HOW_RESULTS.RESOLUTION_STEPS[].{STEP,
     * VIRTUAL_ENTITY_1, VIRTUAL_ENTITY_2 (objects with MEMBER_RECORDS[].RECORDS[]), INBOUND_VIRTUAL_ENTITY_ID,
     * RESULT_VIRTUAL_ENTITY_ID, MATCH_INFO.{MATCH_KEY, ERRULE_CODE, MATCH_KEY_DETAILS, FEATURE_SCORES}} and
     * HOW_RESULTS.FINAL_STATE.{NEED_REEVALUATION, VIRTUAL_ENTITIES[]}.
     */
    static void how(InsuranceModel m, long entityId) throws Exception {
        Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
        flags.addAll(SZ_HOW_ENTITY_DEFAULT_FLAGS);
        flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
        flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        Map<String, Object> raw = Json.parseObject(m.engine().howEntity(entityId, flags));
        Map<String, Object> hr = Json.obj(raw.get("HOW_RESULTS"));
        Map<String, Object> fs = Json.obj(hr.get("FINAL_STATE"));
        System.out.println("FINAL_STATE NEED_REEVALUATION=" + fs.get("NEED_REEVALUATION")
            + " VIRTUAL_ENTITIES=" + Json.arr(fs.get("VIRTUAL_ENTITIES")).size());
        List<Object> steps = Json.arr(hr.get("RESOLUTION_STEPS"));
        if (!steps.isEmpty()) System.out.println("FIRST_STEP_KEYS=" + Json.obj(steps.get(0)).keySet()
            + " MATCH_INFO_KEYS=" + Json.obj(Json.obj(steps.get(0)).get("MATCH_INFO")).keySet());
        for (Object o : steps) {
            Map<String, Object> s = Json.obj(o);
            Map<String, Object> mi = Json.obj(s.get("MATCH_INFO"));
            System.out.println("STEP " + s.get("STEP") + ": " + side(s.get("VIRTUAL_ENTITY_1")) + "  +  "
                + side(s.get("VIRTUAL_ENTITY_2")) + "  ->  " + s.get("RESULT_VIRTUAL_ENTITY_ID")
                + "   key=" + mi.get("MATCH_KEY") + " rule=" + mi.get("ERRULE_CODE"));
            if (mi.containsKey("MATCH_KEY_DETAILS")) {
                for (Object c : Json.arr(Json.obj(mi.get("MATCH_KEY_DETAILS")).get("CONFIRMATIONS"))) {
                    Map<String, Object> x = Json.obj(c);
                    System.out.println("      confirm " + x.get("FTYPE_CODE") + " " + x.get("SCORE") + " " + x.get("SCORE_BUCKET")
                        + "  [" + x.get("INBOUND_FEAT_DESC") + "] vs [" + x.get("CANDIDATE_FEAT_DESC") + "]");
                }
            } else {
                System.out.println("      (MATCH_KEY_DETAILS not returned for this step; feature scores follow)");
            }
            for (Map.Entry<String, Object> ft : Json.obj(mi.get("FEATURE_SCORES")).entrySet()) {
                for (Object c : Json.arr(ft.getValue())) {
                    Map<String, Object> x = Json.obj(c);
                    System.out.println("      score   " + ft.getKey() + " " + x.get("SCORE") + " " + x.get("SCORE_BUCKET")
                        + "  [" + x.get("INBOUND_FEAT_DESC") + "] vs [" + x.get("CANDIDATE_FEAT_DESC") + "]");
                }
            }
        }
    }

    /**
     * findNetwork(SzEntityIds, maxDegrees, buildOutDegrees, buildOutMaxEntities, Set<SzFlag>) (java).
     * SZ_FIND_NETWORK_DEFAULT_FLAGS = SZ_FIND_NETWORK_INCLUDE_MATCHING_INFO + entity name + record summary.
     * Links are ENTITY_NETWORK_LINKS[] with UNDIRECTED MIN_ENTITY_ID / MAX_ENTITY_ID; paths between seeds are
     * ENTITY_PATHS[] with directed START_ENTITY_ID / END_ENTITY_ID; nodes are ENTITIES[].RESOLVED_ENTITY.
     */
    static void network(InsuranceModel m, List<Long> seeds, int maxDegrees) throws Exception {
        String out = m.engine().findNetwork(SzEntityIds.of(seeds), maxDegrees, 1, 50, SZ_FIND_NETWORK_DEFAULT_FLAGS);
        Map<String, Object> raw = Json.parseObject(out);
        Map<Long, String> names = names(raw);
        List<Object> links = Json.arr(raw.get("ENTITY_NETWORK_LINKS"));
        if (!links.isEmpty()) System.out.println("FIRST_LINK_KEYS=" + Json.obj(links.get(0)).keySet());
        System.out.println("NODES=" + names.size() + " LINKS=" + links.size());
        for (Object o : Json.arr(raw.get("ENTITIES"))) {
            Map<String, Object> re = Json.obj(Json.obj(o).get("RESOLVED_ENTITY"));
            System.out.println("  NODE " + re.get("ENTITY_ID") + " " + re.get("ENTITY_NAME") + " "
                + InsuranceModel.sources(Map.of("RECORD_SUMMARY", Json.arr(re.get("RECORD_SUMMARY")))));
        }
        for (Object o : links) {
            Map<String, Object> l = Json.obj(o);
            long a = ((Number) l.get("MIN_ENTITY_ID")).longValue(), b = ((Number) l.get("MAX_ENTITY_ID")).longValue();
            System.out.println("  LINK " + a + " " + names.get(a) + "  <->  " + b + " " + names.get(b)
                + "   " + l.get("MATCH_LEVEL_CODE") + " " + l.get("MATCH_KEY"));
        }
        for (Object o : Json.arr(raw.get("ENTITY_PATHS"))) {
            Map<String, Object> p = Json.obj(o);
            System.out.println("  SEED_PATH " + p.get("START_ENTITY_ID") + " -> " + p.get("END_ENTITY_ID") + " via " + p.get("ENTITIES"));
        }
    }

    /**
     * findPath(long, long, maxDegrees, Set<SzFlag>) (java). SZ_FIND_PATH_DEFAULT_FLAGS = SZ_FIND_PATH_INCLUDE_
     * MATCHING_INFO + entity name + record summary. Edges are ENTITY_PATH_LINKS[] (MIN/MAX_ENTITY_ID), the
     * ordered hops ENTITY_PATHS[0].ENTITIES[].
     */
    static List<Long> path(InsuranceModel m, long a, long b, int maxDegrees) throws Exception {
        Map<String, Object> raw = Json.parseObject(m.engine().findPath(a, b, maxDegrees, SZ_FIND_PATH_DEFAULT_FLAGS));
        Map<Long, String> names = names(raw);
        List<Long> hops = new ArrayList<>();
        List<Object> paths = Json.arr(raw.get("ENTITY_PATHS"));
        if (!paths.isEmpty()) for (Object o : Json.arr(Json.obj(paths.get(0)).get("ENTITIES"))) hops.add(((Number) o).longValue());
        System.out.println("PATH " + a + " -> " + b + " degrees=" + Math.max(0, hops.size() - 1) + " hops=" + hops);
        Map<String, Map<String, Object>> byPair = new HashMap<>();
        for (Object o : Json.arr(raw.get("ENTITY_PATH_LINKS"))) {
            Map<String, Object> l = Json.obj(o);
            byPair.put(l.get("MIN_ENTITY_ID") + "," + l.get("MAX_ENTITY_ID"), l);
        }
        for (int i = 0; i + 1 < hops.size(); i++) {
            long x = hops.get(i), y = hops.get(i + 1);
            Map<String, Object> l = byPair.get(Math.min(x, y) + "," + Math.max(x, y));
            System.out.println("  HOP " + x + " " + names.get(x) + "  ->  " + y + " " + names.get(y) + "   "
                + (l == null ? "(link detail not returned)" : l.get("MATCH_LEVEL_CODE") + " " + l.get("MATCH_KEY")));
        }
        return hops;
    }

    /** The hub method: from the most-related entities, find two neighbors not related to each other. */
    static void hubPath(InsuranceModel m) throws Exception {
        List<String> all = new ArrayList<>(InsuranceModel.CUSTOMER_SOURCES);
        all.addAll(InsuranceModel.OUTSIDE_SOURCES);
        Map<Long, Map<String, Object>> ents = new LinkedHashMap<>();
        for (String s : all) for (String id : m.recordIds(s)) {
            Map<String, Object> e = m.entityByRecord(s, id);
            ents.putIfAbsent(InsuranceModel.entityId(e), e);
        }
        List<Map<String, Object>> hubs = new ArrayList<>(ents.values());
        hubs.sort(Comparator.comparingInt(e -> -InsuranceModel.related(e).size()));
        int tried = 0;
        for (Map<String, Object> hub : hubs) {
            List<Long> nb = new ArrayList<>();
            for (Map<String, Object> r : InsuranceModel.related(hub)) nb.add(((Number) r.get("ENTITY_ID")).longValue());
            if (nb.size() < 2) continue;
            if (++tried > 3) break;
            System.out.println("HUB " + InsuranceModel.entityId(hub) + " " + InsuranceModel.entityName(hub) + " neighbors=" + nb.size());
            for (int i = 0; i < nb.size(); i++) for (int j = i + 1; j < nb.size(); j++) {
                Set<Long> rel = new HashSet<>();
                for (Map<String, Object> r : InsuranceModel.related(m.entity(nb.get(i)))) rel.add(((Number) r.get("ENTITY_ID")).longValue());
                if (rel.contains(nb.get(j))) continue;
                List<Long> hops = path(m, nb.get(i), nb.get(j), 3);
                if (hops.size() >= 3) return;
            }
        }
        System.out.println("NO 2+ DEGREE PAIR FOUND AFTER 3 HUBS");
    }

    private static Map<Long, String> names(Map<String, Object> raw) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Object o : Json.arr(raw.get("ENTITIES"))) {
            Map<String, Object> re = Json.obj(Json.obj(o).get("RESOLVED_ENTITY"));
            names.put(((Number) re.get("ENTITY_ID")).longValue(), Json.str(re.get("ENTITY_NAME")));
        }
        return names;
    }

    private static String side(Object ve) {
        Map<String, Object> v = Json.obj(ve);
        List<String> recs = new ArrayList<>();
        for (Object mr : Json.arr(v.get("MEMBER_RECORDS")))
            for (Object r : Json.arr(Json.obj(mr).get("RECORDS")))
                recs.add(Json.obj(r).get("DATA_SOURCE") + ":" + Json.obj(r).get("RECORD_ID"));
        return v.get("VIRTUAL_ENTITY_ID") + recs.toString();
    }

    static void whyRecords(InsuranceModel m, String ds1, String id1, String ds2, String id2) throws Exception {
        Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
        flags.addAll(SZ_WHY_RECORDS_DEFAULT_FLAGS);
        flags.add(SZ_ENTITY_INCLUDE_ENTITY_NAME);
        flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
        flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        Map<String, Object> raw = Json.parseObject(
            m.engine().whyRecords(SzRecordKey.of(ds1, id1), SzRecordKey.of(ds2, id2), flags));
        List<Object> results = Json.arr(raw.get("WHY_RESULTS"));
        if (results.isEmpty()) { System.out.println("NO WHY_RESULTS returned"); return; }
        Map<String, Object> wr = Json.obj(results.get(0));
        Map<String, Object> mi = Json.obj(wr.get("MATCH_INFO"));
        System.out.println("MATCH_INFO_KEYS=" + mi.keySet());
        System.out.println("ENTITY_IDS=" + wr.get("ENTITY_ID") + " / " + wr.get("ENTITY_ID_2"));
        System.out.println("WHY_KEY=" + mi.get("WHY_KEY"));
        System.out.println("WHY_ERRULE_CODE=" + mi.get("WHY_ERRULE_CODE"));
        System.out.println("MATCH_LEVEL_CODE=" + mi.get("MATCH_LEVEL_CODE"));
        if (mi.containsKey("WHY_KEY_DETAILS")) {
            Map<String, Object> d = Json.obj(mi.get("WHY_KEY_DETAILS"));
            List<Object> conf = Json.arr(d.get("CONFIRMATIONS"));
            System.out.println("WHY_KEY_DETAILS.CONFIRMATIONS (" + conf.size() + "):");
            for (Object o : conf) {
                Map<String, Object> c = Json.obj(o);
                System.out.println("  " + c.get("TOKEN") + "  " + c.get("FTYPE_CODE") + "  score=" + c.get("SCORE")
                    + " " + c.get("SCORE_BUCKET") + "  [" + c.get("INBOUND_FEAT_DESC") + "] vs [" + c.get("CANDIDATE_FEAT_DESC") + "]");
            }
            for (Object o : Json.arr(d.get("DENIALS"))) {
                Map<String, Object> c = Json.obj(o);
                System.out.println("  DENIAL " + c.get("TOKEN") + "  " + c.get("FTYPE_CODE") + "  score=" + c.get("SCORE")
                    + " " + c.get("SCORE_BUCKET") + "  [" + c.get("INBOUND_FEAT_DESC") + "] vs [" + c.get("CANDIDATE_FEAT_DESC") + "]");
            }
        } else {
            System.out.println("WHY_KEY_DETAILS not returned by this SDK for these flags; using FEATURE_SCORES");
        }
        System.out.println("FEATURE_SCORES:");
        for (Map.Entry<String, Object> ft : Json.obj(mi.get("FEATURE_SCORES")).entrySet()) {
            for (Object o : Json.arr(ft.getValue())) {
                Map<String, Object> s = Json.obj(o);
                System.out.println("  " + ft.getKey() + "  score=" + s.get("SCORE") + " " + s.get("SCORE_BUCKET")
                    + "  [" + s.get("INBOUND_FEAT_DESC") + "] vs [" + s.get("CANDIDATE_FEAT_DESC") + "]");
            }
        }
    }
}
