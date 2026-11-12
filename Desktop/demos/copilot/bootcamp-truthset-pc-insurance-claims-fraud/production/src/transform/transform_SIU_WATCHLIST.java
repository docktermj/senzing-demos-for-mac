import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TransformSiuWatchlist {
    private static final String DATA_SOURCE = "SIU_WATCHLIST";
    private static final String SOURCE_DATA_SOURCE = "WATCHLIST";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "DATA_SOURCE", "RECORD_ID", "FEATURES", "CATEGORY", "DATE", "STATUS");
    private static final Set<String> PAYLOAD_FIELDS = Set.of("CATEGORY", "DATE", "STATUS");
    private static final Set<String> ADDRESS_CONTENT_FIELDS = Set.of(
            "ADDR_LINE1", "ADDR_LINE2", "ADDR_LINE3", "ADDR_LINE4", "ADDR_LINE5", "ADDR_LINE6",
            "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_COUNTRY", "ADDR_FULL");
    private static final Map<String, String> FEATURE_FAMILIES = featureFamilies();

    private TransformSiuWatchlist() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = parseArguments(args);
        Path parent = arguments.output().toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Set<String> recordIds = new LinkedHashSet<>();
        int recordsRead = 0;
        int inputLine = 0;
        try (BufferedReader reader = Files.newBufferedReader(arguments.input(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(arguments.output(), StandardCharsets.UTF_8)) {
            String line;
            while (recordsRead < arguments.sampleLimit() && (line = reader.readLine()) != null) {
                inputLine++;
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                try {
                    Map<String, Object> output = transform(line);
                    String recordId = requiredString(output.get("RECORD_ID"), "RECORD_ID");
                    if (!recordIds.add(recordId)) {
                        throw new IllegalArgumentException("Duplicate source RECORD_ID.");
                    }
                    writer.write(JsonSupport.stringify(output));
                    writer.newLine();
                    recordsRead++;
                    if (recordsRead % 10000 == 0) {
                        System.out.println("Processed source records: " + recordsRead);
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Invalid source record at input line " + inputLine + ": "
                                    + exception.getMessage(),
                            exception);
                }
            }
            if (recordsRead == 0) {
                throw new IllegalStateException("No source records were written.");
            }
        }

        System.out.println("Source records processed: " + recordsRead);
        System.out.println("Total records written: " + recordsRead);
        System.out.println("Output: " + arguments.output());
    }

    private static Map<String, Object> transform(String jsonLine) {
        Map<String, Object> source = JsonSupport.object(JsonSupport.parse(jsonLine), "Source record");
        for (String field : source.keySet()) {
            if (!ROOT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unmapped root field: " + field);
            }
        }
        String sourceDataSource = requiredString(source.get("DATA_SOURCE"), "DATA_SOURCE");
        if (!SOURCE_DATA_SOURCE.equals(sourceDataSource)) {
            throw new IllegalArgumentException("Unexpected source DATA_SOURCE: " + sourceDataSource);
        }
        String recordId = requiredString(source.get("RECORD_ID"), "RECORD_ID");
        if (recordId.isBlank()) {
            throw new IllegalArgumentException("RECORD_ID is blank.");
        }

        List<Object> sourceFeatures = JsonSupport.array(source.get("FEATURES"), "FEATURES");
        Map<String, Object> sourceFields = new LinkedHashMap<>();
        for (Object value : sourceFeatures) {
            Map<String, Object> feature = JsonSupport.object(value, "FEATURES item");
            for (Map.Entry<String, Object> entry : feature.entrySet()) {
                String field = entry.getKey();
                if (!"RECORD_TYPE".equals(field) && !FEATURE_FAMILIES.containsKey(field)) {
                    throw new IllegalArgumentException("Unmapped source feature: " + field);
                }
                if (sourceFields.containsKey(field)) {
                    throw new IllegalArgumentException("Repeated source feature: " + field);
                }
                sourceFields.put(field, entry.getValue());
            }
        }

        String recordType = requiredString(sourceFields.get("RECORD_TYPE"), "FEATURES.RECORD_TYPE");
        if (!"PERSON".equals(recordType)) {
            throw new IllegalArgumentException("Source mapping expects PERSON, found: " + recordType);
        }

        List<Object> outputFeatures = new ArrayList<>();
        outputFeatures.add(Map.of("RECORD_TYPE", recordType));
        Map<String, Map<String, Object>> groupedFeatures = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : sourceFields.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if ("RECORD_TYPE".equals(field) || value == null) {
                continue;
            }
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Feature value must be a string: " + field);
            }
            if (text.isBlank()) {
                continue;
            }
            String family = FEATURE_FAMILIES.get(field);
            groupedFeatures.computeIfAbsent(family, ignored -> new LinkedHashMap<>()).put(field, text);
        }

        for (Map.Entry<String, Map<String, Object>> entry : groupedFeatures.entrySet()) {
            validateFeature(entry.getKey(), entry.getValue());
            if ("NAME".equals(entry.getKey()) && entry.getValue().containsKey("NAME_FULL")
                    && entry.getValue().keySet().stream().anyMatch(
                            key -> Set.of("NAME_FIRST", "NAME_LAST", "NAME_MIDDLE").contains(key))) {
                throw new IllegalArgumentException("NAME_FULL cannot share a NAME object with parsed name fields.");
            }
            outputFeatures.add(entry.getValue());
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("DATA_SOURCE", DATA_SOURCE);
        output.put("RECORD_ID", recordId);
        output.put("FEATURES", outputFeatures);
        for (String field : PAYLOAD_FIELDS) {
            addIfPresent(output, field, source.get(field));
        }
        return output;
    }

    private static void validateFeature(String family, Map<String, Object> feature) {
        boolean hasContent = switch (family) {
            case "NAME" -> feature.keySet().stream().anyMatch(
                    key -> Set.of("NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL").contains(key));
            case "ADDRESS" -> feature.keySet().stream().anyMatch(ADDRESS_CONTENT_FIELDS::contains);
            case "DRLIC" -> feature.containsKey("DRIVERS_LICENSE_NUMBER");
            case "DATE_OF_BIRTH" -> feature.containsKey("DATE_OF_BIRTH");
            case "EMAIL" -> feature.containsKey("EMAIL_ADDRESS");
            case "SSN" -> feature.containsKey("SSN_NUMBER");
            case "GENDER" -> feature.containsKey("GENDER");
            case "PHONE" -> feature.containsKey("PHONE_NUMBER");
            case "EMPLOYER" -> feature.containsKey("EMPLOYER");
            default -> false;
        };
        if (!hasContent) {
            throw new IllegalArgumentException("Feature has qualifiers but no identity value: " + family);
        }
    }

    private static void addIfPresent(Map<String, Object> output, String field, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            return;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(field + " must be a string.");
        }
        output.put(field, value);
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(field + " must be a string.");
        }
        return text;
    }

    private static Arguments parseArguments(String[] args) {
        if (args.length < 3 || !"-o".equals(args[1])) {
            throw new IllegalArgumentException(
                    "Usage: java TransformSiuWatchlist <input.jsonl> -o <output.jsonl> [--sample N]");
        }
        int sampleLimit = Integer.MAX_VALUE;
        if (args.length == 5 && "--sample".equals(args[3])) {
            sampleLimit = Integer.parseInt(args[4]);
            if (sampleLimit < 1) {
                throw new IllegalArgumentException("--sample must be greater than zero.");
            }
        } else if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: java TransformSiuWatchlist <input.jsonl> -o <output.jsonl> [--sample N]");
        }
        return new Arguments(Path.of(args[0]), Path.of(args[2]), sampleLimit);
    }

    private static Map<String, String> featureFamilies() {
        Map<String, String> result = new LinkedHashMap<>();
        addFamily(result, "NAME", "NAME_LAST", "NAME_FIRST", "NAME_MIDDLE", "NAME_FULL");
        addFamily(result, "DATE_OF_BIRTH", "DATE_OF_BIRTH");
        addFamily(result, "ADDRESS", "ADDR_TYPE", "ADDR_LINE1", "ADDR_POSTAL_CODE",
                "ADDR_CITY", "ADDR_STATE", "ADDR_COUNTRY");
        addFamily(result, "DRLIC", "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE");
        addFamily(result, "EMAIL", "EMAIL_ADDRESS");
        addFamily(result, "SSN", "SSN_NUMBER");
        addFamily(result, "GENDER", "GENDER");
        addFamily(result, "PHONE", "PHONE_NUMBER");
        addFamily(result, "EMPLOYER", "EMPLOYER");
        return Map.copyOf(result);
    }

    private static void addFamily(Map<String, String> families, String family, String... fields) {
        for (String field : fields) {
            families.put(field, family);
        }
    }

    private record Arguments(Path input, Path output, int sampleLimit) {
    }
}
