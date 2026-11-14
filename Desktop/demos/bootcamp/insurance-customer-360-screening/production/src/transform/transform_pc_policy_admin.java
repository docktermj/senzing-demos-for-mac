package transform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Quality, Mapping, and Transformation: transformer for the PC_POLICY_ADMIN source.
 *
 * Reads data/raw/pc_policy_admin_extract.jsonl and writes Senzing JSON, one record per line, using
 * the shared FeatureListTransformer with this source's settings from the mapping that
 * mapping_workflow validated (docs/mapping/pc_policy_admin_mapper.md).
 *
 * Usage (from the project root):
 *   src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_pc_policy_admin.java
 *   src/scripts/sz-java.sh run transform.TransformPcPolicyAdmin <input.jsonl> -o <output.jsonl> [--sample N]
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class TransformPcPolicyAdmin {

    /** Feature attributes mapped regardless of record type (mapping spec field_mappings). */
    static final Set<String> FEATURE_ATTRS = Set.of(
        "DATE_OF_BIRTH", "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE",
        "ADDR_FULL", "ADDR_COUNTRY", "PHONE_TYPE", "PHONE_NUMBER", "EMAIL_ADDRESS",
        "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE", "SSN_NUMBER", "GENDER",
        "PASSPORT_NUMBER", "PASSPORT_COUNTRY", "NATIONAL_ID_NUMBER", "NATIONAL_ID_COUNTRY",
        "NAME_TYPE", "TAX_ID_NUMBER", "TAX_ID_COUNTRY", "TAX_ID_TYPE", "NPI_NUMBER",
        "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY", "REL_POINTER_DOMAIN", "REL_POINTER_KEY",
        "REL_POINTER_ROLE");

    /** Payload fields, copied to the root in this order when present. */
    static final List<String> PAYLOAD = List.of(
        "POLICY_NUMBER", "LINE_OF_BUSINESS", "INSURED_ROLE", "WRITTEN_PREMIUM", "EFFECTIVE_DATE",
        "EXPIRATION_DATE", "POLICY_STATUS", "PRODUCER_CODE", "CANCELLATION_REASON", "CUSTOMER_TIER");

    /** Record-type decisions the bootcamper made (docs/mapping/pc_policy_admin_mapper.md). */
    static final Map<String, String> RECORD_TYPE_DECISIONS = Map.of(
        // 3025: Businessowners policy (CP- number) with a tax ID; the source states NAME_ORG
        // "PRAVENVGUPTA" and NAME_FULL "WONDERLAND". Bootcamper decided ORGANIZATION (2026-11-14).
        "3025", "ORGANIZATION");

    static final FeatureListTransformer TRANSFORMER =
        new FeatureListTransformer("PC_POLICY_ADMIN", FEATURE_ATTRS, PAYLOAD, RECORD_TYPE_DECISIONS);

    public static void main(String[] args) throws IOException {
        TRANSFORMER.main("TransformPcPolicyAdmin", args);
    }
}
