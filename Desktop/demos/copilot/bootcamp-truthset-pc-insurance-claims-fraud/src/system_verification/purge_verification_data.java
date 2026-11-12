package verification;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.SzNotFoundException;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

class PurgeVerificationData {
    private static final String DATA_FILE =
        "src/system_verification/verification_data.jsonl";
    private static final String DATA_SOURCE = "VERIFY";
    private static final Set<SzFlag> ENTITY_FLAGS = EnumSet.of(
        SzFlag.SZ_ENTITY_INCLUDE_RECORD_DATA,
        SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA
    );

    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(PurgeVerificationData.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();
            List<String> recordIds = readRecordIds();
            List<String> deleteErrors = new ArrayList<>();

            for (String recordId : recordIds) {
                try {
                    engine.deleteRecord(
                        SzRecordKey.of(DATA_SOURCE, recordId), SzFlag.SZ_NO_FLAGS);
                } catch (com.senzing.sdk.SzException e) {
                    deleteErrors.add(recordId + ": " + e);
                }
            }

            List<String> remainingRecords = new ArrayList<>();
            for (String recordId : recordIds) {
                try {
                    engine.getEntity(
                        SzRecordKey.of(DATA_SOURCE, recordId), ENTITY_FLAGS);
                    remainingRecords.add(recordId);
                } catch (SzNotFoundException expectedAbsent) {
                    // This record is absent as required after the purge.
                }
            }

            if (!deleteErrors.isEmpty() || !remainingRecords.isEmpty()) {
                throw new IllegalStateException(
                    "PURGE=failed; delete_errors=" + deleteErrors
                        + "; remaining_VERIFY_records=" + remainingRecords);
            }

            System.out.println(
                "PURGE=passed; records_deleted=" + recordIds.size()
                    + "; remaining_VERIFY_records=0");
        } finally {
            env.destroy();
        }
    }

    private static List<String> readRecordIds() throws Exception {
        List<String> recordIds = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(
            Path.of(DATA_FILE), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String dataSource = stringField(line, "DATA_SOURCE");
                if (!DATA_SOURCE.equals(dataSource)) {
                    throw new IllegalArgumentException(
                        "Refusing to purge non-VERIFY source: " + dataSource);
                }
                recordIds.add(stringField(line, "RECORD_ID"));
            }
        }
        return recordIds;
    }

    private static String stringField(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
            .matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing JSON string field: " + key);
        }
        return matcher.group(1);
    }
}
