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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_ALL_RELATIONS;
import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_DATA;
import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RELATED_MATCHING_INFO;

public final class DiscoverDataPatterns {
    private static final Set<SzFlag> ENTITY_FLAGS = entityFlags();

    private DiscoverDataPatterns() {
    }

    private static Set<SzFlag> entityFlags() {
        Set<SzFlag> flags = new HashSet<>(SZ_ENTITY_INCLUDE_ALL_RELATIONS);
        flags.add(SZ_ENTITY_INCLUDE_RECORD_DATA);
        flags.add(SZ_ENTITY_INCLUDE_RELATED_MATCHING_INFO);
        return Set.copyOf(flags);
    }

    public static void main(String[] args) throws Exception {
        Map<Long, EntityPattern> entities = new TreeMap<>();
        Map<String, Integer> recordsBySource = new TreeMap<>();
        int recordsInspected = 0;

        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (String source : QuerySupport.SOURCE_CODES) {
                List<QuerySupport.SourceRecord> sourceRecords = QuerySupport.readSourceRecords(source);
                recordsBySource.put(source, sourceRecords.size());

                for (QuerySupport.SourceRecord sourceRecord : sourceRecords) {
                    QuerySupport.RecordKey key = sourceRecord.key();
                    String responseJson = engine.getEntity(
                            SzRecordKey.of(key.dataSource(), key.recordId()), ENTITY_FLAGS);
                    JsonObject response = QuerySupport.parseObject(
                            responseJson, "getEntity for " + key.dataSource() + "/" + key.recordId());
                    JsonObject resolvedEntity = QuerySupport.requiredObject(
                            response, "RESOLVED_ENTITY", "getEntity response");
                    long entityId = resolvedEntity.getJsonNumber("ENTITY_ID").longValue();
                    JsonArray records = QuerySupport.requiredArray(
                            resolvedEntity, "RECORDS", "RESOLVED_ENTITY");
                    if (records.isEmpty()) {
                        throw new IllegalStateException("Entity " + entityId + " has no records.");
                    }

                    Set<String> sources = new TreeSet<>();
                    for (JsonValue recordValue : records) {
                        if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException(
                                    "RESOLVED_ENTITY.RECORDS contains a non-object.");
                        }
                        sources.add(recordValue.asJsonObject().getString("DATA_SOURCE"));
                    }

                    JsonArray relatedEntities = QuerySupport.requiredArray(
                            response, "RELATED_ENTITIES", "getEntity response");
                    int disclosedCount = 0;
                    for (JsonValue relatedValue : relatedEntities) {
                        if (relatedValue.getValueType() != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException("RELATED_ENTITIES contains a non-object.");
                        }
                        JsonObject related = relatedValue.asJsonObject();
                        JsonValue disclosedValue = related.get("IS_DISCLOSED");
                        if (disclosedValue == null
                                || disclosedValue.getValueType() != JsonValue.ValueType.NUMBER) {
                            throw new IllegalStateException(
                                    "RELATED_ENTITIES entry is missing numeric IS_DISCLOSED for entity "
                                            + entityId + ": " + related);
                        }
                        int isDisclosed = related.getInt("IS_DISCLOSED");
                        if (isDisclosed == 1) {
                            disclosedCount++;
                        } else if (isDisclosed != 0) {
                            throw new IllegalStateException(
                                    "Unexpected IS_DISCLOSED value: " + isDisclosed);
                        }
                    }

                    EntityPattern pattern = new EntityPattern(
                            entityId, records.size(), Set.copyOf(sources),
                            relatedEntities.size(), disclosedCount,
                            relatedEntities.size() - disclosedCount);
                    EntityPattern previous = entities.putIfAbsent(entityId, pattern);
                    if (previous != null && !previous.equals(pattern)) {
                        throw new IllegalStateException(
                                "Inconsistent entity details across records for entity " + entityId);
                    }
                    recordsInspected++;
                }
            }
        }

        List<EntityPattern> allEntities = new ArrayList<>(entities.values());
        List<EntityPattern> multiRecordEntities = allEntities.stream()
                .filter(entity -> entity.recordCount() >= 3)
                .sorted(Comparator.comparingInt(EntityPattern::recordCount).reversed()
                        .thenComparingLong(EntityPattern::entityId))
                .toList();
        List<EntityPattern> crossSourceMultiRecordEntities = multiRecordEntities.stream()
                .filter(entity -> entity.sources().size() >= 2)
                .toList();
        List<EntityPattern> allCrossSourceEntities = allEntities.stream()
                .filter(entity -> entity.sources().size() >= 2)
                .toList();
        List<EntityPattern> entitiesWithRelationships = allEntities.stream()
                .filter(entity -> entity.relationshipCount() > 0)
                .sorted(Comparator.comparingInt(EntityPattern::relationshipCount).reversed()
                        .thenComparingLong(EntityPattern::entityId))
                .toList();

        JsonObject report = Json.createObjectBuilder()
                .add("records_inspected", recordsInspected)
                .add("records_by_source", jsonCounts(recordsBySource))
                .add("entities_found", allEntities.size())
                .add("cross_source_entities_all_sizes", allCrossSourceEntities.size())
                .add("patterns_found", Json.createObjectBuilder()
                        .add("multi_record", multiRecordEntities.size())
                        .add("cross_source", crossSourceMultiRecordEntities.size())
                        .add("relationships", entitiesWithRelationships.size()))
                .add("candidates", Json.createObjectBuilder()
                        .add("multi_record", jsonEntities(multiRecordEntities, 10))
                        .add("cross_source_multi_record", jsonEntities(crossSourceMultiRecordEntities, 10))
                        .add("relationships", jsonEntities(entitiesWithRelationships, 10)))
                .build();

        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("discover_patterns.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.printf(
                "Discover patterns: %d records, %d entities, %d entities with 3+ records, "
                        + "%d cross-source among them, %d entities with relationships; output=%s%n",
                recordsInspected, allEntities.size(), multiRecordEntities.size(),
                crossSourceMultiRecordEntities.size(), entitiesWithRelationships.size(), outputPath);
    }

    private static JsonObject jsonCounts(Map<String, Integer> counts) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        counts.forEach(builder::add);
        return builder.build();
    }

    private static JsonArray jsonEntities(List<EntityPattern> entities, int limit) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (EntityPattern entity : entities.stream().limit(limit).toList()) {
            JsonArrayBuilder sources = Json.createArrayBuilder();
            entity.sources().stream().sorted().forEach(sources::add);
            builder.add(Json.createObjectBuilder()
                    .add("entity_id", entity.entityId())
                    .add("record_count", entity.recordCount())
                    .add("data_sources", sources)
                    .add("relationship_count", entity.relationshipCount())
                    .add("disclosed_relationships", entity.disclosedCount())
                    .add("discovered_relationships", entity.discoveredCount()));
        }
        return builder.build();
    }

    private record EntityPattern(
            long entityId,
            int recordCount,
            Set<String> sources,
            int relationshipCount,
            int disclosedCount,
            int discoveredCount) {
    }
}
