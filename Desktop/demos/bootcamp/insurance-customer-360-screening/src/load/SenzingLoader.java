package load;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.senzing.sdk.*;

import server.Json;

import static com.senzing.sdk.SzFlag.*;

/**
 * Production loading logic for Senzing-ready JSONL files, shared by the single-source loader
 * (src/load/load_source.java) and the multi-source orchestrator (src/load/orchestrator.java).
 *
 * ── Where the Senzing code comes from ───────────────────────────────────────────────────────────
 * sdk_guide(topic='load', language='java') with no record_count returned the threaded production
 * pattern java/snippets/loading/LoadViaFutures.java (senzing/code-snippets-v4). Its Senzing calls are
 * kept exactly: SzRecordKey.of(dataSource, recordId), engine.addRecord(recordKey, line, SZ_NO_FLAGS),
 * the SzBadInputException / SzRetryableException / other-exception handling, a fixed thread pool, and
 * a bounded backlog of pending futures. Deviations, and why:
 *   - JSON: the snippet imports javax.json, which is not part of Java SE and not shipped with the Senzing
 *     install; this bootcamp compiles with plain javac, so records are parsed with the project's
 *     dependency-free reader (src/server/Json.java). Only DATA_SOURCE and RECORD_ID are read; the line is
 *     passed to addRecord unchanged.
 *   - Counters are per run (fields of a Stats object), not static, so loading several sources in one
 *     process reports each source's own figures (static counters would accumulate across sources).
 *   - Failed records are appended to logs/loading_errors.json (one JSON object per line) with source,
 *     line number, record ID, error type, Senzing error code and message, instead of stderr only.
 *     Retryable failures also go to logs/retry_<source>.jsonl in the project, not a system temp file.
 *   - Progress every N records with throughput; a final statistics block.
 *   - Checkpoint/resume: the highest line number below which every line is finished is written to a
 *     checkpoint file every progress interval; a re-run with resume skips those lines. Re-adding a record
 *     already loaded is safe, because a record with the same DATA_SOURCE and RECORD_ID replaces itself.
 *   - Optional limit (load only the first N records, for a test load or a recorded subset) and optional
 *     in-memory shuffle (Senzing's guidance: sorted or grouped input causes lock contention; shuffle
 *     before loading). The in-memory shuffle suits files that fit in memory; for very large files shuffle
 *     upstream (for example with `shuf`) and leave it off.
 *
 * ── Volume and concurrency ──────────────────────────────────────────────────────────────────────
 * Production volume tier: MEDIUM (more than 500,000 up to 10,000,000 records; the bootcamper picked the
 * range, no count was given). Architecture: thread pool with batching (bounded backlog), checkpoint/resume
 * and throughput reporting.
 * Datastore: SQLite (database_type in config/bootcamp_preferences.yaml). Senzing's guidance says SQLite
 * does not support concurrent writes and lists "database locked errors under concurrent access" among the
 * symptoms of using it, so this loader keeps the thread-pool pattern but runs ONE worker thread.
 * That worker count of 1 is a derivation from that property, not a figure Senzing publishes.
 * When you move to PostgreSQL (recommended at medium volume), raise the thread count: Senzing's guidance is
 * to start with 2-8 workers per CPU core and tune to your database and storage throughput, e.g.
 * --threads $((4 * $(sysctl -n hw.ncpu))).
 *
 * ── Redo ────────────────────────────────────────────────────────────────────────────────────────
 * This class only adds records. Redo records are drained afterwards by src/load/drain_redo.java (batch);
 * in production run redo as a separate, always-running process.
 */
public final class SenzingLoader {

    /** Per-run statistics. */
    public static final class Stats {
        public final String source;
        public int attempted, success, errors, retries, skippedResume;
        public long startNanos, endNanos;
        public final Map<String, Integer> errorCodes = new TreeMap<>();

        Stats(String source) { this.source = source; }

        public double seconds() { return (endNanos - startNanos) / 1e9; }

        public double perSecond() { return seconds() > 0 ? success / seconds() : 0; }
    }

    /** Options for one load. */
    public static final class Options {
        public int threads = 1;           // SQLite: one writer (see class comment)
        public int limit = 0;             // 0 = every record
        public boolean shuffle = false;
        public boolean resume = false;
        public int progressEvery = 50;
        public Path errorLog = Path.of("logs", "loading_errors.json");
        public Path logDir = Path.of("logs");
    }

    private record Rec(int lineNumber, String line, String recordId) {}

    private SenzingLoader() {}

    /**
     * Load one Senzing-ready JSONL file through an engine the caller owns (initialize the environment once
     * per process and share it; never per source or per thread).
     */
    public static Stats load(SzEngine engine, String source, Path input, Options opt) throws Exception {
        Stats stats = new Stats(source);
        Files.createDirectories(opt.logDir);
        Path checkpoint = opt.logDir.resolve(input.getFileName().toString() + ".checkpoint");
        int resumeAfter = 0;
        if (opt.resume && Files.exists(checkpoint)) {
            resumeAfter = Integer.parseInt(Files.readString(checkpoint).trim());
        }

        // read the input (non-blank, non-comment lines), honoring the limit
        List<String[]> lines = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0, kept = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (opt.limit > 0 && kept >= opt.limit) break;
                kept++;
                lines.add(new String[] {Integer.toString(lineNumber), line});
            }
        }
        if (opt.shuffle) Collections.shuffle(lines, new Random(20261114L));

        ExecutorService executor = Executors.newFixedThreadPool(opt.threads);
        int maximumBacklog = opt.threads * 10;
        Map<Future<?>, Rec> pending = new IdentityHashMap<>();
        BitSet done = new BitSet();
        int[] recordLines = lines.stream().mapToInt(l -> Integer.parseInt(l[0])).sorted().toArray();
        int maxLine = recordLines.length > 0 ? recordLines[recordLines.length - 1] : 0;
        PrintWriter retryWriter = null;
        stats.startNanos = System.nanoTime();

        try (PrintWriter errors = new PrintWriter(Files.newBufferedWriter(opt.errorLog, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            Iterator<String[]> it = lines.iterator();
            boolean eof = false;
            while (!eof) {
                while (pending.size() < maximumBacklog) {
                    if (!it.hasNext()) { eof = true; break; }
                    String[] l = it.next();
                    int lineNumber = Integer.parseInt(l[0]);
                    String line = l[1];
                    if (lineNumber <= resumeAfter) {
                        stats.skippedResume++;
                        done.set(lineNumber);
                        continue;
                    }
                    stats.attempted++;
                    String dataSourceCode, recordId;
                    try {
                        Map<String, Object> json = Json.parseObject(line);
                        dataSourceCode = Json.str(json.get("DATA_SOURCE"));
                        recordId = Json.str(json.get("RECORD_ID"));
                    } catch (IllegalArgumentException e) {
                        logFailed(errors, stats, "ERROR", null, e, lineNumber, null);
                        done.set(lineNumber);
                        continue;
                    }
                    SzRecordKey recordKey = SzRecordKey.of(dataSourceCode, recordId);
                    Rec rec = new Rec(lineNumber, line, recordId);
                    Future<?> future = executor.submit(() -> {
                        // call the addRecord() function with no flags
                        engine.addRecord(recordKey, rec.line(), SZ_NO_FLAGS);
                        return null;
                    });
                    pending.put(future, rec);
                }
                do {
                    retryWriter = handlePending(pending, false, errors, stats, done, retryWriter, opt, source);
                    if (pending.size() >= maximumBacklog) Thread.sleep(100L);
                } while (pending.size() >= maximumBacklog);
                progress(stats, done, recordLines, checkpoint, opt, false);
            }
            executor.shutdown();
            retryWriter = handlePending(pending, true, errors, stats, done, retryWriter, opt, source);
        } finally {
            if (!executor.isShutdown()) executor.shutdown();
            if (retryWriter != null) retryWriter.close();
            stats.endNanos = System.nanoTime();
        }
        progress(stats, done, recordLines, checkpoint, opt, true);
        // a complete run needs no checkpoint; an interrupted one keeps it for --resume
        if (stats.errors == 0 && watermark(done, recordLines) >= maxLine) Files.deleteIfExists(checkpoint);
        return stats;
    }

    private static PrintWriter handlePending(Map<Future<?>, Rec> pending, boolean blocking, PrintWriter errors,
            Stats stats, BitSet done, PrintWriter retryWriter, Options opt, String source) throws Exception {
        Iterator<Map.Entry<Future<?>, Rec>> iter = pending.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Future<?>, Rec> entry = iter.next();
            Future<?> future = entry.getKey();
            Rec rec = entry.getValue();
            if (!blocking && !future.isDone()) continue;
            iter.remove();
            try {
                try {
                    future.get();
                    stats.success++;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (!(cause instanceof Exception)) throw e;
                    throw (Exception) cause;
                }
            } catch (SzBadInputException e) {
                logFailed(errors, stats, "ERROR", e.getErrorCode(), e, rec.lineNumber(), rec.recordId());
            } catch (SzRetryableException | InterruptedException | CancellationException e) {
                Integer code = (e instanceof SzException) ? ((SzException) e).getErrorCode() : null;
                logFailed(errors, stats, "WARNING", code, e, rec.lineNumber(), rec.recordId());
                stats.retries++;
                if (retryWriter == null) {
                    retryWriter = new PrintWriter(Files.newBufferedWriter(opt.logDir.resolve("retry_" + source + ".jsonl"),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                }
                retryWriter.println(rec.line());
            } catch (Exception e) {
                Integer code = (e instanceof SzException) ? ((SzException) e).getErrorCode() : null;
                logFailed(errors, stats, "CRITICAL", code, e, rec.lineNumber(), rec.recordId());
                done.set(rec.lineNumber());
                throw e; // critical: stop this load
            }
            done.set(rec.lineNumber());
        }
        return retryWriter;
    }

    private static void logFailed(PrintWriter errors, Stats stats, String type, Integer code, Exception e,
            int lineNumber, String recordId) {
        stats.errors++;
        stats.errorCodes.merge(type + (code != null ? " " + code : ""), 1, Integer::sum);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("at", OffsetDateTime.now().toString());
        entry.put("source", stats.source);
        entry.put("line", lineNumber);
        entry.put("record_id", recordId);
        entry.put("error_type", type);
        entry.put("error_code", code);
        entry.put("message", String.valueOf(e.getMessage()));
        errors.println(Json.write(entry));
        errors.flush();
        System.err.println("** " + type + " ** line " + lineNumber + " record " + recordId + ": " + e.getMessage());
    }

    /**
     * Checkpoint watermark: the highest input line number such that every record line at or below it has
     * finished (loaded or logged as failed). Lines are walked in file order, so a shuffled load still
     * resumes correctly; re-adding a record past the watermark simply replaces it.
     */
    private static int watermark(BitSet done, int[] recordLines) {
        int w = 0;
        for (int line : recordLines) {
            if (!done.get(line)) break;
            w = line;
        }
        return w;
    }

    private static void progress(Stats stats, BitSet done, int[] recordLines, Path checkpoint, Options opt,
            boolean last) throws IOException {
        int finished = stats.success + stats.errors;
        if (!last && (finished == 0 || finished % opt.progressEvery != 0)) return;
        double secs = (System.nanoTime() - stats.startNanos) / 1e9;
        System.out.printf("[%s] %d loaded, %d errors, %.1fs elapsed, %.1f records/s%n",
            stats.source, stats.success, stats.errors, secs, secs > 0 ? stats.success / secs : 0.0);
        Files.writeString(checkpoint, Integer.toString(watermark(done, recordLines)));
    }
}
