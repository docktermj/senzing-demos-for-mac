// System verification, Steps 7 and 8: results validation and database operations, through SDK calls
// only (no SQL). SDK calls and flag names come from get_sdk_reference (getEntity, searchByAttributes,
// whyRecords; MCP server 1.46.0). Structure follows the senzing/code-snippets-v4 search snippet.
// JSON is read with a small dependency-free extractor (javax.json is not shipped with the runtime).
//
// Prints one line per check: CHECK <name> <PASS|FAIL> <detail>, then the raw entity response for
// the merged entity (defensive parsing: the guide reads one raw response before trusting a regex).
import java.util.*;
import java.util.regex.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class VerifyResults {
    static final String DS = "VERIFY";
    static final String[] MERGE = {"V1001", "V1002", "V1003"};
    static final String DISTRACTOR = "V2001";
    static final int EXPECTED_ENTITIES = 2;
    static final int TOTAL_RECORDS = 4;

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(VerifyResults.class.getSimpleName())
            .verboseLogging(false)
            .build();
        try {
            SzEngine engine = env.getEngine();
            Set<SzFlag> flags = EnumSet.of(SZ_ENTITY_INCLUDE_ENTITY_NAME,
                                           SZ_ENTITY_INCLUDE_RECORD_DATA,
                                           SZ_ENTITY_INCLUDE_RECORD_MATCHING_INFO);

            // ---- resolve every record to its entity
            Map<String, Long> entityOf = new LinkedHashMap<>();
            Map<String, String> rawOf = new LinkedHashMap<>();
            for (String id : concat(MERGE, DISTRACTOR)) {
                String raw = engine.getEntity(SzRecordKey.of(DS, id), flags);
                rawOf.put(id, raw);
                entityOf.put(id, resolvedEntityId(raw));
            }
            System.out.println("MAP " + entityOf);

            // ---- Step 7 a) entity count
            Set<Long> distinct = new TreeSet<>(entityOf.values());
            check("entity_count", distinct.size() == EXPECTED_ENTITIES,
                  "expected " + EXPECTED_ENTITIES + ", found " + distinct.size());
            // ---- b) merge cluster is one entity
            Set<Long> mergeIds = new TreeSet<>();
            for (String id : MERGE) mergeIds.add(entityOf.get(id));
            boolean merged = mergeIds.size() == 1;
            check("merge_cluster_one_entity", merged, "entity ids " + mergeIds);
            // ---- c) cross-record resolution (fewer entities than records)
            check("cross_record_resolution", distinct.size() < TOTAL_RECORDS,
                  distinct.size() + " entities from " + TOTAL_RECORDS + " records");
            // ---- d) distractor stays its own singleton
            long dId = entityOf.get(DISTRACTOR);
            check("distractor_singleton", !mergeIds.contains(dId) && recordCount(rawOf.get(DISTRACTOR)) == 1,
                  "entity " + dId + " holds " + recordCount(rawOf.get(DISTRACTOR)) + " record(s)");

            // ---- on a merge mismatch, ask the engine why (never judge the install from the count alone)
            if (!merged) {
                System.out.println("WHY records V1001 vs V1002 (engine's own explanation):");
                System.out.println(engine.whyRecords(SzRecordKey.of(DS, "V1001"),
                                                     SzRecordKey.of(DS, "V1002"),
                                                     SZ_WHY_RECORDS_DEFAULT_FLAGS));
            }

            // ---- Step 8 1) write count: records the engine returns for the merge entity + distractor
            long mergeEntity = entityOf.get(MERGE[0]);
            int written = recordCount(rawOf.get(MERGE[0])) + recordCount(rawOf.get(DISTRACTOR));
            check("db_write_count", written == TOTAL_RECORDS, written + " of " + TOTAL_RECORDS + " records present");
            // ---- 2) read by entity ID
            String byId = engine.getEntity(mergeEntity, flags);
            boolean readOk = byId.contains("\"ENTITY_ID\"") && byId.contains("V1001") && byId.contains("Brightwater");
            check("db_read_by_entity_id", readOk,
                  "entity " + mergeEntity + " returned " + recordCount(byId) + " record(s), name present=" + byId.contains("Brightwater"));
            // ---- 3) search by attributes
            String criteria = "{\"NAME_FULL\": \"Alexandra Brightwater\", \"ADDR_FULL\": \"482 Willow Creek Dr, Springfield, IL 62704\"}";
            String found = engine.searchByAttributes(criteria, SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
            check("db_search_by_attributes", allEntityIds(found).contains(mergeEntity),
                  "search returned entities " + allEntityIds(found));

            System.out.println("RAW_ENTITY " + byId);

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }

    static void check(String name, boolean ok, String detail) {
        System.out.println("CHECK " + name + " " + (ok ? "PASS" : "FAIL") + " " + detail);
    }

    static String[] concat(String[] a, String b) {
        String[] r = Arrays.copyOf(a, a.length + 1);
        r[a.length] = b;
        return r;
    }

    /** ENTITY_ID of RESOLVED_ENTITY: the first ENTITY_ID in the document. */
    static long resolvedEntityId(String json) {
        Matcher m = Pattern.compile("\"ENTITY_ID\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) throw new IllegalStateException("No ENTITY_ID in response: " + json);
        return Long.parseLong(m.group(1));
    }

    /** Every top-level entity id in a search result (RESOLVED_ENTITY blocks). */
    static Set<Long> allEntityIds(String json) {
        Set<Long> ids = new TreeSet<>();
        Matcher m = Pattern.compile("\"RESOLVED_ENTITY\"\\s*:\\s*\\{\\s*\"ENTITY_ID\"\\s*:\\s*(\\d+)").matcher(json);
        while (m.find()) ids.add(Long.parseLong(m.group(1)));
        return ids;
    }

    /** Records inside RESOLVED_ENTITY (stops at RELATED_ENTITIES). */
    static int recordCount(String json) {
        int end = json.indexOf("\"RELATED_ENTITIES\"");
        String part = end > 0 ? json.substring(0, end) : json;
        Matcher m = Pattern.compile("\"RECORD_ID\"\\s*:\\s*\"[^\"]+\"").matcher(part);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}
