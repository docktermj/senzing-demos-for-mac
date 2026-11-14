package transform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Quality, Mapping, and Transformation: transformer for the LIFE_ANNUITY_ADMIN source.
 *
 * Reads data/raw/life_annuity_admin_extract.jsonl and writes Senzing JSON, one record per line,
 * using the shared FeatureListTransformer with this source's settings from the mapping that
 * mapping_workflow validated (docs/mapping/life_annuity_admin_mapper.md).
 *
 * Usage (from the project root):
 *   src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_life_annuity_admin.java
 *   src/scripts/sz-java.sh run transform.TransformLifeAnnuityAdmin <input.jsonl> -o <output.jsonl> [--sample N]
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class TransformLifeAnnuityAdmin {

    /** Feature attributes mapped regardless of record type (mapping spec field_mappings). */
    static final Set<String> FEATURE_ATTRS = Set.of(
        "DATE_OF_BIRTH", "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE",
        "ADDR_COUNTRY", "ADDR_FULL", "PHONE_TYPE", "PHONE_NUMBER", "EMAIL_ADDRESS",
        "PASSPORT_NUMBER", "PASSPORT_COUNTRY", "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE",
        "SSN_NUMBER", "GENDER", "NATIONAL_ID_NUMBER", "NATIONAL_ID_COUNTRY", "TAX_ID_NUMBER",
        "TAX_ID_TYPE", "TAX_ID_COUNTRY", "NPI_NUMBER", "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY",
        "REL_POINTER_DOMAIN", "REL_POINTER_KEY", "REL_POINTER_ROLE");

    /** Payload fields, copied to the root in this order when present. */
    static final List<String> PAYLOAD = List.of(
        "CONTRACT_NUMBER", "PRODUCT_TYPE", "PARTY_ROLE", "ISSUE_DATE", "CONTRACT_STATUS",
        "FACE_AMOUNT", "MODAL_PREMIUM", "PREMIUM_MODE", "ACCOUNT_VALUE", "CUSTOMER_TIER");

    /** No record in this source states both name kinds, so no record-type decisions were needed. */
    static final Map<String, String> RECORD_TYPE_DECISIONS = Map.of();

    static final FeatureListTransformer TRANSFORMER =
        new FeatureListTransformer("LIFE_ANNUITY_ADMIN", FEATURE_ATTRS, PAYLOAD, RECORD_TYPE_DECISIONS);

    public static void main(String[] args) throws IOException {
        TRANSFORMER.main("TransformLifeAnnuityAdmin", args);
    }
}
