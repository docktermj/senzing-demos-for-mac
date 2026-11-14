import java.util.List;

/**
 * Transformation program for CREDIT_BUREAU (credit header records used for identity verification).
 * Mapping: docs/mapping/CREDIT_BUREAU_mapper.md (Senzing mapping_workflow, 2026-11-14).
 * Package-private class so the prescribed snake_case filename compiles (ground rules, INV-237).
 *
 *   src/scripts/run-java.sh data/temp/transform_classes TransformCreditBureau \
 *       data/raw/credit_header.jsonl -o data/senzing-ready/credit_bureau.jsonl [--sample N]
 */
class TransformCreditBureau {
    static final String DATA_SOURCE = "CREDIT_BUREAU";
    static final List<String> PAYLOAD = List.of("FILE_STATUS", "FILE_SINCE");

    public static void main(String[] args) throws Exception {
        SenzingJsonPassThrough.run(args, DATA_SOURCE, PAYLOAD, SenzingJsonPassThrough.attributes(
            "RECORD_TYPE",
            "NAME_TYPE", "NAME_FIRST", "NAME_LAST", "NAME_MIDDLE",
            "DATE_OF_BIRTH", "GENDER", "SSN_NUMBER",
            "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE",
            "PHONE_TYPE", "PHONE_NUMBER",
            // Record 3048 is the anchor CORE_BANKING 3050 points to (domain REFERENCE, role Carer).
            "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY"));
    }
}
