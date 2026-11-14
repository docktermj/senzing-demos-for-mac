package load;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Data processing: drain the Senzing redo queue once, after a batch load, and stop when it is empty.
 *
 * Source: sdk_guide(topic='redo', language='java') returned java/snippets/redo/RedoContinuousViaFutures.java
 * (senzing/code-snippets-v4). Its Senzing calls are kept exactly: engine.getRedoRecord() as the loop
 * sentinel (null = queue empty), engine.processRedoRecord(redo, SZ_NO_FLAGS) submitted to a thread pool,
 * the bounded backlog, and the SzRetryableException / critical-exception handling.
 *
 * The one structural change: that snippet is the STREAMING pattern — when the queue is empty it prints
 * "Pausing for 30 seconds", sleeps and loops forever until CTRL-C. That is right for an always-running
 * production redo processor and wrong after a batch load, where it would never return. Here the loop
 * breaks instead: fetch the next redo record; if none, finish the pending work and fetch once more
 * (processing redo can create more redo); if still none, the queue is empty and we stop.
 * countRedoRecords() is never used as the loop condition (Senzing's guidance: it scans the redo table on
 * every call); it is called once at the end only to report the depth, which must be 0.
 *
 * In production, run redo as a separate always-running process (the streaming pattern above), not at
 * the end of a load.
 *
 * Concurrency: one worker thread, for the same reason as the loader (SQLite does not support concurrent
 * writes); raise it on PostgreSQL. Retryable failures go to logs/retry_redo.jsonl.
 *
 * Usage: src/scripts/sz-java.sh run load.RedoDrainer [--threads N]
 * Prints REDO_PROCESSED, REDO_FAILED and REDO_REMAINING (the final queue depth).
 */
public final class RedoDrainer {

    /** Result of one drain. */
    public static final class Result {
        public int processed, failed, retries;
        public long remaining;
    }

    private RedoDrainer() {}

    /** Drain the queue through an engine the caller owns. */
    public static Result drain(SzEngine engine, int threads) throws Exception {
        Result r = new Result();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        int maximumBacklog = threads * 10;
        Map<Future<?>, String> pending = new IdentityHashMap<>();
        PrintWriter retryWriter = null;
        try {
            while (true) {
                boolean fetchedAny = false;
                for (String redo = engine.getRedoRecord(); redo != null; redo = engine.getRedoRecord()) {
                    fetchedAny = true;
                    final String currentRedo = redo;
                    Future<?> future = executor.submit(() -> engine.processRedoRecord(currentRedo, SZ_NO_FLAGS));
                    pending.put(future, redo);
                    for (int loop = 0; pending.size() >= maximumBacklog; loop++) {
                        if (loop > 0) Thread.sleep(100L);
                        retryWriter = handle(pending, false, r, retryWriter);
                    }
                }
                // queue looked empty: finish what is in flight, then check once more
                retryWriter = handle(pending, true, r, retryWriter);
                if (!fetchedAny) break;
            }
        } finally {
            executor.shutdown();
            if (retryWriter != null) retryWriter.close();
        }
        r.remaining = engine.countRedoRecords(); // reporting only, never a loop condition
        return r;
    }

    private static PrintWriter handle(Map<Future<?>, String> pending, boolean blocking, Result r,
            PrintWriter retryWriter) throws Exception {
        Iterator<Map.Entry<Future<?>, String>> iter = pending.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Future<?>, String> entry = iter.next();
            Future<?> future = entry.getKey();
            if (!blocking && !future.isDone()) continue;
            iter.remove();
            try {
                try {
                    future.get();
                    r.processed++;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (!(cause instanceof Exception)) throw e;
                    throw (Exception) cause;
                }
            } catch (SzRetryableException | InterruptedException | CancellationException e) {
                System.err.println("** WARNING ** FAILED TO PROCESS REDO: " + e);
                r.failed++;
                r.retries++;
                if (retryWriter == null) {
                    Files.createDirectories(Path.of("logs"));
                    retryWriter = new PrintWriter(Files.newBufferedWriter(Path.of("logs", "retry_redo.jsonl"),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                }
                retryWriter.println(entry.getValue());
            } catch (Exception e) {
                System.err.println("** CRITICAL ** FAILED TO PROCESS REDO: " + e);
                r.failed++;
                throw e;
            }
        }
        return retryWriter;
    }

    public static void main(String[] args) throws Exception {
        int threads = 1;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--threads")) threads = Integer.parseInt(args[++i]);
        }
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(RedoDrainer.class.getSimpleName())
            .verboseLogging(false)
            .build();
        Result r;
        try {
            r = drain(env.getEngine(), threads);
        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
        System.out.println("REDO_PROCESSED=" + r.processed);
        System.out.println("REDO_FAILED=" + r.failed);
        System.out.println("REDO_REMAINING=" + r.remaining);
    }
}
