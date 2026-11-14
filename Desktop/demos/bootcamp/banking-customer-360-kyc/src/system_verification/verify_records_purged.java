// Confirms that every record key in a JSONL file is gone from Senzing (getEntity raises
// SzNotFoundException). SDK calls only, never SQL. Used after the Truth Set purge.
//   run-java.sh <classes> VerifyRecordsPurged src/system_verification/truthset_data.jsonl
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

class VerifyRecordsPurged {
    public static void main(String[] args) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) throw new IllegalArgumentException("Unable to get settings");
        Pattern ds = Pattern.compile("\"DATA_SOURCE\"\\s*:\\s*\"([^\"]*)\"");
        Pattern id = Pattern.compile("\"RECORD_ID\"\\s*:\\s*\"([^\"]*)\"");
        SzEnvironment env = SzCoreEnvironment.newBuilder().settings(settings)
            .instanceName(VerifyRecordsPurged.class.getSimpleName()).verboseLogging(false).build();
        int checked = 0, remaining = 0;
        try {
            SzEngine engine = env.getEngine();
            for (String line : Files.readAllLines(Paths.get(args[0]))) {
                Matcher a = ds.matcher(line), b = id.matcher(line);
                if (!a.find() || !b.find()) continue;
                checked++;
                try {
                    engine.getEntity(SzRecordKey.of(a.group(1), b.group(1)), EnumSet.of(SZ_ENTITY_INCLUDE_ENTITY_NAME));
                    remaining++;
                    System.out.println("STILL PRESENT " + a.group(1) + "/" + b.group(1));
                } catch (SzNotFoundException e) {
                    // gone, as intended
                }
            }
        } finally {
            env.destroy();
        }
        System.out.println("Records checked: " + checked + ", still present: " + remaining);
    }
}
