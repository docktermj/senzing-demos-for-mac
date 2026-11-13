import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static com.senzing.sdk.SzFlag.*;

/**
 * Senzing entity-resolution visualization server (Java).
 *
 * Port of the bootcamp's reference server (scripts/senzing_viz_server.py) to the bootcamper's
 * chosen language, implementing the contract in visualization-api-reference.md. It builds an
 * entity model from the loaded records with one getEntity(SzRecordKey, SZ_ENTITY_DEFAULT_FLAGS)
 * call per record (the Truth Set path -- a bootcamper's own datastore needs the export stream
 * instead), serves the JSON API and the single-page tabbed app, and writes a self-contained
 * snapshot. The page itself (HTML/CSS/D3 client) is generated from the reference by
 * generate_assets.py into src/server/assets/.
 *
 * SDK method names, signatures and flags come from the Senzing MCP server
 * (get_sdk_reference, language='java'):
 *   getEntity(SzRecordKey, Set<SzFlag>)        whyRecords(SzRecordKey, SzRecordKey, Set<SzFlag>)
 *   whyRecordInEntity(SzRecordKey, Set<SzFlag>) howEntity(long, Set<SzFlag>)
 *   searchByAttributes(String, Set<SzFlag>)
 *
 * Usage (always through src/scripts/run-java.sh so DYLD_LIBRARY_PATH reaches the JVM):
 *   run-java.sh VizServer --records src/system_verification/truthset_data.jsonl \
 *       --title "Senzing Truth Set" --dataset "the Senzing Truth Set" \
 *       [--snapshot docs/visualizations/truthset_verification.html] [--no-serve] [--port 8080]
 *   run-java.sh VizServer --export --title "..." --dataset "your ... data"   (bootcamper's own data:
 *       the model is built from the export stream instead of a records file)
 */
class VizServer {
    static final String BIND_HOST = "127.0.0.1";          // loopback only, never the wildcard
    static final String SOURCE_KEY_SEP = "|";
    static final int GRAPH_NODE_CAP = 1500;
    static final String SERVER_NONCE = UUID.randomUUID().toString().replace("-", "");
    static final String[] SEARCH_NAME_ATTRS = {"NAME_FULL", "NAME_ORG"};
    static final String[] REQUIRED_PIPELINE_KEYS = {"CONFIGPATH", "RESOURCEPATH", "SUPPORTPATH"};

    // ---- model state --------------------------------------------------------------------
    long recordsTotal = 0;
    final Map<Long, Map<String, Object>> entities = new LinkedHashMap<>();
    final Map<String, Map<String, Object>> edges = new LinkedHashMap<>();   // "min,max" -> meta
    Map<String, Object> featureDist = mapOf("features", new ArrayList<>(), "sampled", 0L,
                                            "multi_record_total", 0L, "capped", false);
    SzEngine engine;

    // ---- assets ---------------------------------------------------------------------------
    String pageTemplate;
    String probeLive;
    String d3Script;
    Map<String, Object> brand;

    // =====================================================================================
    // Model build
    // =====================================================================================
    void build(List<String[]> recordKeys) {
        for (String[] key : recordKeys) {
            recordsTotal++;
            try {
                String resp = engine.getEntity(SzRecordKey.of(key[0], key[1]), SZ_ENTITY_DEFAULT_FLAGS);
                absorb(Json.obj(Json.parse(resp)));
            } catch (Exception e) {
                System.err.println("record " + key[0] + ":" + key[1] + " skipped: " + e);
            }
        }
    }

    /**
     * Builds the model from Senzing's export stream: every resolved entity, one pass. Used for the
     * bootcamper's own datastore (Query, Visualize and Discover), where one getEntity per record
     * would be thousands of round trips and could miss entities no input file names.
     * exportJsonEntityReport(Set<SzFlag>) / fetchNext / closeExportReport per get_sdk_reference and
     * reporting_guide (language='java'). Flags: SZ_EXPORT_INCLUDE_ALL_ENTITIES (which rows) merged
     * with SZ_ENTITY_DEFAULT_FLAGS (what each row carries, including RELATED_ENTITIES); hand-assembled
     * SZ_ENTITY_INCLUDE_* members have produced rows with no relationships and no error. Each export
     * row has the shape of a getEntity response, so absorb() takes it unchanged.
     */
    void buildFromExport() throws SzException {
        long handle = engine.exportJsonEntityReport(flags(SZ_EXPORT_INCLUDE_ALL_ENTITIES, SZ_ENTITY_DEFAULT_FLAGS));
        try {
            for (String row = engine.fetchNext(handle); row != null; row = engine.fetchNext(handle)) {
                if (row.isBlank()) {
                    continue;
                }
                Map<String, Object> resp = Json.obj(Json.parse(row));
                recordsTotal += Json.arr(Json.obj(resp.get("RESOLVED_ENTITY")).get("RECORDS")).size();
                absorb(resp);
            }
        } finally {
            engine.closeExportReport(handle);
        }
    }

    void absorb(Map<String, Object> resp) {
        Map<String, Object> re = Json.obj(resp.get("RESOLVED_ENTITY"));
        Long eid = Json.lng(re.get("ENTITY_ID"));
        if (eid == null) {
            return;
        }
        if (!entities.containsKey(eid)) {
            List<Object> recs = Json.arr(re.get("RECORDS"));
            TreeSet<String> sources = new TreeSet<>();
            List<Object> records = new ArrayList<>();
            for (Object o : recs) {
                Map<String, Object> r = Json.obj(o);
                String ds = Json.str(r.get("DATA_SOURCE"), "?");
                sources.add(ds);
                records.add(mapOf("data_source", ds,
                                  "record_id", Json.str(r.get("RECORD_ID"), "?"),
                                  "match_key", Json.str(r.get("MATCH_KEY"), "")));
            }
            String name = Json.str(re.get("ENTITY_NAME"), "");
            entities.put(eid, mapOf("entity_id", eid,
                                    "entity_name", name.isEmpty() ? "Entity " + eid : name,
                                    "record_count", (long) recs.size(),
                                    "data_sources", new ArrayList<>(sources),
                                    "records", records));
        }
        for (Object o : Json.arr(resp.get("RELATED_ENTITIES"))) {
            Map<String, Object> rel = Json.obj(o);
            Long tid = Json.lng(rel.get("ENTITY_ID"));
            if (tid == null) {
                continue;
            }
            String key = Math.min(eid, tid) + "," + Math.max(eid, tid);
            if (!edges.containsKey(key)) {
                String type = relationshipType(rel);
                edges.put(key, mapOf("a", Math.min(eid, tid), "b", Math.max(eid, tid),
                                     "match_key", Json.str(rel.get("MATCH_KEY"), ""),
                                     "relationship_type", type,
                                     "relationship_label", RELATIONSHIP_LABELS.get(type)));
            }
        }
    }

    /** The contract's closed relationship_type vocabulary; disclosed takes precedence. */
    static final Map<String, String> RELATIONSHIP_LABELS = Map.of(
        "possibly_same", "possibly the same entity",
        "possibly_related", "possibly related",
        "disclosed", "disclosed relationship",
        "ambiguous", "ambiguous");

    static String relationshipType(Map<String, Object> rel) {
        Long disclosed = Json.lng(rel.get("IS_DISCLOSED"));
        Long ambiguous = Json.lng(rel.get("IS_AMBIGUOUS"));
        String level = Json.str(rel.get("MATCH_LEVEL_CODE"), "");
        if (disclosed != null && disclosed == 1) {
            return "disclosed";
        }
        if (ambiguous != null && ambiguous == 1) {
            return "ambiguous";
        }
        if ("POSSIBLY_SAME".equals(level)) {
            return "possibly_same";
        }
        return "possibly_related";
    }

    // =====================================================================================
    // API payloads (shapes per visualization-api-reference.md)
    // =====================================================================================
    List<String> dataSources() {
        TreeSet<String> codes = new TreeSet<>();
        for (Map<String, Object> e : entities.values()) {
            for (Object s : Json.arr(e.get("data_sources"))) {
                if (!String.valueOf(s).isBlank()) {
                    codes.add(String.valueOf(s));
                }
            }
        }
        return new ArrayList<>(codes);
    }

    static long rc(Map<String, Object> e) {
        return Json.lng(e.get("record_count"));
    }

    static Map<String, Object> brief(Map<String, Object> e) {
        return mapOf("entity_id", e.get("entity_id"), "entity_name", e.get("entity_name"),
                     "record_count", e.get("record_count"));
    }

    Map<String, Object> stats() {
        Map<String, Object> hist = mapOf("1", 0L, "2", 0L, "3", 0L, "4+", 0L);
        Map<String, Object> buckets = mapOf("1", new ArrayList<>(), "2", new ArrayList<>(),
                                            "3", new ArrayList<>(), "4+", new ArrayList<>());
        TreeSet<String> all = new TreeSet<>();
        long multi = 0, cross = 0;
        for (Map<String, Object> e : entities.values()) {
            for (Object s : Json.arr(e.get("data_sources"))) {
                all.add(String.valueOf(s));
            }
            long c = rc(e);
            String b = c >= 4 ? "4+" : String.valueOf(c);
            hist.put(b, Json.lng(hist.getOrDefault(b, 0L)) + 1);
            List<Object> lst = Json.arr(buckets.get(b));
            if (buckets.containsKey(b) && lst.size() < 200) {
                lst.add(brief(e));
            }
            if (c > 1) {
                multi++;
            }
            if (Json.arr(e.get("data_sources")).size() >= 2) {
                cross++;
            }
        }
        return mapOf("records_total", recordsTotal,
                     "entities_total", (long) entities.size(),
                     "multi_record_entities", multi,
                     "cross_source_entities", cross,
                     "relationships_total", (long) edges.size(),
                     "data_sources_total", (long) all.size(),
                     "histogram", hist,
                     "bucket_entities", buckets,
                     "sample_entities", sampleEntities(10),
                     "server_nonce", SERVER_NONCE);
    }

    List<Map<String, Object>> byRecordCountDesc() {
        List<Map<String, Object>> list = new ArrayList<>(entities.values());
        list.sort((x, y) -> Long.compare(rc(y), rc(x)));      // stable, like Python's sorted
        return list;
    }

    List<Object> sampleEntities(int cap) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> e : byRecordCountDesc()) {
            if (rc(e) < 2 || out.size() >= cap) {
                break;
            }
            out.add(mapOf("entity_id", e.get("entity_id"), "entity_name", e.get("entity_name"),
                          "record_count", e.get("record_count"),
                          "data_sources", new ArrayList<>(new TreeSet<>(stringList(e.get("data_sources"))))));
        }
        return out;
    }

    Map<String, Object> records(String entityId) {
        Long eid = Json.lng(entityId);
        if (eid == null) {
            return mapOf("entity_id", entityId, "error", "bad request: entity_id must be an integer");
        }
        Map<String, Object> e = entities.get(eid);
        if (e == null) {
            return mapOf("entity_id", eid, "error", "not found: no such entity");
        }
        return mapOf("entity_id", eid, "entity_name", e.get("entity_name"), "records", e.get("records"));
    }

    Map<String, Object> overlap() {
        List<String> sources = dataSources();
        Map<String, Integer> idx = new HashMap<>();
        for (int k = 0; k < sources.size(); k++) {
            idx.put(sources.get(k), k);
        }
        int n = sources.size();
        long[][] m = new long[n][n];
        Map<String, Object> cells = new LinkedHashMap<>();
        boolean capped = false;
        for (Map<String, Object> e : entities.values()) {
            List<String> ds = new ArrayList<>(new TreeSet<>(stringList(e.get("data_sources"))));
            List<int[]> keys = new ArrayList<>();
            for (String s : ds) {
                m[idx.get(s)][idx.get(s)]++;
                keys.add(new int[] {idx.get(s), idx.get(s)});
            }
            for (int i = 0; i < ds.size(); i++) {
                for (int j = i + 1; j < ds.size(); j++) {
                    int a = idx.get(ds.get(i)), b = idx.get(ds.get(j));
                    m[a][b]++;
                    m[b][a]++;
                    keys.add(new int[] {Math.min(a, b), Math.max(a, b)});
                }
            }
            for (int[] k : keys) {
                String ck = k[0] + "," + k[1];
                List<Object> lst = Json.arr(cells.computeIfAbsent(ck, z -> new ArrayList<>()));
                if (lst.size() < 200) {
                    lst.add(brief(e));
                } else {
                    capped = true;
                }
            }
        }
        List<Object> matrix = new ArrayList<>();
        for (long[] row : m) {
            List<Object> r = new ArrayList<>();
            for (long v : row) {
                r.add(v);
            }
            matrix.add(r);
        }
        return mapOf("sources", sources, "matrix", matrix, "cell_entities", cells, "cell_capped", capped);
    }

    Map<String, Object> matchKeys() {
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> e : entities.values()) {
            for (Object o : Json.arr(e.get("records"))) {
                String mk = Json.str(Json.obj(o).get("match_key"), "");
                if (!mk.isEmpty()) {
                    counts.merge(mk, 1L, Long::sum);
                }
            }
        }
        List<Map.Entry<String, Long>> items = new ArrayList<>(counts.entrySet());
        items.sort((x, y) -> !x.getValue().equals(y.getValue())
                ? Long.compare(y.getValue(), x.getValue()) : x.getKey().compareTo(y.getKey()));
        List<Map.Entry<String, Long>> top = items.subList(0, Math.min(20, items.size()));
        Map<String, Object> byKey = new LinkedHashMap<>();
        for (Map.Entry<String, Long> t : top) {
            byKey.put(t.getKey(), new ArrayList<>());
        }
        boolean entCapped = false;
        for (Map<String, Object> e : entities.values()) {
            Set<String> seen = new HashSet<>();
            for (Object o : Json.arr(e.get("records"))) {
                String mk = Json.str(Json.obj(o).get("match_key"), "");
                if (byKey.containsKey(mk) && seen.add(mk)) {
                    List<Object> lst = Json.arr(byKey.get(mk));
                    if (lst.size() < 200) {
                        lst.add(brief(e));
                    } else {
                        entCapped = true;
                    }
                }
            }
        }
        List<Object> mks = new ArrayList<>();
        for (Map.Entry<String, Long> t : top) {
            mks.add(mapOf("match_key", t.getKey(), "count", t.getValue()));
        }
        return mapOf("match_keys", mks, "distinct", (long) counts.size(), "capped", counts.size() > 20,
                     "match_key_entities", byKey, "entities_capped", entCapped);
    }

    /** Every key the graph colors by -- each source plus each combination -- in ONE list. */
    List<String> colorKeys() {
        TreeSet<String> combos = new TreeSet<>();
        for (Map<String, Object> e : entities.values()) {
            List<String> s = new ArrayList<>(new TreeSet<>(stringList(e.get("data_sources"))));
            if (s.size() >= 2) {
                combos.add(String.join(SOURCE_KEY_SEP, s));
            }
        }
        List<String> keys = new ArrayList<>(dataSources());
        keys.addAll(combos);
        return keys;
    }

    Map<String, Object> graph(Integer cap) {
        List<Map<String, Object>> nodes = new ArrayList<>(entities.values());
        long total = nodes.size();
        Set<Long> related = new HashSet<>();
        Map<Long, Long> degree = new HashMap<>();
        for (Map<String, Object> meta : edges.values()) {
            long a = Json.lng(meta.get("a")), b = Json.lng(meta.get("b"));
            related.add(a);
            related.add(b);
            degree.merge(a, 1L, Long::sum);
            degree.merge(b, 1L, Long::sum);
        }
        boolean capped = false;
        if (cap != null && total > cap) {
            nodes.sort(Comparator
                .comparingInt((Map<String, Object> e) -> -new HashSet<>(stringList(e.get("data_sources"))).size())
                .thenComparingLong(e -> -degree.getOrDefault(Json.lng(e.get("entity_id")), 0L))
                .thenComparingLong(e -> -rc(e))
                .thenComparingLong(e -> Json.lng(e.get("entity_id"))));
            nodes = new ArrayList<>(nodes.subList(0, cap));
            capped = true;
        }
        Set<Long> ids = new HashSet<>();
        for (Map<String, Object> e : nodes) {
            ids.add(Json.lng(e.get("entity_id")));
        }
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> meta : edges.values()) {
            long a = Json.lng(meta.get("a")), b = Json.lng(meta.get("b"));
            if (ids.contains(a) && ids.contains(b)) {
                out.add(mapOf("source_entity_id", a, "target_entity_id", b,
                              "match_key", meta.get("match_key"),
                              "relationship_type", meta.get("relationship_type"),
                              "relationship_label", meta.get("relationship_label")));
            }
        }
        return mapOf("nodes", nodes, "edges", out, "total", total, "capped", capped,
                     "related_total", (long) related.size(), "encoding_check", encodingCheck(nodes));
    }

    static Map<String, Object> encodingCheck(List<Map<String, Object>> nodes) {
        TreeSet<String> keys = new TreeSet<>();
        for (Map<String, Object> e : nodes) {
            List<String> s = new ArrayList<>(new TreeSet<>(stringList(e.get("data_sources"))));
            if (!s.isEmpty()) {
                keys.add(String.join(SOURCE_KEY_SEP, s));
            }
        }
        List<String> combos = new ArrayList<>();
        for (String k : keys) {
            if (k.contains(SOURCE_KEY_SEP)) {
                combos.add(k);
            }
        }
        boolean ok = !combos.isEmpty();
        return mapOf("distinct_source_set_keys", (long) keys.size(),
                     "source_set_keys", new ArrayList<>(keys),
                     "combination_keys", combos,
                     "status", ok ? "ok" : "not_exercised",
                     "detail", ok
                         ? "Compare the number of combination rows the source legend names against "
                           + "len(combination_keys); they MUST be equal (INV-270, INV-259). Fewer "
                           + "combination rows means nodes are colored by one member of their source "
                           + "set. Per-source rows are not source-set keys and are not counted."
                         : "No combination key is present, so this check cannot fail and has NOT "
                           + "been exercised (INV-265). It is not a pass.");
    }

    Map<String, Object> merges() {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> e : byRecordCountDesc()) {
            if (rc(e) > 1) {
                out.add(e);
            }
        }
        return mapOf("entities", out);
    }

    Map<String, Object> search(String query) {
        query = query == null ? "" : query.trim();
        if (query.isEmpty()) {
            return mapOf("results", new ArrayList<>());
        }
        List<Object> items = new ArrayList<>();
        List<Object> tried = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String attr : SEARCH_NAME_ATTRS) {
            tried.add(attr);
            try {
                String raw = engine.searchByAttributes(Json.write(mapOf(attr, query)), SZ_ENTITY_DEFAULT_FLAGS);
                items.addAll(Json.arr(Json.obj(Json.parse(raw)).get("RESOLVED_ENTITIES")));
            } catch (Exception ex) {
                // A failed attempt is an attempt, not the end of the list (INV-190).
                failures.add(attr + ": " + ex.getMessage());
                continue;
            }
            if (!items.isEmpty()) {
                break;
            }
        }
        List<Object> results = new ArrayList<>();
        for (Object o : items.subList(0, Math.min(10, items.size()))) {
            Map<String, Object> item = Json.obj(o);
            Map<String, Object> ent = Json.obj(Json.obj(item.get("ENTITY")).get("RESOLVED_ENTITY"));
            Map<String, Object> match = Json.obj(item.get("MATCH_INFO"));
            Long eid = Json.lng(ent.get("ENTITY_ID"));
            Map<String, Object> local = eid == null ? null : entities.get(eid);
            String name = Json.str(ent.get("ENTITY_NAME"), "");
            if (name.isEmpty()) {
                name = local == null ? "?" : Json.str(local.get("entity_name"), "?");
            }
            results.add(mapOf("entity_id", eid, "entity_name", name,
                              "record_count", local == null ? null : local.get("record_count"),
                              "data_sources", local == null ? new ArrayList<>() : local.get("data_sources"),
                              "match_key", Json.str(match.get("MATCH_KEY"), ""),
                              "resolution_rule", Json.str(match.get("ERRULE_CODE"), "")));
        }
        Map<String, Object> out = mapOf("results", results, "attributes_tried", tried);
        if (items.isEmpty() && !failures.isEmpty()) {
            out.put("error", String.join("; ", failures));
        }
        return out;
    }

    static Set<SzFlag> flags(Object... parts) {
        EnumSet<SzFlag> s = EnumSet.noneOf(SzFlag.class);
        for (Object p : parts) {
            if (p instanceof SzFlag) {
                s.add((SzFlag) p);
            } else if (p instanceof Collection) {
                for (Object f : (Collection<?>) p) {
                    s.add((SzFlag) f);
                }
            }
        }
        return s;
    }

    /** Why flags: the default why group plus match-key details (which needs a relations flag). */
    static final Set<SzFlag> WHY_FLAGS = flags(SZ_WHY_RECORDS_DEFAULT_FLAGS, SZ_INCLUDE_MATCH_KEY_DETAILS,
                                               SZ_ENTITY_INCLUDE_ALL_RELATIONS);
    static final Set<SzFlag> WHY_IN_ENTITY_FLAGS = flags(SZ_WHY_RECORD_IN_ENTITY_DEFAULT_FLAGS,
                                                         SZ_INCLUDE_MATCH_KEY_DETAILS, SZ_ENTITY_INCLUDE_ALL_RELATIONS);
    /** How flags: SZ_HOW_ENTITY_DEFAULT_FLAGS alone omits the match-key breakdown (contract). */
    static final Set<SzFlag> HOW_FLAGS = flags(SZ_HOW_ENTITY_DEFAULT_FLAGS, SZ_INCLUDE_MATCH_KEY_DETAILS,
                                               SZ_ENTITY_INCLUDE_ALL_RELATIONS);

    Map<String, Object> how(String entityId) {
        Long eid = Json.lng(entityId);
        if (eid == null) {
            return mapOf("error", "invalid entity_id");
        }
        try {
            return mapOf("entity_id", eid, "result", Json.parse(engine.howEntity(eid, HOW_FLAGS)));
        } catch (Exception ex) {
            return mapOf("entity_id", eid, "error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    Map<String, Object> why(String entityId) {
        Long eid = Json.lng(entityId);
        if (eid == null) {
            return mapOf("error", "invalid entity_id");
        }
        Map<String, Object> e = entities.get(eid);
        List<Object> recs = e == null ? new ArrayList<>() : Json.arr(e.get("records"));
        try {
            String raw;
            String mode;
            if (recs.size() >= 2) {
                Map<String, Object> r1 = Json.obj(recs.get(0)), r2 = Json.obj(recs.get(1));
                raw = engine.whyRecords(
                    SzRecordKey.of(Json.str(r1.get("data_source"), ""), Json.str(r1.get("record_id"), "")),
                    SzRecordKey.of(Json.str(r2.get("data_source"), ""), Json.str(r2.get("record_id"), "")),
                    WHY_FLAGS);
                mode = "why_records";
            } else if (recs.size() == 1) {
                Map<String, Object> r1 = Json.obj(recs.get(0));
                raw = engine.whyRecordInEntity(
                    SzRecordKey.of(Json.str(r1.get("data_source"), ""), Json.str(r1.get("record_id"), "")),
                    WHY_IN_ENTITY_FLAGS);
                mode = "why_record_in_entity";
            } else {
                return mapOf("entity_id", eid, "error", "no records known for this entity");
            }
            return mapOf("entity_id", eid, "mode", mode, "result", Json.parse(raw));
        } catch (Exception ex) {
            return mapOf("entity_id", eid, "error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    void computeFeatureDist(int cap) {
        Map<String, Map<String, Object>> perFeature = new TreeMap<>();
        List<Map<String, Object>> multi = new ArrayList<>();
        for (Map<String, Object> e : byRecordCountDesc()) {
            if (rc(e) > 1) {
                multi.add(e);
            }
        }
        long sampled = 0;
        for (Map<String, Object> e : multi) {
            if (sampled >= cap) {
                break;
            }
            Map<String, Object> res = why(String.valueOf(e.get("entity_id")));
            if (res.containsKey("error") || !res.containsKey("result")) {
                continue;
            }
            boolean counted = false;
            for (Object w : Json.arr(Json.obj(res.get("result")).get("WHY_RESULTS"))) {
                Map<String, Object> fs = Json.obj(Json.obj(Json.obj(w).get("MATCH_INFO")).get("FEATURE_SCORES"));
                for (Map.Entry<String, Object> f : fs.entrySet()) {
                    for (Object sc : Json.arr(f.getValue())) {
                        String bucket = Json.str(Json.obj(sc).get("SCORE_BUCKET"), "").toUpperCase();
                        if (bucket.isEmpty()) {
                            continue;
                        }
                        Map<String, Object> b = perFeature.computeIfAbsent(f.getKey(), z -> new LinkedHashMap<>());
                        b.put(bucket, Json.lng(b.getOrDefault(bucket, 0L)) + 1);
                        counted = true;
                    }
                }
            }
            if (counted) {
                sampled++;
            }
        }
        List<Object> feats = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> f : perFeature.entrySet()) {
            feats.add(mapOf("feature", f.getKey(), "buckets", f.getValue()));
        }
        featureDist = mapOf("features", feats, "sampled", sampled,
                            "multi_record_total", (long) multi.size(), "capped", multi.size() > cap);
    }

    // =====================================================================================
    // Page rendering
    // =====================================================================================
    /** Port of brand_tokens.color_for_sources (cross-checked against brand.json color_check). */
    @SuppressWarnings("unchecked")
    Map<String, Object> colorForSources(List<String> sources) {
        Map<String, Object> pref = Json.obj(brand.get("source_colors"));
        List<Object> fallback = Json.arr(brand.get("fallback_colors"));
        List<Object> strokes = Json.arr(brand.get("source_strokes"));
        List<Object> widths = Json.arr(brand.get("source_stroke_widths"));
        List<Object> shades = Json.arr(brand.get("source_fill_shades"));
        int states = 1 + strokes.size() * widths.size();
        TreeSet<String> codes = new TreeSet<>();
        for (String s : sources) {
            if (s != null && !s.isBlank()) {
                codes.add(s);
            }
        }
        Set<Object> claimed = new HashSet<>();
        for (String c : codes) {
            if (pref.containsKey(c)) {
                claimed.add(pref.get(c));
            }
        }
        List<Object> available = new ArrayList<>();
        for (Object c : fallback) {
            if (!claimed.contains(c)) {
                available.add(c);
            }
        }
        if (available.isEmpty()) {
            available = new ArrayList<>(fallback);
        }
        Map<String, Object> assigned = new LinkedHashMap<>();
        int nth = 0;
        for (String code : codes) {
            String fill;
            int cycle;
            if (pref.containsKey(code)) {
                fill = String.valueOf(pref.get(code));
                cycle = 0;
            } else {
                fill = String.valueOf(available.get(nth % available.size()));
                cycle = nth / available.size();
                double factor = ((Number) shades.get((cycle / states) % shades.size())).doubleValue();
                fill = shade(fill, factor);
                nth++;
            }
            int slot = cycle % states;
            Object stroke;
            Object width;
            if (slot == 0) {
                stroke = strokes.get(0);
                width = null;
            } else {
                int k = slot - 1;
                stroke = strokes.get((k + 1) % strokes.size());
                width = widths.get(k / strokes.size());
            }
            assigned.put(code, mapOf("fill", fill, "stroke", stroke, "stroke_width", width, "cycle", (long) cycle));
        }
        return assigned;
    }

    static String shade(String fill, double factor) {
        if (factor == 0) {
            return fill;
        }
        String target = factor > 0 ? "#FFFFFF" : "#18160F";
        double w = Math.abs(factor);
        int[] out = new int[3];
        for (int k = 0; k < 3; k++) {
            int a = Integer.parseInt(fill.substring(1 + 2 * k, 3 + 2 * k), 16);
            int b = Integer.parseInt(target.substring(1 + 2 * k, 3 + 2 * k), 16);
            out[k] = (int) Math.rint(a + (b - a) * w);            // Python round(): half-to-even
        }
        return String.format("#%02X%02X%02X", out[0], out[1], out[2]);
    }

    String renderPage(String title, String dataShim, String probeBody) {
        // Data shim and D3 replaced LAST so their contents are never rescanned.
        return pageTemplate.replace("__TITLE__", escHtml(title))
            .replace("__PROBE_BODY__", probeBody != null ? probeBody : probeLive)
            .replace("__ROOT_VARS__", Json.str(brand.get("root_vars"), ""))
            .replace("__FONT_STACK__", Json.str(brand.get("font"), ""))
            .replace("__CODE_FONT__", Json.str(brand.get("code_font"), ""))
            .replace("__ACCENT_HOT__", Json.str(brand.get("accent_hot"), ""))
            .replace("__ACCENT__", Json.str(brand.get("accent"), ""))
            .replace("__SRC_COLORS__", Json.scriptSafe(colorForSources(colorKeys())))
            .replace("__DATA_SHIM__", dataShim)
            .replace("__D3_SCRIPT__", d3Script);
    }

    static String escHtml(Object s) {
        return String.valueOf(s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String matchKeyChips(String mk) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char ch : (mk == null ? "" : mk).toCharArray()) {
            if (ch == '+' || ch == '-') {
                if (cur.length() > 0) {
                    parts.add(cur.toString());
                }
                cur = new StringBuilder().append(ch);
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append("<span>").append(escHtml(p)).append("</span>");
        }
        return sb.toString();
    }

    static String resultCard(String searched, Map<String, Object> res) {
        Object rc = res.get("record_count");
        StringBuilder h = new StringBuilder("<div class=\"card\">");
        h.append("<div class=\"muted\" style=\"margin-bottom:4px\">Searched: <b>").append(escHtml(searched)).append("</b></div>");
        h.append("<h4>").append(escHtml(res.getOrDefault("entity_name", "?"))).append("</h4>");
        h.append("<div class=\"muted\">Entity ").append(escHtml(res.get("entity_id"))).append(" · ")
         .append(escHtml(rc != null ? rc : "?")).append(" record(s) · ")
         .append(escHtml(String.join(", ", stringList(res.get("data_sources"))))).append("</div>");
        String mk = Json.str(res.get("match_key"), "");
        if (!mk.isEmpty()) {
            h.append("<div class=\"mk\">").append(matchKeyChips(mk)).append("</div>");
        }
        String rule = Json.str(res.get("resolution_rule"), "");
        if (!rule.isEmpty()) {
            h.append("<div><code>").append(escHtml(rule)).append("</code></div>");
        }
        return h.append("</div>").toString();
    }

    String snapshotProbeHtml(int port, String dataset) {
        List<Object> merges = Json.arr(merges().get("entities"));
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (Object o : merges) {
            ordered.add(Json.obj(o));
        }
        ordered.sort(Comparator.comparingInt((Map<String, Object> e) -> Json.arr(e.get("data_sources")).size() < 2 ? 1 : 0)
                               .thenComparingLong(e -> -rc(e)));
        List<String> cards = new ArrayList<>();
        for (Map<String, Object> ent : ordered) {
            if (cards.size() >= 5) {
                break;
            }
            String name = Json.str(ent.get("entity_name"), "");
            if (name.isEmpty()) {
                continue;
            }
            Map<String, Object> res = null;
            try {
                List<Object> hits = Json.arr(search(name).get("results"));
                for (Object h : hits) {
                    if (Objects.equals(Json.obj(h).get("entity_id"), ent.get("entity_id"))) {
                        res = Json.obj(h);
                        break;
                    }
                }
                if (res == null && !hits.isEmpty()) {
                    res = Json.obj(hits.get(0));
                }
                if (res == null) {
                    // Not verified: the search ran and matched nothing, so it is not offered.
                    System.err.println("dropped snapshot example '" + name + "': search returned no match");
                    continue;
                }
            } catch (Exception ex) {
                System.err.println("snapshot probe search failed for '" + name + "' (non-fatal): " + ex);
                res = mapOf("entity_id", ent.get("entity_id"), "entity_name", name,
                            "record_count", ent.get("record_count"), "data_sources", ent.get("data_sources"),
                            "match_key", "", "resolution_rule", "");
            }
            cards.add(resultCard(name, res));
        }
        String where = (dataset != null && !dataset.isBlank()) ? escHtml(dataset.trim()) : "the loaded data";
        String note = "<p class=\"muted\">This is a saved snapshot, so live search is disabled. Below are "
            + "example searches run against " + where + ". In the live app "
            + "(<code>http://localhost:" + port + "</code>) you can search any name.</p>";
        String browse = "<div id=\"probe-btns\"></div>";
        if (cards.isEmpty()) {
            return note + browse + "<p class=\"muted\">No multi-record entities to show.</p>";
        }
        return note + browse + "<div id=\"results\">" + String.join("", cards) + "</div>";
    }

    void writeSnapshot(String title, String outPath, int port, String dataset) throws IOException {
        Map<String, Object> payload = mapOf("stats", stats(), "graph", graph(GRAPH_NODE_CAP),
                                            "merges", merges(), "overlap", overlap(),
                                            "matchkeys", matchKeys(), "features", featureDist);
        String shim = "<script>const __DATA__=" + Json.scriptSafe(payload) + ";"
            + "var __RECS__=null;"
            + "function __recordsFor(id){if(!__RECS__){__RECS__={};"
            + "(((__DATA__.graph||{}).nodes)||[]).forEach(function(n){"
            + "__RECS__[String(n.entity_id)]={entity_id:n.entity_id,entity_name:n.entity_name,"
            + "records:n.records||[]};});}"
            + "return __RECS__[id]||{entity_id:id,error:'not found: no such entity'};}"
            + "window.fetch=function(u){var p=u.split('?')[0].replace('/api/','');"
            + "var q=(u.split('?')[1]||'');"
            + "if(p==='search'){return Promise.resolve({json:function(){return Promise.resolve({results:[]});}});}"
            + "if(p==='why'||p==='how'){return Promise.resolve({json:function(){return Promise.resolve({error:'Why/How explanations run only against the live visualization server (start it with: src/scripts/run-java.sh VizServer --records ...).'});}});}"
            + "if(p==='records'){var m=q.match(/entity_id=([^&]*)/);"
            + "var r=__recordsFor(m?decodeURIComponent(m[1]):'');"
            + "return Promise.resolve({json:function(){return Promise.resolve(r);}});}"
            + "return Promise.resolve({json:function(){return Promise.resolve(__DATA__[p]);}});};</script>";
        String page = renderPage(title, shim, snapshotProbeHtml(port, dataset));
        Path out = Paths.get(outPath).toAbsolutePath();
        Files.createDirectories(out.getParent());
        Files.writeString(out, page, StandardCharsets.UTF_8);
    }

    // =====================================================================================
    // HTTP server
    // =====================================================================================
    HttpServer serve(String title, int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(BIND_HOST, port), 0);
        server.createContext("/", ex -> {
            try {
                handle(ex, title);
            } catch (Exception e) {
                send(ex, 500, Json.write(mapOf("error", String.valueOf(e))), "application/json");
            }
        });
        server.start();
        return server;
    }

    void handle(HttpExchange ex, String title) throws IOException {
        String path = ex.getRequestURI().getPath();
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        String json = "application/json";
        switch (path) {
            case "/": case "/index.html":
                send(ex, 200, renderPage(title, "", null), "text/html; charset=utf-8"); return;
            case "/api/stats": send(ex, 200, Json.write(stats()), json); return;
            case "/api/graph": send(ex, 200, Json.write(graph(GRAPH_NODE_CAP)), json); return;
            case "/api/merges": send(ex, 200, Json.write(merges()), json); return;
            case "/api/overlap": send(ex, 200, Json.write(overlap()), json); return;
            case "/api/records": send(ex, 200, Json.write(records(q.getOrDefault("entity_id", ""))), json); return;
            case "/api/matchkeys": send(ex, 200, Json.write(matchKeys()), json); return;
            case "/api/features": send(ex, 200, Json.write(featureDist), json); return;
            case "/api/search": send(ex, 200, Json.write(search(q.getOrDefault("q", ""))), json); return;
            case "/api/why": send(ex, 200, Json.write(why(q.getOrDefault("entity_id", ""))), json); return;
            case "/api/how": send(ex, 200, Json.write(how(q.getOrDefault("entity_id", ""))), json); return;
            default: send(ex, 404, Json.write(mapOf("error", "not found")), json);
        }
    }

    static void send(HttpExchange ex, int code, String body, String ctype) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ctype);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) {
            return m;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            m.putIfAbsent(k, v);
        }
        return m;
    }

    /** True when /api/stats on the port is answered by THIS process (nonce comparison). */
    static boolean confirmServerIdentity(int port) {
        String url = "http://" + BIND_HOST + ":" + port + "/api/stats";
        try {
            HttpResponse<String> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).build(),
                      HttpResponse.BodyHandlers.ofString());
            Object answered = Json.obj(Json.parse(r.body())).get("server_nonce");
            if (SERVER_NONCE.equals(answered)) {
                return true;
            }
            System.err.println("ERROR: port " + port + " is answered by a DIFFERENT server.\n  this process: "
                + SERVER_NONCE + "\n  answered by:  " + answered
                + "\nTwo processes are listening on this port. Stop the other server, or use --port.");
        } catch (Exception e) {
            System.err.println("ERROR: could not confirm which server is answering " + url + " (" + e + ").");
        }
        return false;
    }

    // =====================================================================================
    // Startup
    // =====================================================================================
    static List<String> missingPipelineKeys(String raw) {
        try {
            Map<String, Object> pipeline = Json.obj(Json.obj(Json.parse(raw)).get("PIPELINE"));
            List<String> gaps = new ArrayList<>();
            for (String k : REQUIRED_PIPELINE_KEYS) {
                if (Json.str(pipeline.get(k), "").isBlank()) {
                    gaps.add(k);
                }
            }
            return gaps;
        } catch (Exception e) {
            return null;
        }
    }

    static List<String[]> readRecordKeys(List<String> files) throws IOException {
        LinkedHashMap<String, String[]> seen = new LinkedHashMap<>();
        for (String f : files) {
            try (BufferedReader br = Files.newBufferedReader(Paths.get(f), StandardCharsets.UTF_8)) {
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        Map<String, Object> d = Json.obj(Json.parse(line));
                        Object ds = d.get("DATA_SOURCE"), rid = d.get("RECORD_ID");
                        if (ds != null && rid != null) {
                            seen.putIfAbsent(ds + "\u0000" + rid, new String[] {String.valueOf(ds), String.valueOf(rid)});
                        }
                    } catch (Exception ignore) {
                        // a line we cannot parse is skipped, like the reference
                    }
                }
            }
        }
        return new ArrayList<>(seen.values());
    }

    public static void main(String[] args) throws Exception {
        String settingsPath = "config/engine_config.json";
        List<String> recordFiles = new ArrayList<>();
        int port = 8080;
        String title = "Senzing Entity Resolution";
        String dataset = "";
        String snapshot = null;
        boolean noServe = false;
        boolean fromExport = false;
        String assets = "src/server/assets";
        for (int k = 0; k < args.length; k++) {
            switch (args[k]) {
                case "--settings": settingsPath = args[++k]; break;
                case "--records":
                    while (k + 1 < args.length && !args[k + 1].startsWith("--")) {
                        recordFiles.add(args[++k]);
                    }
                    break;
                case "--port": port = Integer.parseInt(args[++k]); break;
                case "--title": title = args[++k]; break;
                case "--dataset": dataset = args[++k]; break;
                case "--snapshot": snapshot = args[++k]; break;
                case "--no-serve": noServe = true; break;
                case "--export": fromExport = true; break;
                case "--assets": assets = args[++k]; break;
                default: System.err.println("unknown argument: " + args[k]); System.exit(2);
            }
        }

        // (INV-091) Refuse to render without the vendored D3 -- before settings or engine work.
        Path d3 = Paths.get(assets, "d3.v7.min.js");
        if (!Files.isReadable(d3)) {
            System.err.println("ERROR: the vendored D3 asset d3.v7.min.js is missing or unreadable at " + d3
                + ". Refusing to render without it; run src/server/generate_assets.py and try again.");
            System.exit(1);
        }
        VizServer v = new VizServer();
        v.d3Script = "<script>" + Files.readString(d3, StandardCharsets.UTF_8) + "</script>";
        v.pageTemplate = Files.readString(Paths.get(assets, "page.html"), StandardCharsets.UTF_8);
        v.probeLive = Files.readString(Paths.get(assets, "probe_live.html"), StandardCharsets.UTF_8);
        v.brand = Json.obj(Json.parse(Files.readString(Paths.get(assets, "brand.json"), StandardCharsets.UTF_8)));

        // Self-check: the Java color allocation must equal the reference's for the same keys.
        Map<String, Object> check = Json.obj(v.brand.get("color_check"));
        String expected = Json.write(check.get("expected"));
        String actual = Json.write(v.colorForSources(stringList(check.get("codes"))));
        if (!expected.equals(actual)) {
            System.err.println("ERROR: source-color allocation differs from the reference.\n  expected "
                + expected + "\n  actual   " + actual);
            System.exit(1);
        }

        // Settings: content-aware precedence -- the file only when its PIPELINE is complete.
        String settings = null;
        Path sp = Paths.get(settingsPath);
        String fileRaw = Files.isReadable(sp) ? Files.readString(sp, StandardCharsets.UTF_8) : "";
        String envRaw = Optional.ofNullable(System.getenv("SENZING_ENGINE_CONFIGURATION_JSON")).orElse("");
        List<String> fileGaps = missingPipelineKeys(fileRaw);
        if (fileGaps != null && fileGaps.isEmpty()) {
            settings = fileRaw;
        } else {
            List<String> envGaps = missingPipelineKeys(envRaw);
            if (envGaps != null && envGaps.isEmpty()) {
                System.err.println("settings: " + settingsPath + " is incomplete or missing; using $SENZING_ENGINE_CONFIGURATION_JSON.");
                settings = envRaw;
            } else {
                System.err.println("Engine settings are incomplete: PIPELINE is missing "
                    + (fileGaps == null ? "(unparseable file)" : fileGaps) + " in " + settingsPath
                    + ". Proceeding would fail with SENZ7426, which points at SUPPORTPATH rather than this file.");
                System.exit(2);
            }
        }

        // The environment must outlive every engine it creates: held for the whole process.
        SzEnvironment env = SzCoreEnvironment.newBuilder().settings(settings)
            .instanceName("bootcamp_viz").verboseLogging(false).build();
        try {
            v.engine = env.getEngine();
            if (fromExport) {
                v.buildFromExport();
            } else if (recordFiles.isEmpty()) {
                System.err.println("--records (Truth Set path) or --export (your own data) is required.");
                System.exit(2);
            } else {
                v.build(readRecordKeys(recordFiles));
            }
            try {
                v.computeFeatureDist(40);
            } catch (Exception e) {
                System.err.println("feature-score distribution unavailable (non-fatal): " + e);
            }
            Map<String, Object> s = v.stats();
            System.out.println("Entity model built: " + s.get("records_total") + " records, " + s.get("entities_total")
                + " entities, " + s.get("multi_record_entities") + " merged, " + s.get("cross_source_entities")
                + " cross-source, " + s.get("relationships_total") + " relationships");
            if (snapshot != null) {
                v.writeSnapshot(title, snapshot, port, dataset);
                System.out.println("Snapshot written: " + snapshot);
            }
            if (noServe) {
                return;
            }
            HttpServer server;
            try {
                server = v.serve(title, port);
            } catch (IOException e) {
                System.err.println("ERROR: could not bind " + BIND_HOST + ":" + port + " (" + e.getMessage()
                    + "). Another server holds that port; use --port.");
                System.exit(1);
                return;
            }
            if (!confirmServerIdentity(port)) {
                server.stop(0);
                System.exit(1);
            }
            System.out.println("Visualization running: http://localhost:" + port);
            System.out.flush();
            Object lock = new Object();
            synchronized (lock) {
                lock.wait();
            }
        } finally {
            env.destroy();
        }
    }

    // ---- small helpers -----------------------------------------------------------------
    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k < kv.length; k += 2) {
            m.put(String.valueOf(kv[k]), kv[k + 1]);
        }
        return m;
    }

    static List<String> stringList(Object o) {
        List<String> out = new ArrayList<>();
        for (Object x : Json.arr(o)) {
            out.add(String.valueOf(x));
        }
        return out;
    }
}
