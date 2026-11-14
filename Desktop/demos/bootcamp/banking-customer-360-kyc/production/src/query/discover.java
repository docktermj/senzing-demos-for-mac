// Discover demonstrations on the bank's own data (Query, Visualize and Discover module, step 4).
//
//   patterns                         step 4a: multi-record (3+), cross-source and related entities
//   why-records DS1 RID1 DS2 RID2    step 4b: why two records resolved together (or did not)
//   why-entities ENTITY1 ENTITY2     step 4b: why two entities are, or are not, the same
//
// Senzing calls, from the Senzing MCP server (2026-11-14):
//   * get_sdk_reference(topic='parameters', language='java'):
//       whyRecords(recordKey1: SzRecordKey, recordKey2: SzRecordKey, flags: Set<SzFlag>) -> String
//       whyEntities(entityId1: long, entityId2: long, flags: Set<SzFlag>) -> String
//   * WHY_FLAGS: SZ_INCLUDE_FEATURE_SCORES (what SZ_WHY_RECORDS_DEFAULT_FLAGS is equivalent to: the
//     per-feature scores and buckets), SZ_ENTITY_INCLUDE_ENTITY_NAME (the why default carries no
//     names), and SZ_INCLUDE_MATCH_KEY_DETAILS, which response_schemas lists as required for
//     WHY_RESULTS[].MATCH_INFO.WHY_KEY_DETAILS and which depends on a relations flag, so
//     SZ_ENTITY_INCLUDE_ALL_RELATIONS (a Set<SzFlag> composite in Java) is merged in with addAll.
//   * response paths: get_sdk_reference(topic='response_schemas', filter='why_records'):
//       WHY_RESULTS[].MATCH_INFO.{WHY_KEY, WHY_ERRULE_CODE, MATCH_LEVEL_CODE, WHY_KEY_DETAILS, FEATURE_SCORES}
//     (on a why response the entity-side MATCH_KEY / ERRULE_CODE names do not exist).
// Raw responses are saved under data/results/discover/ so the plain-language summary can be checked.
// Run: src/scripts/run-java.sh data/temp/query_classes Discover <mode> ...
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static com.senzing.sdk.SzFlag.*;

class Discover {
    static final Set<SzFlag> WHY_FLAGS;
    static {
        Set<SzFlag> f = EnumSet.of(SZ_INCLUDE_FEATURE_SCORES, SZ_ENTITY_INCLUDE_ENTITY_NAME, SZ_INCLUDE_MATCH_KEY_DETAILS);
        f.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        WHY_FLAGS = Collections.unmodifiableSet(f);
    }
    static final Path OUT = Paths.get("data", "results", "discover");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { System.err.println("usage: Discover patterns | why-records DS1 RID1 DS2 RID2 | why-entities E1 E2"); System.exit(64); }
        Files.createDirectories(OUT);
        switch (args[0]) {
            case "patterns": patterns(); break;
            case "why-records": why(args, true); break;
            case "why-entities": why(args, false); break;
            case "how": how(Long.parseLong(args[1])); break;
            case "network": network(args); break;
            case "hub-path": hubPath(); break;
            case "path": path(Long.parseLong(args[1]), Long.parseLong(args[2])); break;
            default: System.err.println("unknown mode " + args[0]); System.exit(64);
        }
    }

    // ---- step 4a ------------------------------------------------------------------------------------
    static void patterns() throws Exception {
        try (ResolvedEntities re = ResolvedEntities.load("DiscoverPatterns")) {
            List<long[]> large = new ArrayList<>();     // {entity, records, sources}
            List<long[]> related = new ArrayList<>();   // {entity, relationships, disclosed}
            for (long eid : re.entities.keySet()) {
                int n = re.records(eid).size();
                if (n >= 3) large.add(new long[]{eid, n, re.sources(eid).size()});
                List<Map<String, Object>> rel = re.related(eid);
                if (!rel.isEmpty()) {
                    long disclosed = rel.stream().filter(r -> r.get("IS_DISCLOSED") instanceof Number && ((Number) r.get("IS_DISCLOSED")).intValue() == 1).count();
                    related.add(new long[]{eid, rel.size(), disclosed});
                }
            }
            large.sort((a, b) -> Long.compare(b[1], a[1]));
            related.sort((a, b) -> Long.compare(b[1], a[1]));
            long cross = large.stream().filter(x -> x[2] >= 2).count();
            System.out.println("Entities with 3+ records : " + large.size());
            System.out.println("  of which cross-source  : " + cross);
            System.out.println("Entities with relationships (disclosed or discovered): " + related.size());
            System.out.println("Largest entities:");
            for (long[] x : large) System.out.println("  " + x[0] + " " + re.name(x[0]) + ": " + x[1] + " records from " + re.sources(x[0]));
            System.out.println("Most-connected entities:");
            for (int i = 0; i < Math.min(8, related.size()); i++) {
                long[] x = related.get(i);
                System.out.println("  " + x[0] + " " + re.name(x[0]) + ": " + x[1] + " relationships (" + x[2] + " disclosed, " + (x[1] - x[2]) + " discovered)");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("multi_record", large.size());
            out.put("cross_source", cross);
            out.put("relationships", related.size());
            List<Map<String, Object>> top = new ArrayList<>();
            for (int i = 0; i < Math.min(8, related.size()); i++) top.add(Map.of("entity_id", related.get(i)[0], "relationships", related.get(i)[1], "disclosed", related.get(i)[2]));
            out.put("most_connected", top);
            Files.writeString(OUT.resolve("patterns.json"), MiniJson.write(out) + "\n", StandardCharsets.UTF_8);
        }
    }

    // ---- step 4b ------------------------------------------------------------------------------------
    static void why(String[] a, boolean records) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) throw new IllegalArgumentException("Unable to get settings");
        SzEnvironment env = SzCoreEnvironment.newBuilder().settings(settings).instanceName("DiscoverWhy").verboseLogging(false).build();
        String label;
        String raw;
        try {
            SzEngine engine = env.getEngine();
            if (records) {
                label = a[1] + "_" + a[2] + "_vs_" + a[3] + "_" + a[4];
                raw = engine.whyRecords(SzRecordKey.of(a[1], a[2]), SzRecordKey.of(a[3], a[4]), WHY_FLAGS);
            } else {
                label = "entities_" + a[1] + "_vs_" + a[2];
                raw = engine.whyEntities(Long.parseLong(a[1]), Long.parseLong(a[2]), WHY_FLAGS);
            }
        } finally {
            env.destroy();
        }
        Files.writeString(OUT.resolve("why_" + label + ".json"), raw + "\n", StandardCharsets.UTF_8);
        Map<String, Object> doc = MiniJson.obj(MiniJson.parse(raw));
        Map<Long, String> names = new HashMap<>();
        for (Map<String, Object> e : ResolvedEntities.objs(doc.get("ENTITIES"))) {
            Map<String, Object> r = MiniJson.obj(e.get("RESOLVED_ENTITY"));
            names.put(((Number) r.get("ENTITY_ID")).longValue(), MiniJson.str(r.get("ENTITY_NAME")));
        }
        for (Map<String, Object> w : ResolvedEntities.objs(doc.get("WHY_RESULTS"))) {
            long e1 = ((Number) w.get("ENTITY_ID")).longValue(), e2 = ((Number) w.get("ENTITY_ID_2")).longValue();
            Map<String, Object> mi = MiniJson.obj(w.get("MATCH_INFO"));
            System.out.println("Comparing " + focus(w.get("FOCUS_RECORDS")) + " (entity " + e1 + " " + names.get(e1) + ")");
            System.out.println("     with " + focus(w.get("FOCUS_RECORDS_2")) + " (entity " + e2 + " " + names.get(e2) + ")");
            System.out.println("  same entity now : " + (e1 == e2 ? "yes" : "no"));
            System.out.println("  MATCH_INFO keys : " + new TreeSet<>(mi.keySet()));
            System.out.println("  WHY_KEY         : " + mi.get("WHY_KEY"));
            System.out.println("  WHY_ERRULE_CODE : " + blank(mi.get("WHY_ERRULE_CODE")));
            System.out.println("  MATCH_LEVEL_CODE: " + blank(mi.get("MATCH_LEVEL_CODE")));
            Map<String, Object> details = MiniJson.obj(mi.get("WHY_KEY_DETAILS"));
            if (details == null) {
                System.out.println("  WHY_KEY_DETAILS : not returned for these flags; see FEATURE_SCORES");
            } else {
                List<Map<String, Object>> conf = ResolvedEntities.objs(details.get("CONFIRMATIONS"));
                System.out.println("  WHY_KEY_DETAILS : " + (conf.isEmpty() ? "present, no confirmations on this pair; see FEATURE_SCORES" : conf.size() + " confirmation(s)"));
                for (Map<String, Object> c : conf)
                    System.out.println("     + " + c.get("FTYPE_CODE") + " score " + c.get("SCORE") + " (" + c.get("SCORE_BUCKET") + "): '" + c.get("INBOUND_FEAT_DESC") + "' vs '" + c.get("CANDIDATE_FEAT_DESC") + "'");
            }
            Map<String, Object> scores = MiniJson.obj(mi.get("FEATURE_SCORES"));
            System.out.println("  FEATURE_SCORES  :");
            if (scores != null) for (Map.Entry<String, Object> f : new TreeMap<>(scores).entrySet())
                for (Map<String, Object> s : ResolvedEntities.objs(f.getValue()))
                    System.out.println("     " + f.getKey() + ": score " + s.get("SCORE") + " (" + s.get("SCORE_BUCKET") + ", " + blank(s.get("SCORE_BEHAVIOR")) + "): '"
                            + s.get("INBOUND_FEAT_DESC") + "' vs '" + s.get("CANDIDATE_FEAT_DESC") + "'");
        }
        System.out.println("raw response: " + OUT.resolve("why_" + label + ".json"));
    }

    // ---- step 4c ------------------------------------------------------------------------------------
    // howEntity(entityId: long, flags: Set<SzFlag>) -> String (get_sdk_reference, 2026-11-14). The method's
    // default, SZ_HOW_ENTITY_DEFAULT_FLAGS, is SZ_INCLUDE_FEATURE_SCORES alone; WHY_FLAGS adds
    // SZ_INCLUDE_MATCH_KEY_DETAILS (+ relations) so RESOLUTION_STEPS[].MATCH_INFO.MATCH_KEY_DETAILS may appear.
    // On the how side the names are MATCH_KEY / ERRULE_CODE / MATCH_KEY_DETAILS, and each step's two sides
    // are the objects VIRTUAL_ENTITY_1 / VIRTUAL_ENTITY_2 (response_schemas filter='how_entity').
    static void how(long eid) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) throw new IllegalArgumentException("Unable to get settings");
        SzEnvironment env = SzCoreEnvironment.newBuilder().settings(settings).instanceName("DiscoverHow").verboseLogging(false).build();
        String raw;
        try {
            raw = env.getEngine().howEntity(eid, WHY_FLAGS);
        } finally {
            env.destroy();
        }
        Files.writeString(OUT.resolve("how_entity_" + eid + ".json"), raw + "\n", StandardCharsets.UTF_8);
        Map<String, Object> hr = MiniJson.obj(MiniJson.obj(MiniJson.parse(raw)).get("HOW_RESULTS"));
        List<Map<String, Object>> steps = ResolvedEntities.objs(hr.get("RESOLUTION_STEPS"));
        if (!steps.isEmpty()) System.out.println("MATCH_INFO keys (step 1): " + new TreeSet<>(MiniJson.obj(steps.get(0).get("MATCH_INFO")).keySet()));
        for (Map<String, Object> s : steps) {
            Map<String, Object> mi = MiniJson.obj(s.get("MATCH_INFO"));
            System.out.println("Step " + s.get("STEP") + ": " + side(s.get("VIRTUAL_ENTITY_1")) + "  +  " + side(s.get("VIRTUAL_ENTITY_2"))
                    + "  ->  " + s.get("RESULT_VIRTUAL_ENTITY_ID"));
            System.out.println("   match key " + mi.get("MATCH_KEY") + ", rule " + blank(mi.get("ERRULE_CODE")));
            Map<String, Object> d = MiniJson.obj(mi.get("MATCH_KEY_DETAILS"));
            List<Map<String, Object>> conf = d == null ? List.of() : ResolvedEntities.objs(d.get("CONFIRMATIONS"));
            if (d == null) System.out.println("   (MATCH_KEY_DETAILS not returned; feature scores follow)");
            for (Map<String, Object> c : conf)
                System.out.println("   + " + c.get("FTYPE_CODE") + " " + c.get("SCORE") + " (" + c.get("SCORE_BUCKET") + "): '" + c.get("INBOUND_FEAT_DESC") + "' vs '" + c.get("CANDIDATE_FEAT_DESC") + "'");
            if (conf.isEmpty()) {
                Map<String, Object> scores = MiniJson.obj(mi.get("FEATURE_SCORES"));
                if (scores != null) for (Map.Entry<String, Object> f : new TreeMap<>(scores).entrySet())
                    for (Map<String, Object> x : ResolvedEntities.objs(f.getValue()))
                        System.out.println("   " + f.getKey() + " " + x.get("SCORE") + " (" + x.get("SCORE_BUCKET") + ")");
            }
        }
        Map<String, Object> fs = MiniJson.obj(hr.get("FINAL_STATE"));
        if (fs == null) System.out.println("FINAL_STATE not returned");
        else System.out.println("FINAL_STATE: NEED_REEVALUATION=" + fs.get("NEED_REEVALUATION") + ", virtual entities=" + ResolvedEntities.objs(fs.get("VIRTUAL_ENTITIES")).size());
        System.out.println("raw response: " + OUT.resolve("how_entity_" + eid + ".json"));
    }

    // ---- step 4d ------------------------------------------------------------------------------------
    // From the Senzing MCP server (2026-11-14):
    //   findNetwork(entityIds: SzEntityIds, maxDegrees: int, buildOutDegrees: int, buildOutMaxEntities: int,
    //               flags: Set<SzFlag>) -> String, with SZ_FIND_NETWORK_DEFAULT_FLAGS (matching info on links,
    //               entity names, record summaries); response ENTITIES[], ENTITY_PATHS[] (START/END_ENTITY_ID,
    //               directed) and ENTITY_NETWORK_LINKS[] (MIN/MAX_ENTITY_ID, undirected, MATCH_KEY, MATCH_LEVEL_CODE,
    //               IS_DISCLOSED).
    //   findPath(startEntityId: long, endEntityId: long, maxDegrees: int, flags: Set<SzFlag>) -> String, with
    //               SZ_FIND_PATH_DEFAULT_FLAGS; its edges are ENTITY_PATH_LINKS[], NOT ENTITY_NETWORK_LINKS[].
    static void network(String[] a) throws Exception {
        int maxDegrees = 3, buildOut = 1, maxEntities = 50;
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i < a.length; i++) ids.add(Long.parseLong(a[i]));
        String raw;
        SzEnvironment env = env("DiscoverNetwork");
        try {
            raw = env.getEngine().findNetwork(SzEntityIds.of(ids), maxDegrees, buildOut, maxEntities, SZ_FIND_NETWORK_DEFAULT_FLAGS);
        } finally {
            env.destroy();
        }
        String label = "network_" + String.join("_", ids.stream().map(String::valueOf).toList());
        Files.writeString(OUT.resolve(label + ".json"), raw + "\n", StandardCharsets.UTF_8);
        Map<String, Object> doc = MiniJson.obj(MiniJson.parse(raw));
        Map<Long, String> names = entityNames(doc);
        List<Map<String, Object>> links = ResolvedEntities.objs(doc.get("ENTITY_NETWORK_LINKS"));
        if (!links.isEmpty()) System.out.println("link keys: " + new TreeSet<>(links.get(0).keySet()));
        System.out.println("Network around " + ids + ": " + names.size() + " entities, " + links.size() + " links (seed max " + maxDegrees + " degrees, build-out " + buildOut + ")");
        for (Map.Entry<Long, String> e : names.entrySet()) System.out.println("  entity " + e.getKey() + " " + e.getValue() + " " + summaries(doc, e.getKey()));
        for (Map<String, Object> l : links)
            System.out.println("  " + l.get("MIN_ENTITY_ID") + " " + names.get(num(l.get("MIN_ENTITY_ID"))) + "  <->  " + l.get("MAX_ENTITY_ID") + " " + names.get(num(l.get("MAX_ENTITY_ID")))
                    + "   " + l.get("MATCH_LEVEL_CODE") + " " + l.get("MATCH_KEY") + (Integer.valueOf(1).equals(intOf(l.get("IS_DISCLOSED"))) ? "  [disclosed]" : "  [discovered]"));
        for (Map<String, Object> p : ResolvedEntities.objs(doc.get("ENTITY_PATHS")))
            System.out.println("  path " + p.get("START_ENTITY_ID") + " -> " + p.get("END_ENTITY_ID") + ": " + p.get("ENTITIES"));
        System.out.println("raw response: " + OUT.resolve(label + ".json"));
    }

    /** Hub method: neighbors of the most-connected entities that are not related to each other, confirmed with findPath. */
    static void hubPath() throws Exception {
        List<long[]> hubs = new ArrayList<>();
        Map<Long, Set<Long>> nbrs = new HashMap<>();
        try (ResolvedEntities re = ResolvedEntities.load("DiscoverHubs")) {
            for (long eid : re.entities.keySet()) {
                Set<Long> n = new TreeSet<>();
                for (Map<String, Object> r : re.related(eid)) n.add(((Number) r.get("ENTITY_ID")).longValue());
                nbrs.put(eid, n);
                hubs.add(new long[]{eid, n.size()});
            }
        }
        hubs.sort((x, y) -> Long.compare(y[1], x[1]));
        SzEnvironment env = env("DiscoverPath");
        try {
            SzEngine engine = env.getEngine();
            for (int h = 0; h < Math.min(3, hubs.size()); h++) {
                long hub = hubs.get(h)[0];
                List<Long> n = new ArrayList<>(nbrs.get(hub));
                for (int i = 0; i < n.size(); i++) for (int j = i + 1; j < n.size(); j++) {
                    long a = n.get(i), b = n.get(j);
                    if (nbrs.getOrDefault(a, Set.of()).contains(b)) continue;
                    String raw = engine.findPath(a, b, 3, SZ_FIND_PATH_DEFAULT_FLAGS);
                    Map<String, Object> doc = MiniJson.obj(MiniJson.parse(raw));
                    List<Map<String, Object>> paths = ResolvedEntities.objs(doc.get("ENTITY_PATHS"));
                    if (paths.isEmpty()) continue;
                    List<Object> hops = MiniJson.arr(paths.get(0).get("ENTITIES"));
                    if (hops.size() < 3) continue;   // 2+ degrees means 3+ entities on the path
                    Files.writeString(OUT.resolve("path_" + a + "_" + b + ".json"), raw + "\n", StandardCharsets.UTF_8);
                    Map<Long, String> names = entityNames(doc);
                    System.out.println("Hub " + hub + " " + names.getOrDefault(hub, "") + " (" + hubs.get(h)[1] + " relationships); neighbors " + a + " and " + b + " are not directly related.");
                    System.out.println("Shortest path, " + (hops.size() - 1) + " degrees: " + hops);
                    for (Object e : hops) System.out.println("  " + e + " " + names.get(num(e)) + " " + summaries(doc, num(e)));
                    for (Map<String, Object> l : ResolvedEntities.objs(doc.get("ENTITY_PATH_LINKS")))
                        System.out.println("  link " + l.get("MIN_ENTITY_ID") + " <-> " + l.get("MAX_ENTITY_ID") + "  " + l.get("MATCH_LEVEL_CODE") + " " + l.get("MATCH_KEY"));
                    System.out.println("raw response: " + OUT.resolve("path_" + a + "_" + b + ".json"));
                    return;
                }
            }
            System.out.println("No pair of 2+ degrees found among the neighbors of the top three hubs.");
        } finally {
            env.destroy();
        }
    }

    static void path(long a, long b) throws Exception {
        String raw;
        SzEnvironment env = env("DiscoverPath");
        try {
            raw = env.getEngine().findPath(a, b, 3, SZ_FIND_PATH_DEFAULT_FLAGS);
        } finally {
            env.destroy();
        }
        Files.writeString(OUT.resolve("path_" + a + "_" + b + ".json"), raw + "\n", StandardCharsets.UTF_8);
        Map<String, Object> doc = MiniJson.obj(MiniJson.parse(raw));
        Map<Long, String> names = entityNames(doc);
        for (Map<String, Object> p : ResolvedEntities.objs(doc.get("ENTITY_PATHS"))) {
            List<Object> hops = MiniJson.arr(p.get("ENTITIES"));
            System.out.println("Path " + p.get("START_ENTITY_ID") + " -> " + p.get("END_ENTITY_ID") + ": " + (hops.isEmpty() ? "none within 3 degrees" : (hops.size() - 1) + " degrees"));
            for (Object e : hops) System.out.println("  " + e + " " + names.get(num(e)) + " " + summaries(doc, num(e)));
        }
        for (Map<String, Object> l : ResolvedEntities.objs(doc.get("ENTITY_PATH_LINKS")))
            System.out.println("  link " + l.get("MIN_ENTITY_ID") + " <-> " + l.get("MAX_ENTITY_ID") + "  " + l.get("MATCH_LEVEL_CODE") + " " + l.get("MATCH_KEY"));
        System.out.println("raw response: " + OUT.resolve("path_" + a + "_" + b + ".json"));
    }

    static SzEnvironment env(String name) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) throw new IllegalArgumentException("Unable to get settings");
        return SzCoreEnvironment.newBuilder().settings(settings).instanceName(name).verboseLogging(false).build();
    }

    static Map<Long, String> entityNames(Map<String, Object> doc) {
        Map<Long, String> names = new TreeMap<>();
        for (Map<String, Object> e : ResolvedEntities.objs(doc.get("ENTITIES"))) {
            Map<String, Object> r = MiniJson.obj(e.get("RESOLVED_ENTITY"));
            names.put(num(r.get("ENTITY_ID")), MiniJson.str(r.get("ENTITY_NAME")));
        }
        return names;
    }

    static String summaries(Map<String, Object> doc, long eid) {
        for (Map<String, Object> e : ResolvedEntities.objs(doc.get("ENTITIES"))) {
            Map<String, Object> r = MiniJson.obj(e.get("RESOLVED_ENTITY"));
            if (num(r.get("ENTITY_ID")) != eid) continue;
            List<String> s = new ArrayList<>();
            for (Map<String, Object> x : ResolvedEntities.objs(r.get("RECORD_SUMMARY"))) s.add(x.get("DATA_SOURCE") + ":" + x.get("RECORD_COUNT"));
            return s.toString();
        }
        return "[]";
    }

    static long num(Object o) { return o instanceof Number ? ((Number) o).longValue() : -1; }

    static Integer intOf(Object o) { return o instanceof Number ? ((Number) o).intValue() : null; }

    static String side(Object ve) {
        Map<String, Object> v = MiniJson.obj(ve);
        List<String> recs = new ArrayList<>();
        for (Map<String, Object> m : ResolvedEntities.objs(v.get("MEMBER_RECORDS")))
            for (Map<String, Object> r : ResolvedEntities.objs(m.get("RECORDS"))) recs.add(r.get("DATA_SOURCE") + "/" + r.get("RECORD_ID"));
        return v.get("VIRTUAL_ENTITY_ID") + " [" + String.join(", ", recs) + "]";
    }

    static String focus(Object o) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : ResolvedEntities.objs(o)) out.add(r.get("DATA_SOURCE") + "/" + r.get("RECORD_ID"));
        return String.join(", ", out);
    }

    static String blank(Object o) { return o == null || String.valueOf(o).isEmpty() ? "(empty)" : String.valueOf(o); }
}
