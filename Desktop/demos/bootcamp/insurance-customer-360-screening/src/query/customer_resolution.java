package query;

import java.nio.file.Path;
import java.util.Map;

import server.Json;

/**
 * Query requirements 1-3: one resolution of all six sources, the customer collapse (179 customer records into
 * the real customers behind them, with the duplicates inside each system) and the cross-line count.
 *
 * It walks every PC_POLICY_ADMIN and LIFE_ANNUITY_ADMIN record we loaded and asks Senzing for that record's
 * entity (getEntity(SzRecordKey, flags), see InsuranceModel), then folds on the entity ID.
 *
 * Usage: src/scripts/sz-java.sh run query.CustomerResolution
 * Writes data/query/customer_resolution.json.
 * The top-level class is package-private so it may live in this snake_case file.
 */
class CustomerResolution {
    public static void main(String[] args) throws Exception {
        try (InsuranceModel m = InsuranceModel.open(CustomerResolution.class.getSimpleName())) {
            Map<String, Object> s = m.resolutionSummary();
            InsuranceModel.writeJson(Path.of("data", "query", "customer_resolution.json"), s);
            for (Map.Entry<String, Object> e : s.entrySet()) {
                if (e.getKey().equals("both_lines_customers")) continue;
                System.out.println(e.getKey() + " = " + Json.write(e.getValue()));
            }
            System.out.println("WROTE data/query/customer_resolution.json");
        }
    }
}
