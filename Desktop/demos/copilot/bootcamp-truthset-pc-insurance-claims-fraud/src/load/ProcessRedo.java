/*
 * Adapted from the MCP-provided senzing/code-snippets-v4 redo scaffold
 * (java/snippets/redo/RedoContinuousViaFutures.java). The continuous sleep
 * loop is changed to a batch drain that exits when getRedoRecord returns null.
 * A single worker keeps database writes serialized for SQLite.
 */
package redo;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

public class ProcessRedo {
    private static final int SQLITE_WRITE_THREAD_COUNT = 1;
    private static final Path RETRY_LOG_PATH = Path.of("logs", "redo_retries.jsonl");

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalArgumentException("SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName(ProcessRedo.class.getSimpleName())
                .verboseLogging(false)
                .build();
        ExecutorService executor = Executors.newFixedThreadPool(SQLITE_WRITE_THREAD_COUNT);
        long processed = 0;
        long failed = 0;
        long retryable = 0;

        try {
            SzEngine engine = environment.getEngine();
            String redoRecord;
            while ((redoRecord = engine.getRedoRecord()) != null) {
                final String currentRedo = redoRecord;
                Future<?> future = executor.submit(
                        () -> engine.processRedoRecord(currentRedo, SZ_NO_FLAGS));
                try {
                    future.get();
                    processed++;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw exception;
                } catch (ExecutionException | CancellationException exception) {
                    Throwable cause = exception instanceof ExecutionException
                            ? ((ExecutionException) exception).getCause()
                            : exception;
                    if (cause instanceof SzRetryableException || cause instanceof CancellationException) {
                        Exception retryError = (Exception) cause;
                        logFailure("RETRYABLE", retryError);
                        appendRetryRecord(currentRedo);
                        failed++;
                        retryable++;
                    } else if (cause instanceof Exception criticalError) {
                        logFailure("CRITICAL", criticalError);
                        failed++;
                        throw criticalError;
                    } else {
                        throw new RuntimeException("Unexpected redo processing failure.", cause);
                    }
                }
            }

            System.out.printf(
                    "Redo drain complete: processed=%d failed=%d retryable=%d; queue reached empty when getRedoRecord returned null.%n",
                    processed, failed, retryable);
            if (retryable > 0) {
                System.out.println("Retry records: " + RETRY_LOG_PATH);
            }
        } catch (Exception exception) {
            System.err.println("Redo processing failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
            throw new RuntimeException(exception);
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

    private static void appendRetryRecord(String redoRecord) throws IOException {
        Files.createDirectories(RETRY_LOG_PATH.getParent());
        Files.writeString(
                RETRY_LOG_PATH,
                redoRecord + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private static void logFailure(String category, Exception exception) {
        String errorCode = exception instanceof SzException senzingException
                ? String.valueOf(senzingException.getErrorCode())
                : exception.getClass().getSimpleName();
        System.err.printf(
                "%s redo failure: code=%s message=%s%n",
                category, errorCode, exception.getMessage());
    }
}
