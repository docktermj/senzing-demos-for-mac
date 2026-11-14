package system_verification;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Truth Set visualization, Step 4: purge the Truth Set records from the repository.
 *
 * Deletes exactly the records listed in truthset_data.jsonl, one
 * SzEngine.deleteRecord(SzRecordKey, Set<SzFlag>) per record with SZ_NO_FLAGS (signature from
 * the Senzing MCP get_sdk_reference, language='java'), so any other data is untouched. Then
 * drains the redo queue (getRedoRecord / processRedoRecord, as in the redo scaffold) and
 * looks every record up again: zero must remain. No direct SQL is used.
 *
 * Output: PURGED=<n>, REDO=<n>, REMAINING=<n>.
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class PurgeTruthsetData {
    public static void main(String[] args) throws IOException {
        String file = args.length > 0 ? args[0] : "src/system_verification/truthset_data.jsonl";
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        List<SzRecordKey> keys = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            String ds = field(line, "DATA_SOURCE"), rid = field(line, "RECORD_ID");
            if (ds != null && rid != null) keys.add(SzRecordKey.of(ds, rid));
        }

        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(PurgeTruthsetData.class.getSimpleName())
            .verboseLogging(false)
            .build();

        try {
            SzEngine engine = env.getEngine();

            int purged = 0;
            List<String> failed = new ArrayList<>();
            for (SzRecordKey k : keys) {
                try {
                    engine.deleteRecord(k, SzFlag.SZ_NO_FLAGS);
                    purged++;
                } catch (SzException e) {
                    failed.add(k + ": " + e.getMessage());
                }
            }

            int redo = 0;
            for (String r = engine.getRedoRecord(); r != null; r = engine.getRedoRecord()) {
                engine.processRedoRecord(r, SzFlag.SZ_NO_FLAGS);
                redo++;
            }

            int remaining = 0;
            for (SzRecordKey k : keys) {
                try {
                    engine.getEntity(k, SzFlag.SZ_NO_FLAGS);
                    remaining++;
                } catch (SzNotFoundException e) {
                    // expected: the record is gone
                }
            }
            System.out.println("PURGED=" + purged);
            System.out.println("REDO=" + redo);
            System.out.println("REMAINING=" + remaining);
            for (String f : failed) System.out.println("FAILED " + f);

        } catch (SzException e) {
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            throw new RuntimeException(e);

        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
