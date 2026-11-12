package configuration;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

class RegisterDataSources {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(RegisterDataSources.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzConfigManager configMgr = env.getConfigManager();
            boolean replacedConfig = false;
            while (!replacedConfig) {
                long configId = configMgr.getDefaultConfigId();
                SzConfig config = configMgr.createConfig(configId);

                String[] dataSources = {
                    "BANK_CUSTOMERS",
                    "BUSINESS_REGISTRY",
                    "AML_WATCHLIST"
                };
                for (String dataSource : dataSources) {
                    config.registerDataSource(dataSource);
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
