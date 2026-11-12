package configuration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.senzing.sdk.SzConfig;
import com.senzing.sdk.SzConfigManager;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzReplaceConflictException;
import com.senzing.sdk.core.SzCoreEnvironment;

class RegisterTruthSetSources {
    private static final String DATA_FILE = "src/system_verification/truthset_data.jsonl";
    private static final Pattern DATA_SOURCE_PATTERN =
        Pattern.compile("\"DATA_SOURCE\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REGISTRY_CODE_PATTERN =
        Pattern.compile("\"DSRC_CODE\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get Senzing engine settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(RegisterTruthSetSources.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            Set<String> dataSources = readDataSources();
            SzConfigManager configManager = env.getConfigManager();

            while (true) {
                long configId = configManager.getDefaultConfigId();
                SzConfig config = configManager.createConfig(configId);
                Set<String> registeredSources =
                    extractValues(config.getDataSourceRegistry(), REGISTRY_CODE_PATTERN);

                boolean changed = false;
                for (String dataSource : dataSources) {
                    if (!registeredSources.contains(dataSource)) {
                        config.registerDataSource(dataSource);
                        changed = true;
                    }
                }

                if (!changed) {
                    verifyRegisteredSources(registeredSources, dataSources);
                    System.out.println("Truth Set sources already registered: " + dataSources);
                    return;
                }

                long newConfigId = configManager.registerConfig(config.export());
                try {
                    configManager.replaceDefaultConfigId(configId, newConfigId);
                    Set<String> persistedSources = extractValues(
                        configManager.createConfig(newConfigId).getDataSourceRegistry(),
                        REGISTRY_CODE_PATTERN);
                    verifyRegisteredSources(persistedSources, dataSources);
                    System.out.println("Truth Set sources registered: " + dataSources);
                    return;
                } catch (SzReplaceConflictException e) {
                    // Reload the current default config and retry without duplicating sources.
                }
            }
        } catch (SzException e) {
            System.err.println("Senzing error " + e.getErrorCode() + ": " + e.getMessage());
            throw new RuntimeException(e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }

    private static Set<String> readDataSources() throws Exception {
        Set<String> dataSources = new TreeSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(DATA_FILE))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                Matcher matcher = DATA_SOURCE_PATTERN.matcher(line);
                if (!matcher.find()) {
                    throw new IllegalArgumentException(
                        "Missing DATA_SOURCE on line " + lineNumber + " of " + DATA_FILE);
                }
                dataSources.add(matcher.group(1));
            }
        }
        if (dataSources.isEmpty()) {
            throw new IllegalArgumentException("No records found in " + DATA_FILE);
        }
        return dataSources;
    }

    private static Set<String> extractValues(String json, Pattern pattern) {
        Set<String> values = new TreeSet<>();
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static void verifyRegisteredSources(
        Set<String> registeredSources, Set<String> dataSources) {
        if (!registeredSources.containsAll(dataSources)) {
            Set<String> missingSources = new TreeSet<>(dataSources);
            missingSources.removeAll(registeredSources);
            throw new IllegalStateException(
                "Data sources are not registered in the default config: " + missingSources);
        }
    }
}
