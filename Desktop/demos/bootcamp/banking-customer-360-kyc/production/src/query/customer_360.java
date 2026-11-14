// Customer 360 for relationship managers (Query, Visualize and Discover module, requirements 1-6).
//
// One profile per real customer: every resolved entity that holds a CORE_BANKING or CARD_SERVICES
// record. For each: deposit accounts and cards (deduplicated by account), total deposits, total card
// credit and card balance, duplicates inside each system, the cross-sell gap, household and ownership
// links Senzing found, and a yes/no for whether a screening-list, credit bureau or business registry
// record exists. Per the business problem, the 360 never shows those records' contents.
//
// Senzing access is through ResolvedEntities (getEntity per record; see that file for flags and
// sources). Writes data/results/customer_360.json and docs/results/customer_master_list.csv.
// Run: src/scripts/run-java.sh data/temp/query_classes Customer360
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

class Customer360 {
    public static void main(String[] args) throws Exception {
        List<Map<String, Object>> profiles = new ArrayList<>();
        int both = 0, depositsOnly = 0, cardOnly = 0, dupCore = 0, dupCard = 0, customerRecords = 0;
        try (ResolvedEntities re = ResolvedEntities.load("Customer360")) {
            for (long eid : re.entities.keySet()) {
                List<Map<String, Object>> core = re.sourceRecordsOf(eid, "CORE_BANKING");
                List<Map<String, Object>> card = re.sourceRecordsOf(eid, "CARD_SERVICES");
                if (core.isEmpty() && card.isEmpty()) continue;
                customerRecords += core.size() + card.size();

                Map<String, Map<String, Object>> deposits = new LinkedHashMap<>();
                for (Map<String, Object> r : core) {
                    String acct = MiniJson.str(r.get("DEPOSIT_ACCOUNT_ID"));
                    Map<String, Object> d = deposits.computeIfAbsent(acct, k -> new LinkedHashMap<>());
                    d.put("account", acct);
                    for (String f : List.of("PRODUCT_TYPE", "OWNERSHIP_TYPE", "ACCOUNT_STATUS", "OPEN_DATE", "CURRENT_BALANCE", "BRANCH", "RELATIONSHIP_TIER"))
                        if (r.get(f) != null) d.put(f.toLowerCase(), r.get(f));
                    addTo(d, "records", "CORE_BANKING/" + r.get("RECORD_ID"));
                }
                Map<String, Map<String, Object>> cards = new LinkedHashMap<>();
                for (Map<String, Object> r : card) {
                    String acct = MiniJson.str(r.get("CARD_ACCOUNT_ID"));
                    Map<String, Object> c = cards.computeIfAbsent(acct, k -> new LinkedHashMap<>());
                    c.put("card_account", acct);
                    for (String f : List.of("PAN_LAST4", "CARDHOLDER_ROLE", "PRODUCT", "CREDIT_LIMIT", "CURRENT_BALANCE", "ACCOUNT_STATUS", "DELINQUENCY_BUCKET", "OPEN_DATE"))
                        if (r.get(f) != null) c.put(f.toLowerCase(), r.get(f));
                    addTo(c, "records", "CARD_SERVICES/" + r.get("RECORD_ID"));
                }
                double totalDeposits = 0, cardCredit = 0, cardBalance = 0;
                for (Map<String, Object> d : deposits.values()) totalDeposits += ResolvedEntities.num(d.get("current_balance"));
                for (Map<String, Object> c : cards.values()) { cardCredit += ResolvedEntities.num(c.get("credit_limit")); cardBalance += ResolvedEntities.num(c.get("current_balance")); }

                String gap = !deposits.isEmpty() && !cards.isEmpty() ? "has both" : deposits.isEmpty() ? "card, no deposit account" : "deposits, no card";
                if (gap.equals("has both")) both++; else if (cards.isEmpty()) depositsOnly++; else cardOnly++;
                if (core.size() > 1) dupCore++;
                if (card.size() > 1) dupCard++;

                List<Map<String, Object>> links = new ArrayList<>();
                for (Map<String, Object> rel : re.related(eid)) {
                    long rid = ((Number) rel.get("ENTITY_ID")).longValue();
                    String level = MiniJson.str(rel.get("MATCH_LEVEL_CODE"));
                    String mk = MiniJson.str(rel.get("MATCH_KEY"));
                    Set<String> relSources = re.sources(rid);
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("entity_id", rid);
                    l.put("name", MiniJson.str(rel.get("ENTITY_NAME")));
                    l.put("match_level", level);
                    l.put("match_key", mk);
                    boolean disclosed = rel.get("IS_DISCLOSED") instanceof Number && ((Number) rel.get("IS_DISCLOSED")).intValue() == 1;
                    if (disclosed) {
                        String[] roles = ResolvedEntities.roles(mk);
                        l.put("kind", "declared relationship");
                        if (!roles[0].isEmpty()) l.put("this_customer_is", roles[0]);
                        if (!roles[1].isEmpty()) l.put("related_party_is", roles[1]);
                    } else if (!Collections.disjoint(relSources, Set.of("CORE_BANKING", "CARD_SERVICES"))) {
                        l.put("kind", "POSSIBLY_SAME".equals(level) ? "possibly the same customer" : "possible household or associate");
                    } else {
                        continue;   // links to screening, bureau or registry-only entities are not shown in the 360
                    }
                    links.add(l);
                }

                Set<String> srcs = re.sources(eid);
                Map<String, Object> other = new LinkedHashMap<>();
                other.put("screening_list_record_exists", srcs.contains("WATCHLIST"));
                other.put("credit_bureau_record_exists", srcs.contains("CREDIT_BUREAU"));
                other.put("business_registry_record_exists", srcs.contains("BUSINESS_REGISTRY"));

                Map<String, Object> p = new LinkedHashMap<>();
                p.put("entity_id", eid);
                p.put("name", re.name(eid));
                p.put("type", re.recordType(eid));
                p.put("deposit_accounts", new ArrayList<>(deposits.values()));
                p.put("cards", new ArrayList<>(cards.values()));
                p.put("total_deposits", round(totalDeposits));
                p.put("total_card_credit", round(cardCredit));
                p.put("total_card_balance", round(cardBalance));
                p.put("cross_sell", gap);
                p.put("duplicate_records_in_core_banking", core.size() > 1 ? core.size() : 0);
                p.put("duplicate_records_in_card_services", card.size() > 1 ? card.size() : 0);
                p.put("links", links);
                p.put("other_records", other);
                profiles.add(p);
            }
        }
        Files.createDirectories(Paths.get("data", "results"));
        Files.createDirectories(Paths.get("docs", "results"));
        Files.writeString(Paths.get("data", "results", "customer_360.json"), MiniJson.write(profiles) + "\n", StandardCharsets.UTF_8);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Paths.get("docs", "results", "customer_master_list.csv"), StandardCharsets.UTF_8))) {
            w.println("entity_id,name,type,deposit_accounts,cards,total_deposits,total_card_credit,total_card_balance,cross_sell,core_banking_duplicates,card_services_duplicates,links,screening_record,credit_bureau_record,business_registry_record");
            for (Map<String, Object> p : profiles) {
                Map<String, Object> o = MiniJson.obj(p.get("other_records"));
                w.println(String.join(",", String.valueOf(p.get("entity_id")), csv(p.get("name")), csv(p.get("type")),
                        String.valueOf(((List<?>) p.get("deposit_accounts")).size()), String.valueOf(((List<?>) p.get("cards")).size()),
                        String.valueOf(p.get("total_deposits")), String.valueOf(p.get("total_card_credit")), String.valueOf(p.get("total_card_balance")),
                        csv(p.get("cross_sell")), String.valueOf(p.get("duplicate_records_in_core_banking")), String.valueOf(p.get("duplicate_records_in_card_services")),
                        String.valueOf(((List<?>) p.get("links")).size()), yn(o.get("screening_list_record_exists")),
                        yn(o.get("credit_bureau_record_exists")), yn(o.get("business_registry_record_exists"))));
            }
        }
        System.out.println("Customer 360");
        System.out.println("  customer records (CORE_BANKING + CARD_SERVICES): " + customerRecords);
        System.out.println("  customers (one profile each)                   : " + profiles.size());
        System.out.println("  with both a deposit account and a card         : " + both);
        System.out.println("  deposits but no card                           : " + depositsOnly);
        System.out.println("  card but no deposit account                    : " + cardOnly);
        System.out.println("  duplicates inside CORE_BANKING / CARD_SERVICES : " + dupCore + " / " + dupCard);
        System.out.println("  wrote data/results/customer_360.json and docs/results/customer_master_list.csv");
    }

    @SuppressWarnings("unchecked")
    static void addTo(Map<String, Object> m, String k, String v) {
        ((List<String>) m.computeIfAbsent(k, x -> new ArrayList<String>())).add(v);
    }

    static double round(double d) { return Math.round(d * 100.0) / 100.0; }

    static String yn(Object b) { return Boolean.TRUE.equals(b) ? "yes" : "no"; }

    static String csv(Object o) {
        String s = o == null ? "" : String.valueOf(o);
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
