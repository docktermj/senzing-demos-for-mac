package loading;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.senzing.sdk.SzConfig;
import com.senzing.sdk.SzConfigManager;
import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

class LoadTruthSet {
    private static final Path DATA_FILE =
        Path.of("src/system_verification/truthset_data.jsonl");
    private static final int EXPECTED_RECORD_COUNT = 159;
    private static final Map<String, Integer> EXPECTED_SOURCE_COUNTS = Map.of(
        "CUSTOMERS", 120,
        "REFERENCE", 22,
        "WATCHLIST", 17);
    private static final Pattern DATA_SOURCE_PATTERN =
        Pattern.compile("\"DATA_SOURCE\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RECORD_ID_PATTERN =
        Pattern.compile("\"RECORD_ID\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REGISTRY_CODE_PATTERN =
        Pattern.compile("\"DSRC_CODE\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get Senzing engine settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(LoadTruthSet.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            ArrayList<RecordLine> records = readAndValidateInput();
            Set<String> sources = new TreeSet<>(EXPECTED_SOURCE_COUNTS.keySet());
            verifyDefaultConfig(env.getConfigManager(), sources);

            SzEngine engine = env.getEngine();
            for (RecordLine record : records) {
                try {
                    engine.addRecord(
                        SzRecordKey.of(record.dataSource, record.recordId),
                        record.json,
                        SZ_NO_FLAGS);
                } catch (Exception e) {
                    reportFailure("record at input line " + record.lineNumber, e);
                    throw e;
                }
            }

            int redoCount = 0;
            String redoRecord;
            while ((redoRecord = engine.getRedoRecord()) != null) {
                try {
                    engine.processRedoRecord(redoRecord, SZ_NO_FLAGS);
                    redoCount++;
                } catch (Exception e) {
                    reportFailure("redo", e);
                    throw e;
                }
            }

            System.out.println(
                "Loaded " + records.size() + " Truth Set records; processed "
                    + redoCount + " redo records; 0 failures.");
        } catch (Exception e) {
            if (!(e instanceof SzException)) {
                System.err.println("Truth Set loading failed: " + e.getClass().getSimpleName());
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }

    private static ArrayList<RecordLine> readAndValidateInput() throws Exception {
        ArrayList<RecordLine> records = new ArrayList<>();
        Map<String, Integer> sourceCounts = new TreeMap<>();
        Set<String> recordKeys = new TreeSet<>();

        try (BufferedReader reader =
                 Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            String json;
            int lineNumber = 0;
            while ((json = reader.readLine()) != null) {
                lineNumber++;
                if (json.trim().isEmpty()) {
                    continue;
                }

                String dataSource = getJsonString(json, DATA_SOURCE_PATTERN, "DATA_SOURCE", lineNumber);
                String recordId = getJsonString(json, RECORD_ID_PATTERN, "RECORD_ID", lineNumber);
                if (!EXPECTED_SOURCE_COUNTS.containsKey(dataSource)) {
                    throw new IllegalArgumentException(
                        "Unexpected data source at input line " + lineNumber);
                }
                if (!recordKeys.add(dataSource + "\u0000" + recordId)) {
                    throw new IllegalArgumentException(
                        "Duplicate record key at input line " + lineNumber);
                }

                records.add(new RecordLine(lineNumber, dataSource, recordId, json));
                sourceCounts.merge(dataSource, 1, Integer::sum);
            }
        }

        if (records.size() != EXPECTED_RECORD_COUNT || !sourceCounts.equals(EXPECTED_SOURCE_COUNTS)) {
            throw new IllegalArgumentException(
                "Truth Set input count mismatch: expected " + EXPECTED_SOURCE_COUNTS
                    + ", found " + sourceCounts);
        }
        return records;
    }

    private static String getJsonString(
        String json, Pattern pattern, String fieldName, int lineNumber) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                "Missing " + fieldName + " at input line " + lineNumber);
        }
        return matcher.group(1);
    }

    private static void verifyDefaultConfig(
        SzConfigManager configManager, Set<String> dataSources) throws SzException {
        long defaultConfigId = configManager.getDefaultConfigId();
        SzConfig config = configManager.createConfig(defaultConfigId);
        Set<String> registeredSources = new TreeSet<>();
        Matcher matcher = REGISTRY_CODE_PATTERN.matcher(config.getDataSourceRegistry());
        while (matcher.find()) {
            registeredSources.add(matcher.group(1));
        }

        if (!registeredSources.containsAll(dataSources) || !registeredSources.contains("VERIFY")) {
            Set<String> missingSources = new TreeSet<>(dataSources);
            missingSources.removeAll(registeredSources);
            throw new IllegalStateException(
                "Default config is missing required sources or VERIFY: " + missingSources);
        }
    }

    private static void reportFailure(String operation, Exception exception) {
        String message =
            "Truth Set " + operation + " failed (" + exception.getClass().getSimpleName() + ")";
        if (exception instanceof SzException) {
            message += ", Senzing error " + ((SzException) exception).getErrorCode();
        }
        System.err.println(message);
    }

    private static final class RecordLine {
        private final int lineNumber;
        private final String dataSource;
        private final String recordId;
        private final String json;

        private RecordLine(int lineNumber, String dataSource, String recordId, String json) {
            this.lineNumber = lineNumber;
            this.dataSource = dataSource;
            this.recordId = recordId;
            this.json = json;
        }
    }
}
