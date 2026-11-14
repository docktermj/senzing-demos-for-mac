package transform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Quality, Mapping, and Transformation: transformer for the SIU_CASES source.
 *
 * Reads data/raw/siu_cases.jsonl and writes Senzing JSON, one record per line, using the shared
 * FeatureListTransformer with this source's settings from the mapping that mapping_workflow
 * validated (docs/mapping/siu_cases_mapper.md). The six case fields travel as payload so a
 * resolved entity shows its investigation history.
 *
 * Usage (from the project root):
 *   src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_siu_cases.java
 *   src/scripts/sz-java.sh run transform.TransformSiuCases <input.jsonl> -o <output.jsonl> [--sample N]
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class TransformSiuCases {

    /** Feature attributes mapped regardless of record type (mapping spec field_mappings). */
    static final Set<String> FEATURE_ATTRS = Set.of(
        "DATE_OF_BIRTH", "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_POSTAL_CODE",
        "DRIVERS_LICENSE_NUMBER", "DRIVERS_LICENSE_STATE", "EMAIL_ADDRESS", "SSN_NUMBER", "GENDER");

    /** Payload fields, copied to the root in this order when present. */
    static final List<String> PAYLOAD = List.of(
        "SIU_CASE_NUMBER", "REFERRAL_DATE", "FRAUD_TYPE", "ROLE_IN_CASE", "LINE_OF_BUSINESS",
        "CASE_DISPOSITION");

    /** No record-type decisions were needed for this source. */
    static final Map<String, String> RECORD_TYPE_DECISIONS = Map.of();

    static final FeatureListTransformer TRANSFORMER =
        new FeatureListTransformer("SIU_CASES", FEATURE_ATTRS, PAYLOAD, RECORD_TYPE_DECISIONS);

    public static void main(String[] args) throws IOException {
        TRANSFORMER.main("TransformSiuCases", args);
    }
}
