package load;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import server.Json;
import validate.ExportStats;

/**
 * Data processing, Phase C: load several Senzing-ready sources in order, then drain redo once.
 *
 * Senzing calls come from the Phase A loader (SenzingLoader, from sdk_guide(topic='load') →
 * LoadViaFutures.java) and the batch redo drainer (RedoDrainer, from sdk_guide(topic='redo')). This file is
 * the orchestration around them, which no Senzing template provides:
 *   - ONE Senzing environment for the whole run (initialize once per process, share the engine);
 *   - strategy: SEQUENTIAL, in the order given (default: this project's recorded load order);
 *   - per-source error isolation: each source loads in its own try/catch, so one failure does not stop the
 *     others, and successful sources are never rolled back;
 *   - retry with exponential backoff (1s, 2s, 4s, 8s) for a source whose load throws a retryable Senzing
 *     exception (SzRetryableException, the retryable class in sdk_guide(topic='error_handling')); a bad-input
 *     record is logged by the loader and never retried at source level;
 *   - per-source counters: SenzingLoader returns a fresh Stats per load, so figures do not accumulate across
 *     sources;
 *   - reconciliation: each source's LOADED must equal the records in the input it was given, and the per-source
 *     figures must sum to the run total, before anything is reported as a result;
 *   - health summary after each source (sources done/remaining, elapsed time, error rate);
 *   - redo drained once after all sources (not between sources), stopping when the queue is empty;
 *   - a summary written to logs/orchestration_summary.json.
 *
 * Concurrency: sequential sources, one writer thread each, because the datastore is SQLite (no concurrent
 * writes). On PostgreSQL raise --threads (2-8 per CPU core is Senzing's starting guidance) and consider
 * parallel sources.
 *
 * Usage: src/scripts/sz-java.sh run load.Orchestrator [--limit N] [--threads N] [SOURCE ...]
 *   --limit N  load only the first N records of each source (a test run)
 * Input for SOURCE is data/senzing-ready/<source in lower case>.jsonl.
 * The top-level class is package-private so it may live in this snake_case file.
 */
class Orchestrator {

    static final List<String> DEFAULT_ORDER = List.of(
        "SANCTIONS_PEP", "BUSINESS_REGISTRY", "PC_POLICY_ADMIN", "LIFE_ANNUITY_ADMIN", "SIU_CASES");

    static final long[] BACKOFF_MILLIS = {1000L, 2000L, 4000L, 8000L};

    public static void main(String[] args) throws Exception {
        SenzingLoader.Options opt = new SenzingLoader.Options();
        opt.shuffle = true;
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--limit": opt.limit = Integer.parseInt(args[++i]); break;
                case "--threads": opt.threads = Integer.parseInt(args[++i]); break;
                default: sources.add(args[i]);
            }
        }
        if (sources.isEmpty()) sources = DEFAULT_ORDER;

        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(Orchestrator.class.getSimpleName())
            .verboseLogging(false)
            .build();

        long start = System.nanoTime();
        List<Map<String, Object>> perSource = new ArrayList<>();
        int totalLoaded = 0, totalFailed = 0, totalInput = 0;
        boolean reconciled = true;
        Map<String, Object> redoSummary = new LinkedHashMap<>();
        try {
            SzEngine engine = env.getEngine();
            int repoBefore = ExportStats.recordCount(engine);
            System.out.println("Records in repository before the run: " + repoBefore);

            for (int n = 0; n < sources.size(); n++) {
                String source = sources.get(n);
                Path input = Path.of("data", "senzing-ready", source.toLowerCase() + ".jsonl");
                int inputCount = countInput(input, opt.limit);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("source", source);
                row.put("input", input.toString());
                row.put("input_records", inputCount);
                SenzingLoader.Stats s = null;
                String status;
                int attempt = 0;
                while (true) {
                    try {
                        s = SenzingLoader.load(engine, source, input, opt);
                        status = "loaded";
                        break;
                    } catch (SzRetryableException e) {
                        if (attempt >= BACKOFF_MILLIS.length) {
                            status = "failed";
                            row.put("error", "retries exhausted: " + e.getMessage());
                            break;
                        }
                        System.err.println("[" + source + "] retryable failure, retrying in "
                            + BACKOFF_MILLIS[attempt] + " ms: " + e.getMessage());
                        Thread.sleep(BACKOFF_MILLIS[attempt++]);
                    } catch (Exception e) {
                        status = "failed";
                        row.put("error", String.valueOf(e.getMessage()));
                        System.err.println("[" + source + "] FAILED: " + e);
                        break;
                    }
                }
                row.put("retries_at_source_level", attempt);
                if (s != null) {
                    row.put("attempted", s.attempted);
                    row.put("loaded", s.success);
                    row.put("failed", s.errors);
                    row.put("seconds", Math.round(s.seconds() * 100) / 100.0);
                    row.put("records_per_second", Math.round(s.perSecond() * 10) / 10.0);
                    boolean match = s.success == inputCount && s.errors == 0;
                    row.put("loaded_equals_input", match);
                    if (!match) { reconciled = false; status = "failed"; }
                    totalLoaded += s.success;
                    totalFailed += s.errors;
                } else {
                    reconciled = false;
                }
                totalInput += inputCount;
                row.put("status", status);
                perSource.add(row);
                double secs = (System.nanoTime() - start) / 1e9;
                System.out.printf("HEALTH: %d of %d sources done, %d remaining, %.1fs elapsed, %d loaded, %d failed%n",
                    n + 1, sources.size(), sources.size() - n - 1, secs, totalLoaded, totalFailed);
            }

            int sumOfRows = perSource.stream().mapToInt(r -> (Integer) r.getOrDefault("loaded", 0)).sum();
            if (sumOfRows != totalLoaded) reconciled = false;

            System.out.println("Processing the redo queue now (refines cross-source resolution).");
            RedoDrainer.Result r = RedoDrainer.drain(engine, opt.threads);
            redoSummary.put("processed", r.processed);
            redoSummary.put("failed", r.failed);
            redoSummary.put("remaining", r.remaining);

            int repoAfter = ExportStats.recordCount(engine);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("at", OffsetDateTime.now().toString());
            summary.put("strategy", "sequential");
            summary.put("limit_per_source", opt.limit);
            summary.put("sources", perSource);
            summary.put("total_input", totalInput);
            summary.put("total_loaded", totalLoaded);
            summary.put("total_failed", totalFailed);
            summary.put("reconciled", reconciled);
            summary.put("redo", redoSummary);
            summary.put("repository_records_before", repoBefore);
            summary.put("repository_records_after", repoAfter);
            summary.put("seconds", Math.round((System.nanoTime() - start) / 1e7) / 100.0);
            Files.createDirectories(Path.of("logs"));
            Files.writeString(Path.of("logs", "orchestration_summary.json"), Json.write(summary), StandardCharsets.UTF_8);
            System.out.println("SUMMARY=" + Json.write(summary));
        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
        System.exit(reconciled ? 0 : 1);
    }

    /** Records the loader will be given: non-blank, non-comment lines, capped by the limit. */
    static int countInput(Path input, int limit) throws Exception {
        int n = 0;
        for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            n++;
        }
        return limit > 0 ? Math.min(n, limit) : n;
    }
}
