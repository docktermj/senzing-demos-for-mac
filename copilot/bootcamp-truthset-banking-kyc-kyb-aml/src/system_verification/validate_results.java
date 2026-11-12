package verification;

import java.util.*;
import java.util.regex.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class ValidateResults {
    private static final String DATA_SOURCE = "VERIFY";
    private static final String[] MERGE_RECORDS = {"SYN-001", "SYN-002", "SYN-003"};
    private static final String DISTRACTOR_RECORD = "SYN-004";

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(ValidateResults.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();
            Map<String, Long> entityByRecord = new LinkedHashMap<>();
            for (String recordId : new String[] {
                    MERGE_RECORDS[0], MERGE_RECORDS[1], MERGE_RECORDS[2], DISTRACTOR_RECORD}) {
                String response = engine.getEntity(
                    SzRecordKey.of(DATA_SOURCE, recordId), SZ_ENTITY_DEFAULT_FLAGS);
                entityByRecord.put(recordId, extractEntityId(response));
            }

            Set<Long> entityIds = new HashSet<>(entityByRecord.values());
            long mergeEntityId = entityByRecord.get(MERGE_RECORDS[0]);
            boolean entityCountMatches = entityIds.size() == 2;
            boolean crossRecordResolution = entityIds.size() < 4;
            boolean mergeClusterJoined = Arrays.stream(MERGE_RECORDS)
                .allMatch(recordId -> entityByRecord.get(recordId) == mergeEntityId);
            boolean distractorSeparate =
                !Objects.equals(mergeEntityId, entityByRecord.get(DISTRACTOR_RECORD));
            System.out.println("ENTITY_IDS=" + entityByRecord);
            System.out.println("ENTITY_COUNT=" + entityIds.size());
            System.out.println("ENTITY_COUNT_MATCHES_EXPECTATION=" + entityCountMatches);
            System.out.println("CROSS_RECORD_RESOLUTION=" + crossRecordResolution);
            System.out.println("MERGE_CLUSTER_JOINED=" + mergeClusterJoined);
            System.out.println("DISTRACTOR_SEPARATE=" + distractorSeparate);

            String entityDetail = engine.getEntity(mergeEntityId, SZ_ENTITY_DEFAULT_FLAGS);
            boolean readVerified =
                entityDetail.contains("\"ENTITY_ID\"")
                && hasStringField(entityDetail, "RECORD_ID", MERGE_RECORDS[0])
                && entityDetail.contains("Avery");
            System.out.println("READ_BY_ENTITY_ID=" + readVerified);

            String searchCriteria =
                "{\"NAME_FIRST\":\"Avery\",\"NAME_LAST\":\"Example\","
                + "\"DATE_OF_BIRTH\":\"1980-02-14\","
                + "\"ADDR_FULL\":\"100 Simulation Way Sample City ZZ 00000\"}";
            String searchResponse = engine.searchByAttributes(
                searchCriteria, SZ_SEARCH_BY_ATTRIBUTES_ALL);
            boolean searchFoundExpectedEntity = containsEntityId(searchResponse, mergeEntityId);
            System.out.println("SEARCH_FOUND_EXPECTED_ENTITY=" + searchFoundExpectedEntity);
            if (!searchFoundExpectedEntity) {
                System.out.println("SEARCH_RESPONSE=" + searchResponse);
            }

            if (!entityCountMatches || !crossRecordResolution
                    || !mergeClusterJoined || !distractorSeparate) {
                throw new IllegalStateException("Synthetic entity-resolution expectation mismatch");
            }
            if (!readVerified || !searchFoundExpectedEntity) {
                throw new IllegalStateException("Database read or search operation failed");
            }
        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }

    private static long extractEntityId(String response) {
        Matcher matcher = Pattern.compile(
            "\"RESOLVED_ENTITY\"\\s*:\\s*\\{.*?\"ENTITY_ID\"\\s*:\\s*(\\d+)",
            Pattern.DOTALL).matcher(response);
        if (!matcher.find()) {
            throw new IllegalStateException("No resolved entity ID was returned: " + response);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static boolean hasStringField(String response, String name, String value) {
        Pattern field = Pattern.compile(
            "\"" + Pattern.quote(name) + "\"\\s*:\\s*\"" + Pattern.quote(value) + "\"");
        return field.matcher(response).find();
    }

    private static boolean containsEntityId(String response, long entityId) {
        Matcher matcher = Pattern.compile("\"ENTITY_ID\"\\s*:\\s*(\\d+)").matcher(response);
        while (matcher.find()) {
            if (Long.parseLong(matcher.group(1)) == entityId) {
                return true;
            }
        }
        return false;
    }
}
