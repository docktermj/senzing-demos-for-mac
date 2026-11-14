// Drains the Senzing redo queue after a batch load, then exits.
//
// Source: senzing/code-snippets-v4 java/snippets/redo/RedoContinuous.java, via
// generate_scaffold(language='java', workflow='redo') (2026-11-14, sha256 942e6634...). Every Senzing
// call is kept as the snippet has it: engine.getRedoRecord(), engine.processRedoRecord(redo,
// SZ_NO_FLAGS), SzRetryableException -> retry file, anything else -> stop, env.destroy().
//
// One deliberate change: the snippet is a STREAMING redo processor. When the queue is empty it
// prints "Pausing for 30 seconds" and loops forever, which is right for a production service that
// runs alongside continuous ingest and wakes to process redo as it appears. After a batch load we
// need the opposite: drain the queue and stop. So an empty fetch (getRedoRecord() returning null)
// ends the loop. The fetch result is the loop condition; a redo count is never polled, because
// Senzing warns that counting redo records scans the table on every call and processing redo can
// create more redo.
//
// Run: src/scripts/run-java.sh data/temp/load_classes ProcessRedoQueue
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static com.senzing.sdk.SzFlag.*;

class ProcessRedoQueue {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName(ProcessRedoQueue.class.getSimpleName())
                .verboseLogging(false)
                .build();
        int redoneCount = 0, errorCount = 0, retryCount = 0;
        long start = System.nanoTime();
        PrintWriter retryWriter = null;
        try {
            SzEngine engine = env.getEngine();
            while (true) {
                String redo = engine.getRedoRecord();
                if (redo == null) break;          // queue empty: batch drain is complete
                try {
                    engine.processRedoRecord(redo, SZ_NO_FLAGS);
                    redoneCount++;
                } catch (SzRetryableException e) {
                    System.err.println("** WARNING ** retryable redo failure: " + e);
                    errorCount++;
                    retryCount++;
                    if (retryWriter == null) {
                        Files.createDirectories(Paths.get("logs"));
                        retryWriter = new PrintWriter(Files.newBufferedWriter(Paths.get("logs", "retry_redo.jsonl"),
                                StandardCharsets.UTF_8));
                    }
                    retryWriter.println(redo);
                } catch (Exception e) {
                    System.err.println("** CRITICAL ** redo failure: " + e);
                    errorCount++;
                    throw e;
                }
            }
        } finally {
            if (retryWriter != null) retryWriter.close();
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
            System.out.println("Redos successfully processed : " + redoneCount);
            System.out.println("Total failed redos           : " + errorCount + (retryCount > 0 ? " (" + retryCount + " in logs/retry_redo.jsonl)" : ""));
            System.out.printf("Duration                     : %.2fs%n", (System.nanoTime() - start) / 1e9);
            System.out.println("Redo queue reached empty     : " + (errorCount == 0 ? "yes" : "see errors above"));
        }
    }
}
