package loading;

import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_EXPORT_DEFAULT_FLAGS;

public class RepositoryRecordCount {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isEmpty()) {
            throw new IllegalArgumentException(
                "SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(RepositoryRecordCount.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = environment.getEngine();
            long exportHandle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
            long entityCount = 0;
            long recordCount = 0;
            Map<String, Long> recordsBySource = new TreeMap<>();

            try {
                String row;
                while ((row = engine.fetchNext(exportHandle)) != null) {
                    entityCount++;
                    JsonObject exportedEntity;
                    try (javax.json.JsonReader reader = Json.createReader(
                        new StringReader(row))) {
                        exportedEntity = reader.readObject();
                    }

                    JsonObject resolvedEntity =
                        exportedEntity.getJsonObject("RESOLVED_ENTITY");
                    JsonArray records = resolvedEntity == null
                        ? null
                        : resolvedEntity.getJsonArray("RECORDS");
                    if (records == null) {
                        throw new IllegalStateException(
                            "Exported entity is missing RESOLVED_ENTITY.RECORDS.");
                    }
                    for (JsonValue value : records) {
                        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException(
                                "Exported entity contains a non-object record.");
                        }
                        JsonObject record = (JsonObject) value;
                        String dataSource = record.getString("DATA_SOURCE");
                        recordsBySource.put(
                            dataSource, recordsBySource.getOrDefault(dataSource, 0L) + 1L);
                        recordCount++;
                    }
                }
            } finally {
                engine.closeExportReport(exportHandle);
            }

            System.out.println("Resolved entities: " + entityCount);
            System.out.println("Repository records: " + recordCount);
            System.out.println("Records by data source: " + recordsBySource);
        } finally {
            environment.destroy();
        }
    }
}
