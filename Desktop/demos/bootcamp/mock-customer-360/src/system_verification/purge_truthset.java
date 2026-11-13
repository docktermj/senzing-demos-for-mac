// purge_truthset.java -- Truth Set visualization, Step 4 (purge the Truth Set records at close).
// Deletes exactly the records listed in src/system_verification/truthset_data.jsonl, one by one,
// so any other repository state is kept; drains the redo queue the deletes create; then confirms
// none of those records can still be read. SDK calls and signatures come from get_sdk_reference
// (topic='parameters', language='java') and the redo scaffold (generate_scaffold workflow='redo'):
//   deleteRecord(SzRecordKey) -> String
//   getRedoRecord() -> String,  processRedoRecord(String, Set<SzFlag>) -> String
//   getEntity(SzRecordKey, Set<SzFlag>) -> String
// Record keys are read with the shared Json reader (src/server/Json.java).
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class PurgeTruthset {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(PurgeTruthset.class.getSimpleName())
            .verboseLogging(false)
            .build();

        int deleted = 0, failed = 0, redone = 0, remaining = 0;
        try {
            SzEngine engine = env.getEngine();
            var lines = Files.readAllLines(Paths.get("src/system_verification/truthset_data.jsonl"),
                                           StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> rec = Json.obj(Json.parse(line));
                SzRecordKey key = SzRecordKey.of(Json.str(rec.get("DATA_SOURCE"), ""),
                                                 Json.str(rec.get("RECORD_ID"), ""));
                try {
                    engine.deleteRecord(key);
                    deleted++;
                } catch (SzException e) {
                    failed++;
                    System.err.println("could not delete " + key + ": " + e.getMessage());
                }
            }
            for (String redo = engine.getRedoRecord(); redo != null; redo = engine.getRedoRecord()) {
                engine.processRedoRecord(redo, SZ_NO_FLAGS);
                redone++;
            }
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> rec = Json.obj(Json.parse(line));
                try {
                    engine.getEntity(SzRecordKey.of(Json.str(rec.get("DATA_SOURCE"), ""),
                                                    Json.str(rec.get("RECORD_ID"), "")), SZ_ENTITY_DEFAULT_FLAGS);
                    remaining++;   // still readable -> not purged
                } catch (SzBadInputException expected) {
                    // record no longer exists: the expected outcome
                }
            }
            System.out.println("PURGE deleted=" + deleted + " failed=" + failed + " redo_processed=" + redone
                + " remaining_truthset_records=" + remaining);
        } finally {
            env.destroy();
        }
    }
}
