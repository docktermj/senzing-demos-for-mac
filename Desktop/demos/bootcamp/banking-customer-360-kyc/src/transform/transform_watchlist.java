import java.util.List;

/**
 * Transformation program for WATCHLIST (internal screening list: fraud, sanctions, PEP).
 * Mapping: docs/mapping/WATCHLIST_mapper.md (Senzing mapping_workflow, 2026-11-14).
 * Reads the DOB-normalized copy data/raw/screening_list-improved.jsonl (Module 5 Phase 1 improve path).
 * Package-private class so the prescribed snake_case filename compiles (ground rules, INV-237).
 *
 *   src/scripts/run-java.sh data/temp/transform_classes TransformWatchlist \
 *       data/raw/screening_list-improved.jsonl -o data/senzing-ready/watchlist.jsonl [--sample N]
 */
class TransformWatchlist {
    static final String DATA_SOURCE = "WATCHLIST";
    static final List<String> PAYLOAD = List.of("LIST_TYPE", "LIST_STATUS", "LISTED_DATE");

    public static void main(String[] args) throws Exception {
        SenzingJsonPassThrough.run(args, DATA_SOURCE, PAYLOAD, SenzingJsonPassThrough.attributes(
            "RECORD_TYPE",
            "NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL",
            "DATE_OF_BIRTH", "GENDER",
            "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_COUNTRY",
            "PHONE_NUMBER", "EMAIL_ADDRESS",
            "SSN_NUMBER", "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE",
            "EMPLOYER"));
    }
}
