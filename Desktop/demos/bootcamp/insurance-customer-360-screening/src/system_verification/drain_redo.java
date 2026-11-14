package system_verification;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Truth Set visualization, Step 1.2: drain the redo queue after loading, so the entity
 * graph is consistent before the visualization reads it.
 *
 * Derived from the redo half of senzing/code-snippets-v4 java/snippets/redo/LoadWithRedoViaLoop.java
 * (Senzing MCP sdk_guide(topic='redo', language='java')): the getRedoRecord /
 * processRedoRecord(redo, SZ_NO_FLAGS) loop is unchanged. The loading half is not repeated
 * (verify_pipeline.java already loaded the records), and the retry-file handling is reduced
 * to counting, since this is a one-shot drain over a 159-record demo set.
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class DrainRedo {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(DrainRedo.class.getSimpleName())
            .verboseLogging(false)
            .build();

        int redoneCount = 0;
        int errorCount = 0;
        try {
            SzEngine engine = env.getEngine();

            // now that we have loaded the records, check for redos and handle them
            for (String redo = engine.getRedoRecord();
                 redo != null;
                 redo = engine.getRedoRecord())
            {
                try {
                    // process the redo record
                    engine.processRedoRecord(redo, SZ_NO_FLAGS);
                    redoneCount++;
                } catch (SzRetryableException e) {
                    System.err.println("** WARNING ** FAILED TO PROCESS REDO: " + redo + "\n" + e);
                    errorCount++;
                }
            }

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);

        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
            System.out.println("Redos successfully processed : " + redoneCount);
            System.out.println("Failed redos                 : " + errorCount);
        }
    }
}
