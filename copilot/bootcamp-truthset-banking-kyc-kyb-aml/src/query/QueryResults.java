package query;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;

import static com.senzing.sdk.SzFlag.SZ_EXPORT_DEFAULT_FLAGS;

public final class QueryResults {
    private static final Path OUTPUT =
        Path.of("data/query/master_list.jsonl");
    private static final Path SUMMARY =
        Path.of("data/query/master_list_summary.txt");

    private QueryResults() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: query.QueryResults");
        }
        Files.createDirectories(OUTPUT.getParent());

        SzEnvironment environment = QuerySupport.openEnvironment(
            QueryResults.class.getSimpleName());
        long entityCount = 0;
        long recordCount = 0;
        Map<String, Long> recordsBySource = new TreeMap<>();
        try (BufferedWriter writer = Files.newBufferedWriter(
            OUTPUT, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
            SzEngine engine = environment.getEngine();
            long handle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
            try {
                String row;
                while ((row = engine.fetchNext(handle)) != null) {
                    JsonObject exported = QuerySupport.parseObject(row);
                    JsonObject resolved = QuerySupport.requireObject(
                        exported, "RESOLVED_ENTITY");
                    QuerySupport.requiredLong(resolved, "ENTITY_ID");
                    JsonArray records = QuerySupport.requireArray(
                        resolved, "RECORDS");
                    for (JsonValue value : records) {
                        if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException(
                                "Exported entity contains a non-object record.");
                        }
                        JsonObject record = (JsonObject) value;
                        String source = QuerySupport.requiredString(
                            record, "DATA_SOURCE");
                        QuerySupport.requiredString(record, "RECORD_ID");
                        recordsBySource.merge(source, 1L, Long::sum);
                        recordCount++;
                    }
                    writer.write(row);
                    writer.newLine();
                    entityCount++;
                }
            } finally {
                engine.closeExportReport(handle);
            }
        } finally {
            environment.destroy();
        }

        String summary = "Resolved entities exported: " + entityCount + "\n"
            + "Resolved records exported: " + recordCount + "\n"
            + "Records by source: " + recordsBySource + "\n"
            + "Master list: " + OUTPUT + "\n"
            + "Answer-key comparison: not run; actual_truthset_key.csv is absent "
            + "from this checkout.\n";
        Files.writeString(
            SUMMARY, summary, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
        System.out.print(summary);
    }
}
