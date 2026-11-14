package query;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import server.Json;

import static com.senzing.sdk.SzFlag.*;

/**
 * Query, Visualize and Discover: the shared query model behind the query programs and the web app.
 *
 * Every entity fact comes from the Senzing SDK (no direct SQL). Calls and response shapes, all from the
 * Senzing MCP server (get_sdk_reference, language='java'):
 *   getEntity(SzRecordKey, Set<SzFlag>)       one record's resolved entity (we iterate over the records we
 *                                             loaded, never over guessed entity IDs)
 *   getEntity(long, Set<SzFlag>)              an entity named in a relationship
 *   whyEntities(long, long, Set<SzFlag>)      why two entities are related (WHY_RESULTS[].MATCH_INFO.WHY_KEY,
 *                                             WHY_ERRULE_CODE, FEATURE_SCORES)
 *   searchByAttributes(String, Set<SzFlag>)   attribute search (RESOLVED_ENTITIES[].ENTITY.RESOLVED_ENTITY,
 *                                             RESOLVED_ENTITIES[].MATCH_INFO)
 * Flags: SZ_ENTITY_DEFAULT_FLAGS (records, record match keys, relationships with their match keys and record
 * summaries) plus SZ_ENTITY_INCLUDE_RECORD_JSON_DATA, so each record carries the business fields it was loaded
 * with (RESOLVED_ENTITY.RECORDS[].JSON_DATA). In Java the composite is a Set<SzFlag>, so it is merged into an
 * EnumSet rather than listed beside single flags. The DEFAULT composite is the exploration path; production
 * code should request exactly the flags it reads (see production/MIGRATION_CHECKLIST.md at graduation).
 *
 * Lifetime: the SzEnvironment is held in a field for as long as the model lives, because an engine does not
 * keep its environment alive (destroying or losing it kills the engine).
 *
 * The business rules (severities, the 50% rule, roll-up) are in docs/reference/screening_rules.md.
 */
public final class InsuranceModel implements AutoCloseable {

    public static final List<String> CUSTOMER_SOURCES = List.of("PC_POLICY_ADMIN", "LIFE_ANNUITY_ADMIN");
    public static final List<String> OUTSIDE_SOURCES =
        List.of("CREDIT_BUREAU", "BUSINESS_REGISTRY", "SIU_CASES", "SANCTIONS_PEP");
    public static final String PC = "PC_POLICY_ADMIN";
    public static final String LIFE = "LIFE_ANNUITY_ADMIN";

    public static final List<String> SEVERITIES = List.of("PROHIBITED", "HIGH", "MEDIUM", "LOW");

    // Disclosed-relationship roles, read from this project's own data (BUSINESS_REGISTRY, PC, LIFE pointers).
    // The role in a match key sits on the side of the record that carried the REL_POINTER.
    private static final List<String> OWNER_OFFICER = List.of("OWNS", "OWNER", "PRESIDENT", "PRINCIPAL",
        "DIRECTOR", "OFFICER");
    private static final Set<String> HOLDER_IS_SUBSIDIARY = Set.of("GLOBAL PARENT", "SUBSIDIARY_OF");
    private static final Set<String> HOLDER_IS_PARENT = Set.of("PARENT_OF");

    private static final Pattern REL_ROLES = Pattern.compile("\\+[A-Z_]+\\(([^:)]*):([^)]*)\\)");
    private static final Pattern SIGNED = Pattern.compile("([+-])([A-Z_]+)");
    private static final Pattern PCT = Pattern.compile("OWNS\\s*(\\d+(?:\\.\\d+)?)\\s*%");

    private final SzEnvironment env;   // must outlive the engine
    private final SzEngine engine;
    private final Set<SzFlag> entityFlags;
    private final Set<SzFlag> whyFlags;
    private final Map<Long, Map<String, Object>> byEntity = new HashMap<>();
    private final Map<String, Long> byRecord = new HashMap<>();
    private final Map<String, List<String>> inputKeys = new LinkedHashMap<>();

    public static InsuranceModel open(String instanceName) throws Exception {
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        try {
            return new InsuranceModel(env);
        } catch (Exception e) {
            env.destroy();
            throw e;
        }
    }

    /**
     * A model over an engine whose environment the CALLER owns and keeps alive (the web app holds its
     * environment for the process lifetime). close() then leaves the environment alone.
     */
    public static InsuranceModel over(SzEngine engine) throws Exception {
        return new InsuranceModel(null, engine);
    }

    private InsuranceModel(SzEnvironment env) throws Exception {
        this(env, env.getEngine());
    }

    private InsuranceModel(SzEnvironment env, SzEngine engine) throws Exception {
        this.env = env;
        this.engine = engine;
        Set<SzFlag> f = EnumSet.noneOf(SzFlag.class);
        f.addAll(SZ_ENTITY_DEFAULT_FLAGS);
        f.add(SZ_ENTITY_INCLUDE_RECORD_JSON_DATA);
        this.entityFlags = Collections.unmodifiableSet(f);
        Set<SzFlag> w = EnumSet.noneOf(SzFlag.class);
        w.addAll(SZ_WHY_ENTITIES_DEFAULT_FLAGS);       // = SZ_INCLUDE_FEATURE_SCORES only
        w.add(SZ_ENTITY_INCLUDE_ENTITY_NAME);          // the why default carries no entity name
        this.whyFlags = Collections.unmodifiableSet(w);
        for (String s : concat(CUSTOMER_SOURCES, OUTSIDE_SOURCES)) inputKeys.put(s, readKeys(s));
    }

    public SzEngine engine() { return engine; }

    public Set<SzFlag> entityFlags() { return entityFlags; }

    @Override
    public void close() {
        // IMPORTANT: make sure to destroy the environment (only when this model created it)
        if (env != null) env.destroy();
    }

    /** Distinct RECORD_IDs loaded for a source, folded on (DATA_SOURCE, RECORD_ID) as Senzing does. */
    public List<String> recordIds(String source) {
        return inputKeys.getOrDefault(source, List.of());
    }

    private static List<String> readKeys(String source) throws Exception {
        Path p = Path.of("data", "senzing-ready", source.toLowerCase() + ".jsonl");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            ids.add(Json.str(Json.parseObject(t).get("RECORD_ID")));
        }
        return new ArrayList<>(ids);
    }

    // ------------------------------------------------------------------ SDK access (cached)

    public Map<String, Object> entityByRecord(String source, String recordId) throws SzException {
        Long id = byRecord.get(source + "|" + recordId);
        if (id != null) return byEntity.get(id);
        return remember(Json.parseObject(engine.getEntity(SzRecordKey.of(source, recordId), entityFlags)));
    }

    public Map<String, Object> entity(long entityId) throws SzException {
        Map<String, Object> e = byEntity.get(entityId);
        if (e != null) return e;
        return remember(Json.parseObject(engine.getEntity(entityId, entityFlags)));
    }

    private Map<String, Object> remember(Map<String, Object> e) {
        long id = entityId(e);
        byEntity.put(id, e);
        for (Map<String, Object> r : records(e)) byRecord.put(r.get("DATA_SOURCE") + "|" + r.get("RECORD_ID"), id);
        return e;
    }

    /** whyEntities, reduced to what an analyst reads: the why key, the rule, and the best score per feature. */
    public Map<String, Object> why(long a, long b) throws SzException {
        Map<String, Object> raw = Json.parseObject(engine.whyEntities(a, b, whyFlags));
        Map<String, Object> out = new LinkedHashMap<>();
        List<Object> results = Json.arr(raw.get("WHY_RESULTS"));
        if (results.isEmpty()) {
            out.put("note", "no WHY_RESULTS returned");
            return out;
        }
        Map<String, Object> info = Json.obj(Json.obj(results.get(0)).get("MATCH_INFO"));
        out.put("why_key", info.get("WHY_KEY"));
        out.put("why_rule", info.get("WHY_ERRULE_CODE"));
        out.put("match_level", info.get("MATCH_LEVEL_CODE"));
        List<Object> scores = new ArrayList<>();
        for (Map.Entry<String, Object> ft : Json.obj(info.get("FEATURE_SCORES")).entrySet()) {
            Map<String, Object> best = null;
            for (Object o : Json.arr(ft.getValue())) {
                Map<String, Object> s = Json.obj(o);
                if (best == null || num(s.get("SCORE")) > num(best.get("SCORE"))) best = s;
            }
            if (best == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feature", ft.getKey());
            row.put("this_side", best.get("INBOUND_FEAT_DESC"));
            row.put("other_side", best.get("CANDIDATE_FEAT_DESC"));
            row.put("score", best.get("SCORE"));
            row.put("bucket", best.get("SCORE_BUCKET"));
            scores.add(row);
        }
        out.put("feature_scores", scores);
        return out;
    }

    // ------------------------------------------------------------------ response helpers

    public static Map<String, Object> resolved(Map<String, Object> e) { return Json.obj(e.get("RESOLVED_ENTITY")); }

    public static long entityId(Map<String, Object> e) { return ((Number) resolved(e).get("ENTITY_ID")).longValue(); }

    public static String entityName(Map<String, Object> e) {
        Object n = resolved(e).get("ENTITY_NAME");
        return n == null ? "(no name returned)" : n.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> records(Map<String, Object> e) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : Json.arr(resolved(e).get("RECORDS"))) out.add((Map<String, Object>) o);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> related(Map<String, Object> e) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : Json.arr(e.get("RELATED_ENTITIES"))) out.add((Map<String, Object>) o);
        return out;
    }

    /** Data sources from a RECORD_SUMMARY (present on resolved and related entities under the default flags). */
    public static Set<String> sources(Map<String, Object> entityOrRelated) {
        Map<String, Object> holder = entityOrRelated.containsKey("RESOLVED_ENTITY")
            ? resolved(entityOrRelated) : entityOrRelated;
        Set<String> s = new TreeSet<>();
        for (Object o : Json.arr(holder.get("RECORD_SUMMARY"))) s.add(Json.str(Json.obj(o).get("DATA_SOURCE")));
        return s;
    }

    public static boolean isCustomer(Set<String> sources) {
        return sources.contains(PC) || sources.contains(LIFE);
    }

    public static String recordType(Map<String, Object> e) {
        for (Object o : Json.arr(Json.obj(resolved(e).get("FEATURES")).get("RECORD_TYPE"))) {
            Object d = Json.obj(o).get("FEAT_DESC");
            if (d != null) return d.toString();
        }
        return "UNKNOWN";
    }

    public static Map<String, Object> jsonData(Map<String, Object> record) { return Json.obj(record.get("JSON_DATA")); }

    public static double num(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v == null) return 0;
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    /** [this entity's role, related entity's role] from a disclosed-relationship match key, upper case. */
    public static String[] relRoles(String matchKey) {
        if (matchKey == null) return new String[] {"", ""};
        Matcher m = REL_ROLES.matcher(matchKey);
        if (!m.find()) return new String[] {"", ""};
        return new String[] {m.group(1).trim().toUpperCase(), m.group(2).trim().toUpperCase()};
    }

    /** Features with + (contributed) or - (detracted), with relationship-role parentheses removed. */
    public static Map<String, String> signedFeatures(String matchKey) {
        Map<String, String> out = new LinkedHashMap<>();
        if (matchKey == null) return out;
        Matcher m = SIGNED.matcher(matchKey.replaceAll("\\([^)]*\\)", ""));
        while (m.find()) out.put(m.group(2), m.group(1));
        return out;
    }

    public static boolean positive(String matchKey, String feature) {
        return "+".equals(signedFeatures(matchKey).get(feature));
    }

    /** True when a name feature contributed (NAME, PNAME, SURNAME, ...). */
    public static boolean nameAgreed(String matchKey) {
        for (Map.Entry<String, String> f : signedFeatures(matchKey).entrySet()) {
            if (f.getValue().equals("+") && f.getKey().contains("NAME")) return true;
        }
        return false;
    }

    /** True when the only contributing features are names. */
    public static boolean nameOnly(String matchKey) {
        Map<String, String> f = signedFeatures(matchKey);
        boolean any = false;
        for (Map.Entry<String, String> x : f.entrySet()) {
            if (!x.getValue().equals("+")) continue;
            if (!x.getKey().contains("NAME")) return false;
            any = true;
        }
        return any;
    }

    private static boolean ownerOrOfficer(String role) {
        for (String p : OWNER_OFFICER) if (role.startsWith(p)) return true;
        return false;
    }

    private static double ownedPct(String role) {
        Matcher m = PCT.matcher(role);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }

    private static int rank(String severity) { return SEVERITIES.indexOf(severity); }

    private static String worse(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return rank(a) <= rank(b) ? a : b;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    // ------------------------------------------------------------------ customer resolution

    /** Every customer entity, found by walking the customer records we loaded. Insertion order = first record. */
    public LinkedHashMap<Long, Map<String, Object>> customers() throws SzException {
        LinkedHashMap<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (String s : CUSTOMER_SOURCES) {
            for (String id : recordIds(s)) {
                Map<String, Object> e = entityByRecord(s, id);
                out.putIfAbsent(entityId(e), e);
            }
        }
        return out;
    }

    public Map<String, Object> resolutionSummary() throws SzException {
        LinkedHashMap<Long, Map<String, Object>> cust = customers();
        int records = 0;
        for (String s : CUSTOMER_SOURCES) records += recordIds(s).size();
        int both = 0, pcOnly = 0, lifeOnly = 0, persons = 0, orgs = 0, withOutside = 0;
        Map<String, Integer> dupEntities = new LinkedHashMap<>();
        Map<String, Integer> dupExtraRecords = new LinkedHashMap<>();
        List<Object> bothList = new ArrayList<>();
        for (Map<String, Object> e : cust.values()) {
            Set<String> src = sources(e);
            boolean p = src.contains(PC), l = src.contains(LIFE);
            if (p && l) {
                both++;
                bothList.add(Map.of("entity_id", entityId(e), "name", entityName(e)));
            } else if (p) pcOnly++;
            else lifeOnly++;
            if (recordType(e).equals("ORGANIZATION")) orgs++; else persons++;
            for (String o : OUTSIDE_SOURCES) if (src.contains(o)) { withOutside++; break; }
            Map<String, Integer> perSource = new HashMap<>();
            for (Map<String, Object> r : records(e)) perSource.merge(Json.str(r.get("DATA_SOURCE")), 1, Integer::sum);
            for (String s : CUSTOMER_SOURCES) {
                int n = perSource.getOrDefault(s, 0);
                if (n > 1) {
                    dupEntities.merge(s, 1, Integer::sum);
                    dupExtraRecords.merge(s, n - 1, Integer::sum);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customer_records", records);
        out.put("customer_records_by_source", Map.of(PC, recordIds(PC).size(), LIFE, recordIds(LIFE).size()));
        out.put("customers", cust.size());
        out.put("records_collapsed", records - cust.size());
        out.put("holds_both_pc_and_life", both);
        out.put("pc_only", pcOnly);
        out.put("life_only", lifeOnly);
        out.put("people", persons);
        out.put("organizations", orgs);
        out.put("customers_with_a_record_outside_customer_systems", withOutside);
        out.put("customers_held_more_than_once_in_one_system", dupEntities);
        out.put("duplicate_records_inside_each_system", dupExtraRecords);
        out.put("both_lines_customers", bothList);
        return out;
    }

    // ------------------------------------------------------------------ customer 360

    /**
     * One customer profile. It never shows SIU, screening list, credit bureau or business registry data: where
     * such a record is part of the customer, it says only that a record outside the customer systems exists.
     */
    public Map<String, Object> customerProfile(Map<String, Object> e) {
        Map<String, Object> p = new LinkedHashMap<>();
        Set<String> src = sources(e);
        p.put("entity_id", entityId(e));
        p.put("name", entityName(e));
        p.put("type", recordType(e));
        List<Object> pc = new ArrayList<>(), life = new ArrayList<>(), recs = new ArrayList<>();
        double premium = 0, face = 0, annuity = 0;
        Map<String, List<String>> perSource = new LinkedHashMap<>();
        boolean outside = false;
        for (Map<String, Object> r : records(e)) {
            String ds = Json.str(r.get("DATA_SOURCE"));
            Map<String, Object> d = jsonData(r);
            if (ds.equals(PC)) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("record_id", r.get("RECORD_ID"));
                for (String k : List.of("POLICY_NUMBER", "LINE_OF_BUSINESS", "INSURED_ROLE", "POLICY_STATUS",
                        "WRITTEN_PREMIUM", "EFFECTIVE_DATE", "EXPIRATION_DATE", "CANCELLATION_REASON", "CUSTOMER_TIER")) {
                    if (d.get(k) != null) x.put(k.toLowerCase(), d.get(k));
                }
                premium += num(d.get("WRITTEN_PREMIUM"));
                pc.add(x);
            } else if (ds.equals(LIFE)) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("record_id", r.get("RECORD_ID"));
                for (String k : List.of("CONTRACT_NUMBER", "PRODUCT_TYPE", "PARTY_ROLE", "CONTRACT_STATUS",
                        "FACE_AMOUNT", "ACCOUNT_VALUE", "MODAL_PREMIUM", "PREMIUM_MODE", "ISSUE_DATE", "CUSTOMER_TIER")) {
                    if (d.get(k) != null) x.put(k.toLowerCase(), d.get(k));
                }
                face += num(d.get("FACE_AMOUNT"));
                annuity += num(d.get("ACCOUNT_VALUE"));
                life.add(x);
            } else {
                outside = true;
                continue;   // never show what the outside record is
            }
            perSource.computeIfAbsent(ds, k -> new ArrayList<>()).add(Json.str(r.get("RECORD_ID")));
            Map<String, Object> rv = new LinkedHashMap<>();
            rv.put("source", ds);
            rv.put("record_id", r.get("RECORD_ID"));
            rv.put("match_key", r.get("MATCH_KEY"));
            rv.put("rule", r.get("ERRULE_CODE"));
            recs.add(rv);
        }
        p.put("pc_policies", pc);
        p.put("life_annuity_contracts", life);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("pc_written_premium", Math.round(premium * 100) / 100.0);
        totals.put("life_face_amount", Math.round(face * 100) / 100.0);
        totals.put("annuity_value", Math.round(annuity * 100) / 100.0);
        p.put("totals", totals);
        boolean hp = src.contains(PC), hl = src.contains(LIFE);
        p.put("cross_sell", hp && hl ? "Holds both lines" : hp ? "Life prospect (P&C only)" : "P&C prospect (life or annuity only)");
        List<Object> dups = new ArrayList<>();
        for (Map.Entry<String, List<String>> x : perSource.entrySet()) {
            if (x.getValue().size() > 1) dups.add(Map.of("source", x.getKey(), "record_ids", x.getValue()));
        }
        p.put("duplicates_inside_a_system", dups);
        p.put("record_outside_customer_systems",
            outside ? "A record outside the customer systems exists for this customer." : null);
        p.put("records", recs);
        List<Object> links = new ArrayList<>();
        for (Map<String, Object> rel : related(e)) {
            Set<String> rs = sources(rel);
            if (!isCustomer(rs)) continue;    // links to other customers only
            String level = Json.str(rel.get("MATCH_LEVEL_CODE"));
            String key = Json.str(rel.get("MATCH_KEY"));
            String label;
            if ("DISCLOSED".equals(level)) {
                String[] roles = relRoles(key);
                if (key.contains("REFERENCE(")) label = "Ownership or officer link (disclosed in a record outside the customer systems)";
                else label = "Disclosed link: " + (roles[1].isEmpty() ? roles[0] : roles[1]).toLowerCase().replace('_', ' ');
            } else if ("POSSIBLY_SAME".equals(level)) {
                label = "Possibly the same customer";
            } else if (positive(key, "ADDRESS")) {
                label = "Shares an address (household)";
            } else {
                label = "Possibly related";
            }
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("entity_id", rel.get("ENTITY_ID"));
            l.put("name", rel.get("ENTITY_NAME"));
            l.put("link", label);
            l.put("match_level", level);
            l.put("match_key", key);
            links.add(l);
        }
        p.put("links_to_other_customers", links);
        return p;
    }

    // ------------------------------------------------------------------ screening and due diligence

    private static Map<String, Object> customerEvidence(Map<String, Object> e) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> r : records(e)) {
            String ds = Json.str(r.get("DATA_SOURCE"));
            if (!CUSTOMER_SOURCES.contains(ds)) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("source", ds);
            x.put("record_id", r.get("RECORD_ID"));
            x.put("match_key", r.get("MATCH_KEY"));
            out.add(x);
        }
        return Map.of("customer_records", out);
    }

    private static Map<String, Object> pick(Map<String, Object> record, List<String> fields) {
        Map<String, Object> d = jsonData(record);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", record.get("DATA_SOURCE"));
        out.put("record_id", record.get("RECORD_ID"));
        out.put("record_match_key", record.get("MATCH_KEY"));
        for (String f : fields) if (d.get(f) != null) out.put(f.toLowerCase(), d.get(f));
        return out;
    }

    private static final List<String> LIST_FIELDS = List.of("LIST_TYPE", "LIST_NAME", "LISTED_DATE",
        "SANCTIONS_PROGRAM", "PEP_POSITION", "PEP_COUNTRY");
    private static final List<String> SIU_FIELDS = List.of("SIU_CASE_NUMBER", "REFERRAL_DATE", "FRAUD_TYPE",
        "ROLE_IN_CASE", "LINE_OF_BUSINESS", "CASE_DISPOSITION");

    private static Map<String, Object> recordIn(Map<String, Object> e, String source, String id) {
        for (Map<String, Object> r : records(e)) {
            if (source.equals(r.get("DATA_SOURCE")) && id.equals(Json.str(r.get("RECORD_ID")))) return r;
        }
        return Map.of("DATA_SOURCE", source, "RECORD_ID", id);
    }

    private final class Queue {
        final Map<Long, Map<String, Object>> alerts = new LinkedHashMap<>();

        @SuppressWarnings("unchecked")
        void add(long subjectId, String subjectName, String kind, Set<String> holdingSources,
                 Map<String, Object> finding) {
            Map<String, Object> a = alerts.computeIfAbsent(subjectId, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("alert_id", "A-" + subjectId);
                m.put("subject_entity_id", subjectId);
                m.put("subject_name", subjectName);
                m.put("subject_kind", kind);
                m.put("holds_pc", holdingSources.contains(PC));
                m.put("holds_life_or_annuity", holdingSources.contains(LIFE));
                m.put("severity", null);
                m.put("findings", new ArrayList<>());
                return m;
            });
            ((List<Object>) a.get("findings")).add(finding);
        }
    }

    private static Map<String, Object> finding(String type, String severity, String rule, String summary) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", type);
        f.put("severity", severity);
        f.put("rule", rule);
        f.put("summary", summary);
        return f;
    }

    /** The screening and due diligence queue, built from the list, SIU and bureau records we loaded. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> screening() throws SzException {
        Queue q = new Queue();
        Map<Long, String> listed = new LinkedHashMap<>();          // listed entity -> Sanctions | PEP
        List<Object> notRaised = new ArrayList<>();                // relationships we deliberately did not alert on

        // 1. Sanctions and PEP list entries
        for (String id : recordIds("SANCTIONS_PEP")) {
            Map<String, Object> e = entityByRecord("SANCTIONS_PEP", id);
            Map<String, Object> listRec = recordIn(e, "SANCTIONS_PEP", id);
            String listType = Json.str(jsonData(listRec).get("LIST_TYPE"));
            boolean sanctions = "Sanctions".equalsIgnoreCase(listType);
            long eid = entityId(e);
            listed.merge(eid, sanctions ? "Sanctions" : "PEP", (a, b) -> a.equals("Sanctions") ? a : b);
            Set<String> src = sources(e);
            Map<String, Object> entry = pick(listRec, LIST_FIELDS);
            if (isCustomer(src)) {
                String sev = sanctions ? "PROHIBITED" : (src.contains(LIFE) ? "HIGH" : "LOW");
                String rule = sanctions ? "Sanctions: a customer who resolves to a sanctions entry is Prohibited"
                    : (src.contains(LIFE) ? "PEP on a customer holding a life or annuity contract is High"
                                          : "PEP on a P&C-only customer is informational (Low)");
                Map<String, Object> f = finding(sanctions ? "SANCTIONS" : "PEP", sev, rule,
                    "Customer resolves to " + listType + " list entry " + id);
                Map<String, Object> ev = new LinkedHashMap<>(customerEvidence(e));
                ev.put("list_entry", entry);
                f.put("evidence", ev);
                q.add(eid, entityName(e), "customer", src, f);
            }
            possibleMatches(q, e, listType, entry, "list_entry", notRaised);
        }

        // 2. SIU cases
        for (String id : recordIds("SIU_CASES")) {
            Map<String, Object> e = entityByRecord("SIU_CASES", id);
            Map<String, Object> rec = recordIn(e, "SIU_CASES", id);
            String disp = Json.str(jsonData(rec).get("CASE_DISPOSITION"));
            Set<String> src = sources(e);
            Map<String, Object> caseInfo = pick(rec, SIU_FIELDS);
            if (isCustomer(src)) {
                boolean high = disp != null && (disp.startsWith("Substantiated") || disp.startsWith("Referred"));
                Map<String, Object> f = finding("SIU", high ? "HIGH" : "MEDIUM",
                    high ? "SIU history Substantiated or Referred to Law Enforcement / DOI is High"
                         : "SIU history Open or Unsubstantiated is Medium",
                    "Customer resolves to SIU case " + jsonData(rec).get("SIU_CASE_NUMBER") + " (" + disp + ")");
                Map<String, Object> ev = new LinkedHashMap<>(customerEvidence(e));
                ev.put("siu_case", caseInfo);
                f.put("evidence", ev);
                q.add(entityId(e), entityName(e), "customer", src, f);
            }
            possibleMatches(q, e, "SIU", caseInfo, "siu_case", notRaised);
        }

        // 3. Identity verification against credit bureau files
        for (String id : recordIds("CREDIT_BUREAU")) {
            Map<String, Object> e = entityByRecord("CREDIT_BUREAU", id);
            long eid = entityId(e);
            Set<Long> shared = new LinkedHashSet<>();
            List<Map<String, Object>> conflicts = new ArrayList<>();
            if (isCustomer(sources(e))) {
                shared.add(eid);
                for (Map<String, Object> r : records(e)) {
                    String k = Json.str(r.get("MATCH_KEY"));
                    if (CUSTOMER_SOURCES.contains(r.get("DATA_SOURCE")) && positive(k, "SSN")
                            && ("-".equals(signedFeatures(k).get("DOB")) || !nameAgreed(k))) {
                        conflicts.add(Map.of("entity_id", eid, "name", entityName(e), "match_key", k,
                            "how", "in the same entity"));
                    }
                }
            }
            for (Map<String, Object> rel : related(e)) {
                String k = Json.str(rel.get("MATCH_KEY"));
                if (!positive(k, "SSN") || !isCustomer(sources(rel))) continue;
                long rid = ((Number) rel.get("ENTITY_ID")).longValue();
                shared.add(rid);
                if ("-".equals(signedFeatures(k).get("DOB")) || !nameAgreed(k)) {
                    conflicts.add(Map.of("entity_id", rid, "name", Json.str(rel.get("ENTITY_NAME")), "match_key", k,
                        "how", Json.str(rel.get("MATCH_LEVEL_CODE"))));
                }
            }
            if (conflicts.isEmpty()) continue;
            boolean synthetic = shared.size() >= 2;
            for (Map<String, Object> c : conflicts) {
                long cid = ((Number) c.get("entity_id")).longValue();
                Map<String, Object> ce = entity(cid);
                Map<String, Object> f = finding("IDENTITY", synthetic ? "HIGH" : "MEDIUM",
                    synthetic ? "Several customers on one bureau file is a synthetic identity signal (High)"
                              : "One customer whose SSN lines up with a bureau file while DOB or name conflicts (Medium)",
                    "SSN agrees with credit bureau file " + id + " but "
                        + ("-".equals(signedFeatures(Json.str(c.get("match_key"))).get("DOB")) ? "date of birth" : "name")
                        + " conflicts; " + shared.size() + " customer(s) share this file");
                Map<String, Object> ev = new LinkedHashMap<>(customerEvidence(ce));
                ev.put("bureau_record", Map.of("source", "CREDIT_BUREAU", "record_id", id));
                ev.put("match_key", c.get("match_key"));
                ev.put("customers_sharing_the_file", new ArrayList<>(shared));
                f.put("evidence", ev);
                q.add(cid, entityName(ce), "customer", sources(ce), f);
            }
        }

        // 4. Ownership screening (owners and officers, directly or through the parent; roll up to the parent)
        Map<Long, List<Map<String, Object>>> exposures = new LinkedHashMap<>();   // business -> listed owners/officers
        for (Map.Entry<Long, String> l : listed.entrySet()) {
            Map<String, Object> le = entity(l.getKey());
            for (Map<String, Object> rel : related(le)) {
                if (!"DISCLOSED".equals(rel.get("MATCH_LEVEL_CODE"))) continue;
                String key = Json.str(rel.get("MATCH_KEY"));
                String myRole = relRoles(key)[0];
                if (!ownerOrOfficer(myRole)) continue;
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("listed_entity_id", l.getKey());
                x.put("listed_name", entityName(le));
                x.put("list_type", l.getValue());
                x.put("role", myRole);
                x.put("owned_pct", ownedPct(myRole));
                x.put("relationship_match_key", key);
                exposures.computeIfAbsent(((Number) rel.get("ENTITY_ID")).longValue(), k -> new ArrayList<>()).add(x);
            }
        }
        Set<Long> done = new HashSet<>();
        for (Long business : exposures.keySet()) {
            if (done.contains(business)) continue;
            Map<Long, Long> parentOf = new LinkedHashMap<>();
            List<Long> group = group(business, parentOf);
            done.addAll(group);
            Set<String> groupSources = new TreeSet<>();
            List<Object> members = new ArrayList<>();
            for (Long m : group) {
                Map<String, Object> me = entity(m);
                Set<String> ms = sources(me);
                if (isCustomer(ms)) groupSources.addAll(ms);
                Map<String, Object> mv = new LinkedHashMap<>();
                mv.put("entity_id", m);
                mv.put("name", entityName(me));
                mv.put("is_customer", isCustomer(ms));
                mv.put("parent_entity_id", parentOf.get(m));
                members.add(mv);
            }
            if (!isCustomer(groupSources)) {
                notRaised.add(Map.of("reason", "listed owner or officer of a business group with no customer in it",
                    "business_entity_id", business));
                continue;
            }
            long top = group.get(0);
            for (Long m : group) if (parentOf.get(m) == null) { top = m; break; }
            Map<String, Object> topE = entity(top);
            for (Long b : group) {
                List<Map<String, Object>> xs = exposures.get(b);
                if (xs == null) continue;
                double sanctionedPct = 0;
                for (Map<String, Object> x : xs) if ("Sanctions".equals(x.get("list_type"))) sanctionedPct += num(x.get("owned_pct"));
                for (Map<String, Object> x : xs) {
                    boolean sanctioned = "Sanctions".equals(x.get("list_type"));
                    String sev, rule;
                    boolean owner = Json.str(x.get("role")).startsWith("OWN");
                    if (sanctioned && owner && sanctionedPct >= 50) {
                        sev = "PROHIBITED";
                        rule = "OFAC 50% rule: sanctioned owners hold " + (int) sanctionedPct + "% of the business";
                    } else if (sanctioned) {
                        sev = "HIGH";
                        rule = "Sanctioned owner or officer below the 50% rule (treated as High; see screening_rules.md)";
                    } else if (groupSources.contains(LIFE)) {
                        sev = "HIGH";
                        rule = "PEP owner or officer of a business holding a life or annuity contract is High";
                    } else {
                        sev = "LOW";
                        rule = "PEP owner or officer of a P&C-only business is informational (Low)";
                    }
                    String role = Json.str(x.get("role")).toLowerCase();
                    Map<String, Object> f = finding("OWNERSHIP", sev, rule,
                        x.get("listed_name") + " (" + x.get("list_type") + ") " + (owner ? role : "is " + role)
                            + " of " + entityName(entity(b)) + (b == top ? "" : ", which rolls up to " + entityName(topE)));
                    Map<String, Object> ev = new LinkedHashMap<>();
                    ev.put("business_entity_id", b);
                    ev.put("relationship_match_key", x.get("relationship_match_key"));
                    ev.put("listed_entity_id", x.get("listed_entity_id"));
                    Map<String, Object> le = entity(((Number) x.get("listed_entity_id")).longValue());
                    for (Map<String, Object> r : records(le)) {
                        if ("SANCTIONS_PEP".equals(r.get("DATA_SOURCE"))) { ev.put("list_entry", pick(r, LIST_FIELDS)); break; }
                    }
                    ev.put("group_members", members);
                    f.put("evidence", ev);
                    q.add(top, entityName(topE), group.size() > 1 ? "business group" : "business", groupSources, f);
                }
            }
        }

        // 5. Fold possible matches into an existing hit, and rate each alert by its most severe finding
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        for (String s : SEVERITIES) bySeverity.put(s, 0);
        List<Map<String, Object>> alerts = new ArrayList<>(q.alerts.values());
        for (Map<String, Object> a : alerts) {
            List<Map<String, Object>> fs = (List<Map<String, Object>>) (List<?>) a.get("findings");
            boolean hasHit = fs.stream().anyMatch(f -> !"POSSIBLE_MATCH".equals(f.get("type")));
            String sev = null;
            for (Map<String, Object> f : fs) {
                if (hasHit && "POSSIBLE_MATCH".equals(f.get("type"))) {
                    f.put("folded_into_hit", true);
                    continue;
                }
                sev = worse(sev, Json.str(f.get("severity")));
            }
            a.put("severity", sev);
            a.put("analyst_decision_needed", fs.stream().anyMatch(f -> "POSSIBLE_MATCH".equals(f.get("type"))
                && !Boolean.TRUE.equals(f.get("folded_into_hit"))));
            bySeverity.merge(sev, 1, Integer::sum);
        }
        alerts.sort(Comparator.comparingInt((Map<String, Object> a) -> rank(Json.str(a.get("severity"))))
            .thenComparing(a -> Json.str(a.get("subject_name"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alerts_total", alerts.size());
        out.put("alerts_by_severity", bySeverity);
        out.put("alerts", alerts);
        out.put("innocent_explanations", innocent(alerts, notRaised));
        return out;
    }

    /** POSSIBLY_SAME relations from a list or SIU entity to customer entities become possible matches. */
    private void possibleMatches(Queue q, Map<String, Object> e, String listType, Map<String, Object> entry,
                                 String entryKey, List<Object> notRaised) throws SzException {
        long eid = entityId(e);
        for (Map<String, Object> rel : related(e)) {
            Set<String> rs = sources(rel);
            if (!isCustomer(rs)) continue;
            String level = Json.str(rel.get("MATCH_LEVEL_CODE"));
            String key = Json.str(rel.get("MATCH_KEY"));
            long rid = ((Number) rel.get("ENTITY_ID")).longValue();
            if (!"POSSIBLY_SAME".equals(level)) {
                if (!"DISCLOSED".equals(level)) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("reason", "related but not the same person (" + level + ")");
                    n.put("from", listType + " " + entry.get("record_id"));
                    n.put("customer_entity_id", rid);
                    n.put("customer_name", rel.get("ENTITY_NAME"));
                    n.put("match_key", key);
                    notRaised.add(n);
                }
                continue;
            }
            boolean pepPcOnly = "PEP".equalsIgnoreCase(listType) && !rs.contains(LIFE);
            Map<String, Object> f = finding("POSSIBLE_MATCH", pepPcOnly ? "LOW" : "MEDIUM",
                pepPcOnly ? "Possible PEP match on a P&C-only customer is Low; an analyst decides"
                          : "Possible match: an analyst decides true match or false positive (Medium)",
                "Possible match to " + listType + " " + entry.get("record_id")
                    + (nameOnly(key) ? " on name only, which is never a hit by itself" : ""));
            Map<String, Object> ev = new LinkedHashMap<>(customerEvidence(entity(rid)));
            ev.put(entryKey, entry);
            ev.put("match_key", key);
            ev.put("why", why(eid, rid));
            f.put("evidence", ev);
            q.add(rid, Json.str(rel.get("ENTITY_NAME")), "customer", rs, f);
        }
    }

    /** Parent/subsidiary group around a business, through disclosed relationships. Fills parentOf. */
    private List<Long> group(long start, Map<Long, Long> parentOf) throws SzException {
        List<Long> order = new ArrayList<>();
        Deque<Long> todo = new ArrayDeque<>(List.of(start));
        Set<Long> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            long x = todo.poll();
            if (!seen.add(x)) continue;
            order.add(x);
            for (Map<String, Object> rel : related(entity(x))) {
                if (!"DISCLOSED".equals(rel.get("MATCH_LEVEL_CODE"))) continue;
                String[] roles = relRoles(Json.str(rel.get("MATCH_KEY")));
                long other = ((Number) rel.get("ENTITY_ID")).longValue();
                Boolean otherIsParent = null;
                if (HOLDER_IS_SUBSIDIARY.contains(roles[1])) otherIsParent = false;
                else if (HOLDER_IS_SUBSIDIARY.contains(roles[0])) otherIsParent = true;
                else if (HOLDER_IS_PARENT.contains(roles[1])) otherIsParent = true;
                else if (HOLDER_IS_PARENT.contains(roles[0])) otherIsParent = false;
                if (otherIsParent == null) continue;
                if (otherIsParent) parentOf.put(x, other); else parentOf.put(other, x);
                todo.add(other);
            }
        }
        for (Long m : order) parentOf.putIfAbsent(m, null);
        return order;
    }

    /** Evidence that the innocent explanations raised no hit. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> innocent(List<Map<String, Object>> alerts, List<Object> notRaised) throws SzException {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> alertBySubject = new HashMap<>();
        List<Object> nameOnlyItems = new ArrayList<>();
        for (Map<String, Object> a : alerts) {
            alertBySubject.put(((Number) a.get("subject_entity_id")).longValue(), a);
            for (Map<String, Object> f : (List<Map<String, Object>>) (List<?>) a.get("findings")) {
                Object mk = Json.obj(f.get("evidence")).get("match_key");
                if ("POSSIBLE_MATCH".equals(f.get("type")) && nameOnly(Json.str(mk))) {
                    nameOnlyItems.add(Map.of("subject", a.get("subject_name"), "match_key", mk,
                        "severity", f.get("severity")));
                }
            }
        }
        out.put("name_only_matches_sent_to_analyst_not_hits", nameOnlyItems);
        out.put("relationships_not_raised", notRaised);
        List<Object> smiths = new ArrayList<>();
        for (Map<String, Object> e : customers().values()) {
            String n = entityName(e).toUpperCase();
            if (!(n.contains("SMITH"))) continue;
            Map<String, Object> a = alertBySubject.get(entityId(e));
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("entity_id", entityId(e));
            s.put("name", entityName(e));
            if (a == null) s.put("alert", "none");
            else {
                List<String> types = new ArrayList<>();
                for (Map<String, Object> f : (List<Map<String, Object>>) (List<?>) a.get("findings")) types.add(Json.str(f.get("type")));
                s.put("alert", a.get("severity") + " " + types);
            }
            smiths.add(s);
        }
        out.put("common_name_customers", smiths);
        return out;
    }

    // ------------------------------------------------------------------ search

    /**
     * Attribute search by name. Tries NAME_FULL, then NAME_ORG (an organization name sent as NAME_FULL can
     * match nothing without raising an error); a failing attribute is retried past, and results are merged by
     * ENTITY_ID. SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS carries RECORD_SUMMARY and MATCH_INFO, which is all
     * this reads.
     */
    public Map<String, Object> searchName(String name) {
        Map<Long, Map<String, Object>> hits = new LinkedHashMap<>();
        List<String> tried = new ArrayList<>(), errors = new ArrayList<>();
        for (String attr : List.of("NAME_FULL", "NAME_ORG")) {
            tried.add(attr);
            try {
                String result = engine.searchByAttributes(Json.write(Map.of(attr, name)),
                    SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
                for (Object o : Json.arr(Json.parseObject(result).get("RESOLVED_ENTITIES"))) {
                    Map<String, Object> r = Json.obj(o);
                    Map<String, Object> re = Json.obj(Json.obj(r.get("ENTITY")).get("RESOLVED_ENTITY"));
                    Map<String, Object> mi = Json.obj(r.get("MATCH_INFO"));
                    long id = ((Number) re.get("ENTITY_ID")).longValue();
                    if (hits.containsKey(id)) continue;
                    Map<String, Object> h = new LinkedHashMap<>();
                    h.put("entity_id", id);
                    h.put("name", re.get("ENTITY_NAME"));
                    h.put("sources", sources(Map.of("RECORD_SUMMARY", Json.arr(re.get("RECORD_SUMMARY")))));
                    h.put("match_level", mi.get("MATCH_LEVEL_CODE"));
                    h.put("match_key", mi.get("MATCH_KEY"));
                    h.put("searched_with", attr);
                    hits.put(id, h);
                }
            } catch (SzException e) {
                errors.add(attr + ": " + e.getMessage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", name);
        out.put("attributes_tried", tried);
        out.put("results", new ArrayList<>(hits.values()));
        if (hits.isEmpty()) out.put("note", "nothing matched the attributes tried: " + tried);
        if (!errors.isEmpty()) out.put("errors", errors);
        return out;
    }

    public static void writeJson(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, Json.write(value), StandardCharsets.UTF_8);
    }
}
