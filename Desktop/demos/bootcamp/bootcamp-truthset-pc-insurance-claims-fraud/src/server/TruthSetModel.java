import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

final class TruthSetModel {
    static final int NODE_CAP = 300;
    static final int LIST_CAP = 200;
    static final int FEATURE_SAMPLE_CAP = 40;
    static final int MATCH_KEY_CAP = 20;

    private static final Set<SzFlag> ENTITY_FLAGS = SZ_ENTITY_DEFAULT_FLAGS;
    private static final Set<SzFlag> EXPORT_FLAGS = mergeFlags(
            SZ_EXPORT_INCLUDE_ALL_ENTITIES,
            SZ_ENTITY_DEFAULT_FLAGS);
    private static final Set<SzFlag> WHY_FLAGS = mergeFlags(
            SZ_WHY_RECORDS_DEFAULT_FLAGS,
            SZ_ENTITY_INCLUDE_ALL_RELATIONS,
            Set.of(SZ_INCLUDE_MATCH_KEY_DETAILS));
    private static final Set<SzFlag> WHY_SINGLE_FLAGS = mergeFlags(
            SZ_WHY_RECORD_IN_ENTITY_DEFAULT_FLAGS,
            SZ_ENTITY_INCLUDE_ALL_RELATIONS,
            Set.of(SZ_INCLUDE_MATCH_KEY_DETAILS));
    private static final Set<SzFlag> HOW_FLAGS = mergeFlags(
            SZ_HOW_ENTITY_DEFAULT_FLAGS,
            SZ_ENTITY_INCLUDE_ALL_RELATIONS,
            Set.of(SZ_INCLUDE_MATCH_KEY_DETAILS));
    private static final Set<SzFlag> SEARCH_FLAGS = mergeFlags(
            SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS,
            SZ_ENTITY_INCLUDE_ALL_RELATIONS,
            Set.of(SZ_INCLUDE_MATCH_KEY_DETAILS));

    private final SzEngine engine;
    private final Map<Long, EntityInfo> entities = new LinkedHashMap<>();
    private final List<EdgeInfo> edges = new ArrayList<>();
    private final List<String> searchCandidates = new ArrayList<>();
    private final JsonObject features;
    private volatile JsonArray verifiedExamplesCache;

    private TruthSetModel(SzEngine engine) {
        this.engine = engine;
        this.features = emptyFeatures(0);
    }

    static TruthSetModel fromRecords(SzEngine engine, Path recordsFile) throws Exception {
        TruthSetModel model = new TruthSetModel(engine);
        Set<String> seenKeys = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(recordsFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                JsonObject record = parseObject(line);
                String dataSource = string(record, "DATA_SOURCE", "");
                String recordId = string(record, "RECORD_ID", "");
                if (dataSource.isBlank() || recordId.isBlank()) {
                    throw new IllegalArgumentException("A Truth Set record is missing DATA_SOURCE or RECORD_ID.");
                }
                String key = dataSource + "\u0000" + recordId;
                if (!seenKeys.add(key)) {
                    continue;
                }
                model.addSearchCandidates(record);
                String response = engine.getEntity(SzRecordKey.of(dataSource, recordId), ENTITY_FLAGS);
                model.addEntityResponse(parseObject(response));
            }
        }

        model.finishRelationships();
        return model.withFeatures(model.buildFeatures());
    }

    static TruthSetModel fromExport(SzEngine engine) throws Exception {
        TruthSetModel model = new TruthSetModel(engine);
        long handle = engine.exportJsonEntityReport(EXPORT_FLAGS);
        try {
            String row;
            while ((row = engine.fetchNext(handle)) != null) {
                model.addEntityResponse(parseObject(row));
            }
        } finally {
            engine.closeExportReport(handle);
        }
        model.finishRelationships();
        return model.withFeatures(model.buildFeatures());
    }

    private TruthSetModel withFeatures(JsonObject value) {
        return new TruthSetModel(engine, entities, edges, searchCandidates, value);
    }

    private TruthSetModel(SzEngine engine,
                          Map<Long, EntityInfo> entities,
                          List<EdgeInfo> edges,
                          List<String> searchCandidates,
                          JsonObject features) {
        this.engine = engine;
        this.entities.putAll(entities);
        this.edges.addAll(edges);
        this.searchCandidates.addAll(searchCandidates);
        this.features = features;
    }

    JsonObject stats() {
        List<EntityInfo> sorted = sortedEntities();
        Map<String, List<EntityInfo>> buckets = new LinkedHashMap<>();
        buckets.put("1", new ArrayList<>());
        buckets.put("2", new ArrayList<>());
        buckets.put("3", new ArrayList<>());
        buckets.put("4+", new ArrayList<>());
        for (EntityInfo entity : sorted) {
            buckets.get(bucket(entity.records.size())).add(entity);
        }

        JsonObjectBuilder histogram = Json.createObjectBuilder();
        JsonObjectBuilder bucketEntities = Json.createObjectBuilder();
        for (Map.Entry<String, List<EntityInfo>> entry : buckets.entrySet()) {
            histogram.add(entry.getKey(), entry.getValue().size());
            bucketEntities.add(entry.getKey(), entitySummaries(entry.getValue(), LIST_CAP));
        }

        JsonArrayBuilder sample = Json.createArrayBuilder();
        sorted.stream()
                .filter(entity -> entity.records.size() > 1)
                .limit(10)
                .forEach(entity -> sample.add(entity.summaryWithSources()));

        int recordsTotal = sorted.stream().mapToInt(entity -> entity.records.size()).sum();
        long multiRecord = sorted.stream().filter(entity -> entity.records.size() > 1).count();
        long crossSource = sorted.stream().filter(entity -> entity.sources.size() > 1).count();
        int sourceCount = sorted.stream().flatMap(entity -> entity.sources.stream()).collect(Collectors.toSet()).size();

        return Json.createObjectBuilder()
                .add("records_total", recordsTotal)
                .add("entities_total", sorted.size())
                .add("multi_record_entities", multiRecord)
                .add("cross_source_entities", crossSource)
                .add("relationships_total", edges.size())
                .add("data_sources_total", sourceCount)
                .add("histogram", histogram)
                .add("bucket_entities", bucketEntities)
                .add("sample_entities", sample)
                .build();
    }

    JsonObject graph() {
        List<EntityInfo> sorted = sortedEntities();
        List<EntityInfo> shown = sorted.stream()
                .sorted(Comparator.comparingInt((EntityInfo entity) -> entity.sources.size()).reversed()
                        .thenComparing(Comparator.comparingInt((EntityInfo entity) -> entity.records.size()).reversed())
                        .thenComparingLong(entity -> entity.id))
                .limit(NODE_CAP)
                .toList();
        Set<Long> shownIds = shown.stream().map(entity -> entity.id).collect(Collectors.toSet());
        Set<Long> relatedIds = new TreeSet<>();
        for (EdgeInfo edge : edges) {
            relatedIds.add(edge.source);
            relatedIds.add(edge.target);
        }

        Set<String> sourceSetKeys = shown.stream().map(EntityInfo::sourceSetKey).collect(Collectors.toCollection(TreeSet::new));
        Map<String, SourceColorEncoder.Paint> colors = SourceColorEncoder.encode(sourceSetKeys);
        Map<String, Integer> sourceSetCounts = new TreeMap<>();
        Map<String, Integer> sourceCounts = new TreeMap<>();
        for (EntityInfo entity : shown) {
            sourceSetCounts.merge(entity.sourceSetKey(), 1, Integer::sum);
            entity.sources.forEach(source -> sourceCounts.merge(source, 1, Integer::sum));
        }
        List<String> combinationKeys = sourceSetKeys.stream()
                .filter(key -> key.contains("|"))
                .toList();
        SourceColorEncoder.Check check = SourceColorEncoder.checkEncoding(sourceSetKeys, colors);

        JsonArrayBuilder nodes = Json.createArrayBuilder();
        for (EntityInfo entity : shown) {
            JsonArrayBuilder records = Json.createArrayBuilder();
            entity.records.forEach(record -> records.add(record.toGraphJson()));
            SourceColorEncoder.Paint color = colors.get(entity.sourceSetKey());
            nodes.add(entity.toSummary()
                    .add("data_sources", stringArray(entity.sources))
                    .add("records", records)
                    .add("source_set_key", entity.sourceSetKey())
                    .add("color", color.toJson())
                    .build());
        }

        JsonArrayBuilder edgeArray = Json.createArrayBuilder();
        for (EdgeInfo edge : edges) {
            if (shownIds.contains(edge.source) && shownIds.contains(edge.target)) {
                edgeArray.add(edge.toJson());
            }
        }
        JsonArrayBuilder combinationArray = Json.createArrayBuilder();
        combinationKeys.forEach(combinationArray::add);
        JsonArrayBuilder sourceSetKeyArray = Json.createArrayBuilder();
        sourceSetKeys.forEach(sourceSetKeyArray::add);
        JsonArrayBuilder sourceCodes = Json.createArrayBuilder();
        sourceCounts.keySet().forEach(sourceCodes::add);
        JsonObjectBuilder colorMap = Json.createObjectBuilder();
        colors.forEach((key, color) -> colorMap.add(key, color.toJson()));
        JsonObjectBuilder sourceSetCountMap = Json.createObjectBuilder();
        sourceSetCounts.forEach(sourceSetCountMap::add);
        JsonObjectBuilder sourceCountMap = Json.createObjectBuilder();
        sourceCounts.forEach(sourceCountMap::add);

        JsonObjectBuilder encoding = Json.createObjectBuilder()
                .add("status", check.status)
                .add("distinct_source_set_keys", sourceSetKeys.size())
                .add("source_set_keys", sourceSetKeyArray)
                .add("combination_keys", combinationArray)
                .add("detail", check.status.equals("not_exercised")
                        ? "No cross-source entities are present among the emitted graph nodes."
                        : check.status.equals("ok")
                                ? "Each emitted node is encoded by its full sorted source set; actual and synthetic colors are unique."
                                : "A source-set color collision was detected.")
                .add("capacity", SourceColorEncoder.CAPACITY)
                .add("actual_unique", check.actualUnique)
                .add("actual_collisions", check.actualCollisions)
                .add("synthetic_unique", check.syntheticUnique)
                .add("synthetic_collisions", check.syntheticCollisions);
        if (check.warning != null) {
            encoding.add("warning", check.warning);
        }

        return Json.createObjectBuilder()
                .add("nodes", nodes)
                .add("edges", edgeArray)
                .add("total", sorted.size())
                .add("capped", sorted.size() > NODE_CAP)
                .add("related_total", relatedIds.size())
                .add("source_set_colors", colorMap)
                .add("source_set_counts", sourceSetCountMap)
                .add("data_source_codes", sourceCodes)
                .add("data_source_counts", sourceCountMap)
                .add("encoding_check", encoding)
                .build();
    }

    JsonObject merges() {
        JsonArrayBuilder array = Json.createArrayBuilder();
        sortedEntities().stream()
                .filter(entity -> entity.records.size() > 1)
                .forEach(entity -> array.add(entity.toMergeJson()));
        return Json.createObjectBuilder().add("entities", array).build();
    }

    JsonObject records(long entityId) {
        EntityInfo entity = entities.get(entityId);
        if (entity == null) {
            return Json.createObjectBuilder()
                    .add("entity_id", entityId)
                    .add("error", "Entity is not present in the model.")
                    .build();
        }
        return Json.createObjectBuilder()
                .add("entity_id", entity.id)
                .add("entity_name", entity.name)
                .add("records", entity.recordsJson())
                .build();
    }

    JsonObject overlap() {
        List<String> sources = entities.values().stream()
                .flatMap(entity -> entity.sources.stream())
                .distinct()
                .sorted()
                .toList();
        int[][] matrix = new int[sources.size()][sources.size()];
        Map<String, List<EntityInfo>> cellEntities = new TreeMap<>();
        for (int i = 0; i < sources.size(); i++) {
            for (int j = i; j < sources.size(); j++) {
                cellEntities.put(i + "," + j, new ArrayList<>());
            }
        }

        for (EntityInfo entity : sortedEntities()) {
            for (int i = 0; i < sources.size(); i++) {
                if (!entity.sources.contains(sources.get(i))) {
                    continue;
                }
                matrix[i][i]++;
                cellEntities.get(i + "," + i).add(entity);
                for (int j = i + 1; j < sources.size(); j++) {
                    if (entity.sources.contains(sources.get(j))) {
                        matrix[i][j]++;
                        matrix[j][i]++;
                        cellEntities.get(i + "," + j).add(entity);
                    }
                }
            }
        }

        JsonArrayBuilder sourceArray = Json.createArrayBuilder();
        sources.forEach(sourceArray::add);
        JsonArrayBuilder rows = Json.createArrayBuilder();
        for (int[] row : matrix) {
            JsonArrayBuilder values = Json.createArrayBuilder();
            for (int value : row) {
                values.add(value);
            }
            rows.add(values);
        }
        JsonObjectBuilder cellArray = Json.createObjectBuilder();
        boolean capped = false;
        for (Map.Entry<String, List<EntityInfo>> entry : cellEntities.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                if (entry.getValue().size() > LIST_CAP) {
                    capped = true;
                }
                cellArray.add(entry.getKey(), entitySummaries(entry.getValue(), LIST_CAP));
            }
        }
        return Json.createObjectBuilder()
                .add("sources", sourceArray)
                .add("matrix", rows)
                .add("cell_entities", cellArray)
                .add("cell_capped", capped)
                .build();
    }

    JsonObject matchKeys() {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Set<Long>> entityIds = new HashMap<>();
        for (EntityInfo entity : entities.values()) {
            if (entity.records.size() < 2) {
                continue;
            }
            for (RecordInfo record : entity.records) {
                if (record.matchKey.isBlank()) {
                    continue;
                }
                counts.merge(record.matchKey, 1, Integer::sum);
                entityIds.computeIfAbsent(record.matchKey, ignored -> new LinkedHashSet<>()).add(entity.id);
            }
        }

        List<String> sortedKeys = counts.keySet().stream()
                .sorted(Comparator.comparingInt((String key) -> counts.get(key)).reversed().thenComparing(Function.identity()))
                .toList();
        JsonArrayBuilder keyArray = Json.createArrayBuilder();
        JsonObjectBuilder keyEntities = Json.createObjectBuilder();
        boolean entitiesCapped = false;
        for (String key : sortedKeys.stream().limit(MATCH_KEY_CAP).toList()) {
            keyArray.add(Json.createObjectBuilder().add("match_key", key).add("count", counts.get(key)));
            List<EntityInfo> carrying = entityIds.get(key).stream()
                    .map(entities::get)
                    .filter(entity -> entity != null)
                    .sorted(entityComparator())
                    .toList();
            if (carrying.size() > LIST_CAP) {
                entitiesCapped = true;
            }
            keyEntities.add(key, entitySummaries(carrying, LIST_CAP));
        }
        return Json.createObjectBuilder()
                .add("match_keys", keyArray)
                .add("distinct", counts.size())
                .add("capped", counts.size() > MATCH_KEY_CAP)
                .add("match_key_entities", keyEntities)
                .add("entities_capped", entitiesCapped)
                .build();
    }

    JsonObject featureScores() {
        return features;
    }

    JsonObject why(long entityId) {
        EntityInfo entity = entities.get(entityId);
        if (entity == null) {
            return whyError(entityId, "Entity is not present in the model.");
        }
        try {
            String result;
            if (entity.records.size() > 1) {
                RecordInfo first = entity.records.get(0);
                RecordInfo second = entity.records.get(1);
                result = engine.whyRecords(
                        SzRecordKey.of(first.dataSource, first.recordId),
                        SzRecordKey.of(second.dataSource, second.recordId),
                        WHY_FLAGS);
                return Json.createObjectBuilder()
                        .add("entity_id", entityId)
                        .add("mode", "why_records")
                        .add("result", parseObject(result))
                        .build();
            }
            RecordInfo record = entity.records.get(0);
            result = engine.whyRecordInEntity(
                    SzRecordKey.of(record.dataSource, record.recordId),
                    WHY_SINGLE_FLAGS);
            return Json.createObjectBuilder()
                    .add("entity_id", entityId)
                    .add("mode", "why_record_in_entity")
                    .add("result", parseObject(result))
                    .build();
        } catch (Exception e) {
            return whyError(entityId, exceptionMessage(e));
        }
    }

    JsonObject how(long entityId) {
        try {
            String result = engine.howEntity(entityId, HOW_FLAGS);
            return Json.createObjectBuilder()
                    .add("entity_id", entityId)
                    .add("result", parseObject(result))
                    .build();
        } catch (Exception e) {
            return Json.createObjectBuilder()
                    .add("entity_id", entityId)
                    .add("error", exceptionMessage(e))
                    .build();
        }
    }

    JsonObject search(String query) {
        String normalized = query == null ? "" : query.trim();
        JsonArrayBuilder results = Json.createArrayBuilder();
        JsonArrayBuilder tried = Json.createArrayBuilder();
        JsonArrayBuilder failed = Json.createArrayBuilder();
        Set<Long> seen = new LinkedHashSet<>();
        boolean found = false;

        if (!normalized.isEmpty()) {
            for (String attribute : List.of("NAME_FULL", "NAME_ORG")) {
                tried.add(attribute);
                JsonObject criteria = Json.createObjectBuilder().add(attribute, normalized).build();
                try {
                    JsonObject response = parseObject(engine.searchByAttributes(
                            criteria.toString(), "SEARCH", SEARCH_FLAGS));
                    JsonArray candidates = array(response, "RESOLVED_ENTITIES");
                    for (JsonValue item : candidates) {
                        if (item.getValueType() != JsonValue.ValueType.OBJECT) {
                            continue;
                        }
                        JsonObject entityWrapper = object(item.asJsonObject(), "ENTITY");
                        JsonObject resolved = object(entityWrapper, "RESOLVED_ENTITY");
                        if (!resolved.containsKey("ENTITY_ID")) {
                            continue;
                        }
                        long entityId = number(resolved, "ENTITY_ID", -1);
                        if (entityId < 0 || !seen.add(entityId)) {
                            continue;
                        }
                        found = true;
                        JsonObject matchInfo = object(item.asJsonObject(), "MATCH_INFO");
                        results.add(seen.size() <= 10
                                ? enrichedSearchResult(entityId, resolved, matchInfo)
                                : basicSearchResult(entityId, resolved));
                    }
                } catch (Exception e) {
                    failed.add(Json.createObjectBuilder()
                            .add("attribute", attribute)
                            .add("error", exceptionMessage(e)));
                }
                if (found) {
                    break;
                }
            }
        }

        JsonObjectBuilder queryObject = Json.createObjectBuilder()
                .add("name", nullableString(normalized))
                .add("address", JsonValue.NULL)
                .add("phone", JsonValue.NULL)
                .add("email", JsonValue.NULL);
        JsonObjectBuilder response = Json.createObjectBuilder()
                .add("results", results)
                .add("query", queryObject)
                .add("attributes_tried", tried)
                .add("failed_attributes", failed);
        if (!normalized.isEmpty() && !found) {
            response.add("no_match_note", "No entity matched the attempted NAME_FULL then NAME_ORG search for this query; an empty result does not establish that a record is absent.");
        }
        return response.build();
    }

    JsonArray verifiedExamples(int limit) {
        if (limit == 5 && verifiedExamplesCache != null) {
            return verifiedExamplesCache;
        }
        JsonArrayBuilder examples = Json.createArrayBuilder();
        Set<String> attempted = new HashSet<>();
        int found = 0;
        for (String candidate : searchCandidates) {
            if (!attempted.add(candidate)) {
                continue;
            }
            JsonObject response = search(candidate);
            if (!array(response, "results").isEmpty()) {
                examples.add(Json.createObjectBuilder()
                        .add("query", candidate)
                        .add("result_count", array(response, "results").size()));
                if (++found >= limit) {
                    break;
                }
            }
        }
        JsonArray result = examples.build();
        if (limit == 5) {
            verifiedExamplesCache = result;
        }
        return result;
    }

    JsonObject examples() {
        return Json.createObjectBuilder().add("examples", verifiedExamples(5)).build();
    }

    JsonObject offlineData() {
        JsonObjectBuilder records = Json.createObjectBuilder();
        for (EntityInfo entity : sortedEntities()) {
            records.add(Long.toString(entity.id), records(entity.id));
        }
        return Json.createObjectBuilder()
                .add("stats", stats())
                .add("graph", graph())
                .add("merges", merges())
                .add("records", records)
                .add("overlap", overlap())
                .add("matchkeys", matchKeys())
                .add("features", features)
                .add("examples", verifiedExamples(5))
                .build();
    }

    int entityCount() {
        return entities.size();
    }

    boolean hasMultiRecordEntities() {
        return entities.values().stream().anyMatch(entity -> entity.records.size() > 1);
    }

    private JsonObject enrichedSearchResult(long entityId, JsonObject searchEntity, JsonObject matchInfo) {
        EntityInfo entity = entities.get(entityId);
        JsonObject details;
        String enrichmentError = null;
        try {
            details = entity == null
                    ? parseObject(engine.getEntity(entityId, ENTITY_FLAGS))
                    : entity.entityResponse;
            JsonObject resolved = object(details, "RESOLVED_ENTITY");
            if (entity == null) {
                entity = new EntityInfo(resolved);
            }
            return buildEnrichedResult(entity, searchEntity, resolved, matchInfo);
        } catch (Exception e) {
            enrichmentError = exceptionMessage(e);
            return basicSearchResult(entityId, searchEntity, enrichmentError);
        }
    }

    private JsonObject basicSearchResult(long entityId, JsonObject searchEntity) {
        return basicSearchResult(entityId, searchEntity, null);
    }

    private JsonObject basicSearchResult(long entityId, JsonObject searchEntity, String enrichmentError) {
        EntityInfo entity = entities.get(entityId);
        JsonArray summaries = array(searchEntity, "RECORD_SUMMARY");
        Set<String> sources = entity == null ? new TreeSet<>() : entity.sources;
        if (entity == null) {
            for (JsonValue value : summaries) {
                if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                    String source = string(value.asJsonObject(), "DATA_SOURCE", "");
                    if (!source.isBlank()) {
                        sources.add(source);
                    }
                }
            }
        }
        JsonObjectBuilder basic = Json.createObjectBuilder()
                .add("entity_id", entityId)
                .add("entity_name", string(searchEntity, "ENTITY_NAME", entity == null ? "" : entity.name))
                .add("record_count", entity == null ? summaries.size() : entity.records.size())
                .add("data_sources", stringArray(sources))
                .add("match_keys", JsonValue.NULL)
                .add("feature_scores", JsonValue.NULL)
                .add("resolution_rules", JsonValue.NULL)
                .add("enrichment_error", enrichmentError == null ? JsonValue.NULL : Json.createValue(enrichmentError));
        return basic.build();
    }

    private JsonObject buildEnrichedResult(EntityInfo entity,
                                           JsonObject searchEntity,
                                           JsonObject resolved,
                                           JsonObject matchInfo) {
        JsonArrayBuilder perRecord = Json.createArrayBuilder();
        JsonArrayBuilder rules = Json.createArrayBuilder();
        if (entity.records.size() > 1) {
            for (RecordInfo record : entity.records) {
                perRecord.add(record.matchKey);
                if (!record.rule.isBlank()) {
                    rules.add(Json.createObjectBuilder()
                            .add("data_source", record.dataSource)
                            .add("record_id", record.recordId)
                            .add("rule", record.rule));
                }
            }
        }
        String entityLevelKey = string(matchInfo, "MATCH_KEY", null);
        JsonObjectBuilder matchKeys = Json.createObjectBuilder()
                .add("entity_level", nullableString(entityLevelKey))
                .add("per_record", perRecord);
        JsonArray scoreArray = featureScoreItems(matchInfo);
        if (scoreArray.isEmpty() && entity.records.size() > 1) {
            scoreArray = scoresFromWhy(entity);
        }
        return Json.createObjectBuilder()
                .add("entity_id", entity.id)
                .add("entity_name", string(searchEntity, "ENTITY_NAME", entity.name))
                .add("record_count", entity.records.size())
                .add("data_sources", stringArray(entity.sources))
                .add("match_keys", matchKeys)
                .add("feature_scores", scoreArray)
                .add("resolution_rules", rules)
                .add("enrichment_error", JsonValue.NULL)
                .build();
    }

    private JsonArray scoresFromWhy(EntityInfo entity) {
        try {
            String response = engine.whyRecords(
                    SzRecordKey.of(entity.records.get(0).dataSource, entity.records.get(0).recordId),
                    SzRecordKey.of(entity.records.get(1).dataSource, entity.records.get(1).recordId),
                    WHY_FLAGS);
            return featureScoreItems(object(array(parseObject(response), "WHY_RESULTS").getJsonObject(0), "MATCH_INFO"));
        } catch (Exception ignored) {
            return Json.createArrayBuilder().build();
        }
    }

    private static JsonArray featureScoreItems(JsonObject matchInfo) {
        JsonObject featureScores = object(matchInfo, "FEATURE_SCORES");
        JsonArrayBuilder result = Json.createArrayBuilder();
        for (String family : List.of("NAME", "ADDRESS", "DOB", "PHONE", "EMAIL", "RECORD_TYPE")) {
            for (JsonValue scoreValue : array(featureScores, family)) {
                if (scoreValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    continue;
                }
                JsonObject score = scoreValue.asJsonObject();
                result.add(Json.createObjectBuilder()
                        .add("feature", family)
                        .add("score", (int) number(score, "SCORE", 0))
                        .add("label", string(score, "SCORE_BUCKET", "")));
            }
        }
        return result.build();
    }

    private JsonObject buildFeatures() {
        List<EntityInfo> multi = sortedEntities().stream()
                .filter(entity -> entity.records.size() > 1)
                .toList();
        Map<String, Map<String, Integer>> buckets = new TreeMap<>();
        int sampled = 0;
        for (EntityInfo entity : multi.stream().limit(FEATURE_SAMPLE_CAP).toList()) {
            try {
                String response = engine.whyRecords(
                        SzRecordKey.of(entity.records.get(0).dataSource, entity.records.get(0).recordId),
                        SzRecordKey.of(entity.records.get(1).dataSource, entity.records.get(1).recordId),
                        SZ_WHY_RECORDS_DEFAULT_FLAGS);
                JsonObject root = parseObject(response);
                JsonArray whyResults = array(root, "WHY_RESULTS");
                boolean hadScore = false;
                for (JsonValue resultValue : whyResults) {
                    if (resultValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        continue;
                    }
                    JsonObject matchInfo = object(resultValue.asJsonObject(), "MATCH_INFO");
                    JsonObject featureScores = object(matchInfo, "FEATURE_SCORES");
                    for (String family : List.of("NAME", "ADDRESS", "DOB", "PHONE", "RECORD_TYPE")) {
                        for (JsonValue scoreValue : array(featureScores, family)) {
                            if (scoreValue.getValueType() != JsonValue.ValueType.OBJECT) {
                                continue;
                            }
                            String bucket = string(scoreValue.asJsonObject(), "SCORE_BUCKET", "");
                            if (!bucket.isBlank()) {
                                buckets.computeIfAbsent(family, ignored -> new TreeMap<>())
                                        .merge(bucket, 1, Integer::sum);
                                hadScore = true;
                            }
                        }
                    }
                }
                if (hadScore) {
                    sampled++;
                }
            } catch (Exception ignored) {
                // A failed why call skips only this sample.
            }
        }

        JsonArrayBuilder featureArray = Json.createArrayBuilder();
        for (Map.Entry<String, Map<String, Integer>> entry : buckets.entrySet()) {
            JsonObjectBuilder values = Json.createObjectBuilder();
            entry.getValue().forEach(values::add);
            featureArray.add(Json.createObjectBuilder()
                    .add("feature", entry.getKey())
                    .add("buckets", values));
        }
        return Json.createObjectBuilder()
                .add("features", featureArray)
                .add("sampled", sampled)
                .add("multi_record_total", multi.size())
                .add("capped", multi.size() > FEATURE_SAMPLE_CAP)
                .build();
    }

    private void addEntityResponse(JsonObject response) {
        JsonObject resolved = object(response, "RESOLVED_ENTITY");
        if (resolved.isEmpty() || !resolved.containsKey("ENTITY_ID")) {
            throw new IllegalStateException("The SDK entity response did not contain RESOLVED_ENTITY.ENTITY_ID.");
        }
        EntityInfo entity = new EntityInfo(resolved);
        entities.put(entity.id, entity);
        if (!entity.name.isBlank()) {
            searchCandidates.add(entity.name);
        }
        for (JsonValue relationValue : array(response, "RELATED_ENTITIES")) {
            if (relationValue.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            JsonObject relation = relationValue.asJsonObject();
            long relatedId = number(relation, "ENTITY_ID", -1);
            if (relatedId < 0 || relatedId == entity.id) {
                continue;
            }
            long low = Math.min(entity.id, relatedId);
            long high = Math.max(entity.id, relatedId);
            String matchLevel = string(relation, "MATCH_LEVEL_CODE", "");
            boolean disclosed = number(relation, "IS_DISCLOSED", 0) == 1;
            boolean ambiguous = number(relation, "IS_AMBIGUOUS", 0) == 1;
            String type = disclosed ? "disclosed"
                    : ambiguous ? "ambiguous"
                    : "POSSIBLY_SAME".equals(matchLevel) ? "possibly_same"
                    : "possibly_related";
            edges.add(new EdgeInfo(low, high, string(relation, "MATCH_KEY", ""), type));
        }
    }

    private void finishRelationships() {
        Map<String, EdgeInfo> unique = new TreeMap<>();
        for (EdgeInfo edge : edges) {
            if (!entities.containsKey(edge.source) || !entities.containsKey(edge.target)) {
                continue;
            }
            String key = edge.source + ":" + edge.target;
            EdgeInfo existing = unique.get(key);
            if (existing == null || relationshipPriority(edge.type) < relationshipPriority(existing.type)) {
                unique.put(key, edge);
            }
        }
        edges.clear();
        edges.addAll(unique.values());
    }

    private void addSearchCandidates(JsonObject record) {
        JsonArray features = array(record, "FEATURES");
        for (JsonValue featureValue : features) {
            if (featureValue.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            JsonObject feature = featureValue.asJsonObject();
            for (String field : List.of("NAME_FULL", "NAME_ORG")) {
                String value = string(feature, field, "");
                if (!value.isBlank()) {
                    searchCandidates.add(value.trim());
                }
            }
            String first = string(feature, "NAME_FIRST", "");
            String last = string(feature, "NAME_LAST", "");
            if (!first.isBlank() && !last.isBlank()) {
                searchCandidates.add((first + " " + last).trim());
            }
        }
    }

    private static JsonObject emptyFeatures(int multiRecordTotal) {
        return Json.createObjectBuilder()
                .add("features", Json.createArrayBuilder())
                .add("sampled", 0)
                .add("multi_record_total", multiRecordTotal)
                .add("capped", false)
                .build();
    }

    private static JsonObject whyError(long entityId, String message) {
        return Json.createObjectBuilder().add("entity_id", entityId).add("error", message).build();
    }

    private static JsonArray entitySummaries(List<EntityInfo> values, int cap) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        values.stream().limit(cap).forEach(entity -> array.add(entity.toSummary().build()));
        return array.build();
    }

    private List<EntityInfo> sortedEntities() {
        return entities.values().stream().sorted(entityComparator()).toList();
    }

    private static Comparator<EntityInfo> entityComparator() {
        return Comparator.comparingInt((EntityInfo entity) -> entity.records.size()).reversed()
                .thenComparingLong(entity -> entity.id);
    }

    private static JsonArrayBuilder stringArray(Collection<String> values) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        values.forEach(builder::add);
        return builder;
    }

    private static String bucket(int recordCount) {
        return recordCount >= 4 ? "4+" : Integer.toString(recordCount);
    }

    private static int relationshipPriority(String type) {
        return switch (type) {
            case "disclosed" -> 0;
            case "ambiguous" -> 1;
            case "possibly_same" -> 2;
            default -> 3;
        };
    }

    static JsonObject parseObject(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    static JsonObject object(JsonObject object, String key) {
        JsonValue value = object.get(key);
        return value != null && value.getValueType() == JsonValue.ValueType.OBJECT
                ? value.asJsonObject()
                : Json.createObjectBuilder().build();
    }

    static JsonArray array(JsonObject object, String key) {
        JsonValue value = object.get(key);
        return value != null && value.getValueType() == JsonValue.ValueType.ARRAY
                ? value.asJsonArray()
                : Json.createArrayBuilder().build();
    }

    static String string(JsonObject object, String key, String fallback) {
        JsonValue value = object.get(key);
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return fallback;
        }
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            return ((JsonString) value).getString();
        }
        return value.toString();
    }

    static long number(JsonObject object, String key, long fallback) {
        JsonValue value = object.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return value.getValueType() == JsonValue.ValueType.NUMBER
                    ? object.getJsonNumber(key).longValue()
                    : Long.parseLong(value.toString());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static JsonValue nullableString(String value) {
        return value == null ? JsonValue.NULL : Json.createValue(value);
    }

    @SafeVarargs
    private static Set<SzFlag> mergeFlags(Set<SzFlag>... groups) {
        Set<SzFlag> merged = new HashSet<>();
        for (Set<SzFlag> group : groups) {
            merged.addAll(group);
        }
        return Set.copyOf(merged);
    }

    private static String exceptionMessage(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static final class EntityInfo {
        final long id;
        final String name;
        final List<RecordInfo> records = new ArrayList<>();
        final Set<String> sources = new TreeSet<>();
        final JsonObject entityResponse;

        EntityInfo(JsonObject resolvedEntity) {
            this.id = number(resolvedEntity, "ENTITY_ID", -1);
            this.name = string(resolvedEntity, "ENTITY_NAME", "");
            this.entityResponse = Json.createObjectBuilder().add("RESOLVED_ENTITY", resolvedEntity).build();
            for (JsonValue value : array(resolvedEntity, "RECORDS")) {
                if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                    continue;
                }
                JsonObject record = value.asJsonObject();
                String source = string(record, "DATA_SOURCE", "");
                String recordId = string(record, "RECORD_ID", "");
                if (source.isBlank() || recordId.isBlank()) {
                    continue;
                }
                records.add(new RecordInfo(
                        source,
                        recordId,
                        string(record, "MATCH_KEY", ""),
                        string(record, "ERRULE_CODE", "")));
                sources.add(source);
            }
            records.sort(Comparator.comparing((RecordInfo record) -> record.dataSource)
                    .thenComparing(record -> record.recordId));
        }

        String sourceSetKey() {
            return String.join("|", sources);
        }

        JsonObjectBuilder toSummary() {
            return Json.createObjectBuilder()
                    .add("entity_id", id)
                    .add("entity_name", name)
                    .add("record_count", records.size());
        }

        JsonObject summaryWithSources() {
            return toSummary().add("data_sources", stringArray(sources)).build();
        }

        JsonObject toMergeJson() {
            JsonArrayBuilder matchKeys = Json.createArrayBuilder();
            records.stream()
                    .map(record -> record.matchKey)
                    .filter(key -> !key.isBlank())
                    .distinct()
                    .forEach(matchKeys::add);
            return toSummary()
                    .add("data_sources", stringArray(sources))
                    .add("match_keys", matchKeys)
                    .add("records", recordsJson())
                    .build();
        }

        JsonArray recordsJson() {
            JsonArrayBuilder builder = Json.createArrayBuilder();
            records.forEach(record -> builder.add(record.toJson()));
            return builder.build();
        }
    }

    private static final class RecordInfo {
        final String dataSource;
        final String recordId;
        final String matchKey;
        final String rule;

        RecordInfo(String dataSource, String recordId, String matchKey, String rule) {
            this.dataSource = dataSource;
            this.recordId = recordId;
            this.matchKey = matchKey;
            this.rule = rule;
        }

        JsonObject toJson() {
            return Json.createObjectBuilder()
                    .add("data_source", dataSource)
                    .add("record_id", recordId)
                    .add("match_key", matchKey)
                    .build();
        }

        JsonObject toGraphJson() {
            return Json.createObjectBuilder()
                    .add("data_source", dataSource)
                    .add("record_id", recordId)
                    .build();
        }
    }

    private static final class EdgeInfo {
        final long source;
        final long target;
        final String matchKey;
        final String type;

        EdgeInfo(long source, long target, String matchKey, String type) {
            this.source = source;
            this.target = target;
            this.matchKey = matchKey;
            this.type = type;
        }

        JsonObject toJson() {
            String label = switch (type) {
                case "possibly_same" -> "possibly the same entity";
                case "possibly_related" -> "possibly related";
                case "disclosed" -> "disclosed relationship";
                case "ambiguous" -> "ambiguous";
                default -> "possibly related";
            };
            return Json.createObjectBuilder()
                    .add("source_entity_id", source)
                    .add("target_entity_id", target)
                    .add("match_key", matchKey)
                    .add("relationship_type", type)
                    .add("relationship_label", label)
                    .build();
        }
    }
}
