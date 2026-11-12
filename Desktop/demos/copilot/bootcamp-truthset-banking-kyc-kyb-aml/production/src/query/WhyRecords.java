package query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.SzRecordKey;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_ALL_RELATIONS;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_FEATURE_SCORES;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_MATCH_KEY_DETAILS;

public final class WhyRecords {
    private WhyRecords() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                "Usage: query.WhyRecords <DATA_SOURCE_1> <RECORD_ID_1>"
                    + " <DATA_SOURCE_2> <RECORD_ID_2>");
        }

        SzEnvironment environment = QuerySupport.openEnvironment(
            WhyRecords.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
            flags.add(SZ_INCLUDE_FEATURE_SCORES);
            flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
            flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);

            String response = engine.whyRecords(
                SzRecordKey.of(args[0], args[1]),
                SzRecordKey.of(args[2], args[3]),
                flags);
            JsonObject result = QuerySupport.parseObject(response);
            JsonArray whyResults = QuerySupport.requireArray(result, "WHY_RESULTS");
            if (whyResults.isEmpty()) {
                throw new IllegalStateException(
                    "The SDK response contains no WHY_RESULTS for the record pair.");
            }

            System.out.println(
                "Comparing " + args[0] + ":" + args[1]
                    + " with " + args[2] + ":" + args[3]);
            for (int i = 0; i < whyResults.size(); i++) {
                JsonValue value = whyResults.get(i);
                if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException(
                        "WHY_RESULTS contains a non-object item.");
                }
                printWhyResult((JsonObject) value, i + 1);
            }
        } finally {
            environment.destroy();
        }
    }

    private static void printWhyResult(JsonObject whyResult, int index) {
        JsonObject matchInfo = QuerySupport.requireObject(whyResult, "MATCH_INFO");
        List<String> keys = new ArrayList<>(matchInfo.keySet());
        Collections.sort(keys);

        System.out.println("WHY_RESULTS[" + (index - 1) + "] MATCH_INFO keys: "
            + String.join(", ", keys));
        System.out.println(
            "  Entity IDs: "
                + QuerySupport.optionalString(whyResult, "ENTITY_ID")
                + " / "
                + QuerySupport.optionalString(whyResult, "ENTITY_ID_2"));
        System.out.println(
            "  Match level: "
                + QuerySupport.optionalString(matchInfo, "MATCH_LEVEL_CODE"));
        System.out.println(
            "  Why key: " + QuerySupport.optionalString(matchInfo, "WHY_KEY"));
        System.out.println(
            "  Resolution rule: "
                + QuerySupport.optionalString(matchInfo, "WHY_ERRULE_CODE"));

        printWhyKeyDetails(matchInfo);
        printFeatureScores(matchInfo);
    }

    private static void printWhyKeyDetails(JsonObject matchInfo) {
        JsonValue value = matchInfo.get("WHY_KEY_DETAILS");
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            System.out.println(
                "  Match-key breakdown not returned by this SDK for these flags;"
                    + " falling back to FEATURE_SCORES.");
            return;
        }
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException("WHY_KEY_DETAILS is not an object.");
        }

        JsonObject details = (JsonObject) value;
        JsonArray confirmations = QuerySupport.requireArray(details, "CONFIRMATIONS");
        if (confirmations.isEmpty()) {
            System.out.println(
                "  WHY_KEY_DETAILS.CONFIRMATIONS is present but empty for this"
                    + " comparison; FEATURE_SCORES below provides the evidence.");
            return;
        }

        System.out.println("  Match-key confirmations:");
        for (JsonValue confirmationValue : confirmations) {
            if (confirmationValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "WHY_KEY_DETAILS.CONFIRMATIONS contains a non-object item.");
            }
            JsonObject confirmation = (JsonObject) confirmationValue;
            System.out.println(
                "    " + QuerySupport.optionalString(confirmation, "FTYPE_CODE")
                    + " score="
                    + QuerySupport.optionalString(confirmation, "SCORE")
                    + " bucket="
                    + QuerySupport.optionalString(confirmation, "SCORE_BUCKET")
                    + " source="
                    + QuerySupport.optionalString(confirmation, "SOURCE"));
        }
    }

    private static void printFeatureScores(JsonObject matchInfo) {
        JsonValue value = matchInfo.get("FEATURE_SCORES");
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            System.out.println("  No FEATURE_SCORES were returned.");
            return;
        }
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException("FEATURE_SCORES is not an object.");
        }

        JsonObject featureScores = (JsonObject) value;
        List<String> features = new ArrayList<>(featureScores.keySet());
        Collections.sort(features);
        boolean printed = false;
        for (String feature : features) {
            JsonValue featureValue = featureScores.get(feature);
            if (featureValue.getValueType() != JsonValue.ValueType.ARRAY) {
                throw new IllegalStateException(
                    "FEATURE_SCORES." + feature + " is not an array.");
            }

            JsonArray comparisons = (JsonArray) featureValue;
            for (JsonValue comparisonValue : comparisons) {
                if (comparisonValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException(
                        "FEATURE_SCORES." + feature
                            + " contains a non-object item.");
                }
                JsonObject comparison = (JsonObject) comparisonValue;
                System.out.println(
                    "  Feature " + feature
                        + ": score="
                        + QuerySupport.optionalString(comparison, "SCORE")
                        + " bucket="
                        + QuerySupport.optionalString(comparison, "SCORE_BUCKET")
                        + " behavior="
                        + QuerySupport.optionalString(comparison, "SCORE_BEHAVIOR"));
                printed = true;
            }
        }
        if (!printed) {
            System.out.println("  FEATURE_SCORES is present but empty.");
        }
    }
}
