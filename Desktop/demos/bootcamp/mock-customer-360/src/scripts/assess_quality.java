// assess_quality.java -- Data Quality, Mapping, and Transformation, Phase 1 Step 6.
//
// Scores each collected source with the module's formula, independently of the data generator:
//   quality = 0.70 x completeness + 0.25 x format_consistency + 0.05 x (100 - duplicate_rate)
// completeness       mean over records of present/applicable fields; applicable = the fields Step 4
//                    found an Entity Specification counterpart for (record keys excluded: they are
//                    measured by duplicate_rate). "Present" per Step 6: not missing, not null, not
//                    an empty/whitespace string.
// format_consistency share of populated values matching their field's DOMINANT observed shape, over
//                    the coded fields (dates, phones, postal codes, state codes, emails). A shape
//                    masks digits as 9 and letters as A/a; an email's shape is valid/invalid.
// duplicate_rate     share of records whose record key is not unique (never whole-row equality).
// Also counts distinct email and phone values shared between each pair of sources, so the
// evaluation report can label cross-source pairs as measured.
//
// Usage: "$SENZING_JAVA_HOME/bin/java" -cp src/build/classes src/scripts/assess_quality.java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

class AssessQuality {
    record Source(String name, String path, String key, List<String> applicable, List<String> coded,
                  String emailField, String phoneField) {}

    static final List<Source> SOURCES = List.of(
        new Source("CRM_CONTACTS", "data/raw/crm_contacts.csv", "contact_id",
            List.of("full_name", "email", "phone", "address"),
            List.of("email", "phone", "created_date"), "email", "phone"),
        new Source("WEB_ORDERS", "data/raw/web_orders.csv", "order_id",
            List.of("first_name", "last_name", "email", "phone", "street", "city", "state", "zip"),
            List.of("email", "phone", "state", "zip", "order_date"), "email", "phone"),
        new Source("SUPPORT_TICKETS", "data/raw/support_tickets.jsonl", "ticket_id",
            List.of("requester_name", "requester_email", "phone", "city", "postal_code"),
            List.of("requester_email", "phone", "postal_code", "opened_on"), "requester_email", "phone"));

    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    static boolean present(String v) { return v != null && !v.isBlank(); }

    static String shape(String field, String v) {
        if (field.contains("email")) {
            return EMAIL.matcher(v).matches() ? "valid-email" : "invalid-email";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : v.toCharArray()) {
            sb.append(Character.isDigit(c) ? '9' : Character.isUpperCase(c) ? 'A' : Character.isLowerCase(c) ? 'a' : c);
        }
        return sb.toString();
    }

    static List<Map<String, String>> read(String path) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
        List<Map<String, String>> rows = new ArrayList<>();
        if (path.endsWith(".jsonl")) {
            for (String l : lines) {
                if (l.isBlank()) { continue; }
                Map<String, String> m = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : Json.obj(Json.parse(l)).entrySet()) {
                    m.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
                }
                rows.add(m);
            }
            return rows;
        }
        List<String> header = csv(lines.get(0));
        for (String l : lines.subList(1, lines.size())) {
            if (l.isEmpty()) { continue; }
            List<String> v = csv(l);
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) { m.put(header.get(i), i < v.size() ? v.get(i) : null); }
            rows.add(m);
        }
        return rows;
    }

    static List<String> csv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') { q = false; }
                else { cur.append(c); }
            } else if (c == '"') { q = true; }
            else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
            else { cur.append(c); }
        }
        out.add(cur.toString());
        return out;
    }

    static double r1(double d) { return Math.round(d * 10) / 10.0; }

    public static void main(String[] args) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, Set<String>> emails = new LinkedHashMap<>(), phones = new LinkedHashMap<>();
        for (Source s : SOURCES) {
            List<Map<String, String>> rows = read(s.path());
            double comp = 0;
            Map<String, Long> presentCount = new LinkedHashMap<>();
            for (String f : s.applicable()) { presentCount.put(f, 0L); }
            Set<String> keys = new HashSet<>();
            long dup = 0, noType = 0;
            Set<String> em = new HashSet<>(), ph = new HashSet<>();
            for (Map<String, String> r : rows) {
                int p = 0;
                for (String f : s.applicable()) {
                    if (present(r.get(f))) { p++; presentCount.merge(f, 1L, Long::sum); }
                }
                comp += (double) p / s.applicable().size();
                if (!keys.add(r.get(s.key()))) { dup++; }
                if (!present(r.get("RECORD_TYPE"))) { noType++; }
                if (present(r.get(s.emailField()))) { em.add(r.get(s.emailField()).trim().toLowerCase()); }
                if (present(r.get(s.phoneField()))) { ph.add(r.get(s.phoneField()).replaceAll("\\D", "")); }
            }
            emails.put(s.name(), em);
            phones.put(s.name(), ph);
            double completeness = 100 * comp / rows.size();
            long pop = 0, match = 0;
            Map<String, Object> perFormat = new LinkedHashMap<>();
            for (String f : s.coded()) {
                Map<String, Long> shapes = new HashMap<>();
                for (Map<String, String> r : rows) {
                    if (present(r.get(f))) { shapes.merge(shape(f, r.get(f).trim()), 1L, Long::sum); }
                }
                long fpop = shapes.values().stream().mapToLong(Long::longValue).sum();
                String dominant = shapes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
                long fmatch = shapes.getOrDefault(dominant, 0L);
                pop += fpop;
                match += fmatch;
                perFormat.put(f, Map.of("dominant", dominant, "consistency", fpop == 0 ? 100.0 : r1(100.0 * fmatch / fpop),
                                        "off_pattern", fpop - fmatch));
            }
            double format = pop == 0 ? 100 : 100.0 * match / pop;
            double dupRate = 100.0 * dup / rows.size();
            double score = 0.70 * completeness + 0.25 * format + 0.05 * (100 - dupRate);
            Map<String, Object> perField = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : presentCount.entrySet()) { perField.put(e.getKey(), r1(100.0 * e.getValue() / rows.size())); }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("records", (long) rows.size());
            o.put("completeness", r1(completeness));
            o.put("field_presence", perField);
            o.put("format_consistency", r1(format));
            o.put("format_by_field", perFormat);
            o.put("duplicate_rate", r1(dupRate));
            o.put("quality_score", r1(score));
            o.put("records_without_record_type", noType);
            o.put("person_typed_records", 0L);
            report.put(s.name(), o);
        }
        Map<String, Object> overlap = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(emails.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                Set<String> e = new HashSet<>(emails.get(names.get(i))); e.retainAll(emails.get(names.get(j)));
                Set<String> p = new HashSet<>(phones.get(names.get(i))); p.retainAll(phones.get(names.get(j)));
                overlap.put(names.get(i) + " x " + names.get(j), Map.of(
                    "shared_email_values", (long) e.size(), "shared_phone_digits", (long) p.size(),
                    "email_values_a", (long) emails.get(names.get(i)).size(), "email_values_b", (long) emails.get(names.get(j)).size()));
            }
        }
        report.put("cross_source_overlap", overlap);
        String out = Json.write(report);
        Files.createDirectories(Path.of("data/mapping"));
        Files.writeString(Path.of("data/mapping/quality_assessment.json"), out, StandardCharsets.UTF_8);
        System.out.println(out);
    }
}
