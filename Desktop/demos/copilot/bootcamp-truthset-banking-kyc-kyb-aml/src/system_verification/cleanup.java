package verification;

import java.util.regex.Pattern;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

class Cleanup {
    private static final String DATA_SOURCE = "VERIFY";
    private static final String[] RECORD_IDS = {
        "SYN-001", "SYN-002", "SYN-003", "SYN-004"
    };

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(Cleanup.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();
            int deleted = 0;
            for (String recordId : RECORD_IDS) {
                try {
                    engine.getEntity(
                        SzRecordKey.of(DATA_SOURCE, recordId), SzFlag.SZ_ENTITY_DEFAULT_FLAGS);
                    engine.deleteRecord(SzRecordKey.of(DATA_SOURCE, recordId));
                    deleted++;
                } catch (SzException e) {
                    if (!isMissingRecord(e)) {
                        throw e;
                    }
                }
            }
            System.out.println("VERIFY_RECORDS_DELETED=" + deleted);

            int absent = 0;
            for (String recordId : RECORD_IDS) {
                try {
                    String response = engine.getEntity(
                        SzRecordKey.of(DATA_SOURCE, recordId), SzFlag.SZ_ENTITY_DEFAULT_FLAGS);
                    throw new IllegalStateException(
                        "Verification record remains after cleanup: " + recordId + " " + response);
                } catch (SzException e) {
                    if (!isMissingRecord(e)) {
                        throw e;
                    }
                    absent++;
                }
            }
            System.out.println("VERIFY_RECORDS_ABSENT=" + absent);
        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }

    private static boolean isMissingRecord(SzException exception) {
        return Pattern.matches("(?:SENZ)?0*33", String.valueOf(exception.getErrorCode()));
    }
}
