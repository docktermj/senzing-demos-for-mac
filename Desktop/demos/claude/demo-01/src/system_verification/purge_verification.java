// purge_verification.java -- System verification, Step 10 (cleanup of the synthetic VERIFY data).
// Deletes only the four synthetic records, one by one, so any other repository state is kept, and
// then confirms none of them can be read back. SDK calls and signatures come from
// get_sdk_reference (topic='parameters', language='java'):
//   deleteRecord(SzRecordKey) -> String
//   getEntity(SzRecordKey, Set<SzFlag>) -> String
// A record that is gone makes getEntity raise a bad-input error (SzBadInputException, the same
// family the scaffold's loader catches); that is the expected outcome here.
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class PurgeVerification {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(PurgeVerification.class.getSimpleName())
            .verboseLogging(false)
            .build();

        String[] ids = {"V1001", "V1002", "V1003", "V2001"};
        int deleted = 0;
        int remaining = 0;
        try {
            SzEngine engine = env.getEngine();
            for (String id : ids) {
                engine.deleteRecord(SzRecordKey.of("VERIFY", id));
                deleted++;
            }
            for (String id : ids) {
                try {
                    engine.getEntity(SzRecordKey.of("VERIFY", id), SZ_ENTITY_DEFAULT_FLAGS);
                    remaining++;   // still readable -> not purged
                } catch (SzBadInputException expected) {
                    // record no longer exists: this is the expected outcome
                }
            }
            System.out.println("PURGE deleted=" + deleted + " remaining_VERIFY_records=" + remaining);

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }
}
