package validate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import server.Json;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_HOW_ENTITY_DEFAULT_FLAGS;

/**
 * Data processing, Phase D: evidence for validation, through generated SDK calls only (no direct SQL).
 *
 * Signatures from get_sdk_reference(topic='parameters', filter='entity', language='java'):
 *   getEntity(entityId: long, flags: Set<SzFlag>) -> String
 *   howEntity(entityId: long, flags: Set<SzFlag>) -> String
 * with SZ_ENTITY_DEFAULT_FLAGS (record data, matching info, relations) and SZ_HOW_ENTITY_DEFAULT_FLAGS.
 * Environment setup as in the loading snippets: settings from SENZING_ENGINE_CONFIGURATION_JSON, one
 * environment per process, destroyed at the end.
 *
 * Modes:
 *   dump-entity <id>            print one raw getEntity response (to read its shape before parsing)
 *   dump-how <id>               print one raw howEntity response
 *   entities <out.json> <id...> write, per entity: name, records (source, record ID, match key, rule,
 *                               original record JSON) and related entities, for the spot-check
 *   how <stats.json> <out.json> how-state audit: howEntity for EVERY multi-record entity listed in the
 *                               export statistics file; records HOW_RESULTS.FINAL_STATE.NEED_REEVALUATION
 *                               and the VIRTUAL_ENTITIES count, or "not measured" when FINAL_STATE is absent
 *
 * Usage: src/scripts/sz-java.sh run validate.EntityReview <mode> ...
 * The top-level class is package-private so it may live in this snake_case file.
 */
class EntityReview {

    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(EntityReview.class.getSimpleName())
            .verboseLogging(false)
            .build();
        try {
            SzEngine engine = env.getEngine();
            switch (args[0]) {
                case "dump-entity":
                    System.out.println(engine.getEntity(Long.parseLong(args[1]), SZ_ENTITY_DEFAULT_FLAGS));
                    break;
                case "dump-how":
                    System.out.println(engine.howEntity(Long.parseLong(args[1]), SZ_HOW_ENTITY_DEFAULT_FLAGS));
                    break;
                case "entities": {
                    List<Object> out = new ArrayList<>();
                    for (int i = 2; i < args.length; i++) out.add(view(engine, Long.parseLong(args[i])));
                    Files.writeString(Path.of(args[1]), Json.write(out), StandardCharsets.UTF_8);
                    System.out.println("WROTE " + out.size() + " entities to " + args[1]);
                    break;
                }
                case "how":
                    how(engine, Path.of(args[1]), Path.of(args[2]));
                    break;
                default:
                    throw new IllegalArgumentException("unknown mode " + args[0]);
            }
        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
    }

    static Map<String, Object> view(SzEngine engine, long entityId) throws SzException {
        Map<String, Object> e = Json.parseObject(engine.getEntity(entityId, SZ_ENTITY_DEFAULT_FLAGS));
        Map<String, Object> resolved = Json.obj(e.get("RESOLVED_ENTITY"));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("entity_id", entityId);
        v.put("name", resolved.get("ENTITY_NAME"));
        List<Object> recs = new ArrayList<>();
        for (Object o : Json.arr(resolved.get("RECORDS"))) {
            Map<String, Object> r = Json.obj(o);
            Map<String, Object> rv = new LinkedHashMap<>();
            rv.put("source", r.get("DATA_SOURCE"));
            rv.put("record_id", r.get("RECORD_ID"));
            rv.put("match_key", r.get("MATCH_KEY"));
            rv.put("rule", r.get("ERRULE_CODE"));
            rv.put("json_data", r.get("JSON_DATA"));
            recs.add(rv);
        }
        v.put("records", recs);
        List<Object> rel = new ArrayList<>();
        for (Object o : Json.arr(e.get("RELATED_ENTITIES"))) {
            Map<String, Object> r = Json.obj(o);
            Map<String, Object> rv = new LinkedHashMap<>();
            rv.put("entity_id", r.get("ENTITY_ID"));
            rv.put("name", r.get("ENTITY_NAME"));
            rv.put("level", r.get("MATCH_LEVEL_CODE"));
            rv.put("match_key", r.get("MATCH_KEY"));
            rel.add(rv);
        }
        v.put("related", rel);
        return v;
    }

    static void how(SzEngine engine, Path statsFile, Path outFile) throws Exception {
        Map<String, Object> stats = Json.parseObject(Files.readString(statsFile));
        List<Object> multi = Json.arr(stats.get("multi_record_entities"));
        List<Object> rows = new ArrayList<>();
        int checked = 0, flagged = 0, notMeasured = 0;
        for (Object o : multi) {
            Map<String, Object> m = Json.obj(o);
            long id = ((Number) m.get("entity_id")).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entity_id", id);
            row.put("name", m.get("name"));
            row.put("records", m.get("records"));
            try {
                Map<String, Object> how = Json.parseObject(engine.howEntity(id, SZ_HOW_ENTITY_DEFAULT_FLAGS));
                Map<String, Object> results = Json.obj(how.get("HOW_RESULTS"));
                Object fs = results.get("FINAL_STATE");
                if (!(fs instanceof Map)) {
                    row.put("outcome", "not measured (no FINAL_STATE)");
                    notMeasured++;
                } else {
                    Map<String, Object> finalState = Json.obj(fs);
                    Object need = finalState.get("NEED_REEVALUATION");
                    int virtual = Json.arr(finalState.get("VIRTUAL_ENTITIES")).size();
                    long needVal = (need instanceof Number) ? ((Number) need).longValue() : 0L;
                    row.put("need_reevaluation", need);
                    row.put("virtual_entities", virtual);
                    List<String> signs = new ArrayList<>();
                    if (needVal != 0) signs.add("NEED_REEVALUATION=" + needVal);
                    if (virtual > 1) signs.add("VIRTUAL_ENTITIES=" + virtual);
                    row.put("signs", signs);
                    row.put("outcome", signs.isEmpty() ? "settled" : "unsettled");
                    checked++;
                    if (!signs.isEmpty()) flagged++;
                }
            } catch (SzException e) {
                row.put("outcome", "not measured (" + e.getMessage() + ")");
                notMeasured++;
            }
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("M", multi.size());
        out.put("checked", checked);
        out.put("unsettled", flagged);
        out.put("not_measured", notMeasured);
        out.put("entities", rows);
        Files.writeString(outFile, Json.write(out), StandardCharsets.UTF_8);
        System.out.println("HOW_AUDIT checked " + checked + " of " + multi.size() + ", unsettled " + flagged
            + ", not measured " + notMeasured);
    }
}
