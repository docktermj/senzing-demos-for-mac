package query;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzRecordKey;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_BRIEF_DEFAULT_FLAGS;

public final class DiscoverPatterns {
    private static final List<Path> DEFAULT_INPUTS = List.of(
        Path.of("data/senzing-ready/BANK_CUSTOMERS.jsonl"),
        Path.of("data/senzing-ready/BUSINESS_REGISTRY.jsonl"),
        Path.of("data/senzing-ready/AML_WATCHLIST.jsonl"));

    private DiscoverPatterns() {}

    public static void main(String[] args) throws Exception {
        List<Path> inputs = args.length == 0
            ? DEFAULT_INPUTS
            : java.util.Arrays.stream(args).map(Path::of).toList();
        LinkedHashMap<QuerySupport.RecordKey, JsonObject> records =
            new LinkedHashMap<>();
        for (Path input : inputs) {
            records.putAll(QuerySupport.readUniqueRecords(input));
        }
        if (records.isEmpty()) {
            throw new IllegalStateException("No record keys were found in the inputs.");
        }

        SzEnvironment environment = QuerySupport.openEnvironment(
            DiscoverPatterns.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            Map<Long, EntityPattern> entities = new TreeMap<>();
            for (QuerySupport.RecordKey recordKey : records.keySet()) {
                JsonObject response = QuerySupport.parseObject(
                    engine.getEntity(
                        SzRecordKey.of(recordKey.dataSource(), recordKey.recordId()),
                        SZ_ENTITY_BRIEF_DEFAULT_FLAGS));
                JsonObject resolved = QuerySupport.requireObject(
                    response, "RESOLVED_ENTITY");
                long entityId = QuerySupport.requiredLong(resolved, "ENTITY_ID");
                EntityPattern pattern = entities.computeIfAbsent(
                    entityId, EntityPattern::new);
                pattern.records.add(recordKey);

                RelationshipCounts relationshipCounts = countRelationships(
                    QuerySupport.requireArray(response, "RELATED_ENTITIES"));
                if (pattern.relationships == null) {
                    pattern.relationships = relationshipCounts;
                } else if (!pattern.relationships.equals(relationshipCounts)) {
                    throw new IllegalStateException(
                        "Relationship counts changed across records in entity " + entityId);
                }
            }

            List<EntityPattern> largeEntities = entities.values().stream()
                .filter(entity -> entity.records.size() >= 3)
                .toList();
            long crossSourceLargeEntities = largeEntities.stream()
                .filter(entity -> sources(entity).size() > 1)
                .count();
            long entitiesWithRelationships = entities.values().stream()
                .filter(entity -> entity.relationships.total() > 0)
                .count();

            System.out.println("Unique loaded record keys: " + records.size());
            System.out.println("Resolved entities touched: " + entities.size());
            System.out.println("Multi-record entities (3+ records): "
                + largeEntities.size());
            System.out.println("Cross-source entities among those: "
                + crossSourceLargeEntities);
            System.out.println("Entities with one or more relationships: "
                + entitiesWithRelationships);
            System.out.println("Multi-record candidates:");
            for (EntityPattern entity : largeEntities) {
                System.out.println(
                    "  Entity " + entity.id + " records=" + entity.records.size()
                        + " sources=" + sources(entity)
                        + " relationships=" + entity.relationships.total()
                        + " disclosed=" + entity.relationships.disclosed()
                        + " discovered=" + entity.relationships.discovered());
            }

            System.out.println("Top relationship candidates:");
            entities.values().stream()
                .filter(entity -> entity.relationships.total() > 0)
                .sorted(Comparator
                    .comparingInt((EntityPattern entity) -> entity.relationships.total())
                    .reversed()
                    .thenComparingLong(entity -> entity.id))
                .limit(10)
                .forEach(entity -> System.out.println(
                    "  Entity " + entity.id
                        + " relationships=" + entity.relationships.total()
                        + " disclosed=" + entity.relationships.disclosed()
                        + " discovered=" + entity.relationships.discovered()
                        + " records=" + entity.records.size()
                        + " sources=" + sources(entity)));
        } finally {
            environment.destroy();
        }
    }

    private static RelationshipCounts countRelationships(JsonArray relatedEntities) {
        int disclosed = 0;
        for (JsonValue value : relatedEntities) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "RELATED_ENTITIES contains a non-object item.");
            }
            JsonObject related = (JsonObject) value;
            if (QuerySupport.requiredLong(related, "IS_DISCLOSED") == 1) {
                disclosed++;
            }
        }
        return new RelationshipCounts(
            relatedEntities.size(), disclosed, relatedEntities.size() - disclosed);
    }

    private static Set<String> sources(EntityPattern entity) {
        Set<String> sources = new TreeSet<>();
        for (QuerySupport.RecordKey record : entity.records) {
            sources.add(record.dataSource());
        }
        return sources;
    }

    private static final class EntityPattern {
        private final long id;
        private final List<QuerySupport.RecordKey> records = new ArrayList<>();
        private RelationshipCounts relationships;

        private EntityPattern(long id) {
            this.id = id;
        }
    }

    private record RelationshipCounts(int total, int disclosed, int discovered) {}
}
