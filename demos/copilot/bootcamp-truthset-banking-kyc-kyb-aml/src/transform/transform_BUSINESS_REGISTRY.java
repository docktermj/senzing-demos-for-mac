import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class transform_BUSINESS_REGISTRY {
    private static final String DATA_SOURCE = "BUSINESS_REGISTRY";
    private static final Set<String> PERSON_NAME_FIELDS = fields(
        "NAME_FULL", "NAME_FIRST", "NAME_LAST");
    private static final Set<String> ORGANIZATION_NAME_FIELDS = fields(
        "NAME_ORG", "NAME_TYPE");
    private static final Set<String> ADDRESS_FIELDS = fields(
        "ADDR_TYPE", "ADDR_FULL", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE",
        "ADDR_POSTAL_CODE", "ADDR_COUNTRY");
    private static final Set<String> STRUCTURED_ADDRESS_FIELDS = fields(
        "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE");
    private static final Set<String> PAYLOAD_FIELDS = fields("CATEGORY", "STATUS");

    private transform_BUSINESS_REGISTRY() {
    }

    public static void main(String[] args) throws IOException {
        Arguments options = parseArguments(args);
        if (!Files.isRegularFile(options.input)) {
            throw new IllegalArgumentException("Input file does not exist: " + options.input);
        }
        if (options.input.toAbsolutePath().normalize()
                .equals(options.output.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Input and output paths must be different.");
        }

        Path outputParent = options.output.toAbsolutePath().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        long recordCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(
                 options.input, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(
                 options.output, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                Map<String, Object> source;
                try {
                    source = Jsonl.parseObject(line);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Invalid JSON at input line " + lineNumber + ": " + e.getMessage(), e);
                }
                Map<String, Object> mapped = mapRecord(source, lineNumber);
                writer.write(Jsonl.stringify(mapped));
                writer.newLine();
                recordCount++;
                if (recordCount % 10000 == 0) {
                    System.out.println("Mapped " + recordCount + " records.");
                }
                if (options.sampleLimit > 0 && recordCount >= options.sampleLimit) {
                    break;
                }
            }
        }
        System.out.println(
            "Read " + recordCount + " source records; wrote " + recordCount
                + " mapped records to " + options.output + ".");
    }

    private static Map<String, Object> mapRecord(Map<String, Object> source, long lineNumber) {
        String recordId = stringValue(source, "RECORD_ID", lineNumber);
        Object sourceFeatureValue = source.get("FEATURES");
        if (!(sourceFeatureValue instanceof List<?>)) {
            throw new IllegalArgumentException(
                "Missing FEATURES array at input line " + lineNumber + ".");
        }
        List<?> sourceFeatures = (List<?>) sourceFeatureValue;
        String recordType = findRecordType(sourceFeatures, lineNumber);
        if (!"PERSON".equals(recordType) && !"ORGANIZATION".equals(recordType)) {
            throw new IllegalArgumentException(
                "Unsupported or missing RECORD_TYPE at input line " + lineNumber
                    + ": " + recordType);
        }
        if (source.containsKey("DATE") && !"ORGANIZATION".equals(recordType)) {
            throw new IllegalArgumentException(
                "DATE was mapped as organization registration date but appears on a non-organization record at input line "
                    + lineNumber + ".");
        }

        List<Object> features = new java.util.ArrayList<Object>();
        Map<String, Object> typeFeature = new LinkedHashMap<String, Object>();
        typeFeature.put("RECORD_TYPE", recordType);
        features.add(typeFeature);
        for (Object value : sourceFeatures) {
            if (!(value instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(
                    "FEATURES entries must be objects at input line " + lineNumber + ".");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceFeature = (Map<String, Object>) value;
            appendFeature(features, sourceFeature, recordType, lineNumber);
        }
        if (source.containsKey("DATE")) {
            Map<String, Object> registrationDate = new LinkedHashMap<String, Object>();
            copyString(registrationDate, source, "DATE", "REGISTRATION_DATE", lineNumber);
            features.add(registrationDate);
        }

        Map<String, Object> output = new LinkedHashMap<String, Object>();
        output.put("DATA_SOURCE", DATA_SOURCE);
        output.put("RECORD_ID", recordId);
        output.put("FEATURES", features);
        for (String field : PAYLOAD_FIELDS) {
            if (source.containsKey(field)) {
                copyString(output, source, field, field, lineNumber);
            }
        }
        return output;
    }

    private static String findRecordType(List<?> features, long lineNumber) {
        String recordType = null;
        for (Object value : features) {
            if (!(value instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(
                    "FEATURES entries must be objects at input line " + lineNumber + ".");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> feature = (Map<String, Object>) value;
            if (!feature.containsKey("RECORD_TYPE")) {
                continue;
            }
            String candidate = stringValue(feature, "RECORD_TYPE", lineNumber);
            if (recordType != null && !recordType.equals(candidate)) {
                throw new IllegalArgumentException(
                    "Conflicting RECORD_TYPE values at input line " + lineNumber + ".");
            }
            recordType = candidate;
        }
        return recordType;
    }

    private static void appendFeature(
            List<Object> output,
            Map<String, Object> source,
            String recordType,
            long lineNumber) {
        Set<String> nameFields = "PERSON".equals(recordType)
            ? PERSON_NAME_FIELDS : ORGANIZATION_NAME_FIELDS;
        appendFields(output, source, nameFields, null, null, lineNumber);
        appendFields(output, source, fields("DATE_OF_BIRTH"), null, null, lineNumber);
        appendAddress(output, source, lineNumber);
        appendFields(output, source, fields("EMPLOYER"), null, null, lineNumber);
        appendFields(output, source, fields("PHONE_NUMBER", "PHONE_TYPE"),
            "PHONE_NUMBER", null, lineNumber);
        appendFields(output, source, fields("EMAIL_ADDRESS"), null, null, lineNumber);
        appendFields(output, source,
            fields("TAX_ID_TYPE", "TAX_ID_NUMBER", "TAX_ID_COUNTRY"),
            "TAX_ID_NUMBER", null, lineNumber);
        appendFields(output, source,
            fields("NATIONAL_ID_TYPE", "NATIONAL_ID_NUMBER", "NATIONAL_ID_COUNTRY"),
            "NATIONAL_ID_NUMBER", null, lineNumber);
        appendFields(output, source, fields("LEI_NUMBER"), null, null, lineNumber);
        appendFields(output, source,
            fields("REL_POINTER_DOMAIN", "REL_POINTER_KEY", "REL_POINTER_ROLE"),
            "REL_POINTER_KEY", null, lineNumber);
        appendFields(output, source,
            fields("REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY"),
            "REL_ANCHOR_KEY", null, lineNumber);
    }

    private static void appendAddress(
            List<Object> output,
            Map<String, Object> source,
            long lineNumber) {
        boolean hasStructuredAddress = containsAny(source, STRUCTURED_ADDRESS_FIELDS);
        boolean hasFullAddress = source.containsKey("ADDR_FULL");
        if (!hasStructuredAddress && !hasFullAddress) {
            return;
        }
        Set<String> excluded = hasStructuredAddress ? fields("ADDR_FULL") : fields();
        appendFields(output, source, ADDRESS_FIELDS, null, excluded, lineNumber);
    }

    private static void appendFields(
            List<Object> output,
            Map<String, Object> source,
            Set<String> allowedFields,
            String requiredField,
            Set<String> excludedFields,
            long lineNumber) {
        if (requiredField != null && !source.containsKey(requiredField)) {
            return;
        }
        Map<String, Object> feature = new LinkedHashMap<String, Object>();
        for (String field : allowedFields) {
            if (source.containsKey(field)
                    && (excludedFields == null || !excludedFields.contains(field))) {
                copyString(feature, source, field, field, lineNumber);
            }
        }
        if (!feature.isEmpty()) {
            output.add(feature);
        }
    }

    private static void copyString(
            Map<String, Object> target,
            Map<String, Object> source,
            String sourceField,
            String targetField,
            long lineNumber) {
        Object value = source.get(sourceField);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                "Expected a string for " + sourceField + " at input line " + lineNumber + ".");
        }
        target.put(targetField, value);
    }

    private static String stringValue(Map<String, Object> object, String field, long lineNumber) {
        Object value = object.get(field);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                "Expected a string for " + field + " at input line " + lineNumber + ".");
        }
        return (String) value;
    }

    private static boolean containsAny(Map<String, Object> object, Set<String> fields) {
        for (String field : fields) {
            if (object.containsKey(field)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> fields(String... names) {
        return new LinkedHashSet<String>(Arrays.asList(names));
    }

    private static Arguments parseArguments(String[] args) {
        if (args.length < 3 || "--help".equals(args[0])) {
            throw new IllegalArgumentException(
                "Usage: transform_BUSINESS_REGISTRY <input.jsonl> -o <output.jsonl> [--sample N]");
        }
        Path input = Paths.get(args[0]);
        Path output = null;
        long sampleLimit = 0;
        for (int i = 1; i < args.length; i++) {
            if ("-o".equals(args[i]) && i + 1 < args.length) {
                output = Paths.get(args[++i]);
            } else if ("--sample".equals(args[i]) && i + 1 < args.length) {
                try {
                    sampleLimit = Long.parseLong(args[++i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("--sample must be a positive integer.", e);
                }
                if (sampleLimit < 1) {
                    throw new IllegalArgumentException("--sample must be a positive integer.");
                }
            } else {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
        }
        if (output == null) {
            throw new IllegalArgumentException("Output path is required with -o.");
        }
        return new Arguments(input, output, sampleLimit);
    }

    private static final class Arguments {
        private final Path input;
        private final Path output;
        private final long sampleLimit;

        private Arguments(Path input, Path output, long sampleLimit) {
            this.input = input;
            this.output = output;
            this.sampleLimit = sampleLimit;
        }
    }
}
