// Production loading program for the banking Customer 360 / KYC sources (Data processing module).
//
// Source: senzing/code-snippets-v4 java/snippets/loading/LoadViaFutures.java, via
// sdk_guide(topic='load', language='java') with no record_count (2026-11-14), which selected the
// threaded production pattern. Every Senzing call is kept as the snippet has it:
//   SzCoreEnvironment.newBuilder().settings(..).instanceName(..).verboseLogging(false).build()
//   env.getEngine(); SzRecordKey.of(dataSource, recordId); engine.addRecord(key, json, SZ_NO_FLAGS)
//   SzBadInputException -> record error; SzRetryableException -> retry file; anything else -> stop
//   env.destroy() in finally
// Deviations from the snippet, and why:
//   * JSON: the snippet imports javax.json (JSON-P), which is not part of Java SE and is not
//     installed here; records are parsed with the bootcamp's dependency-free src/server/MiniJson.java.
//   * Counters are per-run instance state, not static, so a caller can run sources one at a time.
//   * Failed records are appended to logs/loading_errors.json (one JSON object per line) and
//     retryable records to logs/retry_<DATA_SOURCE>.jsonl, instead of stderr and a temp file.
//   * Progress with throughput every N records, a final statistics block, and an optional
//     --limit N (load exactly the first N records, for a test load or a recorded subset).
//
// VOLUME TIER: medium production (more than 500,000 up to 10,000,000 records). The tier came from a
// range, not a count, so no record count was given. Architecture for that tier: a thread pool with
// a bounded backlog of pending futures, which is what this program is.
//
// CONCURRENCY FOR THIS DATASTORE: the database is SQLite, so THREAD_COUNT defaults to 1 (writes
// serialized). This is a DERIVATION, not a served figure: Senzing's guidance states SQLite "does not
// support concurrent writes" and lists "database locked errors under concurrent access", and says to
// tune workers "based on your database and storage throughput"; no Senzing source gives a SQLite
// worker count. On PostgreSQL (or another supported RDBMS) raise it with --threads to Senzing's
// recommended starting point of 2-8 workers per CPU core, then tune.
//
// Senzing also recommends shuffling input files before loading (sorted input causes lock
// contention) and draining the redo queue after loading (see process_redo.java).
//
// Build:  javac -cp "$SZ_SDK_JAR" -d data/temp/load_classes src/server/MiniJson.java src/load/*.java
// Run:    src/scripts/run-java.sh data/temp/load_classes LoadSource <file.jsonl> [--limit N]
//             [--threads N] [--progress N]
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.senzing.sdk.SzFlag.*;

class LoadSource {
    static final int DEFAULT_THREAD_COUNT = 1;     // SQLite: serialized writes (see header)
    static final int BACKLOG_FACTOR = 10;
    static final long PAUSE_TIMEOUT = 100L;
    static final Path ERROR_LOG = Paths.get("logs", "loading_errors.json");

    record Record(int lineNumber, String line, String dataSource, String recordId) { }

    private final int threadCount;
    private final int progressEvery;
    private int attempted = 0, loaded = 0, failed = 0, retries = 0, unreadable = 0;
    private final Map<String, Integer> loadedBySource = new TreeMap<>();
    private final Map<String, Integer> errorsByType = new TreeMap<>();
    private PrintWriter retryWriter = null;
    private Path retryFile = null;
    private long startNanos;

    LoadSource(int threadCount, int progressEvery) {
        this.threadCount = threadCount;
        this.progressEvery = progressEvery;
    }

    public static void main(String[] args) throws Exception {
        String file = null;
        int limit = Integer.MAX_VALUE, threads = DEFAULT_THREAD_COUNT, progress = 50;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--limit": limit = Integer.parseInt(args[++i]); break;
                case "--threads": threads = Integer.parseInt(args[++i]); break;
                case "--progress": progress = Integer.parseInt(args[++i]); break;
                default: file = args[i];
            }
        }
        if (file == null) {
            System.err.println("usage: LoadSource <file.jsonl> [--limit N] [--threads N] [--progress N]");
            System.exit(64);
        }
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        int exit = new LoadSource(threads, progress).run(settings, Paths.get(file), limit);
        System.exit(exit);
    }

    /** Loads up to {@code limit} records from {@code file}; returns 0 when every record loaded. */
    int run(String settings, Path file, int limit) throws Exception {
        // initialize the Senzing environment (once per process, shared by all worker threads)
        SzEnvironment env = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName(LoadSource.class.getSimpleName())
                .verboseLogging(false)
                .build();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Map<Future<?>, Record> pendingFutures = new IdentityHashMap<>();
        int maximumBacklog = threadCount * BACKLOG_FACTOR;
        Files.createDirectories(ERROR_LOG.getParent());
        startNanos = System.nanoTime();
        System.out.println("Loading " + file + (limit == Integer.MAX_VALUE ? "" : " (first " + limit + " records)")
                + " with " + threadCount + " worker thread(s)");
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SzEngine engine = env.getEngine();
            int lineNumber = 0;
            boolean eof = false;
            while (!eof) {
                while (pendingFutures.size() < maximumBacklog) {
                    if (attempted + unreadable >= limit) { eof = true; break; }
                    String line = br.readLine();
                    lineNumber++;
                    if (line == null) { eof = true; break; }
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String dataSource, recordId;
                    try {
                        Map<String, Object> json = MiniJson.obj(MiniJson.parse(line));
                        dataSource = MiniJson.str(json.get("DATA_SOURCE"));
                        recordId = MiniJson.str(json.get("RECORD_ID"));
                    } catch (RuntimeException e) {
                        unreadable++;
                        logFailedRecord("ERROR", e, lineNumber, null, null, line);
                        continue;
                    }
                    Record record = new Record(lineNumber, line, dataSource, recordId);
                    SzRecordKey recordKey = SzRecordKey.of(dataSource, recordId);
                    attempted++;
                    Future<?> future = executor.submit(() -> {
                        // call the addRecord() function with no flags
                        engine.addRecord(recordKey, record.line(), SZ_NO_FLAGS);
                        return null;
                    });
                    pendingFutures.put(future, record);
                }
                do {
                    handlePendingFutures(pendingFutures, false);
                    if (pendingFutures.size() >= maximumBacklog) {
                        try { Thread.sleep(PAUSE_TIMEOUT); } catch (InterruptedException ignore) { }
                    }
                } while (pendingFutures.size() >= maximumBacklog);
            }
            executor.shutdown();
            handlePendingFutures(pendingFutures, true);
        } catch (Exception e) {
            System.err.println();
            System.err.println("*** Terminated due to critical error ***");
            System.err.flush();
            throw e;
        } finally {
            if (!executor.isShutdown()) executor.shutdown();
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
            if (retryWriter != null) retryWriter.close();
            printStatistics(file);
        }
        return (failed == 0 && unreadable == 0) ? 0 : 1;
    }

    private void handlePendingFutures(Map<Future<?>, Record> pendingFutures, boolean blocking) throws Exception {
        Iterator<Map.Entry<Future<?>, Record>> iter = pendingFutures.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Future<?>, Record> entry = iter.next();
            Future<?> future = entry.getKey();
            Record record = entry.getValue();
            if (!blocking && !future.isDone()) continue;
            iter.remove();
            try {
                try {
                    future.get();
                    loaded++;
                    loadedBySource.merge(record.dataSource(), 1, Integer::sum);
                    if (progressEvery > 0 && loaded % progressEvery == 0) printProgress();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (!(cause instanceof Exception)) throw e;
                    throw (Exception) cause;
                }
            } catch (SzBadInputException e) {
                logFailedRecord("ERROR", e, record.lineNumber(), record.dataSource(), record.recordId(), record.line());
                failed++;
            } catch (SzRetryableException | InterruptedException | CancellationException e) {
                logFailedRecord("WARNING", e, record.lineNumber(), record.dataSource(), record.recordId(), record.line());
                failed++;
                retries++;
                trackRetry(record);
            } catch (Exception e) {
                // any other exception (incl. SzException) is critical: log it and stop the load
                logFailedRecord("CRITICAL", e, record.lineNumber(), record.dataSource(), record.recordId(), record.line());
                failed++;
                throw e;
            }
        }
    }

    private void trackRetry(Record record) throws IOException {
        if (retryWriter == null) {
            retryFile = Paths.get("logs", "retry_" + record.dataSource() + ".jsonl");
            retryWriter = new PrintWriter(Files.newBufferedWriter(retryFile, StandardCharsets.UTF_8));
        }
        retryWriter.println(record.line());
    }

    private synchronized void logFailedRecord(String errorType, Exception e, int lineNumber,
                                              String dataSource, String recordId, String line) {
        errorsByType.merge(errorType + " " + e.getClass().getSimpleName(), 1, Integer::sum);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("severity", errorType);
        entry.put("data_source", dataSource);
        entry.put("record_id", recordId);
        entry.put("line_number", lineNumber);
        entry.put("error_code", (e instanceof SzException) ? (Object) ((SzException) e).getErrorCode() : null);
        entry.put("message", String.valueOf(e.getMessage()));
        entry.put("record", line);
        try (Writer w = Files.newBufferedWriter(ERROR_LOG, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(MiniJson.write(entry));
            w.write(System.lineSeparator());
        } catch (IOException io) {
            System.err.println("could not write " + ERROR_LOG + ": " + io.getMessage());
        }
        System.err.println("** " + errorType + " ** line " + lineNumber + " " + dataSource + "/" + recordId + ": " + e);
    }

    private double elapsedSeconds() { return (System.nanoTime() - startNanos) / 1e9; }

    private void printProgress() {
        double s = elapsedSeconds();
        System.out.printf("  %,d loaded, %,d failed, %.1fs elapsed, %.1f records/s%n",
                loaded, failed, s, s > 0 ? loaded / s : 0.0);
    }

    private void printStatistics(Path file) {
        double s = elapsedSeconds();
        System.out.println();
        System.out.println("Load statistics for " + file);
        System.out.println("  Records attempted   : " + attempted);
        System.out.println("  Records loaded      : " + loaded);
        System.out.println("  Records failed      : " + failed + (retries > 0 ? " (" + retries + " retryable, in " + retryFile + ")" : ""));
        System.out.println("  Unreadable lines    : " + unreadable);
        System.out.printf("  Duration            : %.2fs%n", s);
        System.out.printf("  Throughput          : %.1f records/s%n", s > 0 ? loaded / s : 0.0);
        System.out.println("  Loaded by source    : " + loadedBySource);
        System.out.println("  Errors by type      : " + (errorsByType.isEmpty() ? "none" : errorsByType + " (see " + ERROR_LOG + ")"));
        System.out.println("SUMMARY attempted=" + attempted + " loaded=" + loaded + " failed=" + failed
                + " unreadable=" + unreadable + " sources=" + loadedBySource);
    }
}
