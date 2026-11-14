// System verification, Step 10: confirm zero VERIFY entities remain after cleanup. Each record key
// must now be unknown to the engine (getEntity raises SzNotFoundException). SDK calls only, no SQL.
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class VerifyPurged {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(VerifyPurged.class.getSimpleName())
            .verboseLogging(false)
            .build();
        int remaining = 0;
        try {
            SzEngine engine = env.getEngine();
            for (String id : new String[] {"V1001", "V1002", "V1003", "V2001"}) {
                try {
                    engine.getEntity(SzRecordKey.of("VERIFY", id), SZ_ENTITY_INCLUDE_ENTITY_NAME_ONLY());
                    remaining++;
                    System.out.println("STILL PRESENT VERIFY/" + id);
                } catch (SzNotFoundException e) {
                    System.out.println("GONE VERIFY/" + id);
                }
            }
        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
        System.out.println("VERIFY_RECORDS_REMAINING " + remaining);
    }

    private static java.util.Set<SzFlag> SZ_ENTITY_INCLUDE_ENTITY_NAME_ONLY() {
        return java.util.EnumSet.of(SZ_ENTITY_INCLUDE_ENTITY_NAME);
    }
}
