import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TransformPolicyholders {
    private static final String DATA_SOURCE = "POLICYHOLDERS";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "DATA_SOURCE", "RECORD_ID", "FEATURES", "AMOUNT", "CATEGORY", "DATE", "STATUS");
    private static final Set<String> PERSON_NAME_FIELDS = Set.of(
            "NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL");
    private static final Set<String> ORGANIZATION_NAME_FIELDS = Set.of("NAME_ORG", "NAME_TYPE");
    private static final Set<String> PARSED_ADDRESS_FIELDS = Set.of(
            "ADDR_LINE1", "ADDR_LINE2", "ADDR_LINE3", "ADDR_LINE4", "ADDR_LINE5", "ADDR_LINE6",
            "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE");
    private static final Map<String, String> FEATURE_FAMILIES = featureFamilies();

    private TransformPolicyholders() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: java TransformPolicyholders <input.jsonl> <output.jsonl> [record-limit]");
        }
        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);
        int recordLimit = args.length == 3 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;
        if (recordLimit < 1) {
            throw new IllegalArgumentException("record-limit must be greater than zero.");
        }
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        int recordsWritten = 0;
        int unpairedPhoneTypes = 0;
        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while (recordsWritten < recordLimit && (line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.stripLeading().startsWith("#")) {
                    continue;
                }
                try {
                    TransformResult result = transform(line);
                    writer.write(JsonSupport.stringify(result.record()));
                    writer.newLine();
                    recordsWritten++;
                    unpairedPhoneTypes += result.unpairedPhoneTypes();
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Invalid source record at line " + lineNumber + ": " + exception.getMessage(),
                            exception);
                }
            }
        }
        if (recordsWritten == 0) {
            throw new IllegalStateException("No source records were written.");
        }
        System.out.println("Records written: " + recordsWritten);
        System.out.println("Phone-type-only values retained as root payload: " + unpairedPhoneTypes);
        System.out.println("Output: " + outputPath);
    }

    private static TransformResult transform(String jsonLine) {
        Map<String, Object> source = JsonSupport.object(JsonSupport.parse(jsonLine), "Source record");
        for (String field : source.keySet()) {
            if (!ROOT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unmapped root field: " + field);
            }
        }
        String recordId = requiredString(source.get("RECORD_ID"), "RECORD_ID");
        if (recordId.isBlank()) {
            throw new IllegalArgumentException("RECORD_ID is blank.");
        }
        List<Object> sourceFeatures = JsonSupport.array(source.get("FEATURES"), "FEATURES");
        String recordType = findRecordType(sourceFeatures);

        List<Object> outputFeatures = new ArrayList<>();
        Map<String, Object> conditionalPayload = new LinkedHashMap<>();
        outputFeatures.add(Map.of("RECORD_TYPE", recordType));
        int unpairedPhoneTypes = 0;
        for (Object value : sourceFeatures) {
            Map<String, Object> sourceFeature = JsonSupport.object(value, "FEATURES item");
            if (sourceFeature.containsKey("RECORD_TYPE")) {
                if (sourceFeature.size() != 1) {
                    throw new IllegalArgumentException("RECORD_TYPE must be its own feature object.");
                }
                continue;
            }
            FeatureResult result = mapFeature(sourceFeature, recordType);
            if (result.feature() != null) {
                outputFeatures.add(result.feature());
            }
            if (result.unpairedPhoneType() != null) {
                if (conditionalPayload.putIfAbsent("SOURCE_PHONE_QUALIFIER", result.unpairedPhoneType()) != null) {
                    throw new IllegalArgumentException("Multiple unpaired PHONE_TYPE values cannot be retained as one payload.");
                }
                unpairedPhoneTypes++;
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("DATA_SOURCE", DATA_SOURCE);
        output.put("RECORD_ID", recordId);
        output.put("FEATURES", outputFeatures);
        for (String field : List.of("AMOUNT", "CATEGORY", "DATE", "STATUS")) {
            addIfPresent(output, field, source.get(field));
        }
        for (Map.Entry<String, Object> entry : conditionalPayload.entrySet()) {
            if (output.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("Conditional payload conflicts with a root field: " + entry.getKey());
            }
            output.put(entry.getKey(), entry.getValue());
        }
        return new TransformResult(output, unpairedPhoneTypes);
    }

    private static String findRecordType(List<Object> features) {
        String recordType = null;
        for (Object value : features) {
            Map<String, Object> feature = JsonSupport.object(value, "FEATURES item");
            if (!feature.containsKey("RECORD_TYPE")) {
                continue;
            }
            String candidate = requiredString(feature.get("RECORD_TYPE"), "FEATURES.RECORD_TYPE");
            if (recordType != null && !recordType.equals(candidate)) {
                throw new IllegalArgumentException("Conflicting FEATURES.RECORD_TYPE values.");
            }
            recordType = candidate;
        }
        if (!"PERSON".equals(recordType) && !"ORGANIZATION".equals(recordType)) {
            throw new IllegalArgumentException("Missing or unsupported FEATURES.RECORD_TYPE.");
        }
        return recordType;
    }

    private static FeatureResult mapFeature(Map<String, Object> sourceFeature, String recordType) {
        Set<String> families = new LinkedHashSet<>();
        for (String field : sourceFeature.keySet()) {
            String family = FEATURE_FAMILIES.get(field);
            if (family == null) {
                throw new IllegalArgumentException("Unmapped feature field: " + field);
            }
            families.add(family);
        }
        if (families.size() != 1) {
            throw new IllegalArgumentException("A source feature object must contain exactly one feature family.");
        }
        String family = families.iterator().next();
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : sourceFeature.entrySet()) {
            if ("NAME".equals(family) && !nameFields(recordType).contains(entry.getKey())) {
                throw new IllegalArgumentException("Name field is inconsistent with " + recordType + ": " + entry.getKey());
            }
            Object value = entry.getValue();
            if (value == null || value instanceof String text && text.isBlank()) {
                continue;
            }
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Feature value must be a string: " + entry.getKey());
            }
            mapped.put(entry.getKey(), value);
        }
        if (mapped.isEmpty()) {
            return FeatureResult.empty();
        }
        if ("ADDRESS".equals(family)
                && mapped.containsKey("ADDR_FULL")
                && mapped.keySet().stream().anyMatch(PARSED_ADDRESS_FIELDS::contains)) {
            throw new IllegalArgumentException("ADDR_FULL cannot share an ADDRESS feature object with parsed fields.");
        }
        if ("NAME".equals(family)
                && mapped.containsKey("NAME_FULL")
                && mapped.keySet().stream().anyMatch(field -> Set.of("NAME_FIRST", "NAME_LAST", "NAME_MIDDLE").contains(field))) {
            throw new IllegalArgumentException("NAME_FULL cannot share a NAME feature object with parsed name fields.");
        }
        if ("NAME".equals(family)
                && mapped.containsKey("NAME_ORG")
                && mapped.keySet().stream().anyMatch(PERSON_NAME_FIELDS::contains)) {
            throw new IllegalArgumentException("NAME_ORG cannot share a NAME feature object with person-name fields.");
        }

        if ("PHONE".equals(family) && !mapped.containsKey("PHONE_NUMBER")) {
            if (mapped.size() == 1 && mapped.containsKey("PHONE_TYPE")) {
                return FeatureResult.unpairedPhoneType((String) mapped.get("PHONE_TYPE"));
            }
            throw new IllegalArgumentException("PHONE feature has no PHONE_NUMBER.");
        }
        if (!hasContent(family, mapped)) {
            throw new IllegalArgumentException("Feature has qualifiers but no content: " + family);
        }
        return FeatureResult.feature(mapped);
    }

    private static boolean hasContent(String family, Map<String, Object> feature) {
        return switch (family) {
            case "NAME" -> feature.keySet().stream().anyMatch(field ->
                    PERSON_NAME_FIELDS.contains(field) && !"NAME_TYPE".equals(field)
                            || "NAME_ORG".equals(field));
            case "ADDRESS" -> feature.keySet().stream().anyMatch(field ->
                    PARSED_ADDRESS_FIELDS.contains(field) || "ADDR_FULL".equals(field));
            case "PHONE" -> feature.containsKey("PHONE_NUMBER");
            case "EMAIL" -> feature.containsKey("EMAIL_ADDRESS");
            case "DATE_OF_BIRTH" -> feature.containsKey("DATE_OF_BIRTH");
            case "DRLIC" -> feature.containsKey("DRIVERS_LICENSE_NUMBER");
            case "SSN" -> feature.containsKey("SSN_NUMBER");
            case "GENDER" -> feature.containsKey("GENDER");
            case "PASSPORT" -> feature.containsKey("PASSPORT_NUMBER");
            case "NATIONAL_ID" -> feature.containsKey("NATIONAL_ID_NUMBER");
            default -> false;
        };
    }

    private static Set<String> nameFields(String recordType) {
        return "PERSON".equals(recordType) ? PERSON_NAME_FIELDS : ORGANIZATION_NAME_FIELDS;
    }

    private static void addIfPresent(Map<String, Object> output, String field, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            return;
        }
        output.put(field, value);
    }

    private static String requiredString(Object value, String field) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(field + " must be a string.");
        }
        return text;
    }

    private static Map<String, String> featureFamilies() {
        Map<String, String> result = new LinkedHashMap<>();
        addFamily(result, "NAME", "NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL", "NAME_ORG", "NAME_TYPE");
        addFamily(result, "DATE_OF_BIRTH", "DATE_OF_BIRTH");
        addFamily(result, "ADDRESS", "ADDR_TYPE", "ADDR_LINE1", "ADDR_LINE2", "ADDR_LINE3", "ADDR_LINE4",
                "ADDR_LINE5", "ADDR_LINE6", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_COUNTRY", "ADDR_FULL");
        addFamily(result, "PHONE", "PHONE_TYPE", "PHONE_NUMBER");
        addFamily(result, "EMAIL", "EMAIL_ADDRESS");
        addFamily(result, "DRLIC", "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE");
        addFamily(result, "SSN", "SSN_NUMBER");
        addFamily(result, "GENDER", "GENDER");
        addFamily(result, "PASSPORT", "PASSPORT_NUMBER", "PASSPORT_COUNTRY");
        addFamily(result, "NATIONAL_ID", "NATIONAL_ID_NUMBER", "NATIONAL_ID_COUNTRY");
        addFamily(result, "RECORD_TYPE", "RECORD_TYPE");
        return Map.copyOf(result);
    }

    private static void addFamily(Map<String, String> families, String family, String... fields) {
        for (String field : fields) {
            families.put(field, family);
        }
    }

    private record TransformResult(Map<String, Object> record, int unpairedPhoneTypes) {
    }

    private record FeatureResult(Map<String, Object> feature, String unpairedPhoneType) {
        private static FeatureResult feature(Map<String, Object> feature) {
            return new FeatureResult(feature, null);
        }

        private static FeatureResult unpairedPhoneType(String value) {
            return new FeatureResult(null, value);
        }

        private static FeatureResult empty() {
            return new FeatureResult(null, null);
        }
    }
}
