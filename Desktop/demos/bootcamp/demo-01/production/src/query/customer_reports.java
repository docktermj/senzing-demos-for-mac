// customer_reports.java -- Query, Visualize and Discover: the report queries, from one export pass.
//
// Answers four of the business problem's query requirements:
//   1. resolved customer count against the input records, overall and per source;
//   2. customers that appear in more than one source, by source pair;
//   4. the customer master list: one row per resolved customer with its record IDs per source;
//   6. duplicates within a single source (customers with 2+ records in the same source).
// It walks Senzing's export stream (every resolved entity), so it never guesses entity IDs, and it
// counts records by (DATA_SOURCE, RECORD_ID) pairs, which is how Senzing identifies a record.
//
// SDK calls (reporting_guide topic='evaluation'/'export', get_sdk_reference, language='java'):
//   exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS) -> handle, fetchNext(handle) until null,
//   closeExportReport(handle) in finally. Rows carry RESOLVED_ENTITY.{ENTITY_ID, ENTITY_NAME, RECORDS[]}.
//   SZ_EXPORT_DEFAULT_FLAGS is a DEFAULT composite: right for exploration; in production request
//   exactly the flags you consume (its membership can change between Senzing versions, silently).
//
// Outputs (data/results/): customer_master_list.csv, cross_source_customers.csv,
// within_source_duplicates.csv, customer_counts.json.  Usage: src/scripts/run-java.sh CustomerReports
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.senzing.sdk.SzFlag.*;

class CustomerReports {
    static final List<String> SOURCES = List.of("CRM_CONTACTS", "WEB_ORDERS", "SUPPORT_TICKETS");

    static String csv(String s) {
        return (s.contains(",") || s.contains("\"")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of("data/results");
        Files.createDirectories(dir);
        Map<String, Long> recordsPerSource = new TreeMap<>(), entitiesPerSource = new TreeMap<>(), pairCounts = new TreeMap<>();
        Map<String, Long> dupEntitiesPerSource = new TreeMap<>(), dupRecordsPerSource = new TreeMap<>();
        long entities = 0, records = 0, crossEntities = 0;
        try (SenzingSession s = new SenzingSession(CustomerReports.class.getSimpleName());
             PrintWriter master = new PrintWriter(Files.newBufferedWriter(dir.resolve("customer_master_list.csv"), StandardCharsets.UTF_8));
             PrintWriter cross = new PrintWriter(Files.newBufferedWriter(dir.resolve("cross_source_customers.csv"), StandardCharsets.UTF_8));
             PrintWriter dups = new PrintWriter(Files.newBufferedWriter(dir.resolve("within_source_duplicates.csv"), StandardCharsets.UTF_8))) {
            master.println("entity_id,customer_name,record_count,sources,crm_contacts,web_orders,support_tickets");
            cross.println("entity_id,customer_name,sources,record_count");
            dups.println("entity_id,customer_name,data_source,record_count,record_ids");
            long handle = s.engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);
            try {
                for (String row = s.engine.fetchNext(handle); row != null; row = s.engine.fetchNext(handle)) {
                    if (row.isBlank()) { continue; }
                    Map<String, Object> re = Json.obj(Json.obj(Json.parse(row)).get("RESOLVED_ENTITY"));
                    long id = Json.lng(re.get("ENTITY_ID"));
                    String name = Json.str(re.get("ENTITY_NAME"), "(no name returned)");
                    Map<String, Set<String>> bySource = new TreeMap<>();   // (DATA_SOURCE, RECORD_ID) fold
                    for (Object o : Json.arr(re.get("RECORDS"))) {
                        Map<String, Object> r = Json.obj(o);
                        bySource.computeIfAbsent(Json.str(r.get("DATA_SOURCE"), ""), k -> new TreeSet<>())
                                .add(Json.str(r.get("RECORD_ID"), ""));
                    }
                    entities++;
                    long n = bySource.values().stream().mapToLong(Set::size).sum();
                    records += n;
                    for (Map.Entry<String, Set<String>> e : bySource.entrySet()) {
                        recordsPerSource.merge(e.getKey(), (long) e.getValue().size(), Long::sum);
                        entitiesPerSource.merge(e.getKey(), 1L, Long::sum);
                        if (e.getValue().size() > 1) {
                            dupEntitiesPerSource.merge(e.getKey(), 1L, Long::sum);
                            dupRecordsPerSource.merge(e.getKey(), (long) e.getValue().size(), Long::sum);
                            dups.println(id + "," + csv(name) + "," + e.getKey() + "," + e.getValue().size() + "," + String.join(" ", e.getValue()));
                        }
                    }
                    String sources = String.join("+", bySource.keySet());
                    List<String> cols = new ArrayList<>();
                    for (String src : SOURCES) { cols.add(String.join(" ", bySource.getOrDefault(src, Set.of()))); }
                    master.println(id + "," + csv(name) + "," + n + "," + sources + "," + String.join(",", cols));
                    if (bySource.size() > 1) {
                        crossEntities++;
                        cross.println(id + "," + csv(name) + "," + sources + "," + n);
                        List<String> l = new ArrayList<>(bySource.keySet());
                        for (int i = 0; i < l.size(); i++) for (int j = i + 1; j < l.size(); j++) { pairCounts.merge(l.get(i) + " x " + l.get(j), 1L, Long::sum); }
                    }
                }
            } finally {
                s.engine.closeExportReport(handle);
            }
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("input_records", records);
        counts.put("resolved_customers", entities);
        counts.put("records_per_customer", Math.round(100.0 * records / entities) / 100.0);
        Map<String, Object> per = new LinkedHashMap<>();
        for (String src : recordsPerSource.keySet()) {
            per.put(src, Map.of("records", recordsPerSource.get(src), "customers", entitiesPerSource.get(src),
                "customers_with_duplicates_in_source", dupEntitiesPerSource.getOrDefault(src, 0L),
                "records_in_those_duplicates", dupRecordsPerSource.getOrDefault(src, 0L)));
        }
        counts.put("per_source", per);
        counts.put("customers_in_2plus_sources", crossEntities);
        counts.put("customers_by_source_pair", pairCounts);
        Files.writeString(dir.resolve("customer_counts.json"), Json.write(counts), StandardCharsets.UTF_8);
        System.out.println(Json.write(counts));
    }
}
