package query;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.core.SzCoreEnvironment;

final class QuerySupport {
    private static final Set<String> NON_SEARCH_FIELDS = Set.of(
        "RECORD_TYPE",
        "REL_ANCHOR_DOMAIN",
        "REL_ANCHOR_KEY",
        "REL_POINTER_DOMAIN",
        "REL_POINTER_KEY",
        "REL_POINTER_ROLE");

    private QuerySupport() {}

    static SzEnvironment openEnvironment(String instanceName) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isEmpty()) {
            throw new IllegalArgumentException(
                "SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }
        return SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
    }

    static JsonObject parseObject(String text) {
        try (JsonReader reader = Json.createReader(new StringReader(text))) {
            return reader.readObject();
        }
    }

    static JsonObject requireObject(JsonObject object, String field) {
        JsonValue value = object.get(field);
        if (value == null || value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException(
                "Response is missing the documented object field: " + field);
        }
        return (JsonObject) value;
    }

    static JsonArray requireArray(JsonObject object, String field) {
        JsonValue value = object.get(field);
        if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
            throw new IllegalStateException(
                "Response is missing the documented array field: " + field);
        }
        return (JsonArray) value;
    }

    static String requiredString(JsonObject object, String field) {
        JsonValue value = object.get(field);
        if (!(value instanceof JsonString)) {
            throw new IllegalArgumentException(
                "Required JSON string field is missing or invalid: " + field);
        }
        String result = ((JsonString) value).getString();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                "Required JSON string field is empty: " + field);
        }
        return result;
    }

    static String optionalString(JsonObject object, String field) {
        JsonValue value = object.get(field);
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return "(no value returned for " + field + ")";
        }
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            String result = ((JsonString) value).getString();
            return result.isBlank()
                ? "(present but empty: " + field + ")"
                : result;
        }
        return value.toString();
    }

    static long requiredLong(JsonObject object, String field) {
        javax.json.JsonNumber value = object.getJsonNumber(field);
        if (value == null) {
            throw new IllegalStateException(
                "Response is missing the documented numeric field: " + field);
        }
        return value.longValue();
    }

    static JsonObject entityFromResponse(String response) {
        return requireObject(parseObject(response), "RESOLVED_ENTITY");
    }

    static LinkedHashMap<RecordKey, JsonObject> readUniqueRecords(Path path)
        throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Input file does not exist: " + path);
        }

        LinkedHashMap<RecordKey, JsonObject> records = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(
            path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                JsonObject record;
                try {
                    record = parseObject(trimmed);
                } catch (JsonException e) {
                    throw new IOException(
                        "Invalid JSON at " + path + ":" + lineNumber, e);
                }
                RecordKey key;
                try {
                    key = new RecordKey(
                        requiredString(record, "DATA_SOURCE"),
                        requiredString(record, "RECORD_ID"));
                } catch (IllegalArgumentException e) {
                    throw new IOException(
                        "Invalid record key at " + path + ":" + lineNumber, e);
                }
                records.put(key, record);
            }
        }
        return records;
    }

    static JsonObject searchCriteria(JsonObject feature) {
        JsonObjectBuilder criteria = Json.createObjectBuilder();
        for (Map.Entry<String, JsonValue> entry : feature.entrySet()) {
            if (!NON_SEARCH_FIELDS.contains(entry.getKey())) {
                criteria.add(entry.getKey(), entry.getValue());
            }
        }
        return criteria.build();
    }

    static Set<String> recordSources(JsonObject resolvedEntity) {
        JsonArray summary = requireArray(resolvedEntity, "RECORD_SUMMARY");
        Set<String> sources = new LinkedHashSet<>();
        for (JsonValue value : summary) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "RECORD_SUMMARY contains a non-object item.");
            }
            sources.add(requiredString((JsonObject) value, "DATA_SOURCE"));
        }
        return sources;
    }

    static String summarizeFeatureScores(JsonObject matchInfo) {
        JsonValue value = matchInfo.get("FEATURE_SCORES");
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return "(no value returned for MATCH_INFO.FEATURE_SCORES)";
        }
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException(
                "MATCH_INFO.FEATURE_SCORES is not an object.");
        }

        JsonObject scores = (JsonObject) value;
        List<String> evidence = new ArrayList<>();
        for (Map.Entry<String, JsonValue> entry : scores.entrySet()) {
            if (entry.getValue().getValueType() != JsonValue.ValueType.ARRAY) {
                throw new IllegalStateException(
                    "FEATURE_SCORES." + entry.getKey() + " is not an array.");
            }
            JsonArray featureScores = (JsonArray) entry.getValue();
            if (featureScores.isEmpty()) {
                evidence.add(entry.getKey() + ": present but empty");
                continue;
            }
            for (JsonValue featureValue : featureScores) {
                if (featureValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException(
                        "FEATURE_SCORES." + entry.getKey()
                            + " contains a non-object item.");
                }
                JsonObject featureScore = (JsonObject) featureValue;
                evidence.add(
                    entry.getKey()
                        + " bucket=" + optionalString(featureScore, "SCORE_BUCKET")
                        + " score=" + optionalString(featureScore, "SCORE"));
            }
        }
        return evidence.isEmpty()
            ? "FEATURE_SCORES present but empty"
            : String.join("; ", evidence);
    }

    static String summarizeMatchKeyDetails(JsonObject relatedEntity) {
        JsonValue value = relatedEntity.get("MATCH_KEY_DETAILS");
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return "(no value returned for RELATED_ENTITIES[].MATCH_KEY_DETAILS)";
        }
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException(
                "RELATED_ENTITIES[].MATCH_KEY_DETAILS is not an object.");
        }

        JsonObject details = (JsonObject) value;
        return "confirmations=" + summarizeMatchItems(details, "CONFIRMATIONS")
            + "; denials=" + summarizeMatchItems(details, "DENIALS");
    }

    private static String summarizeMatchItems(JsonObject details, String field) {
        JsonValue value = details.get(field);
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return "(no value returned for " + field + ")";
        }
        if (value.getValueType() != JsonValue.ValueType.ARRAY) {
            throw new IllegalStateException(
                "MATCH_KEY_DETAILS." + field + " is not an array.");
        }

        JsonArray items = (JsonArray) value;
        if (items.isEmpty()) {
            return "present but empty";
        }
        List<String> evidence = new ArrayList<>();
        for (JsonValue item : items) {
            if (item.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "MATCH_KEY_DETAILS." + field + " contains a non-object item.");
            }
            JsonObject matchItem = (JsonObject) item;
            evidence.add(
                optionalString(matchItem, "FTYPE_CODE")
                    + "/" + optionalString(matchItem, "SCORE_BUCKET"));
        }
        return String.join(", ", evidence);
    }

    static JsonArray stringArray(Iterable<String> values) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (String value : values) {
            builder.add(value);
        }
        return builder.build();
    }

    record RecordKey(String dataSource, String recordId) {}
}
