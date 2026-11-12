package configuration;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

class RegisterTruthSetSources {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(RegisterTruthSetSources.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzConfigManager configMgr = env.getConfigManager();
            String[] dataSources = {"CUSTOMERS", "REFERENCE", "WATCHLIST"};
            boolean replacedConfig = false;

            while (!replacedConfig) {
                long configId = configMgr.getDefaultConfigId();
                SzConfig config = configMgr.createConfig(configId);
                String registry = config.getDataSourceRegistry();
                boolean changed = false;

                for (String dataSource : dataSources) {
                    if (!registry.contains("\"" + dataSource + "\"")) {
                        config.registerDataSource(dataSource);
                        changed = true;
                    }
                }

                if (!changed) {
                    System.out.println("Truth Set data sources are already registered");
                    break;
                }

                String modifiedConfig = config.export();
                long newConfigId = configMgr.registerConfig(modifiedConfig);
                try {
                    configMgr.replaceDefaultConfigId(configId, newConfigId);
                    replacedConfig = true;
                } catch (SzReplaceConflictException e) {
                    // Retry against the latest default configuration.
                }
            }

            System.out.println("Truth Set data-source registration complete");
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
    }
}
