package verification;

import java.util.*;
import java.util.regex.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

class VerifyResolution {
    private static final String DATA_SOURCE = "VERIFY";
    private static final String[] RECORD_IDS = {
        "SYNTH-1001", "SYNTH-1002", "SYNTH-1003", "SYNTH-2001"
    };
    private static final Set<SzFlag> ENTITY_FLAGS = EnumSet.of(
        SzFlag.SZ_ENTITY_INCLUDE_RECORD_DATA,
        SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA
    );

    public static void main(String[] args) {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(VerifyResolution.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();
            Map<String, Long> entityByRecord = new LinkedHashMap<>();

            for (String recordId : RECORD_IDS) {
                String response = engine.getEntity(
                    SzRecordKey.of(DATA_SOURCE, recordId), ENTITY_FLAGS);
                String resolvedEntity = valueForKey(response, "RESOLVED_ENTITY");
                entityByRecord.put(recordId,
                    Long.parseLong(valueForKey(resolvedEntity, "ENTITY_ID")));
            }

            Set<Long> entityIds = new HashSet<>(entityByRecord.values());
            long mergeEntityId = entityByRecord.get(RECORD_IDS[0]);
            boolean mergeClusterMatches =
                entityByRecord.get(RECORD_IDS[1]).equals(mergeEntityId)
                && entityByRecord.get(RECORD_IDS[2]).equals(mergeEntityId);
            boolean distractorSeparate =
                !entityByRecord.get(RECORD_IDS[3]).equals(mergeEntityId);

            String mergeResponse = engine.getEntity(mergeEntityId, ENTITY_FLAGS);
            String mergeEntity = valueForKey(mergeResponse, "RESOLVED_ENTITY");
            List<String> mergeRecords = arrayObjects(valueForKey(mergeEntity, "RECORDS"));
            Set<String> mergeRecordIds = new HashSet<>();
            boolean namePresent = false;
            for (String record : mergeRecords) {
                if (DATA_SOURCE.equals(stringForKey(record, "DATA_SOURCE"))) {
                    mergeRecordIds.add(stringForKey(record, "RECORD_ID"));
                    String recordJson = valueForKey(record, "JSON_DATA");
                    namePresent |= "Test".equals(stringForKey(recordJson, "NAME_FIRST"));
                }
            }

            boolean expectedClusterPresent =
                mergeRecordIds.contains(RECORD_IDS[0])
                && mergeRecordIds.contains(RECORD_IDS[1])
                && mergeRecordIds.contains(RECORD_IDS[2]);
            String distractorResponse = engine.getEntity(
                entityByRecord.get(RECORD_IDS[3]), ENTITY_FLAGS);
            String distractorEntity = valueForKey(distractorResponse, "RESOLVED_ENTITY");
            boolean distractorSingleton =
                arrayObjects(valueForKey(distractorEntity, "RECORDS")).size() == 1;

            boolean passed = entityIds.size() == 2
                && entityIds.size() < RECORD_IDS.length
                && mergeClusterMatches
                && distractorSeparate
                && expectedClusterPresent
                && distractorSingleton;

            System.out.println("RESULTS_VALIDATION="
                + (passed ? "passed" : "mismatch")
                + "; entities=" + entityIds.size()
                + "; matches_verified=" + mergeRecordIds.size());
            System.out.println("ENTITY_COUNT=" + (entityIds.size() == 2)
                + "; MERGE_CLUSTER=" + mergeClusterMatches
                + "; CROSS_RECORD_RESOLUTION=" + (entityIds.size() < RECORD_IDS.length)
                + "; DISTRACTOR_SINGLETON=" + (distractorSeparate && distractorSingleton)
                + "; ENTITY_RECORD_KEYS=" + expectedClusterPresent
                + "; NAME_ATTRIBUTE=" + namePresent);
            System.out.println("MERGE_ENTITY_ID=" + mergeEntityId);
        } catch (Exception e) {
            System.err.println("RESULTS_VALIDATION=failed; " + e);
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }
    }

    private static String valueForKey(String json, String key) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*").matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing response field: " + key);
        }
        int start = matcher.end();
        int end = valueEnd(json, start);
        return json.substring(start, end).trim();
    }

    private static String stringForKey(String json, String key) {
        String value = valueForKey(json, key);
        if (value.length() < 2 || value.charAt(0) != '"') {
            throw new IllegalArgumentException("Expected string response field: " + key);
        }
        return value.substring(1, value.length() - 1);
    }

    private static List<String> arrayObjects(String json) {
        if (json == null || json.length() < 2 || json.charAt(0) != '[') {
            return Collections.emptyList();
        }
        List<String> objects = new ArrayList<>();
        int index = 1;
        while (index < json.length() - 1) {
            while (index < json.length() && (Character.isWhitespace(json.charAt(index))
                || json.charAt(index) == ',')) {
                index++;
            }
            if (index >= json.length() - 1) {
                break;
            }
            if (json.charAt(index) != '{') {
                throw new IllegalArgumentException("Expected object in response array");
            }
            int end = valueEnd(json, index);
            objects.add(json.substring(index, end));
            index = end;
        }
        return objects;
    }

    private static int valueEnd(String json, int start) {
        char first = json.charAt(start);
        if (first == '"') {
            boolean escaped = false;
            for (int i = start + 1; i < json.length(); i++) {
                char current = json.charAt(i);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    return i + 1;
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }
        if (first == '{' || first == '[') {
            Deque<Character> openings = new ArrayDeque<>();
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < json.length(); i++) {
                char current = json.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if (current == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (current == '"') {
                    inString = true;
                } else if (current == '{' || current == '[') {
                    openings.push(current);
                } else if (current == '}' || current == ']') {
                    char opening = openings.pop();
                    if ((opening == '{' && current != '}')
                        || (opening == '[' && current != ']')) {
                        throw new IllegalArgumentException("Mismatched JSON delimiters");
                    }
                    if (openings.isEmpty()) {
                        return i + 1;
                    }
                }
            }
            throw new IllegalArgumentException("Unterminated JSON value");
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != ','
            && json.charAt(end) != '}' && json.charAt(end) != ']') {
            end++;
        }
        return end;
    }
}
