// Drains the redo queue after a load so deferred resolution completes before results are read.
// Loop and calls follow senzing/code-snippets-v4 java/snippets/redo/LoadWithRedoViaLoop.java
// (via sdk_guide topic='redo', MCP server 1.46.0): getRedoRecord() until null, then
// processRedoRecord(redo, SZ_NO_FLAGS). Default package, package-private class (INV-237).
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class ProcessRedo {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings).instanceName(ProcessRedo.class.getSimpleName()).verboseLogging(false).build();
        int redone = 0;
        try {
            SzEngine engine = env.getEngine();
            for (String redo = engine.getRedoRecord(); redo != null; redo = engine.getRedoRecord()) {
                engine.processRedoRecord(redo, SZ_NO_FLAGS);
                redone++;
            }
        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
        System.out.println("Redos successfully processed : " + redone);
    }
}
