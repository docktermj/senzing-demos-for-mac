package server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.*;

import com.senzing.sdk.SzEngine;

import query.InsuranceModel;

/**
 * The three business tabs the bootcamper asked for, served beside the six Senzing tabs in ONE app:
 *   Overview                    /api/overview
 *   Customer 360                /api/customers      (never shows SIU, list, bureau or registry data)
 *   Screening & Due Diligence   /api/alerts, /api/whypair?a=&b=, POST /api/disposition
 *
 * All entity facts come from the query model (src/query/InsuranceModel.java), which reads Senzing through
 * the SDK. Analyst dispositions (True match, False positive, Refer to SIU) are stored by this app in
 * data/app/dispositions.json and are NEVER written to Senzing.
 */
public final class BusinessTabs {

    public static final List<String> DECISIONS = List.of("True match", "False positive", "Refer to SIU");

    private final InsuranceModel model;
    private final Map<String, Object> resolution;
    private final List<Object> customers = new ArrayList<>();
    private final Map<String, Object> screening;
    private final Path dispositionsFile;
    private final Map<String, Object> overlap;
    private final Map<String, Object> modelStats;

    public BusinessTabs(SzEngine engine, Map<String, Object> modelStats, Map<String, Object> overlap,
                        Path dispositionsFile) throws Exception {
        this.model = InsuranceModel.over(engine);   // the server owns the environment
        this.resolution = model.resolutionSummary();
        for (Map<String, Object> e : model.customers().values()) customers.add(model.customerProfile(e));
        this.screening = model.screening();
        this.dispositionsFile = dispositionsFile;
        this.overlap = overlap;
        this.modelStats = modelStats;
    }

    public Map<String, Object> overview() {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("records_total", modelStats.get("records_total"));
        o.put("entities_total", modelStats.get("entities_total"));
        o.put("cross_source_entities", modelStats.get("cross_source_entities"));
        o.put("relationships_total", modelStats.get("relationships_total"));
        List<Object> sources = new ArrayList<>();
        List<Object> codes = Json.arr(overlap.get("sources"));
        List<Object> matrix = Json.arr(overlap.get("matrix"));
        for (int i = 0; i < codes.size(); i++) {
            String code = Json.str(codes.get(i));
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("source", code);
            s.put("records", model.recordIds(code).size());
            s.put("entities", Json.arr(matrix.get(i)).get(i));
            s.put("role", InsuranceModel.CUSTOMER_SOURCES.contains(code) ? "Customer system" : "Screening and due diligence input");
            sources.add(s);
        }
        o.put("sources", sources);
        o.put("resolution", resolution);
        o.put("alerts_total", screening.get("alerts_total"));
        o.put("alerts_by_severity", screening.get("alerts_by_severity"));
        Map<String, Object> d = dispositionsByAlert();
        o.put("alerts_decided", d.size());
        return o;
    }

    public List<Object> customers() { return customers; }

    /** The queue, each alert carrying its latest disposition and history. */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> alerts() {
        Map<String, Object> out = new LinkedHashMap<>(screening);
        Map<String, Object> byAlert = dispositionsByAlert();
        List<Object> alerts = new ArrayList<>();
        for (Object o : Json.arr(screening.get("alerts"))) {
            Map<String, Object> a = new LinkedHashMap<>((Map<String, Object>) o);
            List<Object> hist = Json.arr(byAlert.get(Json.str(a.get("alert_id"))));
            a.put("disposition_history", hist);
            a.put("disposition", hist.isEmpty() ? null : hist.get(hist.size() - 1));
            alerts.add(a);
        }
        out.put("alerts", alerts);
        out.put("decisions", DECISIONS);
        out.put("dispositions_file", dispositionsFile.toString());
        return out;
    }

    public Map<String, Object> whyPair(String a, String b) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.putAll(model.why(Long.parseLong(a.trim()), Long.parseLong(b.trim())));
        } catch (Exception e) {
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return out;
    }

    private Map<String, Object> dispositionsByAlert() {
        try {
            if (!Files.exists(dispositionsFile)) return new LinkedHashMap<>();
            return Json.obj(Json.parseObject(Files.readString(dispositionsFile, StandardCharsets.UTF_8)).get("by_alert"));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** Store one analyst decision. Appends to the alert's history; the latest entry is the disposition. */
    public synchronized Map<String, Object> saveDisposition(String body) throws Exception {
        Map<String, Object> req = Json.parseObject(body);
        String alertId = Json.str(req.get("alert_id"));
        String decision = Json.str(req.get("decision"));
        Map<String, Object> out = new LinkedHashMap<>();
        boolean known = false;
        for (Object o : Json.arr(screening.get("alerts"))) if (Objects.equals(Json.obj(o).get("alert_id"), alertId)) known = true;
        if (!known) { out.put("error", "unknown alert_id"); return out; }
        if (!DECISIONS.contains(decision)) { out.put("error", "decision must be one of " + DECISIONS); return out; }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("decision", decision);
        String note = Json.str(req.get("note"));
        entry.put("note", note == null ? "" : note.length() > 2000 ? note.substring(0, 2000) : note);
        String analyst = Json.str(req.get("analyst"));
        entry.put("analyst", analyst == null || analyst.isBlank() ? "analyst" : analyst.trim());
        entry.put("at", OffsetDateTime.now().toString());
        Map<String, Object> all = dispositionsByAlert();
        List<Object> hist = new ArrayList<>(Json.arr(all.get(alertId)));
        hist.add(entry);
        all.put(alertId, hist);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("note", "Analyst dispositions recorded by the web app. Never written to Senzing.");
        doc.put("by_alert", all);
        Files.createDirectories(dispositionsFile.getParent());
        Path tmp = dispositionsFile.resolveSibling(dispositionsFile.getFileName() + ".tmp");
        Files.writeString(tmp, Json.write(doc), StandardCharsets.UTF_8);
        Files.move(tmp, dispositionsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        out.put("alert_id", alertId);
        out.put("disposition", entry);
        out.put("disposition_history", hist);
        return out;
    }

    /** Data for the snapshot, so the three tabs work offline (decisions are read-only there). */
    public Map<String, Object> snapshotPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("overview", overview());
        p.put("customers", customers());
        p.put("alerts", alerts());
        return p;
    }

    // ------------------------------------------------------------------ page injection

    /** Adds the three tabs to the generated six-tab page without editing the generated template. */
    public static String inject(String page, String fragment) {
        String tabs = "const ALL_TABS=[[\"overview\",\"Overview\"],[\"c360\",\"Customer 360\"],"
            + "[\"screen\",\"Screening & Due Diligence\"],";
        String sections = "<main>\n  <section class=\"tab\" id=\"tab-overview\"><div id=\"overview\"></div></section>\n"
            + "  <section class=\"tab\" id=\"tab-c360\"><div id=\"c360\"></div></section>\n"
            + "  <section class=\"tab\" id=\"tab-screen\"><div id=\"screen\"></div></section>";
        String out = requireReplace(page, "const ALL_TABS=[", tabs);
        out = requireReplace(out, "<main>", sections);
        out = requireReplace(out, "<section class=\"tab active\" id=\"tab-graph\">", "<section class=\"tab\" id=\"tab-graph\">");
        out = requireReplace(out, "function drawFor(id){",
            "function drawFor(id){if(typeof drawBusiness===\"function\"&&drawBusiness(id))return;");
        out = requireReplace(out, "buildNav();drawGraph();", "buildNav();drawFor(\"overview\");");
        out = requireReplace(out, "<div class=\"modal-bg\"", fragment + "\n<div class=\"modal-bg\"");
        return out;
    }

    private static String requireReplace(String s, String target, String replacement) {
        int i = s.indexOf(target);
        if (i < 0) throw new IllegalStateException("page template changed: cannot find " + target);
        return s.substring(0, i) + replacement + s.substring(i + target.length());
    }
}
