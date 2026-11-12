package loading;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_EXPORT_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_HOW_ENTITY_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_WHY_ENTITIES_DEFAULT_FLAGS;

public final class EntityResolutionAudit {
    private static final List<String> SOURCES = List.of(
        "AML_WATCHLIST", "BUSINESS_REGISTRY", "BANK_CUSTOMERS");
    private static final int MAX_CROSS_SOURCE_SAMPLES = 25;
    private static final int MAX_POSSIBLE_MATCH_EXPLANATIONS = 5;

    private EntityResolutionAudit() {}

    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isEmpty()) {
            throw new IllegalArgumentException(
                "SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        Path root = Paths.get("").toAbsolutePath().normalize();
        Path outputDirectory = Files.createTempDirectory(
            root.resolve("logs"), "module6-validation-");
        SzEnvironment environment = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(EntityResolutionAudit.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = environment.getEngine();
            ExportSummary summary = exportEntities(
                engine, outputDirectory.resolve("entities.jsonl"));
            if (!summary.relatedEntitiesKeyPresent) {
                System.out.println(
                    "Export omitted RELATED_ENTITIES; using per-entity SDK lookup.");
                writeRelationshipFallback(
                    engine, summary,
                    outputDirectory.resolve("relationship_fallback.jsonl"));
            }
            summary.relationshipCount = summary.uniqueRelationships().size();
            auditHowState(
                engine, summary.entities, outputDirectory.resolve("how_state.jsonl"));
            writeCrossSourceDetails(
                engine, summary, outputDirectory.resolve("cross_source_details.jsonl"));
            writeKnownRecordSamples(
                root, engine, summary,
                outputDirectory.resolve("known_records.jsonl"));
            writePossibleMatchExplanations(
                engine, summary, outputDirectory.resolve("why_possible_matches.jsonl"));

            System.out.println("Exported entities: " + summary.entities.size());
            System.out.println("Exported records: " + summary.recordCount);
            System.out.println("Multi-record entities: " + summary.multiRecordCount);
            System.out.println("Cross-source entities: " + summary.crossSourceCount);
            System.out.println("Unique related-entity pairs: " + summary.relationshipCount);
            System.out.println("Audit output directory: " + root.relativize(outputDirectory));
        } finally {
            environment.destroy();
        }
    }

    private static ExportSummary exportEntities(SzEngine engine, Path outputPath)
        throws Exception {
        ExportSummary summary = new ExportSummary();
        long handle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
        try (BufferedWriter writer = Files.newBufferedWriter(
            outputPath, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            String row;
            boolean firstRow = true;
            while ((row = engine.fetchNext(handle)) != null) {
                JsonObject exported = parseObject(row);
                if (firstRow) {
                    List<String> keys = new ArrayList<>(exported.keySet());
                    Collections.sort(keys);
                    summary.firstRowKeys = keys;
                    summary.relatedEntitiesKeyPresent =
                        exported.containsKey("RELATED_ENTITIES");
                    System.out.println("First export row keys: " + keys);
                    System.out.println(
                        "RELATED_ENTITIES present: "
                            + summary.relatedEntitiesKeyPresent);
                    firstRow = false;
                }

                JsonObject resolved = exported.getJsonObject("RESOLVED_ENTITY");
                if (resolved == null || !resolved.containsKey("ENTITY_ID")) {
                    throw new IllegalStateException(
                        "Export row is missing RESOLVED_ENTITY.ENTITY_ID.");
                }
                long entityId = resolved.getJsonNumber("ENTITY_ID").longValue();
                String entityName = resolved.getString("ENTITY_NAME", "");
                JsonArray records = resolved.getJsonArray("RECORDS");
                if (records == null) {
                    throw new IllegalStateException(
                        "Exported entity is missing RESOLVED_ENTITY.RECORDS.");
                }

                EntitySummary entity = new EntitySummary(entityId, entityName);
                for (JsonValue value : records) {
                    if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException(
                            "Exported entity contains a non-object record.");
                    }
                    JsonObject record = (JsonObject) value;
                    String source = record.getString("DATA_SOURCE", "");
                    String recordId = record.getString("RECORD_ID", "");
                    String matchKey = record.getString("MATCH_KEY", "");
                    String rule = record.getString("ERRULE_CODE", "");
                    entity.records.add(new RecordSummary(
                        source, recordId, matchKey, rule));
                    entity.sources.add(source);
                    summary.recordEntityIds.put(recordKey(source, recordId), entityId);
                    summary.recordCount++;
                    summary.recordsBySource.merge(source, 1L, Long::sum);
                }
                summary.entities.add(entity);
                if (entity.records.size() > 1) {
                    summary.multiRecordCount++;
                }
                if (entity.sources.size() > 1) {
                    summary.crossSourceCount++;
                }
                writer.write(exported.toString());
                writer.newLine();

                addRelationships(
                    summary, entityId, exported.getJsonArray("RELATED_ENTITIES"));
            }
        } finally {
            engine.closeExportReport(handle);
        }
        if (summary.entities.isEmpty()) {
            throw new IllegalStateException("The SDK export returned no entities.");
        }
        return summary;
    }

    private static void writeRelationshipFallback(
        SzEngine engine,
        ExportSummary summary,
        Path outputPath
    ) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(
            outputPath, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (EntitySummary entity : summary.entities) {
                javax.json.JsonObjectBuilder result = Json.createObjectBuilder()
                    .add("ENTITY_ID", entity.id);
                try {
                    JsonObject response = parseObject(
                        engine.getEntity(entity.id, SZ_ENTITY_DEFAULT_FLAGS));
                    JsonArray related = response.getJsonArray("RELATED_ENTITIES");
                    result.add("RELATED_ENTITIES_PRESENT", related != null);
                    if (related != null) {
                        result.add("RELATED_ENTITIES", related);
                    }
                    addRelationships(summary, entity.id, related);
                } catch (Exception e) {
                    result.add("ERROR",
                        e.getClass().getSimpleName() + ": " + safeMessage(e));
                }
                writer.write(result.build().toString());
                writer.newLine();
            }
        }
    }

    private static void addRelationships(
        ExportSummary summary,
        long entityId,
        JsonArray relatedEntities
    ) {
        if (relatedEntities == null) {
            return;
        }
        for (JsonValue relatedValue : relatedEntities) {
            if (relatedValue.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            JsonObject related = (JsonObject) relatedValue;
            if (!related.containsKey("ENTITY_ID")) {
                continue;
            }
            long relatedId = related.getJsonNumber("ENTITY_ID").longValue();
            if (relatedId != entityId) {
                String pairKey = relationshipKey(entityId, relatedId);
                RelationshipSummary existing =
                    summary.relationshipPairs.get(pairKey);
                if (existing == null || entityId < existing.entityId) {
                    summary.relationshipPairs.put(pairKey, new RelationshipSummary(
                        entityId,
                        relatedId,
                        related.getString("MATCH_LEVEL_CODE", ""),
                        related.getString("MATCH_KEY", "")));
                }
            }
        }
    }

    private static void auditHowState(
        SzEngine engine,
        List<EntitySummary> entities,
        Path outputPath
    ) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(
            outputPath, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (EntitySummary entity : entities) {
                if (entity.records.size() < 2) {
                    continue;
                }
                javax.json.JsonObjectBuilder result = Json.createObjectBuilder()
                    .add("ENTITY_ID", entity.id)
                    .add("ENTITY_NAME", entity.name)
                    .add("RECORD_COUNT", entity.records.size());
                try {
                    JsonObject response = parseObject(
                        engine.howEntity(entity.id, SZ_HOW_ENTITY_DEFAULT_FLAGS));
                    JsonObject howResults = response.getJsonObject("HOW_RESULTS");
                    JsonObject finalState = howResults == null
                        ? null : howResults.getJsonObject("FINAL_STATE");
                    if (finalState == null) {
                        result.add("checked", false)
                            .add("reason", "FINAL_STATE missing");
                    } else {
                        result.add("checked", true);
                        if (finalState.containsKey("NEED_REEVALUATION")) {
                            result.add(
                                "NEED_REEVALUATION",
                                finalState.getJsonNumber("NEED_REEVALUATION").intValue());
                        }
                        JsonArray virtualEntities =
                            finalState.getJsonArray("VIRTUAL_ENTITIES");
                        if (virtualEntities != null) {
                            result.add("VIRTUAL_ENTITY_COUNT", virtualEntities.size());
                        }
                        result.add("FINAL_STATE", finalState);
                    }
                } catch (Exception e) {
                    result.add("checked", false)
                        .add("reason", e.getClass().getSimpleName() + ": " + safeMessage(e));
                }
                writer.write(result.build().toString());
                writer.newLine();
            }
        }
    }

    private static void writeCrossSourceDetails(
        SzEngine engine,
        ExportSummary summary,
        Path outputPath
    ) throws Exception {
        List<EntitySummary> candidates = new ArrayList<>();
        for (EntitySummary entity : summary.entities) {
            if (entity.sources.size() > 1) {
                candidates.add(entity);
            }
        }
        candidates.sort(Comparator.comparingLong(entity -> entity.id));
        if (candidates.size() > MAX_CROSS_SOURCE_SAMPLES) {
            candidates = new ArrayList<>(
                candidates.subList(0, MAX_CROSS_SOURCE_SAMPLES));
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
            outputPath, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (EntitySummary entity : candidates) {
                javax.json.JsonObjectBuilder result = Json.createObjectBuilder()
                    .add("ENTITY_ID", entity.id)
                    .add("ENTITY_NAME", entity.name)
                    .add("EXPORT_RECORD_COUNT", entity.records.size());
                try {
                    result.add(
                        "GET_ENTITY",
                        parseObject(engine.getEntity(
                            entity.id, SZ_ENTITY_DEFAULT_FLAGS)));
                } catch (Exception e) {
                    result.add("GET_ENTITY_ERROR",
                        e.getClass().getSimpleName() + ": " + safeMessage(e));
                }
                writer.write(result.build().toString());
                writer.newLine();
            }
        }
    }

    private static void writeKnownRecordSamples(
        Path root,
        SzEngine engine,
        ExportSummary summary,
        Path outputPath
    ) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(
            outputPath, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (String source : SOURCES) {
                Path inputPath = root.resolve(
                    "data/senzing-ready/" + source + ".jsonl");
                int sampled = 0;
                try (BufferedReader reader = Files.newBufferedReader(
                    inputPath, StandardCharsets.UTF_8)) {
                    String line;
                    while (sampled < 2 && (line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        JsonObject input = parseObject(trimmed);
                        String recordSource = input.getString("DATA_SOURCE");
                        String recordId = input.getString("RECORD_ID");
                        Long expectedEntityId =
                            summary.recordEntityIds.get(recordKey(recordSource, recordId));
                        javax.json.JsonObjectBuilder result = Json.createObjectBuilder()
                            .add("DATA_SOURCE", recordSource)
                            .add("RECORD_ID", recordId);
                        if (expectedEntityId != null) {
                            result.add("EXPECTED_ENTITY_ID", expectedEntityId);
                        }
                        try {
                            JsonObject response = parseObject(engine.getEntity(
                                SzRecordKey.of(recordSource, recordId),
                                SZ_ENTITY_DEFAULT_FLAGS));
                            JsonObject resolved = response.getJsonObject("RESOLVED_ENTITY");
                            if (resolved != null && resolved.containsKey("ENTITY_ID")) {
                                long actualEntityId =
                                    resolved.getJsonNumber("ENTITY_ID").longValue();
                                result.add("ACTUAL_ENTITY_ID", actualEntityId)
                                    .add("MATCHES_EXPORT",
                                        expectedEntityId != null
                                            && expectedEntityId == actualEntityId)
                                    .add("GET_ENTITY", response);
                            } else {
                                result.add("MATCHES_EXPORT", false)
                                    .add("GET_ENTITY", response);
                            }
                        } catch (Exception e) {
                            result.add("GET_ENTITY_ERROR",
                                e.getClass().getSimpleName() + ": " + safeMessage(e));
                        }
                        writer.write(result.build().toString());
                        writer.newLine();
                        sampled++;
                    }
                }
            }
        }
    }

    private static void writePossibleMatchExplanations(
        SzEngine engine,
        ExportSummary summary,
        Path outputPath
    ) throws Exception {
        List<RelationshipSummary> possibleMatches = new ArrayList<>();
        for (RelationshipSummary relation : summary.uniqueRelationships()) {
            if ("POSSIBLY_SAME".equals(relation.matchLevel)) {
                possibleMatches.add(relation);
            }
        }
        possibleMatches.sort(Comparator
            .comparingLong((RelationshipSummary relation) ->
                Math.min(relation.entityId, relation.relatedId))
            .thenComparingLong(relation ->
                Math.max(relation.entityId, relation.relatedId)));

        try (BufferedWriter writer = Files.newBufferedWriter(
            outputPath, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int count = Math.min(
                possibleMatches.size(), MAX_POSSIBLE_MATCH_EXPLANATIONS);
            for (int i = 0; i < count; i++) {
                RelationshipSummary relation = possibleMatches.get(i);
                javax.json.JsonObjectBuilder result = Json.createObjectBuilder()
                    .add("ENTITY_ID_1", relation.entityId)
                    .add("ENTITY_ID_2", relation.relatedId)
                    .add("MATCH_LEVEL_CODE", relation.matchLevel)
                    .add("MATCH_KEY", relation.matchKey);
                try {
                    result.add(
                        "WHY_RESULTS",
                        parseObject(engine.whyEntities(
                            relation.entityId,
                            relation.relatedId,
                            SZ_WHY_ENTITIES_DEFAULT_FLAGS)));
                } catch (Exception e) {
                    result.add("WHY_ERROR",
                        e.getClass().getSimpleName() + ": " + safeMessage(e));
                }
                writer.write(result.build().toString());
                writer.newLine();
            }
        }
    }

    private static JsonObject parseObject(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isEmpty()
            ? exception.toString() : message;
    }

    private static String recordKey(String source, String recordId) {
        return source + "\u0000" + recordId;
    }

    private static String relationshipKey(long entityId, long relatedId) {
        return Math.min(entityId, relatedId) + ":" + Math.max(entityId, relatedId);
    }

    private static final class ExportSummary {
        private final List<EntitySummary> entities = new ArrayList<>();
        private final TreeMap<String, Long> recordsBySource = new TreeMap<>();
        private final TreeMap<String, Long> recordEntityIds = new TreeMap<>();
        private final Map<String, RelationshipSummary> relationshipPairs =
            new TreeMap<>();
        private List<String> firstRowKeys = Collections.emptyList();
        private boolean relatedEntitiesKeyPresent;
        private long recordCount;
        private long multiRecordCount;
        private long crossSourceCount;
        private long relationshipCount;

        private List<RelationshipSummary> uniqueRelationships() {
            List<RelationshipSummary> relationships =
                new ArrayList<>(relationshipPairs.values());
            relationships.sort(Comparator
                .comparingLong((RelationshipSummary r) -> Math.min(r.entityId, r.relatedId))
                .thenComparingLong(r -> Math.max(r.entityId, r.relatedId)));
            return relationships;
        }
    }

    private static final class EntitySummary {
        private final long id;
        private final String name;
        private final List<RecordSummary> records = new ArrayList<>();
        private final Set<String> sources = new HashSet<>();

        private EntitySummary(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class RecordSummary {
        private final String source;
        private final String recordId;
        private final String matchKey;
        private final String rule;

        private RecordSummary(
            String source, String recordId, String matchKey, String rule
        ) {
            this.source = source;
            this.recordId = recordId;
            this.matchKey = matchKey;
            this.rule = rule;
        }
    }

    private static final class RelationshipSummary {
        private final long entityId;
        private final long relatedId;
        private final String matchLevel;
        private final String matchKey;

        private RelationshipSummary(
            long entityId,
            long relatedId,
            String matchLevel,
            String matchKey
        ) {
            this.entityId = entityId;
            this.relatedId = relatedId;
            this.matchLevel = matchLevel;
            this.matchKey = matchKey;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RelationshipSummary)) {
                return false;
            }
            RelationshipSummary that = (RelationshipSummary) other;
            return Math.min(entityId, relatedId) == Math.min(that.entityId, that.relatedId)
                && Math.max(entityId, relatedId) == Math.max(that.entityId, that.relatedId);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(Math.min(entityId, relatedId)) * 31
                + Long.hashCode(Math.max(entityId, relatedId));
        }
    }
}
