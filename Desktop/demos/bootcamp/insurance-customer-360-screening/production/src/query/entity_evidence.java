package query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import server.Json;

/**
 * Retrieve and format resolved entities as evidence for a quality review: each record with its source, its
 * key identifying values and the match key that joined it; and Senzing's whyEntities explanation for a pair.
 * This is an analyst view of everything in the entity, so it is not subject to the customer 360's privacy rule.
 *
 * Usage:
 *   src/scripts/sz-java.sh run query.EntityEvidence entity <entityId> [<entityId> ...]
 *   src/scripts/sz-java.sh run query.EntityEvidence why <entityId1> <entityId2>
 * The top-level class is package-private so it may live in this snake_case file.
 */
class EntityEvidence {
    static final List<String> SHOW = List.of("NAME_FULL", "NAME_ORG", "NAME_FIRST", "NAME_MIDDLE", "NAME_LAST",
        "DATE_OF_BIRTH", "SSN_NUMBER", "TAX_ID_NUMBER", "PASSPORT_NUMBER", "ADDR_LINE1", "ADDR_CITY", "PHONE_NUMBER",
        "EMAIL_ADDRESS");

    public static void main(String[] args) throws Exception {
        try (InsuranceModel m = InsuranceModel.open(EntityEvidence.class.getSimpleName())) {
            if (args[0].equals("why")) {
                System.out.println(Json.write(m.why(Long.parseLong(args[1]), Long.parseLong(args[2]))));
                return;
            }
            for (int i = 1; i < args.length; i++) {
                Map<String, Object> e = m.entity(Long.parseLong(args[i]));
                System.out.println("ENTITY " + InsuranceModel.entityId(e) + " " + InsuranceModel.entityName(e)
                    + " " + InsuranceModel.sources(e));
                for (Map<String, Object> r : InsuranceModel.records(e)) {
                    Map<String, Object> vals = new LinkedHashMap<>();
                    for (Object fo : Json.arr(InsuranceModel.jsonData(r).get("FEATURES"))) {
                        for (Map.Entry<String, Object> kv : Json.obj(fo).entrySet()) {
                            if (SHOW.contains(kv.getKey())) vals.merge(kv.getKey(), kv.getValue(), (a, b) -> a + " | " + b);
                        }
                    }
                    System.out.println("   " + r.get("DATA_SOURCE") + ":" + r.get("RECORD_ID") + "  key="
                        + r.get("MATCH_KEY") + "  rule=" + r.get("ERRULE_CODE") + "  " + Json.write(vals));
                }
            }
        }
    }
}
