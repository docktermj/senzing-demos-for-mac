// KYC/KYB alert queue for compliance analysts (Query, Visualize and Discover module, requirements 7-10).
//
// Alert rules come from docs/business_problem.md:
//   * Screening hit: a screening-list record resolved into the same entity as a customer or a
//     registered business party. Sanctions -> Prohibited; Internal Fraud and PEP -> High.
//   * Possible screening hit: a screening-list entity Senzing related to a customer or registry
//     entity as POSSIBLY_SAME (not merged) -> Medium, analyst decides. A name-only match is never a hit
//     by itself. POSSIBLY_RELATED links are not alerted: the business problem names households sharing
//     an address and people who moved as innocent explanations.
//   * Identity verification: a credit bureau file and the customers on it (the customer it resolved
//     with, plus customers related to it through a shared SSN). Several customers -> High; one -> Medium.
//   * Beneficial ownership: a business whose declared owner or officer is screened. A Sanctioned owner
//     holding 50% or more -> Prohibited; any other screened owner or officer -> High. Subsidiaries roll
//     up into their ultimate parent's alert.
// Every alert carries its evidence: the records, the match key or declared relationship, and the
// screening-list entry. The compliance view never shows balances or credit limits.
//
// Declared-relationship direction is read with Senzing's MATCH_KEY direction notation (ResolvedEntities.roles).
// For corporate structure the source data's roles are read by meaning: a record that points to another
// with role GLOBAL PARENT or SUBSIDIARY_OF is the subsidiary; one with role PARENT_OF is the parent.
//
// Senzing access is through ResolvedEntities. Writes data/results/kyc_alerts.json and
// docs/results/kyc_alert_queue.md.
// Run: src/scripts/run-java.sh data/temp/query_classes KycAlerts
// Default package, package-private class so the snake_case filename compiles (ground rules, INV-237).
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

class KycAlerts {
    static final List<String> SEVERITY = List.of("Prohibited", "High", "Medium");
    static final Set<String> PARTY_SOURCES = Set.of("CORE_BANKING", "CARD_SERVICES", "BUSINESS_REGISTRY");
    static final Set<String> CUSTOMER_SOURCES = Set.of("CORE_BANKING", "CARD_SERVICES");

    public static void main(String[] args) throws Exception {
        List<Map<String, Object>> alerts = new ArrayList<>();
        try (ResolvedEntities re = ResolvedEntities.load("KycAlerts")) {
            screeningHits(re, alerts);
            possibleHits(re, alerts);
            identityVerification(re, alerts);
            beneficialOwnership(re, alerts);
        }
        alerts.sort(Comparator.comparingInt((Map<String, Object> a) -> SEVERITY.indexOf(a.get("severity")))
                .thenComparing(a -> String.valueOf(a.get("type"))).thenComparing(a -> String.valueOf(a.get("subject"))));
        for (int i = 0; i < alerts.size(); i++) alerts.get(i).put("alert_id", String.format("KYC-%03d", i + 1));
        Files.createDirectories(Paths.get("data", "results"));
        Files.createDirectories(Paths.get("docs", "results"));
        Files.writeString(Paths.get("data", "results", "kyc_alerts.json"), MiniJson.write(alerts) + "\n", StandardCharsets.UTF_8);
        writeMarkdown(alerts);
        Map<String, Integer> bySeverity = new LinkedHashMap<>(), byType = new TreeMap<>();
        for (String s : SEVERITY) bySeverity.put(s, 0);
        for (Map<String, Object> a : alerts) { bySeverity.merge((String) a.get("severity"), 1, Integer::sum); byType.merge((String) a.get("type"), 1, Integer::sum); }
        System.out.println("KYC/KYB alert queue: " + alerts.size() + " alerts");
        System.out.println("  by severity: " + bySeverity);
        System.out.println("  by type    : " + byType);
        System.out.println("  wrote data/results/kyc_alerts.json and docs/results/kyc_alert_queue.md");
    }

    // ---- screening ------------------------------------------------------------------------------------
    static void screeningHits(ResolvedEntities re, List<Map<String, Object>> alerts) {
        for (long eid : re.entities.keySet()) {
            Set<String> s = re.sources(eid);
            if (!s.contains("WATCHLIST") || Collections.disjoint(s, PARTY_SOURCES)) continue;
            List<Map<String, Object>> entries = watchlistEntries(re, eid);
            Map<String, Object> a = alert("Screening hit", severityFor(entries), eid, re.name(eid), re.recordType(eid));
            a.put("screening_entries", entries);
            a.put("records", recordEvidence(re, eid));
            a.put("why", "Senzing resolved the screening-list record into the same entity as " + partyText(s) + ".");
            alerts.add(a);
        }
    }

    static void possibleHits(ResolvedEntities re, List<Map<String, Object>> alerts) {
        Set<String> seen = new HashSet<>();
        for (long w : re.entities.keySet()) {
            if (!re.sources(w).contains("WATCHLIST")) continue;
            for (Map<String, Object> rel : re.related(w)) {
                if (!"POSSIBLY_SAME".equals(MiniJson.str(rel.get("MATCH_LEVEL_CODE")))) continue;
                long other = ((Number) rel.get("ENTITY_ID")).longValue();
                Set<String> os = re.sources(other);
                if (Collections.disjoint(os, PARTY_SOURCES)) continue;
                if (!seen.add(Math.min(w, other) + "-" + Math.max(w, other))) continue;
                String mk = MiniJson.str(rel.get("MATCH_KEY"));
                Map<String, Object> a = alert("Possible screening hit", "Medium", other, re.name(other), re.recordType(other));
                a.put("screening_entries", watchlistEntries(re, w));
                a.put("screening_entity", Map.of("entity_id", w, "name", String.valueOf(re.name(w))));
                a.put("match_key", mk);
                a.put("name_only", "+NAME".equals(mk));
                a.put("records", recordEvidence(re, other));
                a.put("why", "Senzing found the screening-list entity possibly the same as this " + partyText(os)
                        + " but did not merge them (" + mk + "). Analyst decides.");
                alerts.add(a);
            }
        }
    }

    // ---- identity verification ------------------------------------------------------------------------
    static void identityVerification(ResolvedEntities re, List<Map<String, Object>> alerts) {
        for (long b : re.entities.keySet()) {
            if (!re.sources(b).contains("CREDIT_BUREAU")) continue;
            List<Map<String, Object>> customers = new ArrayList<>();
            if (!Collections.disjoint(re.sources(b), CUSTOMER_SOURCES)) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("entity_id", b); c.put("name", re.name(b)); c.put("link", "resolved with the bureau file");
                c.put("match_keys", matchKeys(re, b));
                customers.add(c);
            }
            for (Map<String, Object> rel : re.related(b)) {
                String mk = MiniJson.str(rel.get("MATCH_KEY"));
                long other = ((Number) rel.get("ENTITY_ID")).longValue();
                if (mk == null || !mk.contains("+SSN") || Collections.disjoint(re.sources(other), CUSTOMER_SOURCES)) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("entity_id", other); c.put("name", MiniJson.str(rel.get("ENTITY_NAME")));
                c.put("link", MiniJson.str(rel.get("MATCH_LEVEL_CODE")) + " " + mk);
                customers.add(c);
            }
            if (customers.isEmpty()) continue;
            Map<String, Object> a = alert("Identity verification", customers.size() >= 2 ? "High" : "Medium", b, re.name(b), re.recordType(b));
            a.put("bureau_records", recordsOf(re, b, "CREDIT_BUREAU"));
            a.put("customers_on_file", customers);
            a.put("records", recordEvidence(re, b));
            a.put("why", customers.size() + " customer(s) on one credit bureau file"
                    + (customers.size() >= 2 ? ": several customers share this identity." : "."));
            alerts.add(a);
        }
    }

    // ---- beneficial ownership ---------------------------------------------------------------------------
    static final Pattern PCT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");

    static void beneficialOwnership(ResolvedEntities re, List<Map<String, Object>> alerts) {
        // corporate structure: child -> parent, from declared relationships between organizations
        Map<Long, Long> parentOf = new HashMap<>();
        for (long o : re.entities.keySet()) {
            for (Map<String, Object> rel : re.related(o)) {
                long other = ((Number) rel.get("ENTITY_ID")).longValue();
                String[] roles = ResolvedEntities.roles(MiniJson.str(rel.get("MATCH_KEY")));
                String out = roles[0].toUpperCase(Locale.ROOT);
                if (out.equals("GLOBAL PARENT") || out.equals("SUBSIDIARY_OF")) parentOf.put(o, other);
                else if (out.equals("PARENT_OF")) parentOf.put(other, o);
            }
        }
        // direct findings per organization
        Map<Long, List<Map<String, Object>>> findings = new TreeMap<>();
        for (long o : re.entities.keySet()) {
            if (!"ORGANIZATION".equals(re.recordType(o))) continue;
            for (Map<String, Object> rel : re.related(o)) {
                String[] roles = ResolvedEntities.roles(MiniJson.str(rel.get("MATCH_KEY")));
                String partyRole = roles[1];          // role the related party has toward this business
                if (partyRole.isEmpty()) continue;
                long p = ((Number) rel.get("ENTITY_ID")).longValue();
                if (!re.sources(p).contains("WATCHLIST")) continue;
                List<Map<String, Object>> entries = watchlistEntries(re, p);
                boolean sanctioned = entries.stream().anyMatch(e -> "Sanctions".equals(e.get("list_type")));
                Matcher m = PCT.matcher(partyRole);
                double pct = m.find() ? Double.parseDouble(m.group(1)) : -1;
                String sev = sanctioned && pct >= 50 ? "Prohibited" : "High";
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("party_entity_id", p);
                f.put("party_name", re.name(p));
                f.put("role", partyRole);
                if (pct >= 0) f.put("ownership_percent", pct);
                f.put("screening_entries", entries);
                f.put("declared_relationship", MiniJson.str(rel.get("MATCH_KEY")));
                f.put("severity", sev);
                findings.computeIfAbsent(o, k -> new ArrayList<>()).add(f);
            }
        }
        // roll up to the ultimate parent
        Map<Long, List<Long>> group = new TreeMap<>();
        for (long o : findings.keySet()) group.computeIfAbsent(ultimate(parentOf, o), k -> new ArrayList<>()).add(o);
        for (Map.Entry<Long, List<Long>> g : group.entrySet()) {
            long top = g.getKey();
            String sev = "High";
            List<Map<String, Object>> companies = new ArrayList<>();
            for (long o : g.getValue()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("entity_id", o); c.put("name", re.name(o)); c.put("findings", findings.get(o));
                for (Map<String, Object> f : findings.get(o)) if ("Prohibited".equals(f.get("severity"))) sev = "Prohibited";
                companies.add(c);
            }
            List<Map<String, Object>> subs = new ArrayList<>();
            for (Map.Entry<Long, Long> e : parentOf.entrySet()) {
                if (e.getKey() != top && ultimate(parentOf, e.getKey()) == top) subs.add(Map.of("entity_id", e.getKey(), "name", String.valueOf(re.name(e.getKey()))));
            }
            Map<String, Object> a = alert("Beneficial ownership", sev, top, re.name(top), re.recordType(top));
            a.put("companies_with_findings", companies);
            a.put("subsidiaries_rolled_up", subs);
            a.put("records", recordEvidence(re, top));
            a.put("why", "A screened person is a declared owner or officer of this business"
                    + (g.getValue().size() > 1 || g.getValue().get(0) != top ? " or of a company in its group" : "") + ".");
            alerts.add(a);
        }
    }

    static long ultimate(Map<Long, Long> parentOf, long o) {
        Set<Long> seen = new HashSet<>();
        while (parentOf.containsKey(o) && seen.add(o)) o = parentOf.get(o);
        return o;
    }

    // ---- helpers ----------------------------------------------------------------------------------------
    static Map<String, Object> alert(String type, String severity, long eid, String name, String recordType) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("alert_id", "");
        a.put("type", type);
        a.put("severity", severity);
        a.put("entity_id", eid);
        a.put("subject", name);
        a.put("subject_type", recordType);
        a.put("disposition", null);   // recorded by the analyst app, never written to Senzing
        return a;
    }

    static String severityFor(List<Map<String, Object>> entries) {
        boolean sanctions = entries.stream().anyMatch(e -> "Sanctions".equals(e.get("list_type")));
        return sanctions ? "Prohibited" : "High";   // Internal Fraud and PEP are High
    }

    static List<Map<String, Object>> watchlistEntries(ResolvedEntities re, long eid) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : re.sourceRecordsOf(eid, "WATCHLIST")) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("record", "WATCHLIST/" + r.get("RECORD_ID"));
            e.put("list_type", r.get("LIST_TYPE"));
            e.put("list_status", r.get("LIST_STATUS"));
            e.put("listed_date", r.get("LISTED_DATE"));
            out.add(e);
        }
        return out;
    }

    /** Record-level evidence: each record and the match key that joined it. No business columns. */
    static List<String> recordEvidence(ResolvedEntities re, long eid) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : re.records(eid)) {
            String mk = MiniJson.str(r.get("MATCH_KEY"));
            out.add(r.get("DATA_SOURCE") + "/" + r.get("RECORD_ID") + (mk == null || mk.isEmpty() ? " (first record)" : " joined on " + mk));
        }
        return out;
    }

    static List<String> recordsOf(ResolvedEntities re, long eid, String ds) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : re.records(eid)) if (ds.equals(r.get("DATA_SOURCE"))) out.add(ds + "/" + r.get("RECORD_ID"));
        return out;
    }

    static List<String> matchKeys(ResolvedEntities re, long eid) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : re.records(eid)) { String mk = MiniJson.str(r.get("MATCH_KEY")); if (mk != null && !mk.isEmpty()) out.add(mk); }
        return out;
    }

    static String partyText(Set<String> s) {
        List<String> p = new ArrayList<>();
        if (s.contains("CORE_BANKING") || s.contains("CARD_SERVICES")) p.add("a customer");
        if (s.contains("BUSINESS_REGISTRY")) p.add("a business registry party");
        return String.join(" and ", p);
    }

    static void writeMarkdown(List<Map<String, Object>> alerts) throws IOException {
        StringBuilder b = new StringBuilder("# KYC/KYB alert queue\n\nGenerated by `src/query/kyc_alerts.java` from Senzing's resolved entities. "
                + "Balances and credit limits are deliberately absent. Dispositions (True match, False positive, Escalate to EDD) are recorded by the analyst app, never written to Senzing.\n\n"
                + "| Alert | Severity | Type | Subject | Entity | Evidence |\n|---|---|---|---|---|---|\n");
        for (Map<String, Object> a : alerts) {
            b.append("| ").append(a.get("alert_id")).append(" | ").append(a.get("severity")).append(" | ").append(a.get("type"))
             .append(" | ").append(a.get("subject")).append(" | ").append(a.get("entity_id")).append(" | ").append(evidence(a)).append(" |\n");
        }
        Files.writeString(Paths.get("docs", "results", "kyc_alert_queue.md"), b.toString(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    static String evidence(Map<String, Object> a) {
        List<String> parts = new ArrayList<>();
        Object e = a.get("screening_entries");
        if (e != null) for (Map<String, Object> x : (List<Map<String, Object>>) e) parts.add(x.get("record") + " " + x.get("list_type") + " (" + x.get("list_status") + ")");
        if (a.get("match_key") != null) parts.add("match key " + a.get("match_key"));
        if (a.get("customers_on_file") != null) for (Map<String, Object> c : (List<Map<String, Object>>) a.get("customers_on_file")) parts.add("customer " + c.get("name") + " [" + c.get("link") + "]");
        if (a.get("companies_with_findings") != null) for (Map<String, Object> c : (List<Map<String, Object>>) a.get("companies_with_findings"))
            for (Map<String, Object> f : (List<Map<String, Object>>) c.get("findings"))
                parts.add(c.get("name") + ": " + f.get("party_name") + " is " + f.get("role") + " (" + f.get("declared_relationship") + ")");
        if (a.get("subsidiaries_rolled_up") != null && !((List<?>) a.get("subsidiaries_rolled_up")).isEmpty()) {
            List<String> n = new ArrayList<>();
            for (Map<String, Object> s : (List<Map<String, Object>>) a.get("subsidiaries_rolled_up")) n.add(String.valueOf(s.get("name")));
            parts.add("subsidiaries: " + String.join(", ", n));
        }
        if (a.get("records") != null) parts.add("records: " + String.join("; ", (List<String>) a.get("records")));
        return String.join("<br>", parts).replace("|", "\\|");
    }
}
