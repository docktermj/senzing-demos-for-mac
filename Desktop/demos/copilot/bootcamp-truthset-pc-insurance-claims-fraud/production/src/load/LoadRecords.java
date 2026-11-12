/*
 * Adapted from the MCP-provided senzing/code-snippets-v4 loading scaffold
 * (java/snippets/loading/LoadViaFutures.java). JSON-P parsing was replaced
 * with the project's dependency-free JsonSupport; the Senzing SDK calls are
 * preserved. SQLite writes use one worker because SQLite does not support
 * concurrent writes. For PostgreSQL, tune the pool to the MCP guidance of
 * 2-8 workers per CPU core.
 */
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

public class LoadRecords {
    private static final String DEFAULT_FILE_PATH = "data/senzing-ready/VENDOR_REGISTRY.jsonl";
    private static final int SQLITE_WRITE_THREAD_COUNT = 1;
    private static final int BATCH_SIZE = 100;
    private static final int PROGRESS_INTERVAL = 100;
    private static final String UTF_8 = StandardCharsets.UTF_8.name();
    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final String RECORD_ID = "RECORD_ID";
    private static final Path ERROR_LOG_PATH = Path.of("logs", "loading_errors.json");
    private static final Path RETRY_LOG_PATH = Path.of("logs", "loading_retries.jsonl");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception exception) {
            System.err.println("Loading failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        LoadOptions options = parseOptions(args);
        Path inputPath = Path.of(options.filePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(inputPath)) {
            throw new IOException("Input file does not exist: " + inputPath);
        }

        String inputHash = sha256(inputPath);
        Path checkpointPath = checkpointPath(inputPath);
        long startLine = options.resume()
                ? readCheckpoint(checkpointPath, inputPath, inputHash)
                : 0;
        if (!options.resume()) {
            writeCheckpoint(checkpointPath, inputPath, inputHash, 0);
        }

        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalArgumentException("SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        long startedAt = System.nanoTime();
        long attempted = 0;
        long loaded = 0;
        long failed = 0;
        long retryable = 0;

        try (ErrorLog errorLog = new ErrorLog(ERROR_LOG_PATH)) {
            SzEnvironment environment = SzCoreEnvironment.newBuilder()
                    .settings(settings)
                    .instanceName(LoadRecords.class.getSimpleName())
                    .verboseLogging(false)
                    .build();
            ExecutorService executor = Executors.newFixedThreadPool(SQLITE_WRITE_THREAD_COUNT);
            try {
                SzEngine engine = environment.getEngine();
                List<PendingRecord> batch = new ArrayList<>(BATCH_SIZE);
                long lineNumber = 0;
                long selectedRecords = 0;

                try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
                    String line;
                    while (options.limit() == 0 || selectedRecords < options.limit()) {
                        line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        lineNumber++;
                        if (lineNumber <= startLine) {
                            continue;
                        }
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        selectedRecords++;
                        attempted++;
                        Record record;
                        try {
                            Map<String, Object> recordJson = JsonSupport.object(
                                    JsonSupport.parse(line), "record at line " + lineNumber);
                            String dataSourceCode = requireString(recordJson, DATA_SOURCE, lineNumber);
                            String recordId = requireString(recordJson, RECORD_ID, lineNumber);
                            record = new Record(lineNumber, dataSourceCode, recordId, line);
                        } catch (RuntimeException exception) {
                            failed++;
                            errorLog.write(new Record(lineNumber, null, null, line), exception, "INVALID_RECORD");
                            if (attempted % PROGRESS_INTERVAL == 0) {
                                printProgress(attempted, loaded, failed, startedAt);
                            }
                            continue;
                        }

                        Future<?> future = executor.submit(() -> {
                            SzRecordKey recordKey = SzRecordKey.of(record.dataSourceCode(), record.recordId());
                            engine.addRecord(recordKey, record.line(), SZ_NO_FLAGS);
                            return null;
                        });
                        batch.add(new PendingRecord(record, future));

                        if (attempted % BATCH_SIZE == 0) {
                            BatchCounts counts = processBatch(batch, errorLog);
                            loaded += counts.loaded();
                            failed += counts.failed();
                            retryable += counts.retryable();
                            batch.clear();
                            writeCheckpoint(checkpointPath, inputPath, inputHash, lineNumber);
                            printProgress(attempted, loaded, failed, startedAt);
                        }
                    }
                }

                BatchCounts counts = processBatch(batch, errorLog);
                loaded += counts.loaded();
                failed += counts.failed();
                retryable += counts.retryable();
                writeCheckpoint(checkpointPath, inputPath, inputHash, lineNumber);
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException exception) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                } finally {
                    environment.destroy();
                }
            }
        }

        double elapsedSeconds = elapsedSeconds(startedAt);
        double throughput = loaded / elapsedSeconds;
        System.out.printf(
                "Load complete: attempted=%d loaded=%d failed=%d retryable=%d duration=%.2fs throughput=%.2f records/s%n",
                attempted, loaded, failed, retryable, elapsedSeconds, throughput);
        System.out.println("Error details: " + ERROR_LOG_PATH);
        System.out.println("Resume checkpoint: " + checkpointPath);
    }

    private static BatchCounts processBatch(List<PendingRecord> batch, ErrorLog errorLog) throws Exception {
        long loaded = 0;
        long failed = 0;
        long retryable = 0;
        for (PendingRecord pending : batch) {
            try {
                pending.future().get();
                loaded++;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (ExecutionException | CancellationException exception) {
                Throwable cause = exception instanceof ExecutionException
                        ? ((ExecutionException) exception).getCause()
                        : exception;
                if (cause instanceof SzBadInputException || cause instanceof SzRetryableException
                        || cause instanceof CancellationException) {
                    Exception recordError = (Exception) cause;
                    boolean canRetry = cause instanceof SzRetryableException
                            || cause instanceof CancellationException;
                    errorLog.write(pending.record(), recordError, canRetry ? "RETRYABLE" : "BAD_INPUT");
                    if (canRetry) {
                        appendRetryRecord(pending.record());
                        retryable++;
                    }
                    failed++;
                } else if (cause instanceof Exception recordError) {
                    errorLog.write(pending.record(), recordError, "CRITICAL");
                    throw recordError;
                } else {
                    throw new RuntimeException("Unexpected loading failure.", cause);
                }
            }
        }
        return new BatchCounts(loaded, failed, retryable);
    }

    private static String requireString(Map<String, Object> object, String field, long lineNumber) {
        Object value = object.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Required string field " + field + " is missing or empty at line " + lineNumber + ".");
        }
        return text;
    }

    private static void appendRetryRecord(Record record) throws IOException {
        Files.createDirectories(RETRY_LOG_PATH.getParent());
        Files.writeString(
                RETRY_LOG_PATH,
                record.line() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static void printProgress(long attempted, long loaded, long failed, long startedAt) {
        double elapsedSeconds = elapsedSeconds(startedAt);
        System.out.printf(
                "Progress: attempted=%d loaded=%d failed=%d elapsed=%.2fs throughput=%.2f records/s%n",
                attempted, loaded, failed, elapsedSeconds, loaded / elapsedSeconds);
    }

    private static double elapsedSeconds(long startedAt) {
        return Math.max((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.001);
    }

    private static Path checkpointPath(Path inputPath) {
        String fileName = inputPath.getFileName().toString();
        String sourceName = fileName.replaceFirst("\\.jsonl?$", "");
        return Path.of("logs", sourceName + ".checkpoint.json");
    }

    private static String sha256(Path inputPath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(inputPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static long readCheckpoint(Path checkpointPath, Path inputPath, String inputHash) throws IOException {
        if (!Files.isRegularFile(checkpointPath)) {
            throw new IOException("Cannot resume: checkpoint not found at " + checkpointPath);
        }
        Map<String, Object> checkpoint = JsonSupport.object(
                JsonSupport.parse(Files.readString(checkpointPath, StandardCharsets.UTF_8)),
                "checkpoint");
        if (!inputPath.toString().equals(checkpoint.get("input_path"))
                || !inputHash.equals(checkpoint.get("input_sha256"))) {
            throw new IOException("Cannot resume: input path or content differs from the checkpoint.");
        }
        Object lastLineValue = checkpoint.get("last_line");
        if (!(lastLineValue instanceof java.math.BigDecimal lastLine)) {
            throw new IOException("Cannot resume: checkpoint has no valid last_line.");
        }
        try {
            return lastLine.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IOException("Cannot resume: checkpoint last_line is not an integer.", exception);
        }
    }

    private static void writeCheckpoint(
            Path checkpointPath, Path inputPath, String inputHash, long lastLine) throws IOException {
        Files.createDirectories(checkpointPath.getParent());
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("input_path", inputPath.toString());
        checkpoint.put("input_sha256", inputHash);
        checkpoint.put("last_line", lastLine);
        Path temporaryPath = checkpointPath.resolveSibling(checkpointPath.getFileName() + ".tmp");
        Files.writeString(
                temporaryPath,
                JsonSupport.stringify(checkpoint) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporaryPath,
                    checkpointPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, checkpointPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static LoadOptions parseOptions(String[] args) {
        String filePath = DEFAULT_FILE_PATH;
        long limit = 0;
        boolean resume = false;
        int index = 0;
        if (args.length > 0 && !args[0].startsWith("--")) {
            filePath = args[0];
            index = 1;
        }
        while (index < args.length) {
            String option = args[index++];
            switch (option) {
                case "--resume" -> resume = true;
                case "--limit" -> {
                    if (index >= args.length) {
                        throw new IllegalArgumentException("--limit requires a positive record count.");
                    }
                    limit = Long.parseLong(args[index++]);
                    if (limit <= 0) {
                        throw new IllegalArgumentException("--limit must be greater than zero.");
                    }
                }
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        return new LoadOptions(filePath, limit, resume);
    }

    private record LoadOptions(String filePath, long limit, boolean resume) {
    }

    private record Record(long lineNumber, String dataSourceCode, String recordId, String line) {
    }

    private record PendingRecord(Record record, Future<?> future) {
    }

    private record BatchCounts(long loaded, long failed, long retryable) {
    }

    private static final class ErrorLog implements AutoCloseable {
        private final BufferedWriter writer;
        private boolean first = true;

        private ErrorLog(Path path) throws IOException {
            Files.createDirectories(path.getParent());
            writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            writer.write("[");
        }

        private void write(Record record, Exception exception, String category) throws IOException {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("line_number", record.lineNumber());
            entry.put("data_source", record.dataSourceCode());
            entry.put("record_id", record.recordId());
            entry.put("category", category);
            entry.put("error_code", errorCode(exception));
            entry.put("message", exception.getMessage());
            if (!first) {
                writer.write(",");
            }
            writer.newLine();
            writer.write("  " + JsonSupport.stringify(entry));
            writer.flush();
            first = false;
        }

        private String errorCode(Exception exception) {
            if (exception instanceof SzException senzingException) {
                return String.valueOf(senzingException.getErrorCode());
            }
            return exception.getClass().getSimpleName();
        }

        @Override
        public void close() throws IOException {
            writer.newLine();
            writer.write("]");
            writer.newLine();
            writer.close();
        }
    }
}
