package loading;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

class LoadTruthSet {
    private static final Pattern DATA_SOURCE_PATTERN =
        Pattern.compile("\"DATA_SOURCE\"\\s*:\\s*\"([^\"\\\\]*)\"");
    private static final Pattern RECORD_ID_PATTERN =
        Pattern.compile("\"RECORD_ID\"\\s*:\\s*\"([^\"\\\\]*)\"");

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "Usage: LoadTruthSet <jsonl-path> <expected-record-count>");
        }

        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            throw new IllegalArgumentException("Unable to get settings");
        }

        int expectedCount = Integer.parseInt(args[1]);
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(LoadTruthSet.class.getSimpleName())
            .verboseLogging(false)
            .build();

        int lineNumber = 0;
        int loadedCount = 0;
        try (BufferedReader reader =
                 Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            SzEngine engine = env.getEngine();
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                String dataSource = extract(DATA_SOURCE_PATTERN, line, "DATA_SOURCE");
                String recordId = extract(RECORD_ID_PATTERN, line, "RECORD_ID");
                engine.addRecord(SzRecordKey.of(dataSource, recordId), line, SZ_NO_FLAGS);
                loadedCount++;
                if (loadedCount % 25 == 0 || loadedCount == expectedCount) {
                    System.out.printf("Loaded %d/%d Truth Set records%n", loadedCount, expectedCount);
                }
            }
        } catch (Exception e) {
            System.err.printf("Truth Set load failed at line %d: %s%n", lineNumber, e.getMessage());
            throw new RuntimeException(e);
        } finally {
            env.destroy();
        }

        if (loadedCount != expectedCount) {
            throw new IllegalStateException(
                "Expected " + expectedCount + " records, loaded " + loadedCount);
        }
        System.out.println("Truth Set load complete: " + loadedCount + " records");
    }

    private static String extract(Pattern pattern, String jsonLine, String field) {
        Matcher matcher = pattern.matcher(jsonLine);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing or escaped JSON string field: " + field);
        }
        return matcher.group(1);
    }
}
