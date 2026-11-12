package query;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.SzRecordKey;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA;
import static com.senzing.sdk.SzFlag.SZ_INCLUDE_MATCH_KEY_DETAILS;

public final class Customer360 {
    private static final List<String> DISPLAYABLE_RECORD_FIELDS = List.of(
        "NAME_FULL",
        "NAME_ORG",
        "NAME_FIRST",
        "NAME_LAST",
        "DATE_OF_BIRTH",
        "ADDR_CITY",
        "ADDR_STATE",
        "ADDR_COUNTRY");

    private Customer360() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "Usage: query.Customer360 <DATA_SOURCE> <RECORD_ID>");
        }

        SzEnvironment environment = QuerySupport.openEnvironment(
            Customer360.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            Set<SzFlag> flags = EnumSet.noneOf(SzFlag.class);
            flags.addAll(SZ_ENTITY_DEFAULT_FLAGS);
            flags.add(SZ_INCLUDE_MATCH_KEY_DETAILS);
            flags.add(SZ_ENTITY_INCLUDE_RECORD_JSON_DATA);
            String response = engine.getEntity(
                SzRecordKey.of(args[0], args[1]), flags);
            JsonObject result = QuerySupport.parseObject(response);
            JsonObject resolved = QuerySupport.requireObject(
                result, "RESOLVED_ENTITY");

            System.out.println(
                "Unified entity " + QuerySupport.requiredLong(resolved, "ENTITY_ID")
                    + " | " + QuerySupport.optionalString(resolved, "ENTITY_NAME"));
            printRecords(QuerySupport.requireArray(resolved, "RECORDS"));
            printSources(QuerySupport.requireArray(resolved, "RECORD_SUMMARY"));
            printRelatedEntities(result);
        } finally {
            environment.destroy();
        }
    }

    private static void printRecords(JsonArray records) {
        System.out.println("Resolved records: " + records.size());
        for (JsonValue value : records) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException("RECORDS contains a non-object item.");
            }
            JsonObject record = (JsonObject) value;
            System.out.println(
                "  " + QuerySupport.optionalString(record, "DATA_SOURCE")
                    + ":" + QuerySupport.optionalString(record, "RECORD_ID")
                    + " match_key=" + QuerySupport.optionalString(record, "MATCH_KEY")
                    + " rule=" + QuerySupport.optionalString(record, "ERRULE_CODE"));
            printRecordFeatures(record);
            printRelationshipRoles(record);
        }
    }

    private static void printRecordFeatures(JsonObject record) {
        JsonValue dataValue = record.get("JSON_DATA");
        if (dataValue == null || dataValue.getValueType() != JsonValue.ValueType.OBJECT) {
            System.out.println(
                "    No value returned for RESOLVED_ENTITY.RECORDS[].JSON_DATA.");
            return;
        }

        JsonObject data = (JsonObject) dataValue;
        JsonValue featuresValue = data.get("FEATURES");
        if (featuresValue == null
            || featuresValue.getValueType() != JsonValue.ValueType.ARRAY) {
            System.out.println(
                "    No value returned for RESOLVED_ENTITY.RECORDS[].JSON_DATA.FEATURES.");
            return;
        }

        List<String> summaries = new ArrayList<>();
        for (JsonValue featureValue : (JsonArray) featuresValue) {
            if (featureValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "JSON_DATA.FEATURES contains a non-object item.");
            }
            JsonObject feature = (JsonObject) featureValue;
            for (String field : DISPLAYABLE_RECORD_FIELDS) {
                JsonValue fieldValue = feature.get(field);
                if (fieldValue == null
                    || fieldValue.getValueType() == JsonValue.ValueType.NULL) {
                    continue;
                }
                String value = QuerySupport.optionalString(feature, field);
                if (!value.startsWith("(present but empty:")) {
                    summaries.add(field + "=" + value);
                }
            }
        }
        System.out.println(
            summaries.isEmpty()
                ? "    No displayable name or location fields in JSON_DATA.FEATURES."
                : "    Record data: " + String.join("; ", summaries));
    }

    private static void printRelationshipRoles(JsonObject record) {
        JsonValue dataValue = record.get("JSON_DATA");
        if (dataValue == null || dataValue.getValueType() != JsonValue.ValueType.OBJECT) {
            System.out.println(
                "    No value returned for RESOLVED_ENTITY.RECORDS[].JSON_DATA.");
            return;
        }

        JsonObject data = (JsonObject) dataValue;
        JsonValue featuresValue = data.get("FEATURES");
        if (featuresValue == null
            || featuresValue.getValueType() != JsonValue.ValueType.ARRAY) {
            System.out.println(
                "    No value returned for RESOLVED_ENTITY.RECORDS[].JSON_DATA.FEATURES.");
            return;
        }

        JsonArray features = (JsonArray) featuresValue;
        int relationshipFieldsFound = 0;
        for (JsonValue featureValue : features) {
            if (featureValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "JSON_DATA.FEATURES contains a non-object item.");
            }
            JsonObject feature = (JsonObject) featureValue;
            JsonValue roleValue = feature.get("REL_POINTER_ROLE");
            if (roleValue != null
                && roleValue.getValueType() != JsonValue.ValueType.NULL) {
                System.out.println(
                    "    Relationship pointer: role="
                        + QuerySupport.optionalString(feature, "REL_POINTER_ROLE")
                        + " -> "
                        + QuerySupport.optionalString(feature, "REL_POINTER_DOMAIN")
                        + ":"
                        + QuerySupport.optionalString(feature, "REL_POINTER_KEY"));
                relationshipFieldsFound++;
            }
            JsonValue anchorKeyValue = feature.get("REL_ANCHOR_KEY");
            if (anchorKeyValue != null
                && anchorKeyValue.getValueType() != JsonValue.ValueType.NULL) {
                System.out.println(
                    "    Relationship anchor: "
                        + QuerySupport.optionalString(feature, "REL_ANCHOR_DOMAIN")
                        + ":"
                        + QuerySupport.optionalString(feature, "REL_ANCHOR_KEY"));
                relationshipFieldsFound++;
            }
        }
        if (relationshipFieldsFound == 0) {
            System.out.println(
                "    No REL_POINTER_ROLE or REL_ANCHOR_KEY value returned for this record.");
        }
    }

    private static void printSources(JsonArray summary) {
        System.out.println("Source summary:");
        if (summary.isEmpty()) {
            System.out.println("  RECORD_SUMMARY is present but empty.");
            return;
        }
        for (JsonValue value : summary) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "RECORD_SUMMARY contains a non-object item.");
            }
            JsonObject source = (JsonObject) value;
            System.out.println(
                "  " + QuerySupport.optionalString(source, "DATA_SOURCE")
                    + " records="
                    + QuerySupport.optionalString(source, "RECORD_COUNT"));
        }
    }

    private static void printRelatedEntities(JsonObject result) {
        JsonValue value = result.get("RELATED_ENTITIES");
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            System.out.println(
                "No value returned for the documented RELATED_ENTITIES path.");
            return;
        }
        if (value.getValueType() != JsonValue.ValueType.ARRAY) {
            throw new IllegalStateException("RELATED_ENTITIES is not an array.");
        }

        JsonArray relatedEntities = (JsonArray) value;
        if (relatedEntities.isEmpty()) {
            System.out.println("RELATED_ENTITIES is present but empty.");
            return;
        }

        System.out.println("Related entities: " + relatedEntities.size());
        for (JsonValue relatedValue : relatedEntities) {
            if (relatedValue.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "RELATED_ENTITIES contains a non-object item.");
            }
            JsonObject related = (JsonObject) relatedValue;
            System.out.println(
                "  Entity " + QuerySupport.optionalString(related, "ENTITY_ID")
                    + " | " + QuerySupport.optionalString(related, "ENTITY_NAME")
                    + " | " + QuerySupport.optionalString(related, "MATCH_LEVEL_CODE")
                    + " | relationship match key="
                    + QuerySupport.optionalString(related, "MATCH_KEY"));
            System.out.println(
                "    match-key evidence: "
                    + QuerySupport.summarizeMatchKeyDetails(related));
            JsonValue summaryValue = related.get("RECORD_SUMMARY");
            if (summaryValue == null
                || summaryValue.getValueType() != JsonValue.ValueType.ARRAY) {
                System.out.println(
                    "    No value returned for RELATED_ENTITIES[].RECORD_SUMMARY.");
            } else {
                printSources((JsonArray) summaryValue);
            }
        }
    }
}
