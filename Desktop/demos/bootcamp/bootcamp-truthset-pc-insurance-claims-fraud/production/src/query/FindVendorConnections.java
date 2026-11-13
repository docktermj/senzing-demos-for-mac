import com.senzing.sdk.SzEngine;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FindVendorConnections {
    private FindVendorConnections() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, JsonObject> connections = new TreeMap<>();
        int responsesMissingRelatedEntities = 0;
        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (QuerySupport.SourceRecord inputRecord
                    : QuerySupport.readSourceRecords("POLICYHOLDERS")) {
                JsonObject response = QuerySupport.parseObject(
                        QuerySupport.entityResponse(engine, inputRecord.key()),
                        inputRecord.key().stableKey());
                JsonObject resolvedEntity = QuerySupport.requiredObject(
                        response, "RESOLVED_ENTITY", "getEntity response");
                long policyholderEntityId = resolvedEntity.getJsonNumber("ENTITY_ID").longValue();
                JsonArray entityRecords = QuerySupport.requiredArray(
                        resolvedEntity, "RECORDS", "RESOLVED_ENTITY");
                TreeSet<String> policyholderIds = new TreeSet<>();
                TreeSet<String> vendorIds = new TreeSet<>();
                JsonArrayBuilder evidenceRows = Json.createArrayBuilder();

                for (JsonValue recordValue : entityRecords) {
                    if (recordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RESOLVED_ENTITY.RECORDS contains a non-object.");
                    }
                    JsonObject record = recordValue.asJsonObject();
                    String source = record.getString("DATA_SOURCE");
                    String recordId = record.getString("RECORD_ID");
                    if ("POLICYHOLDERS".equals(source)) {
                        policyholderIds.add(recordId);
                    } else if ("VENDOR_REGISTRY".equals(source)) {
                        vendorIds.add(recordId);
                    }
                    String matchKey = QuerySupport.optionalString(record, "MATCH_KEY");
                    if (matchKey != null) {
                        JsonObjectBuilder evidence = Json.createObjectBuilder()
                                .add("data_source", source)
                                .add("record_id", recordId)
                                .add("match_key", matchKey);
                        QuerySupport.addOptionalString(
                                evidence, "match_level_code",
                                QuerySupport.optionalString(record, "MATCH_LEVEL_CODE"));
                        evidenceRows.add(evidence);
                    }
                }
                JsonArray policyholderRecords = strings(policyholderIds);
                JsonArray vendorRecords = strings(vendorIds);
                if (!vendorIds.isEmpty()) {
                    JsonObject connection = Json.createObjectBuilder()
                            .add("connection_type", "same_resolved_entity")
                            .add("policyholder_entity_id", policyholderEntityId)
                            .add("vendor_entity_id", policyholderEntityId)
                            .add("policyholder_record_ids", policyholderRecords)
                            .add("vendor_record_ids", vendorRecords)
                            .add("match_evidence", evidenceRows)
                            .build();
                    connections.put(
                            "resolved:" + policyholderEntityId + ":" + policyholderEntityId,
                            connection);
                }

                JsonValue relatedValue = response.get("RELATED_ENTITIES");
                if (relatedValue == null) {
                    responsesMissingRelatedEntities++;
                    continue;
                }
                if (relatedValue.getValueType() != JsonValue.ValueType.ARRAY) {
                    throw new IllegalStateException("RELATED_ENTITIES is not an array.");
                }
                JsonArray relatedEntities = relatedValue.asJsonArray();
                for (JsonValue relatedValueEntry : relatedEntities) {
                    if (relatedValueEntry.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RELATED_ENTITIES contains a non-object.");
                    }
                    JsonObject related = relatedValueEntry.asJsonObject();
                    JsonArray summaries = QuerySupport.requiredArray(
                            related, "RECORD_SUMMARY", "RELATED_ENTITIES entry");
                    int vendorRecordCount = 0;
                    for (JsonValue summaryValue : summaries) {
                        if (summaryValue.getValueType() != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException("RECORD_SUMMARY contains a non-object.");
                        }
                        JsonObject summary = summaryValue.asJsonObject();
                        if ("VENDOR_REGISTRY".equals(summary.getString("DATA_SOURCE"))) {
                            vendorRecordCount += summary.getInt("RECORD_COUNT");
                        }
                    }
                    if (vendorRecordCount == 0) {
                        continue;
                    }

                    long vendorEntityId = related.getJsonNumber("ENTITY_ID").longValue();
                    JsonArrayBuilder vendorSourceCounts = Json.createArrayBuilder().add(
                            Json.createObjectBuilder()
                                    .add("data_source", "VENDOR_REGISTRY")
                                    .add("record_count", vendorRecordCount));
                    JsonObjectBuilder connection = Json.createObjectBuilder()
                            .add("connection_type", "related_entity")
                            .add("policyholder_entity_id", policyholderEntityId)
                            .add("vendor_entity_id", vendorEntityId)
                            .add("policyholder_record_ids", policyholderRecords)
                            .add("vendor_source_counts", vendorSourceCounts);
                    QuerySupport.addOptionalString(
                            connection, "vendor_entity_name",
                            QuerySupport.optionalString(related, "ENTITY_NAME"));
                    QuerySupport.addOptionalString(
                            connection, "match_key", QuerySupport.optionalString(related, "MATCH_KEY"));
                    QuerySupport.addOptionalString(
                            connection, "match_level_code",
                            QuerySupport.optionalString(related, "MATCH_LEVEL_CODE"));
                    QuerySupport.addOptionalString(
                            connection, "errule_code", QuerySupport.optionalString(related, "ERRULE_CODE"));
                    connections.put(
                            "related:" + policyholderEntityId + ":" + vendorEntityId,
                            connection.build());
                }
            }
        }

        JsonArrayBuilder connectionRows = Json.createArrayBuilder();
        connections.values().forEach(connectionRows::add);
        JsonObject report = Json.createObjectBuilder()
                .add("finding_type", "review_leads_not_verdicts")
                .add("policyholder_records_screened",
                        QuerySupport.readSourceRecords("POLICYHOLDERS").size())
                .add("related_entities_field_missing_count", responsesMissingRelatedEntities)
                .add("connection_count", connections.size())
                .add("connections", connectionRows)
                .build();
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("vendor_connections.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.printf(
                "Policyholder/vendor connection leads: %d; output=%s%n",
                connections.size(), outputPath);
    }

    private static JsonArray strings(TreeSet<String> values) {
        JsonArrayBuilder output = Json.createArrayBuilder();
        values.forEach(output::add);
        return output.build();
    }
}
