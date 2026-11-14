package setup;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * SDK setup, Step 9: prove the engine initializes against database/G2C.db by
 * creating an SzEngine and calling a method on it (primeEngine), not just a
 * version query.
 *
 * Derived from senzing/code-snippets-v4 java/snippets/initialization/EnginePriming.java
 * (via the Senzing MCP server's generate_scaffold(language='java', workflow='initialize');
 * sha256 verified). The SDK calls are unchanged; only the class name and the
 * success line differ.
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class TestEngineConnection {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = TestEngineConnection.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();

            long start = System.nanoTime();

            System.out.println("Priming Senzing engine...");
            engine.primeEngine();

            long duration = (System.nanoTime() - start) / ONE_MILLION;
            System.out.println("Primed Senzing engine.  (" + duration + "ms)");
            System.out.println("ENGINE_CONNECTION=OK");

        } catch (SzException e) {
            // handle any exception that may have occurred
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);

        } catch (Exception e) {
            System.err.println();
            System.err.println("*** Terminated due to critical error ***");
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

    private static final long ONE_MILLION = 1000000L;
}
