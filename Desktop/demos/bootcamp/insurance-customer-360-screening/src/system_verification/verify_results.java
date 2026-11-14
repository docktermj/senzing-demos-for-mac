package system_verification;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * System verification, Steps 7 and 8: results validation and database operations.
 *
 * Written against the Senzing Java SDK signatures returned by the Senzing MCP server
 * (get_sdk_reference, language='java'):
 *   SzEngine.getEntity(SzRecordKey, Set<SzFlag>) -> String
 *   SzEngine.searchByAttributes(String attributes, Set<SzFlag>) -> String
 *   SzEngine.whyRecords(SzRecordKey, SzRecordKey) -> String
 * Response fields used (get_sdk_reference response_schemas, get_entity):
 *   RESOLVED_ENTITY.ENTITY_ID, RESOLVED_ENTITY.RECORDS[].DATA_SOURCE / RECORD_ID,
 *   RESOLVED_ENTITY.ENTITY_NAME. The first ENTITY_ID in the response is the resolved
 *   entity's; no JSON library is used (javax.json is not provided by the install).
 *
 * Expected outcome, by construction (verification_data.jsonl): V1, V2, V3 are one person and
 * must resolve to one entity; V4 is a different person and must stay its own entity, so
 * 4 records -> 2 entities with a 3-record cluster. No direct SQL is used.
 *
 * Output is one labelled line per result so the run can be recorded.
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class VerifyResults {
    private static final String DS = "VERIFY";
    private static final String[] IDS = {"V1", "V2", "V3", "V4"};
    private static final int EXPECTED_ENTITIES = 2;
    private static final int EXPECTED_CLUSTER = 3;

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

            // flags: only what this program reads
            Set<SzFlag> entityFlags = new HashSet<>();
            entityFlags.add(SZ_ENTITY_INCLUDE_ENTITY_NAME);
            entityFlags.add(SZ_ENTITY_INCLUDE_RECORD_DATA);

            // ---- Step 7: where did each record land? ----
            Map<String, Long> entityOf = new LinkedHashMap<>();
            Map<Long, Integer> recordsPerEntity = new LinkedHashMap<>();
            String clusterEntityJson = null;
            for (String id : IDS) {
                String json = engine.getEntity(SzRecordKey.of(DS, id), entityFlags);
                Long entityId = firstLong(json, "ENTITY_ID");
                if (entityId == null) {
                    System.out.println("RESULT=no ENTITY_ID in response for " + id + " (check flags / field names): " + json);
                    entityId = -1L;
                }
                entityOf.put(id, entityId);
                recordsPerEntity.merge(entityId, 1, Integer::sum);
                if (id.equals("V1")) {
                    clusterEntityJson = json;
                }
            }
            int entities = recordsPerEntity.size();
            int largest = Collections.max(recordsPerEntity.values());

            boolean a = (entities == EXPECTED_ENTITIES);
            boolean b = entityOf.get("V1").equals(entityOf.get("V2")) && entityOf.get("V1").equals(entityOf.get("V3"));
            boolean c = entities < IDS.length;
            boolean d = !entityOf.get("V4").equals(entityOf.get("V1"));
            System.out.println("ENTITY_OF=" + entityOf);
            System.out.println("CHECK_a_entity_count=" + a + " (found " + entities + ", expected " + EXPECTED_ENTITIES + ")");
            System.out.println("CHECK_b_merge_cluster_one_entity=" + b);
            System.out.println("CHECK_c_cross_record_resolution=" + c + " (" + entities + " entities < " + IDS.length + " records)");
            System.out.println("CHECK_d_distractor_singleton=" + d);
            System.out.println("ENTITIES=" + entities);
            System.out.println("MATCHES_VERIFIED=" + largest + " (expected " + EXPECTED_CLUSTER + ")");

            if (!(a && b && c && d)) {
                // a mismatch is a diagnostic, not a verdict: ask the engine why
                System.out.println("WHY_V1_V2=" + engine.whyRecords(SzRecordKey.of(DS, "V1"), SzRecordKey.of(DS, "V2")));
                System.out.println("WHY_V1_V3=" + engine.whyRecords(SzRecordKey.of(DS, "V1"), SzRecordKey.of(DS, "V3")));
            }

            // ---- Step 8: database operations ----
            // write count: every loaded record is retrievable by its record key
            int retrievable = 0;
            for (String id : IDS) {
                engine.getEntity(SzRecordKey.of(DS, id), SZ_NO_FLAGS);
                retrievable++;
            }
            System.out.println("OP_write_count=" + (retrievable == IDS.length) + " (" + retrievable + " of " + IDS.length + " records retrievable)");

            // read by entity ID: the merge-cluster entity
            long clusterId = entityOf.get("V1");
            String byId = engine.getEntity(clusterId, entityFlags);
            boolean readOk = byId.contains("\"ENTITY_ID\":" + clusterId)
                && byId.contains("\"DATA_SOURCE\":\"" + DS + "\"")
                && byId.contains("\"RECORD_ID\":\"V1\"")
                && byId.contains("Tamrick");
            System.out.println("OP_read_by_entity_id=" + readOk + " (entity " + clusterId + ")");

            // search by attributes: name and address of one synthetic record
            String attrs = "{\"NAME_FIRST\":\"Orsolya\",\"NAME_LAST\":\"Tamrick\","
                + "\"ADDR_LINE1\":\"482 Quillfeather Ln\",\"ADDR_CITY\":\"Brindlemoor\","
                + "\"ADDR_STATE\":\"OR\",\"ADDR_POSTAL_CODE\":\"97299\"}";
            Set<SzFlag> searchFlags = new HashSet<>(SZ_SEARCH_INCLUDE_ALL_ENTITIES);
            searchFlags.add(SZ_ENTITY_INCLUDE_ENTITY_NAME);
            searchFlags.add(SZ_ENTITY_INCLUDE_RECORD_SUMMARY);
            String found = engine.searchByAttributes(attrs, searchFlags);
            boolean searchOk = found.contains("\"ENTITY_ID\":" + clusterId);
            System.out.println("OP_search_by_attributes=" + searchOk);

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);

        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(e);

        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
    }

    /** First integer value of a named field (no JSON library). */
    private static Long firstLong(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }
}
