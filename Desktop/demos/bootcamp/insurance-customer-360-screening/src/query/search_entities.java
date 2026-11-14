package query;

import server.Json;

/**
 * Search the resolved entities by name (searchByAttributes; NAME_FULL then NAME_ORG, merged by entity ID).
 * Used for requirement 8: common names such as John Smith and Robert Smith return several distinct people.
 *
 * Usage: src/scripts/sz-java.sh run query.SearchEntities "<name>" ["<name>" ...]
 * The top-level class is package-private so it may live in this snake_case file.
 */
class SearchEntities {
    public static void main(String[] args) throws Exception {
        try (InsuranceModel m = InsuranceModel.open(SearchEntities.class.getSimpleName())) {
            for (String name : args) System.out.println(Json.write(m.searchName(name)));
        }
    }
}
