package transform;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Quality, Mapping, and Transformation: transformer for the SANCTIONS_PEP source.
 *
 * Reads data/raw/sanctions_pep.jsonl and writes Senzing JSON, one record per line, using the shared
 * FeatureListTransformer with this source's settings from the mapping that mapping_workflow
 * validated (docs/mapping/sanctions_pep_mapper.md). Names in non-Latin scripts are passed through
 * unchanged; the list fields travel as payload so a screening hit shows which list matched.
 *
 * Usage (from the project root):
 *   src/scripts/sz-java.sh compile src/server/Json.java src/transform/FeatureListTransformer.java src/transform/transform_sanctions_pep.java
 *   src/scripts/sz-java.sh run transform.TransformSanctionsPep <input.jsonl> -o <output.jsonl> [--sample N]
 *
 * The top-level class is package-private so it may live in this snake_case file.
 */
class TransformSanctionsPep {

    /** Feature attributes mapped regardless of record type (mapping spec field_mappings). */
    static final Set<String> FEATURE_ATTRS = Set.of(
        "ADDR_TYPE", "ADDR_LINE1", "ADDR_CITY", "ADDR_STATE", "ADDR_COUNTRY", "PHONE_NUMBER", "EMPLOYER");

    /** Payload fields, copied to the root in this order when present. */
    static final List<String> PAYLOAD = List.of(
        "LIST_TYPE", "LIST_NAME", "LISTED_DATE", "SANCTIONS_PROGRAM", "PEP_POSITION", "PEP_COUNTRY");

    /** No record-type decisions were needed for this source. */
    static final Map<String, String> RECORD_TYPE_DECISIONS = Map.of();

    static final FeatureListTransformer TRANSFORMER =
        new FeatureListTransformer("SANCTIONS_PEP", FEATURE_ATTRS, PAYLOAD, RECORD_TYPE_DECISIONS);

    public static void main(String[] args) throws IOException {
        TRANSFORMER.main("TransformSanctionsPep", args);
    }
}
