import com.senzing.sdk.SzEngine;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_DEFAULT_FLAGS;

public final class BuildCrossSourceReviewQueue {
    private BuildCrossSourceReviewQueue() {
    }

    public static void main(String[] args) throws Exception {
        List<Long> entityIds = readEntityIds();
        Map<String, JsonObject> pairs = new TreeMap<>();

        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (long entityId : entityIds) {
                JsonObject response = QuerySupport.parseObject(
                        engine.getEntity(entityId, SZ_ENTITY_DEFAULT_FLAGS),
                        "getEntity " + entityId);
                JsonObject resolved = QuerySupport.requiredObject(
                        response, "RESOLVED_ENTITY", "getEntity response");
                String entityName = QuerySupport.optionalString(resolved, "ENTITY_NAME");
                Set<String> entitySources = recordSources(resolved);
                JsonArray relatedEntities = QuerySupport.requiredArray(
                        response, "RELATED_ENTITIES", "getEntity response");
                for (JsonValue relatedValue : relatedEntities) {
                    if (relatedValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RELATED_ENTITIES contains a non-object.");
                    }
                    JsonObject related = relatedValue.asJsonObject();
                    long relatedId = related.getJsonNumber("ENTITY_ID").longValue();
                    int isAmbiguous = related.getInt("IS_AMBIGUOUS");
                    String matchLevel = QuerySupport.optionalString(related, "MATCH_LEVEL_CODE");
                    if (!isReviewCandidate(matchLevel, isAmbiguous) || entityId == relatedId) {
                        continue;
                    }

                    Set<String> relatedSources = summarySources(related);
                    Set<String> combinedSources = new TreeSet<>(entitySources);
                    combinedSources.addAll(relatedSources);
                    if (combinedSources.size() < 2) {
                        continue;
                    }

                    long idA = Math.min(entityId, relatedId);
                    long idB = Math.max(entityId, relatedId);
                    String key = idA + "\u001f" + idB + "\u001f"
                            + (matchLevel == null ? "" : matchLevel) + "\u001f"
                            + QuerySupport.optionalString(related, "MATCH_KEY");
                    if (pairs.containsKey(key)) {
                        continue;
                    }

                    boolean currentIsA = entityId == idA;
                    JsonObjectBuilder pair = Json.createObjectBuilder()
                            .add("entity_id_a", idA)
                            .add("entity_id_b", idB)
                            .add("match_level_code", matchLevel == null ? "AMBIGUOUS" : matchLevel)
                            .add("is_ambiguous", isAmbiguous)
                            .add("match_key", QuerySupport.optionalString(related, "MATCH_KEY") == null
                                    ? JsonValue.NULL
                                    : Json.createValue(QuerySupport.optionalString(related, "MATCH_KEY")))
                            .add("data_sources_a", stringArray(
                                    currentIsA ? entitySources : relatedSources))
                            .add("data_sources_b", stringArray(
                                    currentIsA ? relatedSources : entitySources));
                    QuerySupport.addOptionalString(
                            pair, "entity_name_a",
                            currentIsA ? entityName : QuerySupport.optionalString(related, "ENTITY_NAME"));
                    QuerySupport.addOptionalString(
                            pair, "entity_name_b",
                            currentIsA ? QuerySupport.optionalString(related, "ENTITY_NAME") : entityName);
                    pairs.put(key, pair.build());
                }
            }
        }

        JsonArrayBuilder pairArray = Json.createArrayBuilder();
        pairs.values().forEach(pairArray::add);
        JsonObject report = Json.createObjectBuilder()
                .add("entities_scanned", entityIds.size())
                .add("pair_count", pairs.size())
                .add("criteria", "Cross-source POSSIBLY_SAME or ambiguous related-entity results.")
                .add("pairs", pairArray)
                .build();
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("cross_source_review_queue.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.printf(
                "Cross-source review queue: %d entities scanned, %d candidate pairs; output=%s%n",
                entityIds.size(), pairs.size(), outputPath);
    }

    private static List<Long> readEntityIds() throws Exception {
        Path path = QuerySupport.REPORT_DIRECTORY.resolve("master_list.jsonl");
        List<Long> entityIds = new ArrayList<>();
        Set<Long> uniqueIds = new TreeSet<>();
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
                uniqueIds.add(entity.getJsonNumber("entity_id").longValue());
            }
        }
        entityIds.addAll(uniqueIds);
        return List.copyOf(entityIds);
    }

    private static boolean isReviewCandidate(String matchLevel, int isAmbiguous) {
        return isAmbiguous == 1 || "AMBIGUOUS".equals(matchLevel)
                || "POSSIBLY_SAME".equals(matchLevel);
    }

    private static Set<String> recordSources(JsonObject resolvedEntity) {
        JsonArray records = QuerySupport.requiredArray(
                resolvedEntity, "RECORDS", "RESOLVED_ENTITY");
        Set<String> sources = new TreeSet<>();
        for (JsonValue value : records) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("RESOLVED_ENTITY.RECORDS contains a non-object.");
            }
            sources.add(requiredString(value.asJsonObject(), "DATA_SOURCE", "record"));
        }
        return Set.copyOf(sources);
    }

    private static Set<String> summarySources(JsonObject relatedEntity) {
        JsonArray summary = QuerySupport.requiredArray(
                relatedEntity, "RECORD_SUMMARY", "RELATED_ENTITIES[]");
        Set<String> sources = new TreeSet<>();
        for (JsonValue value : summary) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("RECORD_SUMMARY contains a non-object.");
            }
            sources.add(requiredString(value.asJsonObject(), "DATA_SOURCE", "RECORD_SUMMARY[]"));
        }
        return Set.copyOf(sources);
    }

    private static JsonArrayBuilder stringArray(Set<String> values) {
        JsonArrayBuilder result = Json.createArrayBuilder();
        values.forEach(result::add);
        return result;
    }

    private static String requiredString(JsonObject object, String key, String context) {
        String value = QuerySupport.stringValue(object.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(context + " is missing string field " + key + ".");
        }
        return value;
    }
}
