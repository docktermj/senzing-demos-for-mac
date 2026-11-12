package query;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.json.JsonObject;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzRecordKey;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_BRIEF_DEFAULT_FLAGS;

public final class FindDuplicates {
    private static final List<Path> DEFAULT_INPUTS = List.of(
        Path.of("data/senzing-ready/BANK_CUSTOMERS.jsonl"),
        Path.of("data/senzing-ready/BUSINESS_REGISTRY.jsonl"),
        Path.of("data/senzing-ready/AML_WATCHLIST.jsonl"));

    private FindDuplicates() {}

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
            FindDuplicates.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            Map<Long, List<QuerySupport.RecordKey>> recordsByEntity =
                new TreeMap<>();
            for (QuerySupport.RecordKey key : records.keySet()) {
                JsonObject resolved = QuerySupport.entityFromResponse(
                    engine.getEntity(
                        SzRecordKey.of(key.dataSource(), key.recordId()),
                        SZ_ENTITY_BRIEF_DEFAULT_FLAGS));
                long entityId = QuerySupport.requiredLong(resolved, "ENTITY_ID");
                recordsByEntity.computeIfAbsent(
                    entityId, ignored -> new ArrayList<>()).add(key);
            }

            long multiRecordEntities = recordsByEntity.values().stream()
                .filter(entityRecords -> entityRecords.size() > 1)
                .count();
            long crossSourceEntities = recordsByEntity.values().stream()
                .filter(entityRecords -> entityRecords.size() > 1)
                .filter(FindDuplicates::hasMultipleSources)
                .count();

            System.out.println("Unique loaded record keys: " + records.size());
            System.out.println("Resolved entities touched: " + recordsByEntity.size());
            System.out.println("Multi-record entities: " + multiRecordEntities);
            System.out.println(
                "Cross-source multi-record entities: " + crossSourceEntities);
            for (Map.Entry<Long, List<QuerySupport.RecordKey>> entry
                : recordsByEntity.entrySet()) {
                List<QuerySupport.RecordKey> entityRecords = entry.getValue();
                if (entityRecords.size() < 2) {
                    continue;
                }
                entityRecords.sort(Comparator.comparing(
                    key -> key.dataSource() + ":" + key.recordId()));
                System.out.println(
                    "Entity " + entry.getKey() + " records=" + entityRecords.size()
                        + " sources=" + sources(entityRecords));
                for (QuerySupport.RecordKey key : entityRecords) {
                    System.out.println(
                        "  " + key.dataSource() + ":" + key.recordId());
                }
            }
        } finally {
            environment.destroy();
        }
    }

    private static boolean hasMultipleSources(
        List<QuerySupport.RecordKey> records
    ) {
        return sources(records).size() > 1;
    }

    private static Set<String> sources(List<QuerySupport.RecordKey> records) {
        Set<String> sources = new LinkedHashSet<>();
        for (QuerySupport.RecordKey record : records) {
            sources.add(record.dataSource());
        }
        return sources;
    }
}
