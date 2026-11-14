package transform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Quality, Mapping, and Transformation: transformer for the CREDIT_BUREAU source.
 *
 * Reads data/raw/credit_header.jsonl and writes Senzing JSON, one record per line, using the shared
 * FeatureListTransformer with this source's settings from the mapping that mapping_workflow
 * validated (docs/mapping/credit_bureau_mapper.md). Every record is PERSON in the source; the
 * transformer still takes RECORD_TYPE from each record rather than assuming it.
 *
 * Usage (from the project root):
 *   src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_credit_bureau.java
 *   src/scripts/sz-java.sh run transform.TransformCreditBureau <input.jsonl> -o <output.jsonl> [--sample N]
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class TransformCreditBureau {

    /** Feature attributes mapped regardless of record type (mapping spec field_mappings). */
    static final Set<String> FEATURE_ATTRS = Set.of(
        "NAME_TYPE", "DATE_OF_BIRTH", "SSN_NUMBER", "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY",
        "ADDR_STATE", "ADDR_POSTAL_CODE", "PHONE_TYPE", "PHONE_NUMBER", "GENDER",
        "REL_ANCHOR_DOMAIN", "REL_ANCHOR_KEY");

    /** Payload fields, copied to the root in this order when present. */
    static final List<String> PAYLOAD = List.of("FILE_SINCE", "FILE_STATUS");

    /** No record-type decisions were needed for this source. */
    static final Map<String, String> RECORD_TYPE_DECISIONS = Map.of();

    static final FeatureListTransformer TRANSFORMER =
        new FeatureListTransformer("CREDIT_BUREAU", FEATURE_ATTRS, PAYLOAD, RECORD_TYPE_DECISIONS);

    public static void main(String[] args) throws IOException {
        TRANSFORMER.main("TransformCreditBureau", args);
    }
}
