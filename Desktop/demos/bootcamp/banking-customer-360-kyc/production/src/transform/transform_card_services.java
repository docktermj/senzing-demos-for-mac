import java.util.List;

/**
 * Transformation program for CARD_SERVICES (card platform cardholder master).
 * Mapping: docs/mapping/CARD_SERVICES_mapper.md (Senzing mapping_workflow, 2026-11-14).
 * Package-private class so the prescribed snake_case filename compiles (ground rules, INV-237).
 *
 *   src/scripts/run-java.sh data/temp/transform_classes TransformCardServices \
 *       data/raw/card_cardholder_master.jsonl -o data/senzing-ready/card_services.jsonl [--sample N]
 */
class TransformCardServices {
    static final String DATA_SOURCE = "CARD_SERVICES";
    static final List<String> PAYLOAD = List.of("CARD_ACCOUNT_ID", "PAN_LAST4", "CARDHOLDER_ROLE", "PRODUCT",
        "CREDIT_LIMIT", "CURRENT_BALANCE", "OPEN_DATE", "ACCOUNT_STATUS", "DELINQUENCY_BUCKET");

    public static void main(String[] args) throws Exception {
        SenzingJsonPassThrough.run(args, DATA_SOURCE, PAYLOAD, SenzingJsonPassThrough.attributes(
            "RECORD_TYPE",
            "NAME_TYPE", "NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL", "NAME_ORG",
            "DATE_OF_BIRTH", "GENDER",
            "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_FULL", "ADDR_COUNTRY",
            "PHONE_TYPE", "PHONE_NUMBER", "EMAIL_ADDRESS",
            "SSN_NUMBER", "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE",
            "PASSPORT_NUMBER", "PASSPORT_COUNTRY", "NATIONAL_ID_NUMBER", "NATIONAL_ID_COUNTRY",
            "TAX_ID_NUMBER", "TAX_ID_COUNTRY", "TAX_ID_TYPE", "NPI_NUMBER",
            "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY", "REL_POINTER_DOMAIN", "REL_POINTER_KEY", "REL_POINTER_ROLE"));
    }
}
