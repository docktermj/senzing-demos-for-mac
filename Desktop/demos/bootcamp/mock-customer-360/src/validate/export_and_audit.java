// export_and_audit.java -- Data processing, Phase D: export every resolved entity, then run the
// how-state audit on every entity with 2 or more records.
//
// SDK calls come from the Senzing MCP server, unchanged:
//  - export: reporting_guide(topic='evaluation', language='java') export_entities pattern --
//    exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS), fetchNext(handle) until null,
//    closeExportReport(handle) in a finally block (closeExportReport, not closeExport).
//  - how: get_sdk_reference(topic='parameters'/'response_schemas', filter='how_entity', language='java')
//    -- howEntity(entityId) with the method's default flags; HOW_RESULTS.FINAL_STATE carries
//    NEED_REEVALUATION and VIRTUAL_ENTITIES[].
// No SQL is run against the repository. Rows are parsed with the shared Json reader.
//
// Writes data/results/entity_export.jsonl (one exported row per line) and data/results/how_audit.json.
// Usage: src/scripts/run-java.sh ExportAndAudit
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class ExportAndAudit {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(ExportAndAudit.class.getSimpleName())
            .verboseLogging(false)
            .build();
        Path exportFile = Path.of("data/results/entity_export.jsonl");
        Files.createDirectories(exportFile.getParent());
        List<Long> multi = new ArrayList<>();
        long entities = 0, recordsSum = 0;
        try {
            SzEngine engine = env.getEngine();
            // ---- export (single pass) ----
            long handle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
            try (BufferedWriter w = Files.newBufferedWriter(exportFile, StandardCharsets.UTF_8)) {
                String row;
                while ((row = engine.fetchNext(handle)) != null) {
                    row = row.trim();
                    if (row.isEmpty()) { continue; }
                    Map<String, Object> obj = Json.obj(Json.parse(row));
                    if (entities == 0) {   // dump ONE row's shape before trusting the parser
                        System.out.println("first row top-level keys: " + new TreeSet<>(obj.keySet()));
                        System.out.println("carries RELATED_ENTITIES: " + obj.containsKey("RELATED_ENTITIES"));
                    }
                    Map<String, Object> re = Json.obj(obj.get("RESOLVED_ENTITY"));
                    int n = Json.arr(re.get("RECORDS")).size();
                    recordsSum += n;
                    if (n >= 2) { multi.add(Json.lng(re.get("ENTITY_ID"))); }
                    entities++;
                    w.write(row);
                    w.newLine();
                }
            } finally {
                engine.closeExportReport(handle);
            }
            System.out.println("EXPORT entities=" + entities + " records_in_entities=" + recordsSum
                + " multi_record_entities=" + multi.size() + " -> " + exportFile);

            // ---- how-state audit: every multi-record entity, no sampling ----
            long checked = 0, notChecked = 0;
            List<Object> flagged = new ArrayList<>();
            boolean dumped = false;
            for (long entityId : multi) {
                try {
                    String how = engine.howEntity(entityId);
                    Map<String, Object> hr = Json.obj(Json.obj(Json.parse(how)).get("HOW_RESULTS"));
                    if (!dumped) {
                        System.out.println("first how response HOW_RESULTS keys: " + new TreeSet<>(hr.keySet()));
                        dumped = true;
                    }
                    Object fsRaw = hr.get("FINAL_STATE");
                    if (fsRaw == null) { notChecked++; continue; }
                    Map<String, Object> fs = Json.obj(fsRaw);
                    long need = fs.get("NEED_REEVALUATION") == null ? 0 : Json.lng(fs.get("NEED_REEVALUATION"));
                    int virtual = Json.arr(fs.get("VIRTUAL_ENTITIES")).size();
                    checked++;
                    if (need != 0 || virtual > 1) {
                        List<String> signs = new ArrayList<>();
                        if (need != 0) { signs.add("NEED_REEVALUATION=" + need); }
                        if (virtual > 1) { signs.add("VIRTUAL_ENTITIES=" + virtual); }
                        Map<String, Object> f = new LinkedHashMap<>();
                        f.put("entity_id", entityId);
                        f.put("signs", signs);
                        flagged.add(f);
                    }
                } catch (SzException e) {
                    notChecked++;
                    System.err.println("how failed for entity " + entityId + ": " + e.getMessage());
                }
            }
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("exported_entities", entities);
            audit.put("records_in_entities", recordsSum);
            audit.put("multi_record_entities_M", (long) multi.size());
            audit.put("checked_N", checked);
            audit.put("not_checked", notChecked);
            audit.put("unsettled", flagged);
            Files.writeString(Path.of("data/results/how_audit.json"), Json.write(audit), StandardCharsets.UTF_8);
            System.out.println("HOW-AUDIT checked " + checked + " of " + multi.size() + ", unsettled=" + flagged.size()
                + ", not_checked=" + notChecked);
        } finally {
            env.destroy();
        }
    }
}
