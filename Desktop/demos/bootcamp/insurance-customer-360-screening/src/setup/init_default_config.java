package setup;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * SDK setup, Step 8a: seed the default Senzing configuration into the freshly
 * schema-created datastore (database/G2C.db). One-time administrative task.
 *
 * Derived from senzing/code-snippets-v4 java/snippets/configuration/InitDefaultConfig.java
 * (via the Senzing MCP server's sdk_guide(topic='configure', platform='macos_arm',
 * language='java'); sha256 verified).
 * Deviations from the scaffold:
 *   - reads getDefaultConfigId() first and does nothing if a default is already
 *     registered, so re-running it is safe (signature from get_sdk_reference:
 *     SzConfigManager.getDefaultConfigId() -> long);
 *   - prints the default config id after seeding, to verify the seed landed.
 * The seeding calls (createConfig, export, setDefaultConfig) are unchanged.
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class InitDefaultConfig {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = InitDefaultConfig.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();

        try {
            // get the config and config manager from the environment
            SzConfigManager configMgr = env.getConfigManager();

            long existing = configMgr.getDefaultConfigId();
            if (existing != 0L) {
                System.out.println("DEFAULT_CONFIG_ID=" + existing + " (already registered; nothing to do)");
                return;
            }

            // prepare a config to be modified
            SzConfig    config              = configMgr.createConfig();
            String      configDefinition    = config.export();

            // register the modified config in the repository as the default
            configMgr.setDefaultConfig(configDefinition);

            System.out.println("DEFAULT_CONFIG_ID=" + configMgr.getDefaultConfigId() + " (seeded)");

        } catch (SzException e) {
            // handle any exception that may have occurred
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
