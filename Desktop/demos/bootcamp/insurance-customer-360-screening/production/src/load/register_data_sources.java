package load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Data processing, step 4a: register this project's DATA_SOURCE codes in the Senzing configuration
 * before any record is loaded (a load against an unregistered code fails with SENZ2207).
 *
 * Source: sdk_guide(topic='configure', language='java', data_sources=[...]) returned
 * java/snippets/configuration/RegisterDataSources.java (senzing/code-snippets-v4). Its Senzing calls are
 * kept exactly: getDefaultConfigId, createConfig(configId), registerDataSource, export, registerConfig,
 * replaceDefaultConfigId, and the retry loop on SzReplaceConflictException. Changes, as that snippet's own
 * notes ask:
 *   - the sample codes {"CUSTOMERS", "EMPLOYEES", "WATCHLIST"} are replaced by this project's six codes
 *     (or the codes given on the command line);
 *   - re-runnable by construction: the current config's data source registry
 *     (SzConfig.getDataSourceRegistry(), signature from get_sdk_reference) is read first, only codes not
 *     already present are registered, and when nothing is missing no new config is registered at all.
 *     Idempotency does not rest on catching an error.
 *
 * Usage (from the project root):
 *   src/scripts/sz-java.sh compile src/load/register_data_sources.java
 *   src/scripts/sz-java.sh run load.RegisterDataSources [CODE ...]
 *
 * Output: one line per code (REGISTERED or ALREADY_PRESENT), then CONFIG_ID=<default config id>.
 * The top-level class is package-private so it may live in this snake_case file.
 */
class RegisterDataSources {

    /** The project's DATA_SOURCE codes, as they appear in data/senzing-ready/ and config/data_sources.yaml. */
    static final List<String> PROJECT_CODES = List.of(
        "PC_POLICY_ADMIN", "LIFE_ANNUITY_ADMIN", "CREDIT_BUREAU",
        "BUSINESS_REGISTRY", "SIU_CASES", "SANCTIONS_PEP");

    private static final Pattern DSRC_CODE = Pattern.compile("\"DSRC_CODE\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        List<String> wanted = (args.length > 0) ? Arrays.asList(args) : PROJECT_CODES;

        // create a descriptive instance name (can be anything)
        String instanceName = RegisterDataSources.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();

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

                // read which codes the current config already holds
                Set<String> present = new LinkedHashSet<>();
                Matcher m = DSRC_CODE.matcher(config.getDataSourceRegistry());
                while (m.find()) present.add(m.group(1));

                List<String> missing = new ArrayList<>();
                for (String code : wanted) {
                    if (present.contains(code)) {
                        System.out.println("ALREADY_PRESENT " + code);
                    } else {
                        missing.add(code);
                    }
                }
                if (missing.isEmpty()) {
                    // nothing to change: keep the current default config
                    System.out.println("CONFIG_ID=" + configId);
                    break;
                }

                // add each missing data source
                for (String dataSource : missing) {
                    config.registerDataSource(dataSource);
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
                    for (String code : missing) System.out.println("REGISTERED " + code);
                    System.out.println("CONFIG_ID=" + newConfigId);

                } catch (SzReplaceConflictException e) {
                    // another thread or process changed the default config ID since we
                    // retrieved it, so repeat with the latest default config ID
                }
            }

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
