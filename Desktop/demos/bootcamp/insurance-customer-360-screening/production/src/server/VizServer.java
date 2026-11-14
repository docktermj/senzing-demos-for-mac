package server;

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

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static com.senzing.sdk.SzFlag.*;

/**
 * Senzing entity-resolution visualization web app, written in Java.
 *
 * A port of the bootcamp's reference server (scripts/senzing_viz_server.py) to the
 * bootcamper's chosen language, implementing the contract in
 * skills/module-03b-truthset-visualization/visualization-api-reference.md.
 *
 * It builds an entity model from the loaded records by looking each one up through the
 * Senzing SDK (one getEntity(SzRecordKey, SZ_ENTITY_DEFAULT_FLAGS) per record - the Truth
 * Set path; a bootcamper's own datastore needs the export stream instead), then serves:
 *   GET /                 single-page D3 v7 app (six tabs + summary banner)
 *   GET /api/stats        aggregate statistics (+ bucket_entities, sample_entities, server_nonce)
 *   GET /api/graph        nodes + edges (+ total, capped, related_total, encoding_check)
 *   GET /api/merges       multi-record entities
 *   GET /api/records      ?entity_id=  constituent records of one entity
 *   GET /api/search       ?q=  search-by-attributes, NAME_FULL then NAME_ORG
 *   GET /api/why          ?entity_id=  whyRecords / whyRecordInEntity
 *   GET /api/how          ?entity_id=  howEntity
 *   GET /api/overlap      cross-source overlap matrix (+ cell_entities)
 *   GET /api/matchkeys    match-key frequency (+ match_key_entities)
 *   GET /api/features     feature-score distribution over a capped why sample
 * With --business (this project's three tabs, see BusinessTabs.java):
 *   GET /api/overview, /api/customers, /api/alerts, /api/whypair?a=&b=;  POST /api/disposition
 * With --export the model is built from the export stream (every resolved entity) instead of
 * one getEntity per record, which is the Truth Set path.
 *
 * Senzing calls and flags come from the Senzing MCP server (get_sdk_reference, Java binding):
 *   SzEngine.getEntity(SzRecordKey, Set<SzFlag>), searchByAttributes(String, Set<SzFlag>),
 *   whyRecords(SzRecordKey, SzRecordKey, Set<SzFlag>), whyRecordInEntity(SzRecordKey, Set<SzFlag>),
 *   howEntity(long, Set<SzFlag>); flags SZ_ENTITY_DEFAULT_FLAGS, SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS,
 *   SZ_WHY_RECORDS_DEFAULT_FLAGS, SZ_WHY_RECORD_IN_ENTITY_DEFAULT_FLAGS, SZ_HOW_ENTITY_DEFAULT_FLAGS.
 * Response fields read: RESOLVED_ENTITY.{ENTITY_ID,ENTITY_NAME,RECORDS[].{DATA_SOURCE,RECORD_ID,MATCH_KEY}},
 *   RELATED_ENTITIES[].{ENTITY_ID,MATCH_KEY,MATCH_LEVEL_CODE,ERRULE_CODE},
 *   RESOLVED_ENTITIES[].{ENTITY.RESOLVED_ENTITY.{ENTITY_ID,ENTITY_NAME},MATCH_INFO.{MATCH_KEY,ERRULE_CODE}},
 *   WHY_RESULTS[].MATCH_INFO.FEATURE_SCORES.*[].SCORE_BUCKET (get_sdk_reference response_schemas).
 * No SQL is ever run against the database.
 *
 * The page (HTML/CSS/D3) is generated from the reference by src/scripts/extract_viz_page.py
 * into src/server/resources/page.html, with D3 vendored beside it (offline, no CDN).
 *
 * Usage (always through the launcher, which sources the env and calls the JDK's own java):
 *   src/scripts/sz-java.sh run server.VizServer (--records <file.jsonl> | --export) [--business] [--title T] [--dataset D]
 *       [--snapshot out.html] [--no-serve] [--port 8080] [--settings config/engine_config.json]
 */
public final class VizServer {

    /** Loopback only, never the wildcard: a wildcard bind coexists with a loopback listener. */
    static final String BIND_HOST = "127.0.0.1";
    static final String SOURCE_KEY_SEP = "|";
    static final int GRAPH_NODE_CAP = 1500;
    static final String SERVER_NONCE = UUID.randomUUID().toString().replace("-", "");
    static final String[] SEARCH_NAME_ATTRS = {"NAME_FULL", "NAME_ORG"};
    static final String[] REQUIRED_PIPELINE_KEYS = {"CONFIGPATH", "RESOURCEPATH", "SUPPORTPATH"};

    // ---- Senzing brand tokens (values replicated from scripts/brand_tokens.py) -----------
    static final Map<String, String> SOURCE_COLORS = new LinkedHashMap<>();
    static {
        SOURCE_COLORS.put("CUSTOMERS", "#F57826");
        SOURCE_COLORS.put("REFERENCE", "#3B6EA5");
        SOURCE_COLORS.put("WATCHLIST", "#C8922A");
    }
    static final String[] FALLBACK_COLORS = {"#8b5cf6", "#ec4899", "#0ea5e9", "#a3a34a", "#ef4444", "#14b8a6"};
    static final String[] SOURCE_STROKES = {"#FFFFFF", "#18160F", "#FAF8F3"};
    static final double[] SOURCE_STROKE_WIDTHS = {1.5, 3.0};
    static final double[] SOURCE_FILL_SHADES = {0.0, 0.30, -0.30, 0.55, -0.55};
    static final Map<String, String> BRAND = new LinkedHashMap<>();
    static {
        BRAND.put("bg", "#FAF8F3");
        BRAND.put("surface", "#FFFFFF");
        BRAND.put("dark", "#18160F");
        BRAND.put("ink", "#18160F");
        BRAND.put("muted", "#4A4640");
        BRAND.put("accent", "#F57826");
        BRAND.put("accent_hot", "#FF4E1F");
        BRAND.put("accent_soft", "#FDEEE3");
        BRAND.put("line", "#E5DFD3");
        BRAND.put("green", "#1D9E75");
        BRAND.put("font", "Roboto, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif");
        BRAND.put("code_font", "'Fira Code', 'Courier New', Courier, monospace");
    }

    /** Colors assigned from the sources actually present (never a name-keyed lookup alone). */
    static Map<String, Object> colorForSources(Collection<String> sources) {
        int states = 1 + SOURCE_STROKES.length * SOURCE_STROKE_WIDTHS.length;
        TreeSet<String> codes = new TreeSet<>();
        for (String s : sources) if (s != null && !s.trim().isEmpty()) codes.add(s);
        Map<String, String> preferred = new LinkedHashMap<>();
        for (String c : codes) if (SOURCE_COLORS.containsKey(c)) preferred.put(c, SOURCE_COLORS.get(c));
        Set<String> claimed = new HashSet<>(preferred.values());
        List<String> available = new ArrayList<>();
        for (String c : FALLBACK_COLORS) if (!claimed.contains(c)) available.add(c);
        if (available.isEmpty()) available.addAll(Arrays.asList(FALLBACK_COLORS));

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
                fill = shade(fill, SOURCE_FILL_SHADES[(cycle / states) % SOURCE_FILL_SHADES.length]);
                nth++;
            }
            int slot = cycle % states;
            String stroke;
            Double width;
            if (slot == 0) {
                stroke = SOURCE_STROKES[0];
                width = null;
            } else {
                int k = slot - 1;
                stroke = SOURCE_STROKES[(k + 1) % SOURCE_STROKES.length];
                width = SOURCE_STROKE_WIDTHS[k / SOURCE_STROKES.length];
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("fill", fill);
            v.put("stroke", stroke);
            v.put("stroke_width", width);
            v.put("cycle", (long) cycle);
            assigned.put(code, v);
        }
        return assigned;
    }

    static String shade(String fill, double factor) {
        if (factor == 0.0) return fill;
        String target = factor > 0 ? "#FFFFFF" : "#18160F";
        double w = Math.abs(factor);
        int[] a = rgb(fill), b = rgb(target);
        return String.format("#%02X%02X%02X",
            Math.round(a[0] + (b[0] - a[0]) * w),
            Math.round(a[1] + (b[1] - a[1]) * w),
            Math.round(a[2] + (b[2] - a[2]) * w));
    }

    static int[] rgb(String v) {
        return new int[] {Integer.parseInt(v.substring(1, 3), 16),
            Integer.parseInt(v.substring(3, 5), 16), Integer.parseInt(v.substring(5, 7), 16)};
    }

    // =====================================================================================
    // Entity model
    // =====================================================================================
    static final class Model {
        long recordsTotal = 0;
        final Map<Long, Map<String, Object>> entities = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> edges = new LinkedHashMap<>(); // "a,b" -> meta
        final Map<String, long[]> edgeEnds = new LinkedHashMap<>();
        Map<String, Object> featureDist = emptyFeatureDist();

        static Map<String, Object> emptyFeatureDist() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("features", new ArrayList<>());
            m.put("sampled", 0L);
            m.put("multi_record_total", 0L);
            m.put("capped", false);
            return m;
        }

        Model build(SzEngine engine, Set<SzFlag> flags, List<String[]> keys) {
            for (String[] k : keys) {
                recordsTotal++;
                try {
                    absorb(Json.parseObject(engine.getEntity(SzRecordKey.of(k[0], k[1]), flags)));
                } catch (Exception e) {
                    // a record that cannot be looked up is skipped, as in the reference
                }
            }
            return this;
        }

        /**
         * Build from the export stream (INV-289): every resolved entity, one pass, no per-record
         * round trips. Java signature exportJsonEntityReport(Set<SzFlag>) -> long, fetchNext(long),
         * closeExportReport(long) (get_sdk_reference, java). Flags SZ_EXPORT_INCLUDE_ALL_ENTITIES plus
         * SZ_ENTITY_DEFAULT_FLAGS -- the composite, never hand-assembled SZ_ENTITY_INCLUDE_* members,
         * which can drop RELATED_ENTITIES without an error. Each row has the getEntity shape.
         */
        Model buildFromExport(SzEngine engine) throws SzException {
            Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
            flags.addAll(SZ_EXPORT_INCLUDE_ALL_ENTITIES);
            flags.addAll(SZ_ENTITY_DEFAULT_FLAGS);
            long handle = engine.exportJsonEntityReport(flags);
            try {
                for (String row = engine.fetchNext(handle); row != null; row = engine.fetchNext(handle)) {
                    Map<String, Object> r = Json.parseObject(row);
                    recordsTotal += Json.arr(Json.obj(r.get("RESOLVED_ENTITY")).get("RECORDS")).size();
                    absorb(r);
                }
            } finally {
                engine.closeExportReport(handle);
            }
            return this;
        }

        void absorb(Map<String, Object> resp) {
            Map<String, Object> re = Json.obj(resp.get("RESOLVED_ENTITY"));
            Object idObj = re.get("ENTITY_ID");
            if (!(idObj instanceof Number)) return;
            long eid = ((Number) idObj).longValue();
            if (!entities.containsKey(eid)) {
                List<Object> recs = Json.arr(re.get("RECORDS"));
                TreeSet<String> sources = new TreeSet<>();
                List<Object> records = new ArrayList<>();
                for (Object o : recs) {
                    Map<String, Object> r = Json.obj(o);
                    String ds = r.get("DATA_SOURCE") == null ? "?" : r.get("DATA_SOURCE").toString();
                    sources.add(ds);
                    Map<String, Object> rr = new LinkedHashMap<>();
                    rr.put("data_source", ds);
                    rr.put("record_id", r.get("RECORD_ID") == null ? "?" : r.get("RECORD_ID").toString());
                    Object mk = r.get("MATCH_KEY");
                    rr.put("match_key", mk == null ? "" : mk.toString());
                    records.add(rr);
                }
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("entity_id", eid);
                Object name = re.get("ENTITY_NAME");
                e.put("entity_name", (name == null || name.toString().isEmpty()) ? "Entity " + eid : name.toString());
                e.put("record_count", (long) recs.size());
                e.put("data_sources", new ArrayList<>(sources));
                e.put("records", records);
                entities.put(eid, e);
            }
            for (Object o : Json.arr(resp.get("RELATED_ENTITIES"))) {
                Map<String, Object> rel = Json.obj(o);
                Object tidObj = rel.get("ENTITY_ID");
                if (!(tidObj instanceof Number)) continue;
                long tid = ((Number) tidObj).longValue();
                long a = Math.min(eid, tid), b = Math.max(eid, tid);
                String key = a + "," + b;
                if (!edges.containsKey(key)) {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("match_key", rel.get("MATCH_KEY") == null ? "" : rel.get("MATCH_KEY").toString());
                    String rt = firstNonEmpty(rel.get("MATCH_LEVEL_CODE"), rel.get("ERRULE_CODE"));
                    meta.put("relationship_type", rt == null ? "RELATED" : rt);
                    edges.put(key, meta);
                    edgeEnds.put(key, new long[] {a, b});
                }
            }
        }

        static String firstNonEmpty(Object... vals) {
            for (Object v : vals) if (v != null && !v.toString().isEmpty()) return v.toString();
            return null;
        }

        static long rc(Map<String, Object> e) { return ((Number) e.get("record_count")).longValue(); }

        @SuppressWarnings("unchecked")
        static List<String> ds(Map<String, Object> e) { return (List<String>) e.get("data_sources"); }

        @SuppressWarnings("unchecked")
        static List<Map<String, Object>> recs(Map<String, Object> e) {
            return (List<Map<String, Object>>) (List<?>) e.get("records");
        }

        List<String> dataSources() {
            TreeSet<String> codes = new TreeSet<>();
            for (Map<String, Object> e : entities.values())
                for (String s : ds(e)) if (s != null && !s.trim().isEmpty()) codes.add(s);
            return new ArrayList<>(codes);
        }

        static Map<String, Object> brief(Map<String, Object> e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entity_id", e.get("entity_id"));
            m.put("entity_name", e.get("entity_name"));
            m.put("record_count", e.get("record_count"));
            return m;
        }

        Map<String, Object> stats() {
            Map<String, Object> hist = new LinkedHashMap<>();
            Map<String, Object> buckets = new LinkedHashMap<>();
            for (String b : new String[] {"1", "2", "3", "4+"}) {
                hist.put(b, 0L);
                buckets.put(b, new ArrayList<>());
            }
            Set<String> all = new HashSet<>();
            long multi = 0, cross = 0;
            for (Map<String, Object> e : entities.values()) {
                all.addAll(ds(e));
                long c = rc(e);
                String b = c >= 4 ? "4+" : String.valueOf(c);
                hist.put(b, ((Long) hist.getOrDefault(b, 0L)) + 1);
                List<Object> lst = Json.arr(buckets.get(b));
                if (lst.size() < 200) lst.add(brief(e));
                if (c > 1) multi++;
                if (ds(e).size() >= 2) cross++;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("records_total", recordsTotal);
            m.put("entities_total", (long) entities.size());
            m.put("multi_record_entities", multi);
            m.put("cross_source_entities", cross);
            m.put("relationships_total", (long) edges.size());
            m.put("data_sources_total", (long) all.size());
            m.put("histogram", hist);
            m.put("bucket_entities", buckets);
            m.put("sample_entities", sampleEntities(10));
            m.put("server_nonce", SERVER_NONCE);
            return m;
        }

        List<Object> sampleEntities(int cap) {
            List<Map<String, Object>> sorted = new ArrayList<>(entities.values());
            sorted.sort((x, y) -> Long.compare(rc(y), rc(x)));
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
            Map<String, Object> m = new LinkedHashMap<>();
            long eid;
            try {
                eid = Long.parseLong(entityId.trim());
            } catch (Exception ex) {
                m.put("entity_id", entityId);
                m.put("error", "bad request: entity_id must be an integer");
                return m;
            }
            Map<String, Object> e = entities.get(eid);
            m.put("entity_id", eid);
            if (e == null) {
                m.put("error", "not found: no such entity");
                return m;
            }
            m.put("entity_name", e.get("entity_name"));
            m.put("records", e.get("records"));
            return m;
        }

        Map<String, Object> overlap() {
            List<String> sources = dataSourcesAll();
            Map<String, Integer> idx = new HashMap<>();
            for (int k = 0; k < sources.size(); k++) idx.put(sources.get(k), k);
            int n = sources.size();
            long[][] matrix = new long[n][n];
            for (Map<String, Object> e : entities.values()) {
                List<String> d = new ArrayList<>(new TreeSet<>(ds(e)));
                for (String s : d) matrix[idx.get(s)][idx.get(s)]++;
                for (int a = 0; a < d.size(); a++)
                    for (int b = a + 1; b < d.size(); b++) {
                        int x = idx.get(d.get(a)), y = idx.get(d.get(b));
                        matrix[x][y]++;
                        matrix[y][x]++;
                    }
            }
            Map<String, Object> cells = new LinkedHashMap<>();
            boolean capped = false;
            for (Map<String, Object> e : entities.values()) {
                List<String> d = new ArrayList<>(new TreeSet<>(ds(e)));
                List<int[]> keys = new ArrayList<>();
                for (String s : d) keys.add(new int[] {idx.get(s), idx.get(s)});
                for (int a = 0; a < d.size(); a++)
                    for (int b = a + 1; b < d.size(); b++) {
                        int x = idx.get(d.get(a)), y = idx.get(d.get(b));
                        keys.add(new int[] {Math.min(x, y), Math.max(x, y)});
                    }
                for (int[] k : keys) {
                    String ck = k[0] + "," + k[1];
                    List<Object> lst = Json.arr(cells.computeIfAbsent(ck, z -> new ArrayList<>()));
                    if (lst.size() < 200) lst.add(brief(e));
                    else capped = true;
                }
            }
            List<Object> mat = new ArrayList<>();
            for (long[] row : matrix) {
                List<Object> r = new ArrayList<>();
                for (long v : row) r.add(v);
                mat.add(r);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sources", sources);
            m.put("matrix", mat);
            m.put("cell_entities", cells);
            m.put("cell_capped", capped);
            return m;
        }

        List<String> dataSourcesAll() {
            TreeSet<String> s = new TreeSet<>();
            for (Map<String, Object> e : entities.values()) s.addAll(ds(e));
            return new ArrayList<>(s);
        }

        Map<String, Object> matchKeys() {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (Map<String, Object> e : entities.values())
                for (Map<String, Object> r : recs(e)) {
                    String mk = Json.str(r.get("match_key"));
                    if (mk != null && !mk.isEmpty()) counts.merge(mk, 1L, Long::sum);
                }
            List<Map.Entry<String, Long>> items = new ArrayList<>(counts.entrySet());
            items.sort((x, y) -> {
                int c = Long.compare(y.getValue(), x.getValue());
                return c != 0 ? c : x.getKey().compareTo(y.getKey());
            });
            List<String> top = new ArrayList<>();
            for (int k = 0; k < Math.min(20, items.size()); k++) top.add(items.get(k).getKey());
            Map<String, Object> byKey = new LinkedHashMap<>();
            for (String k : top) byKey.put(k, new ArrayList<>());
            boolean entCapped = false;
            Set<String> wanted = new HashSet<>(top);
            for (Map<String, Object> e : entities.values()) {
                Set<String> seen = new HashSet<>();
                for (Map<String, Object> r : recs(e)) {
                    String mk = Json.str(r.get("match_key"));
                    if (mk == null) mk = "";
                    if (wanted.contains(mk) && seen.add(mk)) {
                        List<Object> lst = Json.arr(byKey.get(mk));
                        if (lst.size() < 200) lst.add(brief(e));
                        else entCapped = true;
                    }
                }
            }
            List<Object> mks = new ArrayList<>();
            for (int k = 0; k < Math.min(20, items.size()); k++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("match_key", items.get(k).getKey());
                m.put("count", items.get(k).getValue());
                mks.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("match_keys", mks);
            m.put("distinct", (long) counts.size());
            m.put("capped", counts.size() > 20);
            m.put("match_key_entities", byKey);
            m.put("entities_capped", entCapped);
            return m;
        }

        /** Every key the graph colors by - each source plus each combination - in ONE list. */
        List<String> colorKeys() {
            TreeSet<String> singles = new TreeSet<>(dataSources());
            TreeSet<String> combos = new TreeSet<>();
            for (Map<String, Object> e : entities.values()) {
                List<String> s = new ArrayList<>(new TreeSet<>(ds(e)));
                if (s.size() >= 2) combos.add(String.join(SOURCE_KEY_SEP, s));
            }
            List<String> out = new ArrayList<>(singles);
            out.addAll(combos);
            return out;
        }

        Map<String, Object> graph(Integer cap) {
            List<Map<String, Object>> nodes = new ArrayList<>(entities.values());
            Set<Long> nodeIds = new HashSet<>(entities.keySet());
            long total = nodes.size();
            Set<Long> ends = new HashSet<>();
            for (long[] ab : edgeEnds.values()) { ends.add(ab[0]); ends.add(ab[1]); }
            boolean capped = false;
            if (cap != null && total > cap) {
                Map<Long, Integer> degree = new HashMap<>();
                for (long[] ab : edgeEnds.values()) {
                    degree.merge(ab[0], 1, Integer::sum);
                    degree.merge(ab[1], 1, Integer::sum);
                }
                nodes.sort((x, y) -> {
                    int c = Integer.compare(new HashSet<>(ds(y)).size(), new HashSet<>(ds(x)).size());
                    if (c != 0) return c;
                    long ix = ((Number) x.get("entity_id")).longValue(), iy = ((Number) y.get("entity_id")).longValue();
                    c = Integer.compare(degree.getOrDefault(iy, 0), degree.getOrDefault(ix, 0));
                    if (c != 0) return c;
                    c = Long.compare(rc(y), rc(x));
                    return c != 0 ? c : Long.compare(ix, iy);
                });
                nodes = new ArrayList<>(nodes.subList(0, cap));
                nodeIds = new HashSet<>();
                for (Map<String, Object> e : nodes) nodeIds.add(((Number) e.get("entity_id")).longValue());
                capped = true;
            }
            List<Object> edgeList = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> en : edges.entrySet()) {
                long[] ab = edgeEnds.get(en.getKey());
                if (nodeIds.contains(ab[0]) && nodeIds.contains(ab[1])) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("source_entity_id", ab[0]);
                    m.put("target_entity_id", ab[1]);
                    m.put("match_key", en.getValue().get("match_key"));
                    m.put("relationship_type", en.getValue().get("relationship_type"));
                    edgeList.add(m);
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodes", nodes);
            m.put("edges", edgeList);
            m.put("total", total);
            m.put("capped", capped);
            m.put("related_total", (long) ends.size());
            m.put("encoding_check", encodingCheck(nodes));
            return m;
        }

        static Map<String, Object> encodingCheck(List<Map<String, Object>> nodes) {
            TreeSet<String> keys = new TreeSet<>();
            for (Map<String, Object> e : nodes) {
                List<String> s = new ArrayList<>(new TreeSet<>(ds(e)));
                if (!s.isEmpty()) keys.add(String.join(SOURCE_KEY_SEP, s));
            }
            List<String> combos = new ArrayList<>();
            for (String k : keys) if (k.contains(SOURCE_KEY_SEP)) combos.add(k);
            String status = combos.isEmpty() ? "not_exercised" : "ok";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("distinct_source_set_keys", (long) keys.size());
            m.put("source_set_keys", new ArrayList<>(keys));
            m.put("combination_keys", combos);
            m.put("status", status);
            m.put("detail", "ok".equals(status)
                ? "Compare the number of combination rows the source legend names against "
                  + "len(combination_keys); they MUST be equal (INV-270, INV-259). Fewer "
                  + "combination rows means nodes are colored by one member of their source "
                  + "set. Per-source rows are not source-set keys and are not counted."
                : "No combination key is present, so this check cannot fail and has NOT "
                  + "been exercised (INV-265). It is not a pass.");
            return m;
        }

        Map<String, Object> merges() {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> e : entities.values()) if (rc(e) > 1) out.add(e);
            out.sort((x, y) -> Long.compare(rc(y), rc(x)));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entities", out);
            return m;
        }

        Map<String, Object> search(SzEngine engine, String query) {
            query = query == null ? "" : query.trim();
            Map<String, Object> out = new LinkedHashMap<>();
            if (query.isEmpty()) {
                out.put("results", new ArrayList<>());
                return out;
            }
            List<Object> items = new ArrayList<>();
            List<Object> tried = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (String attr : SEARCH_NAME_ATTRS) {
                tried.add(attr);
                try {
                    Map<String, Object> q = new LinkedHashMap<>();
                    q.put(attr, query);
                    Map<String, Object> resp = Json.parseObject(
                        engine.searchByAttributes(Json.write(q), SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS));
                    items.addAll(Json.arr(resp.get("RESOLVED_ENTITIES")));
                } catch (Exception ex) {
                    failures.add(attr + ": " + ex.getMessage());
                    continue;
                }
                if (!items.isEmpty()) break;
            }
            List<Object> results = new ArrayList<>();
            for (Object o : items.subList(0, Math.min(10, items.size()))) {
                Map<String, Object> item = Json.obj(o);
                Map<String, Object> ent = Json.obj(Json.obj(item.get("ENTITY")).get("RESOLVED_ENTITY"));
                Object idObj = ent.get("ENTITY_ID");
                Long eid = idObj instanceof Number ? ((Number) idObj).longValue() : null;
                Map<String, Object> match = Json.obj(item.get("MATCH_INFO"));
                Map<String, Object> local = eid == null ? null : entities.get(eid);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("entity_id", eid);
                Object en = ent.get("ENTITY_NAME");
                r.put("entity_name", (en != null && !en.toString().isEmpty()) ? en.toString()
                    : (local != null ? local.get("entity_name") : "?"));
                r.put("record_count", local != null ? local.get("record_count") : null);
                r.put("data_sources", local != null ? local.get("data_sources") : new ArrayList<>());
                r.put("match_key", match.get("MATCH_KEY") == null ? "" : match.get("MATCH_KEY").toString());
                r.put("resolution_rule", match.get("ERRULE_CODE") == null ? "" : match.get("ERRULE_CODE").toString());
                results.add(r);
            }
            out.put("results", results);
            out.put("attributes_tried", tried);
            if (items.isEmpty() && !failures.isEmpty()) out.put("error", String.join("; ", failures));
            return out;
        }

        Map<String, Object> how(SzEngine engine, String entityId) {
            Map<String, Object> m = new LinkedHashMap<>();
            long eid;
            try {
                eid = Long.parseLong(entityId.trim());
            } catch (Exception ex) {
                m.put("error", "invalid entity_id");
                return m;
            }
            m.put("entity_id", eid);
            try {
                m.put("result", Json.parse(engine.howEntity(eid, SZ_HOW_ENTITY_DEFAULT_FLAGS)));
            } catch (Exception ex) {
                m.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            return m;
        }

        Map<String, Object> why(SzEngine engine, String entityId) {
            Map<String, Object> m = new LinkedHashMap<>();
            long eid;
            try {
                eid = Long.parseLong(entityId.trim());
            } catch (Exception ex) {
                m.put("error", "invalid entity_id");
                return m;
            }
            m.put("entity_id", eid);
            Map<String, Object> e = entities.get(eid);
            List<Map<String, Object>> rs = e == null ? new ArrayList<>() : recs(e);
            try {
                String raw;
                String mode;
                if (rs.size() >= 2) {
                    raw = engine.whyRecords(
                        SzRecordKey.of(rs.get(0).get("data_source").toString(), rs.get(0).get("record_id").toString()),
                        SzRecordKey.of(rs.get(1).get("data_source").toString(), rs.get(1).get("record_id").toString()),
                        SZ_WHY_RECORDS_DEFAULT_FLAGS);
                    mode = "why_records";
                } else if (rs.size() == 1) {
                    raw = engine.whyRecordInEntity(
                        SzRecordKey.of(rs.get(0).get("data_source").toString(), rs.get(0).get("record_id").toString()),
                        SZ_WHY_RECORD_IN_ENTITY_DEFAULT_FLAGS);
                    mode = "why_record_in_entity";
                } else {
                    m.put("error", "no records known for this entity");
                    return m;
                }
                m.put("mode", mode);
                m.put("result", Json.parse(raw));
            } catch (Exception ex) {
                m.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            return m;
        }

        void computeFeatureDist(SzEngine engine, int cap) {
            Map<String, Map<String, Long>> perFeature = new TreeMap<>();
            List<Map<String, Object>> multi = new ArrayList<>();
            for (Map<String, Object> e : entities.values()) if (rc(e) > 1) multi.add(e);
            multi.sort((x, y) -> Long.compare(rc(y), rc(x)));
            long sampled = 0;
            for (Map<String, Object> e : multi) {
                if (sampled >= cap) break;
                Map<String, Object> res = why(engine, e.get("entity_id").toString());
                if (res.containsKey("error") || !res.containsKey("result")) continue;
                boolean counted = false;
                for (Object w : Json.arr(Json.obj(res.get("result")).get("WHY_RESULTS"))) {
                    Map<String, Object> fs = Json.obj(Json.obj(Json.obj(w).get("MATCH_INFO")).get("FEATURE_SCORES"));
                    for (Map.Entry<String, Object> f : fs.entrySet()) {
                        for (Object sc : Json.arr(f.getValue())) {
                            Object bObj = Json.obj(sc).get("SCORE_BUCKET");
                            String bucket = bObj == null ? "" : bObj.toString().toUpperCase();
                            if (bucket.isEmpty()) continue;
                            perFeature.computeIfAbsent(f.getKey(), z -> new LinkedHashMap<>()).merge(bucket, 1L, Long::sum);
                            counted = true;
                        }
                    }
                }
                if (counted) sampled++;
            }
            List<Object> feats = new ArrayList<>();
            for (Map.Entry<String, Map<String, Long>> f : perFeature.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("feature", f.getKey());
                m.put("buckets", f.getValue());
                feats.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("features", feats);
            m.put("sampled", sampled);
            m.put("multi_record_total", (long) multi.size());
            m.put("capped", multi.size() > cap);
            featureDist = m;
        }
    }

    // =====================================================================================
    // Page rendering and snapshot
    // =====================================================================================
    static String escHtml(Object s) {
        return String.valueOf(s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** JSON safe inside an inline script: < > & escaped as \\uXXXX (INV-106). */
    static String scriptJson(Object o) {
        return Json.write(o).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    static final String PROBE_BODY_LIVE =
        "<div style=\"margin-bottom:10px\">"
        + "<input id=\"search-in\" placeholder=\"Search a name (e.g. Robert Smith)\"> "
        + "<button class=\"probe\" onclick=\"doSearch()\">Search</button></div>"
        + "<div id=\"probe-btns\"></div><div id=\"results\"></div>";

    final Path pageTemplate;
    final Path d3Path;
    BusinessTabs business;          // the three business tabs, when --business is given
    String businessFragment;

    VizServer(Path resources) {
        this.pageTemplate = resources.resolve("page.html");
        this.d3Path = resources.resolve("vendor").resolve("d3.v7.min.js");
    }

    String d3Script() throws IOException {
        // INV-091: no CDN fallback - a missing asset is an error, never a network URL.
        return "<script>" + Files.readString(d3Path, StandardCharsets.UTF_8) + "</script>";
    }

    String renderPage(String title, String dataShim, String probeBody, List<String> sources) throws IOException {
        String rootVars = "--navy:" + BRAND.get("dark") + ";--blue:" + BRAND.get("accent")
            + ";--gold:" + BRAND.get("accent_hot") + ";--ink:" + BRAND.get("ink")
            + ";--muted:" + BRAND.get("muted") + ";--line:" + BRAND.get("line")
            + ";--bg:" + BRAND.get("bg") + ";--accent-soft:" + BRAND.get("accent_soft")
            + ";--green:" + BRAND.get("green");
        String page = Files.readString(pageTemplate, StandardCharsets.UTF_8);
        page = page.replace("__TITLE__", escHtml(title))
            .replace("__PROBE_BODY__", probeBody != null ? probeBody : PROBE_BODY_LIVE)
            .replace("__ROOT_VARS__", rootVars)
            .replace("__FONT_STACK__", BRAND.get("font"))
            .replace("__CODE_FONT__", BRAND.get("code_font"))
            .replace("__ACCENT_HOT__", BRAND.get("accent_hot"))
            .replace("__ACCENT__", BRAND.get("accent"))
            .replace("__SRC_COLORS__", scriptJson(colorForSources(
                sources != null && !sources.isEmpty() ? sources : SOURCE_COLORS.keySet())))
            .replace("__DATA_SHIM__", dataShim)
            .replace("__D3_SCRIPT__", d3Script());
        return business == null ? page : BusinessTabs.inject(page, businessFragment);
    }

    static String matchKeyChips(String mk) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char ch : (mk == null ? "" : mk).toCharArray()) {
            if (ch == '+' || ch == '-') {
                if (cur.length() > 0) parts.add(cur.toString());
                cur = new StringBuilder().append(ch);
            } else {
                cur.append(ch);
            }
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
        List<String> dsl = res.get("data_sources") instanceof List ? (List<String>) res.get("data_sources") : new ArrayList<>();
        h.append("<div class=\"muted\">Entity ").append(escHtml(res.get("entity_id"))).append(" · ")
            .append(escHtml(rc != null ? rc : "?")).append(" record(s) · ")
            .append(escHtml(String.join(", ", dsl))).append("</div>");
        String mk = Json.str(res.get("match_key"));
        if (mk != null && !mk.isEmpty()) h.append("<div class=\"mk\">").append(matchKeyChips(mk)).append("</div>");
        String rr = Json.str(res.get("resolution_rule"));
        if (rr != null && !rr.isEmpty()) h.append("<div><code>").append(escHtml(rr)).append("</code></div>");
        h.append("</div>");
        return h.toString();
    }

    static String snapshotProbeHtml(Model model, SzEngine engine, int port, String dataset) {
        List<Map<String, Object>> merges = new ArrayList<>();
        for (Object o : Json.arr(model.merges().get("entities"))) merges.add(Json.obj(o));
        merges.sort((x, y) -> {
            boolean xs = Model.ds(x).size() < 2, ys = Model.ds(y).size() < 2;
            if (xs != ys) return xs ? 1 : -1;
            return Long.compare(Model.rc(y), Model.rc(x));
        });
        List<String> cards = new ArrayList<>();
        for (Map<String, Object> ent : merges) {
            if (cards.size() >= 5) break;
            String name = Json.str(ent.get("entity_name"));
            if (name == null || name.isEmpty()) continue;
            Map<String, Object> res = null;
            boolean searchable = true;
            try {
                List<Object> hits = Json.arr(model.search(engine, name).get("results"));
                for (Object h : hits) {
                    if (Objects.equals(Json.obj(h).get("entity_id"), ent.get("entity_id"))) {
                        res = Json.obj(h);
                        break;
                    }
                }
                if (res == null && !hits.isEmpty()) res = Json.obj(hits.get(0));
                if (res == null) {
                    searchable = false;
                    System.err.println("dropped snapshot example '" + name + "': search returned no match "
                        + "(the example is not verified, so it is not offered)");
                }
            } catch (Exception ex) {
                System.err.println("snapshot probe search failed for '" + name + "' (non-fatal): " + ex);
                res = null;
            }
            if (!searchable) continue;
            if (res == null) {
                res = new LinkedHashMap<>();
                res.put("entity_id", ent.get("entity_id"));
                res.put("entity_name", name);
                res.put("record_count", ent.get("record_count"));
                res.put("data_sources", ent.get("data_sources"));
                res.put("match_key", "");
                res.put("resolution_rule", "");
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

    void writeSnapshot(Model model, SzEngine engine, String title, Path out, int port, String dataset) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stats", model.stats());
        payload.put("graph", model.graph(GRAPH_NODE_CAP));
        payload.put("merges", model.merges());
        payload.put("overlap", model.overlap());
        payload.put("matchkeys", model.matchKeys());
        payload.put("features", model.featureDist);
        if (business != null) payload.putAll(business.snapshotPayload());
        String shim =
            "<script>const __DATA__=" + scriptJson(payload) + ";"
            + "var __RECS__=null;"
            + "function __recordsFor(id){if(!__RECS__){__RECS__={};"
            + "(((__DATA__.graph||{}).nodes)||[]).forEach(function(n){"
            + "__RECS__[String(n.entity_id)]={entity_id:n.entity_id,entity_name:n.entity_name,"
            + "records:n.records||[]};});}"
            + "return __RECS__[id]||{entity_id:id,error:'not found: no such entity'};}"
            + "window.fetch=function(u){var p=u.split('?')[0].replace('/api/','');"
            + "var q=(u.split('?')[1]||'');"
            + "if(p==='search'){return Promise.resolve({json:function(){return Promise.resolve({results:[]});}});}"
            + "if(p==='why'||p==='how'){return Promise.resolve({json:function(){return Promise.resolve({error:'Why/How explanations run only against the live visualization server (start it with: src/scripts/sz-java.sh run server.VizServer --records ...).'});}});}"
            + "if(p==='disposition'||p==='whypair'){return Promise.resolve({json:function(){return Promise.resolve({error:'This is the saved snapshot: decisions are recorded only in the live app.'});}});}"
            + "if(p==='records'){var m=q.match(/entity_id=([^&]*)/);"
            + "var r=__recordsFor(m?decodeURIComponent(m[1]):'');"
            + "return Promise.resolve({json:function(){return Promise.resolve(r);}});}"
            + "return Promise.resolve({json:function(){return Promise.resolve(__DATA__[p]);}});};</script>";
        String page = renderPage(title, shim, snapshotProbeHtml(model, engine, port, dataset), model.colorKeys());
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, page, StandardCharsets.UTF_8);
    }

    // =====================================================================================
    // Settings (content-aware precedence, as in the reference)
    // =====================================================================================
    static List<String> pipelineGaps(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        Map<String, Object> doc;
        try {
            doc = Json.parseObject(raw);
        } catch (Exception e) {
            return null;
        }
        Object p = doc.get("PIPELINE");
        List<String> gaps = new ArrayList<>();
        if (!(p instanceof Map)) {
            gaps.addAll(Arrays.asList(REQUIRED_PIPELINE_KEYS));
            return gaps;
        }
        Map<String, Object> pm = Json.obj(p);
        for (String k : REQUIRED_PIPELINE_KEYS) {
            Object v = pm.get(k);
            if (v == null || v.toString().trim().isEmpty()) gaps.add(k);
        }
        return gaps;
    }

    static String resolveSettings(String path) {
        String fileRaw = "";
        try {
            if (Files.exists(Paths.get(path))) fileRaw = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("settings: cannot read " + path + " (" + e + "); falling back to $SENZING_ENGINE_CONFIGURATION_JSON");
        }
        String envRaw = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (envRaw == null) envRaw = "";
        List<String> fg = pipelineGaps(fileRaw), eg = pipelineGaps(envRaw);
        if (fg != null && fg.isEmpty()) return fileRaw;
        if (eg != null && eg.isEmpty()) {
            if (!fileRaw.trim().isEmpty()) System.err.println("settings: " + path + " is incomplete; using $SENZING_ENGINE_CONFIGURATION_JSON instead.");
            return envRaw;
        }
        if (fg != null) {
            System.err.println("Engine settings in " + path + " are incomplete: PIPELINE is missing " + String.join(", ", fg)
                + ". Proceeding would fail with SENZ7426, which points at SUPPORTPATH rather than at this file.");
        } else {
            System.err.println("No usable engine settings (" + path + " and $SENZING_ENGINE_CONFIGURATION_JSON).");
        }
        return null;
    }

    static List<String[]> recordKeys(List<String> files) throws IOException {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String[]> keys = new ArrayList<>();
        for (String f : files) {
            for (String line : Files.readAllLines(Paths.get(f), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Map<String, Object> d;
                try {
                    d = Json.parseObject(line);
                } catch (Exception e) {
                    continue;
                }
                Object ds = d.get("DATA_SOURCE"), rid = d.get("RECORD_ID");
                if (ds == null || rid == null) continue;
                if (seen.add(ds + "\u0000" + rid)) keys.add(new String[] {ds.toString(), rid.toString()});
            }
        }
        return keys;
    }

    // =====================================================================================
    // HTTP server
    // =====================================================================================
    static Map<String, String> query(URI uri) {
        Map<String, String> q = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) return q;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? "" : part.substring(eq + 1);
            q.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return q;
    }

    static void send(HttpExchange ex, int code, String body, String ctype) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ctype);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    void serve(Model model, SzEngine engine, String title, int port) throws Exception {
        HttpServer http;
        try {
            http = HttpServer.create(new InetSocketAddress(BIND_HOST, port), 0);
        } catch (IOException e) {
            System.err.println("ERROR: could not bind " + BIND_HOST + ":" + port + " (" + e.getMessage() + ").\n"
                + "Another server already holds that port on the loopback interface. Stop it, "
                + "or start this one on a different port with --port.");
            System.exit(1);
            return;
        }
        final String json = "application/json";
        http.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getPath();
                Map<String, String> q = query(ex.getRequestURI());
                switch (path) {
                    case "/": case "/index.html":
                        send(ex, 200, renderPage(title, "", null, model.colorKeys()), "text/html; charset=utf-8"); return;
                    case "/api/stats": send(ex, 200, Json.write(model.stats()), json); return;
                    case "/api/graph": send(ex, 200, Json.write(model.graph(GRAPH_NODE_CAP)), json); return;
                    case "/api/merges": send(ex, 200, Json.write(model.merges()), json); return;
                    case "/api/overlap": send(ex, 200, Json.write(model.overlap()), json); return;
                    case "/api/records": send(ex, 200, Json.write(model.records(q.getOrDefault("entity_id", ""))), json); return;
                    case "/api/matchkeys": send(ex, 200, Json.write(model.matchKeys()), json); return;
                    case "/api/features": send(ex, 200, Json.write(model.featureDist), json); return;
                    case "/api/search": send(ex, 200, Json.write(model.search(engine, q.getOrDefault("q", ""))), json); return;
                    case "/api/why": send(ex, 200, Json.write(model.why(engine, q.getOrDefault("entity_id", ""))), json); return;
                    case "/api/how": send(ex, 200, Json.write(model.how(engine, q.getOrDefault("entity_id", ""))), json); return;
                    case "/api/overview": if (business != null) { send(ex, 200, Json.write(business.overview()), json); return; } break;
                    case "/api/customers": if (business != null) { send(ex, 200, Json.write(business.customers()), json); return; } break;
                    case "/api/alerts": if (business != null) { send(ex, 200, Json.write(business.alerts()), json); return; } break;
                    case "/api/whypair": if (business != null) { send(ex, 200, Json.write(business.whyPair(q.getOrDefault("a", ""), q.getOrDefault("b", ""))), json); return; } break;
                    case "/api/disposition":
                        if (business != null && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                            send(ex, 200, Json.write(business.saveDisposition(body)), json);
                            return;
                        }
                        break;
                    default:
                        break;
                }
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "not found");
                send(ex, 404, Json.write(err), json);
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", String.valueOf(e.getMessage()));
                send(ex, 500, Json.write(err), json);
            }
        });
        http.setExecutor(Executors.newFixedThreadPool(4));
        http.start();

        // A successful bind is not proof the port was free: ask which server answers.
        if (!confirmServerIdentity(port)) {
            http.stop(0);
            System.exit(1);
        }
        System.out.println("Visualization running: http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
        System.out.flush();
    }

    static boolean confirmServerIdentity(int port) {
        String url = "http://" + BIND_HOST + ":" + port + "/api/stats";
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            try (InputStream in = c.getInputStream()) {
                Object answered = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).get("server_nonce");
                if (SERVER_NONCE.equals(answered)) return true;
                System.err.println("ERROR: port " + port + " is answered by a DIFFERENT server.\n"
                    + "  this process: " + SERVER_NONCE + "\n  answered by:  " + answered + "\n"
                    + "Stop the other server, or use --port for a free port.");
                return false;
            }
        } catch (Exception e) {
            System.err.println("ERROR: could not confirm which server is answering " + url + " (" + e + ").");
            return false;
        }
    }

    // =====================================================================================
    // main
    // =====================================================================================
    public static void main(String[] args) throws Exception {
        String settingsPath = "config/engine_config.json";
        List<String> records = new ArrayList<>();
        int port = 8080;
        String title = "Senzing Entity Resolution";
        String dataset = "";
        String snapshot = null;
        boolean noServe = false;
        boolean fromExport = false;
        boolean withBusiness = false;
        for (int k = 0; k < args.length; k++) {
            switch (args[k]) {
                case "--settings": settingsPath = args[++k]; break;
                case "--records":
                    while (k + 1 < args.length && !args[k + 1].startsWith("--")) records.add(args[++k]);
                    break;
                case "--port": port = Integer.parseInt(args[++k]); break;
                case "--title": title = args[++k]; break;
                case "--dataset": dataset = args[++k]; break;
                case "--snapshot": snapshot = args[++k]; break;
                case "--no-serve": noServe = true; break;
                case "--export": fromExport = true; break;
                case "--business": withBusiness = true; break;
                default:
                    System.err.println("unknown argument: " + args[k]);
                    System.exit(2);
            }
        }
        if (records.isEmpty() && !fromExport) {
            System.err.println("--records is required: this Java server implements the per-record (Truth Set) "
                + "build. For a bootcamper's own datastore pass --export (the export-stream build).");
            System.exit(2);
        }

        String root = System.getenv("SENZING_PROJECT_ROOT");
        Path resources = Paths.get(root != null ? root : ".", "src", "server", "resources");
        VizServer app = new VizServer(resources);

        // INV-091: refuse to render without the vendored D3, before any other work.
        if (!Files.isReadable(app.d3Path)) {
            System.err.println("ERROR: the vendored D3 asset is missing or unreadable: " + app.d3Path
                + " (d3.v7.min.js). Refusing to render without it; regenerate with "
                + "src/scripts/extract_viz_page.py and run again.");
            System.exit(1);
        }
        if (!Files.isReadable(app.pageTemplate)) {
            System.err.println("ERROR: page template missing: " + app.pageTemplate);
            System.exit(1);
        }

        String settings = resolveSettings(settingsPath);
        if (settings == null) System.exit(2);

        // The environment must outlive every engine it creates: hold it for the process lifetime.
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings).instanceName("bootcamp_viz").verboseLogging(false).build();
        Runtime.getRuntime().addShutdownHook(new Thread(env::destroy));
        SzEngine engine = env.getEngine();

        Model model = fromExport ? new Model().buildFromExport(engine)
                                 : new Model().build(engine, SZ_ENTITY_DEFAULT_FLAGS, recordKeys(records));
        try {
            model.computeFeatureDist(engine, 40);
        } catch (Exception e) {
            System.err.println("feature-score distribution unavailable (non-fatal): " + e);
        }
        Map<String, Object> s = model.stats();
        System.out.println("Entity model built: " + s.get("records_total") + " records, " + s.get("entities_total")
            + " entities, " + s.get("multi_record_entities") + " merged, " + s.get("cross_source_entities")
            + " cross-source, " + s.get("relationships_total") + " relationships");

        if (withBusiness) {
            Path frag = resources.resolve("business_tabs.html");
            app.businessFragment = Files.readString(frag, StandardCharsets.UTF_8);
            app.business = new BusinessTabs(engine, model.stats(), model.overlap(),
                Paths.get(root != null ? root : ".", "data", "app", "dispositions.json"));
            System.out.println("Business tabs ready: " + app.business.customers().size() + " customers, "
                + app.business.alerts().get("alerts_total") + " alerts");
        }

        if (snapshot != null) {
            app.writeSnapshot(model, engine, title, Paths.get(snapshot), port, dataset);
            System.out.println("Snapshot written: " + snapshot);
        }
        if (noServe) {
            System.exit(0); // the shutdown hook destroys the environment
        }
        app.serve(model, engine, title, port);
    }
}
