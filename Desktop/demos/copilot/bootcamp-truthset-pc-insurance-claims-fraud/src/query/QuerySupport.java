import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzRecordKey;
import com.senzing.sdk.core.SzCoreEnvironment;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_DEFAULT_FLAGS;

public final class QuerySupport {
    static final List<String> SOURCE_CODES = List.of(
            "POLICYHOLDERS", "VENDOR_REGISTRY", "SIU_WATCHLIST");
    static final Path REPORT_DIRECTORY = Path.of("reports", "query");

    private static final Map<String, Path> SOURCE_FILES = Map.of(
            "POLICYHOLDERS", Path.of("data/senzing-ready/POLICYHOLDERS.jsonl"),
            "VENDOR_REGISTRY", Path.of("data/senzing-ready/VENDOR_REGISTRY.jsonl"),
            "SIU_WATCHLIST", Path.of("data/senzing-ready/SIU_WATCHLIST.jsonl"));
    private static final Set<String> SEARCHABLE_ATTRIBUTES = Set.of(
            "NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL",
            "DATE_OF_BIRTH", "SSN_NUMBER", "NATIONAL_ID_NUMBER",
            "DRIVERS_LICENSE_NUMBER", "PASSPORT_NUMBER",
            "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_COUNTRY",
            "PHONE_NUMBER", "EMAIL_ADDRESS", "WEBSITE_ADDRESS");

    private QuerySupport() {
    }

    static Runtime open() {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null || settings.isBlank()) {
            throw new IllegalArgumentException("SENZING_ENGINE_CONFIGURATION_JSON is not set.");
        }

        SzEnvironment environment = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName("Module7Query")
                .verboseLogging(false)
                .build();
        return new Runtime(environment);
    }

    static List<SourceRecord> readSourceRecords(String source) throws IOException {
        Path path = SOURCE_FILES.get(source);
        if (path == null) {
            throw new IllegalArgumentException("Unknown data source: " + source);
        }

        Map<RecordKey, SourceRecord> records = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                JsonObject record;
                try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                    record = jsonReader.readObject();
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid JSON in " + path + ":" + lineNumber, exception);
                }

                String recordSource = requiredString(record, "DATA_SOURCE", path, lineNumber);
                String recordId = requiredString(record, "RECORD_ID", path, lineNumber);
                if (!source.equals(recordSource)) {
                    throw new IOException("Unexpected DATA_SOURCE in " + path + ":" + lineNumber);
                }
                RecordKey key = new RecordKey(recordSource, recordId);
                records.put(key, new SourceRecord(key, record));
            }
        }
        return List.copyOf(records.values());
    }

    static List<SourceRecord> readAllSourceRecords() throws IOException {
        Map<RecordKey, SourceRecord> records = new LinkedHashMap<>();
        for (String source : SOURCE_CODES) {
            for (SourceRecord record : readSourceRecords(source)) {
                records.put(record.key(), record);
            }
        }
        return List.copyOf(records.values());
    }

    static JsonObject parseObject(String json, String context) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid JSON response for " + context, exception);
        }
    }

    static JsonObject requiredObject(JsonObject object, String key, String context) {
        JsonValue value = object.get(key);
        if (value == null || value.getValueType() != JsonValue.ValueType.OBJECT) {
            throw new IllegalStateException(context + " is missing object field " + key);
        }
        return value.asJsonObject();
    }

    static JsonArray requiredArray(JsonObject object, String key, String context) {
        JsonValue value = object.get(key);
        if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
            throw new IllegalStateException(context + " is missing array field " + key);
        }
        return value.asJsonArray();
    }

    static String requiredString(JsonObject object, String key, Path path, long lineNumber) throws IOException {
        String value = stringValue(object.get(key));
        if (value == null || value.isBlank()) {
            throw new IOException("Missing " + key + " in " + path + ":" + lineNumber);
        }
        return value;
    }

    static String stringValue(JsonValue value) {
        if (value == null || value.getValueType() != JsonValue.ValueType.STRING) {
            return null;
        }
        return ((JsonString) value).getString();
    }

    static String optionalString(JsonObject object, String key) {
        String value = stringValue(object.get(key));
        return value == null || value.isBlank() ? null : value;
    }

    static void addOptionalString(JsonObjectBuilder builder, String key, String value) {
        if (value == null) {
            builder.addNull(key);
        } else {
            builder.add(key, value);
        }
    }

    static JsonObject searchCriteria(JsonObject record) {
        JsonArray features = requiredArray(record, "FEATURES", "input record");
        Map<String, String> values = new TreeMap<>();
        for (JsonValue featureValue : features) {
            if (featureValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalArgumentException("FEATURES contains a non-object value.");
            }
            JsonObject feature = featureValue.asJsonObject();
            for (Map.Entry<String, JsonValue> entry : feature.entrySet()) {
                if (!SEARCHABLE_ATTRIBUTES.contains(entry.getKey())) {
                    continue;
                }
                String value = stringValue(entry.getValue());
                if (value == null || value.isBlank()) {
                    continue;
                }
                String previous = values.putIfAbsent(entry.getKey(), value);
                if (previous != null && !previous.equals(value)) {
                    throw new IllegalArgumentException(
                            "Conflicting " + entry.getKey() + " values in a search record.");
                }
            }
        }

        JsonObjectBuilder criteria = Json.createObjectBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            criteria.add(entry.getKey(), entry.getValue());
        }
        return criteria.build();
    }

    static void writeJson(Path path, JsonObject object) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, object + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    static void writeJsonLines(Path path, List<JsonObject> objects) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>(objects.size());
        for (JsonObject object : objects) {
            lines.add(object.toString());
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    static String entityResponse(SzEngine engine, RecordKey key) throws Exception {
        return engine.getEntity(SzRecordKey.of(key.dataSource(), key.recordId()), SZ_ENTITY_DEFAULT_FLAGS);
    }

    record RecordKey(String dataSource, String recordId) {
        String stableKey() {
            return dataSource + "\u001f" + recordId;
        }
    }

    record SourceRecord(RecordKey key, JsonObject input) {
    }

    public static final class Runtime implements AutoCloseable {
        private final SzEnvironment environment;

        private Runtime(SzEnvironment environment) {
            this.environment = environment;
        }

        SzEngine engine() throws Exception {
            return environment.getEngine();
        }

        @Override
        public void close() {
            environment.destroy();
        }
    }
}
