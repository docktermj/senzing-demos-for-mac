// Exports every resolved entity, and the construction history of every multi-record entity,
// for the Data processing module's validation (Phase D).
//
// Senzing calls, as served by the Senzing MCP server (2026-11-14):
//   * export: reporting_guide(topic='evaluation', language='java') pattern "export_entities":
//       engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS); engine.fetchNext(handle) until null;
//       engine.closeExportReport(handle) in finally (closeExportReport, not closeExport).
//   * how: get_sdk_reference(topic='parameters', filter='how_entity', language='java'):
//       howEntity(entityId: long) -> String  (the overload with the method's default flags)
//   * environment setup as in Senzing's code-snippets-v4 Java snippets.
// No direct SQL against database/G2C.db. JSON via src/server/MiniJson.java.
//
// Writes data/validation/export.jsonl (one exported row per line) and data/validation/how.jsonl
// ({"ENTITY_ID": id, "HOW": <howEntity response>} per multi-record entity), and prints the first
// row's top-level keys so the reader can confirm RELATED_ENTITIES is present before parsing.
//
// Run: src/scripts/run-java.sh data/temp/load_classes ExportEntities
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static com.senzing.sdk.SzFlag.SZ_EXPORT_DEFAULT_FLAGS;

class ExportEntities {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) throw new IllegalArgumentException("Unable to get settings");
        SzEnvironment env = SzCoreEnvironment.newBuilder()
                .settings(settings).instanceName(ExportEntities.class.getSimpleName()).verboseLogging(false).build();
        Path dir = Paths.get("data", "validation");
        Files.createDirectories(dir);
        List<Long> multiRecord = new ArrayList<>();
        int rows = 0, howOk = 0, howFailed = 0;
        try {
            SzEngine engine = env.getEngine();
            long handle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
            try (BufferedWriter w = Files.newBufferedWriter(dir.resolve("export.jsonl"), StandardCharsets.UTF_8)) {
                String row;
                while ((row = engine.fetchNext(handle)) != null) {
                    row = row.trim();
                    if (row.isEmpty()) continue;
                    Map<String, Object> obj = MiniJson.obj(MiniJson.parse(row));
                    if (rows == 0) System.out.println("first row top-level keys: " + new TreeSet<>(obj.keySet()));
                    Map<String, Object> re = MiniJson.obj(obj.get("RESOLVED_ENTITY"));
                    if (re != null && re.get("RECORDS") != null && MiniJson.arr(re.get("RECORDS")).size() >= 2)
                        multiRecord.add(((Number) re.get("ENTITY_ID")).longValue());
                    w.write(row);
                    w.newLine();
                    rows++;
                }
            } finally {
                engine.closeExportReport(handle);
            }
            try (BufferedWriter w = Files.newBufferedWriter(dir.resolve("how.jsonl"), StandardCharsets.UTF_8)) {
                for (long id : multiRecord) {
                    try {
                        String how = engine.howEntity(id);
                        w.write("{\"ENTITY_ID\":" + id + ",\"HOW\":" + how.trim() + "}");
                        w.newLine();
                        howOk++;
                    } catch (SzException e) {
                        System.err.println("howEntity(" + id + ") failed: " + e.getMessage());
                        howFailed++;
                    }
                }
            }
        } finally {
            env.destroy();
        }
        System.out.println("exported entities        : " + rows);
        System.out.println("multi-record entities (M): " + multiRecord.size());
        System.out.println("howEntity completed      : " + howOk + " (failed " + howFailed + ")");
    }
}
