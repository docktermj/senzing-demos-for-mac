package loading;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoadOrchestrator {
    private static final String APPLICATION_CLASSPATH =
        "src/load/.build:src/server/target/dependency/javax.json-1.1.4.jar";
    private static final Pattern SOURCE_HEADER =
        Pattern.compile("^([A-Z][A-Z0-9_]*):\\s*$");
    private static final Pattern SUMMARY_LINE =
        Pattern.compile("^(Total attempted|Successfully loaded|Failed|Retryable failures):\\s*(\\d+)\\s*$");

    private LoadOrchestrator() {}

    public static void main(String[] args) throws Exception {
        Options options = parseArguments(args);
        Path root = Paths.get("").toAbsolutePath().normalize();
        Map<String, LoadTarget> registry =
            readRegistry(root.resolve("config/data_sources.yaml"));
        List<LoadTarget> targets = new ArrayList<>();

        for (String sourceName : options.sourceNames) {
            LoadTarget target = registry.get(sourceName);
            if (target == null) {
                throw new IllegalArgumentException(
                    "Source is not defined in config/data_sources.yaml: " + sourceName);
            }
            if (!"complete".equals(target.mappingStatus)) {
                throw new IllegalStateException(
                    sourceName + " mapping_status must be complete, got: "
                        + target.mappingStatus);
            }
            if ("loaded".equals(target.loadStatus)) {
                target.skipReason = "already loaded";
            } else if (!"not_loaded".equals(target.loadStatus)) {
                throw new IllegalStateException(
                    sourceName + " load_status must be not_loaded, got: "
                        + target.loadStatus);
            }
            targets.add(target);
        }

        List<SourceResult> results = new ArrayList<>();
        long startedAtNanos = System.nanoTime();
        for (LoadTarget target : targets) {
            PreparedInput input = null;
            SourceResult result;
            try {
                input = prepareInput(root, target, options.testCount);
                if (input.skipped) {
                    result = SourceResult.skipped(
                        target.name, input.skipReason);
                    System.out.printf(
                        "SKIPPED %s: %s%n", target.name, input.skipReason);
                } else {
                    result = runSource(
                        root, target.name, input, options.maxRetries);
                    if (result.success) {
                        updateRegistryStatus(
                            root.resolve("config/data_sources.yaml"),
                            target.name, options.testCount > 0);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                System.err.printf(
                    Locale.ROOT, "FAILED %s: %s%n", target.name, e.getMessage());
                result = SourceResult.failed(
                    target.name,
                    input == null ? target.recordCount : input.expectedCount);
            } finally {
                if (input != null) {
                    try {
                        input.cleanup();
                    } catch (IOException e) {
                        System.err.printf(
                            Locale.ROOT, "FAILED %s temporary-file cleanup: %s%n",
                            target.name, e.getMessage());
                    }
                }
            }
            results.add(result);
            printHealth(results, targets.size(), startedAtNanos);
        }

        printOverallSummary(results);
        if (results.stream().anyMatch(
            result -> !result.success && !result.skipped)) {
            System.exit(1);
        }
    }

    private static Options parseArguments(String[] args) {
        List<String> sourceNames = new ArrayList<>();
        int testCount = 0;
        int maxRetries = 4;

        for (String arg : args) {
            if (arg.startsWith("--test-count=")) {
                testCount = parseNonNegativeInt(
                    arg.substring("--test-count=".length()), "--test-count");
            } else if (arg.startsWith("--max-retries=")) {
                maxRetries = parseNonNegativeInt(
                    arg.substring("--max-retries=".length()), "--max-retries");
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            } else {
                sourceNames.add(arg);
            }
        }
        if (sourceNames.isEmpty()) {
            throw new IllegalArgumentException(
                "Usage: loading.LoadOrchestrator <SOURCE> [<SOURCE> ...] "
                    + "[--test-count=N] [--max-retries=N]");
        }
        if (testCount == 0 && argsContainTestCount(args)) {
            throw new IllegalArgumentException("--test-count must be greater than zero.");
        }
        if (sourceNames.stream().distinct().count() != sourceNames.size()) {
            throw new IllegalArgumentException("Source names must not be repeated.");
        }
        return new Options(sourceNames, testCount, maxRetries);
    }

    private static boolean argsContainTestCount(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--test-count=")) {
                return true;
            }
        }
        return false;
    }

    private static int parseNonNegativeInt(String value, String option) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException("negative");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                option + " must be a non-negative integer: " + value, e);
        }
    }

    private static Map<String, LoadTarget> readRegistry(Path registryPath)
        throws IOException {
        if (!Files.isRegularFile(registryPath)) {
            throw new IOException("Source registry does not exist: " + registryPath);
        }

        Map<String, LoadTarget> targets = new LinkedHashMap<>();
        boolean inSources = false;
        boolean inSubset = false;
        LoadTarget current = null;

        try (BufferedReader reader = Files.newBufferedReader(
            registryPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int indent = leadingSpaces(line);
                if (indent == 0) {
                    inSources = "sources:".equals(trimmed);
                    current = null;
                    inSubset = false;
                    continue;
                }
                if (!inSources) {
                    continue;
                }
                if (indent == 2) {
                    Matcher header = SOURCE_HEADER.matcher(trimmed);
                    if (header.matches()) {
                        current = new LoadTarget(header.group(1));
                        targets.put(current.name, current);
                        inSubset = false;
                    }
                    continue;
                }
                if (current == null) {
                    continue;
                }

                KeyValue field = parseKeyValue(line, indent);
                if (field == null) {
                    continue;
                }
                if (indent == 4) {
                    inSubset = "load_subset".equals(field.key) && field.value.isEmpty();
                    if (!inSubset) {
                        current.setSourceField(field.key, field.value);
                    }
                } else if (indent == 6 && inSubset) {
                    current.setSubsetField(field.key, field.value);
                }
            }
        }
        return targets;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static KeyValue parseKeyValue(String line, int indent) {
        String content = line.substring(indent);
        int separator = content.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        return new KeyValue(
            content.substring(0, separator).trim(),
            unquote(content.substring(separator + 1).trim()));
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static PreparedInput prepareInput(
        Path root,
        LoadTarget target,
        int testCount
    ) throws IOException {
        if (!target.skipReason.isEmpty()) {
            return PreparedInput.skipped(target.skipReason);
        }
        Path sourceFile = root.resolve(target.filePath).normalize();
        if (!sourceFile.startsWith(root) || !Files.isRegularFile(sourceFile)) {
            throw new IOException(
                "Source file is missing or outside the repository: " + target.filePath);
        }

        List<Path> temporaryFiles = new ArrayList<>();
        try {
            Path effectiveFile = sourceFile;
            long effectiveCount = countRecords(sourceFile);

            if (!target.subsetStrategy.isEmpty()) {
                if ("first_n".equals(target.subsetStrategy)) {
                    if (target.subsetLimit < 0) {
                        throw new IllegalStateException(
                            target.name + " load_subset.limit must not be negative.");
                    }
                    if (target.subsetLimit == 0) {
                        return PreparedInput.skipped("license capacity is exhausted");
                    }
                    long subsetCount = Math.min(effectiveCount, target.subsetLimit);
                    effectiveFile = writeFirstRecords(
                        root, target.name, sourceFile, subsetCount);
                    temporaryFiles.add(effectiveFile);
                    effectiveCount = subsetCount;
                } else if ("overlap_preserving".equals(target.subsetStrategy)) {
                    if (target.subsetFilePath.isEmpty()) {
                        throw new IllegalStateException(
                            target.name + " overlap_preserving subset has no file_path.");
                    }
                    effectiveFile = root.resolve(target.subsetFilePath).normalize();
                    if (!effectiveFile.startsWith(root)
                        || !Files.isRegularFile(effectiveFile)) {
                        throw new IOException(
                            "Overlap-preserving subset is missing: "
                                + target.subsetFilePath);
                    }
                    effectiveCount = countRecords(effectiveFile);
                    if (target.subsetRecordCount >= 0
                        && effectiveCount != target.subsetRecordCount) {
                        throw new IllegalStateException(
                            target.name + " subset record_count is "
                                + target.subsetRecordCount + " but file contains "
                                + effectiveCount);
                    }
                    if (effectiveCount == 0) {
                        return PreparedInput.skipped(
                            "license capacity is exhausted");
                    }
                } else {
                    throw new IllegalStateException(
                        target.name + " has unsupported load_subset.strategy: "
                            + target.subsetStrategy);
                }
            }

            if (testCount > 0) {
                long sampleCount = Math.min(effectiveCount, testCount);
                if (sampleCount == 0) {
                    throw new IllegalStateException(
                        target.name + " has no records to test.");
                }
                Path sample = writeFirstRecords(
                    root, target.name + "_test", effectiveFile, sampleCount);
                temporaryFiles.add(sample);
                effectiveFile = sample;
                effectiveCount = sampleCount;
            }

            if (testCount == 0 && target.recordCount >= 0
                && effectiveCount != target.recordCount
                && target.subsetStrategy.isEmpty()) {
                throw new IllegalStateException(
                    target.name + " registry record_count is " + target.recordCount
                        + " but the source file contains " + effectiveCount);
            }
            return new PreparedInput(
                effectiveFile, effectiveCount, temporaryFiles, false, "");
        } catch (IOException | RuntimeException e) {
            try {
                cleanupTemporaryFiles(temporaryFiles);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    private static long countRecords(Path input) throws IOException {
        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(
            input, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Path writeFirstRecords(
        Path root,
        String sourceName,
        Path input,
        long requestedCount
    ) throws IOException {
        Path temporaryDirectory = root.resolve("data/temp");
        Files.createDirectories(temporaryDirectory);
        Path output = Files.createTempFile(
            temporaryDirectory, sourceName + "_", ".jsonl");
        long copied = 0;
        try (BufferedReader reader = Files.newBufferedReader(
                input, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(
                output, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            String line;
            while (copied < requestedCount && (line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                writer.write(line);
                writer.newLine();
                copied++;
            }
        } catch (IOException e) {
            Files.deleteIfExists(output);
            throw e;
        }
        if (copied != requestedCount) {
            Files.deleteIfExists(output);
            throw new IOException(
                sourceName + " expected " + requestedCount
                    + " records for its load plan but found " + copied);
        }
        return output;
    }

    private static void cleanupTemporaryFiles(List<Path> temporaryFiles)
        throws IOException {
        IOException failure = null;
        for (Path temporaryFile : temporaryFiles) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static SourceResult runSource(
        Path root,
        String sourceName,
        PreparedInput input,
        int maxRetries
    ) throws Exception {
        int attemptNumber = 0;
        while (true) {
            attemptNumber++;
            AttemptResult attempt = runAttempt(
                root, sourceName, input.path, attemptNumber);
            if (attempt.isSuccessful(input.expectedCount)) {
                System.out.printf(
                    Locale.ROOT,
                    "SOURCE %s expected=%d attempted=%d loaded=%d failed=0 attempts=%d%n",
                    sourceName, input.expectedCount, attempt.attempted,
                    attempt.loaded, attemptNumber);
                return SourceResult.success(
                    sourceName, input.expectedCount, attempt.attempted, attempt.loaded);
            }

            boolean retryable = attempt.exitCode == 0
                && attempt.summaryComplete
                && attempt.failed > 0
                && attempt.retryableFailures == attempt.failed;
            if (retryable && attemptNumber <= maxRetries) {
                long delaySeconds = Math.min(8L, 1L << Math.min(attemptNumber - 1, 3));
                System.err.printf(
                    Locale.ROOT,
                    "RETRY %s attempt=%d retryable_failures=%d delay=%ds%n",
                    sourceName, attemptNumber, attempt.retryableFailures, delaySeconds);
                TimeUnit.SECONDS.sleep(delaySeconds);
                continue;
            }

            System.err.printf(
                Locale.ROOT,
                "SOURCE %s expected=%d attempted=%d loaded=%d failed=%d "
                    + "retryable_failures=%d exit=%d attempts=%d%n",
                sourceName, input.expectedCount, attempt.attempted, attempt.loaded,
                attempt.failed, attempt.retryableFailures, attempt.exitCode, attemptNumber);
            return SourceResult.failed(
                sourceName, input.expectedCount, attempt.attempted,
                attempt.loaded, attempt.failed);
        }
    }

    private static AttemptResult runAttempt(
        Path root,
        String sourceName,
        Path inputPath,
        int attemptNumber
    ) throws IOException, InterruptedException {
        Path logDirectory = root.resolve("logs");
        Files.createDirectories(logDirectory);
        Path logPath = Files.createTempFile(
            logDirectory,
            "orchestrator_" + sourceName + "_attempt" + attemptNumber + "_",
            ".log");
        System.out.println("Attempt log: " + root.relativize(logPath));

        ProcessBuilder builder = new ProcessBuilder(
            root.resolve("src/scripts/senzing-java.sh").toString(),
            APPLICATION_CLASSPATH,
            "loading.LoadRecords",
            inputPath.toString());
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(logPath.toFile());

        int exitCode;
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            Files.deleteIfExists(logPath);
            throw e;
        }
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Files.deleteIfExists(logPath);
            throw e;
        }

        AttemptResult result = new AttemptResult(exitCode);
        try (BufferedReader reader = Files.newBufferedReader(
            logPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + sourceName + "] " + line);
                Matcher summary = SUMMARY_LINE.matcher(line.trim());
                if (summary.matches()) {
                    long value = Long.parseLong(summary.group(2));
                    switch (summary.group(1)) {
                        case "Total attempted":
                            result.attempted = value;
                            result.attemptedSeen = true;
                            break;
                        case "Successfully loaded":
                            result.loaded = value;
                            result.loadedSeen = true;
                            break;
                        case "Failed":
                            result.failed = value;
                            result.failedSeen = true;
                            break;
                        case "Retryable failures":
                            result.retryableFailures = value;
                            result.retryableSeen = true;
                            break;
                        default:
                            throw new IllegalStateException(
                                "Unexpected loader summary field: " + summary.group(1));
                    }
                }
            }
        }
        result.summaryComplete = result.attemptedSeen && result.loadedSeen
            && result.failedSeen && result.retryableSeen;
        return result;
    }

    private static void printOverallSummary(List<SourceResult> results) {
        long expected = 0;
        long attempted = 0;
        long loaded = 0;
        long failed = 0;
        boolean allSuccessful = true;
        int skipped = 0;
        int notLoaded = 0;

        System.out.println("Per-source reconciliation:");
        for (SourceResult result : results) {
            expected += result.expected;
            attempted += result.attempted;
            loaded += result.loaded;
            failed += result.failed;
            allSuccessful &= result.success || result.skipped;
            if (result.skipped) {
                skipped++;
                if (!"already loaded".equals(result.skipReason)) {
                    notLoaded++;
                }
            }
            System.out.printf(
                Locale.ROOT,
                "%s expected=%d attempted=%d loaded=%d failed=%d status=%s%n",
                result.sourceName, result.expected, result.attempted,
                result.loaded, result.failed, result.status());
        }
        System.out.printf(
            Locale.ROOT,
            "TOTAL expected=%d attempted=%d loaded=%d failed=%d skipped_sources=%d status=%s%n",
            expected, attempted, loaded, failed,
            skipped,
            allSuccessful && notLoaded == 0
                && expected == attempted && expected == loaded && failed == 0
                ? "complete" : (allSuccessful ? "partial" : "failed"));
    }

    private static void printHealth(
        List<SourceResult> results,
        int totalSources,
        long startedAtNanos
    ) {
        long attempted = 0;
        long failed = 0;
        int failedSources = 0;
        for (SourceResult result : results) {
            attempted += result.attempted;
            failed += result.failed;
            if (!result.success && !result.skipped) {
                failedSources++;
            }
        }
        String errorRate = attempted == 0
            ? "n/a"
            : String.format(Locale.ROOT, "%.2f%%", 100.0 * failed / attempted);
        double elapsed = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0;
        System.out.printf(
            Locale.ROOT,
            "HEALTH sources_completed=%d sources_remaining=%d failed_sources=%d "
                + "records_attempted=%d records_failed=%d error_rate=%s elapsed=%.1fs%n",
            results.size(), totalSources - results.size(), failedSources,
            attempted, failed, errorRate, elapsed);
    }

    private static void updateRegistryStatus(
        Path registryPath,
        String sourceName,
        boolean testLoad
    ) throws IOException {
        List<String> lines = Files.readAllLines(registryPath, StandardCharsets.UTF_8);
        int sourceStart = -1;
        int sourceEnd = lines.size();
        boolean inSources = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            int indent = leadingSpaces(lines.get(i));
            if (indent == 0) {
                inSources = "sources:".equals(trimmed);
                continue;
            }
            if (!inSources || indent != 2) {
                continue;
            }
            if (sourceStart >= 0) {
                sourceEnd = i;
                break;
            }
            if ((sourceName + ":").equals(trimmed)) {
                sourceStart = i;
            }
        }
        if (sourceStart < 0) {
            throw new IOException(
                "Could not locate source in registry: " + sourceName);
        }

        String statusKey = testLoad ? "test_load_status" : "load_status";
        String statusValue = testLoad ? "complete" : "loaded";
        boolean statusUpdated = false;
        boolean timestampUpdated = false;
        boolean inValidationChecks = false;
        boolean validationCheckUpdated = false;
        int validationStart = -1;
        int validationEnd = sourceEnd;
        String timestamp = OffsetDateTime.now()
            .withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        for (int i = sourceStart + 1; i < sourceEnd; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            int indent = leadingSpaces(line);
            if (indent == 4) {
                if (trimmed.startsWith(statusKey + ":")) {
                    lines.set(i, "    " + statusKey + ": " + statusValue);
                    statusUpdated = true;
                } else if (trimmed.startsWith("updated_at:")) {
                    lines.set(i, "    updated_at: \"" + timestamp + "\"");
                    timestampUpdated = true;
                }
                if ("validation_checks:".equals(trimmed)) {
                    inValidationChecks = true;
                    validationStart = i;
                } else if (inValidationChecks && validationEnd == sourceEnd) {
                    validationEnd = i;
                    inValidationChecks = false;
                }
            } else if (indent == 6 && inValidationChecks
                && trimmed.startsWith("load_count_matches_source:")) {
                lines.set(i, "      load_count_matches_source: pass");
                validationCheckUpdated = true;
            }
        }

        if (!statusUpdated || !timestampUpdated) {
            throw new IOException(
                "Could not update " + statusKey + " and updated_at for " + sourceName);
        }
        if (!testLoad) {
            if (validationStart < 0) {
                throw new IOException(
                    "Could not locate validation_checks for " + sourceName);
            }
            if (!validationCheckUpdated) {
                lines.add(validationEnd, "      load_count_matches_source: pass");
            }
        }

        Path temporary = Files.createTempFile(
            registryPath.getParent(), "data_sources_", ".yaml");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            Files.move(
                temporary, registryPath,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static final class Options {
        private final List<String> sourceNames;
        private final int testCount;
        private final int maxRetries;

        private Options(List<String> sourceNames, int testCount, int maxRetries) {
            this.sourceNames = sourceNames;
            this.testCount = testCount;
            this.maxRetries = maxRetries;
        }
    }

    private static final class KeyValue {
        private final String key;
        private final String value;

        private KeyValue(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class LoadTarget {
        private final String name;
        private String filePath = "";
        private String mappingStatus = "";
        private String loadStatus = "";
        private long recordCount = -1;
        private String skipReason = "";
        private String subsetStrategy = "";
        private String subsetFilePath = "";
        private int subsetLimit = -1;
        private long subsetRecordCount = -1;

        private LoadTarget(String name) {
            this.name = name;
        }

        private void setSourceField(String key, String value) {
            switch (key) {
                case "file_path":
                    filePath = value;
                    break;
                case "mapping_status":
                    mappingStatus = value;
                    break;
                case "load_status":
                    loadStatus = value;
                    break;
                case "record_count":
                    recordCount = Long.parseLong(value);
                    break;
                default:
                    break;
            }
        }

        private void setSubsetField(String key, String value) {
            switch (key) {
                case "strategy":
                    subsetStrategy = value;
                    break;
                case "file_path":
                    subsetFilePath = value;
                    break;
                case "limit":
                    subsetLimit = Integer.parseInt(value);
                    break;
                case "record_count":
                    subsetRecordCount = Long.parseLong(value);
                    break;
                default:
                    break;
            }
        }
    }

    private static final class PreparedInput {
        private final Path path;
        private final long expectedCount;
        private final List<Path> temporaryFiles;
        private final boolean skipped;
        private final String skipReason;

        private PreparedInput(
            Path path,
            long expectedCount,
            List<Path> temporaryFiles,
            boolean skipped,
            String skipReason
        ) {
            this.path = path;
            this.expectedCount = expectedCount;
            this.temporaryFiles = temporaryFiles;
            this.skipped = skipped;
            this.skipReason = skipReason;
        }

        private static PreparedInput skipped(String reason) {
            return new PreparedInput(null, 0, new ArrayList<>(), true, reason);
        }

        private void cleanup() throws IOException {
            cleanupTemporaryFiles(temporaryFiles);
        }
    }

    private static final class AttemptResult {
        private final int exitCode;
        private long attempted;
        private long loaded;
        private long failed;
        private long retryableFailures;
        private boolean attemptedSeen;
        private boolean loadedSeen;
        private boolean failedSeen;
        private boolean retryableSeen;
        private boolean summaryComplete;

        private AttemptResult(int exitCode) {
            this.exitCode = exitCode;
        }

        private boolean isSuccessful(long expectedCount) {
            return exitCode == 0 && summaryComplete
                && attempted == expectedCount && loaded == expectedCount && failed == 0;
        }
    }

    private static final class SourceResult {
        private final String sourceName;
        private final long expected;
        private final long attempted;
        private final long loaded;
        private final long failed;
        private final boolean success;
        private final boolean skipped;
        private final String skipReason;

        private SourceResult(
            String sourceName,
            long expected,
            long attempted,
            long loaded,
            long failed,
            boolean success,
            boolean skipped,
            String skipReason
        ) {
            this.sourceName = sourceName;
            this.expected = expected;
            this.attempted = attempted;
            this.loaded = loaded;
            this.failed = failed;
            this.success = success;
            this.skipped = skipped;
            this.skipReason = skipReason;
        }

        private static SourceResult success(
            String sourceName, long expected, long attempted, long loaded
        ) {
            return new SourceResult(
                sourceName, expected, attempted, loaded, 0, true, false, "");
        }

        private static SourceResult failed(String sourceName, long expected) {
            return new SourceResult(sourceName, expected, 0, 0, 0, false, false, "");
        }

        private static SourceResult failed(
            String sourceName, long expected, long attempted, long loaded, long failed
        ) {
            return new SourceResult(
                sourceName, expected, attempted, loaded, failed, false, false, "");
        }

        private static SourceResult skipped(String sourceName, String reason) {
            return new SourceResult(
                sourceName, 0, 0, 0, 0, false, true, reason);
        }

        private String status() {
            if (skipped) {
                return "already loaded".equals(skipReason)
                    ? "already loaded"
                    : "not_loaded (" + skipReason + ")";
            }
            return success ? "complete" : "failed";
        }
    }
}
