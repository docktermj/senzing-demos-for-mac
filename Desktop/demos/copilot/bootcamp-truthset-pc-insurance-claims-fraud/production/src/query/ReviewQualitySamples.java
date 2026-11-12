import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzFlag;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA;
import static com.senzing.sdk.SzFlag.SZ_WHY_ENTITIES_DEFAULT_FLAGS;

public final class ReviewQualitySamples {
    private static final List<Long> ENTITY_SAMPLE_IDS = List.of(400004L, 500001L, 700039L);
    private static final List<EntityPair> POSSIBLE_MATCH_SAMPLES = List.of(
            new EntityPair(400001L, 500001L),
            new EntityPair(400002L, 700019L),
            new EntityPair(200004L, 300013L));

    private ReviewQualitySamples() {
    }

    public static void main(String[] args) throws Exception {
        Set<SzFlag> entityFlags = new HashSet<>(SZ_ENTITY_DEFAULT_FLAGS);
        entityFlags.add(SZ_ENTITY_INCLUDE_RECORD_JSON_DATA);
        JsonArrayBuilder entitySamples = Json.createArrayBuilder();
        JsonArrayBuilder possibleMatchSamples = Json.createArrayBuilder();

        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (long entityId : ENTITY_SAMPLE_IDS) {
                JsonObject response = QuerySupport.parseObject(
                        engine.getEntity(entityId, entityFlags), "entity " + entityId);
                JsonObject resolvedEntity = QuerySupport.requiredObject(
                        response, "RESOLVED_ENTITY", "getEntity response");
                JsonArray records = QuerySupport.requiredArray(
                        resolvedEntity, "RECORDS", "RESOLVED_ENTITY");
                JsonArrayBuilder recordRows = Json.createArrayBuilder();
                for (JsonValue recordValue : records) {
                    if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RESOLVED_ENTITY.RECORDS contains a non-object.");
                    }
                    JsonObject record = recordValue.asJsonObject();
                    JsonObject sourceRecord = QuerySupport.requiredObject(
                            record, "JSON_DATA", "RESOLVED_ENTITY.RECORDS entry");
                    JsonObjectBuilder row = Json.createObjectBuilder()
                            .add("data_source", record.getString("DATA_SOURCE"))
                            .add("record_id", record.getString("RECORD_ID"))
                            .add("source_record", sourceRecord);
                    QuerySupport.addOptionalString(
                            row, "match_key", QuerySupport.optionalString(record, "MATCH_KEY"));
                    QuerySupport.addOptionalString(
                            row, "match_level_code",
                            QuerySupport.optionalString(record, "MATCH_LEVEL_CODE"));
                    recordRows.add(row);
                }

                JsonObjectBuilder entitySample = Json.createObjectBuilder()
                        .add("entity_id", resolvedEntity.getJsonNumber("ENTITY_ID"))
                        .add("record_count", records.size())
                        .add("records", recordRows);
                QuerySupport.addOptionalString(
                        entitySample, "entity_name",
                        QuerySupport.optionalString(resolvedEntity, "ENTITY_NAME"));
                entitySamples.add(entitySample);
            }

            for (EntityPair pair : POSSIBLE_MATCH_SAMPLES) {
                JsonObject response = QuerySupport.parseObject(
                        engine.whyEntities(
                                pair.entityId1(), pair.entityId2(), SZ_WHY_ENTITIES_DEFAULT_FLAGS),
                        pair.entityId1() + " vs " + pair.entityId2());
                JsonArray whyResults = QuerySupport.requiredArray(
                        response, "WHY_RESULTS", "whyEntities response");
                if (whyResults.isEmpty()) {
                    throw new IllegalStateException(
                            "whyEntities returned no results for " + pair.entityId1() + " vs " + pair.entityId2());
                }

                JsonArrayBuilder resultRows = Json.createArrayBuilder();
                for (JsonValue whyValue : whyResults) {
                    if (whyValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("WHY_RESULTS contains a non-object.");
                    }
                    JsonObject whyResult = whyValue.asJsonObject();
                    JsonObject matchInfo = QuerySupport.requiredObject(
                            whyResult, "MATCH_INFO", "WHY_RESULTS entry");
                    JsonObjectBuilder row = Json.createObjectBuilder()
                            .add("match_level_code", matchInfo.getString("MATCH_LEVEL_CODE"))
                            .add("why_key", matchInfo.getString("WHY_KEY"))
                            .add("why_errule_code", matchInfo.getString("WHY_ERRULE_CODE"))
                            .add("feature_scores", QuerySupport.requiredObject(
                                    matchInfo, "FEATURE_SCORES", "WHY_RESULTS.MATCH_INFO"));
                    resultRows.add(row);
                }
                possibleMatchSamples.add(Json.createObjectBuilder()
                        .add("entity_id_1", pair.entityId1())
                        .add("entity_id_2", pair.entityId2())
                        .add("why_results", resultRows));
            }
        }

        JsonObject report = Json.createObjectBuilder()
                .add("entity_samples", entitySamples)
                .add("possible_match_samples", possibleMatchSamples)
                .add("interpretation", "Review evidence only; these are not adjudicated truth labels.")
                .build();
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("quality_samples.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.printf(
                "Quality evidence: %d entity samples and %d possible-match pairs; output=%s%n",
                ENTITY_SAMPLE_IDS.size(), POSSIBLE_MATCH_SAMPLES.size(), outputPath);
    }

    private record EntityPair(long entityId1, long entityId2) {
    }
}
