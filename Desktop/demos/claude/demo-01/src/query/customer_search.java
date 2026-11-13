// customer_search.java -- Query, Visualize and Discover: customer search and 360 view (the logic an
// API would wrap).
//
// 1. searchByAttributes(attributes, SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS) finds candidate customers
//    from a name, email and/or phone (signatures and the RESOLVED_ENTITIES[].ENTITY.RESOLVED_ENTITY /
//    MATCH_INFO response shape from get_sdk_reference, language='java'). A name is tried as NAME_FULL,
//    then as NAME_ORG if nothing matched or the attempt errored; an empty result reports which
//    attributes were tried rather than "not in your data".
// 2. For the top results, getEntity(entityId, flags) returns the 360 view. Flags: SZ_ENTITY_DEFAULT_FLAGS
//    (records, relationships, names) plus SZ_ENTITY_INCLUDE_RECORD_JSON_DATA so each source record's
//    own data is shown. DEFAULT composites are for exploration; in production list exactly the flags
//    whose output you consume.
//
// Usage: src/scripts/run-java.sh CustomerSearch [--name "First Last"] [--email x@y] [--phone 555-...] [--top N] [--raw]
import java.util.*;
import com.senzing.sdk.*;

import static com.senzing.sdk.SzFlag.*;

class CustomerSearch {
    static String search(SzEngine engine, Map<String, Object> attrs) throws SzException {
        return engine.searchByAttributes(Json.write(attrs), SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
    }

    public static void main(String[] args) throws Exception {
        String name = null, email = null, phone = null;
        int top = 3;
        boolean raw = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--name" -> name = args[++i];
                case "--email" -> email = args[++i];
                case "--phone" -> phone = args[++i];
                case "--top" -> top = Integer.parseInt(args[++i]);
                case "--raw" -> raw = true;
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (name == null && email == null && phone == null) {
            System.err.println("usage: CustomerSearch [--name ...] [--email ...] [--phone ...] [--top N] [--raw]");
            System.exit(2);
        }
        Set<SzFlag> entityFlags = EnumSet.noneOf(SzFlag.class);
        entityFlags.addAll(SZ_ENTITY_DEFAULT_FLAGS);
        entityFlags.add(SZ_ENTITY_INCLUDE_RECORD_JSON_DATA);

        try (SenzingSession s = new SenzingSession(CustomerSearch.class.getSimpleName())) {
            List<String> nameAttrs = name == null ? List.of("") : List.of("NAME_FULL", "NAME_ORG");
            List<Object> results = List.of();
            List<String> tried = new ArrayList<>();
            for (String nameAttr : nameAttrs) {
                Map<String, Object> attrs = new LinkedHashMap<>();
                if (name != null) { attrs.put(nameAttr, name); }
                if (email != null) { attrs.put("EMAIL_ADDRESS", email); }
                if (phone != null) { attrs.put("PHONE_NUMBER", phone); }
                tried.add(attrs.keySet().toString());
                try {
                    String resp = search(s.engine, attrs);
                    if (raw) { System.out.println("RAW SEARCH: " + resp); }
                    results = Json.arr(Json.obj(Json.parse(resp)).get("RESOLVED_ENTITIES"));
                } catch (SzException e) {
                    System.err.println("search with " + attrs.keySet() + " failed: " + e.getMessage());
                }
                if (!results.isEmpty()) { break; }
            }
            if (results.isEmpty()) {
                System.out.println("Nothing matched the attributes tried: " + tried);
                return;
            }
            System.out.println(results.size() + " candidate customer(s); showing the top " + Math.min(top, results.size()) + ":");
            for (int i = 0; i < Math.min(top, results.size()); i++) {
                Map<String, Object> r = Json.obj(results.get(i));
                Map<String, Object> mi = Json.obj(r.get("MATCH_INFO"));
                long id = Json.lng(Json.obj(Json.obj(r.get("ENTITY")).get("RESOLVED_ENTITY")).get("ENTITY_ID"));
                String view = s.engine.getEntity(id, entityFlags);
                if (raw && i == 0) { System.out.println("RAW ENTITY: " + view); }
                Map<String, Object> doc = Json.obj(Json.parse(view));
                Map<String, Object> re = Json.obj(doc.get("RESOLVED_ENTITY"));
                System.out.println();
                System.out.println("== Customer " + id + ": " + Json.str(re.get("ENTITY_NAME"), "(no name returned)")
                    + "   [search match: " + Json.str(mi.get("MATCH_LEVEL_CODE"), "?") + " " + Json.str(mi.get("MATCH_KEY"), "") + "]");
                for (Object o : Json.arr(re.get("RECORD_SUMMARY"))) {
                    Map<String, Object> rs = Json.obj(o);
                    System.out.println("   in " + Json.str(rs.get("DATA_SOURCE"), "?") + ": " + Json.lng(rs.get("RECORD_COUNT")) + " record(s)");
                }
                for (Object o : Json.arr(re.get("RECORDS"))) {
                    Map<String, Object> rec = Json.obj(o);
                    Object jd = rec.get("JSON_DATA");
                    String payload = jd == null ? "(no record data returned)" : Json.write(jd instanceof String js ? Json.parse(js) : jd);
                    System.out.println("   - " + Json.str(rec.get("DATA_SOURCE"), "?") + " " + Json.str(rec.get("RECORD_ID"), "?")
                        + "  match key: " + (Json.str(rec.get("MATCH_KEY"), "").isEmpty() ? "(first record)" : Json.str(rec.get("MATCH_KEY"), ""))
                        + "\n       " + payload);
                }
                List<Object> related = Json.arr(doc.get("RELATED_ENTITIES"));
                if (!related.isEmpty()) {
                    System.out.println("   related customers (not merged): " + related.size());
                    for (Object o : related.subList(0, Math.min(5, related.size()))) {
                        Map<String, Object> rel = Json.obj(o);
                        System.out.println("     ~ " + Json.lng(rel.get("ENTITY_ID")) + " " + Json.str(rel.get("ENTITY_NAME"), "")
                            + "  " + Json.str(rel.get("MATCH_LEVEL_CODE"), "") + " " + Json.str(rel.get("MATCH_KEY"), ""));
                    }
                }
            }
        }
    }
}
