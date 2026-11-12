import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static com.senzing.sdk.SzFlag.*;

public final class TruthSetVizServer {
    private static final int GRAPH_NODE_CAP = 1500;
    private static final int FEATURE_SAMPLE_CAP = 40;
    private static final int CELL_CAP = 200;
    private static final String SOURCE_KEY_SEPARATOR = "|";
    private static final String SERVER_NONCE = UUID.randomUUID().toString().replace("-", "");

    private static final Map<String, String> PREFERRED_SOURCE_COLORS = Map.of(
        "CUSTOMERS", "#F57826",
        "REFERENCE", "#3B6EA5",
        "WATCHLIST", "#C8922A"
    );
    private static final List<String> FALLBACK_COLORS = List.of(
        "#8b5cf6", "#ec4899", "#0ea5e9", "#a3a34a", "#ef4444", "#14b8a6"
    );
    private static final List<String> SOURCE_STROKES = List.of(
        "#FFFFFF", "#18160F", "#FAF8F3"
    );
    private static final List<Double> SOURCE_STROKE_WIDTHS = List.of(1.5, 3.0);
    private static final List<Double> SOURCE_FILL_SHADES = List.of(0.0, 0.30, -0.30, 0.55, -0.55);
    private static final int SOURCE_STROKE_STATES =
        1 + SOURCE_STROKES.size() * SOURCE_STROKE_WIDTHS.size();
    private static final int SOURCE_ENCODING_CAPACITY =
        FALLBACK_COLORS.size() * SOURCE_STROKE_STATES * SOURCE_FILL_SHADES.size();
    private static final List<String> FEATURE_BUCKETS = List.of(
        "SAME", "CLOSE", "PLUS", "LIKELY", "PLAUSIBLE", "UNLIKELY", "NO_CHANCE"
    );
    private static final String FONT_STACK =
        "Roboto, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif";
    private static final String CODE_FONT_STACK =
        "'Fira Code', 'Courier New', Courier, monospace";

    private TruthSetVizServer() {}

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Path assets = options.assets.toAbsolutePath().normalize();
        Path templatePath = assets.resolve("index.template.html");
        Path liveProbePath = assets.resolve("probe-live.html");
        Path d3Path = assets.resolve("vendor/d3.v7.min.js");
        requireReadable(templatePath, "index.template.html");
        requireReadable(liveProbePath, "probe-live.html");
        requireReadable(d3Path, "d3.v7.min.js");

        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalStateException(
                "SENZING_ENGINE_CONFIGURATION_JSON is not set; source src/scripts/senzing-env.sh first");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(TruthSetVizServer.class.getSimpleName())
            .verboseLogging(false)
            .build();
        try {
            SzEngine engine = environment.getEngine();
            Model model = new Model(SERVER_NONCE);
            if (options.records.isEmpty()) {
                model.buildFromExport(engine);
            } else {
                model.buildFromRecords(engine, options.records);
            }
            if (model.entities.isEmpty() || model.recordsTotal == 0) {
                throw new IllegalStateException(
                    "The entity model is empty; refusing to write an empty visualization");
            }
            if (options.expectedRecords > 0 && model.recordsTotal != options.expectedRecords) {
                throw new IllegalStateException(
                    "Expected " + options.expectedRecords + " input records but modeled "
                        + model.recordsTotal);
            }
            model.computeFeatureScores(engine);
            Map<String, Object> stats = model.stats();
            System.out.printf(
                "Entity model built: %d records, %d entities, %d merged, %d cross-source, "
                    + "%d relationships%n",
                model.recordsTotal, model.entities.size(),
                stats.get("multi_record_entities"), stats.get("cross_source_entities"),
                stats.get("relationships_total"));

            if (options.snapshot != null) {
                String template = Files.readString(templatePath, StandardCharsets.UTF_8);
                String d3 = Files.readString(d3Path, StandardCharsets.UTF_8);
                writeSnapshot(model, engine, options, template, d3);
                System.out.println("Snapshot written: " + options.snapshot);
            }
            if (options.noServe) {
                if (options.snapshot == null) {
                    throw new IllegalArgumentException("--no-serve requires --snapshot");
                }
                return;
            }

            String template = Files.readString(templatePath, StandardCharsets.UTF_8);
            String liveProbe = Files.readString(liveProbePath, StandardCharsets.UTF_8);
            String d3 = Files.readString(d3Path, StandardCharsets.UTF_8);
            serve(model, engine, options, template, liveProbe, d3);
        } finally {
            environment.destroy();
        }
    }

    private static void requireReadable(Path path, String assetName) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException(
                "Required visualization asset is missing or unreadable: " + assetName + " (" + path + ")");
        }
    }

    private static void writeSnapshot(
        Model model, SzEngine engine, Options options, String template, String d3
    ) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stats", model.stats());
        payload.put("graph", model.graph(GRAPH_NODE_CAP));
        payload.put("merges", model.merges());
        payload.put("records", model.snapshotRecords());
        payload.put("overlap", model.overlap());
        payload.put("matchkeys", model.matchKeys());
        payload.put("features", model.featureScores());
        String shim = snapshotShim(payload);
        String probe = snapshotProbeBody(model, engine, options.port, options.dataset);
        String page = renderPage(template, options.title, probe, model.colorKeys(), shim, d3);
        Path output = options.snapshot.toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, page, StandardCharsets.UTF_8);
    }

    private static String snapshotShim(Map<String, Object> payload) {
        return "<script>const __DATA__=" + scriptSafeJson(payload) + ";"
            + "function __recordsFor(id){return ((__DATA__.records||{})[String(id)])||"
            + "{entity_id:id,error:'not found: no such entity'};}"
            + "window.fetch=function(u){var p=u.split('?')[0].replace('/api/','');"
            + "var q=(u.split('?')[1]||'');"
            + "if(p==='search'){return Promise.resolve({json:function(){return Promise.resolve({results:[]});}});}"
            + "if(p==='why'||p==='how'){return Promise.resolve({json:function(){return Promise.resolve({error:"
            + "'Why/How explanations run only against the live Java visualization server.'});}});}"
            + "if(p==='records'){var m=q.match(/entity_id=([^&]*)/);"
            + "var r=__recordsFor(m?decodeURIComponent(m[1]):'');"
            + "return Promise.resolve({json:function(){return Promise.resolve(r);}});}"
            + "return Promise.resolve({json:function(){return Promise.resolve(__DATA__[p]);}});};</script>";
    }

    private static String snapshotProbeBody(Model model, SzEngine engine, int port, String dataset) {
        List<Map<String, Object>> candidates = new ArrayList<>(model.mergedEntities());
        candidates.sort(Comparator
            .comparing((Map<String, Object> e) -> ((List<?>) e.get("data_sources")).size() < 2)
            .thenComparing((Map<String, Object> e) -> -asLong(e.get("record_count")))
            .thenComparing(e -> asLong(e.get("entity_id"))));
        List<String> cards = new ArrayList<>();
        for (Map<String, Object> entity : candidates) {
            if (cards.size() >= 5) {
                break;
            }
            String name = string(entity.get("entity_name"));
            if (name.isBlank()) {
                continue;
            }
            try {
                List<Map<String, Object>> results =
                    asMaps(model.search(engine, name).get("results"));
                Map<String, Object> result = null;
                for (Map<String, Object> candidate : results) {
                    if (asLong(candidate.get("entity_id")) == asLong(entity.get("entity_id"))) {
                        result = candidate;
                        break;
                    }
                }
                if (result == null && !results.isEmpty()) {
                    result = results.get(0);
                }
                if (result == null) {
                    System.err.println("Dropped snapshot example with no verified search hit: " + name);
                    continue;
                }
                cards.add(resultCard(name, result));
            } catch (Exception e) {
                System.err.println("Dropped snapshot example after search failure for " + name
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        String where = dataset == null || dataset.isBlank() ? "the loaded data" : dataset.trim();
        String note = "<p class=\"muted\">This is a saved snapshot, so live search is disabled. "
            + "Below are example searches run against " + htmlEscape(where) + ". In the live app "
            + "(<code>http://localhost:" + port + "</code>) you can search any name.</p>";
        String browse = "<div id=\"probe-btns\"></div>";
        if (cards.isEmpty()) {
            return note + browse + "<p class=\"muted\">No multi-record entities to show.</p>";
        }
        return note + browse + "<div id=\"results\">" + String.join("", cards) + "</div>";
    }

    private static String resultCard(String searched, Map<String, Object> result) {
        StringBuilder html = new StringBuilder("<div class=\"card\">");
        html.append("<div class=\"muted\" style=\"margin-bottom:4px\">Searched: <b>")
            .append(htmlEscape(searched)).append("</b></div>");
        html.append("<h4>").append(htmlEscape(result.get("entity_name"))).append("</h4>");
        List<?> sources = asList(result.get("data_sources"));
        html.append("<div class=\"muted\">Entity ")
            .append(htmlEscape(result.get("entity_id"))).append(" · ")
            .append(htmlEscape(result.get("record_count"))).append(" record(s) · ")
            .append(htmlEscape(String.join(", ", sources.stream().map(TruthSetVizServer::string).toList())))
            .append("</div>");
        Map<String, Object> matchKeys = asMap(result.get("match_keys"));
        String matchKey = string(matchKeys.get("entity_level"));
        List<Map<String, Object>> rules = asMaps(result.get("resolution_rules"));
        if (!rules.isEmpty()) {
            String rule = string(rules.get(0).get("rule"));
            html.append("<div class=\"muted\">Resolution rule: ")
                .append(rule.isBlank() ? "not returned"
                    : "<code>" + htmlEscape(rule) + "</code>")
                .append("</div>");
        } else {
            html.append("<div class=\"muted\">Resolution rule: not returned</div>");
        }
        html.append("<div class=\"search-evidence\"><div class=\"muted\">Entity-level match key:</div>")
            .append(matchKey.isBlank() ? "<span class=\"muted\">not returned</span>"
                : "<div class=\"mk\">" + matchKeyChips(matchKey) + "</div>")
            .append("</div>");
        List<Map<String, Object>> perRecord = asMaps(matchKeys.get("per_record"));
        html.append("<div class=\"search-evidence\"><div class=\"muted\">Per-record match keys:</div>");
        if (perRecord.isEmpty()) {
            html.append("<span class=\"muted\">not returned</span>");
        } else {
            for (Map<String, Object> record : perRecord) {
                String label = string(record.get("data_source")) + ":" + string(record.get("record_id"));
                String key = string(record.get("match_key"));
                html.append("<div>").append(htmlEscape(label)).append(": ")
                    .append(key.isBlank() ? "<span class=\"muted\">not returned</span>"
                        : matchKeyChips(key))
                    .append("</div>");
            }
        }
        html.append("</div><div class=\"search-evidence\"><div class=\"muted\">Feature scores:</div>");
        List<Map<String, Object>> scores = asMaps(result.get("feature_scores"));
        if (scores.isEmpty()) {
            html.append("<span class=\"muted\">not returned</span>");
        } else {
            for (Map<String, Object> score : scores) {
                String bucket = string(score.get("label")).toUpperCase();
                String value = string(score.get("score"));
                html.append("<span class=\"bucket ").append(scoreBucketClass(bucket)).append("\">")
                    .append(htmlEscape(score.get("feature"))).append(": ")
                    .append(bucket.isBlank() ? "not returned" : htmlEscape(bucket))
                    .append(value.isBlank() ? "" : " · " + htmlEscape(value))
                    .append("</span> ");
            }
        }
        html.append("</div>");
        long entityId = asLong(result.get("entity_id"));
        String entityName = string(result.get("entity_name"));
        html.append("<div class=\"actions snapshot-actions\">");
        for (String action : List.of("records", "why", "how")) {
            String label = action.equals("records") ? "Records" : action.equals("why") ? "Why?" : "How?";
            html.append("<button type=\"button\" data-snapshot-action=\"")
                .append(action).append("\" data-entity-id=\"").append(entityId)
                .append("\" data-entity-name=\"").append(htmlEscape(entityName))
                .append("\">").append(label).append("</button>");
        }
        html.append("</div>");
        return html.append("</div>").toString();
    }

    private static String scoreBucketClass(String bucket) {
        return switch (bucket) {
            case "SAME", "CLOSE" -> "b-strong";
            case "UNLIKELY", "NO_CHANCE" -> "b-none";
            default -> "b-mid";
        };
    }

    private static String matchKeyChips(String matchKey) {
        StringBuilder output = new StringBuilder();
        StringBuilder part = new StringBuilder();
        for (char c : matchKey.toCharArray()) {
            if (c == '+' || c == '-') {
                if (part.length() > 0) {
                    output.append("<span>").append(htmlEscape(part)).append("</span>");
                }
                part.setLength(0);
                part.append(c);
            } else {
                part.append(c);
            }
        }
        if (part.length() > 0) {
            output.append("<span>").append(htmlEscape(part)).append("</span>");
        }
        return output.toString();
    }

    private static String renderPage(
        String template, String title, String probe, List<String> colorKeys, String dataShim, String d3
    ) {
        String rootVars = "--navy:#18160F;--blue:#F57826;--gold:#FF4E1F;--ink:#18160F;"
            + "--muted:#4A4640;--line:#E5DFD3;--bg:#FAF8F3;--accent-soft:#FDEEE3;--green:#1D9E75;"
            + "--score-positive:var(--green);--score-caution:#8a6d00;--score-negative:#a50e0e";
        String page = template
            .replace("__TITLE__", htmlEscape(title))
            .replace("__PROBE_BODY__", probe)
            .replace("__ROOT_VARS__", rootVars)
            .replace("__FONT_STACK__", FONT_STACK)
            .replace("__CODE_FONT__", CODE_FONT_STACK)
            .replace("__ACCENT_HOT__", "#FF4E1F")
            .replace("__ACCENT__", "#F57826")
            .replace("__SRC_COLORS__", scriptSafeJson(colorForSources(colorKeys)))
            .replace("__DATA_SHIM__", dataShim)
            .replace("__D3_SCRIPT__", "<script>" + d3 + "</script>");
        List<String> placeholders = List.of(
            "__TITLE__", "__PROBE_BODY__", "__ROOT_VARS__", "__FONT_STACK__",
            "__CODE_FONT__", "__ACCENT_HOT__", "__ACCENT__", "__SRC_COLORS__",
            "__DATA_SHIM__", "__D3_SCRIPT__"
        );
        if (placeholders.stream().anyMatch(page::contains)) {
            throw new IllegalStateException("Visualization template contains an unresolved placeholder");
        }
        return page;
    }

    private static void serve(
        Model model, SzEngine engine, Options options, String template, String liveProbe, String d3
    ) throws Exception {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", options.port), 0);
        } catch (IOException e) {
            throw new IOException("Could not bind 127.0.0.1:" + options.port + ": " + e.getMessage(), e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> handle(exchange, model, engine, options, template, liveProbe, d3));
        server.start();
        if (!confirmServerIdentity(options.port)) {
            server.stop(0);
            throw new IllegalStateException(
                "Port " + options.port + " is answered by a different server; refusing to hand over its URL");
        }
        writeServerState(options.stateFile, options.port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0), "viz-server-shutdown"));
        System.out.println("Visualization running: http://localhost:" + options.port);
        System.out.println("Server PID: " + ProcessHandle.current().pid());
        new CountDownLatch(1).await();
    }

    private static boolean confirmServerIdentity(int port) {
        String url = "http://127.0.0.1:" + port + "/api/stats";
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
            connection.setRequestMethod("GET");
            try (var input = connection.getInputStream()) {
                Map<String, Object> body = asMap(parseJson(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
                if (SERVER_NONCE.equals(body.get("server_nonce"))) {
                    return true;
                }
                System.err.println("Port " + port + " answered with a different server nonce.");
                System.err.println("  this process: " + SERVER_NONCE);
                System.err.println("  responder: " + body.get("server_nonce"));
                return false;
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            System.err.println("Could not confirm the server answering " + url + ": "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private static void writeServerState(Path stateFile, int port) throws IOException {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("pid", ProcessHandle.current().pid());
        state.put("port", port);
        state.put("server_nonce", SERVER_NONCE);
        state.put("url", "http://localhost:" + port);
        Path output = stateFile.toAbsolutePath().normalize();
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, jsonString(state), StandardCharsets.UTF_8);
    }

    private static void handle(
        HttpExchange exchange, Model model, SzEngine engine, Options options,
        String template, String liveProbe, String d3
    ) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = queryParams(exchange.getRequestURI().getRawQuery());
            if (path.equals("/") || path.equals("/index.html")) {
                send(exchange, 200, renderPage(
                    template, options.title, liveProbe, model.colorKeys(), "", d3),
                    "text/html; charset=utf-8");
                return;
            }
            Object body;
            switch (path) {
                case "/api/stats" -> body = model.stats();
                case "/api/graph" -> body = model.graph(GRAPH_NODE_CAP);
                case "/api/merges" -> body = model.merges();
                case "/api/records" -> body = model.records(query.getOrDefault("entity_id", ""));
                case "/api/search" -> body = model.search(engine, query.getOrDefault("q", ""));
                case "/api/why" -> body = model.why(engine, query.getOrDefault("entity_id", ""));
                case "/api/how" -> body = model.how(engine, query.getOrDefault("entity_id", ""));
                case "/api/overlap" -> body = model.overlap();
                case "/api/matchkeys" -> body = model.matchKeys();
                case "/api/features" -> body = model.featureScores();
                default -> {
                    send(exchange, 404, Map.of("error", "not found"), "application/json; charset=utf-8");
                    return;
                }
            }
            send(exchange, 200, body, "application/json; charset=utf-8");
        } catch (Exception e) {
            send(exchange, 500, Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()),
                "application/json; charset=utf-8");
        } finally {
            exchange.close();
        }
    }

    private static void send(HttpExchange exchange, int status, Object body, String contentType)
        throws IOException {
        byte[] bytes = (body instanceof String s ? s : jsonString(body)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static Map<String, String> queryParams(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String item : rawQuery.split("&")) {
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private static final class Model {
        private final String serverNonce;
        private final Map<Long, Entity> entities = new TreeMap<>();
        private final Map<String, Edge> edges = new TreeMap<>();
        private int recordsTotal;
        private Map<String, Object> featureDistribution = Map.of(
            "features", List.of(), "sampled", 0, "multi_record_total", 0, "capped", false
        );

        private Model(String serverNonce) {
            this.serverNonce = serverNonce;
        }

        private void buildFromRecords(SzEngine engine, List<Path> inputFiles) throws Exception {
            Set<String> seen = new HashSet<>();
            for (Path input : inputFiles) {
                if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
                    throw new IOException("Cannot read record file: " + input);
                }
                try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
                    String line;
                    int lineNumber = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        if (line.isBlank()) {
                            continue;
                        }
                        Map<String, Object> record;
                        try {
                            record = asMap(parseJson(line));
                        } catch (RuntimeException e) {
                            throw new IOException("Invalid JSON at " + input + ":" + lineNumber, e);
                        }
                        String dataSource = string(record.get("DATA_SOURCE"));
                        String recordId = string(record.get("RECORD_ID"));
                        if (dataSource.isBlank() || recordId.isBlank()) {
                            throw new IOException("Missing DATA_SOURCE or RECORD_ID at "
                                + input + ":" + lineNumber);
                        }
                        String key = dataSource + "\u001f" + recordId;
                        if (!seen.add(key)) {
                            continue;
                        }
                        String response = engine.getEntity(
                            SzRecordKey.of(dataSource, recordId), SZ_ENTITY_DEFAULT_FLAGS);
                        absorb(asMap(parseJson(response)));
                        recordsTotal++;
                    }
                }
            }
            removeDanglingEdges();
        }

        private void buildFromExport(SzEngine engine) throws Exception {
            Set<SzFlag> flags = new HashSet<>(SZ_EXPORT_INCLUDE_ALL_ENTITIES);
            flags.addAll(SZ_ENTITY_DEFAULT_FLAGS);
            long handle = engine.exportJsonEntityReport(flags);
            try {
                while (true) {
                    String row = engine.fetchNext(handle);
                    if (row == null || row.isBlank()) {
                        break;
                    }
                    Map<String, Object> response = asMap(parseJson(row));
                    Long id = maybeLong(asMap(response.get("RESOLVED_ENTITY")).get("ENTITY_ID"));
                    if (id == null || entities.containsKey(id)) {
                        continue;
                    }
                    absorb(response);
                    Entity entity = entities.get(id);
                    recordsTotal += entity.records.size();
                }
            } finally {
                engine.closeExportReport(handle);
            }
            removeDanglingEdges();
        }

        private void absorb(Map<String, Object> response) {
            if (!(response.get("RELATED_ENTITIES") instanceof List<?>)) {
                throw new IllegalStateException(
                    "Entity export row is missing RELATED_ENTITIES; refusing to build a graph without verified edges");
            }
            Map<String, Object> resolved = asMap(response.get("RESOLVED_ENTITY"));
            Long id = maybeLong(resolved.get("ENTITY_ID"));
            if (id == null) {
                throw new IllegalStateException("Entity response is missing RESOLVED_ENTITY.ENTITY_ID");
            }
            List<Map<String, Object>> recordDocs = asMaps(resolved.get("RECORDS"));
            if (!entities.containsKey(id)) {
                List<Map<String, Object>> records = new ArrayList<>();
                List<Map<String, Object>> rules = new ArrayList<>();
                Set<String> sources = new TreeSet<>();
                for (Map<String, Object> record : recordDocs) {
                    String source = string(record.get("DATA_SOURCE"));
                    String recordId = string(record.get("RECORD_ID"));
                    if (source.isBlank() || recordId.isBlank()) {
                        throw new IllegalStateException(
                            "Entity " + id + " has a record without DATA_SOURCE or RECORD_ID");
                    }
                    sources.add(source);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("data_source", source);
                    item.put("record_id", recordId);
                    item.put("match_key", string(record.get("MATCH_KEY")));
                    records.add(item);
                    String rule = string(record.get("ERRULE_CODE"));
                    if (!rule.isBlank()) {
                        Map<String, Object> ruleItem = new LinkedHashMap<>();
                        ruleItem.put("data_source", source);
                        ruleItem.put("record_id", recordId);
                        ruleItem.put("rule", rule);
                        rules.add(ruleItem);
                    }
                }
                if (records.isEmpty()) {
                    throw new IllegalStateException("Entity " + id + " has no records in its response");
                }
                String name = string(resolved.get("ENTITY_NAME"));
                entities.put(id, new Entity(
                    id, name.isBlank() ? "Entity " + id : name,
                    new ArrayList<>(sources), records, rules));
            }
            for (Map<String, Object> related : asMaps(response.get("RELATED_ENTITIES"))) {
                Long target = maybeLong(related.get("ENTITY_ID"));
                if (target == null || target.equals(id)) {
                    continue;
                }
                long low = Math.min(id, target);
                long high = Math.max(id, target);
                String key = low + "|" + high;
                String type = relationshipType(related);
                String matchKey = string(related.get("MATCH_KEY"));
                Edge existing = edges.get(key);
                if (existing == null || relationshipPriority(type) > relationshipPriority(existing.type)) {
                    edges.put(key, new Edge(low, high, matchKey, type));
                }
            }
        }

        private void removeDanglingEdges() {
            edges.entrySet().removeIf(entry ->
                !entities.containsKey(entry.getValue().source)
                    || !entities.containsKey(entry.getValue().target));
        }

        private Map<String, Object> stats() {
            Map<String, Object> histogram = new LinkedHashMap<>();
            Map<String, Object> buckets = new LinkedHashMap<>();
            for (String bucket : List.of("1", "2", "3", "4+")) {
                histogram.put(bucket, 0);
                buckets.put(bucket, new ArrayList<Map<String, Object>>());
            }
            Set<String> sources = new TreeSet<>();
            int multi = 0;
            int cross = 0;
            for (Entity entity : entities.values()) {
                sources.addAll(entity.dataSources);
                int count = entity.records.size();
                String bucket = count >= 4 ? "4+" : Integer.toString(count);
                histogram.put(bucket, asLong(histogram.get(bucket)) + 1);
                if (count > 1) {
                    multi++;
                }
                if (entity.dataSources.size() >= 2) {
                    cross++;
                }
                List<Map<String, Object>> values = asMaps(buckets.get(bucket));
                if (values.size() < CELL_CAP) {
                    values.add(Map.of(
                        "entity_id", entity.id,
                        "entity_name", entity.name,
                        "record_count", count
                    ));
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("records_total", recordsTotal);
            result.put("entities_total", entities.size());
            result.put("multi_record_entities", multi);
            result.put("cross_source_entities", cross);
            result.put("relationships_total", edges.size());
            result.put("data_sources_total", sources.size());
            result.put("histogram", histogram);
            result.put("bucket_entities", buckets);
            result.put("sample_entities", sampleEntities());
            result.put("server_nonce", serverNonce);
            return result;
        }

        private List<Map<String, Object>> sampleEntities() {
            List<Entity> sorted = entities.values().stream()
                .filter(e -> e.records.size() > 1)
                .sorted(Comparator.comparingInt((Entity e) -> e.records.size()).reversed()
                    .thenComparingLong(e -> e.id))
                .toList();
            List<Map<String, Object>> sample = new ArrayList<>();
            for (Entity entity : sorted.stream().limit(10).toList()) {
                sample.add(Map.of(
                    "entity_id", entity.id,
                    "entity_name", entity.name,
                    "record_count", entity.records.size(),
                    "data_sources", entity.dataSources
                ));
            }
            return sample;
        }

        private Map<String, Object> graph(int cap) {
            List<Entity> nodes = new ArrayList<>(entities.values());
            int total = nodes.size();
            Set<Long> related = new HashSet<>();
            Map<Long, Integer> degree = new HashMap<>();
            for (Edge edge : edges.values()) {
                related.add(edge.source);
                related.add(edge.target);
                degree.merge(edge.source, 1, Integer::sum);
                degree.merge(edge.target, 1, Integer::sum);
            }
            boolean capped = nodes.size() > cap;
            if (capped) {
                nodes.sort(Comparator
                    .comparingInt((Entity e) -> e.dataSources.size()).reversed()
                    .thenComparing(Comparator.comparingInt(
                        (Entity e) -> degree.getOrDefault(e.id, 0)).reversed())
                    .thenComparing(Comparator.comparingInt(
                        (Entity e) -> e.records.size()).reversed())
                    .thenComparingLong(e -> e.id));
                nodes = new ArrayList<>(nodes.subList(0, cap));
            }
            Set<Long> nodeIds = new HashSet<>();
            List<Map<String, Object>> nodePayload = new ArrayList<>();
            for (Entity entity : nodes) {
                nodeIds.add(entity.id);
                nodePayload.add(entity.toMap());
            }
            List<Map<String, Object>> edgePayload = new ArrayList<>();
            for (Edge edge : edges.values()) {
                if (nodeIds.contains(edge.source) && nodeIds.contains(edge.target)) {
                    edgePayload.add(edge.toMap());
                }
            }
            Map<String, Object> graph = new LinkedHashMap<>();
            graph.put("nodes", nodePayload);
            graph.put("edges", edgePayload);
            graph.put("total", total);
            graph.put("capped", capped);
            graph.put("related_total", related.size());
            graph.put("encoding_check", encodingCheck(nodes));
            return graph;
        }

        private Map<String, Object> encodingCheck(List<Entity> nodes) {
            Set<String> keys = new TreeSet<>();
            for (Entity entity : nodes) {
                if (!entity.dataSources.isEmpty()) {
                    keys.add(String.join(SOURCE_KEY_SEPARATOR, entity.dataSources));
                }
            }
            List<String> combinations = keys.stream()
                .filter(key -> key.contains(SOURCE_KEY_SEPARATOR)).toList();
            boolean exercised = !combinations.isEmpty();
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("distinct_source_set_keys", keys.size());
            check.put("source_set_keys", new ArrayList<>(keys));
            check.put("combination_keys", combinations);
            check.put("status", exercised ? "ok" : "not_exercised");
            check.put("detail", exercised
                ? "Compare the number of combination rows the source legend names against "
                    + "len(combination_keys); they MUST be equal. Per-source rows are not source-set keys."
                : "No combination key is present, so this check has NOT been exercised; it is not a pass.");
            return check;
        }

        private Map<String, Object> merges() {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Entity entity : sortedEntities()) {
                if (entity.records.size() > 1) {
                    result.add(entity.toMap());
                }
            }
            return Map.of("entities", result);
        }

        private List<Map<String, Object>> mergedEntities() {
            return asMaps(merges().get("entities"));
        }

        private List<Entity> sortedEntities() {
            return entities.values().stream()
                .sorted(Comparator.comparingInt((Entity e) -> e.records.size()).reversed()
                    .thenComparingLong(e -> e.id))
                .toList();
        }

        private Map<String, Object> records(String entityId) {
            Long id = maybeLong(entityId);
            if (id == null) {
                return Map.of("entity_id", entityId, "error", "bad request: entity_id must be an integer");
            }
            Entity entity = entities.get(id);
            if (entity == null) {
                return Map.of("entity_id", id, "error", "not found: no such entity");
            }
            return Map.of(
                "entity_id", id,
                "entity_name", entity.name,
                "records", entity.records
            );
        }

        private Map<String, Object> snapshotRecords() {
            Map<String, Object> result = new TreeMap<>();
            for (Entity entity : entities.values()) {
                result.put(Long.toString(entity.id), records(Long.toString(entity.id)));
            }
            return result;
        }

        private Map<String, Object> overlap() {
            List<String> sources = allDataSources();
            Map<String, Integer> indices = new HashMap<>();
            for (int i = 0; i < sources.size(); i++) {
                indices.put(sources.get(i), i);
            }
            int[][] matrix = new int[sources.size()][sources.size()];
            Map<String, List<Map<String, Object>>> cells = new TreeMap<>();
            boolean cellCapped = false;
            for (Entity entity : entities.values()) {
                Map<String, Object> entry = Map.of(
                    "entity_id", entity.id,
                    "entity_name", entity.name,
                    "record_count", entity.records.size()
                );
                List<Integer> indicesForEntity = entity.dataSources.stream()
                    .map(indices::get).sorted().toList();
                for (int i : indicesForEntity) {
                    matrix[i][i]++;
                    cellCapped |= addCell(cells, i, i, entry);
                }
                for (int i = 0; i < indicesForEntity.size(); i++) {
                    for (int j = i + 1; j < indicesForEntity.size(); j++) {
                        int a = indicesForEntity.get(i);
                        int b = indicesForEntity.get(j);
                        matrix[a][b]++;
                        matrix[b][a]++;
                        cellCapped |= addCell(cells, a, b, entry);
                    }
                }
            }
            List<List<Integer>> rows = new ArrayList<>();
            for (int[] row : matrix) {
                List<Integer> values = new ArrayList<>();
                for (int value : row) {
                    values.add(value);
                }
                rows.add(values);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sources", sources);
            result.put("matrix", rows);
            result.put("cell_entities", cells);
            result.put("cell_capped", cellCapped);
            return result;
        }

        private boolean addCell(
            Map<String, List<Map<String, Object>>> cells, int i, int j, Map<String, Object> entity
        ) {
            String key = i + "," + j;
            List<Map<String, Object>> values = cells.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (values.size() >= CELL_CAP) {
                return true;
            }
            values.add(entity);
            return false;
        }

        private Map<String, Object> matchKeys() {
            Map<String, Integer> counts = new HashMap<>();
            for (Entity entity : entities.values()) {
                for (Map<String, Object> record : entity.records) {
                    String key = string(record.get("match_key"));
                    if (!key.isBlank()) {
                        counts.merge(key, 1, Integer::sum);
                    }
                }
            }
            List<Map.Entry<String, Integer>> ordered = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
                .toList();
            List<String> topKeys = ordered.stream().limit(20).map(Map.Entry::getKey).toList();
            Map<String, List<Map<String, Object>>> byKey = new LinkedHashMap<>();
            for (String key : topKeys) {
                byKey.put(key, new ArrayList<>());
            }
            boolean capped = false;
            for (Entity entity : entities.values()) {
                Set<String> seen = new HashSet<>();
                for (Map<String, Object> record : entity.records) {
                    String key = string(record.get("match_key"));
                    if (byKey.containsKey(key) && seen.add(key)) {
                        List<Map<String, Object>> list = byKey.get(key);
                        if (list.size() < CELL_CAP) {
                            list.add(Map.of(
                                "entity_id", entity.id,
                                "entity_name", entity.name,
                                "record_count", entity.records.size()
                            ));
                        } else {
                            capped = true;
                        }
                    }
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : ordered.stream().limit(20).toList()) {
                rows.add(Map.of("match_key", entry.getKey(), "count", entry.getValue()));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("match_keys", rows);
            result.put("distinct", counts.size());
            result.put("capped", counts.size() > 20);
            result.put("match_key_entities", byKey);
            result.put("entities_capped", capped);
            return result;
        }

        private Map<String, Object> featureScores() {
            return featureDistribution;
        }

        private void computeFeatureScores(SzEngine engine) {
            Map<String, Map<String, Integer>> perFeature = new TreeMap<>();
            List<Entity> multi = entities.values().stream()
                .filter(entity -> entity.records.size() > 1)
                .sorted(Comparator.comparingInt((Entity e) -> e.records.size()).reversed()
                    .thenComparingLong(e -> e.id))
                .toList();
            int sampled = 0;
            for (Entity entity : multi) {
                if (sampled >= FEATURE_SAMPLE_CAP) {
                    break;
                }
                Map<String, Object> result = why(engine, Long.toString(entity.id));
                if (result.containsKey("error")) {
                    System.err.println("Feature-score sample skipped for entity " + entity.id + ": "
                        + result.get("error"));
                    continue;
                }
                Map<String, Object> sdkResult = asMap(result.get("result"));
                boolean counted = false;
                for (Map<String, Object> why : asMaps(sdkResult.get("WHY_RESULTS"))) {
                    Map<String, Object> featureScores =
                        asMap(asMap(why.get("MATCH_INFO")).get("FEATURE_SCORES"));
                    for (Map.Entry<String, Object> feature : featureScores.entrySet()) {
                        for (Map<String, Object> score : asMaps(feature.getValue())) {
                            String bucket = string(score.get("SCORE_BUCKET")).toUpperCase();
                            if (!bucket.isBlank()) {
                                perFeature.computeIfAbsent(feature.getKey(), ignored -> new TreeMap<>())
                                    .merge(bucket, 1, Integer::sum);
                                counted = true;
                            }
                        }
                    }
                }
                if (counted) {
                    sampled++;
                } else {
                    System.err.println("Feature-score sample contained no score buckets for entity "
                        + entity.id);
                }
            }
            List<Map<String, Object>> features = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> feature : perFeature.entrySet()) {
                Map<String, Integer> buckets = new LinkedHashMap<>();
                for (String bucket : FEATURE_BUCKETS) {
                    Integer count = feature.getValue().get(bucket);
                    if (count != null) {
                        buckets.put(bucket, count);
                    }
                }
                features.add(Map.of("feature", feature.getKey(), "buckets", buckets));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("features", features);
            result.put("sampled", sampled);
            result.put("multi_record_total", multi.size());
            result.put("capped", multi.size() > FEATURE_SAMPLE_CAP);
            featureDistribution = result;
        }

        private Map<String, Object> search(SzEngine engine, String query) {
            String name = query == null ? "" : query.trim();
            if (name.isBlank()) {
                return Map.of("results", List.of(), "attributes_tried", List.of());
            }
            List<String> attrs = List.of("NAME_FULL", "NAME_ORG");
            List<String> tried = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            List<Map<String, Object>> hits = new ArrayList<>();
            Set<Long> seen = new LinkedHashSet<>();
            for (String attr : attrs) {
                tried.add(attr);
                Map<String, Object> attributes = Map.of(attr, name);
                try {
                    Map<String, Object> response = asMap(parseJson(engine.searchByAttributes(
                        jsonString(attributes), SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS)));
                    for (Map<String, Object> item : asMaps(response.get("RESOLVED_ENTITIES"))) {
                        Map<String, Object> resolved = asMap(
                            asMap(asMap(item.get("ENTITY")).get("RESOLVED_ENTITY")));
                        Long id = maybeLong(resolved.get("ENTITY_ID"));
                        if (id != null && seen.add(id)) {
                            hits.add(item);
                        }
                    }
                    if (!hits.isEmpty()) {
                        break;
                    }
                } catch (Exception e) {
                    failures.add(attr + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                Map<String, Object> item = hits.get(i);
                Map<String, Object> resolved = asMap(
                    asMap(asMap(item.get("ENTITY")).get("RESOLVED_ENTITY")));
                Map<String, Object> matchInfo = asMap(item.get("MATCH_INFO"));
                Long id = maybeLong(resolved.get("ENTITY_ID"));
                Entity local = id == null ? null : entities.get(id);
                String entityName = string(resolved.get("ENTITY_NAME"));
                if (entityName.isBlank() && local != null) {
                    entityName = local.name;
                }
                int count = local == null
                    ? asMaps(resolved.get("RECORDS")).size()
                    : local.records.size();
                List<String> sources = local == null
                    ? sourcesFromRecords(asMaps(resolved.get("RECORDS")))
                    : local.dataSources;
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("entity_id", id);
                result.put("entity_name", entityName);
                result.put("record_count", count);
                result.put("data_sources", sources);
                if (i < 10 && id != null) {
                    try {
                        Map<String, Object> detail = asMap(parseJson(
                            engine.getEntity(id, SZ_ENTITY_DEFAULT_FLAGS)));
                        Map<String, Object> detailEntity = asMap(detail.get("RESOLVED_ENTITY"));
                        List<Map<String, Object>> detailRecords = asMaps(detailEntity.get("RECORDS"));
                        Map<String, Object> matchKeys = new LinkedHashMap<>();
                        String entityMatchKey = string(matchInfo.get("MATCH_KEY"));
                        if (entityMatchKey.isBlank()) {
                            entityMatchKey = string(detailEntity.get("MATCH_KEY"));
                        }
                        matchKeys.put("entity_level", entityMatchKey);
                        result.put("match_key", entityMatchKey);
                        List<Map<String, Object>> perRecord = new ArrayList<>();
                        List<Map<String, Object>> rules = new ArrayList<>();
                        for (Map<String, Object> record : detailRecords) {
                            String matchKey = string(record.get("MATCH_KEY"));
                            Map<String, Object> perRecordMatch = new LinkedHashMap<>();
                            perRecordMatch.put("data_source", string(record.get("DATA_SOURCE")));
                            perRecordMatch.put("record_id", string(record.get("RECORD_ID")));
                            perRecordMatch.put("match_key", matchKey);
                            perRecord.add(perRecordMatch);
                            String rule = string(record.get("ERRULE_CODE"));
                            if (!rule.isBlank()) {
                                rules.add(Map.of(
                                    "data_source", string(record.get("DATA_SOURCE")),
                                    "record_id", string(record.get("RECORD_ID")),
                                    "rule", rule
                                ));
                            }
                        }
                        matchKeys.put("per_record", perRecord);
                        result.put("match_keys", matchKeys);
                        result.put("feature_scores", extractFeatureScores(matchInfo));
                        result.put("resolution_rules", rules);
                        String resolutionRule = string(matchInfo.get("ERRULE_CODE"));
                        if (resolutionRule.isBlank() && !rules.isEmpty()) {
                            resolutionRule = string(rules.get(0).get("rule"));
                        }
                        result.put("resolution_rule", resolutionRule);
                        result.put("enrichment_error", null);
                    } catch (Exception e) {
                        result.put("match_key", null);
                        result.put("match_keys", null);
                        result.put("feature_scores", null);
                        result.put("resolution_rules", null);
                        result.put("resolution_rule", null);
                        result.put("enrichment_error",
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                } else {
                    result.put("match_key", null);
                    result.put("match_keys", null);
                    result.put("feature_scores", null);
                    result.put("resolution_rules", null);
                    result.put("resolution_rule", null);
                    result.put("enrichment_error", null);
                }
                results.add(result);
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("results", results);
            output.put("attributes_tried", tried);
            output.put("query", Map.of(
                "name", name, "address", JsonValue.NULL, "phone", JsonValue.NULL, "email", JsonValue.NULL
            ));
            if (hits.isEmpty() && !failures.isEmpty()) {
                output.put("error", String.join("; ", failures));
            }
            return output;
        }

        private List<Map<String, Object>> extractFeatureScores(Map<String, Object> matchInfo) {
            Map<String, Object> byFeature =
                asMap(matchInfo.get("FEATURE_SCORES"));
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<String, Object> feature : byFeature.entrySet()) {
                List<Map<String, Object>> scores = asMaps(feature.getValue());
                if (scores.isEmpty()) {
                    continue;
                }
                Map<String, Object> score = scores.get(0);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("feature", feature.getKey());
                item.put("score", maybeLong(score.get("SCORE")));
                item.put("label", string(score.get("SCORE_BUCKET")));
                result.add(item);
            }
            return result;
        }

        private Map<String, Object> why(SzEngine engine, String entityId) {
            Long id = maybeLong(entityId);
            if (id == null) {
                return Map.of("error", "invalid entity_id");
            }
            Entity entity = entities.get(id);
            if (entity == null || entity.records.isEmpty()) {
                return Map.of("entity_id", id, "error", "no records known for this entity");
            }
            try {
                Set<SzFlag> flags = withMatchKeyDetails(SZ_WHY_RECORDS_DEFAULT_FLAGS);
                String raw;
                String mode;
                if (entity.records.size() >= 2) {
                    raw = engine.whyRecords(recordKey(entity.records.get(0)), recordKey(entity.records.get(1)), flags);
                    mode = "why_records";
                } else {
                    raw = engine.whyRecordInEntity(recordKey(entity.records.get(0)), flags);
                    mode = "why_record_in_entity";
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("entity_id", id);
                result.put("mode", mode);
                result.put("result", parseJson(raw));
                return result;
            } catch (Exception e) {
                return Map.of("entity_id", id,
                    "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        private Map<String, Object> how(SzEngine engine, String entityId) {
            Long id = maybeLong(entityId);
            if (id == null) {
                return Map.of("error", "invalid entity_id");
            }
            try {
                Set<SzFlag> flags = withMatchKeyDetails(SZ_HOW_ENTITY_DEFAULT_FLAGS);
                String raw = engine.howEntity(id, flags);
                return Map.of("entity_id", id, "result", parseJson(raw));
            } catch (Exception e) {
                return Map.of("entity_id", id,
                    "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        private Set<SzFlag> withMatchKeyDetails(Set<SzFlag> base) {
            Set<SzFlag> result = new HashSet<>(base);
            result.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
            result.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
            return result;
        }

        private SzRecordKey recordKey(Map<String, Object> record) {
            return SzRecordKey.of(
                string(record.get("data_source")), string(record.get("record_id")));
        }

        private List<String> allDataSources() {
            Set<String> result = new TreeSet<>();
            for (Entity entity : entities.values()) {
                result.addAll(entity.dataSources);
            }
            return new ArrayList<>(result);
        }

        private List<String> sourcesFromRecords(List<Map<String, Object>> records) {
            Set<String> sources = new TreeSet<>();
            for (Map<String, Object> record : records) {
                String source = string(record.get("DATA_SOURCE"));
                if (!source.isBlank()) {
                    sources.add(source);
                }
            }
            return new ArrayList<>(sources);
        }

        private List<String> colorKeys() {
            Set<String> singles = new TreeSet<>(allDataSources());
            Set<String> combinations = new TreeSet<>();
            for (Entity entity : entities.values()) {
                if (entity.dataSources.size() >= 2) {
                    combinations.add(String.join(SOURCE_KEY_SEPARATOR, entity.dataSources));
                }
            }
            List<String> keys = new ArrayList<>(singles);
            keys.addAll(combinations);
            return keys;
        }
    }

    private static Set<SzFlag> withMatchKeyDetails(Set<SzFlag> base) {
        Set<SzFlag> result = new HashSet<>(base);
        result.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        result.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
        return result;
    }

    private static final class Entity {
        private final long id;
        private final String name;
        private final List<String> dataSources;
        private final List<Map<String, Object>> records;
        private final List<Map<String, Object>> rules;

        private Entity(
            long id, String name, List<String> dataSources,
            List<Map<String, Object>> records, List<Map<String, Object>> rules
        ) {
            this.id = id;
            this.name = name;
            this.dataSources = dataSources;
            this.records = records;
            this.rules = rules;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entity_id", id);
            result.put("entity_name", name);
            result.put("record_count", records.size());
            result.put("data_sources", dataSources);
            result.put("records", records);
            return result;
        }
    }

    private static final class Edge {
        private final long source;
        private final long target;
        private final String matchKey;
        private final String type;

        private Edge(long source, long target, String matchKey, String type) {
            this.source = source;
            this.target = target;
            this.matchKey = matchKey;
            this.type = type;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source_entity_id", source);
            result.put("target_entity_id", target);
            result.put("match_key", matchKey);
            result.put("relationship_type", type);
            return result;
        }
    }

    private static String relationshipType(Map<String, Object> relation) {
        if (asLong(relation.get("IS_DISCLOSED")) == 1) {
            return "disclosed";
        }
        if (asLong(relation.get("IS_AMBIGUOUS")) == 1) {
            return "ambiguous";
        }
        return "POSSIBLY_SAME".equals(string(relation.get("MATCH_LEVEL_CODE")))
            ? "possibly_same" : "possibly_related";
    }

    private static int relationshipPriority(String type) {
        return switch (type) {
            case "ambiguous" -> 3;
            case "disclosed" -> 2;
            case "possibly_same" -> 1;
            default -> 0;
        };
    }

    private static Map<String, Object> colorForSources(List<String> sourceKeys) {
        List<String> codes = sourceKeys.stream().filter(s -> s != null && !s.isBlank())
            .distinct().sorted().toList();
        Map<String, String> preferred = new HashMap<>();
        Set<String> claimed = new HashSet<>();
        for (String code : codes) {
            String color = PREFERRED_SOURCE_COLORS.get(code);
            if (color != null) {
                preferred.put(code, color);
                claimed.add(color);
            }
        }
        List<String> available = FALLBACK_COLORS.stream()
            .filter(color -> !claimed.contains(color)).toList();
        if (available.isEmpty()) {
            available = FALLBACK_COLORS;
        }
        int fallbackCount = codes.size() - preferred.size();
        int capacity = Math.min(
            SOURCE_ENCODING_CAPACITY,
            available.size() * SOURCE_STROKE_STATES * SOURCE_FILL_SHADES.size());
        if (fallbackCount > capacity) {
            System.err.printf(
                "WARNING: %d source-set keys exceed the %d distinct rendered color encodings; "
                    + "some colors will repeat.%n",
                fallbackCount, capacity);
        }
        Map<String, Object> assigned = new LinkedHashMap<>();
        Set<String> rendered = new HashSet<>();
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
                double shade = SOURCE_FILL_SHADES.get(
                    (cycle / SOURCE_STROKE_STATES) % SOURCE_FILL_SHADES.size());
                fill = shadeFill(fill, shade);
                nth++;
            }
            int slot = cycle % SOURCE_STROKE_STATES;
            String stroke = SOURCE_STROKES.get(0);
            Double width = null;
            if (slot != 0) {
                int k = slot - 1;
                stroke = SOURCE_STROKES.get((k + 1) % SOURCE_STROKES.size());
                width = SOURCE_STROKE_WIDTHS.get(k / SOURCE_STROKES.size());
            }
            Map<String, Object> style = new LinkedHashMap<>();
            style.put("fill", fill);
            style.put("stroke", stroke);
            style.put("stroke_width", width);
            style.put("cycle", cycle);
            String renderedKey = fill + "|"
                + (width == null ? "none" : stroke + "|" + width);
            if (!rendered.add(renderedKey) && fallbackCount <= capacity) {
                throw new IllegalStateException(
                    "Distinct source-set keys received the same rendered color encoding: " + code);
            }
            assigned.put(code, style);
        }
        return assigned;
    }

    private static String shadeFill(String fill, double factor) {
        if (factor == 0) {
            return fill;
        }
        String target = factor > 0 ? "#FFFFFF" : "#18160F";
        int[] sourceRgb = rgb(fill);
        int[] targetRgb = rgb(target);
        double weight = Math.abs(factor);
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = (int) Math.round(sourceRgb[i] + (targetRgb[i] - sourceRgb[i]) * weight);
        }
        return String.format("#%02X%02X%02X", result[0], result[1], result[2]);
    }

    private static int[] rgb(String hex) {
        return new int[] {
            Integer.parseInt(hex.substring(1, 3), 16),
            Integer.parseInt(hex.substring(3, 5), 16),
            Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    private static final class Options {
        private String title = "Senzing Entity Resolution";
        private String dataset = "";
        private int port = 8080;
        private int expectedRecords;
        private boolean noServe;
        private Path assets = Path.of("src/server");
        private Path snapshot;
        private Path stateFile = Path.of("data/temp/truthset-viz-server.json");
        private final List<Path> records = new ArrayList<>();

        private static Options parse(String[] args) {
            Options result = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--title" -> result.title = requireValue(args, ++i, arg);
                    case "--dataset" -> result.dataset = requireValue(args, ++i, arg);
                    case "--port" -> result.port = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--expected-records" ->
                        result.expectedRecords = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--assets" -> result.assets = Path.of(requireValue(args, ++i, arg));
                    case "--snapshot" -> result.snapshot = Path.of(requireValue(args, ++i, arg));
                    case "--state" -> result.stateFile = Path.of(requireValue(args, ++i, arg));
                    case "--no-serve" -> result.noServe = true;
                    case "--records" -> {
                        while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                            result.records.add(Path.of(args[++i]));
                        }
                        if (result.records.isEmpty()) {
                            throw new IllegalArgumentException("--records requires at least one JSONL path");
                        }
                    }
                    case "--help", "-h" -> {
                        System.out.println("Usage: TruthSetVizServer [--records file.jsonl ...] "
                            + "[--title text] [--dataset text] [--port number] "
                            + "[--snapshot path] [--no-serve] [--assets path] [--state path]");
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            if (result.port < 1 || result.port > 65535) {
                throw new IllegalArgumentException("--port must be between 1 and 65535");
            }
            return result;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        return Options.requireValue(args, index, option);
    }

    private static Object parseJson(String raw) {
        try (var reader = Json.createReader(new StringReader(raw))) {
            return toJava(reader.readValue());
        }
    }

    private static Object toJava(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> {
                Map<String, Object> result = new LinkedHashMap<>();
                value.asJsonObject().forEach((key, child) -> result.put(key, toJava(child)));
                yield result;
            }
            case ARRAY -> {
                List<Object> result = new ArrayList<>();
                value.asJsonArray().forEach(child -> result.add(toJava(child)));
                yield result;
            }
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> {
                JsonNumber number = (JsonNumber) value;
                yield number.isIntegral() ? number.longValue() : number.doubleValue();
            }
            case TRUE -> true;
            case FALSE -> false;
            case NULL -> null;
        };
    }

    private static String jsonString(Object value) {
        JsonValue jsonValue = toJsonValue(value);
        if (!(jsonValue instanceof JsonStructure structure)) {
            throw new IllegalArgumentException("Only JSON objects and arrays can be serialized here");
        }
        StringWriter output = new StringWriter();
        try (JsonWriter writer = Json.createWriter(output)) {
            writer.write(structure);
        }
        return output.toString();
    }

    private static JsonValue toJsonValue(Object value) {
        if (value == null || value == JsonValue.NULL) {
            return JsonValue.NULL;
        }
        if (value instanceof JsonValue jsonValue) {
            return jsonValue;
        }
        if (value instanceof String text) {
            return Json.createValue(text);
        }
        if (value instanceof Boolean bool) {
            return bool ? JsonValue.TRUE : JsonValue.FALSE;
        }
        if (value instanceof Integer number) {
            return Json.createValue(number);
        }
        if (value instanceof Long number) {
            return Json.createValue(number);
        }
        if (value instanceof Double number) {
            return Json.createValue(number);
        }
        if (value instanceof Number number) {
            return Json.createValue(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                builder.add(string(entry.getKey()), toJsonValue(entry.getValue()));
            }
            return builder.build();
        }
        if (value instanceof Iterable<?> values) {
            JsonArrayBuilder builder = Json.createArrayBuilder();
            for (Object child : values) {
                builder.add(toJsonValue(child));
            }
            return builder.build();
        }
        if (value.getClass().isArray()) {
            JsonArrayBuilder builder = Json.createArrayBuilder();
            for (Object child : (Object[]) value) {
                builder.add(toJsonValue(child));
            }
            return builder.build();
        }
        return Json.createValue(string(value));
    }

    private static String scriptSafeJson(Object value) {
        return jsonString(value)
            .replace("&", "\\u0026")
            .replace("<", "\\u003c")
            .replace(">", "\\u003e");
    }

    private static String htmlEscape(Object value) {
        return string(value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static List<Map<String, Object>> asMaps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object child : asList(value)) {
            if (child instanceof Map<?, ?>) {
                result.add(asMap(child));
            }
        }
        return result;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long asLong(Object value) {
        Long result = maybeLong(value);
        return result == null ? 0 : result;
    }

    private static Long maybeLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
