package cleanup;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

class PurgeTruthSet {
    private static final int EXPECTED_RECORD_COUNT = 159;
    private static final Map<String, Integer> EXPECTED_SOURCE_COUNTS = Map.of(
        "CUSTOMERS", 120,
        "REFERENCE", 22,
        "WATCHLIST", 17);
    private static final Pattern DATA_SOURCE_PATTERN =
        Pattern.compile("\"DATA_SOURCE\"\\s*:\\s*\"([^\"\\\\]*)\"");
    private static final Pattern RECORD_ID_PATTERN =
        Pattern.compile("\"RECORD_ID\"\\s*:\\s*\"([^\"\\\\]*)\"");
    private static final Pattern MISSING_RECORD_CODE =
        Pattern.compile("(?:SENZ)?0*33");

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: PurgeTruthSet <truthset-jsonl-path>");
        }

        List<TruthSetKey> keys = readAndValidateKeys(Path.of(args[0]));
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalArgumentException("Unable to get Senzing engine settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(PurgeTruthSet.class.getSimpleName())
            .verboseLogging(false)
            .build();

        int deleted = 0;
        int alreadyAbsent = 0;
        int failures = 0;
        int absentAfterVerification = 0;
        int remaining = 0;
        try {
            SzEngine engine = env.getEngine();
            for (TruthSetKey key : keys) {
                try {
                    engine.deleteRecord(SzRecordKey.of(key.dataSource(), key.recordId()), SZ_NO_FLAGS);
                    deleted++;
                } catch (SzException e) {
                    if (isMissingRecord(e)) {
                        alreadyAbsent++;
                    } else {
                        failures++;
                        System.err.printf(
                            "Delete failed at input line %d (Senzing error code %s)%n",
                            key.lineNumber(), e.getErrorCode());
                    }
                }
            }

            for (TruthSetKey key : keys) {
                try {
                    engine.getEntity(SzRecordKey.of(key.dataSource(), key.recordId()));
                    remaining++;
                } catch (SzException e) {
                    if (isMissingRecord(e)) {
                        absentAfterVerification++;
                    } else {
                        failures++;
                        System.err.printf(
                            "Verification failed at input line %d (Senzing error code %s)%n",
                            key.lineNumber(), e.getErrorCode());
                    }
                }
            }
        } catch (SzException e) {
            failures++;
            System.err.printf("Senzing cleanup failed (error code %s)%n", e.getErrorCode());
        } finally {
            env.destroy();
        }

        System.out.printf(
            "Truth Set cleanup: deleted=%d already_absent=%d verified_absent=%d remaining=%d failures=%d%n",
            deleted, alreadyAbsent, absentAfterVerification, remaining, failures);
        if (remaining != 0 || failures != 0 || absentAfterVerification != EXPECTED_RECORD_COUNT) {
            System.exit(1);
        }
    }

    private static List<TruthSetKey> readAndValidateKeys(Path path) throws IOException {
        List<TruthSetKey> keys = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        Set<SourceRecord> uniqueKeys = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String dataSource = extract(DATA_SOURCE_PATTERN, line, "DATA_SOURCE", lineNumber);
                String recordId = extract(RECORD_ID_PATTERN, line, "RECORD_ID", lineNumber);
                if (!EXPECTED_SOURCE_COUNTS.containsKey(dataSource)
                    || !uniqueKeys.add(new SourceRecord(dataSource, recordId))) {
                    throw new IllegalStateException(
                        "Unexpected or duplicate Truth Set key at input line " + lineNumber);
                }
                counts.merge(dataSource, 1, Integer::sum);
                keys.add(new TruthSetKey(lineNumber, dataSource, recordId));
            }
        }

        if (keys.size() != EXPECTED_RECORD_COUNT || !counts.equals(EXPECTED_SOURCE_COUNTS)) {
            throw new IllegalStateException(
                "Truth Set input did not match the expected record and source counts");
        }
        return keys;
    }

    private static String extract(Pattern pattern, String line, String field, int lineNumber) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                "Missing or escaped " + field + " at input line " + lineNumber);
        }
        return matcher.group(1);
    }

    private static boolean isMissingRecord(SzException exception) {
        return MISSING_RECORD_CODE.matcher(String.valueOf(exception.getErrorCode())).matches();
    }

    private record SourceRecord(String dataSource, String recordId) {}
    private record TruthSetKey(int lineNumber, String dataSource, String recordId) {}
}
