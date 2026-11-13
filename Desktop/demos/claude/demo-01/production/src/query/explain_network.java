// explain_network.java -- Query, Visualize and Discover, Step 4d: relationship networks and paths.
//
// SDK calls (get_sdk_reference topic='parameters', language='java'; reporting_guide topic='graph'):
//   findNetwork(SzEntityIds.of(...), maxDegrees, buildOutDegrees, buildOutMaxEntities, SZ_FIND_NETWORK_DEFAULT_FLAGS)
//   findPath(startEntityId, endEntityId, maxDegrees, SZ_FIND_PATH_DEFAULT_FLAGS)
// Response arrays differ by method: findNetwork's edges are ENTITY_NETWORK_LINKS[], findPath's are
// ENTITY_PATH_LINKS[]. Link endpoints are MIN_ENTITY_ID / MAX_ENTITY_ID (undirected), while
// ENTITY_PATHS[] uses START_ENTITY_ID / END_ENTITY_ID with ENTITIES[] in hop order. Entity details are
// ENTITIES[].RESOLVED_ENTITY.{ENTITY_ID, ENTITY_NAME, RECORD_SUMMARY[]}. Run with --raw to dump one link.
//
// Usage: src/scripts/run-java.sh ExplainNetwork network <ID> <ID> [<ID> ...] [--raw]
//        src/scripts/run-java.sh ExplainNetwork path <START_ID> <END_ID> [--raw]
import java.util.*;
import com.senzing.sdk.*;

import static com.senzing.sdk.SzFlag.*;

class ExplainNetwork {
    static Map<Long, String> describe(Map<String, Object> doc) {
        Map<Long, String> out = new LinkedHashMap<>();
        for (Object o : Json.arr(doc.get("ENTITIES"))) {
            Map<String, Object> re = Json.obj(Json.obj(o).get("RESOLVED_ENTITY"));
            List<String> srcs = new ArrayList<>();
            for (Object s : Json.arr(re.get("RECORD_SUMMARY"))) {
                Map<String, Object> rs = Json.obj(s);
                srcs.add(Json.str(rs.get("DATA_SOURCE"), "?") + " x" + Json.lng(rs.get("RECORD_COUNT")));
            }
            out.put(Json.lng(re.get("ENTITY_ID")), Json.str(re.get("ENTITY_NAME"), "(no name returned)") + " [" + String.join(", ", srcs) + "]");
        }
        return out;
    }

    static void printLinks(List<Object> links, Map<Long, String> ents, boolean raw) {
        if (raw && !links.isEmpty()) { System.out.println("RAW LINK: " + Json.write(links.get(0))); }
        if (links.isEmpty()) { System.out.println("   (no links returned)"); }
        for (Object o : links) {
            Map<String, Object> l = Json.obj(o);
            Long a = Json.lng(l.get("MIN_ENTITY_ID")), b = Json.lng(l.get("MAX_ENTITY_ID"));
            System.out.println("   " + a + " " + ents.getOrDefault(a, "?") + "  <->  " + b + " " + ents.getOrDefault(b, "?")
                + "   " + Json.str(l.get("MATCH_LEVEL_CODE"), "?") + " " + Json.str(l.get("MATCH_KEY"), "(link detail not returned)"));
        }
    }

    public static void main(String[] args) throws Exception {
        boolean raw = Arrays.asList(args).contains("--raw");
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i < args.length; i++) { if (!args[i].startsWith("--")) { ids.add(Long.parseLong(args[i])); } }
        try (SenzingSession s = new SenzingSession(ExplainNetwork.class.getSimpleName())) {
            if (args[0].equals("network")) {
                long[] arr = ids.stream().mapToLong(Long::longValue).toArray();
                String resp = s.engine.findNetwork(SzEntityIds.of(arr), 2, 1, 25, SZ_FIND_NETWORK_DEFAULT_FLAGS);
                Map<String, Object> doc = Json.obj(Json.parse(resp));
                if (raw) { System.out.println("TOP-LEVEL KEYS: " + new TreeSet<>(doc.keySet())); }
                Map<Long, String> ents = describe(doc);
                System.out.println("Network around " + ids + ": " + ents.size() + " entities");
                ents.forEach((k, v) -> System.out.println("   " + k + " " + v));
                System.out.println("Links (ENTITY_NETWORK_LINKS):");
                printLinks(Json.arr(doc.get("ENTITY_NETWORK_LINKS")), ents, raw);
                System.out.println("Paths between the seed entities (ENTITY_PATHS):");
                for (Object o : Json.arr(doc.get("ENTITY_PATHS"))) {
                    Map<String, Object> p = Json.obj(o);
                    List<Object> hops = Json.arr(p.get("ENTITIES"));
                    System.out.println("   " + Json.lng(p.get("START_ENTITY_ID")) + " -> " + Json.lng(p.get("END_ENTITY_ID"))
                        + ": " + (hops.isEmpty() ? "no path within the degree limit" : (hops.size() - 1) + " degree(s) via " + hops));
                }
            } else {
                String resp = s.engine.findPath(ids.get(0), ids.get(1), 3, SZ_FIND_PATH_DEFAULT_FLAGS);
                Map<String, Object> doc = Json.obj(Json.parse(resp));
                if (raw) { System.out.println("TOP-LEVEL KEYS: " + new TreeSet<>(doc.keySet())); }
                Map<Long, String> ents = describe(doc);
                List<Object> paths = Json.arr(doc.get("ENTITY_PATHS"));
                List<Object> hops = paths.isEmpty() ? List.of() : Json.arr(Json.obj(paths.get(0)).get("ENTITIES"));
                if (hops.isEmpty()) {
                    System.out.println("No path between " + ids.get(0) + " and " + ids.get(1) + " within 3 degrees.");
                    return;
                }
                System.out.println("Shortest path " + ids.get(0) + " -> " + ids.get(1) + ": " + (hops.size() - 1) + " degree(s)");
                for (Object h : hops) { long id = Json.lng(h); System.out.println("   " + id + " " + ents.getOrDefault(id, "?")); }
                System.out.println("Links on the path (ENTITY_PATH_LINKS):");
                printLinks(Json.arr(doc.get("ENTITY_PATH_LINKS")), ents, raw);
            }
        }
    }
}
