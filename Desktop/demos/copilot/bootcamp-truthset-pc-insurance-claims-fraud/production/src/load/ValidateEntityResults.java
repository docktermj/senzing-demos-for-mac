import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.senzing.sdk.SzFlag.SZ_EXPORT_DEFAULT_FLAGS;

public final class ValidateEntityResults {
    private static final Map<String, Path> SOURCES = Map.of(
            "POLICYHOLDERS", Path.of("data/senzing-ready/POLICYHOLDERS.jsonl"),
            "VENDOR_REGISTRY", Path.of("data/senzing-ready/VENDOR_REGISTRY.jsonl"),
            "SIU_WATCHLIST", Path.of("data/senzing-ready/SIU_WATCHLIST.jsonl"));
    private static final Path OUTPUT_PATH = Path.of("logs", "entity_validation.json");
    private static final int MAX_CROSS_SOURCE_SAMPLES = 25;

    private ValidateEntityResults() {
    }

    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalArgumentException("SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName(ValidateEntityResults.class.getSimpleName())
                .verboseLogging(false)
                .build();
        try {
            SzEngine engine = environment.getEngine();
            Map<Long, EntitySummary> entities = querySources(engine);
            Map<String, Object> exportAudit = analyzeExport(engine, entities);
            writeReport(entities, exportAudit);
            printSummary(entities, exportAudit);
        } finally {
            environment.destroy();
        }
    }

    private static Map<Long, EntitySummary> querySources(SzEngine engine) throws Exception {
        Map<Long, EntitySummary> entities = new TreeMap<>();
        for (Map.Entry<String, Path> source : SOURCES.entrySet()) {
            try (BufferedReader reader = Files.newBufferedReader(source.getValue(), StandardCharsets.UTF_8)) {
                String line;
                long lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    Map<String, Object> inputRecord = JsonSupport.object(
                            JsonSupport.parse(line), source.getKey() + " input record");
                    String recordSource = requireString(inputRecord.get("DATA_SOURCE"), "DATA_SOURCE", lineNumber);
                    String recordId = requireString(inputRecord.get("RECORD_ID"), "RECORD_ID", lineNumber);
                    if (!source.getKey().equals(recordSource)) {
                        throw new IOException("Unexpected DATA_SOURCE in " + source.getValue() + ":" + lineNumber);
                    }

                    String response = engine.getEntity(SzRecordKey.of(recordSource, recordId));
                    Map<String, Object> responseObject = JsonSupport.object(
                            JsonSupport.parse(response), "entity response for " + recordSource + ":" + recordId);
                    Map<String, Object> resolvedEntity = JsonSupport.object(
                            responseObject.get("RESOLVED_ENTITY"), "resolved entity");
                    long entityId = requireLong(resolvedEntity.get("ENTITY_ID"), "ENTITY_ID");
                    List<Object> records = JsonSupport.array(
                            resolvedEntity.get("RECORDS"), "resolved entity records");

                    EntitySummary summary = entities.computeIfAbsent(entityId, EntitySummary::new);
                    summary.setEntityName(optionalString(resolvedEntity.get("ENTITY_NAME")));
                    for (Object value : records) {
                        Map<String, Object> entityRecord = JsonSupport.object(value, "entity record");
                        String dataSource = requireString(entityRecord.get("DATA_SOURCE"), "DATA_SOURCE", lineNumber);
                        String entityRecordId = requireString(entityRecord.get("RECORD_ID"), "RECORD_ID", lineNumber);
                        String matchKey = optionalString(entityRecord.get("MATCH_KEY"));
                        String matchLevel = optionalString(entityRecord.get("MATCH_LEVEL_CODE"));
                        String ruleCode = optionalString(entityRecord.get("ERRULE_CODE"));
                        summary.add(new EntityRecord(dataSource, entityRecordId, matchKey, matchLevel, ruleCode));
                    }
                }
            }
        }
        return entities;
    }

    private static Map<String, Object> analyzeExport(
            SzEngine engine, Map<Long, EntitySummary> queriedEntities) throws Exception {
        Map<Long, ExportEntity> exportedEntities = new TreeMap<>();
        Map<String, SuppressorCounts> perRecordCounts = new LinkedHashMap<>();
        perRecordCounts.put("single_source", new SuppressorCounts());
        perRecordCounts.put("cross_source", new SuppressorCounts());
        Map<String, Long> matchPrinciples = new TreeMap<>();
        Map<String, RelationshipSummary> relationships = new TreeMap<>();
        Map<String, Long> sourcePairEntityCounts = new TreeMap<>();
        Map<String, Object> audit = new LinkedHashMap<>();

        long exportHandle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
        long exportedRecordCount = 0;
        String topLevelKeys = null;
        boolean exportHasRelatedEntities = false;
        boolean relationshipReaderAvailable = false;
        try {
            String row;
            while ((row = engine.fetchNext(exportHandle)) != null) {
                Map<String, Object> root = JsonSupport.object(JsonSupport.parse(row), "entity export");
                if (topLevelKeys == null) {
                    topLevelKeys = String.join(",", new TreeSet<>(root.keySet()));
                    exportHasRelatedEntities = root.containsKey("RELATED_ENTITIES");
                    relationshipReaderAvailable = exportHasRelatedEntities;
                }

                Map<String, Object> resolvedEntity = JsonSupport.object(
                        root.get("RESOLVED_ENTITY"), "resolved entity in export");
                long entityId = requireLong(resolvedEntity.get("ENTITY_ID"), "ENTITY_ID");
                String entityName = optionalString(resolvedEntity.get("ENTITY_NAME"));
                List<Object> rawRecords = JsonSupport.array(
                        resolvedEntity.get("RECORDS"), "resolved entity records in export");
                List<Map<String, Object>> records = new ArrayList<>();
                Set<String> sources = new TreeSet<>();
                for (Object value : rawRecords) {
                    Map<String, Object> record = JsonSupport.object(value, "exported entity record");
                    records.add(record);
                    sources.add(requireString(record.get("DATA_SOURCE"), "DATA_SOURCE", entityId));
                }
                exportedRecordCount += records.size();
                exportedEntities.put(entityId, new ExportEntity(entityId, entityName, records, sources));

                String perRecordBucket = sources.size() > 1 ? "cross_source" : "single_source";
                for (Map<String, Object> record : records) {
                    String matchKey = optionalString(record.get("MATCH_KEY"));
                    perRecordCounts.get(perRecordBucket).addKey(matchKey);
                    String rule = optionalString(record.get("ERRULE_CODE"));
                    if (!matchKey.isBlank() || !rule.isBlank()) {
                        matchPrinciples.merge(rule + " | " + matchKey, 1L, Long::sum);
                    }
                }

                if (sources.size() > 1) {
                    List<String> sortedSources = new ArrayList<>(sources);
                    for (int i = 0; i < sortedSources.size(); i++) {
                        for (int j = i + 1; j < sortedSources.size(); j++) {
                            sourcePairEntityCounts.merge(
                                    sortedSources.get(i) + " / " + sortedSources.get(j), 1L, Long::sum);
                        }
                    }
                }

                if (root.containsKey("RELATED_ENTITIES")) {
                    List<Object> related = JsonSupport.array(
                            root.get("RELATED_ENTITIES"), "related entities in export");
                    addRelationships(relationships, entityId, related);
                }
            }
        } finally {
            engine.closeExportReport(exportHandle);
        }

        if (!exportHasRelatedEntities) {
            for (ExportEntity entity : exportedEntities.values()) {
                String response = engine.getEntity(entity.entityId());
                Map<String, Object> root = JsonSupport.object(
                        JsonSupport.parse(response), "entity detail for " + entity.entityId());
                if (root.containsKey("RELATED_ENTITIES")) {
                    relationshipReaderAvailable = true;
                    List<Object> related = JsonSupport.array(
                            root.get("RELATED_ENTITIES"), "related entities in entity detail");
                    addRelationships(relationships, entity.entityId(), related);
                }
            }
        }

        SuppressorCounts relationshipCounts = new SuppressorCounts();
        Map<String, Long> relationshipsByMatchLevel = new TreeMap<>();
        List<Map<String, Object>> possibleMatchExamples = new ArrayList<>();
        long keyedRelationshipCount = 0;
        for (RelationshipSummary relationship : relationships.values()) {
            Set<String> relationshipSuppressors = new TreeSet<>();
            for (String matchKey : relationship.matchKeys()) {
                relationshipSuppressors.addAll(suppressedFeatures(matchKey));
            }
            relationshipCounts.addComparison(relationshipSuppressors);
            if (!relationship.matchKeys().isEmpty()) {
                keyedRelationshipCount++;
            }
            for (String level : relationship.matchLevels()) {
                relationshipsByMatchLevel.merge(level, 1L, Long::sum);
            }
            if (possibleMatchExamples.size() < 10
                    && (relationship.matchLevels().contains("POSSIBLY_SAME")
                    || relationship.matchLevels().contains("POSSIBLY_RELATED"))) {
                Map<String, Object> example = new LinkedHashMap<>();
                example.put("entity_id_1", relationship.entityId1());
                example.put("entity_id_2", relationship.entityId2());
                example.put("match_levels", new ArrayList<>(relationship.matchLevels()));
                example.put("match_keys", new ArrayList<>(relationship.matchKeys()));
                example.put("records_1", recordSummaries(exportedEntities.get(relationship.entityId1())));
                example.put("records_2", recordSummaries(exportedEntities.get(relationship.entityId2())));
                try {
                    example.put("why", whySummary(engine, relationship.entityId1(), relationship.entityId2()));
                } catch (Exception exception) {
                    example.put("why_error", exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage());
                }
                possibleMatchExamples.add(example);
            }
        }

        Map<String, Object> howAudit = auditHowState(engine, exportedEntities);
        audit.put("export_top_level_keys", topLevelKeys == null ? "" : topLevelKeys);
        audit.put("export_carries_related_entities", exportHasRelatedEntities);
        audit.put("export_record_count", exportedRecordCount);
        audit.put("export_entity_count", exportedEntities.size());
        audit.put("record_count_matches_record_id_queries",
                exportedRecordCount == queriedEntities.values().stream()
                        .mapToLong(entity -> entity.records().size()).sum());
        audit.put("entities_by_source", entitiesBySource(exportedEntities));
        audit.put("cross_source_entity_count", exportedEntities.values().stream()
                .filter(entity -> entity.sources().size() > 1).count());
        audit.put("entity_size_distribution", exportSizeDistribution(exportedEntities));
        audit.put("source_pair_cross_source_entity_counts", sourcePairEntityCounts);
        audit.put("match_principle_frequency", rankedCounts(matchPrinciples));
        audit.put("per_record_match_key_audit", suppressorAudit(perRecordCounts));

        Map<String, Object> relationAudit = suppressorAudit(
                Map.of("relationships", relationshipCounts));
        relationAudit.put("status", !relationshipReaderAvailable
                ? "could_not_measure"
                : relationships.isEmpty() ? "no_relationships"
                : relationshipCounts.keysWithSuppressors() == 0 ? "no_finding" : "finding");
        relationAudit.put("unique_relationship_count", relationships.size());
        relationAudit.put("relationships_with_match_keys", keyedRelationshipCount);
        relationAudit.put("relationships_by_match_level", relationshipsByMatchLevel);
        relationAudit.put("possible_match_examples", possibleMatchExamples);
        audit.put("relationship_match_key_audit", relationAudit);
        audit.put("how_state_audit", howAudit);
        return audit;
    }

    private static void addRelationships(
            Map<String, RelationshipSummary> relationships, long entityId, List<Object> relatedEntities) {
        for (Object value : relatedEntities) {
            Map<String, Object> related = JsonSupport.object(value, "related entity");
            long relatedId = requireLong(related.get("ENTITY_ID"), "related ENTITY_ID");
            if (relatedId == entityId) {
                continue;
            }
            long firstId = Math.min(entityId, relatedId);
            long secondId = Math.max(entityId, relatedId);
            String pairKey = firstId + ":" + secondId;
            RelationshipSummary summary = relationships.computeIfAbsent(
                    pairKey, ignored -> new RelationshipSummary(firstId, secondId));
            summary.add(optionalString(related.get("MATCH_LEVEL_CODE")),
                    optionalString(related.get("MATCH_KEY")));
        }
    }

    private static Map<String, Object> auditHowState(
            SzEngine engine, Map<Long, ExportEntity> entities) {
        long multiRecordEntities = entities.values().stream()
                .filter(entity -> entity.records().size() >= 2).count();
        long checked = 0;
        List<Map<String, Object>> unsettled = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (ExportEntity entity : entities.values()) {
            if (entity.records().size() < 2) {
                continue;
            }
            try {
                String response = engine.howEntity(entity.entityId());
                Map<String, Object> root = JsonSupport.object(JsonSupport.parse(response), "how entity response");
                Map<String, Object> howResults = JsonSupport.object(root.get("HOW_RESULTS"), "how results");
                Object finalStateValue = howResults.get("FINAL_STATE");
                if (!(finalStateValue instanceof Map<?, ?>)) {
                    errors.add(howError(entity.entityId(), "FINAL_STATE is absent"));
                    continue;
                }
                Map<String, Object> finalState = JsonSupport.object(finalStateValue, "how final state");
                Object reevaluationValue = finalState.get("NEED_REEVALUATION");
                Object virtualEntitiesValue = finalState.get("VIRTUAL_ENTITIES");
                if (!(reevaluationValue instanceof BigDecimal) || !(virtualEntitiesValue instanceof List<?>)) {
                    errors.add(howError(entity.entityId(), "FINAL_STATE is missing a required audit field"));
                    continue;
                }
                checked++;
                long needsReevaluation = requireLong(reevaluationValue, "NEED_REEVALUATION");
                int virtualEntityCount = JsonSupport.array(
                        virtualEntitiesValue, "final state virtual entities").size();
                if (needsReevaluation != 0 || virtualEntityCount > 1) {
                    Map<String, Object> finding = new LinkedHashMap<>();
                    finding.put("entity_id", entity.entityId());
                    finding.put("entity_name", entity.entityName());
                    finding.put("record_count", entity.records().size());
                    List<String> signs = new ArrayList<>();
                    if (needsReevaluation != 0) {
                        signs.add("NEED_REEVALUATION");
                    }
                    if (virtualEntityCount > 1) {
                        signs.add("multiple_virtual_entities");
                    }
                    finding.put("signs", signs);
                    unsettled.add(finding);
                }
            } catch (Exception exception) {
                errors.add(howError(entity.entityId(), exception.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outcome", multiRecordEntities == 0
                ? entities.isEmpty() ? "could_not_measure" : "nothing_to_check"
                : !errors.isEmpty() ? "could_not_measure"
                : unsettled.isEmpty() ? "no_finding" : "finding");
        result.put("checked", checked);
        result.put("multi_record_entity_count", multiRecordEntities);
        result.put("unsettled_count", unsettled.size());
        result.put("unsettled_entities", unsettled);
        result.put("not_checked_count", multiRecordEntities - checked);
        result.put("errors", errors);
        return result;
    }

    private static Map<String, Object> howError(long entityId, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("entity_id", entityId);
        error.put("error", message == null ? "unknown error" : message);
        return error;
    }

    private static List<Map<String, Object>> recordSummaries(ExportEntity entity) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (entity == null) {
            return result;
        }
        for (Map<String, Object> record : entity.records()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("data_source", record.get("DATA_SOURCE"));
            summary.put("record_id", record.get("RECORD_ID"));
            summary.put("match_key", optionalString(record.get("MATCH_KEY")));
            summary.put("rule_code", optionalString(record.get("ERRULE_CODE")));
            result.add(summary);
        }
        return result;
    }

    private static Map<String, Object> whySummary(SzEngine engine, long entityId1, long entityId2)
            throws Exception {
        String response = engine.whyEntities(entityId1, entityId2);
        Map<String, Object> root = JsonSupport.object(JsonSupport.parse(response), "why entities response");
        List<Object> results = JsonSupport.array(root.get("WHY_RESULTS"), "why results");
        Map<String, Object> summary = new LinkedHashMap<>();
        if (results.isEmpty()) {
            summary.put("status", "empty_why_results");
            return summary;
        }
        Map<String, Object> firstResult = JsonSupport.object(results.get(0), "why result");
        Map<String, Object> matchInfo = JsonSupport.object(firstResult.get("MATCH_INFO"), "why match info");
        summary.put("status", "queried");
        summary.put("match_level_code", optionalString(matchInfo.get("MATCH_LEVEL_CODE")));
        summary.put("why_key", optionalString(matchInfo.get("WHY_KEY")));
        summary.put("why_errule_code", optionalString(matchInfo.get("WHY_ERRULE_CODE")));
        return summary;
    }

    private static Map<String, Long> entitiesBySource(Map<Long, ExportEntity> entities) {
        Map<String, Long> counts = new TreeMap<>();
        for (ExportEntity entity : entities.values()) {
            for (String source : entity.sources()) {
                counts.merge(source, 1L, Long::sum);
            }
        }
        return counts;
    }

    private static Map<String, Object> suppressorAudit(Map<String, SuppressorCounts> counts) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, SuppressorCounts> entry : counts.entrySet()) {
            SuppressorCounts bucket = entry.getValue();
            Map<String, Object> bucketResult = new LinkedHashMap<>();
            bucketResult.put("comparison_count", bucket.comparisons());
            bucketResult.put("comparisons_with_suppressors", bucket.keysWithSuppressors());
            bucketResult.put("suppressor_feature_counts", rankedCounts(bucket.featureCounts()));
            bucketResult.put("suppressor_share_percent", bucket.comparisons() == 0 ? null
                    : 100.0 * bucket.keysWithSuppressors() / bucket.comparisons());
            bucketResult.put("status", bucket.comparisons() == 0 ? "could_not_measure"
                    : bucket.keysWithSuppressors() == 0 ? "no_finding" : "finding");
            result.put(entry.getKey(), bucketResult);
        }
        return result;
    }

    private static Map<String, Long> rankedCounts(Map<String, Long> counts) {
        Map<String, Long> ranked = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .forEach(entry -> ranked.put(entry.getKey(), entry.getValue()));
        return ranked;
    }

    private static Map<String, Long> exportSizeDistribution(Map<Long, ExportEntity> entities) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String bucket : List.of("1", "2", "3-5", "6-10", "11-50", "51+")) {
            counts.put(bucket, 0L);
        }
        for (ExportEntity entity : entities.values()) {
            int size = entity.records().size();
            String bucket = size == 1 ? "1"
                    : size == 2 ? "2"
                    : size <= 5 ? "3-5"
                    : size <= 10 ? "6-10"
                    : size <= 50 ? "11-50" : "51+";
            counts.put(bucket, counts.get(bucket) + 1);
        }
        return counts;
    }

    private static Set<String> suppressedFeatures(String matchKey) {
        StringBuilder withoutRoles = new StringBuilder();
        int roleDepth = 0;
        for (int i = 0; i < matchKey.length(); i++) {
            char character = matchKey.charAt(i);
            if (character == '(') {
                roleDepth++;
            } else if (character == ')' && roleDepth > 0) {
                roleDepth--;
            } else if (roleDepth == 0) {
                withoutRoles.append(character);
            }
        }

        Set<String> result = new TreeSet<>();
        StringBuilder feature = new StringBuilder();
        char sign = 0;
        boolean escaped = false;
        for (int i = 0; i < withoutRoles.length(); i++) {
            char character = withoutRoles.charAt(i);
            if (escaped) {
                feature.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '+' || character == '-') {
                if (sign == '-' && !feature.isEmpty()) {
                    result.add(feature.toString());
                }
                feature.setLength(0);
                sign = character;
            } else {
                feature.append(character);
            }
        }
        if (sign == '-' && !feature.isEmpty()) {
            result.add(feature.toString());
        }
        return result;
    }

    private static void writeReport(Map<Long, EntitySummary> entities, Map<String, Object> exportAudit)
            throws IOException {
        Files.createDirectories(OUTPUT_PATH.getParent());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("record_count", entities.values().stream().mapToLong(entity -> entity.records().size()).sum());
        root.put("entity_count", entities.size());
        root.put("cross_source_entity_count", entities.values().stream().filter(EntitySummary::isCrossSource).count());
        root.put("size_distribution", sizeDistribution(entities));
        root.put("records_by_source", recordsBySource(entities));
        root.put("export_audit", exportAudit);

        List<Map<String, Object>> crossSourceSamples = new ArrayList<>();
        for (EntitySummary entity : entities.values()) {
            if (entity.isCrossSource() && crossSourceSamples.size() < MAX_CROSS_SOURCE_SAMPLES) {
                crossSourceSamples.add(entity.toMap());
            }
        }
        root.put("cross_source_samples", crossSourceSamples);
        List<Map<String, Object>> singleSourceSamples = new ArrayList<>();
        for (EntitySummary entity : entities.values()) {
            if (!entity.isCrossSource() && entity.records().size() > 1
                    && singleSourceSamples.size() < 10) {
                singleSourceSamples.add(entity.toMap());
            }
        }
        root.put("single_source_multi_record_samples", singleSourceSamples);
        Files.writeString(
                OUTPUT_PATH,
                JsonSupport.stringify(root) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    private static Map<String, Long> sizeDistribution(Map<Long, EntitySummary> entities) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String bucket : List.of("1", "2", "3-5", "6-10", "11-50", "51+")) {
            counts.put(bucket, 0L);
        }
        for (EntitySummary entity : entities.values()) {
            int size = entity.records().size();
            String bucket = size == 1 ? "1"
                    : size == 2 ? "2"
                    : size <= 5 ? "3-5"
                    : size <= 10 ? "6-10"
                    : size <= 50 ? "11-50" : "51+";
            counts.put(bucket, counts.get(bucket) + 1);
        }
        return counts;
    }

    private static Map<String, Long> recordsBySource(Map<Long, EntitySummary> entities) {
        Map<String, Long> counts = new TreeMap<>();
        for (EntitySummary entity : entities.values()) {
            for (EntityRecord record : entity.records().values()) {
                counts.merge(record.dataSource(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private static void printSummary(Map<Long, EntitySummary> entities, Map<String, Object> exportAudit) {
        long recordCount = entities.values().stream().mapToLong(entity -> entity.records().size()).sum();
        long crossSourceEntities = entities.values().stream().filter(EntitySummary::isCrossSource).count();
        System.out.printf(
                "Entity validation complete: records=%d entities=%d cross_source_entities=%d%n",
                recordCount, entities.size(), crossSourceEntities);
        System.out.println("Records by source: " + recordsBySource(entities));
        System.out.println("Entity sizes: " + sizeDistribution(entities));
        System.out.println("Export top-level keys: " + exportAudit.get("export_top_level_keys"));
        System.out.println("How-state audit: " + exportAudit.get("how_state_audit"));
        System.out.println("Evidence: " + OUTPUT_PATH);
    }

    private static String requireString(Object value, String field, long lineNumber) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing string " + field + " at line " + lineNumber + ".");
        }
        return text;
    }

    private static long requireLong(Object value, String field) {
        if (!(value instanceof BigDecimal number)) {
            throw new IllegalArgumentException("Missing numeric " + field + " in entity response.");
        }
        try {
            return number.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Non-integral " + field + " in entity response.", exception);
        }
    }

    private static String optionalString(Object value) {
        return value instanceof String text ? text : "";
    }

    private record EntityRecord(
            String dataSource, String recordId, String matchKey, String matchLevel, String ruleCode) {
        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data_source", dataSource);
            result.put("record_id", recordId);
            result.put("match_key", matchKey);
            result.put("match_level_code", matchLevel);
            result.put("rule_code", ruleCode);
            return result;
        }
    }

    private record ExportEntity(
            long entityId, String entityName, List<Map<String, Object>> records, Set<String> sources) {
    }

    private static final class EntitySummary {
        private final long entityId;
        private final Map<String, EntityRecord> records = new TreeMap<>();
        private String entityName = "";

        private EntitySummary(long entityId) {
            this.entityId = entityId;
        }

        private void add(EntityRecord record) {
            records.put(record.dataSource() + "\u0000" + record.recordId(), record);
        }

        private void setEntityName(String value) {
            if (entityName.isBlank() && !value.isBlank()) {
                entityName = value;
            }
        }

        private Map<String, EntityRecord> records() {
            return records;
        }

        private boolean isCrossSource() {
            return records.values().stream().map(EntityRecord::dataSource).distinct().count() > 1;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entity_id", entityId);
            result.put("entity_name", entityName);
            result.put("sources", new ArrayList<>(
                    new TreeSet<>(records.values().stream().map(EntityRecord::dataSource).toList())));
            List<Map<String, Object>> recordList = new ArrayList<>();
            for (EntityRecord record : records.values()) {
                recordList.add(record.toMap());
            }
            result.put("records", recordList);
            return result;
        }
    }

    private static final class SuppressorCounts {
        private long comparisons;
        private long keysWithSuppressors;
        private final Map<String, Long> featureCounts = new TreeMap<>();

        private void addKey(String key) {
            if (key == null || key.isBlank()) {
                return;
            }
            addComparison(suppressedFeatures(key));
        }

        private void addComparison(Set<String> suppressors) {
            comparisons++;
            if (!suppressors.isEmpty()) {
                keysWithSuppressors++;
                for (String feature : suppressors) {
                    featureCounts.merge(feature, 1L, Long::sum);
                }
            }
        }

        private long comparisons() {
            return comparisons;
        }

        private long keysWithSuppressors() {
            return keysWithSuppressors;
        }

        private Map<String, Long> featureCounts() {
            return featureCounts;
        }
    }

    private static final class RelationshipSummary {
        private final long entityId1;
        private final long entityId2;
        private final Set<String> matchLevels = new TreeSet<>();
        private final Set<String> matchKeys = new TreeSet<>();

        private RelationshipSummary(long entityId1, long entityId2) {
            this.entityId1 = entityId1;
            this.entityId2 = entityId2;
        }

        private void add(String matchLevel, String matchKey) {
            if (!matchLevel.isBlank()) {
                matchLevels.add(matchLevel);
            }
            if (!matchKey.isBlank()) {
                matchKeys.add(matchKey);
            }
        }

        private long entityId1() {
            return entityId1;
        }

        private long entityId2() {
            return entityId2;
        }

        private Set<String> matchLevels() {
            return matchLevels;
        }

        private Set<String> matchKeys() {
            return matchKeys;
        }
    }
}
