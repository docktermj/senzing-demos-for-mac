import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzFlag;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.senzing.sdk.SzFlag.SZ_INCLUDE_FEATURE_SCORES;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_MATCH_KEY_DETAILS;

public final class BuildHowMergeAudit {
    private BuildHowMergeAudit() {
    }

    public static void main(String[] args) throws Exception {
        Map<Long, Integer> entityRecordCounts = readMultiRecordEntities();
        Set<SzFlag> flags = new HashSet<>();
        flags.add(SZ_INCLUDE_FEATURE_SCORES);
        flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
        JsonArrayBuilder mergeEvents = Json.createArrayBuilder();
        int entitiesAudited = 0;
        int stepCount = 0;

        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (Map.Entry<Long, Integer> entity : entityRecordCounts.entrySet()) {
                long entityId = entity.getKey();
                JsonObject response = QuerySupport.parseObject(
                        engine.howEntity(entityId, flags), "howEntity " + entityId);
                JsonObject howResults = QuerySupport.requiredObject(
                        response, "HOW_RESULTS", "howEntity response");
                JsonArray steps = QuerySupport.requiredArray(
                        howResults, "RESOLUTION_STEPS", "HOW_RESULTS");
                if (steps.isEmpty()) {
                    throw new IllegalStateException(
                            "howEntity returned no resolution steps for multi-record entity "
                                    + entityId + ".");
                }
                for (JsonValue stepValue : steps) {
                    if (stepValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RESOLUTION_STEPS contains a non-object.");
                    }
                    JsonObject step = stepValue.asJsonObject();
                    JsonObject matchInfo = QuerySupport.requiredObject(
                            step, "MATCH_INFO", "resolution step");
                    JsonObjectBuilder event = Json.createObjectBuilder()
                            .add("entity_id", entityId)
                            .add("step", step.getInt("STEP"))
                            .add("match_key", requiredString(matchInfo, "MATCH_KEY"))
                            .add("rule_code", requiredString(matchInfo, "ERRULE_CODE"))
                            .add("virtual_entity_1_records", records(
                                    QuerySupport.requiredObject(
                                            step, "VIRTUAL_ENTITY_1", "resolution step")))
                            .add("virtual_entity_2_records", records(
                                    QuerySupport.requiredObject(
                                            step, "VIRTUAL_ENTITY_2", "resolution step")));
                    mergeEvents.add(event);
                    stepCount++;
                }
                entitiesAudited++;
            }
        }

        JsonObject report = Json.createObjectBuilder()
                .add("multi_record_entities_audited", entitiesAudited)
                .add("resolution_step_count", stepCount)
                .add("merge_events", mergeEvents)
                .build();
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("how_merge_audit.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.printf(
                "How merge audit: %d multi-record entities, %d resolution steps; output=%s%n",
                entitiesAudited, stepCount, outputPath);
    }

    private static Map<Long, Integer> readMultiRecordEntities() throws Exception {
        Path path = QuerySupport.REPORT_DIRECTORY.resolve("master_list.jsonl");
        Map<Long, Integer> entities = new TreeMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                JsonObject entity = QuerySupport.parseObject(
                        line, path + ":" + lineNumber);
                int recordCount = entity.getInt("record_count");
                if (recordCount > 1) {
                    entities.put(entity.getJsonNumber("entity_id").longValue(), recordCount);
                }
            }
        }
        return entities;
    }

    private static JsonArrayBuilder records(JsonObject virtualEntity) {
        JsonArray members = QuerySupport.requiredArray(
                virtualEntity, "MEMBER_RECORDS", "virtual entity");
        JsonArrayBuilder records = Json.createArrayBuilder();
        for (JsonValue memberValue : members) {
            if (memberValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("MEMBER_RECORDS contains a non-object.");
            }
            JsonArray memberRecords = QuerySupport.requiredArray(
                    memberValue.asJsonObject(), "RECORDS", "MEMBER_RECORDS entry");
            for (JsonValue recordValue : memberRecords) {
                if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("MEMBER_RECORDS.RECORDS contains a non-object.");
                }
                JsonObject record = recordValue.asJsonObject();
                records.add(record.getString("DATA_SOURCE") + "/" + record.getString("RECORD_ID"));
            }
        }
        return records;
    }

    private static String requiredString(JsonObject object, String key) {
        String value = QuerySupport.stringValue(object.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("MATCH_INFO is missing string field " + key + ".");
        }
        return value;
    }
}
