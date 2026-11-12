import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzFlag;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_ALL_RELATIONS;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_FEATURE_SCORES;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_MATCH_KEY_DETAILS;

public final class DemonstrateHowAnalysis {
    private DemonstrateHowAnalysis() {
    }

    public static void main(String[] args) throws Exception {
        Set<SzFlag> flags = new HashSet<>();
        flags.add(SZ_INCLUDE_FEATURE_SCORES);
        flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
        flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);

        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            String json = engine.howEntity(400004L, flags);
            JsonObject response = QuerySupport.parseObject(json, "howEntity response");
            JsonObject howResults = QuerySupport.requiredObject(response, "HOW_RESULTS", "howEntity response");
            JsonObject finalState = QuerySupport.requiredObject(howResults, "FINAL_STATE", "HOW_RESULTS");
            JsonArray steps = QuerySupport.requiredArray(howResults, "RESOLUTION_STEPS", "HOW_RESULTS");
            if (steps.isEmpty()) {
                throw new IllegalStateException("howEntity returned no RESOLUTION_STEPS.");
            }

            JsonArrayBuilder parsedSteps = Json.createArrayBuilder();
            for (JsonValue stepValue : steps) {
                if (stepValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("RESOLUTION_STEPS contains a non-object.");
                }
                JsonObject step = stepValue.asJsonObject();
                JsonObject matchInfo = QuerySupport.requiredObject(step, "MATCH_INFO", "resolution step");
                System.out.println("STEP keys: " + new TreeSet<>(step.keySet()));
                System.out.println("MATCH_INFO keys: " + new TreeSet<>(matchInfo.keySet()));
                parsedSteps.add(parseStep(step, matchInfo));
            }

            JsonObject parsedFinalState = parseFinalState(finalState);
            JsonObject report = Json.createObjectBuilder()
                    .add("entity_id", 400004)
                    .add("final_state", parsedFinalState)
                    .add("resolution_steps", parsedSteps)
                    .build();
            Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("how_analysis.json");
            QuerySupport.writeJson(outputPath, report);
            System.out.printf(
                    "How Analysis for entity 400004: %d steps, %d final virtual entity group(s), "
                            + "need_reevaluation=%d; output=%s%n",
                    steps.size(),
                    parsedFinalState.getInt("virtual_entity_count"),
                    parsedFinalState.getInt("need_reevaluation"),
                    outputPath);
        }
    }

    private static JsonObject parseStep(JsonObject step, JsonObject matchInfo) {
        JsonArrayBuilder matchInfoKeys = Json.createArrayBuilder();
        new TreeSet<>(matchInfo.keySet()).forEach(matchInfoKeys::add);

        JsonObjectBuilder output = Json.createObjectBuilder()
                .add("step", step.getInt("STEP"))
                .add("virtual_entity_1", parseVirtualEntity(
                        QuerySupport.requiredObject(step, "VIRTUAL_ENTITY_1", "resolution step")))
                .add("virtual_entity_2", parseVirtualEntity(
                        QuerySupport.requiredObject(step, "VIRTUAL_ENTITY_2", "resolution step")))
                .add("match_info_keys", matchInfoKeys)
                .add("match_key", requiredString(matchInfo, "MATCH_KEY"))
                .add("rule_code", requiredString(matchInfo, "ERRULE_CODE"))
                .add("feature_scores", parseFeatureScores(
                        QuerySupport.requiredObject(matchInfo, "FEATURE_SCORES", "MATCH_INFO")));

        JsonValue detailsValue = matchInfo.get("MATCH_KEY_DETAILS");
        if (detailsValue == null) {
            output.add("match_key_details_status", "not_returned");
        } else {
            if (detailsValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("MATCH_KEY_DETAILS is not an object.");
            }
            JsonArray confirmations = QuerySupport.requiredArray(
                    detailsValue.asJsonObject(), "CONFIRMATIONS", "MATCH_KEY_DETAILS");
            output.add(
                    "match_key_details_status",
                    confirmations.isEmpty() ? "empty_for_this_step" : "present");
            if (!confirmations.isEmpty()) {
                output.add("match_key_confirmations", parseConfirmations(confirmations));
            }
        }
        return output.build();
    }

    private static JsonArray parseFeatureScores(JsonObject featureScores) {
        JsonArrayBuilder comparisons = Json.createArrayBuilder();
        for (String feature : new TreeSet<>(featureScores.keySet())) {
            JsonArray scores = QuerySupport.requiredArray(featureScores, feature, "FEATURE_SCORES");
            for (JsonValue scoreValue : scores) {
                if (scoreValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("FEATURE_SCORES entry is not an object.");
                }
                JsonObject score = scoreValue.asJsonObject();
                JsonObjectBuilder comparison = Json.createObjectBuilder()
                        .add("feature", feature)
                        .add("score", score.getInt("SCORE"))
                        .add("score_bucket", requiredString(score, "SCORE_BUCKET"));
                QuerySupport.addOptionalString(
                        comparison, "inbound_value",
                        QuerySupport.optionalString(score, "INBOUND_FEAT_DESC"));
                QuerySupport.addOptionalString(
                        comparison, "candidate_value",
                        QuerySupport.optionalString(score, "CANDIDATE_FEAT_DESC"));
                comparisons.add(comparison);
            }
        }
        return comparisons.build();
    }

    private static JsonArray parseConfirmations(JsonArray confirmations) {
        JsonArrayBuilder output = Json.createArrayBuilder();
        for (JsonValue confirmationValue : confirmations) {
            if (confirmationValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("MATCH_KEY_DETAILS.CONFIRMATIONS contains a non-object.");
            }
            JsonObject confirmation = confirmationValue.asJsonObject();
            JsonObjectBuilder row = Json.createObjectBuilder()
                    .add("score", confirmation.getInt("SCORE"))
                    .add("score_bucket", requiredString(confirmation, "SCORE_BUCKET"));
            QuerySupport.addOptionalString(
                    row, "feature", QuerySupport.optionalString(confirmation, "FTYPE_CODE"));
            QuerySupport.addOptionalString(
                    row, "token", QuerySupport.optionalString(confirmation, "TOKEN"));
            QuerySupport.addOptionalString(
                    row, "source", QuerySupport.optionalString(confirmation, "SOURCE"));
            QuerySupport.addOptionalString(
                    row, "inbound_value", QuerySupport.optionalString(confirmation, "INBOUND_FEAT_DESC"));
            QuerySupport.addOptionalString(
                    row, "candidate_value", QuerySupport.optionalString(confirmation, "CANDIDATE_FEAT_DESC"));
            output.add(row);
        }
        return output.build();
    }

    private static JsonObject parseVirtualEntity(JsonObject virtualEntity) {
        JsonArray members = QuerySupport.requiredArray(
                virtualEntity, "MEMBER_RECORDS", "virtual entity");
        JsonArrayBuilder parsedMembers = Json.createArrayBuilder();
        for (JsonValue memberValue : members) {
            if (memberValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("MEMBER_RECORDS contains a non-object.");
            }
            JsonObject member = memberValue.asJsonObject();
            JsonArray records = QuerySupport.requiredArray(member, "RECORDS", "MEMBER_RECORDS entry");
            JsonArrayBuilder parsedRecords = Json.createArrayBuilder();
            for (JsonValue recordValue : records) {
                if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("MEMBER_RECORDS.RECORDS contains a non-object.");
                }
                JsonObject record = recordValue.asJsonObject();
                parsedRecords.add(Json.createObjectBuilder()
                        .add("data_source", requiredString(record, "DATA_SOURCE"))
                        .add("record_id", requiredString(record, "RECORD_ID")));
            }
            parsedMembers.add(Json.createObjectBuilder()
                    .add("internal_id", member.getJsonNumber("INTERNAL_ID"))
                    .add("records", parsedRecords));
        }
        return Json.createObjectBuilder()
                .add("virtual_entity_id", requiredString(virtualEntity, "VIRTUAL_ENTITY_ID"))
                .add("member_records", parsedMembers)
                .build();
    }

    private static JsonObject parseFinalState(JsonObject finalState) {
        JsonArray virtualEntities = QuerySupport.requiredArray(
                finalState, "VIRTUAL_ENTITIES", "FINAL_STATE");
        JsonArrayBuilder parsedEntities = Json.createArrayBuilder();
        for (JsonValue entityValue : virtualEntities) {
            if (entityValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("FINAL_STATE.VIRTUAL_ENTITIES contains a non-object.");
            }
            parsedEntities.add(parseVirtualEntity(entityValue.asJsonObject()));
        }
        return Json.createObjectBuilder()
                .add("need_reevaluation", finalState.getInt("NEED_REEVALUATION"))
                .add("virtual_entity_count", virtualEntities.size())
                .add("virtual_entities", parsedEntities)
                .build();
    }

    private static String requiredString(JsonObject object, String key) {
        String value = QuerySupport.stringValue(object.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing string field " + key + ".");
        }
        return value;
    }
}
