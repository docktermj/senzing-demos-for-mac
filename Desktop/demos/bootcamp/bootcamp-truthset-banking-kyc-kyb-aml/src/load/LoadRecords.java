package loading;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

import com.senzing.sdk.SzBadInputException;
import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.SzRetryableException;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

/*
 * Based on Senzing's MCP-provided LoadViaFutures scaffold. The existing
 * JSON-P dependency is used for robust JSONL parsing and streaming error logs.
 */
public class LoadRecords {
    private static final int THREAD_COUNT = 1;
    private static final int BACKLOG_FACTOR = 10;
    private static final int MAXIMUM_BACKLOG = THREAD_COUNT * BACKLOG_FACTOR;
    private static final int PROGRESS_INTERVAL = 100;
    private static final long PAUSE_MILLIS = 100L;
    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final String RECORD_ID = "RECORD_ID";
    private static final Pattern SENZING_ERROR_CODE = Pattern.compile("\\bSENZ\\d+\\b");
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS");

    /*
     * Medium production was selected as a range, with no exact count. Keep the
     * threaded scaffold pattern but serialize writes for SQLite. One writer is
     * a conservative derivation from SQLite's no-concurrent-write constraint,
     * not a Senzing-published worker count. When moving to PostgreSQL, raise
     * this and tune it; Senzing guidance starts at 2-8 workers per CPU core.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: loading.LoadRecords <senzing-ready-jsonl>");
        }

        Path inputPath = Paths.get(args[0]);
        if (!Files.isRegularFile(inputPath)) {
            throw new IOException("Input file does not exist: " + inputPath);
        }

        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isEmpty()) {
            throw new IllegalArgumentException(
                "SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        Path errorLogPath = Paths.get("logs", "loading_errors.json");
        Files.createDirectories(errorLogPath.getParent());
        archiveExistingErrorLog(errorLogPath);

        Stats stats = new Stats();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        SzEnvironment environment = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(LoadRecords.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try (BufferedWriter errorWriter = Files.newBufferedWriter(
                 errorLogPath, StandardCharsets.UTF_8,
                 StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             JsonGenerator errorLog = Json.createGeneratorFactory(
                 Collections.<String, Object>emptyMap()).createGenerator(errorWriter)) {
            errorLog.writeStartArray();
            try {
                load(inputPath, environment.getEngine(), executor, errorLog, stats);
            } finally {
                errorLog.writeEnd();
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                    System.err.println("Loading workers did not terminate cleanly.");
                }
            }
            environment.destroy();
        }

        printSummary(stats);
        System.out.println("Error log: " + errorLogPath);
    }

    private static void load(
        Path inputPath,
        SzEngine engine,
        ExecutorService executor,
        JsonGenerator errorLog,
        Stats stats
    ) throws Exception {
        Map<Future<?>, Record> pending = new IdentityHashMap<>();
        stats.startedAtNanos = System.nanoTime();

        try (BufferedReader reader = Files.newBufferedReader(
            inputPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                stats.attempted++;
                try {
                    Record record = parseRecord(lineNumber, trimmed);
                    Future<?> future = executor.submit(() -> engine.addRecord(
                        SzRecordKey.of(record.dataSource, record.recordId),
                        record.line,
                        SZ_NO_FLAGS));
                    pending.put(future, record);
                } catch (JsonException | IllegalArgumentException e) {
                    recordFailure(
                        errorLog, new Record(lineNumber, trimmed, null, null),
                        e, false, stats);
                }

                while (pending.size() >= MAXIMUM_BACKLOG) {
                    handlePending(pending, false, errorLog, stats);
                    if (pending.size() >= MAXIMUM_BACKLOG) {
                        Thread.sleep(PAUSE_MILLIS);
                    }
                }
            }
        }

        executor.shutdown();
        handlePending(pending, true, errorLog, stats);
    }

    private static Record parseRecord(int lineNumber, String line) {
        JsonObject json;
        try (javax.json.JsonReader reader = Json.createReader(
            new java.io.StringReader(line))) {
            json = reader.readObject();
        }

        String dataSource = requiredString(json, DATA_SOURCE);
        String recordId = requiredString(json, RECORD_ID);
        return new Record(lineNumber, line, dataSource, recordId);
    }

    private static String requiredString(JsonObject json, String key) {
        JsonValue value = json.get(key);
        if (!(value instanceof JsonString)) {
            throw new IllegalArgumentException(
                "Required JSON string field is missing or invalid: " + key);
        }
        String result = ((JsonString) value).getString();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                "Required JSON string field is empty: " + key);
        }
        return result;
    }

    private static void handlePending(
        Map<Future<?>, Record> pending,
        boolean blocking,
        JsonGenerator errorLog,
        Stats stats
    ) throws Exception {
        Iterator<Map.Entry<Future<?>, Record>> iterator =
            pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Future<?>, Record> entry = iterator.next();
            Future<?> future = entry.getKey();
            Record record = entry.getValue();
            if (!blocking && !future.isDone()) {
                continue;
            }
            iterator.remove();

            try {
                future.get();
                stats.loaded++;
                stats.completed++;
                reportProgressIfDue(stats);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SzBadInputException
                    || cause instanceof SzRetryableException
                    || cause instanceof SzException
                    || cause instanceof IllegalArgumentException
                    || cause instanceof CancellationException) {
                    recordFailure(
                        errorLog, record, (Exception) cause,
                        cause instanceof SzRetryableException, stats);
                } else if (cause instanceof Exception) {
                    throw (Exception) cause;
                } else {
                    throw new RuntimeException("Unexpected loading failure.", cause);
                }
            }
        }
    }

    private static void recordFailure(
        JsonGenerator errorLog,
        Record record,
        Exception exception,
        boolean retryable,
        Stats stats
    ) {
        String errorCode = findErrorCode(exception);
        String message = exception.getMessage();
        if (message == null || message.isEmpty()) {
            message = exception.toString();
        }

        JsonObjectBuilder entry = Json.createObjectBuilder()
            .add("line_number", record.lineNumber);
        addNullableString(entry, "data_source", record.dataSource);
        addNullableString(entry, "record_id", record.recordId);
        addNullableString(entry, "error_code", errorCode);
        entry.add("message", message);
        entry.add("retryable", retryable);
        errorLog.write(entry.build());

        stats.failed++;
        if (retryable) {
            stats.retryableFailures++;
        }
        stats.completed++;
        String summaryKey = errorCode == null ? "unclassified" : errorCode;
        stats.errorsByCode.put(
            summaryKey, stats.errorsByCode.getOrDefault(summaryKey, 0L) + 1L);
        reportProgressIfDue(stats);
    }

    private static void addNullableString(
        JsonObjectBuilder object,
        String key,
        String value
    ) {
        if (value == null) {
            object.add(key, JsonValue.NULL);
        } else {
            object.add(key, value);
        }
    }

    private static String findErrorCode(Exception exception) {
        Matcher matcher = SENZING_ERROR_CODE.matcher(exception.toString());
        return matcher.find() ? matcher.group() : null;
    }

    private static void reportProgressIfDue(Stats stats) {
        if (stats.completed == 0 || stats.completed % PROGRESS_INTERVAL != 0) {
            return;
        }
        double elapsedSeconds = elapsedSeconds(stats);
        double throughput = stats.completed / Math.max(elapsedSeconds, 0.001);
        System.out.printf(
            Locale.ROOT,
            "Progress: attempted=%d loaded=%d failed=%d elapsed=%.1fs rate=%.1f records/s%n",
            stats.attempted, stats.loaded, stats.failed, elapsedSeconds, throughput);
    }

    private static void printSummary(Stats stats) {
        double elapsedSeconds = elapsedSeconds(stats);
        double throughput = stats.completed / Math.max(elapsedSeconds, 0.001);
        System.out.printf(Locale.ROOT, "Total attempted: %d%n", stats.attempted);
        System.out.printf(Locale.ROOT, "Successfully loaded: %d%n", stats.loaded);
        System.out.printf(Locale.ROOT, "Failed: %d%n", stats.failed);
        System.out.printf(Locale.ROOT, "Retryable failures: %d%n", stats.retryableFailures);
        System.out.printf(Locale.ROOT, "Duration: %.1f seconds%n", elapsedSeconds);
        System.out.printf(Locale.ROOT, "Throughput: %.1f records/s%n", throughput);
        System.out.println("Error summary: "
            + (stats.errorsByCode.isEmpty() ? "none" : new TreeMap<>(stats.errorsByCode)));
    }

    private static double elapsedSeconds(Stats stats) {
        if (stats.startedAtNanos == 0L) {
            return 0.0;
        }
        return (System.nanoTime() - stats.startedAtNanos) / 1_000_000_000.0;
    }

    private static void archiveExistingErrorLog(Path errorLogPath) throws IOException {
        if (!Files.exists(errorLogPath)) {
            return;
        }

        String timestamp = LocalDateTime.now().format(ARCHIVE_TIMESTAMP);
        Path archivePath = errorLogPath.resolveSibling(
            "loading_errors-" + timestamp + ".json");
        int suffix = 1;
        while (Files.exists(archivePath)) {
            archivePath = errorLogPath.resolveSibling(
                "loading_errors-" + timestamp + "-" + suffix++ + ".json");
        }
        Files.move(errorLogPath, archivePath, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("Archived prior error log: " + archivePath);
    }

    private static final class Record {
        private final int lineNumber;
        private final String line;
        private final String dataSource;
        private final String recordId;

        private Record(int lineNumber, String line, String dataSource, String recordId) {
            this.lineNumber = lineNumber;
            this.line = line;
            this.dataSource = dataSource;
            this.recordId = recordId;
        }
    }

    private static final class Stats {
        private long attempted;
        private long loaded;
        private long failed;
        private long retryableFailures;
        private long completed;
        private long startedAtNanos;
        private final Map<String, Long> errorsByCode = new HashMap<>();
    }
}
