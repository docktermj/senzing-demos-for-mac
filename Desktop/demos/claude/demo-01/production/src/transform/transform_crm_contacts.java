// transform_crm_contacts.java -- Data Quality, Mapping, and Transformation: CRM_CONTACTS mapper.
// Maps data/raw/crm_contacts.csv to Senzing JSON (one record per line) per the mapping validated by
// mapping_workflow step 3 (data/mapping/crm_contacts_mapping_spec.json; docs/mapping/crm_contacts_mapper.md):
//   DATA_SOURCE "CRM_CONTACTS", RECORD_ID = contact_id, RECORD_TYPE PERSON (own feature object)
//   full_name     -> NAME_FULL     (single name field: not split)
//   email         -> EMAIL_ADDRESS
//   phone         -> PHONE_NUMBER
//   address       -> ADDR_FULL     (single free-text address: not split; Senzing parses it)
//   created_date  -> root payload
// Values are emitted verbatim; blank values are omitted.
//
// Usage (after javac into src/build/classes):
//   src/scripts/run-java.sh TransformCrmContacts <input.csv> -o <output.jsonl> [--sample N]
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TransformCrmContacts {
    static final String DATA_SOURCE = "CRM_CONTACTS";

    /** Maps one source row to a Senzing JSON record, or null when it has no RECORD_ID. */
    static Map<String, Object> map(Map<String, String> row) {
        if (!Records.present(row.get("contact_id"))) { return null; }
        List<Object> features = new ArrayList<>();
        features.add(Map.of("RECORD_TYPE", "PERSON"));
        Mapper.addFeature(features, row, "full_name", "NAME_FULL");
        Mapper.addFeature(features, row, "email", "EMAIL_ADDRESS");
        Mapper.addFeature(features, row, "phone", "PHONE_NUMBER");
        Mapper.addFeature(features, row, "address", "ADDR_FULL");
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("DATA_SOURCE", DATA_SOURCE);
        rec.put("RECORD_ID", row.get("contact_id"));
        rec.put("FEATURES", features);
        if (Records.present(row.get("created_date"))) { rec.put("created_date", row.get("created_date")); }
        return rec;
    }

    public static void main(String[] args) throws Exception {
        Mapper.run("TransformCrmContacts", args, TransformCrmContacts::map);
    }
}
