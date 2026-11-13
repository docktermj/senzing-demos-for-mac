import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzFlag;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.senzing.sdk.SzFlag.SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS;
import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_DATA;
import static com.senzing.sdk.SzFlag.SZ_SEARCH_INCLUDE_STATS;

public final class ScreenWatchlistLeads {
    private ScreenWatchlistLeads() {
    }

    public static void main(String[] args) throws Exception {
        List<QuerySupport.SourceRecord> watchlistRecords =
                QuerySupport.readSourceRecords("SIU_WATCHLIST");
        List<JsonObject> screenings = new ArrayList<>();
        Set<SzFlag> flags = new HashSet<>(SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
        flags.add(SZ_ENTITY_INCLUDE_RECORD_DATA);
        flags.add(SZ_SEARCH_INCLUDE_STATS);

        try (QuerySupport.Runtime runtime = QuerySupport.open()) {
            SzEngine engine = runtime.engine();
            for (QuerySupport.SourceRecord record : watchlistRecords) {
                JsonObject criteria = QuerySupport.searchCriteria(record.input());
                JsonObjectBuilder screening = Json.createObjectBuilder()
                        .add("watchlist_record_id", record.key().recordId())
                        .add("criteria_fields", stringArray(criteria.keySet()));

                if (criteria.isEmpty()) {
                    screening.add("screening_status", "not_screened_no_usable_criteria")
                            .add("candidate_count", 0)
                            .add("candidates", Json.createArrayBuilder());
                    screenings.add(screening.build());
                    continue;
                }

                JsonObject response = QuerySupport.parseObject(
                        engine.searchByAttributes(criteria.toString(), flags),
                        "watchlist record " + record.key().recordId());
                JsonArray stats = QuerySupport.requiredArray(
                        response, "SEARCH_STATISTICS", "searchByAttributes response");
                if (stats.isEmpty()) {
                    throw new IllegalStateException("SEARCH_STATISTICS is empty for a watchlist search.");
                }
                JsonObject candidateKeys = QuerySupport.requiredObject(
                        stats.getJsonObject(0), "CANDIDATE_KEYS", "SEARCH_STATISTICS entry");
                JsonObject keySummary = QuerySupport.requiredObject(
                        candidateKeys, "SUMMARY", "CANDIDATE_KEYS");
                int found = keySummary.getInt("FOUND");
                int notFound = keySummary.getInt("NOT_FOUND");
                int generic = keySummary.getInt("GENERIC");
                screening.add("lookup_keys_found", found)
                        .add("lookup_keys_not_found", notFound)
                        .add("lookup_keys_generic", generic);

                JsonArray results = QuerySupport.requiredArray(
                        response, "RESOLVED_ENTITIES", "searchByAttributes response");
                JsonArrayBuilder candidateRows = Json.createArrayBuilder();
                int candidateCount = 0;
                for (JsonValue resultValue : results) {
                    if (resultValue.getValueType() != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException("RESOLVED_ENTITIES contains a non-object.");
                    }
                    JsonObject result = resultValue.asJsonObject();
                    JsonObject entity = QuerySupport.requiredObject(result, "ENTITY", "search result");
                    JsonObject resolvedEntity = QuerySupport.requiredObject(
                            entity, "RESOLVED_ENTITY", "search result ENTITY");
                    JsonArray summaries = QuerySupport.requiredArray(
                            resolvedEntity, "RECORD_SUMMARY", "search result RESOLVED_ENTITY");
                    JsonObjectBuilder sourceCounts = Json.createObjectBuilder();
                    boolean relevantSource = false;
                    for (JsonValue summaryValue : summaries) {
                        if (summaryValue.getValueType() != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException("RECORD_SUMMARY contains a non-object.");
                        }
                        JsonObject summary = summaryValue.asJsonObject();
                        String source = summary.getString("DATA_SOURCE");
                        int count = summary.getInt("RECORD_COUNT");
                        sourceCounts.add(source, count);
                        relevantSource |= "POLICYHOLDERS".equals(source)
                                || "VENDOR_REGISTRY".equals(source);
                    }
                    if (!relevantSource) {
                        continue;
                    }

                    JsonObject matchInfo = QuerySupport.requiredObject(result, "MATCH_INFO", "search result");
                    JsonObjectBuilder candidate = Json.createObjectBuilder()
                            .add("entity_id", resolvedEntity.getJsonNumber("ENTITY_ID"))
                            .add("source_counts", sourceCounts)
                            .add("match_info", Json.createObjectBuilder()
                                    .add("match_level_code", matchInfo.getString("MATCH_LEVEL_CODE"))
                                    .add("match_key", matchInfo.getString("MATCH_KEY")));
                    QuerySupport.addOptionalString(
                            candidate, "entity_name",
                            QuerySupport.optionalString(resolvedEntity, "ENTITY_NAME"));

                    JsonArray entityRecords = QuerySupport.requiredArray(
                            resolvedEntity, "RECORDS", "search result RESOLVED_ENTITY");
                    JsonArrayBuilder recordRows = Json.createArrayBuilder();
                    for (JsonValue entityRecordValue : entityRecords) {
                        if (entityRecordValue.getValueType() != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException("search result RECORDS contains a non-object.");
                        }
                        JsonObject entityRecord = entityRecordValue.asJsonObject();
                        JsonObject recordRow = Json.createObjectBuilder()
                                .add("data_source", entityRecord.getString("DATA_SOURCE"))
                                .add("record_id", entityRecord.getString("RECORD_ID"))
                                .build();
                        recordRows.add(recordRow);
                    }
                    candidate.add("entity_records", recordRows);
                    candidateRows.add(candidate);
                    candidateCount++;
                }

                String status = candidateCount > 0
                        ? "candidate_leads_found"
                        : (found == 0 && notFound == 0
                                ? "no_usable_lookup_keys"
                                : "no_policyholder_or_vendor_candidates");
                screening.add("screening_status", status)
                        .add("candidate_count", candidateCount)
                        .add("candidates", candidateRows);
                screenings.add(screening.build());
            }
        }

        JsonArrayBuilder screeningRows = Json.createArrayBuilder();
        screenings.forEach(screeningRows::add);
        JsonObject report = Json.createObjectBuilder()
                .add("finding_type", "potential_connections_for_human_review")
                .add("watchlist_records_screened", watchlistRecords.size())
                .add("lead_count", screenings.stream()
                        .filter(screening -> screening.getInt("candidate_count") > 0)
                        .mapToInt(screening -> screening.getInt("candidate_count"))
                        .sum())
                .add("screenings", screeningRows)
                .build();
        Path outputPath = QuerySupport.REPORT_DIRECTORY.resolve("watchlist_leads.json");
        QuerySupport.writeJson(outputPath, report);
        System.out.printf(
                "Watchlist screening: %d records; output=%s%n",
                watchlistRecords.size(), outputPath);
    }

    private static JsonArray stringArray(Set<String> values) {
        JsonArrayBuilder output = Json.createArrayBuilder();
        values.stream().sorted().forEach(output::add);
        return output.build();
    }
}
