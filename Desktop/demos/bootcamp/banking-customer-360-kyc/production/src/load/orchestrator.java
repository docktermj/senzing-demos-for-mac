// Multi-source load orchestrator for the banking Customer 360 / KYC sources (Data processing module).
//
// This program makes no Senzing calls itself. Each source is loaded by the Step 3 loader
// (src/load/load_source.java, built from Senzing's LoadViaFutures snippet) in its OWN JVM process,
// so the loader's per-run counters start clean for every source and a crash in one source's load
// cannot take down the others (per-source error isolation).
//
// Strategy: Sequential (the bootcamper's choice), in the recorded load order (docs/loading_strategy.md).
// Production patterns included:
//   * retry with exponential backoff (1s, 2s, 4s, ...) when a source's load process does not finish
//     (crash, no summary); a retry re-sends the same records, which replace themselves by
//     DATA_SOURCE + RECORD_ID. A load that finished but reported failed records is NOT retried:
//     those are record-level errors that re-running cannot fix (see logs/loading_errors.json)
//   * partial success: a failed source is reported and the next source still loads; nothing is rolled back
//   * per-source reconciliation: records loaded must equal that source's load input (lines in the
//     file, or the --limit), and the per-source counts must sum to the aggregate
//   * health summary after every source: elapsed time, sources done/remaining, error rate
// Record-level retryable errors (SzRetryableException, per Senzing's error-handling snippet) are
// handled inside the loader, which writes them to logs/retry_<SOURCE>.jsonl.
//
// Run:  src/scripts/run-java.sh data/temp/load_classes LoadOrchestrator [--limit N] [SOURCE=path ...]
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

class LoadOrchestrator {
    static final String[][] DEFAULT_ORDER = {
        {"BUSINESS_REGISTRY", "data/senzing-ready/business_registry.jsonl"},
        {"WATCHLIST", "data/senzing-ready/watchlist.jsonl"},
        {"CORE_BANKING", "data/senzing-ready/core_banking.jsonl"},
        {"CARD_SERVICES", "data/senzing-ready/card_services.jsonl"},
    };
    static final int MAX_RETRIES = 3;

    record Result(String source, int input, int loaded, int failed, int attempts, boolean ok, String note) { }

    public static void main(String[] args) throws Exception {
        int limit = Integer.MAX_VALUE;
        List<String[]> order = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--limit")) limit = Integer.parseInt(args[++i]);
            else { String[] kv = args[i].split("=", 2); order.add(new String[]{kv[0], kv[1]}); }
        }
        if (order.isEmpty()) order.addAll(Arrays.asList(DEFAULT_ORDER));

        long start = System.nanoTime();
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String source = order.get(i)[0];
            Path file = Paths.get(order.get(i)[1]);
            int input = Math.min(countRecords(file), limit);
            System.out.println("=== " + source + ": loading " + input + " record(s) from " + file + " ===");
            Result r = loadWithRetry(source, file, limit, input);
            results.add(r);
            printHealth(results, order.size(), start);
        }
        System.exit(printSummary(results) ? 0 : 1);
    }

    static Result loadWithRetry(String source, Path file, int limit, int input) throws InterruptedException {
        long backoffMs = 1000;
        String note = "";
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                int[] counts = runLoader(file, limit);   // {exit, attempted, loaded, failed}
                boolean reconciled = counts[2] == input && counts[1] == input;
                if (counts[0] == 0 && reconciled) return new Result(source, input, counts[2], counts[3], attempt, true, "");
                note = "exit " + counts[0] + ", attempted " + counts[1] + ", loaded " + counts[2] + " of " + input;
                // The loader finished and reported its counts: its failures are record-level (bad input,
                // logged in logs/loading_errors.json; retryable ones in logs/retry_<SOURCE>.jsonl), so
                // re-running the whole source cannot fix them. Retry only a load that did not finish.
                if (counts[1] >= 0) return new Result(source, input, counts[2], counts[3], attempt, false, note);
            } catch (IOException e) {
                note = "could not run loader: " + e.getMessage();
            }
            if (attempt <= MAX_RETRIES) {
                System.out.println("  attempt " + attempt + " did not complete (" + note + "); retrying in " + backoffMs / 1000 + "s");
                Thread.sleep(backoffMs);
                backoffMs *= 2;
            }
        }
        return new Result(source, input, -1, -1, MAX_RETRIES + 1, false, note);
    }

    /** Runs the loader for one source in its own JVM; returns {exit, attempted, loaded, failed}. */
    static int[] runLoader(Path file, int limit) throws IOException, InterruptedException {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>(List.of(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", System.getProperty("java.class.path"), "LoadSource", file.toString(), "--progress", "0"));
        if (limit != Integer.MAX_VALUE) { cmd.add("--limit"); cmd.add(String.valueOf(limit)); }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        int[] counts = {0, -1, -1, -1};
        Pattern summary = Pattern.compile("SUMMARY attempted=(\\d+) loaded=(\\d+) failed=(\\d+)");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                System.out.println("  | " + line);
                Matcher m = summary.matcher(line);
                if (m.find()) {
                    counts[1] = Integer.parseInt(m.group(1));
                    counts[2] = Integer.parseInt(m.group(2));
                    counts[3] = Integer.parseInt(m.group(3));
                }
            }
        }
        counts[0] = p.waitFor();
        return counts;
    }

    static int countRecords(Path file) throws IOException {
        int n = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) n++;
        }
        return n;
    }

    static void printHealth(List<Result> results, int total, long start) {
        int loaded = 0, failed = 0;
        for (Result r : results) { loaded += Math.max(r.loaded(), 0); failed += Math.max(r.failed(), 0); }
        double s = (System.nanoTime() - start) / 1e9;
        System.out.printf("  health: %d of %d sources done, %d remaining, %.1fs elapsed, %d loaded, error rate %.2f%%%n",
                results.size(), total, total - results.size(), s, loaded,
                loaded + failed == 0 ? 0.0 : 100.0 * failed / (loaded + failed));
    }

    /** Prints the per-source table and the reconciliation; returns true when every source reconciled. */
    static boolean printSummary(List<Result> results) {
        System.out.println();
        System.out.println("Orchestration summary (per-source reconciliation: loaded vs load input)");
        System.out.printf("  %-18s %6s %7s %7s %9s  %s%n", "source", "input", "loaded", "failed", "attempts", "result");
        int sumInput = 0, sumLoaded = 0;
        boolean allOk = true;
        for (Result r : results) {
            System.out.printf("  %-18s %6d %7s %7s %9d  %s%n", r.source(), r.input(),
                    r.ok() ? r.loaded() : "-", r.ok() ? r.failed() : "-", r.attempts(),
                    r.ok() ? "reconciled" : "FAILED: " + r.note());
            sumInput += r.input();
            if (r.ok()) sumLoaded += r.loaded(); else allOk = false;
        }
        System.out.println("  total input " + sumInput + ", total loaded " + sumLoaded
                + (allOk && sumInput == sumLoaded ? " (per-source counts sum to the aggregate)" : " (DOES NOT RECONCILE)"));
        return allOk && sumInput == sumLoaded;
    }
}
