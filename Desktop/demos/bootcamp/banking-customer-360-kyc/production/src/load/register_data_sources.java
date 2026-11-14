// Registers the five banking DATA_SOURCE codes in the Senzing configuration before loading.
//
// Source: senzing/code-snippets-v4 java/snippets/configuration/RegisterDataSources.java, via
// sdk_guide(topic='configure', language='java', data_sources=[...]) (2026-11-14). The snippet's
// sample codes {"CUSTOMERS", "EMPLOYEES", "WATCHLIST"} are replaced with this project's codes, which
// are the distinct DATA_SOURCE values in data/senzing-ready/*.jsonl. Every Senzing call is kept as
// the snippet has it, including its retry loop on SzReplaceConflictException.
//
// Re-runnable by construction: it reads the current registry with SzConfig.getDataSourceRegistry()
// (signature from get_sdk_reference, 2026-11-14) and registers only codes not already present; when
// nothing is missing it registers no new config at all. JSON is read with src/server/MiniJson.java
// (the snippet itself needs no JSON library).
//
// Run: src/scripts/run-java.sh data/temp/load_classes RegisterBankingDataSources
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.util.*;

class RegisterBankingDataSources {
    static final String[] DATA_SOURCES = {"CORE_BANKING", "CARD_SERVICES", "CREDIT_BUREAU", "BUSINESS_REGISTRY", "WATCHLIST"};

    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(RegisterBankingDataSources.class.getSimpleName())
            .verboseLogging(false)
            .build();
        try {
            SzConfigManager configMgr = env.getConfigManager();
            boolean replacedConfig = false;
            while (!replacedConfig) {
                long configId = configMgr.getDefaultConfigId();
                SzConfig config = configMgr.createConfig(configId);

                Set<String> existing = codesIn(MiniJson.parse(config.getDataSourceRegistry()), new TreeSet<>());
                List<String> missing = new ArrayList<>();
                for (String dataSource : DATA_SOURCES) if (!existing.contains(dataSource)) missing.add(dataSource);
                System.out.println("Already registered : " + existing);
                if (missing.isEmpty()) {
                    System.out.println("Nothing to register; default config " + configId + " unchanged.");
                    break;
                }
                for (String dataSource : missing) {
                    config.registerDataSource(dataSource);
                }
                String modifiedConfig = config.export();
                long newConfigId = configMgr.registerConfig(modifiedConfig);
                try {
                    configMgr.replaceDefaultConfigId(configId, newConfigId);
                    replacedConfig = true;
                    System.out.println("Registered         : " + missing);
                    System.out.println("Default config     : " + configId + " -> " + newConfigId);
                } catch (SzReplaceConflictException e) {
                    // another process changed the default config ID; repeat with the latest one
                }
            }
        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new RuntimeException(e);
        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
    }

    /** Collects every DSRC_CODE value in the registry document, wherever it sits. */
    static Set<String> codesIn(Object node, Set<String> out) {
        if (node instanceof Map) {
            for (Map.Entry<String, Object> e : MiniJson.obj(node).entrySet()) {
                if (e.getKey().equals("DSRC_CODE") && e.getValue() != null) out.add(String.valueOf(e.getValue()));
                else codesIn(e.getValue(), out);
            }
        } else if (node instanceof List) {
            for (Object o : MiniJson.arr(node)) codesIn(o, out);
        }
        return out;
    }
}
