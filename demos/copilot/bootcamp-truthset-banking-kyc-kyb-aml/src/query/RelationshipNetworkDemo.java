package query;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEntityIds;
import com.senzing.sdk.SzEnvironment;

import static com.senzing.sdk.SzFlag.SZ_FIND_NETWORK_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_FIND_PATH_DEFAULT_FLAGS;

public final class RelationshipNetworkDemo {
    private static final int MAX_DEGREES = 2;
    private static final int BUILD_OUT_DEGREES = 1;
    private static final int BUILD_OUT_MAX_ENTITIES = 100;

    private RelationshipNetworkDemo() {}

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && "network".equals(args[0]) && args.length == 4) {
            runNetwork(
                Long.parseLong(args[1]),
                Long.parseLong(args[2]),
                Long.parseLong(args[3]));
            return;
        }
        if (args.length >= 3 && "path".equals(args[0]) && args.length <= 4) {
            int maxDegrees = args.length == 4
                ? Integer.parseInt(args[3])
                : 3;
            runPath(
                Long.parseLong(args[1]),
                Long.parseLong(args[2]),
                maxDegrees);
            return;
        }
        throw new IllegalArgumentException(
            "Usage: query.RelationshipNetworkDemo network <ENTITY_ID_1>"
                + " <ENTITY_ID_2> <ENTITY_ID_3> | path <START_ID> <END_ID>"
                + " [MAX_DEGREES]");
    }

    private static void runNetwork(
        long entityId1,
        long entityId2,
        long entityId3) throws Exception {
        SzEnvironment environment = QuerySupport.openEnvironment(
            RelationshipNetworkDemo.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            String response = engine.findNetwork(
                SzEntityIds.of(entityId1, entityId2, entityId3),
                MAX_DEGREES,
                BUILD_OUT_DEGREES,
                BUILD_OUT_MAX_ENTITIES,
                SZ_FIND_NETWORK_DEFAULT_FLAGS);
            JsonObject result = QuerySupport.parseObject(response);
            printNetwork(
                result,
                List.of(entityId1, entityId2, entityId3));
        } finally {
            environment.destroy();
        }
    }

    private static void printNetwork(JsonObject result, List<Long> seedIds) {
        JsonArray entities = QuerySupport.requireArray(result, "ENTITIES");
        JsonArray links = QuerySupport.requireArray(result, "ENTITY_NETWORK_LINKS");
        JsonArray paths = QuerySupport.requireArray(result, "ENTITY_PATHS");
        Map<Long, String> labels = new HashMap<>();
        Map<Long, Set<Long>> adjacency = new HashMap<>();

        System.out.println("Network entities: " + entities.size());
        for (JsonValue value : entities) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("ENTITIES contains a non-object item.");
            }
            JsonObject entity = QuerySupport.requireObject(
                (JsonObject) value, "RESOLVED_ENTITY");
            long entityId = QuerySupport.requiredLong(entity, "ENTITY_ID");
            String name = QuerySupport.optionalString(entity, "ENTITY_NAME");
            String sources = summarizeSources(entity);
            labels.put(entityId, entityLabel(entityId, name, sources));
            System.out.println("  " + labels.get(entityId));
        }

        System.out.println("Network links: " + links.size());
        if (!links.isEmpty()) {
            JsonObject firstLink = requireObject(links.get(0), "ENTITY_NETWORK_LINKS[0]");
            List<String> keys = new ArrayList<>(firstLink.keySet());
            Collections.sort(keys);
            System.out.println(
                "  First raw link keys: " + String.join(", ", keys)
                    + " | " + firstLink);
        }
        for (JsonValue value : links) {
            JsonObject link = requireObject(value, "ENTITY_NETWORK_LINKS[]");
            long minId = QuerySupport.requiredLong(link, "MIN_ENTITY_ID");
            long maxId = QuerySupport.requiredLong(link, "MAX_ENTITY_ID");
            adjacency.computeIfAbsent(minId, ignored -> new LinkedHashSet<>()).add(maxId);
            adjacency.computeIfAbsent(maxId, ignored -> new LinkedHashSet<>()).add(minId);
            System.out.println(
                "  " + entityLabel(labels, minId) + " -- " + entityLabel(labels, maxId)
                    + " | match_key=" + QuerySupport.optionalString(link, "MATCH_KEY")
                    + " | match_level="
                    + QuerySupport.optionalString(link, "MATCH_LEVEL_CODE")
                    + " | disclosed="
                    + QuerySupport.optionalString(link, "IS_DISCLOSED")
                    + " | ambiguous="
                    + QuerySupport.optionalString(link, "IS_AMBIGUOUS"));
        }

        System.out.println("Seed-to-seed paths: " + paths.size());
        for (JsonValue value : paths) {
            JsonObject path = requireObject(value, "ENTITY_PATHS[]");
            JsonValue pathEntities = path.get("ENTITIES");
            System.out.println(
                "  " + QuerySupport.optionalString(path, "START_ENTITY_ID")
                    + " -> " + QuerySupport.optionalString(path, "END_ENTITY_ID")
                    + " via " + (pathEntities == null ? "(not returned)" : pathEntities));
        }

        for (long seedId : seedIds) {
            List<Long> neighbors = new ArrayList<>(
                adjacency.getOrDefault(seedId, Set.of()));
            Collections.sort(neighbors);
            System.out.println(
                "Neighbors of hub candidate " + entityLabel(labels, seedId)
                    + ": " + neighbors);
        }
    }

    private static void runPath(
        long startId,
        long endId,
        int maxDegrees) throws Exception {
        SzEnvironment environment = QuerySupport.openEnvironment(
            RelationshipNetworkDemo.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            String response = engine.findPath(
                startId, endId, maxDegrees, SZ_FIND_PATH_DEFAULT_FLAGS);
            JsonObject result = QuerySupport.parseObject(response);
            printPath(result, startId, endId);
        } finally {
            environment.destroy();
        }
    }

    private static void printPath(JsonObject result, long startId, long endId) {
        JsonArray paths = QuerySupport.requireArray(result, "ENTITY_PATHS");
        JsonArray links = QuerySupport.requireArray(result, "ENTITY_PATH_LINKS");
        System.out.println(
            "findPath " + startId + " -> " + endId
                + ": " + paths.size() + " path(s), "
                + links.size() + " link(s).");
        if (paths.isEmpty() || links.isEmpty()) {
            System.out.println("No path links were returned for this pair.");
            return;
        }

        JsonObject firstPath = requireObject(paths.get(0), "ENTITY_PATHS[0]");
        System.out.println("  First raw path: " + firstPath);
        JsonObject firstLink = requireObject(links.get(0), "ENTITY_PATH_LINKS[0]");
        List<String> linkKeys = new ArrayList<>(firstLink.keySet());
        Collections.sort(linkKeys);
        System.out.println(
            "  First raw link keys: " + String.join(", ", linkKeys)
                + " | " + firstLink);

        Map<Long, Set<Long>> adjacency = new HashMap<>();
        Map<String, JsonObject> edgeDetails = new LinkedHashMap<>();
        for (JsonValue value : links) {
            JsonObject link = requireObject(value, "ENTITY_PATH_LINKS[]");
            long minId = QuerySupport.requiredLong(link, "MIN_ENTITY_ID");
            long maxId = QuerySupport.requiredLong(link, "MAX_ENTITY_ID");
            adjacency.computeIfAbsent(minId, ignored -> new LinkedHashSet<>()).add(maxId);
            adjacency.computeIfAbsent(maxId, ignored -> new LinkedHashSet<>()).add(minId);
            edgeDetails.put(edgeKey(minId, maxId), link);
        }

        List<Long> route = shortestRoute(adjacency, startId, endId);
        if (route.isEmpty()) {
            System.out.println(
                "Could not order the returned links from the requested start to end.");
            return;
        }
        System.out.println(
            "  Shortest route: " + joinRoute(route)
                + " (" + (route.size() - 1) + " degree(s))");
        for (int i = 1; i < route.size(); i++) {
            JsonObject link = edgeDetails.get(edgeKey(route.get(i - 1), route.get(i)));
            if (link == null) {
                throw new IllegalStateException(
                    "A path step has no matching ENTITY_PATH_LINKS entry.");
            }
            System.out.println(
                "  " + route.get(i - 1) + " -- " + route.get(i)
                    + " | match_key=" + QuerySupport.optionalString(link, "MATCH_KEY")
                    + " | match_level="
                    + QuerySupport.optionalString(link, "MATCH_LEVEL_CODE")
                    + " | disclosed="
                    + QuerySupport.optionalString(link, "IS_DISCLOSED"));
        }
    }

    private static List<Long> shortestRoute(
        Map<Long, Set<Long>> adjacency,
        long startId,
        long endId) {
        Queue<Long> queue = new ArrayDeque<>();
        Map<Long, Long> previous = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        queue.add(startId);
        visited.add(startId);

        while (!queue.isEmpty()) {
            long current = queue.remove();
            if (current == endId) {
                break;
            }
            List<Long> neighbors = new ArrayList<>(
                adjacency.getOrDefault(current, Set.of()));
            Collections.sort(neighbors);
            for (long neighbor : neighbors) {
                if (visited.add(neighbor)) {
                    previous.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }
        if (!visited.contains(endId)) {
            return List.of();
        }

        List<Long> route = new ArrayList<>();
        for (long current = endId; ; current = previous.get(current)) {
            route.add(current);
            if (current == startId) {
                break;
            }
        }
        Collections.reverse(route);
        return route;
    }

    private static JsonObject requireObject(JsonValue value, String path) {
        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException(path + " is not an object.");
        }
        return (JsonObject) value;
    }

    private static String summarizeSources(JsonObject entity) {
        JsonValue value = entity.get("RECORD_SUMMARY");
        if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
            return "(source summary not returned)";
        }
        JsonArray summary = (JsonArray) value;
        if (summary.isEmpty()) {
            return "(source summary present but empty)";
        }
        List<String> sources = new ArrayList<>();
        for (JsonValue summaryValue : summary) {
            JsonObject source = requireObject(summaryValue, "RECORD_SUMMARY[]");
            sources.add(
                QuerySupport.optionalString(source, "DATA_SOURCE")
                    + " x"
                    + QuerySupport.optionalString(source, "RECORD_COUNT"));
        }
        return String.join(", ", sources);
    }

    private static String entityLabel(
        Map<Long, String> labels,
        long entityId) {
        return labels.getOrDefault(entityId, "Entity " + entityId);
    }

    private static String entityLabel(
        long entityId,
        String name,
        String sources) {
        return "Entity " + entityId + " (" + name + "; " + sources + ")";
    }

    private static String edgeKey(long entityId1, long entityId2) {
        long minId = Math.min(entityId1, entityId2);
        long maxId = Math.max(entityId1, entityId2);
        return minId + ":" + maxId;
    }

    private static String joinRoute(List<Long> route) {
        List<String> ids = new ArrayList<>();
        for (long entityId : route) {
            ids.add(Long.toString(entityId));
        }
        return String.join(" -> ", ids);
    }
}
