package loading;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

public class ProcessRedo {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isEmpty()) {
            throw new IllegalArgumentException(
                "SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(ProcessRedo.class.getSimpleName())
            .verboseLogging(false)
            .build();

        long processed = 0;
        try {
            SzEngine engine = environment.getEngine();
            // Production uses an always-running processor that sleeps when empty; this batch drain exits.
            for (String redo = engine.getRedoRecord();
                 redo != null;
                 redo = engine.getRedoRecord()) {
                engine.processRedoRecord(redo, SZ_NO_FLAGS);
                processed++;
            }

            System.out.println("Redos processed: " + processed);
            System.out.println("Redo queue reached empty.");
        } finally {
            environment.destroy();
        }
    }
}
