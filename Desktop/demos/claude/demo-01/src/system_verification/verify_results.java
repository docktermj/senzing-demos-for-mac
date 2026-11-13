// verify_results.java -- System verification, Steps 7 and 8 (results validation and database
// operations). Environment setup and error handling follow the senzing/code-snippets-v4 pattern
// used by the other files here. SDK calls and signatures come from get_sdk_reference
// (topic='parameters', language='java') on the Senzing MCP server:
//   getEntity(SzRecordKey, Set<SzFlag>) -> String
//   getEntity(long, Set<SzFlag>) -> String
//   searchByAttributes(String) -> String
//   whyRecords(SzRecordKey, SzRecordKey) -> String
// Response field names (RESOLVED_ENTITY.ENTITY_ID, RESOLVED_ENTITY.ENTITY_NAME,
// RESOLVED_ENTITY.RECORDS[].DATA_SOURCE/RECORD_ID, RESOLVED_ENTITY.RECORD_SUMMARY[].RECORD_COUNT)
// come from get_sdk_reference(topic='response_schemas', filter='get_entity_by_record_id').
// Responses are read with small regular expressions because no JSON library ships with the SDK.
import java.util.regex.*;
import java.util.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class VerifyResults {
    static final String DS = "VERIFY";
    static final String[] MERGE_IDS = {"V1001", "V1002", "V1003"};
    static final String DISTRACTOR_ID = "V2001";
    static final int EXPECTED_ENTITIES = 2;
    static final int EXPECTED_MERGE_COUNT = 3;

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

            // ---- Step 7: results validation -------------------------------------------
            Map<String, Long> entityOf = new LinkedHashMap<>();
            Map<Long, Integer> recordCount = new LinkedHashMap<>();
            Map<String, String> raw = new HashMap<>();
            List<String> all = new ArrayList<>(Arrays.asList(MERGE_IDS));
            all.add(DISTRACTOR_ID);
            for (String id : all) {
                String entity = engine.getEntity(SzRecordKey.of(DS, id), SZ_ENTITY_DEFAULT_FLAGS);
                long entityId = Long.parseLong(first(entity, "\"ENTITY_ID\"\\s*:\\s*(\\d+)"));
                entityOf.put(id, entityId);
                raw.put(id, entity);
                recordCount.put(entityId, Integer.parseInt(first(entity, "\"RECORD_COUNT\"\\s*:\\s*(\\d+)")));
            }
            Set<Long> distinct = new LinkedHashSet<>(entityOf.values());
            boolean a = distinct.size() == EXPECTED_ENTITIES;
            long mergeEntity = entityOf.get(MERGE_IDS[0]);
            boolean b = entityOf.get(MERGE_IDS[1]) == mergeEntity && entityOf.get(MERGE_IDS[2]) == mergeEntity;
            boolean c = distinct.size() < all.size();
            boolean d = entityOf.get(DISTRACTOR_ID) != mergeEntity;
            int mergeCount = recordCount.get(mergeEntity);
            System.out.println("RESULTS entities=" + distinct.size() + " expected=" + EXPECTED_ENTITIES
                + " | a_entity_count=" + a + " b_merge_cluster_one_entity=" + b
                + " c_fewer_entities_than_records=" + c + " d_distractor_singleton=" + d
                + " | merge_entity_records=" + mergeCount + " expected=" + EXPECTED_MERGE_COUNT);
            if (!(a && b && c && d)) {
                System.out.println("MISMATCH: asking the engine why (whyRecords on the first merge pair)");
                System.out.println(engine.whyRecords(SzRecordKey.of(DS, MERGE_IDS[0]),
                                                     SzRecordKey.of(DS, MERGE_IDS[1])));
            }

            // ---- Step 8: database operations ------------------------------------------
            int written = 0;
            for (long e : distinct) {
                written += recordCount.get(e);
            }
            System.out.println("OP write_count=" + written + " expected=" + all.size()
                + " ok=" + (written == all.size()));

            String byId = engine.getEntity(mergeEntity, SZ_ENTITY_DEFAULT_FLAGS);
            boolean hasId = byId.contains("\"ENTITY_ID\":" + mergeEntity);
            boolean hasKey = Pattern.compile("\"DATA_SOURCE\"\\s*:\\s*\"" + DS + "\"").matcher(byId).find()
                && Pattern.compile("\"RECORD_ID\"\\s*:\\s*\"V100\\d\"").matcher(byId).find();
            boolean hasName = byId.contains("Testwell");
            System.out.println("OP read_by_entity_id id=" + hasId + " record_key=" + hasKey
                + " name=" + hasName + " ok=" + (hasId && hasKey && hasName));

            String search = engine.searchByAttributes(
                "{\"NAME_FIRST\":\"Jordan\",\"NAME_LAST\":\"Testwell\",\"ADDR_LINE1\":\"100 Verification Way\","
                + "\"ADDR_CITY\":\"Testville\",\"ADDR_STATE\":\"NV\",\"ADDR_POSTAL_CODE\":\"89000\"}");
            boolean found = Pattern.compile("\"ENTITY_ID\"\\s*:\\s*" + mergeEntity + "\\b").matcher(search).find();
            System.out.println("OP search_by_attributes expected_entity_in_results=" + found);

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }

    static String first(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("Field not found in response: " + regex);
        }
        return m.group(1);
    }
}
