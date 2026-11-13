// transform_web_orders.java -- Data Quality, Mapping, and Transformation: WEB_ORDERS mapper.
// Maps data/raw/web_orders.csv to Senzing JSON (one record per line) per the mapping validated by
// mapping_workflow step 3 (data/mapping/web_orders_mapping_spec.json; docs/mapping/web_orders_mapper.md):
//   DATA_SOURCE "WEB_ORDERS", RECORD_ID = order_id, RECORD_TYPE PERSON (own feature object)
//   first_name/last_name          -> one NAME object    (NAME_FIRST, NAME_LAST)
//   email                         -> EMAIL_ADDRESS
//   phone                         -> PHONE_NUMBER
//   street/city/state/zip         -> one ADDRESS object (ADDR_LINE1, ADDR_CITY, ADDR_STATE, ADDR_POSTAL_CODE)
//   order_date, order_total       -> root payload
// Values are emitted verbatim; blank values are omitted, and a feature object is written only
// when at least one of its attributes is present.
//
// Usage (after javac into src/build/classes):
//   src/scripts/run-java.sh TransformWebOrders <input.csv> -o <output.jsonl> [--sample N]
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TransformWebOrders {
    static final String DATA_SOURCE = "WEB_ORDERS";

    /** Maps one source row to a Senzing JSON record, or null when it has no RECORD_ID. */
    static Map<String, Object> map(Map<String, String> row) {
        if (!Records.present(row.get("order_id"))) { return null; }
        List<Object> features = new ArrayList<>();
        features.add(Map.of("RECORD_TYPE", "PERSON"));
        Mapper.addFeature(features, row, "first_name", "NAME_FIRST", "last_name", "NAME_LAST");
        Mapper.addFeature(features, row, "email", "EMAIL_ADDRESS");
        Mapper.addFeature(features, row, "phone", "PHONE_NUMBER");
        Mapper.addFeature(features, row, "street", "ADDR_LINE1", "city", "ADDR_CITY", "state", "ADDR_STATE",
                          "zip", "ADDR_POSTAL_CODE");
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("DATA_SOURCE", DATA_SOURCE);
        rec.put("RECORD_ID", row.get("order_id"));
        rec.put("FEATURES", features);
        for (String payload : List.of("order_date", "order_total")) {
            if (Records.present(row.get(payload))) { rec.put(payload, row.get(payload)); }
        }
        return rec;
    }

    public static void main(String[] args) throws Exception {
        Mapper.run("TransformWebOrders", args, TransformWebOrders::map);
    }
}
