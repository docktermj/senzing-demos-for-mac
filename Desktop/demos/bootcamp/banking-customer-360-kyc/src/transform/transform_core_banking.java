import java.util.List;

/**
 * Transformation program for CORE_BANKING (core banking CIF extract).
 * Mapping: docs/mapping/CORE_BANKING_mapper.md (Senzing mapping_workflow, 2026-11-14).
 * Package-private class so the prescribed snake_case filename compiles (ground rules, INV-237).
 *
 *   src/scripts/run-java.sh data/temp/transform_classes TransformCoreBanking \
 *       data/raw/core_cif_extract.jsonl -o data/senzing-ready/core_banking.jsonl [--sample N]
 */
class TransformCoreBanking {
    static final String DATA_SOURCE = "CORE_BANKING";
    static final List<String> PAYLOAD = List.of("ACCOUNT_NUMBER", "PRODUCT_TYPE", "OWNERSHIP_TYPE",
        "ACCOUNT_STATUS", "OPEN_DATE", "CURRENT_BALANCE", "BRANCH", "RELATIONSHIP_TIER");

    public static void main(String[] args) throws Exception {
        SenzingJsonPassThrough.run(args, DATA_SOURCE, PAYLOAD, SenzingJsonPassThrough.attributes(
            "RECORD_TYPE",
            "NAME_TYPE", "NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL", "NAME_ORG",
            "DATE_OF_BIRTH", "GENDER",
            "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_FULL", "ADDR_COUNTRY",
            "PHONE_TYPE", "PHONE_NUMBER", "EMAIL_ADDRESS",
            "SSN_NUMBER", "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE",
            "PASSPORT_NUMBER", "PASSPORT_COUNTRY", "NATIONAL_ID_NUMBER", "NATIONAL_ID_COUNTRY",
            "TAX_ID_NUMBER", "TAX_ID_COUNTRY", "TAX_ID_TYPE",
            "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY", "REL_POINTER_DOMAIN", "REL_POINTER_KEY", "REL_POINTER_ROLE"),
            // ACCOUNT_NUMBER is a reserved Senzing attribute: at the root, any key ENDING in it is read as a
            // (labelled) ACCOUNT feature, so the payload copy is named DEPOSIT_ACCOUNT_ID.
            java.util.Map.of("ACCOUNT_NUMBER", "DEPOSIT_ACCOUNT_ID"),
            java.util.Map.of("3025", TransformCoreBanking::record3025AsOrganization));
    }

    /**
     * Bootcamper decision, 2026-11-14 (docs/mapping/CORE_BANKING_mapper.md, "Record corrections"):
     * record 3025 has no RECORD_TYPE and carries NAME_ORG "PRAVENVGUPTA" plus NAME_FULL "WONDERLAND".
     * Its account is OWNERSHIP_TYPE Business, it carries a tax ID, and CARD_SERVICES 3024 (same tax ID
     * and date) names WONDERLAND as an organization, so the names are treated as swapped: the record
     * becomes ORGANIZATION with NAME_ORG WONDERLAND (PRIMARY) and NAME_ORG PRAVENVGUPTA (AKA). Values
     * are moved, never rewritten.
     */
    static java.util.List<Object> record3025AsOrganization(java.util.List<Object> features) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        java.util.Map<String, Object> type = new java.util.LinkedHashMap<>();
        type.put("RECORD_TYPE", "ORGANIZATION");
        out.add(type);
        java.util.List<Object> rest = new java.util.ArrayList<>();
        String primary = null, alias = null;
        for (Object o : features) {
            java.util.Map<String, Object> f = MiniJson.obj(o);
            if (f.containsKey("NAME_FULL")) primary = String.valueOf(f.get("NAME_FULL"));
            else if (f.containsKey("NAME_ORG")) alias = String.valueOf(f.get("NAME_ORG"));
            else if (!f.containsKey("RECORD_TYPE")) rest.add(f);
        }
        if (primary != null) out.add(name(primary, "PRIMARY"));
        if (alias != null) out.add(name(alias, "AKA"));
        out.addAll(rest);
        return out;
    }

    private static java.util.Map<String, Object> name(String value, String type) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("NAME_TYPE", type);
        m.put("NAME_ORG", value);
        return m;
    }
}
