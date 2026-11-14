package validate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import server.Json;

import static com.senzing.sdk.SzFlag.SZ_EXPORT_DEFAULT_FLAGS;

/**
 * Entity-resolution statistics from ONE pass over the Senzing export stream.
 *
 * Method from reporting_guide(topic='evaluation', language='java'): "Export Iteration for ER Evaluation" —
 * engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS), engine.fetchNext(handle) until null, and
 * engine.closeExportReport(handle) in a finally block (closeExportReport, not closeExport, in Java). Each
 * row carries RESOLVED_ENTITY (ENTITY_ID, ENTITY_NAME, RECORDS[]) and RELATED_ENTITIES[] (ENTITY_ID,
 * MATCH_LEVEL_CODE, MATCH_KEY, ERRULE_CODE). Relationships appear in both entities' rows, so they are
 * de-duplicated on the (min, max) entity-ID pair. Export iteration is O(all entities): right for a
 * bootcamp-sized repository, not for millions of entities (use INFO messages and a data mart there).
 * No direct SQL is used.
 *
 * Computes: records and entities per source; entity-size buckets with sample IDs; cross-source entities and
 * source pairs; per-record match keys (ERRULE_CODE + MATCH_KEY); relationships by MATCH_LEVEL_CODE and match
 * key; the match-key audit's three suppressor buckets, each against its own denominator (single-source
 * per-record, cross-source per-record, relationship); and the list of multi-record entities for the
 * how-state audit. The first row's top-level keys are reported so a reader that cannot see
 * RELATED_ENTITIES is caught rather than read as "no relationships".
 *
 * Match-key parsing (Senzing: '+' contributed, '-' detracted): remove each parenthesized role first; a
 * backslash makes the next character part of the name; split on unescaped '+' and '-'; report names with
 * the escape removed.
 *
 * Usage: src/scripts/sz-java.sh run validate.ExportStats [output.json]
 *   (default output data/temp/er_stats.json). Prints a short summary.
 */
public final class ExportStats {

    private ExportStats() {}

    /** Records currently in the repository, measured through the export (sum of RECORDS[] lengths). */
    public static int recordCount(SzEngine engine) throws SzException {
        int records = 0;
        long handle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
        try {
            String row;
            while ((row = engine.fetchNext(handle)) != null) {
                Map<String, Object> resolved = Json.obj(Json.parseObject(row).get("RESOLVED_ENTITY"));
                records += Json.arr(resolved.get("RECORDS")).size();
            }
        } finally {
            engine.closeExportReport(handle);
        }
        return records;
    }

    /** Signed features of a match key, in order, e.g. "+NAME+DOB-SSN" -> [+NAME, +DOB, -SSN]. */
    public static List<String> signedFeatures(String matchKey) {
        List<String> out = new ArrayList<>();
        if (matchKey == null) return out;
        String k = matchKey.replaceAll("\\([^)]*\\)", "");
        StringBuilder name = new StringBuilder();
        char sign = 0;
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c == '\\' && i + 1 < k.length()) { name.append(k.charAt(++i)); continue; }
            if (c == '+' || c == '-') {
                if (sign != 0 && name.length() > 0) out.add(sign + name.toString());
                sign = c;
                name.setLength(0);
            } else {
                name.append(c);
            }
        }
        if (sign != 0 && name.length() > 0) out.add(sign + name.toString());
        return out;
    }

    private static String bucket(int n) {
        if (n == 1) return "1";
        if (n == 2) return "2";
        if (n <= 5) return "3-5";
        if (n <= 10) return "6-10";
        if (n <= 50) return "11-50";
        return "51+";
    }

    private static <K> void inc(Map<K, Integer> m, K k) { m.merge(k, 1, Integer::sum); }

    /** Compute every statistic in one export pass. */
    public static Map<String, Object> compute(SzEngine engine) throws SzException {
        int entities = 0, records = 0, crossSource = 0;
        Map<String, Integer> recordsBySource = new TreeMap<>(), entitiesBySource = new TreeMap<>();
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (String b : List.of("1", "2", "3-5", "6-10", "11-50", "51+")) sizes.put(b, 0);
        Map<String, List<Long>> sizeSamples = new LinkedHashMap<>();
        Map<String, Integer> sourcePairs = new TreeMap<>(), recordMatchKeys = new TreeMap<>();
        Map<String, Integer> relLevels = new TreeMap<>(), relMatchKeys = new TreeMap<>();
        Map<String, Integer> supSingle = new TreeMap<>(), supCross = new TreeMap<>(), supRel = new TreeMap<>();
        int perRecordSingle = 0, perRecordCross = 0;
        Set<String> seenPairs = new HashSet<>();
        List<Map<String, Object>> multiRecord = new ArrayList<>();
        List<Map<String, Object>> crossEntities = new ArrayList<>();
        List<Map<String, Object>> relPairs = new ArrayList<>();
        List<String> firstRowKeys = null;

        long handle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
        try {
            String row;
            while ((row = engine.fetchNext(handle)) != null) {
                Map<String, Object> obj = Json.parseObject(row);
                if (firstRowKeys == null) firstRowKeys = new ArrayList<>(new TreeSet<>(obj.keySet()));
                Map<String, Object> resolved = Json.obj(obj.get("RESOLVED_ENTITY"));
                long entityId = ((Number) resolved.get("ENTITY_ID")).longValue();
                String name = Json.str(resolved.get("ENTITY_NAME"));
                List<Object> recs = Json.arr(resolved.get("RECORDS"));
                entities++;
                records += recs.size();
                Set<String> sources = new TreeSet<>();
                for (Object r : recs) {
                    String ds = Json.str(Json.obj(r).get("DATA_SOURCE"));
                    sources.add(ds);
                    inc(recordsBySource, ds);
                }
                for (String ds : sources) inc(entitiesBySource, ds);
                String b = bucket(recs.size());
                inc(sizes, b);
                List<Long> sample = sizeSamples.computeIfAbsent(b, x -> new ArrayList<>());
                if (sample.size() < 5) sample.add(entityId);

                boolean cross = sources.size() > 1;
                if (cross) {
                    crossSource++;
                    List<String> ss = new ArrayList<>(sources);
                    for (int i = 0; i < ss.size(); i++)
                        for (int j = i + 1; j < ss.size(); j++) inc(sourcePairs, ss.get(i) + " + " + ss.get(j));
                    Map<String, Object> ce = new LinkedHashMap<>();
                    ce.put("entity_id", entityId);
                    ce.put("name", name);
                    ce.put("sources", new ArrayList<>(sources));
                    ce.put("records", recs.size());
                    crossEntities.add(ce);
                }
                if (recs.size() > 1) {
                    Map<String, Object> me = new LinkedHashMap<>();
                    me.put("entity_id", entityId);
                    me.put("name", name);
                    me.put("records", recs.size());
                    me.put("sources", new ArrayList<>(sources));
                    multiRecord.add(me);
                }
                for (Object r : recs) {
                    Map<String, Object> rec = Json.obj(r);
                    String mk = Json.str(rec.get("MATCH_KEY"));
                    if (mk == null || mk.isBlank()) continue; // the entity's first record carries no key
                    inc(recordMatchKeys, Json.str(rec.get("ERRULE_CODE")) + " " + mk);
                    if (cross) perRecordCross++; else perRecordSingle++;
                    for (String f : signedFeatures(mk)) {
                        if (f.startsWith("-")) inc(cross ? supCross : supSingle, f.substring(1));
                    }
                }
                for (Object o : Json.arr(obj.get("RELATED_ENTITIES"))) {
                    Map<String, Object> rel = Json.obj(o);
                    long other = ((Number) rel.get("ENTITY_ID")).longValue();
                    String key = Math.min(entityId, other) + "-" + Math.max(entityId, other);
                    if (!seenPairs.add(key)) continue;
                    inc(relLevels, Json.str(rel.get("MATCH_LEVEL_CODE")));
                    String mk = Json.str(rel.get("MATCH_KEY"));
                    inc(relMatchKeys, Json.str(rel.get("MATCH_LEVEL_CODE")) + " " + mk);
                    Map<String, Object> pair = new LinkedHashMap<>();
                    pair.put("entity_a", Math.min(entityId, other));
                    pair.put("entity_b", Math.max(entityId, other));
                    pair.put("name_a", entityId < other ? name : rel.get("ENTITY_NAME"));
                    pair.put("name_b", entityId < other ? rel.get("ENTITY_NAME") : name);
                    pair.put("level", rel.get("MATCH_LEVEL_CODE"));
                    pair.put("match_key", mk);
                    relPairs.add(pair);
                    for (String f : signedFeatures(mk)) {
                        if (f.startsWith("-")) inc(supRel, f.substring(1));
                    }
                }
            }
        } finally {
            engine.closeExportReport(handle);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("first_row_top_level_keys", firstRowKeys);
        out.put("carries_related_entities", firstRowKeys != null && firstRowKeys.contains("RELATED_ENTITIES"));
        out.put("entities", entities);
        out.put("records", records);
        out.put("records_by_source", recordsBySource);
        out.put("entities_by_source", entitiesBySource);
        Map<String, Object> compression = new TreeMap<>();
        for (String ds : recordsBySource.keySet()) {
            double c = 1.0 - (double) entitiesBySource.get(ds) / recordsBySource.get(ds);
            compression.put(ds, Math.round(c * 1000) / 10.0);
        }
        out.put("compression_pct_by_source", compression);
        out.put("entity_sizes", sizes);
        out.put("entity_size_samples", sizeSamples);
        out.put("cross_source_entities", crossSource);
        out.put("cross_source_pairs", sourcePairs);
        out.put("record_match_keys", recordMatchKeys);
        out.put("relationships", seenPairs.size());
        out.put("relationship_levels", relLevels);
        out.put("relationship_match_keys", relMatchKeys);
        out.put("relationship_pairs", relPairs);
        Map<String, Object> sup = new LinkedHashMap<>();
        sup.put("single_source_per_record", Map.of("denominator", perRecordSingle, "suppressors", supSingle));
        sup.put("cross_source_per_record", Map.of("denominator", perRecordCross, "suppressors", supCross));
        sup.put("relationship", Map.of("denominator", seenPairs.size(), "suppressors", supRel));
        out.put("suppressor_buckets", sup);
        out.put("multi_record_entities", multiRecord);
        out.put("cross_source_entity_list", crossEntities);
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "data/temp/er_stats.json");
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(ExportStats.class.getSimpleName())
            .verboseLogging(false)
            .build();
        Map<String, Object> stats;
        try {
            stats = compute(env.getEngine());
        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, Json.write(stats), StandardCharsets.UTF_8);
        System.out.println("FIRST_ROW_KEYS=" + stats.get("first_row_top_level_keys"));
        System.out.println("ENTITIES=" + stats.get("entities"));
        System.out.println("RECORDS=" + stats.get("records"));
        System.out.println("RECORDS_BY_SOURCE=" + stats.get("records_by_source"));
        System.out.println("CROSS_SOURCE_ENTITIES=" + stats.get("cross_source_entities"));
        System.out.println("RELATIONSHIPS=" + stats.get("relationships"));
        System.out.println("OUTPUT=" + output);
    }
}
