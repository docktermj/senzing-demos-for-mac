package verification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.core.SzCoreEnvironment;

class VerifyDatabaseOperations {
    private static final String DATA_FILE =
        "src/system_verification/verification_data.jsonl";
    private static final String SEARCH_CRITERIA =
        "{\"NAME_FIRST\":\"Test\",\"NAME_LAST\":\"Record\","
            + "\"ADDR_LINE1\":\"100 Example Street\","
            + "\"ADDR_CITY\":\"Testville\",\"ADDR_STATE\":\"TX\","
            + "\"ADDR_POSTAL_CODE\":\"75001\"}";
    private static final Set<SzFlag> ENTITY_FLAGS = EnumSet.of(
        SzFlag.SZ_ENTITY_INCLUDE_RECORD_DATA,
        SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA
    );
    private static final Set<SzFlag> SEARCH_FLAGS =
        SzFlag.SZ_SEARCH_BY_ATTRIBUTES_MINIMAL_STRONG;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                "Usage: VerifyDatabaseOperations <write|read|search> "
                    + "<records-loaded> <merge-entity-id>");
        }

        String operation = args[0];
        int recordsLoaded = Integer.parseInt(args[1]);
        long mergeEntityId = Long.parseLong(args[2]);
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(VerifyDatabaseOperations.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();
            switch (operation) {
                case "write":
                    verifyWriteCount(recordsLoaded);
                    break;
                case "read":
                    verifyRead(engine, mergeEntityId);
                    break;
                case "search":
                    verifySearch(engine, mergeEntityId);
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Unknown database operation: " + operation);
            }
        } finally {
            env.destroy();
        }
    }

    private static void verifyWriteCount(int recordsLoaded) throws IOException {
        long expectedCount;
        try (Stream<String> lines = Files.lines(Path.of(DATA_FILE))) {
            expectedCount = lines
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .count();
        }
        if (recordsLoaded != expectedCount) {
            throw new IllegalStateException(
                "WRITE_OPERATION=failed; records_loaded=" + recordsLoaded
                    + "; expected=" + expectedCount);
        }
        System.out.println(
            "WRITE_OPERATION=passed; records_loaded=" + recordsLoaded
                + "; expected=" + expectedCount);
    }

    private static void verifyRead(SzEngine engine, long entityId) throws Exception {
        String response = engine.getEntity(entityId, ENTITY_FLAGS);
        boolean idPresent = hasNumberField(response, "ENTITY_ID", entityId);
        boolean recordKeyPresent =
            hasStringField(response, "DATA_SOURCE", "VERIFY")
            && hasStringField(response, "RECORD_ID", "SYNTH-1001");
        boolean namePresent = hasStringField(response, "NAME_FIRST", "Test");
        if (!idPresent || !recordKeyPresent || !namePresent) {
            throw new IllegalStateException(
                "READ_OPERATION=failed; entity_id_present=" + idPresent
                    + "; record_key_present=" + recordKeyPresent
                    + "; name_present=" + namePresent);
        }
        System.out.println(
            "READ_OPERATION=passed; entity_id=" + entityId
                + "; record_key=VERIFY/SYNTH-1001; name_attribute=NAME_FIRST");
    }

    private static void verifySearch(SzEngine engine, long entityId) throws Exception {
        String response = engine.searchByAttributes(
            SEARCH_CRITERIA, "SEARCH", SEARCH_FLAGS);
        if (!hasNumberField(response, "ENTITY_ID", entityId)) {
            throw new IllegalStateException(
                "SEARCH_OPERATION=failed; expected_entity_id=" + entityId
                    + "; response=" + response);
        }
        System.out.println(
            "SEARCH_OPERATION=passed; expected_entity_id=" + entityId
                + "; attributes=NAME_FIRST,NAME_LAST,ADDR_LINE1,ADDR_CITY,"
                + "ADDR_STATE,ADDR_POSTAL_CODE");
    }

    private static boolean hasStringField(
        String json, String key, String expectedValue) {
        return Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\""
                + Pattern.quote(expectedValue) + "\"")
            .matcher(json)
            .find();
    }

    private static boolean hasNumberField(
        String json, String key, long expectedValue) {
        return Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*"
                + expectedValue + "(?!\\d)")
            .matcher(json)
            .find();
    }
}
