import com.senzing.sdk.SzEngine;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class BuildMasterList {
    private BuildMasterList() {
    }

    public static void main(String[] args) throws Exception {
        List<QuerySupport.SourceRecord> inputRecords = QuerySupport.readAllSourceRecords();
        Map<Long, EntityView> entities = new TreeMap<>();

        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (QuerySupport.SourceRecord inputRecord : inputRecords) {
                JsonObject response = QuerySupport.parseObject(
                        QuerySupport.entityResponse(engine, inputRecord.key()),
                        inputRecord.key().stableKey());
                JsonObject resolvedEntity = QuerySupport.requiredObject(
                        response, "RESOLVED_ENTITY", "getEntity response");
                long entityId = resolvedEntity.getJsonNumber("ENTITY_ID").longValue();
                String entityName = QuerySupport.optionalString(resolvedEntity, "ENTITY_NAME");
                EntityView entity = entities.computeIfAbsent(entityId, id -> new EntityView(id, entityName));

                JsonArray records = QuerySupport.requiredArray(
                        resolvedEntity, "RECORDS", "RESOLVED_ENTITY");
                for (JsonValue recordValue : records) {
                    if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RESOLVED_ENTITY.RECORDS contains a non-object.");
                    }
                    JsonObject record = recordValue.asJsonObject();
                    String source = record.getString("DATA_SOURCE");
                    String recordId = record.getString("RECORD_ID");
                    JsonObjectBuilder recordOutput = Json.createObjectBuilder()
                            .add("data_source", source)
                            .add("record_id", recordId);
                    QuerySupport.addOptionalString(
                            recordOutput, "match_key", QuerySupport.optionalString(record, "MATCH_KEY"));
                    QuerySupport.addOptionalString(
                            recordOutput, "match_level_code", QuerySupport.optionalString(record, "MATCH_LEVEL_CODE"));
                    entity.addRecord(source + "\u001f" + recordId, recordOutput.build());
                }
            }
        }

        List<JsonObject> output = new ArrayList<>(entities.size());
        for (EntityView entity : entities.values()) {
            output.add(entity.toJson());
        }
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("master_list.jsonl");
        QuerySupport.writeJsonLines(outputPath, output);
        System.out.printf(
                "Master list: %d unique source records across %d resolved entities; output=%s%n",
                inputRecords.size(), output.size(), outputPath);
    }

    private static final class EntityView {
        private final long entityId;
        private final String entityName;
        private final Map<String, JsonObject> records = new TreeMap<>();

        private EntityView(long entityId, String entityName) {
            this.entityId = entityId;
            this.entityName = entityName;
        }

        private void addRecord(String key, JsonObject record) {
            records.put(key, record);
        }

        private JsonObject toJson() {
            JsonArrayBuilder recordArray = Json.createArrayBuilder();
            JsonObjectBuilder sourceCounts = Json.createObjectBuilder();
            Map<String, Integer> counts = new TreeMap<>();
            for (JsonObject record : records.values()) {
                recordArray.add(record);
                counts.merge(record.getString("data_source"), 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                sourceCounts.add(entry.getKey(), entry.getValue());
            }

            JsonObjectBuilder output = Json.createObjectBuilder()
                    .add("entity_id", entityId)
                    .add("record_count", records.size())
                    .add("source_counts", sourceCounts)
                    .add("records", recordArray);
            QuerySupport.addOptionalString(output, "entity_name", entityName);
            return output.build();
        }
    }
}
