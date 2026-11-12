package configuration;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

public class InitDefaultConfig {
    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        String instanceName = InitDefaultConfig.class.getSimpleName();
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();

        try {
            SzConfigManager configMgr = env.getConfigManager();
            SzConfig config = configMgr.createConfig();
            String configDefinition = config.export();
            long configId = configMgr.setDefaultConfig(configDefinition);
            env.reinitialize(configId);
            System.out.println("Default configuration registered: " + configId);
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
