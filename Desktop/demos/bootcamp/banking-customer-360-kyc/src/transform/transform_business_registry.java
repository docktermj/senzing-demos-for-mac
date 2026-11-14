import java.util.List;
import java.util.Map;

/**
 * Transformation program for BUSINESS_REGISTRY (registrations with owners, officers and parent
 * companies, linked by disclosed relationships).
 * Mapping: docs/mapping/BUSINESS_REGISTRY_mapper.md (Senzing mapping_workflow, 2026-11-14).
 * Package-private class so the prescribed snake_case filename compiles (ground rules, INV-237).
 *
 *   src/scripts/run-java.sh data/temp/transform_classes TransformBusinessRegistry \
 *       data/raw/business_registry.jsonl -o data/senzing-ready/business_registry.jsonl [--sample N]
 */
class TransformBusinessRegistry {
    static final String DATA_SOURCE = "BUSINESS_REGISTRY";
    static final List<String> PAYLOAD = List.of("REGISTRY_ROLE", "REGISTRY_STATUS");

    public static void main(String[] args) throws Exception {
        SenzingJsonPassThrough.run(args, DATA_SOURCE, PAYLOAD, SenzingJsonPassThrough.attributes(
            "RECORD_TYPE",
            // Person names on PERSON records, NAME_ORG on ORGANIZATION records, as the source types them.
            "NAME_TYPE", "NAME_FULL", "NAME_FIRST", "NAME_LAST", "NAME_ORG",
            "DATE_OF_BIRTH",
            "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_COUNTRY", "ADDR_FULL",
            "PHONE_TYPE", "PHONE_NUMBER", "EMAIL_ADDRESS", "EMPLOYER",
            "NATIONAL_ID_NUMBER", "NATIONAL_ID_TYPE", "NATIONAL_ID_COUNTRY",
            "TAX_ID_NUMBER", "TAX_ID_TYPE", "TAX_ID_COUNTRY", "LEI_NUMBER",
            "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY", "REL_POINTER_DOMAIN", "REL_POINTER_KEY", "REL_POINTER_ROLE"),
            Map.of(), Map.of(),
            // Incorporation year (ORGANIZATION records only) is the organization's registration date.
            Map.of("INCORPORATED", "REGISTRATION_DATE"));
    }
}
