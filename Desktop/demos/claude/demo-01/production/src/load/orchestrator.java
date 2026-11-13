// orchestrator.java -- Data processing, Phase C Step 17: load several sources in order.
//
// Ordinary orchestration code around the Step 3 loading engine (src/load/RecordLoader.java, built from
// the MCP LoadViaFutures scaffold): no Senzing call is made here except creating and destroying the
// environment, exactly as the loader's main() does it. What this adds:
//  - ordered, sequential loading (the strategy chosen for these sources; they have no dependencies);
//  - one Senzing environment per process, shared by every source (initialize once, per the MCP
//    anti-patterns guidance), with a FRESH per-source tally: the loader's Stats object is created per
//    load, so counts never accumulate across sources;
//  - error isolation and partial success: a source whose load throws is retried with exponential
//    backoff (1s, 2s, 4s, 8s), and if it still fails it is reported as failed while the remaining
//    sources load and successful ones stand. A retry is safe because a record sent again with the same
//    DATA_SOURCE + RECORD_ID replaces the one already loaded. Retryable per-record failures
//    (SzRetryableException, per sdk_guide topic='error_handling') land in logs/retry-<file> and are
//    re-sent once with the same backoff;
//  - per-source reconciliation: loaded must equal the load input (all non-blank lines, or the first N
//    with --limit), and the per-source counts must sum to the aggregate, or the run says so and exits 1;
//  - health summaries after each source and a JSON completion summary in logs/orchestration_summary.json.
//
// Usage: src/scripts/run-java.sh Orchestrator [--source CODE=path ...] [--threads N] [--limit N]
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

class Orchestrator {
    /** Remaining sources in the Step 14 load order (CRM_CONTACTS was loaded first, in Phase B). */
    static final String[][] DEFAULT_PLAN = {
        {"WEB_ORDERS", "data/senzing-ready/web_orders.jsonl"},
        {"SUPPORT_TICKETS", "data/senzing-ready/support_tickets.jsonl"},
    };
    static final long[] BACKOFF_MILLIS = {1000, 2000, 4000, 8000};

    record Outcome(String source, String file, long input, RecordLoader.Stats stats, String status, String error) {}

    static long countInput(Path file, long limit) throws Exception {
        long n;
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            n = lines.map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).count();
        }
        return limit > 0 ? Math.min(n, limit) : n;
    }

    /** Loads one source inside its own error boundary, retrying with exponential backoff. */
    static Outcome loadSource(RecordLoader loader, String source, Path file, long limit) throws Exception {
        long input = countInput(file, limit);
        String lastError = null;
        for (int attempt = 0; attempt <= BACKOFF_MILLIS.length; attempt++) {
            try {
                RecordLoader.Stats stats = loader.load(file, limit);
                Path retryFile = Path.of("logs", "retry-" + file.getFileName());
                for (int r = 0; stats.retries > 0 && Files.exists(retryFile) && r < BACKOFF_MILLIS.length; r++) {
                    Thread.sleep(BACKOFF_MILLIS[r]);
                    Path again = Files.move(retryFile, Path.of("logs", "retrying-" + file.getFileName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("  re-sending " + stats.retries + " retryable records (attempt " + (r + 1) + ")");
                    RecordLoader.Stats retried = loader.load(again, 0);
                    stats.success += retried.success;
                    stats.errors -= retried.success;
                    stats.retries = retried.retries;
                    Files.deleteIfExists(again);
                }
                String status = stats.success == input ? "loaded" : "mismatch";
                return new Outcome(source, file.toString(), input, stats, status, null);
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (attempt < BACKOFF_MILLIS.length) {
                    System.err.println("  " + source + " failed (" + lastError + "); retrying in "
                        + BACKOFF_MILLIS[attempt] / 1000 + "s");
                    Thread.sleep(BACKOFF_MILLIS[attempt]);
                }
            }
        }
        return new Outcome(source, file.toString(), input, null, "failed", lastError);
    }

    public static void main(String[] args) throws Exception {
        List<String[]> plan = new ArrayList<>();
        int threads = RecordLoader.DEFAULT_THREAD_COUNT;
        long limit = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source" -> plan.add(args[++i].split("=", 2));
                case "--threads" -> threads = Integer.parseInt(args[++i]);
                case "--limit" -> limit = Long.parseLong(args[++i]);
                default -> throw new IllegalArgumentException("unknown argument " + args[i]);
            }
        }
        if (plan.isEmpty()) { plan.addAll(Arrays.asList(DEFAULT_PLAN)); }

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

        List<Outcome> outcomes = new ArrayList<>();
        long start = System.currentTimeMillis();
        try {
            RecordLoader loader = new RecordLoader(env.getEngine(), threads);
            for (int i = 0; i < plan.size(); i++) {
                String source = plan.get(i)[0];
                Path file = Path.of(plan.get(i)[1]);
                System.out.println("[" + (i + 1) + "/" + plan.size() + "] " + source + " <- " + file
                    + " (threads=" + threads + (limit > 0 ? ", limit=" + limit : "") + ")");
                Outcome o = loadSource(loader, source, file, limit);
                outcomes.add(o);
                long done = outcomes.size(), errs = 0, loaded = 0;
                for (Outcome x : outcomes) {
                    if (x.stats() != null) { errs += x.stats().errors; loaded += x.stats().success; }
                }
                System.out.printf("  HEALTH %s: %s | sources %d/%d done | %,d loaded, %d errors so far | %.1fs elapsed%n",
                    source, o.status(), done, plan.size(), loaded, errs, (System.currentTimeMillis() - start) / 1000.0);
            }
        } finally {
            env.destroy();
        }

        // reconcile per source and in aggregate before reporting anything as a result
        long sumInput = 0, sumLoaded = 0, sumFailed = 0;
        boolean reconciled = true;
        List<Object> rows = new ArrayList<>();
        System.out.println();
        System.out.println("SOURCE            INPUT   LOADED  FAILED  STATUS");
        for (Outcome o : outcomes) {
            long loaded = o.stats() == null ? 0 : o.stats().success;
            long failed = o.stats() == null ? 0 : o.stats().errors;
            sumInput += o.input(); sumLoaded += loaded; sumFailed += failed;
            if (!"loaded".equals(o.status())) { reconciled = false; }
            System.out.printf("%-16s %6d %8d %7d  %s%s%n", o.source(), o.input(), loaded, failed, o.status(),
                o.error() == null ? "" : " (" + o.error() + ")");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", o.source()); row.put("file", o.file()); row.put("input", o.input());
            row.put("loaded", loaded); row.put("failed", failed); row.put("status", o.status());
            row.put("rate_per_sec", o.stats() == null ? null : (Object) (Math.round(o.stats().perSecond() * 10) / 10.0));
            row.put("error", o.error());
            rows.add(row);
        }
        long aggregate = 0;
        for (Outcome o : outcomes) { aggregate += o.stats() == null ? 0 : o.stats().success; }
        boolean sums = aggregate == sumLoaded;
        System.out.printf("TOTAL            %6d %8d %7d  per-source counts sum to aggregate: %s%n", sumInput, sumLoaded, sumFailed, sums);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("limit", limit); summary.put("threads", (long) threads);
        summary.put("sources", rows); summary.put("input_total", sumInput); summary.put("loaded_total", sumLoaded);
        summary.put("reconciled", reconciled && sums);
        summary.put("elapsed_sec", (System.currentTimeMillis() - start) / 1000.0);
        Files.writeString(Path.of("logs", "orchestration_summary.json"), Json.write(summary), StandardCharsets.UTF_8);
        if (!(reconciled && sums)) {
            System.out.println("RECONCILIATION FAILED: per-source counts do not match their load inputs; see above.");
            System.exit(1);
        }
        System.out.println("RECONCILED: every source loaded exactly its load input.");
    }
}
