import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEntityIds;
import com.senzing.sdk.SzFlag;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonNumber;
import javax.json.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA;
import static com.senzing.sdk.SzFlag.SZ_FIND_NETWORK_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_FIND_PATH_DEFAULT_FLAGS;

public final class DemonstrateRelationshipNetworks {
    private static final int HUB_LIMIT = 3;
    private static final int MAX_PATH_DEGREES = 5;

    private DemonstrateRelationshipNetworks() {
    }

    public static void main(String[] args) throws Exception {
        List<Long> hubs = readRelationshipHubs();
        Map<Long, EntitySnapshot> entityCache = new HashMap<>();
        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            PathChoice choice = findIndirectPair(engine, hubs, entityCache);
            JsonObject pathResponse = choice.response();
            List<Long> pathEntityIds = choice.entityIds();
            List<Long> networkSeeds = distinctSeedIds(choice.hubId(), pathEntityIds);

            String networkJson = engine.findNetwork(
                    SzEntityIds.of(toLongArray(networkSeeds)),
                    Math.max(2, pathEntityIds.size() - 1), 0, 100,
                    SZ_FIND_NETWORK_DEFAULT_FLAGS);
            JsonObject networkResponse = QuerySupport.parseObject(
                    networkJson, "findNetwork response");
            JsonObject report = createReport(
                    choice, pathResponse, networkResponse, networkSeeds,
                    engine, entityCache);

            Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("relationship_networks.json");
            QuerySupport.writeJson(outputPath, report);
            printSummary(report, outputPath);
        }
    }

    private static List<Long> readRelationshipHubs() throws Exception {
        Path path = QuerySupport.REPORT_DIRECTORY.resolve("discover_patterns.json");
        JsonObject report = QuerySupport.parseObject(Files.readString(path), path.toString());
        JsonArray candidates = QuerySupport.requiredArray(
                QuerySupport.requiredObject(report, "candidates", "discover_patterns.json"),
                "relationships", "candidates");
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Step 4a found no entities with relationships.");
        }
        List<Long> hubs = new ArrayList<>();
        for (int i = 0; i < Math.min(HUB_LIMIT, candidates.size()); i++) {
            JsonObject candidate = candidates.getJsonObject(i);
            hubs.add(candidate.getJsonNumber("entity_id").longValue());
        }
        return List.copyOf(hubs);
    }

    private static PathChoice findIndirectPair(
            SzEngine engine,
            List<Long> hubs,
            Map<Long, EntitySnapshot> entityCache) throws Exception {
        List<Long> checkedHubs = hubs.subList(0, Math.min(HUB_LIMIT, hubs.size()));
        for (long hubId : checkedHubs) {
            EntitySnapshot hub = entity(engine, hubId, entityCache);
            List<Long> neighbors = new ArrayList<>(new TreeSet<>(hub.relatedEntityIds()));
            for (int i = 0; i < neighbors.size(); i++) {
                for (int j = i + 1; j < neighbors.size(); j++) {
                    long startId = neighbors.get(i);
                    long endId = neighbors.get(j);
                    EntitySnapshot start = entity(engine, startId, entityCache);
                    EntitySnapshot end = entity(engine, endId, entityCache);
                    if (start.relatedEntityIds().contains(endId)
                            || end.relatedEntityIds().contains(startId)) {
                        continue;
                    }

                    JsonObject response = findPath(engine, startId, endId);
                    List<List<Long>> paths = parsePaths(response, "findPath response");
                    for (List<Long> path : paths) {
                        if (path.size() >= 3) {
                            return new PathChoice(
                                    hubId, startId, endId, true, checkedHubs,
                                    path, response);
                        }
                    }
                }
            }
        }

        for (long hubId : checkedHubs) {
            EntitySnapshot hub = entity(engine, hubId, entityCache);
            List<Long> neighbors = new ArrayList<>(new TreeSet<>(hub.relatedEntityIds()));
            for (int i = 0; i < neighbors.size(); i++) {
                for (int j = i + 1; j < neighbors.size(); j++) {
                    long startId = neighbors.get(i);
                    long endId = neighbors.get(j);
                    EntitySnapshot start = entity(engine, startId, entityCache);
                    EntitySnapshot end = entity(engine, endId, entityCache);
                    if (!start.relatedEntityIds().contains(endId)
                            && !end.relatedEntityIds().contains(startId)) {
                        continue;
                    }
                    JsonObject response = findPath(engine, startId, endId);
                    List<List<Long>> paths = parsePaths(response, "findPath response");
                    if (!paths.isEmpty()) {
                        List<Long> path = paths.get(0);
                        return new PathChoice(
                                hubId, startId, endId, false, checkedHubs,
                                path, response);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "No path was returned for the first three relationship hubs or their neighbors.");
    }

    private static JsonObject findPath(SzEngine engine, long startId, long endId) throws Exception {
        String json = engine.findPath(
                startId, endId, MAX_PATH_DEGREES, SZ_FIND_PATH_DEFAULT_FLAGS);
        return QuerySupport.parseObject(json, "findPath " + startId + " to " + endId);
    }

    private static List<List<Long>> parsePaths(JsonObject response, String context) {
        JsonArray paths = QuerySupport.requiredArray(response, "ENTITY_PATHS", context);
        List<List<Long>> result = new ArrayList<>();
        for (JsonValue pathValue : paths) {
            if (pathValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(context + ".ENTITY_PATHS contains a non-object.");
            }
            JsonArray entities = QuerySupport.requiredArray(
                    pathValue.asJsonObject(), "ENTITIES", context + ".ENTITY_PATHS[]");
            List<Long> entityIds = new ArrayList<>();
            for (JsonValue entityValue : entities) {
                if (entityValue.getValueType() != JsonValue.ValueType.NUMBER) {
                    throw new IllegalStateException(
                            context + ".ENTITY_PATHS[].ENTITIES contains a non-number.");
                }
                entityIds.add(((JsonNumber) entityValue).longValue());
            }
            result.add(List.copyOf(entityIds));
        }
        return List.copyOf(result);
    }

    private static EntitySnapshot entity(
            SzEngine engine,
            long entityId,
            Map<Long, EntitySnapshot> cache) throws Exception {
        EntitySnapshot cached = cache.get(entityId);
        if (cached != null) {
            return cached;
        }

        Set<SzFlag> flags = new HashSet<>(SZ_ENTITY_DEFAULT_FLAGS);
        flags.add(SZ_ENTITY_INCLUDE_RECORD_JSON_DATA);
        String json = engine.getEntity(entityId, flags);
        JsonObject response = QuerySupport.parseObject(json, "getEntity " + entityId);
        JsonArray relatedEntities = QuerySupport.requiredArray(
                response, "RELATED_ENTITIES", "getEntity response");
        Set<Long> relatedIds = new TreeSet<>();
        for (JsonValue relatedValue : relatedEntities) {
            if (relatedValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("RELATED_ENTITIES contains a non-object.");
            }
            relatedIds.add(relatedValue.asJsonObject()
                    .getJsonNumber("ENTITY_ID").longValue());
        }

        JsonObject resolvedEntity = QuerySupport.requiredObject(
                response, "RESOLVED_ENTITY", "getEntity response");
        JsonArray records = QuerySupport.requiredArray(
                resolvedEntity, "RECORDS", "RESOLVED_ENTITY");
        List<RelationshipReference> references = new ArrayList<>();
        for (JsonValue recordValue : records) {
            if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("RESOLVED_ENTITY.RECORDS contains a non-object.");
            }
            JsonObject record = recordValue.asJsonObject();
            String source = requiredString(record, "DATA_SOURCE", "record");
            String recordId = requiredString(record, "RECORD_ID", "record");
            JsonObject data = QuerySupport.requiredObject(record, "JSON_DATA", "entity record");
            JsonArray features = QuerySupport.requiredArray(data, "FEATURES", "JSON_DATA");
            for (JsonValue featureValue : features) {
                if (featureValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("JSON_DATA.FEATURES contains a non-object.");
                }
                JsonObject feature = featureValue.asJsonObject();
                addRelationshipReference(
                        references, feature, "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY",
                        "ANCHOR", null, source, recordId);
                addRelationshipReference(
                        references, feature, "REL_POINTER_DOMAIN", "REL_POINTER_KEY",
                        "POINTER", QuerySupport.optionalString(feature, "REL_POINTER_ROLE"),
                        source, recordId);
            }
        }

        EntitySnapshot snapshot = new EntitySnapshot(
                entityId, Set.copyOf(relatedIds), List.copyOf(references));
        cache.put(entityId, snapshot);
        return snapshot;
    }

    private static void addRelationshipReference(
            List<RelationshipReference> references,
            JsonObject feature,
            String domainKey,
            String valueKey,
            String referenceType,
            String role,
            String source,
            String recordId) {
        String domain = QuerySupport.optionalString(feature, domainKey);
        String key = QuerySupport.optionalString(feature, valueKey);
        if (domain != null && key != null) {
            references.add(new RelationshipReference(
                    domain, key, referenceType, role, source, recordId));
        }
    }

    private static JsonObject createReport(
            PathChoice choice,
            JsonObject pathResponse,
            JsonObject networkResponse,
            List<Long> networkSeeds,
            SzEngine engine,
            Map<Long, EntitySnapshot> entityCache) throws Exception {
        JsonArray networkEntities = QuerySupport.requiredArray(
                networkResponse, "ENTITIES", "findNetwork response");
        Map<Long, EntityView> entityViews = new LinkedHashMap<>();
        for (JsonValue entityValue : networkEntities) {
            if (entityValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("ENTITIES contains a non-object.");
            }
            JsonObject resolved = QuerySupport.requiredObject(
                    entityValue.asJsonObject(), "RESOLVED_ENTITY", "ENTITIES[]");
            long entityId = resolved.getJsonNumber("ENTITY_ID").longValue();
            String entityName = requiredString(resolved, "ENTITY_NAME", "RESOLVED_ENTITY");
            JsonArray summaries = QuerySupport.requiredArray(
                    resolved, "RECORD_SUMMARY", "RESOLVED_ENTITY");
            Set<String> sources = new TreeSet<>();
            int recordCount = 0;
            for (JsonValue summaryValue : summaries) {
                if (summaryValue.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException("RECORD_SUMMARY contains a non-object.");
                }
                JsonObject summary = summaryValue.asJsonObject();
                sources.add(requiredString(summary, "DATA_SOURCE", "RECORD_SUMMARY[]"));
                recordCount += summary.getInt("RECORD_COUNT");
            }
            entityViews.put(entityId, new EntityView(entityId, entityName, sources, recordCount));
        }

        for (long entityId : entityViews.keySet()) {
            entity(engine, entityId, entityCache);
        }

        JsonArray links = QuerySupport.requiredArray(
                networkResponse, "ENTITY_NETWORK_LINKS", "findNetwork response");
        JsonArrayBuilder reportLinks = Json.createArrayBuilder();
        for (JsonValue linkValue : links) {
            if (linkValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("ENTITY_NETWORK_LINKS contains a non-object.");
            }
            JsonObject link = linkValue.asJsonObject();
            long minId = link.getJsonNumber("MIN_ENTITY_ID").longValue();
            long maxId = link.getJsonNumber("MAX_ENTITY_ID").longValue();
            EntityView min = requiredEntityView(entityViews, minId);
            EntityView max = requiredEntityView(entityViews, maxId);
            JsonArray evidence = relationEvidence(
                    entityCache.get(minId), entityCache.get(maxId));
            reportLinks.add(Json.createObjectBuilder()
                    .add("min_entity_id", minId)
                    .add("min_entity_name", min.name())
                    .add("max_entity_id", maxId)
                    .add("max_entity_name", max.name())
                    .add("match_level_code", requiredString(link, "MATCH_LEVEL_CODE", "network link"))
                    .add("match_key", requiredString(link, "MATCH_KEY", "network link"))
                    .add("is_disclosed", link.getInt("IS_DISCLOSED"))
                    .add("is_ambiguous", link.getInt("IS_AMBIGUOUS"))
                    .add("shared_relationship_attributes", evidence));
        }

        JsonArrayBuilder reportNodes = Json.createArrayBuilder();
        for (EntityView view : entityViews.values()) {
            JsonArrayBuilder sources = Json.createArrayBuilder();
            view.sources().forEach(sources::add);
            reportNodes.add(Json.createObjectBuilder()
                    .add("entity_id", view.entityId())
                    .add("entity_name", view.name())
                    .add("data_sources", sources)
                    .add("record_count", view.recordCount()));
        }

        List<List<Long>> returnedPaths = parsePaths(networkResponse, "findNetwork response");
        JsonArrayBuilder reportNetworkPaths = Json.createArrayBuilder();
        returnedPaths.forEach(path -> reportNetworkPaths.add(longArray(path)));
        List<Long> checkedHubs = choice.checkedHubs();
        JsonArrayBuilder checked = longArray(checkedHubs);
        JsonArrayBuilder reportPathLinks = Json.createArrayBuilder();
        JsonArray pathLinks = QuerySupport.requiredArray(
                pathResponse, "ENTITY_PATH_LINKS", "findPath response");
        for (JsonValue linkValue : pathLinks) {
            JsonObject link = linkValue.asJsonObject();
            long minId = link.getJsonNumber("MIN_ENTITY_ID").longValue();
            long maxId = link.getJsonNumber("MAX_ENTITY_ID").longValue();
            reportPathLinks.add(Json.createObjectBuilder()
                    .add("min_entity_id", minId)
                    .add("max_entity_id", maxId)
                    .add("match_level_code", requiredString(link, "MATCH_LEVEL_CODE", "path link"))
                    .add("match_key", requiredString(link, "MATCH_KEY", "path link"))
                    .add("is_disclosed", link.getInt("IS_DISCLOSED"))
                    .add("is_ambiguous", link.getInt("IS_AMBIGUOUS"))
                    .add("shared_relationship_attributes", relationEvidence(
                            entityCache.get(minId), entityCache.get(maxId))));
        }

        return Json.createObjectBuilder()
                .add("step", "4d")
                .add("hub_entities_checked", checked)
                .add("selected_hub_entity_id", choice.hubId())
                .add("indirect_pair_found", choice.indirect())
                .add("start_entity_id", choice.startId())
                .add("end_entity_id", choice.endId())
                .add("degrees_of_separation", Math.max(0, choice.entityIds().size() - 1))
                .add("path_entity_ids", longArray(choice.entityIds()))
                .add("path_links", reportPathLinks)
                .add("network_seed_entity_ids", longArray(networkSeeds))
                .add("network_paths", reportNetworkPaths)
                .add("network_entities", reportNodes)
                .add("network_links", reportLinks)
                .build();
    }

    private static JsonArray relationEvidence(EntitySnapshot left, EntitySnapshot right) {
        JsonArrayBuilder evidence = Json.createArrayBuilder();
        for (RelationshipReference a : left.references()) {
            for (RelationshipReference b : right.references()) {
                if (!a.domain().equals(b.domain()) || !a.key().equals(b.key())) {
                    continue;
                }
                JsonObjectBuilder item = Json.createObjectBuilder()
                        .add("domain", a.domain())
                        .add("key", a.key())
                        .add("left_reference_type", a.referenceType())
                        .add("right_reference_type", b.referenceType())
                        .add("left_source_record", a.source() + "/" + a.recordId())
                        .add("right_source_record", b.source() + "/" + b.recordId());
                QuerySupport.addOptionalString(item, "left_role", a.role());
                QuerySupport.addOptionalString(item, "right_role", b.role());
                evidence.add(item);
            }
        }
        return evidence.build();
    }

    private static EntityView requiredEntityView(Map<Long, EntityView> entities, long entityId) {
        EntityView entity = entities.get(entityId);
        if (entity == null) {
            throw new IllegalStateException(
                    "Network link references entity " + entityId + " missing from ENTITIES.");
        }
        return entity;
    }

    private static List<Long> distinctSeedIds(long hubId, List<Long> path) {
        LinkedHashMap<Long, Boolean> distinct = new LinkedHashMap<>();
        distinct.put(hubId, Boolean.TRUE);
        path.forEach(entityId -> distinct.put(entityId, Boolean.TRUE));
        return List.copyOf(distinct.keySet());
    }

    private static long[] toLongArray(List<Long> values) {
        long[] result = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static JsonArrayBuilder longArray(List<Long> values) {
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

    private static void printSummary(JsonObject report, Path outputPath) {
        JsonArray pathEntityIds = report.getJsonArray("path_entity_ids");
        System.out.printf(
                "Relationship network: selected hub=%d, path=%s, degrees=%d, links=%d; output=%s%n",
                report.getJsonNumber("selected_hub_entity_id").longValue(),
                pathEntityIds,
                report.getInt("degrees_of_separation"),
                report.getJsonArray("network_links").size(),
                outputPath);
        for (JsonValue value : report.getJsonArray("network_entities")) {
            JsonObject entity = value.asJsonObject();
            System.out.printf("  Entity %d: %s (%d records, sources=%s)%n",
                    entity.getJsonNumber("entity_id").longValue(),
                    entity.getString("entity_name"),
                    entity.getInt("record_count"),
                    entity.getJsonArray("data_sources"));
        }
        for (JsonValue value : report.getJsonArray("network_links")) {
            JsonObject link = value.asJsonObject();
            System.out.printf("  %d -- %d: %s / %s (disclosed=%d)%n",
                    link.getJsonNumber("min_entity_id").longValue(),
                    link.getJsonNumber("max_entity_id").longValue(),
                    link.getString("match_level_code"),
                    link.getString("match_key"),
                    link.getInt("is_disclosed"));
        }
    }

    private record EntitySnapshot(
            long entityId,
            Set<Long> relatedEntityIds,
            List<RelationshipReference> references) {
    }

    private record RelationshipReference(
            String domain,
            String key,
            String referenceType,
            String role,
            String source,
            String recordId) {
    }

    private record EntityView(long entityId, String name, Set<String> sources, int recordCount) {
    }

    private record PathChoice(
            long hubId,
            long startId,
            long endId,
            boolean indirect,
            List<Long> checkedHubs,
            List<Long> entityIds,
            JsonObject response) {
    }
}
