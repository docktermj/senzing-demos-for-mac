// register_data_sources.java -- Data processing, Step 4a (register the DATA_SOURCE codes before loading).
// Source: senzing/code-snippets-v4 java/snippets/configuration/RegisterDataSources.java (via
// sdk_guide topic='configure', language='java', data_sources=[...]). DEPARTURES, and nothing else:
//  1. The snippet's sample codes {"CUSTOMERS", "EMPLOYEES", "WATCHLIST"} are replaced by this project's
//     codes (the distinct DATA_SOURCE values in data/senzing-ready/), or by the codes given as arguments.
//  2. Re-runnable by construction: SzConfig.getDataSourceRegistry() (signature from get_sdk_reference)
//     is read first and only codes not yet registered are registered; when none are missing, no new
//     config is registered at all. Parsed with the shared Json reader (src/server/Json.java).
//  3. The class is package-private and the package line is dropped (snake_case filename).
// Every other SDK call (getDefaultConfigId, createConfig, registerDataSource, export, registerConfig,
// replaceDefaultConfigId and the SzReplaceConflictException retry loop) is as the snippet has it.
//
// Usage: src/scripts/run-java.sh RegisterDataSources [CODE ...]
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

class RegisterDataSources {
    static final String[] PROJECT_CODES = {"CRM_CONTACTS", "WEB_ORDERS", "SUPPORT_TICKETS"};

    /** The DATA_SOURCE codes already in a config, read from its data source registry JSON. */
    static Set<String> registeredCodes(SzConfig config) throws SzException {
        Set<String> codes = new LinkedHashSet<>();
        Map<String, Object> registry = Json.obj(Json.parse(config.getDataSourceRegistry()));
        for (Object ds : Json.arr(registry.get("DATA_SOURCES"))) {
            codes.add(Json.str(Json.obj(ds).get("DSRC_CODE"), ""));
        }
        return codes;
    }

    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = RegisterDataSources.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();

        String[] dataSources = (args.length > 0) ? args : PROJECT_CODES;
        try {
            // get the config manager from the environment
            SzConfigManager configMgr = env.getConfigManager();

            // setup a loop to handle race-condition conflicts on
            // replacing the default config ID
            boolean replacedConfig = false;
            while (!replacedConfig) {
                // get the current default config ID
                long configId = configMgr.getDefaultConfigId();

                // get the SzConfig for the config ID
                SzConfig config = configMgr.createConfig(configId);

                // register only the codes this config does not already have
                Set<String> existing = registeredCodes(config);
                List<String> added = new ArrayList<>();
                for (String dataSource : dataSources) {
                    if (!existing.contains(dataSource)) {
                        config.registerDataSource(dataSource);
                        added.add(dataSource);
                    }
                }
                if (added.isEmpty()) {
                    System.out.println("All data sources already registered (config " + configId + "): "
                        + String.join(", ", dataSources));
                    break;
                }

                // export the modified config to JSON text
                String modifiedConfig = config.export();

                // register the modified config in the repository
                long newConfigId = configMgr.registerConfig(modifiedConfig);

                try {
                    // replace the default config
                    configMgr.replaceDefaultConfigId(configId, newConfigId);

                    // if we get here then set the flag indicating success
                    replacedConfig = true;
                    System.out.println("Registered data sources " + added + " (config " + configId + " -> " + newConfigId + ")");

                } catch (SzReplaceConflictException e) {
                    // if we get here then another thread or process has
                    // changed the default config ID since we retrieved it
                    // (i.e.: we have a race condition) so we allow the
                    // loop to repeat with the latest default config ID
                }
            }

            // confirm against the default config now in force
            SzConfig current = configMgr.createConfig(configMgr.getDefaultConfigId());
            System.out.println("Data sources in the default config: " + registeredCodes(current));

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
