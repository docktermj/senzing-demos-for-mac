package verification;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzNotFoundException;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

class PurgeTruthSetData {
    private static final Path DATA_FILE =
        Path.of("src/system_verification/truthset_data.jsonl");
    private static final Map<String, Integer> EXPECTED_SOURCE_COUNTS = Map.of(
        "CUSTOMERS", 120,
        "REFERENCE", 22,
        "WATCHLIST", 17);
    private static final int EXPECTED_RECORD_COUNT =
        EXPECTED_SOURCE_COUNTS.values().stream().mapToInt(Integer::intValue).sum();

    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get Senzing engine settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(PurgeTruthSetData.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            List<RecordKey> records = readAndValidateRecords();
            SzEngine engine = env.getEngine();
            List<String> deleteErrors = new ArrayList<>();

            for (RecordKey record : records) {
                try {
                    engine.deleteRecord(SzRecordKey.of(record.dataSource, record.recordId));
                } catch (SzException e) {
                    deleteErrors.add(record.label() + " (Senzing error " + e.getErrorCode() + ")");
                }
            }

            List<String> remainingRecords = new ArrayList<>();
            for (RecordKey record : records) {
                try {
                    engine.getEntity(SzRecordKey.of(record.dataSource, record.recordId));
                    remainingRecords.add(record.label());
                } catch (SzNotFoundException expectedAbsent) {
                    // This record is absent as required after the purge.
                }
            }

            if (!deleteErrors.isEmpty() || !remainingRecords.isEmpty()) {
                String message = "PURGE=failed; delete_errors=" + deleteErrors
                    + "; remaining_truthset_records=" + remainingRecords;
                System.err.println(message);
                throw new IllegalStateException(message);
            }

            System.out.println(
                "PURGE=passed; records_deleted=" + records.size()
                    + "; remaining_truthset_records=0");
        } finally {
            env.destroy();
        }
    }

    private static List<RecordKey> readAndValidateRecords() throws Exception {
        List<RecordKey> records = new ArrayList<>();
        Map<String, Integer> sourceCounts = new TreeMap<>();
        Set<String> seen = new HashSet<>();

        try (BufferedReader reader =
                 Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }

                JsonObject json;
                try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                    json = jsonReader.readObject();
                }
                String dataSource = json.getString("DATA_SOURCE");
                String recordId = json.getString("RECORD_ID");
                if (!EXPECTED_SOURCE_COUNTS.containsKey(dataSource)) {
                    throw new IllegalArgumentException(
                        "Refusing to purge unexpected data source at input line " + lineNumber);
                }
                if (!seen.add(dataSource + "\u0000" + recordId)) {
                    throw new IllegalArgumentException(
                        "Refusing to purge duplicate record key at input line " + lineNumber);
                }

                records.add(new RecordKey(dataSource, recordId));
                sourceCounts.merge(dataSource, 1, Integer::sum);
            }
        }

        if (records.size() != EXPECTED_RECORD_COUNT || !sourceCounts.equals(EXPECTED_SOURCE_COUNTS)) {
            throw new IllegalArgumentException(
                "Truth Set purge scope mismatch: expected " + EXPECTED_SOURCE_COUNTS
                    + ", found " + sourceCounts);
        }
        return records;
    }

    private static final class RecordKey {
        private final String dataSource;
        private final String recordId;

        private RecordKey(String dataSource, String recordId) {
            this.dataSource = dataSource;
            this.recordId = recordId;
        }

        private String label() {
            return dataSource + "/" + recordId;
        }
    }
}
