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

import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_ALL_RELATIONS;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_FEATURE_SCORES;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_MATCH_KEY_DETAILS;

public final class HowEntity {
    private HowEntity() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: query.HowEntity <ENTITY_ID>");
        }
        long entityId = Long.parseLong(args[0]);

        SzEnvironment environment = QuerySupport.openEnvironment(
            HowEntity.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
            flags.add(SZ_INCLUDE_FEATURE_SCORES);
            flags.addAll(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
            flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);

            String response = engine.howEntity(entityId, flags);
            JsonObject result = QuerySupport.parseObject(response);
            JsonObject howResults = QuerySupport.requireObject(result, "HOW_RESULTS");
            printFinalState(QuerySupport.requireObject(howResults, "FINAL_STATE"));
            printResolutionSteps(
                QuerySupport.requireArray(howResults, "RESOLUTION_STEPS"));
        } finally {
            environment.destroy();
        }
    }

    private static void printFinalState(JsonObject finalState) {
        String reevaluation = QuerySupport.optionalString(
            finalState, "NEED_REEVALUATION");
        JsonArray virtualEntities = QuerySupport.requireArray(
            finalState, "VIRTUAL_ENTITIES");
        System.out.println("Final state:");
        System.out.println("  NEED_REEVALUATION: " + reevaluation);
        System.out.println("  VIRTUAL_ENTITIES count: " + virtualEntities.size());
        if (!"0".equals(reevaluation) || virtualEntities.size() > 1) {
            System.out.println(
                "  The history may not represent one settled construction; review"
                    + " the Data Processing How-state audit.");
        }

        for (JsonValue value : virtualEntities) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "FINAL_STATE.VIRTUAL_ENTITIES contains a non-object item.");
            }
            JsonObject virtualEntity = (JsonObject) value;
            System.out.println(
                "  Virtual entity "
                    + QuerySupport.optionalString(virtualEntity, "VIRTUAL_ENTITY_ID")
                    + ": " + recordKeys(virtualEntity));
        }
    }

    private static void printResolutionSteps(JsonArray steps) {
        System.out.println("Resolution steps: " + steps.size());
        for (int i = 0; i < steps.size(); i++) {
            JsonValue value = steps.get(i);
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "RESOLUTION_STEPS contains a non-object item.");
            }

            JsonObject step = (JsonObject) value;
            List<String> stepKeys = new ArrayList<>(step.keySet());
            Collections.sort(stepKeys);
            JsonObject matchInfo = QuerySupport.requireObject(step, "MATCH_INFO");
            List<String> matchInfoKeys = new ArrayList<>(matchInfo.keySet());
            Collections.sort(matchInfoKeys);

            String inboundId = QuerySupport.optionalString(
                step, "INBOUND_VIRTUAL_ENTITY_ID");
            String resultId = QuerySupport.optionalString(
                step, "RESULT_VIRTUAL_ENTITY_ID");
            JsonObject virtualEntity1 = QuerySupport.requireObject(
                step, "VIRTUAL_ENTITY_1");
            JsonObject virtualEntity2 = QuerySupport.requireObject(
                step, "VIRTUAL_ENTITY_2");
            String virtualEntity1Id = QuerySupport.optionalString(
                virtualEntity1, "VIRTUAL_ENTITY_ID");
            String virtualEntity2Id = QuerySupport.optionalString(
                virtualEntity2, "VIRTUAL_ENTITY_ID");
            String records1 = recordKeys(virtualEntity1);
            String records2 = recordKeys(virtualEntity2);

            System.out.println(
                "Step " + QuerySupport.optionalString(step, "STEP")
                    + " raw keys: " + String.join(", ", stepKeys));
            System.out.println(
                "  MATCH_INFO keys: " + String.join(", ", matchInfoKeys));
            if (inboundId.equals(virtualEntity1Id)) {
                System.out.println(
                    "  Inbound " + records1 + " joined " + records2
                        + " -> " + resultId);
            } else if (inboundId.equals(virtualEntity2Id)) {
                System.out.println(
                    "  Inbound " + records2 + " joined " + records1
                        + " -> " + resultId);
            } else {
                System.out.println(
                    "  Virtual entities " + virtualEntity1Id + " (" + records1
                        + ") and " + virtualEntity2Id + " (" + records2
                        + ") produced " + resultId
                        + "; inbound ID did not match either side: " + inboundId);
            }
            System.out.println(
                "  Match key: " + QuerySupport.optionalString(matchInfo, "MATCH_KEY"));
            System.out.println(
                "  Rule code: " + QuerySupport.optionalString(matchInfo, "ERRULE_CODE"));
            printMatchKeyDetails(matchInfo);
            printFeatureScores(matchInfo);
        }
    }

    private static String recordKeys(JsonObject virtualEntity) {
        JsonArray memberRecords = QuerySupport.requireArray(
            virtualEntity, "MEMBER_RECORDS");
        List<String> keys = new ArrayList<>();
        for (JsonValue memberValue : memberRecords) {
            if (memberValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "MEMBER_RECORDS contains a non-object item.");
            }
            JsonObject member = (JsonObject) memberValue;
            JsonArray records = QuerySupport.requireArray(member, "RECORDS");
            for (JsonValue recordValue : records) {
                if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException(
                        "MEMBER_RECORDS[].RECORDS contains a non-object item.");
                }
                JsonObject record = (JsonObject) recordValue;
                keys.add(
                    QuerySupport.optionalString(record, "DATA_SOURCE")
                        + ":" + QuerySupport.optionalString(record, "RECORD_ID"));
            }
        }
        return keys.isEmpty() ? "(no member records returned)" : String.join(", ", keys);
    }

    private static void printMatchKeyDetails(JsonObject matchInfo) {
        JsonValue value = matchInfo.get("MATCH_KEY_DETAILS");
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            System.out.println(
                "  Match-key breakdown not returned by this SDK for these flags;"
                    + " falling back to FEATURE_SCORES.");
            return;
        }
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException("MATCH_KEY_DETAILS is not an object.");
        }

        JsonObject details = (JsonObject) value;
        JsonArray confirmations = QuerySupport.requireArray(details, "CONFIRMATIONS");
        if (confirmations.isEmpty()) {
            System.out.println(
                "  MATCH_KEY_DETAILS.CONFIRMATIONS is present but empty for this"
                    + " step; FEATURE_SCORES below provides the evidence.");
            return;
        }

        System.out.println("  Match-key confirmations:");
        for (JsonValue confirmationValue : confirmations) {
            if (confirmationValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "MATCH_KEY_DETAILS.CONFIRMATIONS contains a non-object item.");
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
