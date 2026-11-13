// discover_patterns.java -- Query, Visualize and Discover, Step 4a: find demonstration candidates.
//
// Iterates over the records actually loaded (the DATA_SOURCE + RECORD_ID pairs in the
// data/senzing-ready/ files) and looks each one up with getEntity(SzRecordKey, flags) -- never a
// guessed range of entity IDs. Each entity is examined once (folded by ENTITY_ID). Flags:
// SZ_ENTITY_DEFAULT_FLAGS, whose members include SZ_ENTITY_INCLUDE_ALL_RELATIONS (possibly-same,
// possibly-related, name-only and disclosed relations), so both discovered and disclosed
// relationships come back in RELATED_ENTITIES[] (a disclosed one carries IS_DISCLOSED = 1).
// Signatures from get_sdk_reference(language='java').
//
// Writes data/results/discover_candidates.json.  Usage: src/scripts/run-java.sh DiscoverPatterns
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.senzing.sdk.*;

import static com.senzing.sdk.SzFlag.*;

class DiscoverPatterns {
    public static void main(String[] args) throws Exception {
        List<String> files = List.of("data/senzing-ready/crm_contacts.jsonl", "data/senzing-ready/web_orders.jsonl",
                                     "data/senzing-ready/support_tickets.jsonl");
        Set<Long> seen = new HashSet<>();
        List<Map<String, Object>> large = new ArrayList<>(), cross = new ArrayList<>(), related = new ArrayList<>();
        long lookups = 0, disclosedTotal = 0, discoveredTotal = 0;
        try (SenzingSession s = new SenzingSession(DiscoverPatterns.class.getSimpleName())) {
            for (String f : files) {
                for (String line : Files.readAllLines(Path.of(f), StandardCharsets.UTF_8)) {
                    if (line.isBlank()) { continue; }
                    Map<String, Object> rec = Json.obj(Json.parse(line));
                    SzRecordKey key = SzRecordKey.of(Json.str(rec.get("DATA_SOURCE"), ""), Json.str(rec.get("RECORD_ID"), ""));
                    lookups++;
                    Map<String, Object> doc = Json.obj(Json.parse(s.engine.getEntity(key, SZ_ENTITY_DEFAULT_FLAGS)));
                    Map<String, Object> re = Json.obj(doc.get("RESOLVED_ENTITY"));
                    long id = Json.lng(re.get("ENTITY_ID"));
                    if (!seen.add(id)) { continue; }
                    List<Object> recs = Json.arr(re.get("RECORDS"));
                    Set<String> sources = new TreeSet<>();
                    for (Object o : recs) { sources.add(Json.str(Json.obj(o).get("DATA_SOURCE"), "")); }
                    long disclosed = 0, discovered = 0;
                    for (Object o : Json.arr(doc.get("RELATED_ENTITIES"))) {
                        Long d = Json.lng(Json.obj(o).get("IS_DISCLOSED"));
                        if (d != null && d == 1) { disclosed++; } else { discovered++; }
                    }
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("entity_id", id);
                    c.put("name", Json.str(re.get("ENTITY_NAME"), ""));
                    c.put("records", (long) recs.size());
                    c.put("sources", new ArrayList<>(sources));
                    c.put("relationships_disclosed", disclosed);
                    c.put("relationships_discovered", discovered);
                    if (recs.size() >= 3) {
                        large.add(c);
                        if (sources.size() >= 2) { cross.add(c); }
                    }
                    if (disclosed + discovered > 0) {
                        related.add(c);
                        disclosedTotal += disclosed;
                        discoveredTotal += discovered;
                    }
                }
            }
        }
        Comparator<Map<String, Object>> bySize = Comparator.comparingLong(m -> -Json.lng(m.get("records")));
        Comparator<Map<String, Object>> byRel = Comparator.comparingLong(
            m -> -(Json.lng(m.get("relationships_disclosed")) + Json.lng(m.get("relationships_discovered"))));
        large.sort(bySize); cross.sort(bySize); related.sort(byRel);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records_looked_up", lookups);
        out.put("entities_examined", (long) seen.size());
        out.put("multi_record_3plus", (long) large.size());
        out.put("cross_source_3plus", (long) cross.size());
        out.put("entities_with_relationships", (long) related.size());
        out.put("relationship_ends_disclosed", disclosedTotal);
        out.put("relationship_ends_discovered", discoveredTotal);
        out.put("top_cross_source", cross.subList(0, Math.min(5, cross.size())));
        out.put("top_related", related.subList(0, Math.min(5, related.size())));
        Files.writeString(Path.of("data/results/discover_candidates.json"), Json.write(out), StandardCharsets.UTF_8);
        System.out.println(Json.write(out));
    }
}
