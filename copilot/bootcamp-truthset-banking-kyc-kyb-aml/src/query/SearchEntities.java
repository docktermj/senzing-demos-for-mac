package query;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS;

public final class SearchEntities {
    private SearchEntities() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: query.SearchEntities '<search-criteria-json>'");
        }

        JsonObject criteria = QuerySupport.parseObject(args[0]);
        SzEnvironment environment = QuerySupport.openEnvironment(
            SearchEntities.class.getSimpleName());
        try {
            SzEngine engine = environment.getEngine();
            String response = engine.searchByAttributes(
                criteria.toString(), SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
            JsonObject result = QuerySupport.parseObject(response);
            JsonArray entities = QuerySupport.requireArray(
                result, "RESOLVED_ENTITIES");

            System.out.println("Search result entities: " + entities.size());
            for (int index = 0; index < entities.size(); index++) {
                JsonValue value = entities.get(index);
                if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                    throw new IllegalStateException(
                        "RESOLVED_ENTITIES contains a non-object item.");
                }
                JsonObject entry = (JsonObject) value;
                JsonObject entity = QuerySupport.requireObject(entry, "ENTITY");
                JsonObject resolved = QuerySupport.requireObject(
                    entity, "RESOLVED_ENTITY");
                JsonObject matchInfo = QuerySupport.requireObject(
                    entry, "MATCH_INFO");

                System.out.println(
                    "Entity " + QuerySupport.requiredLong(resolved, "ENTITY_ID")
                        + " | " + QuerySupport.optionalString(resolved, "ENTITY_NAME"));
                System.out.println(
                    "  match level: "
                        + QuerySupport.optionalString(matchInfo, "MATCH_LEVEL_CODE"));
                System.out.println(
                    "  match key: " + QuerySupport.optionalString(matchInfo, "MATCH_KEY"));
                System.out.println(
                    "  ER rule: " + QuerySupport.optionalString(matchInfo, "ERRULE_CODE"));
                System.out.println(
                    "  feature evidence: "
                        + QuerySupport.summarizeFeatureScores(matchInfo));
                printSources(QuerySupport.requireArray(resolved, "RECORD_SUMMARY"));
            }
        } finally {
            environment.destroy();
        }
    }

    private static void printSources(JsonArray summary) {
        if (summary.isEmpty()) {
            System.out.println("  RECORD_SUMMARY is present but empty.");
            return;
        }
        System.out.println("  sources:");
        for (JsonValue value : summary) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalStateException(
                    "RECORD_SUMMARY contains a non-object item.");
            }
            JsonObject source = (JsonObject) value;
            System.out.println(
                "    " + QuerySupport.requiredString(source, "DATA_SOURCE")
                    + " records="
                    + QuerySupport.requiredLong(source, "RECORD_COUNT"));
        }
    }
}
