// generate_customer_data.java -- Data collection, Step 2 (synthesized scenario).
//
// Generates the three Customer 360 sources agreed in "Discover the Business Problem":
//   CRM_CONTACTS    ~4,000 records  CSV    one full-name field, free-text address
//   WEB_ORDERS      ~4,500 records  CSV    name in two parts, address in parts
//   SUPPORT_TICKETS ~1,500 records  JSONL  sparse (many missing values), nicknames and typos
// into data/raw/, plus the ground truth (record -> invented customer) in data/ground_truth/.
//
// What the data deliberately carries (so the next module has real work to find):
//  - SHAPE differences across sources (single name field vs parts; free-text vs parsed address).
//  - QUALITY gaps: missing values and off-pattern values, with the weakest source aimed at the
//    70-79 band and the others at >=80 (the contrast is the teaching). The generator scores each
//    source with the bootcamp's formula and fails loudly if a band is missed -- it never adjusts
//    a score.
//  - CROSS-SOURCE OVERLAP and per-source duplicates (distinct record keys, same invented person).
//  - Names repeat across DIFFERENT invented people (hard negatives); an email, phone or any
//    identifier belongs to exactly one invented person, which the generator verifies.
//
// Quality score (Module 5, Step 6):  0.70 x completeness + 0.25 x format_consistency
//                                    + 0.05 x (100 - duplicate_rate)
//  completeness  = mean over records of present/applicable fields, applicable = fields with an
//                  Entity Specification counterpart (names, email, phone, address parts).
//  format_consistency = share of populated email/phone/postal/state/date values matching the
//                  field's dominant pattern (aggregate; per-field figures are printed too).
//  duplicate_rate = share of records whose record key repeats (always 0 here: keys are unique).
//
// Usage: src/scripts/run-java.sh GenerateCustomerData      (run from the project root)
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

class GenerateCustomerData {
    // ---- scenario sizing (INV-277: about 10,000 records) --------------------------------
    static final long SEED = 20261006L;
    // membership masks: bit0 = CRM, bit1 = WEB, bit2 = SUPPORT  -> entities per mask
    static final int[][] MASKS = {{1, 1900}, {2, 2300}, {4, 600}, {3, 1300}, {5, 300}, {6, 400}, {7, 200}};
    static final int CRM_DUPES = 300, WEB_DUPES = 300;

    // ---- quality targets -----------------------------------------------------------------
    // per-field MISSING rates, per source
    static final double CRM_MISS_EMAIL = 0.05, CRM_MISS_PHONE = 0.12, CRM_MISS_ADDR = 0.10;
    static final double WEB_MISS_EMAIL = 0.02, WEB_MISS_PHONE = 0.30, WEB_MISS_ADDR = 0.20;
    static final double SUP_MISS_NAME = 0.02, SUP_MISS_EMAIL = 0.20, SUP_MISS_PHONE = 0.50,
                        SUP_MISS_CITY = 0.48, SUP_MISS_POSTAL = 0.48;
    // off-pattern rates among POPULATED values
    static final double CRM_BAD_PHONE = 0.08, CRM_BAD_DATE = 0.15;
    static final double WEB_BAD_PHONE = 0.06, WEB_BAD_STATE = 0.06, WEB_BAD_ZIP = 0.05;
    static final double SUP_BAD_PHONE = 0.10, SUP_BAD_DATE = 0.07;
    static final double SUP_NICKNAME = 0.12, SUP_TYPO = 0.06;

    static final String[] FIRST = {"James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
        "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica", "Thomas", "Sarah",
        "Charles", "Karen", "Christopher", "Nancy", "Daniel", "Lisa", "Matthew", "Betty", "Anthony", "Margaret",
        "Mark", "Sandra", "Donald", "Ashley", "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna",
        "Joshua", "Michelle", "Kenneth", "Carol", "Kevin", "Amanda", "Brian", "Melissa", "George", "Deborah",
        "Maria", "Jose", "Wei", "Priya", "Ahmed", "Fatima", "Carlos", "Sofia", "Hiroshi", "Yuki"};
    static final String[] LAST = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor",
        "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark",
        "Ramirez", "Lewis", "Robinson", "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres",
        "Nguyen", "Hill", "Flores", "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell",
        "Mitchell", "Carter", "Roberts", "Gomez", "Phillips", "Evans", "Turner", "Diaz", "Parker", "Cruz",
        "Edwards", "Collins", "Reyes", "Stewart", "Morris", "Morales", "Murphy", "Cook", "Rogers", "Gutierrez",
        "Ortiz", "Morgan", "Cooper", "Peterson", "Bailey", "Reed", "Kelly", "Howard", "Ramos", "Kim",
        "Cox", "Ward", "Richardson", "Watson", "Brooks", "Chavez", "Wood", "James", "Bennett", "Gray",
        "Mendoza", "Ruiz", "Hughes", "Price", "Alvarez", "Castillo", "Sanders", "Patel", "Myers", "Long",
        "Ross", "Foster", "Jimenez", "Powell", "Jenkins", "Perry", "Russell", "Sullivan", "Bell", "Coleman"};
    static final Map<String, String> NICK = Map.ofEntries(
        Map.entry("Robert", "Bob"), Map.entry("William", "Bill"), Map.entry("Elizabeth", "Liz"),
        Map.entry("Michael", "Mike"), Map.entry("Jennifer", "Jenny"), Map.entry("Richard", "Rick"),
        Map.entry("Thomas", "Tom"), Map.entry("Christopher", "Chris"), Map.entry("Margaret", "Peggy"),
        Map.entry("Patricia", "Pat"), Map.entry("Joseph", "Joe"), Map.entry("Daniel", "Dan"),
        Map.entry("Matthew", "Matt"), Map.entry("Kenneth", "Ken"), Map.entry("Barbara", "Barb"),
        Map.entry("Susan", "Sue"), Map.entry("Anthony", "Tony"), Map.entry("Steven", "Steve"));
    static final String[] STREETS = {"Maple", "Oak", "Pine", "Cedar", "Elm", "Washington", "Lake", "Hill", "Park",
        "Sunset", "Highland", "Madison", "Jefferson", "Lincoln", "Willow", "Church", "Mill", "River", "Spring",
        "Forest", "Meadow", "Ridge", "Franklin", "Walnut", "Chestnut", "Birch", "Dogwood", "Magnolia", "Cherry", "Aspen"};
    static final String[][] SUFFIX = {{"Street", "St"}, {"Avenue", "Ave"}, {"Road", "Rd"}, {"Drive", "Dr"}, {"Lane", "Ln"}, {"Court", "Ct"}};
    static final String[][] CITIES = {{"Springfield", "IL", "627"}, {"Riverton", "WY", "825"}, {"Fairview", "OR", "970"},
        {"Madison", "WI", "537"}, {"Georgetown", "TX", "786"}, {"Clinton", "IA", "527"}, {"Salem", "OR", "973"},
        {"Franklin", "TN", "370"}, {"Greenville", "SC", "296"}, {"Bristol", "CT", "060"}, {"Auburn", "AL", "368"},
        {"Oxford", "MS", "386"}, {"Kingston", "NY", "124"}, {"Arlington", "VA", "222"}, {"Ashland", "KY", "411"},
        {"Burlington", "VT", "054"}, {"Dayton", "OH", "454"}, {"Jackson", "MS", "392"}, {"Milton", "FL", "325"},
        {"Newport", "RI", "028"}, {"Lebanon", "NH", "037"}, {"Camden", "NJ", "081"}, {"Hudson", "NY", "125"},
        {"Marion", "OH", "433"}, {"Plymouth", "MA", "023"}};
    static final String[] DOMAINS = {"example.com", "example.org", "example.net", "mailtest.example"};
    static final String[] AREAS = {"702", "213", "312", "415", "617", "206", "305", "404"};
    static final String[] TOPICS = {"billing question", "order status", "return request", "password reset",
        "address change", "product defect", "delivery delay", "account closure", "loyalty points", "gift card"};

    // ---- invented customer ------------------------------------------------------------------
    static class Person {
        int id; String first, last, email, phoneDigits, street, city, state, zip; int mask;
    }

    static final Random R = new Random(SEED);
    static final Pattern PHONE = Pattern.compile("^\\d{3}-\\d{3}-\\d{4}$");
    static final Pattern ZIP = Pattern.compile("^\\d{5}$");
    static final Pattern STATE = Pattern.compile("^[A-Z]{2}$");
    static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    static boolean chance(double p) { return R.nextDouble() < p; }
    static <T> T pick(T[] a) { return a[R.nextInt(a.length)]; }

    static Person newPerson(int id, int mask) {
        Person p = new Person();
        p.id = id; p.mask = mask;
        p.first = pick(FIRST); p.last = pick(LAST);
        p.email = (p.first + "." + p.last + "." + id + "@" + DOMAINS[id % DOMAINS.length]).toLowerCase();
        int area = id % AREAS.length, r = id / AREAS.length;       // (area, r) unique per id
        p.phoneDigits = AREAS[area] + (200 + r / 50) + (1000 + (r % 50) * 173);
        String[] sfx = SUFFIX[R.nextInt(SUFFIX.length)];
        p.street = (100 + R.nextInt(9800)) + " " + pick(STREETS) + " " + sfx[0];
        String[] c = pick(CITIES);
        p.city = c[0]; p.state = c[1]; p.zip = c[2] + (10 + R.nextInt(90));
        return p;
    }

    static String phone(Person p, double badRate) {
        String d = p.phoneDigits;
        if (chance(badRate)) {
            return R.nextBoolean() ? "(" + d.substring(0, 3) + ") " + d.substring(3, 6) + "-" + d.substring(6) : d;
        }
        return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
    }

    static String date(double badRate) {
        int y = 2019 + R.nextInt(7), m = 1 + R.nextInt(12), d = 1 + R.nextInt(28);
        return chance(badRate) ? String.format("%d/%d/%d", m, d, y) : String.format("%d-%02d-%02d", y, m, d);
    }

    static String abbreviate(String street) {
        for (String[] s : SUFFIX) {
            if (street.endsWith(" " + s[0])) { return street.substring(0, street.length() - s[0].length()) + s[1]; }
        }
        return street;
    }

    static String typo(String s) {
        if (s.length() < 4) { return s; }
        int i = 1 + R.nextInt(s.length() - 2);
        return R.nextBoolean() ? s.substring(0, i) + s.substring(i + 1)
                               : s.substring(0, i) + s.charAt(i + 1) + s.charAt(i) + s.substring(i + 2);
    }

    // ---- tabular rows ----------------------------------------------------------------------
    static class Row { Person p; Map<String, String> v = new LinkedHashMap<>(); String key; }

    static String csv(String s) {
        if (s == null) { return ""; }
        return (s.contains(",") || s.contains("\"") || s.contains("\n")) ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    static Row crmRow(Person p, int seq) {
        Row r = new Row(); r.p = p; r.key = String.format("C%06d", seq);
        r.v.put("contact_id", r.key);
        r.v.put("full_name", p.first + " " + p.last);
        r.v.put("email", chance(CRM_MISS_EMAIL) ? "" : p.email);
        r.v.put("phone", chance(CRM_MISS_PHONE) ? "" : phone(p, CRM_BAD_PHONE));
        String street = chance(0.3) ? abbreviate(p.street) : p.street;
        r.v.put("address", chance(CRM_MISS_ADDR) ? "" : street + ", " + p.city + ", " + p.state + " " + p.zip);
        r.v.put("created_date", date(CRM_BAD_DATE));
        return r;
    }

    static Row webRow(Person p, int seq) {
        Row r = new Row(); r.p = p; r.key = String.format("W%06d", seq);
        r.v.put("order_id", r.key);
        r.v.put("first_name", p.first);
        r.v.put("last_name", p.last);
        r.v.put("email", chance(WEB_MISS_EMAIL) ? "" : p.email);
        r.v.put("phone", chance(WEB_MISS_PHONE) ? "" : phone(p, WEB_BAD_PHONE));
        boolean addr = !chance(WEB_MISS_ADDR);
        r.v.put("street", addr ? (chance(0.3) ? abbreviate(p.street) : p.street) : "");
        r.v.put("city", addr ? p.city : "");
        r.v.put("state", addr ? (chance(WEB_BAD_STATE) ? p.state.toLowerCase() : p.state) : "");
        r.v.put("zip", addr ? (chance(WEB_BAD_ZIP) ? p.zip + "-" + (1000 + R.nextInt(9000)) : p.zip) : "");
        r.v.put("order_date", date(0));
        r.v.put("order_total", String.format("%d.%02d", 5 + R.nextInt(400), R.nextInt(100)));
        return r;
    }

    static Row supRow(Person p, int seq) {
        Row r = new Row(); r.p = p; r.key = String.format("T%06d", seq);
        r.v.put("ticket_id", r.key);
        String first = p.first;
        if (NICK.containsKey(first) && chance(SUP_NICKNAME / 0.30)) { first = NICK.get(first); }   // ~12% overall
        String last = p.last;
        if (chance(SUP_TYPO)) { if (R.nextBoolean()) { first = typo(first); } else { last = typo(last); } }
        r.v.put("requester_name", chance(SUP_MISS_NAME) ? "" : first + " " + last);
        r.v.put("requester_email", chance(SUP_MISS_EMAIL) ? "" : p.email);
        r.v.put("phone", chance(SUP_MISS_PHONE) ? "" : phone(p, SUP_BAD_PHONE));
        r.v.put("city", chance(SUP_MISS_CITY) ? "" : p.city);
        r.v.put("postal_code", chance(SUP_MISS_POSTAL) ? "" : p.zip);
        r.v.put("topic", pick(TOPICS));
        r.v.put("opened_on", date(SUP_BAD_DATE));
        return r;
    }

    // ---- scoring (Module 5, Step 6) -----------------------------------------------------------
    static Map<String, Object> score(List<Row> rows, List<String> applicable, Map<String, Pattern> patterns) {
        double compSum = 0;
        for (Row r : rows) {
            int present = 0;
            for (String f : applicable) { if (!r.v.getOrDefault(f, "").isBlank()) { present++; } }
            compSum += (double) present / applicable.size();
        }
        double completeness = 100.0 * compSum / rows.size();
        long populated = 0, matching = 0;
        Map<String, Object> perField = new LinkedHashMap<>();
        for (Map.Entry<String, Pattern> e : patterns.entrySet()) {
            long pop = 0, ok = 0;
            for (Row r : rows) {
                String s = r.v.getOrDefault(e.getKey(), "");
                if (s.isBlank()) { continue; }
                pop++;
                if (e.getValue().matcher(s).matches()) { ok++; }
            }
            populated += pop; matching += ok;
            perField.put(e.getKey(), pop == 0 ? null : Math.round(1000.0 * ok / pop) / 10.0);
        }
        double format = populated == 0 ? 100.0 : 100.0 * matching / populated;
        Set<String> keys = new HashSet<>();
        long dup = 0;
        for (Row r : rows) { if (!keys.add(r.key)) { dup++; } }
        double dupRate = 100.0 * dup / rows.size();
        double score = 0.70 * completeness + 0.25 * format + 0.05 * (100 - dupRate);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("completeness", Math.round(10 * completeness) / 10.0);
        out.put("format_consistency", Math.round(10 * format) / 10.0);
        out.put("duplicate_rate", Math.round(10 * dupRate) / 10.0);
        out.put("score", Math.round(10 * score) / 10.0);
        out.put("per_field_format", perField);
        return out;
    }

    static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    static String csvText(List<Row> rows) {
        StringBuilder sb = new StringBuilder(String.join(",", rows.get(0).v.keySet())).append("\n");
        for (Row r : rows) {
            StringJoiner j = new StringJoiner(",");
            for (String v : r.v.values()) { j.add(csv(v)); }
            sb.append(j).append("\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        // ---- the population: 7,000 invented customers with source memberships -----------------
        List<Person> pop = new ArrayList<>();
        int id = 1;
        for (int[] m : MASKS) { for (int k = 0; k < m[1]; k++) { pop.add(newPerson(id++, m[0])); } }
        Collections.shuffle(pop, R);
        List<Person> inCrm = new ArrayList<>(), inWeb = new ArrayList<>(), inSup = new ArrayList<>();
        for (Person p : pop) {
            if ((p.mask & 1) != 0) { inCrm.add(p); }
            if ((p.mask & 2) != 0) { inWeb.add(p); }
            if ((p.mask & 4) != 0) { inSup.add(p); }
        }

        // ---- identifier-collision check (INV-239): no value shared by two invented people -----
        Map<String, Set<Integer>> owners = new HashMap<>();
        for (Person p : pop) {
            owners.computeIfAbsent("email:" + p.email, z -> new HashSet<>()).add(p.id);
            owners.computeIfAbsent("phone:" + p.phoneDigits, z -> new HashSet<>()).add(p.id);
        }
        long collisions = owners.values().stream().filter(s -> s.size() > 1).count();
        if (collisions > 0) { throw new IllegalStateException("identifier collisions: " + collisions); }

        // ---- rows -----------------------------------------------------------------------------
        List<Row> crm = new ArrayList<>(), web = new ArrayList<>(), sup = new ArrayList<>();
        int seq = 1;
        for (Person p : inCrm) { crm.add(crmRow(p, seq++)); }
        for (int k = 0; k < CRM_DUPES; k++) { crm.add(crmRow(inCrm.get(R.nextInt(inCrm.size())), seq++)); }
        seq = 1;
        for (Person p : inWeb) { web.add(webRow(p, seq++)); }
        for (int k = 0; k < WEB_DUPES; k++) { web.add(webRow(inWeb.get(R.nextInt(inWeb.size())), seq++)); }
        seq = 1;
        for (Person p : inSup) { sup.add(supRow(p, seq++)); }
        Collections.shuffle(crm, R); Collections.shuffle(web, R); Collections.shuffle(sup, R);   // not grouped by entity

        // ---- score each source against the intended band --------------------------------------
        Map<String, Pattern> crmPat = new LinkedHashMap<>(Map.of("email", EMAIL, "phone", PHONE, "created_date", DATE));
        Map<String, Pattern> webPat = new LinkedHashMap<>(Map.of("email", EMAIL, "phone", PHONE, "state", STATE, "zip", ZIP, "order_date", DATE));
        Map<String, Pattern> supPat = new LinkedHashMap<>(Map.of("requester_email", EMAIL, "phone", PHONE, "postal_code", ZIP, "opened_on", DATE));
        Map<String, Object> sCrm = score(crm, List.of("full_name", "email", "phone", "address"), crmPat);
        Map<String, Object> sWeb = score(web, List.of("first_name", "last_name", "email", "phone", "street", "city", "state", "zip"), webPat);
        Map<String, Object> sSup = score(sup, List.of("requester_name", "requester_email", "phone", "city", "postal_code"), supPat);

        double c = (Double) sCrm.get("score"), w = (Double) sWeb.get("score"), s = (Double) sSup.get("score");
        System.out.println("CRM_CONTACTS    " + crm.size() + " records  " + sCrm);
        System.out.println("WEB_ORDERS      " + web.size() + " records  " + sWeb);
        System.out.println("SUPPORT_TICKETS " + sup.size() + " records  " + sSup);
        System.out.println("identifier collisions: " + collisions);
        boolean bandsOk = c >= 80 && w >= 80 && s >= 70 && s < 80;
        System.out.println("bands: CRM>=80 " + (c >= 80) + ", WEB>=80 " + (w >= 80) + ", SUPPORT in 70-79 " + (s >= 70 && s < 80));
        if (!bandsOk) {
            System.err.println("BAND MISSED -- widen or narrow the gap rates above and regenerate (never adjust a score).");
            System.exit(3);
        }

        // ---- write the files -------------------------------------------------------------------
        write(Paths.get("data/raw/crm_contacts.csv"), csvText(crm));
        write(Paths.get("data/raw/web_orders.csv"), csvText(web));
        StringBuilder jl = new StringBuilder();
        for (Row r : sup) {
            Map<String, Object> o = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : r.v.entrySet()) { if (!e.getValue().isBlank()) { o.put(e.getKey(), e.getValue()); } }
            jl.append(Json.write(o)).append("\n");
        }
        write(Paths.get("data/raw/support_tickets.jsonl"), jl.toString());

        StringBuilder gt = new StringBuilder("source,record_key,customer_id\n");
        for (Row r : crm) { gt.append("CRM_CONTACTS,").append(r.key).append(",").append(r.p.id).append("\n"); }
        for (Row r : web) { gt.append("WEB_ORDERS,").append(r.key).append(",").append(r.p.id).append("\n"); }
        for (Row r : sup) { gt.append("SUPPORT_TICKETS,").append(r.key).append(",").append(r.p.id).append("\n"); }
        write(Paths.get("data/ground_truth/record_to_customer.csv"), gt.toString());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("seed", SEED);
        stats.put("customers", (long) pop.size());
        stats.put("CRM_CONTACTS", Map.of("records", (long) crm.size(), "quality", sCrm));
        stats.put("WEB_ORDERS", Map.of("records", (long) web.size(), "quality", sWeb));
        stats.put("SUPPORT_TICKETS", Map.of("records", (long) sup.size(), "quality", sSup));
        stats.put("identifier_collisions", collisions);
        write(Paths.get("data/ground_truth/generation_stats.json"), Json.write(stats));
        System.out.println("written: data/raw/{crm_contacts.csv,web_orders.csv,support_tickets.jsonl}, data/ground_truth/");
    }
}
