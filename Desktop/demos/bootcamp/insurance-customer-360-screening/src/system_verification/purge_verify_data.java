package system_verification;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * System verification, Step 10: purge the synthetic VERIFY records.
 *
 * Uses SzEngine.deleteRecord(SzRecordKey, Set<SzFlag>) with SZ_NO_FLAGS (signature from the
 * Senzing MCP get_sdk_reference, language='java'). Only the four records this module loaded
 * are deleted, so any other data in the repository is untouched. No direct SQL is used.
 * After deleting, each record is looked up again; zero must remain.
 *
 * Output: PURGED=<n> and REMAINING=<n>.
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class PurgeVerifyData {
    private static final String DS = "VERIFY";
    private static final String[] IDS = {"V1", "V2", "V3", "V4"};

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(PurgeVerifyData.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();

            int purged = 0;
            for (String id : IDS) {
                engine.deleteRecord(SzRecordKey.of(DS, id), SzFlag.SZ_NO_FLAGS);
                purged++;
            }

            int remaining = 0;
            for (String id : IDS) {
                try {
                    engine.getEntity(SzRecordKey.of(DS, id), SzFlag.SZ_NO_FLAGS);
                    remaining++;
                } catch (SzNotFoundException e) {
                    // expected: the record is gone
                }
            }
            System.out.println("PURGED=" + purged);
            System.out.println("REMAINING=" + remaining);

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);

        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(e);

        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
    }
}
