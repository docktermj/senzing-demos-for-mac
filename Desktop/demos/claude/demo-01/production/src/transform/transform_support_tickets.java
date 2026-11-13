// transform_support_tickets.java -- Data Quality, Mapping, and Transformation: SUPPORT_TICKETS mapper.
// Maps data/raw/support_tickets.jsonl to Senzing JSON (one record per line) per the mapping validated
// by mapping_workflow step 3 (data/mapping/support_tickets_mapping_spec.json;
// docs/mapping/support_tickets_mapper.md):
//   DATA_SOURCE "SUPPORT_TICKETS", RECORD_ID = ticket_id, RECORD_TYPE PERSON (the requester)
//   requester_name     -> NAME_FULL     (single name field: not split)
//   requester_email    -> EMAIL_ADDRESS
//   phone              -> PHONE_NUMBER
//   city/postal_code   -> one ADDRESS object (ADDR_CITY, ADDR_POSTAL_CODE)
//   topic, opened_on   -> root payload
// The source is sparse (absent values are omitted keys); values are emitted verbatim, blanks
// omitted, and a feature object is written only when at least one of its attributes is present.
//
// Usage (after javac into src/build/classes):
//   src/scripts/run-java.sh TransformSupportTickets <input.jsonl> -o <output.jsonl> [--sample N]
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TransformSupportTickets {
    static final String DATA_SOURCE = "SUPPORT_TICKETS";

    /** Maps one source row to a Senzing JSON record, or null when it has no RECORD_ID. */
    static Map<String, Object> map(Map<String, String> row) {
        if (!Records.present(row.get("ticket_id"))) { return null; }
        List<Object> features = new ArrayList<>();
        features.add(Map.of("RECORD_TYPE", "PERSON"));
        Mapper.addFeature(features, row, "requester_name", "NAME_FULL");
        Mapper.addFeature(features, row, "requester_email", "EMAIL_ADDRESS");
        Mapper.addFeature(features, row, "phone", "PHONE_NUMBER");
        Mapper.addFeature(features, row, "city", "ADDR_CITY", "postal_code", "ADDR_POSTAL_CODE");
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("DATA_SOURCE", DATA_SOURCE);
        rec.put("RECORD_ID", row.get("ticket_id"));
        rec.put("FEATURES", features);
        for (String payload : List.of("topic", "opened_on")) {
            if (Records.present(row.get(payload))) { rec.put(payload, row.get(payload)); }
        }
        return rec;
    }

    public static void main(String[] args) throws Exception {
        Mapper.run("TransformSupportTickets", args, TransformSupportTickets::map);
    }
}
