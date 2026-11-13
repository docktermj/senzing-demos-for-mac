// RecordLoader.java -- Data processing, Step 3: the shared loading engine behind the production loading
// program (src/load/load_customer_data.java) and the multi-source orchestrator (src/load/orchestrator.java).
// It is a shared class, so it lives in a file named after it (bootcamp file-placement rule INV-237).
//
// PRODUCTION TIER: medium (more than 500,000 up to 10,000,000 records). The tier came from a range
// (option 3); no record count was given, so the build follows the range. Architecture for that tier:
// a thread pool sharing ONE engine (the engine is thread-safe and is initialized once per process),
// a bounded backlog of in-flight records, per-record error logging, a retry file, and throughput
// reporting.
//
// CONCURRENCY FOR THIS DATASTORE: the bootcamp repository is SQLite, which the Senzing MCP server's
// anti-patterns guidance says "does not support concurrent writes" (database-locked errors under
// concurrent access). So this copy runs the threaded pattern with ONE worker by default. That
// single-writer figure is a DERIVATION from that property, not a number the server documents. When
// you move to PostgreSQL (recommended at medium production scale), raise it with --threads: the same
// guidance says to start with 2-8 workers per CPU core and tune to your database and storage
// throughput (the scaffold's own default is 8).
//
// Source: senzing/code-snippets-v4 java/snippets/loading/LoadViaFutures.java (via
// sdk_guide topic='load', language='java'; threaded production pattern). DEPARTURES, and nothing else:
//  1. javax.json is not in Java SE and the SDK does not provide it, so the JSON parsing uses the
//     shared dependency-free reader (src/server/Json.java). Every Senzing SDK call (SzCoreEnvironment
//     builder, getEngine, SzRecordKey.of, addRecord(key, json, SZ_NO_FLAGS), destroy) and every
//     exception class handled (SzBadInputException, SzRetryableException) is as the scaffold has it.
//  2. Counters live in a per-load Stats object instead of static fields, so a caller that loads
//     several sources in one process (the orchestrator) gets a fresh tally per source.
//  3. Production features the bootcamp requires: failed records are written as JSON lines to
//     logs/loading_errors.json (record ID, error type, SENZ code, message) without stopping the load;
//     retryable records go to logs/retry-<file>.jsonl instead of a temp file; progress every 1,000
//     records with throughput; a completion summary; --limit N loads exactly the first N records.
//  4. Split into this shared class (the loading loop) and the command-line program
//     src/load/load_customer_data.java (environment setup and arguments), so the orchestrator can reuse it.
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

final class RecordLoader {
    /** One source line in flight. */
    record Record(int lineNumber, String recordId, String line) {}

    /** Per-load tally; a new one for every file loaded. */
    static final class Stats {
        final String file;
        long attempted, success, errors, retries, elapsedMillis;
        Stats(String file) { this.file = file; }
        double perSecond() { return elapsedMillis == 0 ? 0 : 1000.0 * success / elapsedMillis; }
        @Override public String toString() {
            return String.format("%s: attempted=%d loaded=%d failed=%d retry=%d elapsed=%.1fs rate=%.1f rec/s",
                file, attempted, success, errors, retries, elapsedMillis / 1000.0, perSecond());
        }
    }

    static final int DEFAULT_THREAD_COUNT = 1;   // SQLite: single writer (see header)
    static final int BACKLOG_FACTOR = 10;
    static final long PAUSE_TIMEOUT = 100L;
    static final int PROGRESS_EVERY = 1000;
    static final Path ERROR_LOG = Path.of("logs", "loading_errors.json");

    final SzEngine engine;
    final int threadCount;

    RecordLoader(SzEngine engine, int threadCount) {
        this.engine = engine;
        this.threadCount = threadCount;
    }

    /** Loads one Senzing JSON Lines file (the first {@code limit} records when limit > 0). */
    Stats load(Path filePath, long limit) throws Exception {
        Stats stats = new Stats(filePath.toString());
        int maximumBacklog = threadCount * BACKLOG_FACTOR;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Map<Future<?>, Record> pendingFutures = new IdentityHashMap<>();
        Files.createDirectories(ERROR_LOG.getParent());
        Path retryFile = Path.of("logs", "retry-" + filePath.getFileName());
        long start = System.currentTimeMillis();

        try (BufferedReader br = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
             PrintWriter errorLog = new PrintWriter(Files.newBufferedWriter(ERROR_LOG, StandardCharsets.UTF_8,
                 StandardOpenOption.CREATE, StandardOpenOption.APPEND));
             PrintWriter retryWriter = new PrintWriter(Files.newBufferedWriter(retryFile, StandardCharsets.UTF_8))) {
            int lineNumber = 0;
            boolean eof = false;
            while (!eof) {
                // queue records so long as there are more and the backlog is not too large
                while (pendingFutures.size() < maximumBacklog) {
                    if (limit > 0 && stats.attempted >= limit) { eof = true; break; }
                    String line = br.readLine();
                    lineNumber++;
                    if (line == null) { eof = true; break; }
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) { continue; }
                    stats.attempted++;
                    String recordId = null;
                    try {
                        // parse the line as a JSON object and extract the record key
                        Map<String, Object> recordJson = Json.obj(Json.parse(line));
                        String dataSourceCode = Json.str(recordJson.get("DATA_SOURCE"), null);
                        recordId = Json.str(recordJson.get("RECORD_ID"), null);
                        SzRecordKey recordKey = SzRecordKey.of(dataSourceCode, recordId);
                        Record record = new Record(lineNumber, recordId, line);

                        Future<?> future = executor.submit(() -> {
                            // call the addRecord() function with no flags
                            engine.addRecord(recordKey, record.line(), SZ_NO_FLAGS);
                            return null;
                        });
                        pendingFutures.put(future, record);
                    } catch (RuntimeException e) {   // unparseable JSON line
                        logFailedRecord(errorLog, stats, "ERROR", e, lineNumber, recordId);
                        stats.errors++;
                    }
                }
                do {
                    // handle any pending futures WITHOUT blocking to reduce the backlog
                    handlePendingFutures(pendingFutures, false, stats, errorLog, retryWriter, start);
                    if (pendingFutures.size() >= maximumBacklog) {
                        try { Thread.sleep(PAUSE_TIMEOUT); } catch (InterruptedException ignore) { }
                    }
                } while (pendingFutures.size() >= maximumBacklog);
            }
            executor.shutdown();
            // after all records are submitted, block on the remaining futures
            handlePendingFutures(pendingFutures, true, stats, errorLog, retryWriter, start);
        } finally {
            if (!executor.isShutdown()) { executor.shutdown(); }
            stats.elapsedMillis = System.currentTimeMillis() - start;
        }
        if (stats.retries == 0) { Files.deleteIfExists(retryFile); }
        return stats;
    }

    private void handlePendingFutures(Map<Future<?>, Record> pendingFutures, boolean blocking, Stats stats,
                                      PrintWriter errorLog, PrintWriter retryWriter, long start) throws Exception {
        Iterator<Map.Entry<Future<?>, Record>> iter = pendingFutures.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Future<?>, Record> entry = iter.next();
            Future<?> future = entry.getKey();
            Record record = entry.getValue();
            if (!blocking && !future.isDone()) { continue; }
            iter.remove();
            try {
                try {
                    future.get();
                    stats.success++;
                    if (stats.success % PROGRESS_EVERY == 0) { progress(stats, start); }
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if ((cause == null) || !(cause instanceof Exception)) { throw e; }
                    throw ((Exception) cause);
                }
            } catch (SzBadInputException e) {
                logFailedRecord(errorLog, stats, "ERROR", e, record.lineNumber(), record.recordId());
                stats.errors++;
            } catch (SzRetryableException | InterruptedException | CancellationException e) {
                // handle thread interruption and cancellation as retries
                logFailedRecord(errorLog, stats, "WARNING", e, record.lineNumber(), record.recordId());
                stats.errors++;
                stats.retries++;
                retryWriter.println(record.line());
            } catch (Exception e) {
                // any other exception (incl. SzException) is critical: log it and stop
                logFailedRecord(errorLog, stats, "CRITICAL", e, record.lineNumber(), record.recordId());
                stats.errors++;
                throw e;
            }
        }
    }

    private static void progress(Stats stats, long start) {
        double secs = (System.currentTimeMillis() - start) / 1000.0;
        System.out.printf("  %,d loaded, %d errors, %.1fs elapsed, %.1f rec/s%n",
            stats.success, stats.errors, secs, secs == 0 ? 0 : stats.success / secs);
    }

    /** Writes one failed record to logs/loading_errors.json as a JSON line. */
    private static void logFailedRecord(PrintWriter errorLog, Stats stats, String errorType, Exception e,
                                        int lineNumber, String recordId) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("file", stats.file);
        entry.put("line", (long) lineNumber);
        entry.put("record_id", recordId);
        entry.put("error_type", errorType);
        entry.put("error_code", (e instanceof SzException sz) ? (Object) (long) sz.getErrorCode() : null);
        entry.put("message", String.valueOf(e.getMessage()));
        errorLog.println(Json.write(entry));
        errorLog.flush();
        System.err.println("** " + errorType + " ** line " + lineNumber + " record " + recordId + ": " + e.getMessage());
    }
}
