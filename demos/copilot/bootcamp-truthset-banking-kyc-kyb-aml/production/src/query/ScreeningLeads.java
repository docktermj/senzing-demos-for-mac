package query;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;

import static com.senzing.sdk.SzFlag.SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS;

public final class ScreeningLeads {
    private static final Path INPUT =
        Path.of("data/senzing-ready/AML_WATCHLIST.jsonl");
    private static final Path OUTPUT =
        Path.of("data/query/screening_leads.jsonl");
    private static final Set<String> REVIEW_SOURCES =
        Set.of("BANK_CUSTOMERS", "BUSINESS_REGISTRY");

    private ScreeningLeads() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException(
                "Usage: query.ScreeningLeads");
        }

        Map<QuerySupport.RecordKey, JsonObject> records =
            QuerySupport.readUniqueRecords(INPUT);
        if (records.isEmpty()) {
            throw new IllegalStateException("The watchlist input contains no records.");
        }
        Files.createDirectories(OUTPUT.getParent());

        SzEnvironment environment = QuerySupport.openEnvironment(
            ScreeningLeads.class.getSimpleName());
        long queryCount = 0;
        long recordsWithLeads = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(
            OUTPUT, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
            SzEngine engine = environment.getEngine();
            for (Map.Entry<QuerySupport.RecordKey, JsonObject> recordEntry
                : records.entrySet()) {
                QuerySupport.RecordKey recordKey = recordEntry.getKey();
                JsonArray features = QuerySupport.requireArray(
                    recordEntry.getValue(), "FEATURES");
                Map<Long, Candidate> candidates = new TreeMap<>();
                int recordQueryCount = 0;

                for (JsonValue featureValue : features) {
                    if (featureValue.getValueType()
                        != JsonValue.ValueType.OBJECT) {
                        throw new IllegalStateException(
                            "FEATURES contains a non-object item for "
                                + recordKey.dataSource() + ":" + recordKey.recordId());
                    }
                    JsonObject criteria = QuerySupport.searchCriteria(
                        (JsonObject) featureValue);
                    if (criteria.isEmpty()) {
                        continue;
                    }

                    String response = engine.searchByAttributes(
                        criteria.toString(),
                        SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
                    queryCount++;
                    recordQueryCount++;
                    JsonObject search = QuerySupport.parseObject(response);
                    JsonArray results = QuerySupport.requireArray(
                        search, "RESOLVED_ENTITIES");
                    for (JsonValue resultValue : results) {
                        if (resultValue.getValueType()
                            != JsonValue.ValueType.OBJECT) {
                            throw new IllegalStateException(
                                "RESOLVED_ENTITIES contains a non-object item.");
                        }
                        JsonObject result = (JsonObject) resultValue;
                        JsonObject entity = QuerySupport.requireObject(
                            result, "ENTITY");
                        JsonObject resolved = QuerySupport.requireObject(
                            entity, "RESOLVED_ENTITY");
                        Set<String> sources =
                            QuerySupport.recordSources(resolved);
                        if (sources.stream().noneMatch(REVIEW_SOURCES::contains)) {
                            continue;
                        }

                        JsonObject matchInfo = QuerySupport.requireObject(
                            result, "MATCH_INFO");
                        long entityId =
                            QuerySupport.requiredLong(resolved, "ENTITY_ID");
                        Candidate candidate = candidates.computeIfAbsent(
                            entityId,
                            ignored -> new Candidate(
                                entityId,
                                QuerySupport.optionalString(
                                    resolved, "ENTITY_NAME"),
                                QuerySupport.requireArray(
                                    resolved, "RECORD_SUMMARY")));
                        candidate.addEvidence(
                            criteria.keySet(),
                            matchInfo);
                    }
                }

                if (!candidates.isEmpty()) {
                    recordsWithLeads++;
                }
                writer.write(reportLine(
                    recordKey, recordQueryCount, candidates).toString());
                writer.newLine();
            }
        } finally {
            environment.destroy();
        }

        System.out.println("Distinct watchlist records: " + records.size());
        System.out.println("Attribute searches run: " + queryCount);
        System.out.println("Records with customer/business review leads: "
            + recordsWithLeads);
        System.out.println("Evidence report: " + OUTPUT);
        System.out.println(
            "Leads are review candidates, not screening verdicts.");
    }

    private static JsonObject reportLine(
        QuerySupport.RecordKey recordKey,
        int queryCount,
        Map<Long, Candidate> candidates
    ) {
        JsonArrayBuilder candidateArray = Json.createArrayBuilder();
        for (Candidate candidate : candidates.values()) {
            candidateArray.add(candidate.toJson());
        }
        return Json.createObjectBuilder()
            .add("watchlist_data_source", recordKey.dataSource())
            .add("watchlist_record_id", recordKey.recordId())
            .add("attribute_searches", queryCount)
            .add("status", candidates.isEmpty()
                ? "NO_CUSTOMER_SIDE_CANDIDATES"
                : "REVIEW_LEADS_FOUND")
            .add("candidates", candidateArray)
            .build();
    }

    private static final class Candidate {
        private final long entityId;
        private final String entityName;
        private final JsonArray recordSummary;
        private final Set<String> evidence = new LinkedHashSet<>();

        private Candidate(
            long entityId,
            String entityName,
            JsonArray recordSummary
        ) {
            this.entityId = entityId;
            this.entityName = entityName;
            this.recordSummary = recordSummary;
        }

        private void addEvidence(
            Set<String> features,
            JsonObject matchInfo
        ) {
            evidence.add(
                "features=" + String.join(",", features)
                    + "; match_level="
                    + QuerySupport.optionalString(matchInfo, "MATCH_LEVEL_CODE")
                    + "; match_key="
                    + QuerySupport.optionalString(matchInfo, "MATCH_KEY")
                    + "; rule="
                    + QuerySupport.optionalString(matchInfo, "ERRULE_CODE")
                    + "; feature_scores="
                    + QuerySupport.summarizeFeatureScores(matchInfo));
        }

        private JsonObject toJson() {
            List<String> orderedEvidence = new ArrayList<>(evidence);
            return Json.createObjectBuilder()
                .add("entity_id", entityId)
                .add("entity_name", entityName)
                .add("record_summary", recordSummary)
                .add("evidence", QuerySupport.stringArray(orderedEvidence))
                .build();
        }
    }
}
