import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

import static com.senzing.sdk.SzFlag.*;

/**
 * Senzing entity-resolution visualization server -- Java port of the bootcamp's shipped
 * reference {@code scripts/senzing_viz_server.py} (INV-090), implementing the
 * {@code visualization-api-reference.md} contract.
 *
 * <p>Builds an entity model from the loaded records through the Senzing SDK (one
 * {@code getEntity(SzRecordKey, SZ_ENTITY_DEFAULT_FLAGS)} per record -- the Truth Set path;
 * never SQL), then serves the single-page D3 app and its JSON APIs:
 * /api/stats, /api/graph, /api/merges, /api/records, /api/search, /api/why, /api/how,
 * /api/overlap, /api/matchkeys, /api/features. It can also write a self-contained snapshot.
 *
 * <p>SDK method signatures and flag names come from the Senzing MCP server
 * (get_sdk_reference, server 1.46.0, 2026-11-14): getEntity, searchByAttributes,
 * whyRecords, whyRecordInEntity, howEntity; SZ_ENTITY_DEFAULT_FLAGS,
 * SZ_WHY_RECORDS_DEFAULT_FLAGS, SZ_WHY_RECORD_IN_ENTITY_DEFAULT_FLAGS,
 * SZ_HOW_ENTITY_DEFAULT_FLAGS. JSON handling uses MiniJson (dependency-free) because the
 * Senzing runtime ships no JSON library. The browser page, brand tokens and vendored D3 are
 * generated from the reference by src/server/generate_assets.py into src/server/assets/.
 *
 * <pre>
 *   source src/scripts/senzing-env.sh
 *   src/scripts/run-java.sh data/temp/server_classes TruthsetVizServer \
 *       --records src/system_verification/truthset_data.jsonl \
 *       --title "Senzing Truth Set" --dataset "the Senzing Truth Set" \
 *       --snapshot docs/visualizations/truthset_verification.html [--no-serve] [--port 8080]
 *
 *   # the bootcamper's own loaded data (Query, Visualize and Discover), built from the export stream:
 *   src/scripts/run-java.sh data/temp/server_classes TruthsetVizServer --from-export \
 *       --title "Banking Customer 360 and KYC" --dataset "your CORE_BANKING, ... data" \
 *       --snapshot docs/visualizations/results_visualization.html --port 8081
 * </pre>
 */
public class TruthsetVizServer {
    /** Loopback only: a wildcard bind can coexist with another server on the same port. */
    static final String BIND_HOST = "127.0.0.1";
    static final String SOURCE_KEY_SEP = "|";
    static final int GRAPH_NODE_CAP = 1500;
    static final String SERVER_NONCE = UUID.randomUUID().toString().replace("-", "");
    static final List<String> REQUIRED_PIPELINE_KEYS = List.of("CONFIGPATH", "RESOURCEPATH", "SUPPORTPATH");
    static final String[] SEARCH_NAME_ATTRS = {"NAME_FULL", "NAME_ORG"};

    // ---------------------------------------------------------------- assets / brand
    static Path assetsDir = Paths.get("src/server/assets");
    /** --from-export: build from the SDK export stream (the bootcamper's own data, Query, Visualize and Discover). */
    static boolean exportMode = false;
    static Map<String, Object> brandDoc;

    static Map<String, Object> brand() { return MiniJson.obj(brandDoc.get("brand")); }

    static String d3Script() throws IOException {
        Path d3 = assetsDir.resolve("vendor/d3.v7.min.js");
        if (!Files.isReadable(d3)) {
            throw new IOException("vendored D3 asset missing or unreadable: " + d3
                + "; refusing to render without it (INV-091)");
        }
        return "<script>" + Files.readString(d3, StandardCharsets.UTF_8) + "</script>";
    }

    /** Port of brand_tokens.color_for_sources: colors from the keys actually present (INV-127). */
    static Map<String, Object> colorForSources(Collection<String> sources) {
        Map<String, Object> sourceColors = MiniJson.obj(brandDoc.get("source_colors"));
        List<Object> fallback = MiniJson.arr(brandDoc.get("fallback_colors"));
        List<Object> strokes = MiniJson.arr(brandDoc.get("source_strokes"));
        List<Object> widths = MiniJson.arr(brandDoc.get("source_stroke_widths"));
        List<Object> shades = MiniJson.arr(brandDoc.get("source_fill_shades"));
        int states = 1 + strokes.size() * widths.size();
        TreeSet<String> codes = new TreeSet<>();
        for (String s : sources) if (s != null && !s.trim().isEmpty()) codes.add(s);
        Map<String, String> preferred = new LinkedHashMap<>();
        for (String c : codes) if (sourceColors.containsKey(c)) preferred.put(c, (String) sourceColors.get(c));
        Set<String> claimed = new HashSet<>(preferred.values());
        List<String> available = new ArrayList<>();
        for (Object f : fallback) if (!claimed.contains((String) f)) available.add((String) f);
        if (available.isEmpty()) for (Object f : fallback) available.add((String) f);
        Map<String, Object> assigned = new LinkedHashMap<>();
        int nth = 0;
        for (String code : codes) {
            String fill;
            int cycle;
            if (preferred.containsKey(code)) {
                fill = preferred.get(code);
                cycle = 0;
            } else {
                fill = available.get(nth % available.size());
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
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("fill", fill);
            a.put("stroke", stroke);
            a.put("stroke_width", width);
            a.put("cycle", (long) cycle);
            assigned.put(code, a);
        }
        return assigned;
    }

    static String shade(String fill, double factor) {
        if (factor == 0) return fill;
        String target = factor > 0 ? "#FFFFFF" : "#18160F";
        double w = Math.abs(factor);
        StringBuilder b = new StringBuilder("#");
        for (int k = 1; k < 7; k += 2) {
            int a = Integer.parseInt(fill.substring(k, k + 2), 16);
            int t = Integer.parseInt(target.substring(k, k + 2), 16);
            b.append(String.format("%02X", (int) Math.rint(a + (t - a) * w)));
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- the model
    static final class Model {
        long recordsTotal = 0;
        final Map<Long, Map<String, Object>> entities = new LinkedHashMap<>();
        final Map<List<Long>, Map<String, Object>> edges = new LinkedHashMap<>();
        Map<String, Object> featureDist = mapOf("features", new ArrayList<>(), "sampled", 0L,
                                                "multi_record_total", 0L, "capped", false);

        Model build(SzEngine engine, Set<SzFlag> flags, List<String[]> keys) {
            for (String[] k : keys) {
                recordsTotal++;
                try {
                    absorb(MiniJson.obj(MiniJson.parse(engine.getEntity(SzRecordKey.of(k[0], k[1]), flags))));
                } catch (Exception e) {
                    // a record that cannot be looked up is skipped, as in the reference
                }
            }
            return this;
        }

        /**
         * Builds the model from Senzing's export stream instead of one getEntity per record (INV-289):
         * one pass, and every resolved entity is included whether or not its records are in a file.
         * Calls from the Senzing MCP server (2026-11-14): exportJsonEntityReport(flags: Set&lt;SzFlag&gt;)
         * -> long handle, fetchNext(handle) until null, closeExportReport(handle) in finally
         * (reporting_guide topic='evaluation', java). Flags: SZ_EXPORT_INCLUDE_ALL_ENTITIES plus
         * SZ_ENTITY_DEFAULT_FLAGS, both Set&lt;SzFlag&gt; composites in Java, so they are merged; the
         * entity default is what puts RELATED_ENTITIES[] and RECORDS[] on each row (INV-179).
         */
        Model buildFromExport(SzEngine engine) throws SzException {
            Set<SzFlag> exportFlags = EnumSet.noneOf(SzFlag.class);
            exportFlags.addAll(SZ_EXPORT_INCLUDE_ALL_ENTITIES);
            exportFlags.addAll(SZ_ENTITY_DEFAULT_FLAGS);
            long handle = engine.exportJsonEntityReport(exportFlags);
            try {
                String row;
                while ((row = engine.fetchNext(handle)) != null) {
                    if (row.isBlank()) continue;
                    Map<String, Object> resp = MiniJson.obj(MiniJson.parse(row));
                    recordsTotal += MiniJson.arr(MiniJson.obj(resp.get("RESOLVED_ENTITY")).get("RECORDS")).size();
                    absorb(resp);
                }
            } finally {
                engine.closeExportReport(handle);
            }
            return this;
        }

        void absorb(Map<String, Object> resp) {
            Map<String, Object> re = MiniJson.obj(resp.get("RESOLVED_ENTITY"));
            Object eidObj = re.get("ENTITY_ID");
            if (!(eidObj instanceof Number)) return;
            long eid = ((Number) eidObj).longValue();
            if (!entities.containsKey(eid)) {
                List<Object> records = MiniJson.arr(re.get("RECORDS"));
                TreeSet<String> sources = new TreeSet<>();
                List<Object> recs = new ArrayList<>();
                for (Object ro : records) {
                    Map<String, Object> r = MiniJson.obj(ro);
                    String ds = r.get("DATA_SOURCE") == null ? "?" : String.valueOf(r.get("DATA_SOURCE"));
                    sources.add(ds);
                    Object mk = r.get("MATCH_KEY");
                    recs.add(mapOf("data_source", ds,
                                   "record_id", r.get("RECORD_ID") == null ? "?" : String.valueOf(r.get("RECORD_ID")),
                                   "match_key", mk == null ? "" : String.valueOf(mk)));
                }
                String name = MiniJson.str(re.get("ENTITY_NAME"));
                entities.put(eid, mapOf("entity_id", eid,
                                        "entity_name", (name == null || name.isEmpty()) ? "Entity " + eid : name,
                                        "record_count", (long) records.size(),
                                        "data_sources", new ArrayList<>(sources),
                                        "records", recs));
            }
            for (Object relO : MiniJson.arr(resp.get("RELATED_ENTITIES"))) {
                Map<String, Object> rel = MiniJson.obj(relO);
                if (!(rel.get("ENTITY_ID") instanceof Number)) continue;
                long tid = ((Number) rel.get("ENTITY_ID")).longValue();
                List<Long> key = List.of(Math.min(eid, tid), Math.max(eid, tid));
                if (!edges.containsKey(key)) {
                    String type = firstNonEmpty(MiniJson.str(rel.get("MATCH_LEVEL_CODE")),
                                                MiniJson.str(rel.get("ERRULE_CODE")), "RELATED");
                    Object mk = rel.get("MATCH_KEY");
                    edges.put(key, mapOf("match_key", mk == null ? "" : String.valueOf(mk),
                                         "relationship_type", type));
                }
            }
        }

        static long rc(Map<String, Object> e) { return ((Number) e.get("record_count")).longValue(); }

        @SuppressWarnings("unchecked")
        static List<String> ds(Map<String, Object> e) { return (List<String>) e.get("data_sources"); }

        @SuppressWarnings("unchecked")
        static List<Map<String, Object>> recs(Map<String, Object> e) {
            return (List<Map<String, Object>>) (List<?>) e.get("records");
        }

        static Map<String, Object> brief(Map<String, Object> e) {
            return mapOf("entity_id", e.get("entity_id"), "entity_name", e.get("entity_name"),
                         "record_count", e.get("record_count"));
        }

        List<String> dataSources() {
            TreeSet<String> codes = new TreeSet<>();
            for (Map<String, Object> e : entities.values())
                for (String s : ds(e)) if (s != null && !s.trim().isEmpty()) codes.add(s);
            return new ArrayList<>(codes);
        }

        Map<String, Object> stats() {
            Map<String, Object> hist = mapOf("1", 0L, "2", 0L, "3", 0L, "4+", 0L);
            Map<String, Object> buckets = mapOf("1", new ArrayList<>(), "2", new ArrayList<>(),
                                                "3", new ArrayList<>(), "4+", new ArrayList<>());
            Set<String> all = new HashSet<>();
            long multi = 0, cross = 0;
            for (Map<String, Object> e : entities.values()) {
                all.addAll(ds(e));
                long c = rc(e);
                String b = c >= 4 ? "4+" : String.valueOf(c);
                hist.put(b, ((Number) hist.getOrDefault(b, 0L)).longValue() + 1);
                List<Object> lst = MiniJson.arr(buckets.get(b));
                if (buckets.containsKey(b) && lst.size() < 200) lst.add(brief(e));
                if (c > 1) multi++;
                if (ds(e).size() >= 2) cross++;
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

        List<Object> sampleEntities(int cap) {
            List<Map<String, Object>> sorted = new ArrayList<>(entities.values());
            sorted.sort((a, b) -> Long.compare(rc(b), rc(a)));
            List<Object> out = new ArrayList<>();
            for (Map<String, Object> e : sorted) {
                if (rc(e) < 2) break;
                Map<String, Object> m = brief(e);
                m.put("data_sources", new ArrayList<>(new TreeSet<>(ds(e))));
                out.add(m);
                if (out.size() >= cap) break;
            }
            return out;
        }

        Map<String, Object> records(String entityId) {
            long eid;
            try { eid = Long.parseLong(entityId.trim()); }
            catch (Exception e) { return mapOf("entity_id", entityId, "error", "bad request: entity_id must be an integer"); }
            Map<String, Object> e = entities.get(eid);
            if (e == null) return mapOf("entity_id", eid, "error", "not found: no such entity");
            return mapOf("entity_id", eid, "entity_name", e.get("entity_name"), "records", e.get("records"));
        }

        Map<String, Object> overlap() {
            TreeSet<String> srcSet = new TreeSet<>();
            for (Map<String, Object> e : entities.values()) srcSet.addAll(ds(e));
            List<String> sources = new ArrayList<>(srcSet);
            Map<String, Integer> idx = new HashMap<>();
            for (int k = 0; k < sources.size(); k++) idx.put(sources.get(k), k);
            int n = sources.size();
            long[][] matrix = new long[n][n];
            for (Map<String, Object> e : entities.values()) {
                List<String> d = new ArrayList<>(new TreeSet<>(ds(e)));
                for (String s : d) matrix[idx.get(s)][idx.get(s)]++;
                for (int a = 0; a < d.size(); a++)
                    for (int b = a + 1; b < d.size(); b++) {
                        matrix[idx.get(d.get(a))][idx.get(d.get(b))]++;
                        matrix[idx.get(d.get(b))][idx.get(d.get(a))]++;
                    }
            }
            Map<String, Object> cells = new LinkedHashMap<>();
            boolean capped = false;
            for (Map<String, Object> e : entities.values()) {
                List<String> d = new ArrayList<>(new TreeSet<>(ds(e)));
                List<int[]> keys = new ArrayList<>();
                for (String s : d) keys.add(new int[]{idx.get(s), idx.get(s)});
                for (int a = 0; a < d.size(); a++)
                    for (int b = a + 1; b < d.size(); b++) {
                        int x = idx.get(d.get(a)), y = idx.get(d.get(b));
                        keys.add(new int[]{Math.min(x, y), Math.max(x, y)});
                    }
                for (int[] k : keys) {
                    String ck = k[0] + "," + k[1];
                    List<Object> lst = MiniJson.arr(cells.computeIfAbsent(ck, z -> new ArrayList<>()));
                    if (lst.size() < 200) lst.add(brief(e)); else capped = true;
                }
            }
            List<Object> mat = new ArrayList<>();
            for (long[] row : matrix) {
                List<Object> r = new ArrayList<>();
                for (long v : row) r.add(v);
                mat.add(r);
            }
            return mapOf("sources", sources, "matrix", mat, "cell_entities", cells, "cell_capped", capped);
        }

        Map<String, Object> matchKeys() {
            Map<String, Long> counts = new HashMap<>();
            for (Map<String, Object> e : entities.values())
                for (Map<String, Object> r : recs(e)) {
                    String mk = String.valueOf(r.getOrDefault("match_key", ""));
                    if (!mk.isEmpty()) counts.merge(mk, 1L, Long::sum);
                }
            List<Map.Entry<String, Long>> items = new ArrayList<>(counts.entrySet());
            items.sort((a, b) -> a.getValue().equals(b.getValue())
                ? a.getKey().compareTo(b.getKey()) : Long.compare(b.getValue(), a.getValue()));
            List<Map.Entry<String, Long>> top = items.subList(0, Math.min(20, items.size()));
            Map<String, Object> byKey = new LinkedHashMap<>();
            for (Map.Entry<String, Long> t : top) byKey.put(t.getKey(), new ArrayList<>());
            boolean entCapped = false;
            for (Map<String, Object> e : entities.values()) {
                Set<String> seen = new HashSet<>();
                for (Map<String, Object> r : recs(e)) {
                    String mk = String.valueOf(r.getOrDefault("match_key", ""));
                    if (byKey.containsKey(mk) && seen.add(mk)) {
                        List<Object> lst = MiniJson.arr(byKey.get(mk));
                        if (lst.size() < 200) lst.add(brief(e)); else entCapped = true;
                    }
                }
            }
            List<Object> mks = new ArrayList<>();
            for (Map.Entry<String, Long> t : top) mks.add(mapOf("match_key", t.getKey(), "count", t.getValue()));
            return mapOf("match_keys", mks, "distinct", (long) counts.size(), "capped", counts.size() > 20,
                         "match_key_entities", byKey, "entities_capped", entCapped);
        }

        /** Every key the graph colors by: each source, then each source COMBINATION, one list. */
        List<String> colorKeys() {
            TreeSet<String> combos = new TreeSet<>();
            for (Map<String, Object> e : entities.values()) {
                List<String> d = new ArrayList<>(new TreeSet<>(ds(e)));
                if (d.size() >= 2) combos.add(String.join(SOURCE_KEY_SEP, d));
            }
            List<String> out = new ArrayList<>(new TreeSet<>(dataSources()));
            out.addAll(combos);
            return out;
        }

        Map<String, Object> graph(Integer cap) {
            List<Map<String, Object>> nodes = new ArrayList<>(entities.values());
            Set<Long> nodeIds = new HashSet<>(entities.keySet());
            int total = nodes.size();
            Set<Long> ends = new HashSet<>();
            for (List<Long> p : edges.keySet()) ends.addAll(p);
            boolean capped = false;
            if (cap != null && total > cap) {
                Map<Long, Long> degree = new HashMap<>();
                for (List<Long> p : edges.keySet()) {
                    degree.merge(p.get(0), 1L, Long::sum);
                    degree.merge(p.get(1), 1L, Long::sum);
                }
                nodes.sort(Comparator
                    .comparingInt((Map<String, Object> e) -> -new HashSet<>(ds(e)).size())
                    .thenComparingLong(e -> -degree.getOrDefault((Long) e.get("entity_id"), 0L))
                    .thenComparingLong(e -> -rc(e))
                    .thenComparingLong(e -> (Long) e.get("entity_id")));
                nodes = new ArrayList<>(nodes.subList(0, cap));
                nodeIds = new HashSet<>();
                for (Map<String, Object> e : nodes) nodeIds.add((Long) e.get("entity_id"));
                capped = true;
            }
            List<Object> edgeList = new ArrayList<>();
            for (Map.Entry<List<Long>, Map<String, Object>> en : edges.entrySet()) {
                long a = en.getKey().get(0), b = en.getKey().get(1);
                if (nodeIds.contains(a) && nodeIds.contains(b))
                    edgeList.add(mapOf("source_entity_id", a, "target_entity_id", b,
                                       "match_key", en.getValue().get("match_key"),
                                       "relationship_type", en.getValue().get("relationship_type")));
            }
            return mapOf("nodes", nodes, "edges", edgeList, "total", (long) total, "capped", capped,
                         "related_total", (long) ends.size(), "encoding_check", encodingCheck(nodes));
        }

        static Map<String, Object> encodingCheck(List<Map<String, Object>> nodes) {
            TreeSet<String> keys = new TreeSet<>();
            for (Map<String, Object> e : nodes) {
                List<String> d = new ArrayList<>(new TreeSet<>(ds(e)));
                if (!d.isEmpty()) keys.add(String.join(SOURCE_KEY_SEP, d));
            }
            List<String> combos = new ArrayList<>();
            for (String k : keys) if (k.contains(SOURCE_KEY_SEP)) combos.add(k);
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
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> e : entities.values()) if (rc(e) > 1) out.add(e);
            out.sort((a, b) -> Long.compare(rc(b), rc(a)));
            return mapOf("entities", out);
        }

        /** Tries NAME_FULL, then NAME_ORG (organizations do not match under NAME_FULL). */
        Map<String, Object> search(SzEngine engine, Set<SzFlag> flags, String query) {
            query = query == null ? "" : query.trim();
            if (query.isEmpty()) return mapOf("results", new ArrayList<>());
            List<Object> items = new ArrayList<>();
            List<Object> tried = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (String attr : SEARCH_NAME_ATTRS) {
                tried.add(attr);
                try {
                    String raw = engine.searchByAttributes(MiniJson.write(mapOf(attr, query)), flags);
                    items.addAll(MiniJson.arr(MiniJson.obj(MiniJson.parse(raw)).get("RESOLVED_ENTITIES")));
                } catch (Exception exc) {
                    failures.add(attr + ": " + exc.getMessage());
                    continue;
                }
                if (!items.isEmpty()) break;
            }
            List<Object> results = new ArrayList<>();
            for (Object it : items.subList(0, Math.min(10, items.size()))) {
                Map<String, Object> item = MiniJson.obj(it);
                Map<String, Object> ent = MiniJson.obj(MiniJson.obj(item.get("ENTITY")).get("RESOLVED_ENTITY"));
                Object eidO = ent.get("ENTITY_ID");
                Long eid = eidO instanceof Number ? ((Number) eidO).longValue() : null;
                Map<String, Object> match = MiniJson.obj(item.get("MATCH_INFO"));
                Map<String, Object> local = eid == null ? new HashMap<>() : entities.getOrDefault(eid, new HashMap<>());
                String nm = MiniJson.str(ent.get("ENTITY_NAME"));
                results.add(mapOf("entity_id", eid,
                                  "entity_name", (nm == null || nm.isEmpty()) ? String.valueOf(local.getOrDefault("entity_name", "?")) : nm,
                                  "record_count", local.get("record_count"),
                                  "data_sources", local.getOrDefault("data_sources", new ArrayList<>()),
                                  "match_key", match.get("MATCH_KEY") == null ? "" : match.get("MATCH_KEY"),
                                  "resolution_rule", match.get("ERRULE_CODE") == null ? "" : match.get("ERRULE_CODE")));
            }
            Map<String, Object> out = mapOf("results", results, "attributes_tried", tried);
            if (items.isEmpty() && !failures.isEmpty()) out.put("error", String.join("; ", failures));
            return out;
        }

        Map<String, Object> how(SzEngine engine, String entityId) {
            long eid;
            try { eid = Long.parseLong(entityId.trim()); } catch (Exception e) { return mapOf("error", "invalid entity_id"); }
            try {
                return mapOf("entity_id", eid, "result", MiniJson.parse(engine.howEntity(eid, SZ_HOW_ENTITY_DEFAULT_FLAGS)));
            } catch (Exception exc) {
                return mapOf("entity_id", eid, "error", exc.getClass().getSimpleName() + ": " + exc.getMessage());
            }
        }

        Map<String, Object> why(SzEngine engine, String entityId) {
            long eid;
            try { eid = Long.parseLong(entityId.trim()); } catch (Exception e) { return mapOf("error", "invalid entity_id"); }
            Map<String, Object> e = entities.get(eid);
            List<Map<String, Object>> rs = e == null ? new ArrayList<>() : recs(e);
            try {
                String raw;
                String mode;
                if (rs.size() >= 2) {
                    raw = engine.whyRecords(
                        SzRecordKey.of((String) rs.get(0).get("data_source"), (String) rs.get(0).get("record_id")),
                        SzRecordKey.of((String) rs.get(1).get("data_source"), (String) rs.get(1).get("record_id")),
                        SZ_WHY_RECORDS_DEFAULT_FLAGS);
                    mode = "why_records";
                } else if (rs.size() == 1) {
                    raw = engine.whyRecordInEntity(
                        SzRecordKey.of((String) rs.get(0).get("data_source"), (String) rs.get(0).get("record_id")),
                        SZ_WHY_RECORD_IN_ENTITY_DEFAULT_FLAGS);
                    mode = "why_record_in_entity";
                } else {
                    return mapOf("entity_id", eid, "error", "no records known for this entity");
                }
                return mapOf("entity_id", eid, "mode", mode, "result", MiniJson.parse(raw));
            } catch (Exception exc) {
                return mapOf("entity_id", eid, "error", exc.getClass().getSimpleName() + ": " + exc.getMessage());
            }
        }

        /** Feature-score buckets across a capped sample of multi-record entities (non-fatal). */
        void computeFeatureDist(SzEngine engine, int cap) {
            Map<String, Map<String, Long>> perFeature = new TreeMap<>();
            List<Map<String, Object>> multi = new ArrayList<>();
            for (Map<String, Object> e : entities.values()) if (rc(e) > 1) multi.add(e);
            multi.sort((a, b) -> Long.compare(rc(b), rc(a)));
            long sampled = 0;
            for (Map<String, Object> e : multi) {
                if (sampled >= cap) break;
                Map<String, Object> res = why(engine, String.valueOf(e.get("entity_id")));
                if (res.containsKey("error") || !res.containsKey("result")) continue;
                boolean counted = false;
                for (Object wo : MiniJson.arr(MiniJson.obj(res.get("result")).get("WHY_RESULTS"))) {
                    Map<String, Object> fs = MiniJson.obj(MiniJson.obj(MiniJson.obj(wo).get("MATCH_INFO")).get("FEATURE_SCORES"));
                    for (Map.Entry<String, Object> fe : fs.entrySet()) {
                        for (Object sc : MiniJson.arr(fe.getValue())) {
                            String bucket = String.valueOf(MiniJson.obj(sc).getOrDefault("SCORE_BUCKET", "")).toUpperCase();
                            if (bucket.isEmpty() || bucket.equals("NULL")) continue;
                            perFeature.computeIfAbsent(fe.getKey(), z -> new LinkedHashMap<>()).merge(bucket, 1L, Long::sum);
                            counted = true;
                        }
                    }
                }
                if (counted) sampled++;
            }
            List<Object> feats = new ArrayList<>();
            for (Map.Entry<String, Map<String, Long>> f : perFeature.entrySet())
                feats.add(mapOf("feature", f.getKey(), "buckets", f.getValue()));
            featureDist = mapOf("features", feats, "sampled", sampled,
                                "multi_record_total", (long) multi.size(), "capped", multi.size() > cap);
        }
    }

    // ---------------------------------------------------------------- page rendering
    static String renderPage(String title, String dataShim, String probeBody, Collection<String> sources)
            throws IOException {
        Map<String, Object> b = brand();
        String rootVars = "--navy:" + b.get("dark") + ";--blue:" + b.get("accent") + ";--gold:" + b.get("accent_hot")
            + ";--ink:" + b.get("ink") + ";--muted:" + b.get("muted") + ";--line:" + b.get("line")
            + ";--bg:" + b.get("bg") + ";--accent-soft:" + b.get("accent_soft") + ";--green:" + b.get("green");
        String page = Files.readString(assetsDir.resolve("page_template.html"), StandardCharsets.UTF_8);
        String live = Files.readString(assetsDir.resolve("probe_body_live.html"), StandardCharsets.UTF_8);
        Collection<String> keys = (sources == null || sources.isEmpty())
            ? MiniJson.obj(brandDoc.get("source_colors")).keySet() : sources;
        // Replace D3 and the data shim LAST so their contents are never rescanned.
        return page.replace("__TITLE__", escHtml(title))
            .replace("__PROBE_BODY__", probeBody != null ? probeBody : live)
            .replace("__ROOT_VARS__", rootVars)
            .replace("__FONT_STACK__", String.valueOf(b.get("font")))
            .replace("__CODE_FONT__", String.valueOf(b.get("code_font")))
            .replace("__ACCENT_HOT__", String.valueOf(b.get("accent_hot")))
            .replace("__ACCENT__", String.valueOf(b.get("accent")))
            .replace("__SRC_COLORS__", MiniJson.writeForScript(colorForSources(keys)))
            .replace("__DATA_SHIM__", dataShim)
            .replace("__D3_SCRIPT__", d3Script());
    }

    static String escHtml(Object o) {
        return String.valueOf(o == null ? "" : o).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String matchKeyChips(String mk) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char ch : (mk == null ? "" : mk).toCharArray()) {
            if (ch == '+' || ch == '-') {
                if (cur.length() > 0) parts.add(cur.toString());
                cur = new StringBuilder().append(ch);
            } else cur.append(ch);
        }
        if (cur.length() > 0) parts.add(cur.toString());
        StringBuilder b = new StringBuilder();
        for (String p : parts) if (!p.isEmpty()) b.append("<span>").append(escHtml(p)).append("</span>");
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    static String resultCard(String searched, Map<String, Object> res) {
        Object rc = res.get("record_count");
        StringBuilder h = new StringBuilder("<div class=\"card\">");
        h.append("<div class=\"muted\" style=\"margin-bottom:4px\">Searched: <b>").append(escHtml(searched)).append("</b></div>");
        h.append("<h4>").append(escHtml(res.getOrDefault("entity_name", "?"))).append("</h4>");
        h.append("<div class=\"muted\">Entity ").append(escHtml(res.get("entity_id"))).append(" · ")
         .append(escHtml(rc != null ? rc : "?")).append(" record(s) · ")
         .append(escHtml(String.join(", ", (List<String>) res.getOrDefault("data_sources", new ArrayList<>()))))
         .append("</div>");
        String mk = String.valueOf(res.getOrDefault("match_key", ""));
        if (!mk.isEmpty()) h.append("<div class=\"mk\">").append(matchKeyChips(mk)).append("</div>");
        String rule = String.valueOf(res.getOrDefault("resolution_rule", ""));
        if (!rule.isEmpty()) h.append("<div><code>").append(escHtml(rule)).append("</code></div>");
        return h.append("</div>").toString();
    }

    @SuppressWarnings("unchecked")
    static String snapshotProbeHtml(Model model, SzEngine engine, Set<SzFlag> flags, int port, String dataset) {
        List<Map<String, Object>> merges = (List<Map<String, Object>>) model.merges().get("entities");
        List<Map<String, Object>> ordered = new ArrayList<>(merges);
        ordered.sort(Comparator.comparing((Map<String, Object> e) -> Model.ds(e).size() < 2)
                               .thenComparingLong(e -> -Model.rc(e)));
        List<String> cards = new ArrayList<>();
        for (Map<String, Object> ent : ordered) {
            if (cards.size() >= 5) break;
            String name = String.valueOf(ent.getOrDefault("entity_name", ""));
            if (name.isEmpty()) continue;
            Map<String, Object> res = null;
            try {
                List<Object> hits = MiniJson.arr(model.search(engine, flags, name).get("results"));
                for (Object h : hits)
                    if (Objects.equals(MiniJson.obj(h).get("entity_id"), ent.get("entity_id"))) { res = MiniJson.obj(h); break; }
                if (res == null && !hits.isEmpty()) res = MiniJson.obj(hits.get(0));
                if (res == null) {
                    System.err.println("dropped snapshot example '" + name + "': search returned no match "
                        + "(the example is not verified, so it is not offered)");
                    continue;
                }
            } catch (Exception exc) {
                System.err.println("snapshot probe search failed for '" + name + "' (non-fatal): " + exc);
                res = mapOf("entity_id", ent.get("entity_id"), "entity_name", name,
                            "record_count", ent.get("record_count"), "data_sources", ent.get("data_sources"),
                            "match_key", "", "resolution_rule", "");
            }
            cards.add(resultCard(name, res));
        }
        String where = (dataset != null && !dataset.trim().isEmpty()) ? escHtml(dataset.trim()) : "the loaded data";
        String note = "<p class=\"muted\">This is a saved snapshot, so live search is disabled. Below are "
            + "example searches run against " + where + ". In the live app "
            + "(<code>http://localhost:" + port + "</code>) you can search any name.</p>";
        String browse = "<div id=\"probe-btns\"></div>";
        if (cards.isEmpty()) return note + browse + "<p class=\"muted\">No multi-record entities to show.</p>";
        return note + browse + "<div id=\"results\">" + String.join("", cards) + "</div>";
    }

    static void writeSnapshot(Model model, SzEngine engine, Set<SzFlag> flags, String title, Path out,
                              int port, String dataset) throws IOException {
        Map<String, Object> payload = mapOf("stats", model.stats(), "graph", model.graph(GRAPH_NODE_CAP),
            "merges", model.merges(), "overlap", model.overlap(), "matchkeys", model.matchKeys(),
            "features", model.featureDist);
        String shim = "<script>const __DATA__=" + MiniJson.writeForScript(payload) + ";"
            + "var __RECS__=null;"
            + "function __recordsFor(id){if(!__RECS__){__RECS__={};"
            + "(((__DATA__.graph||{}).nodes)||[]).forEach(function(n){"
            + "__RECS__[String(n.entity_id)]={entity_id:n.entity_id,entity_name:n.entity_name,"
            + "records:n.records||[]};});}"
            + "return __RECS__[id]||{entity_id:id,error:'not found: no such entity'};}"
            + "window.fetch=function(u){var p=u.split('?')[0].replace('/api/','');"
            + "var q=(u.split('?')[1]||'');"
            + "if(p==='search'){return Promise.resolve({json:function(){return Promise.resolve({results:[]});}});}"
            + "if(p==='why'||p==='how'){return Promise.resolve({json:function(){return Promise.resolve({error:'Why/How explanations run only against the live visualization server (start it with: src/scripts/run-java.sh data/temp/server_classes TruthsetVizServer " + (exportMode ? "--from-export" : "--records ...") + ").'});}});}"
            + "if(p==='records'){var m=q.match(/entity_id=([^&]*)/);"
            + "var r=__recordsFor(m?decodeURIComponent(m[1]):'');"
            + "return Promise.resolve({json:function(){return Promise.resolve(r);}});}"
            + "return Promise.resolve({json:function(){return Promise.resolve(__DATA__[p]);}});};</script>";
        String page = renderPage(title, shim, snapshotProbeHtml(model, engine, flags, port, dataset), model.colorKeys());
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, page, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- settings
    /** Missing REQUIRED_PIPELINE_KEYS, or null when not usable JSON. */
    static List<String> pipelineGaps(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        Object doc;
        try { doc = MiniJson.parse(raw); } catch (Exception e) { return null; }
        if (!(doc instanceof Map)) return null;
        Object p = MiniJson.obj(doc).get("PIPELINE");
        if (!(p instanceof Map)) return new ArrayList<>(REQUIRED_PIPELINE_KEYS);
        List<String> gaps = new ArrayList<>();
        for (String k : REQUIRED_PIPELINE_KEYS) {
            Object v = MiniJson.obj(p).get(k);
            if (v == null || String.valueOf(v).trim().isEmpty()) gaps.add(k);
        }
        return gaps;
    }

    /** Content-aware settings choice (INV-210): returns the settings or exits with the real cause. */
    static String resolveSettings(String path, String env) {
        String fileRaw = "";
        try { if (Files.exists(Paths.get(path))) fileRaw = Files.readString(Paths.get(path)); }
        catch (IOException e) { System.err.println("settings: cannot read " + path + " (" + e.getMessage() + ")"); }
        env = env == null ? "" : env;
        List<String> fileGaps = pipelineGaps(fileRaw), envGaps = pipelineGaps(env);
        if (fileGaps != null && fileGaps.isEmpty()) {
            if (envGaps != null && envGaps.isEmpty() && !fileRaw.trim().equals(env.trim()))
                System.err.println("settings: using " + path + "; $SENZING_ENGINE_CONFIGURATION_JSON is also set and differs — the file wins.");
            return fileRaw;
        }
        if (envGaps != null && envGaps.isEmpty()) {
            if (!fileRaw.trim().isEmpty())
                System.err.println("settings: " + path + " is incomplete; using $SENZING_ENGINE_CONFIGURATION_JSON instead.");
            return env;
        }
        if (fileGaps != null && !fileGaps.isEmpty())
            die(2, "Engine settings in " + path + " are incomplete: PIPELINE is missing " + String.join(", ", fileGaps)
                + ". Proceeding would fail with SENZ7426, which points at SUPPORTPATH rather than at this file.");
        if (envGaps != null && !envGaps.isEmpty())
            die(2, "Engine settings in $SENZING_ENGINE_CONFIGURATION_JSON are incomplete: PIPELINE is missing "
                + String.join(", ", envGaps) + ".");
        die(2, "No usable engine settings (missing or unparseable " + path + " and env var).");
        return null;
    }

    static void die(int code, String msg) {
        System.err.println("ERROR: " + msg);
        System.exit(code);
    }

    static List<String[]> recordKeys(List<String> files) throws IOException {
        LinkedHashSet<List<String>> seen = new LinkedHashSet<>();
        for (String f : files) {
            for (String line : Files.readAllLines(Paths.get(f), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    Map<String, Object> d = MiniJson.obj(MiniJson.parse(line));
                    if (d.get("DATA_SOURCE") == null || d.get("RECORD_ID") == null) continue;
                    seen.add(List.of(String.valueOf(d.get("DATA_SOURCE")), String.valueOf(d.get("RECORD_ID"))));
                } catch (Exception ignore) { /* unparseable line is skipped */ }
            }
        }
        List<String[]> out = new ArrayList<>();
        for (List<String> k : seen) out.add(new String[]{k.get(0), k.get(1)});
        return out;
    }

    // ---------------------------------------------------------------- HTTP
    static Map<String, String> query(URI uri) {
        Map<String, String> q = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) return q;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            q.putIfAbsent(k, v);
        }
        return q;
    }

    static void send(HttpExchange x, int code, String body, String ctype) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", ctype);
        x.sendResponseHeaders(code, data.length);
        try (OutputStream os = x.getResponseBody()) { os.write(data); }
    }

    static boolean confirmServerIdentity(int port) {
        String url = "http://" + BIND_HOST + ":" + port + "/api/stats";
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            try (InputStream in = c.getInputStream()) {
                Object answered = MiniJson.obj(MiniJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))).get("server_nonce");
                if (SERVER_NONCE.equals(answered)) return true;
                System.err.println("ERROR: port " + port + " is answered by a DIFFERENT server (nonce " + answered
                    + "). Stop the other server, or use --port for a free port.");
                return false;
            }
        } catch (Exception exc) {
            System.err.println("ERROR: could not confirm which server is answering " + url + " (" + exc + ").");
            return false;
        }
    }

    // ---------------------------------------------------------------- main
    public static void main(String[] args) throws Exception {
        String settingsPath = "config/engine_config.json", title = "Senzing Entity Resolution", dataset = "";
        String snapshot = null;
        List<String> records = new ArrayList<>();
        int port = 8080;
        boolean noServe = false;
        for (int a = 0; a < args.length; a++) {
            switch (args[a]) {
                case "--settings": settingsPath = args[++a]; break;
                case "--records": while (a + 1 < args.length && !args[a + 1].startsWith("--")) records.add(args[++a]); break;
                case "--port": port = Integer.parseInt(args[++a]); break;
                case "--title": title = args[++a]; break;
                case "--dataset": dataset = args[++a]; break;
                case "--snapshot": snapshot = args[++a]; break;
                case "--no-serve": noServe = true; break;
                case "--from-export": exportMode = true; break;
                case "--assets": assetsDir = Paths.get(args[++a]); break;
                default: die(64, "unknown argument " + args[a]);
            }
        }
        // (INV-091) Refuse to render without the vendored D3, before any other work.
        Path d3 = assetsDir.resolve("vendor/d3.v7.min.js");
        if (!Files.isReadable(d3)) {
            die(1, "the vendored D3 asset is missing or unreadable: " + d3
                + ". Refusing to render without it (no CDN fallback); run src/server/generate_assets.py.");
        }
        brandDoc = MiniJson.obj(MiniJson.parse(Files.readString(assetsDir.resolve("brand.json"))));
        if (records.isEmpty() && !exportMode) die(64, "give --from-export (your loaded data) or --records <files> (the Truth Set per-record build)");

        String settings = resolveSettings(settingsPath, System.getenv("SENZING_ENGINE_CONFIGURATION_JSON"));
        // The environment is held for the whole process: it must outlive every engine it creates.
        SzEnvironment env = SzCoreEnvironment.newBuilder().settings(settings)
            .instanceName("TruthsetVizServer").verboseLogging(false).build();
        final SzEngine engine = env.getEngine();
        final Set<SzFlag> flags = SZ_ENTITY_DEFAULT_FLAGS;
        final Model model;
        try {
            model = exportMode ? new Model().buildFromExport(engine) : new Model().build(engine, flags, recordKeys(records));
            try { model.computeFeatureDist(engine, 40); }
            catch (Exception exc) { System.err.println("feature-score distribution unavailable (non-fatal): " + exc); }
        } catch (Exception exc) {
            env.destroy();
            die(1, "Could not build entity model: " + exc);
            return;
        }
        Map<String, Object> s = model.stats();
        System.out.println("Entity model built: " + s.get("records_total") + " records, " + s.get("entities_total")
            + " entities, " + s.get("multi_record_entities") + " merged, " + s.get("cross_source_entities")
            + " cross-source, " + s.get("relationships_total") + " relationships");
        System.out.println("Color allocation check: " + MiniJson.write(colorForSources(
            List.of("CUSTOMERS", "REFERENCE", "WATCHLIST", "CUSTOMERS|WATCHLIST", "ZETA"))));

        if (snapshot != null) {
            writeSnapshot(model, engine, flags, title, Paths.get(snapshot), port, dataset);
            System.out.println("Snapshot written: " + snapshot);
        }
        if (noServe) {
            env.destroy();
            return;
        }

        final String pageTitle = title;
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(BIND_HOST, port), 0);
        } catch (IOException exc) {
            env.destroy();
            die(1, "could not bind " + BIND_HOST + ":" + port + " (" + exc.getMessage()
                + "). Another server holds that port; use --port for a free one.");
            return;
        }
        server.createContext("/", x -> {
            String path = x.getRequestURI().getPath();
            Map<String, String> q = query(x.getRequestURI());
            try {
                switch (path) {
                    case "/": case "/index.html":
                        send(x, 200, renderPage(pageTitle, "", null, model.colorKeys()), "text/html; charset=utf-8"); return;
                    case "/api/stats": send(x, 200, MiniJson.write(model.stats()), "application/json"); return;
                    case "/api/graph": send(x, 200, MiniJson.write(model.graph(GRAPH_NODE_CAP)), "application/json"); return;
                    case "/api/merges": send(x, 200, MiniJson.write(model.merges()), "application/json"); return;
                    case "/api/overlap": send(x, 200, MiniJson.write(model.overlap()), "application/json"); return;
                    case "/api/records": send(x, 200, MiniJson.write(model.records(q.getOrDefault("entity_id", ""))), "application/json"); return;
                    case "/api/matchkeys": send(x, 200, MiniJson.write(model.matchKeys()), "application/json"); return;
                    case "/api/features": send(x, 200, MiniJson.write(model.featureDist), "application/json"); return;
                    case "/api/search": send(x, 200, MiniJson.write(model.search(engine, flags, q.getOrDefault("q", ""))), "application/json"); return;
                    case "/api/why": send(x, 200, MiniJson.write(model.why(engine, q.getOrDefault("entity_id", ""))), "application/json"); return;
                    case "/api/how": send(x, 200, MiniJson.write(model.how(engine, q.getOrDefault("entity_id", ""))), "application/json"); return;
                    default: send(x, 404, MiniJson.write(mapOf("error", "not found")), "application/json");
                }
            } catch (Exception exc) {
                send(x, 500, MiniJson.write(mapOf("error", String.valueOf(exc))), "application/json");
            }
        });
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        if (!confirmServerIdentity(port)) {
            server.stop(0);
            env.destroy();
            System.exit(1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            env.destroy();
        }));
        System.out.println("Visualization running: http://localhost:" + port);
    }

    // ---------------------------------------------------------------- helpers
    static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.isEmpty()) return v;
        return "";
    }

    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int k = 0; k < kv.length; k += 2) m.put((String) kv[k], kv[k + 1]);
        return m;
    }
}
