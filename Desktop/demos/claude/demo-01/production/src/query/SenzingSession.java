// SenzingSession.java -- shared helper for the query programs: opens the Senzing environment once
// and keeps it for as long as the session is open, so every engine it hands out stays valid.
// The factory (environment) must outlive every engine it creates: returning only the engine from
// a helper that built the environment in a local hands back a dead engine. This class therefore
// owns the environment and is closed (destroying it) only when the caller is done.
// Environment setup is the MCP scaffold's (SzCoreEnvironment builder, getEngine, destroy).
// It is a shared class, so it lives in a file named after it (bootcamp file-placement rule).
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

final class SenzingSession implements AutoCloseable {
    private final SzEnvironment env;
    final SzEngine engine;

    SenzingSession(String instanceName) throws SzException {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("SENZING_ENGINE_CONFIGURATION_JSON is not set; source src/scripts/senzing-env.sh");
        }
        this.env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        this.engine = env.getEngine();
    }

    @Override
    public void close() {
        // IMPORTANT: make sure to destroy the environment
        env.destroy();
    }
}
