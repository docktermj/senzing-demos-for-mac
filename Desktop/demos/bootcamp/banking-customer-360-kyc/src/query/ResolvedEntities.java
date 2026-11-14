// Shared entity view for the query programs (Query, Visualize and Discover module).
//
// Builds Senzing's resolved view of every loaded record: for each record in data/senzing-ready/*.jsonl
// it calls engine.getEntity(SzRecordKey, FLAGS) and keeps one response per ENTITY_ID. The caller knows
// record keys, not entity IDs, so the programs iterate records and fold on (DATA_SOURCE, RECORD_ID).
//
// Senzing calls and shapes, from the Senzing MCP server (2026-11-14):
//   * get_sdk_reference(topic='parameters', filter='get_entity_by_record_id', language='java'):
//       getEntity(recordKey: SzRecordKey, flags: Set<SzFlag>) -> String
//   * FLAGS: SZ_ENTITY_DEFAULT_FLAGS (a Set<SzFlag> composite in Java, so it is MERGED into the set,
//     not listed as an element) plus SZ_ENTITY_INCLUDE_RELATED_RECORD_DATA (a single SzFlag), so each
//     RELATED_ENTITIES[] entry carries RECORDS[] (DATA_SOURCE, RECORD_ID). The default composite brings
//     RESOLVED_ENTITY.RECORDS[] with MATCH_KEY / ERRULE_CODE, ENTITY_NAME, FEATURES, RECORD_SUMMARY[],
//     and every relation type with MATCH_LEVEL_CODE, MATCH_KEY, ENTITY_NAME, RECORD_SUMMARY[].
//     Senzing cautions that *_DEFAULT_FLAGS composites suit exploration; in production request exactly
//     the flags whose output you consume, because a composite's membership can change on upgrade
//     without any error.
//   * response paths: get_sdk_reference(topic='response_schemas', filter='get_entity_by_record_id').
// Lifetime: this object owns the SzEnvironment for as long as the engine is used (the environment
// must outlive every engine it creates); close() destroys it.
// The bank's business columns (balances, accounts, list types) are read from the same load-ready
// files, keyed by DATA_SOURCE/RECORD_ID; Senzing supplies the resolution.
// JSON via src/server/MiniJson.java. No SQL against the Senzing database.
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static com.senzing.sdk.SzFlag.*;

public final class ResolvedEntities implements AutoCloseable {
    public static final List<String> SOURCES = List.of("CORE_BANKING", "CARD_SERVICES", "CREDIT_BUREAU",
            "BUSINESS_REGISTRY", "WATCHLIST");
    public static final Set<SzFlag> FLAGS;
    static {
        Set<SzFlag> f = EnumSet.noneOf(SzFlag.class);
        f.addAll(SZ_ENTITY_DEFAULT_FLAGS);
        f.add(SZ_ENTITY_INCLUDE_RELATED_RECORD_DATA);
        FLAGS = Collections.unmodifiableSet(f);
    }

    private final SzEnvironment env;
    private final SzEngine engine;
    /** ENTITY_ID -> the getEntity response (RESOLVED_ENTITY, RELATED_ENTITIES). */
    public final Map<Long, Map<String, Object>> entities = new TreeMap<>();
    /** "DATA_SOURCE/RECORD_ID" -> ENTITY_ID. */
    public final Map<String, Long> recordEntity = new HashMap<>();
    /** "DATA_SOURCE/RECORD_ID" -> the load-ready source record (business columns). */
    public final Map<String, Map<String, Object>> sourceRecords = new LinkedHashMap<>();

    private ResolvedEntities(String instanceName) throws SzException {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) throw new IllegalArgumentException("Unable to get settings");
        env = SzCoreEnvironment.newBuilder().settings(settings).instanceName(instanceName).verboseLogging(false).build();
        try {
            engine = env.getEngine();
        } catch (SzException e) {
            env.destroy();
            throw e;
        }
    }

    public static ResolvedEntities load(String instanceName) throws IOException, SzException {
        ResolvedEntities r = new ResolvedEntities(instanceName);
        try {
            for (String ds : SOURCES) {
                Path file = Paths.get("data", "senzing-ready", ds.toLowerCase() + ".jsonl");
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    Map<String, Object> rec = MiniJson.obj(MiniJson.parse(line));
                    String rid = MiniJson.str(rec.get("RECORD_ID"));
                    String key = key(ds, rid);
                    if (r.sourceRecords.put(key, rec) != null) continue;   // fold on (DATA_SOURCE, RECORD_ID)
                    if (r.recordEntity.containsKey(key)) continue;          // already seen via its entity
                    Map<String, Object> resp = MiniJson.obj(MiniJson.parse(r.engine.getEntity(SzRecordKey.of(ds, rid), FLAGS)));
                    long eid = ((Number) MiniJson.obj(resp.get("RESOLVED_ENTITY")).get("ENTITY_ID")).longValue();
                    r.entities.put(eid, resp);
                    for (Map<String, Object> x : r.records(eid)) r.recordEntity.put(key(MiniJson.str(x.get("DATA_SOURCE")), MiniJson.str(x.get("RECORD_ID"))), eid);
                }
            }
        } catch (IOException | SzException | RuntimeException e) {
            r.close();
            throw e;
        }
        return r;
    }

    public static String key(String ds, String rid) { return ds + "/" + rid; }

    public Map<String, Object> resolved(long eid) { return MiniJson.obj(entities.get(eid).get("RESOLVED_ENTITY")); }

    public String name(long eid) { return MiniJson.str(resolved(eid).get("ENTITY_NAME")); }

    public List<Map<String, Object>> records(long eid) { return objs(resolved(eid).get("RECORDS")); }

    public List<Map<String, Object>> related(long eid) {
        Object rel = entities.get(eid).get("RELATED_ENTITIES");
        return rel == null ? List.of() : objs(rel);
    }

    /** RECORD_TYPE feature of the entity (PERSON / ORGANIZATION), or "UNTYPED". */
    public String recordType(long eid) {
        Map<String, Object> feats = MiniJson.obj(resolved(eid).get("FEATURES"));
        if (feats != null && feats.get("RECORD_TYPE") != null) {
            List<Map<String, Object>> t = objs(feats.get("RECORD_TYPE"));
            if (!t.isEmpty()) return MiniJson.str(t.get(0).get("FEAT_DESC"));
        }
        return "UNTYPED";
    }

    /** Data sources present in an entity. */
    public Set<String> sources(long eid) {
        Set<String> s = new TreeSet<>();
        for (Map<String, Object> x : records(eid)) s.add(MiniJson.str(x.get("DATA_SOURCE")));
        return s;
    }

    /** Source records of one data source inside an entity, with their business columns. */
    public List<Map<String, Object>> sourceRecordsOf(long eid, String ds) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> x : records(eid)) {
            if (ds.equals(MiniJson.str(x.get("DATA_SOURCE")))) {
                Map<String, Object> src = sourceRecords.get(key(ds, MiniJson.str(x.get("RECORD_ID"))));
                if (src != null) out.add(src);
            }
        }
        return out;
    }

    /** Parses a disclosed-relationship role from a MATCH_KEY such as "+REFERENCE(OWNS 60%:)".
     *  Returns {outbound, inbound}: the role THIS entity has toward the related one (left of the colon)
     *  and the role the related entity has toward this one (right of it); either may be empty. The
     *  notation is Senzing's documented MATCH_KEY direction notation for disclosed relationships. */
    public static String[] roles(String matchKey) {
        if (matchKey == null) return new String[]{"", ""};
        int open = matchKey.indexOf('('), close = matchKey.lastIndexOf(')');
        if (open < 0 || close < open) return new String[]{"", ""};
        String inner = matchKey.substring(open + 1, close);
        int colon = inner.indexOf(':');
        if (colon < 0) return new String[]{inner.trim(), ""};
        return new String[]{inner.substring(0, colon).trim(), inner.substring(colon + 1).trim()};
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objs(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o == null) return out;
        for (Object x : MiniJson.arr(o)) out.add((Map<String, Object>) x);
        return out;
    }

    static double num(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0.0; }

    @Override
    public void close() {
        // IMPORTANT: destroy the environment once every engine call is done
        env.destroy();
    }
}
