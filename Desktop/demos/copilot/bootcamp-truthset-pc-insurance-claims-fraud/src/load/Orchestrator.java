import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Orchestrator {
    private static final Path DATABASE_PATH = Path.of("database", "G2C.db");
    private static final Path ERROR_LOG_PATH = Path.of("logs", "loading_errors.json");
    private static final Path ORCHESTRATION_LOG_DIR = Path.of("logs", "orchestrator");
    private static final Path SUMMARY_PATH = Path.of("logs", "orchestration_summary.json");
    private static final Pattern LOAD_SUMMARY = Pattern.compile(
            "Load complete: attempted=(\\d+) loaded=(\\d+) failed=(\\d+) retryable=(\\d+)");
    private static final Pattern PERMANENT_ERROR = Pattern.compile(
            "\"category\"\\s*:\\s*\"(?:BAD_INPUT|CRITICAL)\"");
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final long HEALTH_INTERVAL_SECONDS = 30;

    private Orchestrator() {
    }

    public static void main(String[] args) {
        try {
            int exitCode = execute(parseOptions(args));
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (Exception exception) {
            System.err.println("Orchestration failed: " + exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static int execute(Options options) throws IOException {
        if (options.sources().isEmpty()) {
            throw new IllegalArgumentException("Provide at least one CODE=JSONL_PATH source.");
        }
        Files.createDirectories(ORCHESTRATION_LOG_DIR);

        List<SourceResult> results = new ArrayList<>();
        long orchestrationStarted = System.nanoTime();
        for (int index = 0; index < options.sources().size(); index++) {
            Source source = options.sources().get(index);
            SourceResult result;
            try {
                result = loadSource(source, options, results, index);
            } catch (Exception exception) {
                result = SourceResult.failed(source, 0, 0, 0, 0, 0,
                        null, null, null, exception.getMessage());
                System.err.printf("Source %s failed before load: %s%n", source.code(), exception.getMessage());
            }
            results.add(result);
            writeSummary(results, options, orchestrationStarted);
        }

        long attempted = results.stream().mapToLong(SourceResult::attempted).sum();
        long loaded = results.stream().mapToLong(SourceResult::loaded).sum();
        long failed = results.stream().mapToLong(SourceResult::failed).sum();
        long retryable = results.stream().mapToLong(SourceResult::retryable).sum();
        long succeeded = results.stream().filter(result -> result.status().equals("loaded")).count();
        double elapsed = elapsedSeconds(orchestrationStarted);
        System.out.printf(
                "Orchestration complete: sources=%d/%d loaded=%d failed=%d retryable=%d duration=%.2fs%n",
                succeeded, results.size(), loaded, failed, retryable, elapsed);
        System.out.printf(
                "Aggregate records: attempted=%d loaded=%d failed=%d error_rate=%.2f%%%n",
                attempted, loaded, failed, percentage(failed, attempted));
        System.out.println("Summary: " + SUMMARY_PATH);
        return succeeded == results.size() ? 0 : 1;
    }

    private static SourceResult loadSource(
            Source source, Options options, List<SourceResult> completed, int sourceIndex) throws Exception {
        InputStats input = inspectInput(source);
        long requestedLimit = options.globalLimit();
        Long sourceLimit = options.sourceLimits().get(source.code());
        if (sourceLimit != null) {
            requestedLimit = requestedLimit == 0 ? sourceLimit : Math.min(requestedLimit, sourceLimit);
        }
        long expectedRecords = requestedLimit == 0
                ? input.recordCount()
                : Math.min(requestedLimit, input.recordCount());

        Path backupPath = createBackup(source, options.globalLimit() > 0);
        long startedAt = System.nanoTime();
        AttemptResult lastAttempt = null;
        for (int attempt = 1; attempt <= options.maxAttempts(); attempt++) {
            if (attempt > 1) {
                long delaySeconds = Math.min(1L << (attempt - 2), 8L);
                System.out.printf("Retrying %s after %ds (attempt %d/%d).%n",
                        source.code(), delaySeconds, attempt, options.maxAttempts());
                TimeUnit.SECONDS.sleep(delaySeconds);
            }

            lastAttempt = runAttempt(
                    source, requestedLimit, expectedRecords, attempt,
                    completed, sourceIndex, options.sources().size(), startedAt);
            boolean permanentError = hasPermanentErrors(lastAttempt.errorLogPath());
            if (lastAttempt.exitCode() == 0
                    && lastAttempt.summary() != null
                    && lastAttempt.summary().attempted() == expectedRecords
                    && lastAttempt.summary().loaded() == expectedRecords
                    && lastAttempt.summary().failed() == 0
                    && lastAttempt.summary().retryable() == 0) {
                System.out.printf(
                        "Source %s loaded: attempted=%d loaded=%d failed=0 duration=%.2fs%n",
                        source.code(), lastAttempt.summary().attempted(),
                        lastAttempt.summary().loaded(), elapsedSeconds(startedAt));
                return SourceResult.loaded(
                        source, attempt, lastAttempt.summary(), elapsedSeconds(startedAt),
                        backupPath, lastAttempt.logPath(), lastAttempt.errorLogPath());
            }

            boolean retryableRecordsOnly = lastAttempt.summary() != null
                    && lastAttempt.summary().retryable() > 0
                    && lastAttempt.summary().failed() == lastAttempt.summary().retryable();
            boolean processFailure = lastAttempt.exitCode() != 0 && !permanentError;
            if (permanentError || (!retryableRecordsOnly && !processFailure)) {
                break;
            }
        }

        Summary summary = lastAttempt == null ? null : lastAttempt.summary();
        long attempted = summary == null ? 0 : summary.attempted();
        long loaded = summary == null ? 0 : summary.loaded();
        long failed = summary == null ? 0 : summary.failed();
        long retryable = summary == null ? 0 : summary.retryable();
        String error = lastAttempt == null
                ? "No load attempt was made."
                : "Load did not reconcile with the source input; inspect the per-attempt logs.";
        System.err.printf(
                "Source %s failed after %d attempt(s): attempted=%d loaded=%d failed=%d retryable=%d%n",
                source.code(), lastAttempt == null ? 0 : lastAttempt.attempt(),
                attempted, loaded, failed, retryable);
        return SourceResult.failed(
                source, lastAttempt == null ? 0 : lastAttempt.attempt(), attempted, loaded, failed, retryable,
                backupPath, lastAttempt == null ? null : lastAttempt.logPath(),
                lastAttempt == null ? null : lastAttempt.errorLogPath(), error);
    }

    private static AttemptResult runAttempt(
            Source source,
            long limit,
            long expectedRecords,
            int attempt,
            List<SourceResult> completed,
            int sourceIndex,
            int sourceTotal,
            long orchestrationStarted) throws Exception {
        String prefix = source.code() + "-attempt-" + attempt;
        Path logPath = ORCHESTRATION_LOG_DIR.resolve(prefix + ".log");
        Path errorCopyPath = ORCHESTRATION_LOG_DIR.resolve(prefix + "-errors.json");
        Files.writeString(
                ERROR_LOG_PATH, "[]\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        List<String> command = new ArrayList<>();
        command.add(Path.of("src", "scripts", "run-java.sh").toAbsolutePath().toString());
        command.add("LoadRecords");
        command.add(source.path().toAbsolutePath().normalize().toString());
        if (limit > 0) {
            command.add("--limit");
            command.add(Long.toString(limit));
        }
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logPath.toFile());
        System.out.printf(
                "Source %s started: expected=%d, input=%s%n",
                source.code(), expectedRecords, source.path());

        Process process = builder.start();
        long attemptStarted = System.nanoTime();
        int exitCode = waitForProcess(
                process, source, completed, sourceIndex, sourceTotal,
                attemptStarted, orchestrationStarted, logPath);
        if (Files.isRegularFile(ERROR_LOG_PATH)) {
            Files.copy(ERROR_LOG_PATH, errorCopyPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.writeString(errorCopyPath, "[]\n", StandardCharsets.UTF_8);
        }
        return new AttemptResult(
                attempt, exitCode, parseSummary(logPath), logPath, errorCopyPath);
    }

    private static int waitForProcess(
            Process process,
            Source source,
            List<SourceResult> completed,
            int sourceIndex,
            int sourceTotal,
            long attemptStarted,
            long orchestrationStarted,
            Path logPath) throws IOException, InterruptedException {
        try {
            while (!process.waitFor(HEALTH_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
                long attempted = completed.stream().mapToLong(SourceResult::attempted).sum();
                long failed = completed.stream().mapToLong(SourceResult::failed).sum();
                System.out.printf(
                        "Health: active=%s completed_sources=%d/%d remaining_sources=%d "
                                + "completed_attempted=%d completed_failed=%d error_rate=%.2f%% "
                                + "source_elapsed=%.2fs orchestration_elapsed=%.2fs log=%s%n",
                        source.code(), completed.size(), sourceTotal, sourceTotal - sourceIndex,
                        attempted, failed, percentage(failed, attempted),
                        elapsedSeconds(attemptStarted), elapsedSeconds(orchestrationStarted), logPath);
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private static InputStats inspectInput(Source source) throws IOException {
        if (!Files.isRegularFile(source.path()) || Files.size(source.path()) == 0) {
            throw new IOException("Input is missing or empty: " + source.path());
        }
        Set<String> recordIds = new HashSet<>();
        long recordCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(source.path(), StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Map<String, Object> record = JsonSupport.object(
                        JsonSupport.parse(line), source.code() + " record at line " + lineNumber);
                Object dataSource = record.get("DATA_SOURCE");
                Object recordId = record.get("RECORD_ID");
                if (!source.code().equals(dataSource)) {
                    throw new IOException("Unexpected DATA_SOURCE at " + source.path() + ":" + lineNumber);
                }
                if (!(recordId instanceof String id) || id.isBlank()) {
                    throw new IOException("Missing string RECORD_ID at " + source.path() + ":" + lineNumber);
                }
                if (!recordIds.add(id)) {
                    throw new IOException("Duplicate RECORD_ID in " + source.path() + ": " + id);
                }
                recordCount++;
            }
        }
        return new InputStats(recordCount);
    }

    private static Path createBackup(Source source, boolean testMode) throws IOException {
        if (!Files.isRegularFile(DATABASE_PATH)) {
            throw new IOException("Database backup cannot be created; missing " + DATABASE_PATH);
        }
        String runType = testMode ? "test" : "full";
        Path backupPath = DATABASE_PATH.resolveSibling(
                "G2C.db.before-" + source.code() + "-" + runType + "-" + Instant.now().toEpochMilli() + ".bak");
        Files.copy(DATABASE_PATH, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
        System.out.printf("Backup created: %s%n", backupPath);
        return backupPath;
    }

    private static Summary parseSummary(Path logPath) throws IOException {
        for (String line : Files.readAllLines(logPath, StandardCharsets.UTF_8)) {
            Matcher matcher = LOAD_SUMMARY.matcher(line);
            if (matcher.find()) {
                return new Summary(
                        Long.parseLong(matcher.group(1)),
                        Long.parseLong(matcher.group(2)),
                        Long.parseLong(matcher.group(3)),
                        Long.parseLong(matcher.group(4)));
            }
        }
        return null;
    }

    private static boolean hasPermanentErrors(Path errorLogPath) throws IOException {
        if (errorLogPath == null || !Files.isRegularFile(errorLogPath)) {
            return false;
        }
        return PERMANENT_ERROR.matcher(Files.readString(errorLogPath, StandardCharsets.UTF_8)).find();
    }

    private static void writeSummary(
            List<SourceResult> results, Options options, long orchestrationStarted) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generated_at", Instant.now().toString());
        root.put("strategy", "sequential");
        root.put("test_limit", options.globalLimit() == 0 ? null : options.globalLimit());
        root.put("max_attempts", options.maxAttempts());
        root.put("elapsed_seconds", elapsedSeconds(orchestrationStarted));

        List<Map<String, Object>> sources = new ArrayList<>();
        for (SourceResult result : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", result.source().code());
            row.put("input", result.source().path().toString());
            row.put("status", result.status());
            row.put("attempts", result.attempts());
            row.put("attempted", result.attempted());
            row.put("loaded", result.loaded());
            row.put("failed", result.failed());
            row.put("retryable", result.retryable());
            row.put("duration_seconds", result.durationSeconds());
            row.put("backup", result.backupPath() == null ? null : result.backupPath().toString());
            row.put("log", result.logPath() == null ? null : result.logPath().toString());
            row.put("error_log", result.errorLogPath() == null ? null : result.errorLogPath().toString());
            row.put("error", result.error());
            sources.add(row);
        }
        root.put("sources", sources);
        Files.writeString(
                SUMMARY_PATH,
                JsonSupport.stringify(root) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private static Options parseOptions(String[] args) {
        long globalLimit = 0;
        int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        List<Source> sources = new ArrayList<>();
        Map<String, Long> sourceLimits = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            switch (argument) {
                case "--limit" -> {
                    globalLimit = positiveLong(nextArgument(args, ++i, "--limit"), "--limit");
                }
                case "--max-attempts" -> {
                    long value = positiveLong(nextArgument(args, ++i, "--max-attempts"), "--max-attempts");
                    if (value > 10) {
                        throw new IllegalArgumentException("--max-attempts must not exceed 10.");
                    }
                    maxAttempts = (int) value;
                }
                case "--source-limit" -> {
                    String[] parts = splitAssignment(nextArgument(args, ++i, "--source-limit"));
                    sourceLimits.put(parts[0], positiveLong(parts[1], "source limit"));
                }
                default -> {
                    String[] parts = splitAssignment(argument);
                    sources.add(new Source(parts[0], Path.of(parts[1])));
                }
            }
        }
        Set<String> sourceCodes = new HashSet<>();
        for (Source source : sources) {
            if (!source.code().matches("[A-Z0-9_]+")) {
                throw new IllegalArgumentException("Invalid DATA_SOURCE code: " + source.code());
            }
            if (!sourceCodes.add(source.code())) {
                throw new IllegalArgumentException("Duplicate source argument: " + source.code());
            }
        }
        if (!sourceCodes.containsAll(sourceLimits.keySet())) {
            throw new IllegalArgumentException("--source-limit names a source that was not provided.");
        }
        return new Options(globalLimit, maxAttempts, sources, sourceLimits);
    }

    private static String nextArgument(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value.");
        }
        return args[index];
    }

    private static String[] splitAssignment(String value) {
        int separator = value.indexOf('=');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Expected CODE=VALUE, got: " + value);
        }
        return new String[] {value.substring(0, separator), value.substring(separator + 1)};
    }

    private static long positiveLong(String value, String label) {
        long parsed = Long.parseLong(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return parsed;
    }

    private static double elapsedSeconds(long startedAt) {
        return Math.max((System.nanoTime() - startedAt) / 1_000_000_000.0, 0.001);
    }

    private static double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : numerator * 100.0 / denominator;
    }

    private record Source(String code, Path path) {
    }

    private record Options(
            long globalLimit, int maxAttempts, List<Source> sources, Map<String, Long> sourceLimits) {
    }

    private record InputStats(long recordCount) {
    }

    private record Summary(long attempted, long loaded, long failed, long retryable) {
    }

    private record AttemptResult(
            int attempt, int exitCode, Summary summary, Path logPath, Path errorLogPath) {
    }

    private record SourceResult(
            Source source,
            String status,
            int attempts,
            long attempted,
            long loaded,
            long failed,
            long retryable,
            double durationSeconds,
            Path backupPath,
            Path logPath,
            Path errorLogPath,
            String error) {
        private static SourceResult loaded(
                Source source, int attempts, Summary summary, double duration,
                Path backup, Path log, Path errorLog) {
            return new SourceResult(
                    source, "loaded", attempts, summary.attempted(), summary.loaded(),
                    summary.failed(), summary.retryable(), duration, backup, log, errorLog, null);
        }

        private static SourceResult failed(
                Source source, int attempts, long attempted, long loaded, long failed, long retryable,
                Path backup, Path log, Path errorLog, String error) {
            return new SourceResult(
                    source, "failed", attempts, attempted, loaded, failed, retryable,
                    0, backup, log, errorLog, error);
        }
    }
}
