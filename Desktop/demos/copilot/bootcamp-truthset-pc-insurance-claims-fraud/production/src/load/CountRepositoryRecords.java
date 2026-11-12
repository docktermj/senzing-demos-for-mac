import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.util.List;
import java.util.Map;

import static com.senzing.sdk.SzFlag.SZ_EXPORT_DEFAULT_FLAGS;

public final class CountRepositoryRecords {
    private CountRepositoryRecords() {
    }

    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalArgumentException("SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName(CountRepositoryRecords.class.getSimpleName())
                .verboseLogging(false)
                .build();
        try {
            SzEngine engine = environment.getEngine();
            long exportHandle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
            long recordCount = 0;
            try {
                String row;
                while ((row = engine.fetchNext(exportHandle)) != null) {
                    Map<String, Object> entity = JsonSupport.object(
                            JsonSupport.parse(row), "entity export");
                    Map<String, Object> resolvedEntity = JsonSupport.object(
                            entity.get("RESOLVED_ENTITY"), "resolved entity");
                    List<Object> records = JsonSupport.array(
                            resolvedEntity.get("RECORDS"), "resolved entity records");
                    recordCount += records.size();
                }
            } finally {
                engine.closeExportReport(exportHandle);
            }
            System.out.println("record_count=" + recordCount);
        } finally {
            environment.destroy();
        }
    }
}
