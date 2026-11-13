import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.SzRecordKey;

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

public final class DemonstrateWhyAnalysis {
    private DemonstrateWhyAnalysis() {
    }

    public static void main(String[] args) throws Exception {
        Set<SzFlag> flags = new HashSet<>();
        flags.add(SZ_INCLUDE_FEATURE_SCORES);
        flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);

        JsonArrayBuilder whyResults = Json.createArrayBuilder();
        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            String json = engine.whyRecords(
                    SzRecordKey.of("POLICYHOLDERS", "1011"),
                    SzRecordKey.of("SIU_WATCHLIST", "1014"),
                    flags);
            JsonObject response = QuerySupport.parseObject(json, "whyRecords response");
            JsonArray results = QuerySupport.requiredArray(response, "WHY_RESULTS", "whyRecords response");
            if (results.isEmpty()) {
                throw new IllegalStateException("whyRecords returned no WHY_RESULTS.");
            }
            for (JsonValue resultValue : results) {
                if (resultValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("WHY_RESULTS contains a non-object.");
                }
                JsonObject result = resultValue.asJsonObject();
                JsonObject matchInfo = QuerySupport.requiredObject(result, "MATCH_INFO", "WHY_RESULTS entry");
                System.out.println("MATCH_INFO keys: " + new TreeSet<>(matchInfo.keySet()));
                whyResults.add(analyzeResult(matchInfo));
            }
        }

        JsonObject report = Json.createObjectBuilder()
                .add("entity_demonstrated", 400004)
                .add("record_1", recordKey("POLICYHOLDERS", "1011"))
                .add("record_2", recordKey("SIU_WATCHLIST", "1014"))
                .add("why_results", whyResults)
                .build();
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("why_analysis.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.println("Why Analysis evidence written to " + outputPath);
    }

    private static JsonObject analyzeResult(JsonObject matchInfo) {
        JsonArrayBuilder keys = Json.createArrayBuilder();
        new TreeSet<>(matchInfo.keySet()).forEach(keys::add);

        String detailsStatus = "not_returned";
        JsonArrayBuilder confirmations = Json.createArrayBuilder();
        JsonValue detailsValue = matchInfo.get("WHY_KEY_DETAILS");
        if (detailsValue != null) {
            if (detailsValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("WHY_KEY_DETAILS is not an object.");
            }
            JsonArray detailRows = QuerySupport.requiredArray(
                    detailsValue.asJsonObject(), "CONFIRMATIONS", "WHY_KEY_DETAILS");
            detailsStatus = detailRows.isEmpty() ? "empty_for_this_match" : "present";
            for (JsonValue detailValue : detailRows) {
                if (detailValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("WHY_KEY_DETAILS.CONFIRMATIONS contains a non-object.");
                }
                JsonObject detail = detailValue.asJsonObject();
                JsonObjectBuilder row = Json.createObjectBuilder()
                        .add("score", detail.getInt("SCORE"))
                        .add("score_bucket", detail.getString("SCORE_BUCKET"));
                QuerySupport.addOptionalString(row, "feature", QuerySupport.optionalString(detail, "FTYPE_CODE"));
                QuerySupport.addOptionalString(row, "token", QuerySupport.optionalString(detail, "TOKEN"));
                QuerySupport.addOptionalString(row, "source", QuerySupport.optionalString(detail, "SOURCE"));
                QuerySupport.addOptionalString(
                        row, "inbound_value", QuerySupport.optionalString(detail, "INBOUND_FEAT_DESC"));
                QuerySupport.addOptionalString(
                        row, "candidate_value", QuerySupport.optionalString(detail, "CANDIDATE_FEAT_DESC"));
                confirmations.add(row);
            }
        }

        JsonObject featureScores = QuerySupport.requiredObject(
                matchInfo, "FEATURE_SCORES", "MATCH_INFO");
        JsonArrayBuilder featureComparisons = Json.createArrayBuilder();
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
                        .add("score_bucket", score.getString("SCORE_BUCKET"));
                QuerySupport.addOptionalString(
                        comparison, "inbound_value",
                        QuerySupport.optionalString(score, "INBOUND_FEAT_DESC"));
                QuerySupport.addOptionalString(
                        comparison, "candidate_value",
                        QuerySupport.optionalString(score, "CANDIDATE_FEAT_DESC"));
                featureComparisons.add(comparison);
            }
        }

        JsonObjectBuilder output = Json.createObjectBuilder()
                .add("match_info_keys", keys)
                .add("match_level_code", requiredString(matchInfo, "MATCH_LEVEL_CODE"))
                .add("why_rule_code", requiredString(matchInfo, "WHY_ERRULE_CODE"))
                .add("why_key", requiredString(matchInfo, "WHY_KEY"))
                .add("why_key_details_status", detailsStatus)
                .add("feature_comparisons", featureComparisons)
                .add("feature_scores", featureScores);
        if (!"not_returned".equals(detailsStatus)) {
            output.add("why_key_confirmations", confirmations);
        }
        return output.build();
    }

    private static JsonObject recordKey(String dataSource, String recordId) {
        return Json.createObjectBuilder()
                .add("data_source", dataSource)
                .add("record_id", recordId)
                .build();
    }

    private static String requiredString(JsonObject object, String key) {
        JsonValue value = object.get(key);
        String text = QuerySupport.stringValue(value);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Missing string field " + key + " in MATCH_INFO.");
        }
        return text;
    }
}
