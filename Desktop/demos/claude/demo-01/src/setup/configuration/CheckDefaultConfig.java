package configuration;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Bootcamp check: prints the default configuration ID registered in the datastore
 * (0 means none is registered yet).
 *
 * Environment setup and error handling follow the senzing/code-snippets-v4
 * InitDefaultConfig.java pattern; the one SDK call, SzConfigManager.getDefaultConfigId()
 * -> long, is taken from get_sdk_reference(topic='parameters', language='java').
 */
public class CheckDefaultConfig {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(CheckDefaultConfig.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            long configId = env.getConfigManager().getDefaultConfigId();
            System.out.println("DEFAULT_CONFIG_ID=" + configId);

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);

        } finally {
            env.destroy();
        }
    }
}
