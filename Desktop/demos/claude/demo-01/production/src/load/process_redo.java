// process_redo.java -- Data processing, Steps 9 and 20: drain the redo queue after a batch load.
//
// BATCH, NOT STREAMING. In production, redo is usually handled by an always-running redo processor
// that wakes, processes pending redos, and sleeps when the queue is empty (the MCP scaffolds
// RedoContinuous / RedoContinuousViaFutures). That never terminates, which is right for streaming
// ingest and wrong after a batch load, so this program uses the batch drain instead: fetch the next
// redo record, stop when none is returned, otherwise process it. The fetch's return value is the loop
// sentinel; a redo-count call is never used as the loop condition (the Senzing MCP anti-patterns
// guidance: it scans the whole queue per call, and processing redo creates more redo).
//
// Source: senzing/code-snippets-v4 java/snippets/redo/LoadWithRedoViaLoop.java (via generate_scaffold
// workflow='redo'), its redo loop only: getRedoRecord() / processRedoRecord(redo, SZ_NO_FLAGS) and its
// SzRetryableException handling, exactly as the scaffold has them. Departures: the load half is
// dropped (the loader does that), retryable redo records go to logs/retry-redo.jsonl, progress and a
// terminal summary are printed, and the class is package-private (snake_case filename).
//
// Usage: src/scripts/run-java.sh ProcessRedo
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class ProcessRedo {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(ProcessRedo.class.getSimpleName())
            .verboseLogging(false)
            .build();
        long redoneCount = 0, errorCount = 0, retryCount = 0;
        long start = System.currentTimeMillis();
        Path retryFile = Path.of("logs", "retry-redo.jsonl");
        Files.createDirectories(retryFile.getParent());
        try (PrintWriter retryWriter = new PrintWriter(Files.newBufferedWriter(retryFile, StandardCharsets.UTF_8))) {
            SzEngine engine = env.getEngine();
            for (String redo = engine.getRedoRecord(); redo != null; redo = engine.getRedoRecord()) {
                try {
                    // process the redo record
                    engine.processRedoRecord(redo, SZ_NO_FLAGS);
                    redoneCount++;
                    if (redoneCount % 500 == 0) { System.out.println("  " + redoneCount + " redo records processed..."); }
                } catch (SzRetryableException e) {
                    System.err.println("** WARNING ** retryable redo failure: " + e.getMessage());
                    errorCount++;
                    retryCount++;
                    retryWriter.println(redo);
                }
            }
        } finally {
            env.destroy();
        }
        if (retryCount == 0) { Files.deleteIfExists(retryFile); }
        System.out.printf("REDO processed=%d errors=%d retry=%d elapsed=%.1fs queue=empty%n",
            redoneCount, errorCount, retryCount, (System.currentTimeMillis() - start) / 1000.0);
    }
}
