package loading;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

class ProcessTruthSetRedo {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(ProcessTruthSetRedo.class.getSimpleName())
            .verboseLogging(false)
            .build();

        int processed = 0;
        try {
            SzEngine engine = env.getEngine();
            String redo;
            while ((redo = engine.getRedoRecord()) != null) {
                engine.processRedoRecord(redo, SZ_NO_FLAGS);
                processed++;
            }
        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }

        System.out.println("Truth Set redo records processed: " + processed);
    }
}
