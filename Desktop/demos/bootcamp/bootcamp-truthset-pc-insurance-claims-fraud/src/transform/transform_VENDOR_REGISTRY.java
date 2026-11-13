import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class TransformVendorRegistry {
    private static final String DATA_SOURCE = "VENDOR_REGISTRY";
    private static final String EMPLOYER_DOMAIN = "VENDOR_EMPLOYER";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "DATA_SOURCE", "RECORD_ID", "FEATURES", "CATEGORY", "DATE", "STATUS");
    private static final Set<String> ADDRESS_PARTS = Set.of(
            "ADDR_LINE1", "ADDR_LINE2", "ADDR_LINE3", "ADDR_LINE4", "ADDR_LINE5", "ADDR_LINE6",
            "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE");
    private static final Map<String, String> FEATURE_FAMILIES = featureFamilies();

    private TransformVendorRegistry() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = parseArguments(args);
        Path parent = arguments.output().toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<String, String> employerIds = new LinkedHashMap<>();
        Set<String> recordIds = new LinkedHashSet<>();
        int recordsRead = 0;
        int recordsWritten = 0;
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
                    Map<String, Object> output = transform(line, employerIds);
                    String recordId = requiredString(output.get("RECORD_ID"), "RECORD_ID");
                    if (!recordIds.add(recordId)) {
                        throw new IllegalArgumentException("Duplicate source RECORD_ID.");
                    }
                    writer.write(JsonSupport.stringify(output));
                    writer.newLine();
                    recordsRead++;
                    recordsWritten++;
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
            for (Map.Entry<String, String> employer : employerIds.entrySet()) {
                if (recordIds.contains(employer.getValue())) {
                    throw new IllegalArgumentException("Embedded employer RECORD_ID collides with a source RECORD_ID.");
                }
                writer.write(JsonSupport.stringify(employerRecord(employer.getKey(), employer.getValue())));
                writer.newLine();
                recordsWritten++;
            }
        }

        System.out.println("Source records processed: " + recordsRead);
        System.out.println("Embedded employer entities written: " + employerIds.size());
        System.out.println("Total records written: " + recordsWritten);
        System.out.println("Output: " + arguments.output());
    }

    private static Map<String, Object> transform(String jsonLine, Map<String, String> employerIds) {
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
        outputFeatures.add(Map.of("RECORD_TYPE", recordType));

        for (Object value : sourceFeatures) {
            Map<String, Object> sourceFeature = JsonSupport.object(value, "FEATURES item");
            Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : sourceFeature.entrySet()) {
                String field = entry.getKey();
                Object fieldValue = entry.getValue();
                if ("RECORD_TYPE".equals(field) || fieldValue == null
                        || fieldValue instanceof String text && text.isBlank()) {
                    continue;
                }
                if ("EMPLOYER".equals(field)) {
                    if (!"PERSON".equals(recordType)) {
                        throw new IllegalArgumentException("EMPLOYER is only valid on PERSON records.");
                    }
                    if (!(fieldValue instanceof String employerName)) {
                        throw new IllegalArgumentException("EMPLOYER must be a string.");
                    }
                    String employerId = employerIds.computeIfAbsent(employerName, TransformVendorRegistry::employerRecordId);
                    outputFeatures.add(Map.of(
                            "REL_POINTER_DOMAIN", EMPLOYER_DOMAIN,
                            "REL_POINTER_KEY", employerId,
                            "REL_POINTER_ROLE", "EMPLOYED_BY"));
                    continue;
                }

                if (!(fieldValue instanceof String text)) {
                    throw new IllegalArgumentException("Feature value must be a string: " + field);
                }
                if (Set.of("NAME_FIRST", "NAME_LAST").contains(field) && !"PERSON".equals(recordType)) {
                    throw new IllegalArgumentException("Person-name field on " + recordType + ": " + field);
                }
                if (Set.of("NAME_ORG", "NAME_TYPE").contains(field) && !"ORGANIZATION".equals(recordType)) {
                    throw new IllegalArgumentException("Organization-name field on " + recordType + ": " + field);
                }
                String family = FEATURE_FAMILIES.get(field);
                if (family == null) {
                    throw new IllegalArgumentException("Unmapped feature field: " + field);
                }
                grouped.computeIfAbsent(family, ignored -> new LinkedHashMap<>()).put(field, text);
            }
            for (Map.Entry<String, Map<String, Object>> entry : grouped.entrySet()) {
                validateFeature(entry.getKey(), entry.getValue());
                if ("ADDRESS".equals(entry.getKey())
                        && entry.getValue().containsKey("ADDR_FULL")
                        && entry.getValue().keySet().stream().anyMatch(ADDRESS_PARTS::contains)) {
                    throw new IllegalArgumentException("ADDR_FULL cannot share an ADDRESS object with parsed address fields.");
                }
                outputFeatures.add(entry.getValue());
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("DATA_SOURCE", DATA_SOURCE);
        output.put("RECORD_ID", recordId);
        output.put("FEATURES", outputFeatures);
        for (String field : List.of("CATEGORY", "DATE", "STATUS")) {
            addIfPresent(output, field, source.get(field));
        }
        return output;
    }

    private static Map<String, Object> employerRecord(String name, String recordId) {
        List<Object> features = List.of(
                Map.of("RECORD_TYPE", "ORGANIZATION"),
                Map.of("NAME_ORG", name),
                Map.of("REL_ANCHOR_DOMAIN", EMPLOYER_DOMAIN, "REL_ANCHOR_KEY", recordId));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("DATA_SOURCE", DATA_SOURCE);
        output.put("RECORD_ID", recordId);
        output.put("FEATURES", features);
        return output;
    }

    private static String findRecordType(List<Object> features) {
        Set<String> types = new LinkedHashSet<>();
        for (Object value : features) {
            Map<String, Object> feature = JsonSupport.object(value, "FEATURES item");
            if (feature.containsKey("RECORD_TYPE")) {
                types.add(requiredString(feature.get("RECORD_TYPE"), "FEATURES.RECORD_TYPE"));
            }
        }
        if (types.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one source RECORD_TYPE.");
        }
        String recordType = types.iterator().next();
        if (!Set.of("PERSON", "ORGANIZATION").contains(recordType)) {
            throw new IllegalArgumentException("Unsupported RECORD_TYPE: " + recordType);
        }
        return recordType;
    }

    private static void validateFeature(String family, Map<String, Object> feature) {
        boolean hasContent = switch (family) {
            case "NAME" -> feature.keySet().stream().anyMatch(
                    key -> Set.of("NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL", "NAME_ORG").contains(key));
            case "ADDRESS" -> feature.keySet().stream().anyMatch(
                    key -> ADDRESS_PARTS.contains(key) || "ADDR_FULL".equals(key));
            case "PHONE" -> feature.containsKey("PHONE_NUMBER");
            case "EMAIL" -> feature.containsKey("EMAIL_ADDRESS");
            case "DATE_OF_BIRTH" -> feature.containsKey("DATE_OF_BIRTH");
            case "NATIONAL_ID" -> feature.containsKey("NATIONAL_ID_NUMBER");
            case "TAX_ID" -> feature.containsKey("TAX_ID_NUMBER");
            case "LEI_NUMBER" -> feature.containsKey("LEI_NUMBER");
            case "REL_ANCHOR" -> feature.containsKey("REL_ANCHOR_DOMAIN")
                    && feature.containsKey("REL_ANCHOR_KEY");
            case "REL_POINTER" -> feature.containsKey("REL_POINTER_DOMAIN")
                    && feature.containsKey("REL_POINTER_KEY") && feature.containsKey("REL_POINTER_ROLE");
            default -> false;
        };
        if (!hasContent) {
            throw new IllegalArgumentException("Feature has qualifiers but no identity value: " + family);
        }
    }

    private static String employerRecordId(String employerName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("NAME_ORG\u0000" + employerName).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("EMP_");
            for (byte value : hash) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
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
                    "Usage: java TransformVendorRegistry <input.jsonl> -o <output.jsonl> [--sample N]");
        }
        int sampleLimit = Integer.MAX_VALUE;
        if (args.length == 5 && "--sample".equals(args[3])) {
            sampleLimit = Integer.parseInt(args[4]);
            if (sampleLimit < 1) {
                throw new IllegalArgumentException("--sample must be greater than zero.");
            }
        } else if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: java TransformVendorRegistry <input.jsonl> -o <output.jsonl> [--sample N]");
        }
        return new Arguments(Path.of(args[0]), Path.of(args[2]), sampleLimit);
    }

    private static Map<String, String> featureFamilies() {
        Map<String, String> result = new LinkedHashMap<>();
        addFamily(result, "ADDRESS", "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_POSTAL_CODE",
                "ADDR_COUNTRY", "ADDR_FULL", "ADDR_STATE");
        addFamily(result, "REL_ANCHOR", "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY");
        addFamily(result, "REL_POINTER", "REL_POINTER_DOMAIN", "REL_POINTER_KEY", "REL_POINTER_ROLE");
        addFamily(result, "NAME", "NAME_FULL", "NAME_FIRST", "NAME_LAST", "NAME_ORG", "NAME_TYPE");
        addFamily(result, "DATE_OF_BIRTH", "DATE_OF_BIRTH");
        addFamily(result, "NATIONAL_ID", "NATIONAL_ID_NUMBER", "NATIONAL_ID_TYPE", "NATIONAL_ID_COUNTRY");
        addFamily(result, "TAX_ID", "TAX_ID_NUMBER", "TAX_ID_TYPE", "TAX_ID_COUNTRY");
        addFamily(result, "PHONE", "PHONE_NUMBER", "PHONE_TYPE");
        addFamily(result, "LEI_NUMBER", "LEI_NUMBER");
        addFamily(result, "EMAIL", "EMAIL_ADDRESS");
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
