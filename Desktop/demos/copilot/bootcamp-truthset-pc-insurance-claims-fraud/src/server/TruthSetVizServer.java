import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.core.SzCoreEnvironment;

public final class TruthSetVizServer {
    private static final String DEFAULT_TITLE = "Senzing Truth Set";
    private static final String DEFAULT_DATASET = "the Senzing Truth Set";
    private static final Path DEFAULT_RECORDS = Path.of("src/system_verification/truthset_data.jsonl");
    private static final Path DEFAULT_SNAPSHOT = Path.of("docs/visualizations/truthset_verification.html");
    private static final Path DEFAULT_D3 = Path.of("src/server/d3.v7.min.js");

    private TruthSetVizServer() {}

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        String d3 = readD3(options.d3Path);
        String pageTemplate = TruthSetPage.template();
        if (pageTemplate.isBlank()) {
            throw new IllegalStateException("The generated page template is empty.");
        }

        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalStateException("SENZING_ENGINE_CONFIGURATION_JSON is not set; source src/scripts/senzing-env.sh first.");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName(TruthSetVizServer.class.getSimpleName())
                .verboseLogging(false)
                .build();

        HttpServer server = null;
        ExecutorService executor = null;
        CountDownLatch stopped = new CountDownLatch(1);
        try {
            TruthSetModel model = options.recordsPath == null
                    ? TruthSetModel.fromExport(environment.getEngine())
                    : TruthSetModel.fromRecords(environment.getEngine(), options.recordsPath);
            JsonObject offlineData = model.offlineData();

            if (options.snapshotPath != null) {
                writeSnapshot(options.snapshotPath, pageTemplate, d3, options, offlineData);
                System.out.println("Snapshot written: " + options.snapshotPath);
            }
            if (options.noServe) {
                environment.destroy();
                return;
            }

            String nonce = makeNonce();
            BoundServer bound = bind(options.port);
            server = bound.server;
            executor = Executors.newFixedThreadPool(4);
            server.setExecutor(executor);
            final HttpServer runningServer = server;
            final ExecutorService runningExecutor = executor;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                runningServer.stop(0);
                runningExecutor.shutdownNow();
                environment.destroy();
                stopped.countDown();
            }, "truthset-viz-shutdown"));
            registerRoutes(server, model, options, pageTemplate, d3, nonce, offlineData);
            server.start();
            System.out.printf("Truth Set visualization listening at http://localhost:%d (pid %d)%n",
                    server.getAddress().getPort(), ProcessHandle.current().pid());
            System.out.println("Startup nonce: " + nonce);
            if (bound.fallback) {
                System.out.println("Requested port " + options.port + " was unavailable; using " + server.getAddress().getPort() + ".");
            }
            stopped.await();
        } finally {
            if (server != null) {
                server.stop(0);
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            if (!environment.isDestroyed()) {
                environment.destroy();
            }
        }
    }

    private static String readD3(Path d3Path) throws IOException {
        if (!Files.isRegularFile(d3Path) || !Files.isReadable(d3Path) || Files.size(d3Path) == 0) {
            throw new IOException("Required offline asset is missing or unreadable: " + d3Path + " (d3.v7.min.js).");
        }
        return Files.readString(d3Path, StandardCharsets.UTF_8);
    }

    private static void writeSnapshot(Path snapshotPath,
                                      String template,
                                      String d3,
                                      Options options,
                                      JsonObject offlineData) throws IOException {
        Path parent = snapshotPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String html = TruthSetPage.render(template, d3, options.title, options.dataset, offlineData, true, "");
        Files.writeString(snapshotPath, html, StandardCharsets.UTF_8);
    }

    private static void registerRoutes(HttpServer server,
                                       TruthSetModel model,
                                       Options options,
                                       String template,
                                       String d3,
                                       String nonce,
                                       JsonObject offlineData) {
        server.createContext("/", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, error("Only GET is supported."));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                sendHtml(exchange, 200, TruthSetPage.render(
                        template, d3, options.title, options.dataset, Json.createObjectBuilder().build(), false, nonce));
                return;
            }
            if ("/api/health".equals(path)) {
                if (!nonce.equals(query(exchange.getRequestURI()).get("nonce"))) {
                    sendJson(exchange, 403, error("Startup nonce mismatch."));
                    return;
                }
                sendJson(exchange, 200, Json.createObjectBuilder()
                        .add("status", "ok")
                        .add("nonce", nonce)
                        .add("entities_total", model.entityCount())
                        .build());
                return;
            }
            try {
                Map<String, String> query = query(exchange.getRequestURI());
                JsonObject response = switch (path) {
                    case "/api/stats" -> withNonce(model.stats(), nonce);
                    case "/api/graph" -> model.graph();
                    case "/api/merges" -> model.merges();
                    case "/api/records" -> model.records(parseEntityId(query));
                    case "/api/search" -> model.search(query.getOrDefault("q", ""));
                    case "/api/why" -> model.why(parseEntityId(query));
                    case "/api/how" -> model.how(parseEntityId(query));
                    case "/api/overlap" -> model.overlap();
                    case "/api/matchkeys" -> model.matchKeys();
                    case "/api/features" -> model.featureScores();
                    case "/api/examples" -> model.examples();
                    default -> null;
                };
                if (response == null) {
                    sendJson(exchange, 404, error("Not found."));
                } else {
                    sendJson(exchange, 200, response);
                }
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, error(e.getMessage()));
            } catch (Exception e) {
                sendJson(exchange, 500, error(e.getClass().getSimpleName() + ": " + safeMessage(e)));
            }
        });
    }

    private static JsonObject withNonce(JsonObject original, String nonce) {
        JsonObjectBuilder result = Json.createObjectBuilder();
        for (Map.Entry<String, JsonValue> entry : original.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        result.add("nonce", nonce);
        return result.build();
    }

    private static long parseEntityId(Map<String, String> query) {
        String value = query.get("entity_id");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The entity_id query parameter is required.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("entity_id must be an integer.");
        }
    }

    private static JsonObject error(String message) {
        return Json.createObjectBuilder().add("error", message == null ? "Request failed." : message).build();
    }

    private static void sendJson(HttpExchange exchange, int status, JsonObject json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.toString());
    }

    private static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        send(exchange, status, "text/html; charset=utf-8", html);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private static BoundServer bind(int requestedPort) throws IOException {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try {
            return new BoundServer(HttpServer.create(new InetSocketAddress(loopback, requestedPort), 0), false);
        } catch (IOException first) {
            if (requestedPort != 8080) {
                throw first;
            }
            for (int port = 8081; port <= 8090; port++) {
                try {
                    return new BoundServer(HttpServer.create(new InetSocketAddress(loopback, port), 0), true);
                } catch (IOException ignored) {
                    // Try the next loopback port.
                }
            }
            return new BoundServer(HttpServer.create(new InetSocketAddress(loopback, 0), 0), true);
        }
    }

    private static String makeNonce() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "request failed" : message;
    }

    private static final class BoundServer {
        final HttpServer server;
        final boolean fallback;

        BoundServer(HttpServer server, boolean fallback) {
            this.server = server;
            this.fallback = fallback;
        }
    }

    private static final class Options {
        Path recordsPath = DEFAULT_RECORDS;
        Path snapshotPath = DEFAULT_SNAPSHOT;
        Path d3Path = DEFAULT_D3;
        String title = DEFAULT_TITLE;
        String dataset = DEFAULT_DATASET;
        int port = 8080;
        boolean noServe;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String argument = args[i];
                switch (argument) {
                    case "--records" -> options.recordsPath = Path.of(value(args, ++i, argument));
                    case "--snapshot" -> options.snapshotPath = Path.of(value(args, ++i, argument));
                    case "--d3" -> options.d3Path = Path.of(value(args, ++i, argument));
                    case "--title" -> options.title = value(args, ++i, argument);
                    case "--dataset" -> options.dataset = value(args, ++i, argument);
                    case "--port" -> options.port = Integer.parseInt(value(args, ++i, argument));
                    case "--no-serve" -> options.noServe = true;
                    case "--export" -> options.recordsPath = null;
                    case "--help" -> {
                        usage();
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + argument);
                }
            }
            if (options.port < 0 || options.port > 65535) {
                throw new IllegalArgumentException("port must be between 0 and 65535.");
            }
            return options;
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }

        private static void usage() {
            System.out.println("Usage: TruthSetVizServer [--records path | --export] [--title text] [--dataset text]");
            System.out.println("       [--snapshot path] [--d3 path] [--no-serve] [--port number]");
        }
    }
}
