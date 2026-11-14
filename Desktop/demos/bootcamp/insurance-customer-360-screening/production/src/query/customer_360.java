package query;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import server.Json;

/**
 * Query requirement 4: the customer 360, one profile per real customer (the master list).
 *
 * Each profile shows every P&C policy and life or annuity contract, the totals, the cross-sell gap, the
 * duplicates inside each system, the links to other customers, and the record keys with the match key that
 * joined each record. It never shows SIU, screening list, credit bureau or business registry data.
 *
 * Usage:
 *   src/scripts/sz-java.sh run query.Customer360                      write the master list
 *   src/scripts/sz-java.sh run query.Customer360 <SOURCE> <RECORD_ID> print one customer's profile
 * Writes data/query/customer_master_list.json.
 * The top-level class is package-private so it may live in this snake_case file.
 */
class Customer360 {
    public static void main(String[] args) throws Exception {
        try (InsuranceModel m = InsuranceModel.open(Customer360.class.getSimpleName())) {
            if (args.length == 2) {
                System.out.println(Json.write(m.customerProfile(m.entityByRecord(args[0], args[1]))));
                return;
            }
            List<Object> list = new ArrayList<>();
            for (Map<String, Object> e : m.customers().values()) list.add(m.customerProfile(e));
            InsuranceModel.writeJson(Path.of("data", "query", "customer_master_list.json"), list);
            System.out.println("PROFILES=" + list.size());
            System.out.println("WROTE data/query/customer_master_list.json");
        }
    }
}
