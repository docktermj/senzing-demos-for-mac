package transform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Quality, Mapping, and Transformation: transformer for the BUSINESS_REGISTRY source.
 *
 * Reads data/raw/business_registry.jsonl and writes Senzing JSON, one record per line, using the
 * shared FeatureListTransformer with this source's settings from the mapping that mapping_workflow
 * validated (docs/mapping/business_registry_mapper.md). The root INCORPORATED year is emitted as a
 * REGISTRATION_DATE feature (organizations only carry it).
 *
 * Usage (from the project root):
 *   src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_business_registry.java
 *   src/scripts/sz-java.sh run transform.TransformBusinessRegistry <input.jsonl> -o <output.jsonl> [--sample N]
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class TransformBusinessRegistry {

    /** Feature attributes mapped regardless of record type (mapping spec field_mappings). */
    static final Set<String> FEATURE_ATTRS = Set.of(
        "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE", "ADDR_COUNTRY",
        "ADDR_FULL", "NATIONAL_ID_NUMBER", "NATIONAL_ID_TYPE", "NATIONAL_ID_COUNTRY", "TAX_ID_NUMBER",
        "TAX_ID_TYPE", "TAX_ID_COUNTRY", "LEI_NUMBER", "DATE_OF_BIRTH", "PHONE_NUMBER", "PHONE_TYPE",
        "EMAIL_ADDRESS", "EMPLOYER", "NAME_TYPE", "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY",
        "REL_POINTER_DOMAIN", "REL_POINTER_KEY", "REL_POINTER_ROLE");

    /** Payload fields, copied to the root in this order when present. */
    static final List<String> PAYLOAD = List.of("REGISTRY_ROLE", "REGISTRY_STATUS");

    /** Root fields emitted as features. */
    static final Map<String, String> ROOT_FEATURES = Map.of("INCORPORATED", "REGISTRATION_DATE");

    /** No record-type decisions were needed for this source. */
    static final Map<String, String> RECORD_TYPE_DECISIONS = Map.of();

    static final FeatureListTransformer TRANSFORMER = new FeatureListTransformer(
        "BUSINESS_REGISTRY", FEATURE_ATTRS, PAYLOAD, RECORD_TYPE_DECISIONS, ROOT_FEATURES);

    public static void main(String[] args) throws IOException {
        TRANSFORMER.main("TransformBusinessRegistry", args);
    }
}
