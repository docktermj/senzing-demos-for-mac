import com.senzing.sdk.SzEngine;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FindPolicyholderDuplicates {
    private FindPolicyholderDuplicates() {
    }

    public static void main(String[] args) throws Exception {
        Map<Long, TreeSet<String>> policyholdersByEntity = new TreeMap<>();
        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (QuerySupport.SourceRecord inputRecord
                    : QuerySupport.readSourceRecords("POLICYHOLDERS")) {
                JsonObject response = QuerySupport.parseObject(
                        QuerySupport.entityResponse(engine, inputRecord.key()),
                        inputRecord.key().stableKey());
                JsonObject resolvedEntity = QuerySupport.requiredObject(
                        response, "RESOLVED_ENTITY", "getEntity response");
                long entityId = resolvedEntity.getJsonNumber("ENTITY_ID").longValue();
                JsonArray records = QuerySupport.requiredArray(
                        resolvedEntity, "RECORDS", "RESOLVED_ENTITY");

                TreeSet<String> recordIds = policyholdersByEntity.computeIfAbsent(
                        entityId, ignored -> new TreeSet<>());
                for (JsonValue recordValue : records) {
                    if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RESOLVED_ENTITY.RECORDS contains a non-object.");
                    }
                    JsonObject record = recordValue.asJsonObject();
                    if ("POLICYHOLDERS".equals(record.getString("DATA_SOURCE"))) {
                        recordIds.add(record.getString("RECORD_ID"));
                    }
                }
            }
        }

        JsonArrayBuilder duplicateRows = Json.createArrayBuilder();
        int duplicateEntityCount = 0;
        for (Map.Entry<Long, TreeSet<String>> entry : policyholdersByEntity.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            duplicateEntityCount++;
            JsonArrayBuilder recordIds = Json.createArrayBuilder();
            entry.getValue().forEach(recordIds::add);
            duplicateRows.add(Json.createObjectBuilder()
                    .add("entity_id", entry.getKey())
                    .add("policyholder_record_count", entry.getValue().size())
                    .add("policyholder_record_ids", recordIds));
        }

        JsonObject report = Json.createObjectBuilder()
                .add("data_source", "POLICYHOLDERS")
                .add("unique_policyholder_records", QuerySupport.readSourceRecords("POLICYHOLDERS").size())
                .add("duplicate_entity_count", duplicateEntityCount)
                .add("entities", duplicateRows)
                .build();
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("policyholder_duplicates.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.printf(
                "Policyholder duplicate groups: %d; output=%s%n",
                duplicateEntityCount, outputPath);
    }
}
