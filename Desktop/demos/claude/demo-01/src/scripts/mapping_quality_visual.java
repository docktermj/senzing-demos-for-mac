// mapping_quality_visual.java -- Data Quality, Mapping, and Transformation, Phase 2 Step 15 visual.
//
// Renders one mapped source's quality analysis as a self-contained HTML page: matching-feature
// coverage measured from the mapper's output JSONL, attribute coverage, payload coverage, and the
// field mapping summary from the mapping spec validated by mapping_workflow step 3. Plain HTML/CSS
// bars, no scripts and no network fetches, so it renders offline. Colors and fonts come from the
// Senzing brand tokens in src/server/assets/brand.json. Every data-sourced string (field names,
// attributes, notes) is HTML-escaped. Reusable for every source.
//
// Usage (after javac into src/build/classes; run from the project root):
//   src/scripts/run-java.sh MappingQualityVisual <DATA_SOURCE> <mapping_spec.json> <output.jsonl> <out.html>
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class MappingQualityVisual {
    /** Feature family of an attribute, for grouping the coverage bars. */
    static String family(String attr) {
        if (attr.startsWith("NAME_")) { return "NAME"; }
        if (attr.startsWith("ADDR_")) { return "ADDRESS"; }
        if (attr.startsWith("PHONE_")) { return "PHONE"; }
        if (attr.startsWith("EMAIL_")) { return "EMAIL"; }
        return attr;
    }

    static String pct(long n, long total) { return String.valueOf(Math.round(1000.0 * n / total) / 10.0); }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: MappingQualityVisual <DATA_SOURCE> <mapping_spec.json> <output.jsonl> <out.html>");
            System.exit(2);
        }
        String source = args[0];
        Map<String, Object> spec = Json.obj(Json.parse(Files.readString(Path.of(args[1]))));
        Map<String, Object> qa = Json.obj(Json.parse(Files.readString(Path.of("data/mapping/quality_assessment.json"))));
        Map<String, Object> brand = Json.obj(Json.parse(Files.readString(Path.of("src/server/assets/brand.json"))));

        // Measure the output: records carrying each feature family / attribute / payload key.
        long total = 0;
        Map<String, Long> families = new LinkedHashMap<>(), attrs = new LinkedHashMap<>(), payload = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8)) {
            if (line.isBlank()) { continue; }
            total++;
            Map<String, Object> rec = Json.obj(Json.parse(line));
            java.util.Set<String> famSeen = new java.util.HashSet<>(), attrSeen = new java.util.HashSet<>();
            for (Object f : Json.arr(rec.get("FEATURES"))) {
                for (String a : Json.obj(f).keySet()) {
                    if (a.equals("RECORD_TYPE")) { continue; }
                    attrSeen.add(a);
                    famSeen.add(family(a));
                }
            }
            for (String fam : famSeen) { families.merge(fam, 1L, Long::sum); }
            for (String a : attrSeen) { attrs.merge(a, 1L, Long::sum); }
            for (String k : rec.keySet()) {
                if (!List.of("DATA_SOURCE", "RECORD_ID", "FEATURES").contains(k)) { payload.merge(k, 1L, Long::sum); }
            }
        }

        StringBuilder cov = new StringBuilder();
        for (Map.Entry<String, Long> e : families.entrySet()) {
            cov.append(Html.bar(e.getKey(), Double.parseDouble(pct(e.getValue(), total)), ""));
        }
        StringBuilder attrBars = new StringBuilder();
        for (Map.Entry<String, Long> e : attrs.entrySet()) {
            attrBars.append(Html.bar(e.getKey(), Double.parseDouble(pct(e.getValue(), total)), ""));
        }
        StringBuilder payBars = new StringBuilder();
        for (Map.Entry<String, Long> e : payload.entrySet()) {
            payBars.append(Html.bar(e.getKey(), Double.parseDouble(pct(e.getValue(), total)), ""));
        }

        StringBuilder table = new StringBuilder("<table><tr><th>Source field</th><th>Disposition</th><th>Senzing attribute</th><th>Notes</th></tr>");
        long mapped = 0, payloadCount = 0, ignored = 0;
        for (Object sm : Json.arr(spec.get("schema_mappings"))) {
            for (Object o : Json.arr(Json.obj(sm).get("field_mappings"))) {
                Map<String, Object> m = Json.obj(o);
                String disp = Json.str(m.get("disposition"), "");
                String field = Json.str(m.get("source_field"), "");
                String target;
                switch (disp) {
                    case "feature" -> { target = Json.str(m.get("attribute"), ""); mapped++; }
                    case "derived" -> {
                        target = Json.str(m.get("derived_as"), "");
                        field = m.containsKey("source") ? Json.str(m.get("source"), "") : "(constant " + Json.str(m.get("value"), "") + ")";
                    }
                    case "payload" -> { target = "(kept as payload)"; payloadCount++; }
                    default -> { target = "&mdash;"; ignored++; }
                }
                String note = Json.str(m.containsKey("justification") ? m.get("justification")
                    : m.containsKey("reason") ? m.get("reason") : m.get("ref"), "");
                table.append("<tr><td class=\"code\">").append(Html.esc(field)).append("</td><td><span class=\"disp ")
                    .append(Html.esc(disp)).append("\">").append(Html.esc(disp)).append("</span></td><td class=\"code\">")
                    .append(target.equals("&mdash;") ? target : Html.esc(target)).append("</td><td class=\"muted\">")
                    .append(Html.esc(note)).append("</td></tr>");
            }
        }
        table.append("</table>");

        double score = ((Number) Json.obj(qa.get(source)).get("quality_score")).doubleValue();
        String css = ":root{" + Json.str(brand.get("root_vars"), "") + "}"
            + "body{margin:0;background:var(--bg);color:var(--ink);font-family:" + Json.str(brand.get("font"), "sans-serif") + "}"
            + "header{background:var(--navy);color:#fff;padding:18px 28px;border-bottom:4px solid var(--blue)}"
            + "header h1{margin:0;font-size:22px}header p{margin:4px 0 0;opacity:.8;font-size:13px}"
            + "main{display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:18px;padding:22px 28px}"
            + ".card{background:#fff;border:1px solid var(--line);border-radius:10px;padding:16px 20px}.wide{grid-column:1/-1}"
            + "h2{margin:0 0 6px;font-size:17px}h3{margin:16px 0 8px;font-size:13px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}"
            + ".score{display:flex;align-items:center;gap:12px}.big{font-size:34px;font-weight:700;color:var(--blue)}"
            + ".band{font-size:12px;padding:3px 9px;border-radius:12px;border:1px solid var(--line)}"
            + ".band.ok{color:var(--green);border-color:var(--green)}.band.warn{color:var(--gold);border-color:var(--gold)}.band.bad{color:#B3261E;border-color:#B3261E}"
            + ".muted{color:var(--muted);font-size:12px}"
            + ".row{display:grid;grid-template-columns:150px 1fr 110px;align-items:center;gap:10px;margin:5px 0;font-size:13px}"
            + ".lbl,.code{font-family:" + Json.str(brand.get("code_font"), "monospace") + ";font-size:12px}"
            + ".track{background:var(--accent-soft);border-radius:5px;height:14px;overflow:hidden}"
            + ".fill{height:100%}.fill.hi{background:var(--blue)}.fill.mid{background:var(--gold)}.fill.lo{background:#B3261E}"
            + ".note{color:var(--muted);font-size:11px}"
            + ".disp{font-size:11px;padding:2px 7px;border-radius:10px;background:var(--accent-soft)}"
            + ".disp.feature{background:var(--blue);color:#fff}.disp.derived{background:var(--navy);color:#fff}"
            + "table{border-collapse:collapse;font-size:13px;width:100%}td,th{border-bottom:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}"
            + ".stats{display:flex;gap:22px;margin-top:10px}.stats b{display:block;font-size:22px;color:var(--ink)}";
        String html = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>Mapping Quality: " + Html.esc(source) + "</title><style>" + css + "</style></head><body>"
            + "<header><h1>Mapping Quality &mdash; " + Html.esc(source) + "</h1><p>Measured from "
            + Html.esc(args[2]) + " (" + total + " records). Structurally validated; match behavior is confirmed after loading.</p></header>"
            + "<main><section class=\"card\"><h2>Overview</h2><div class=\"score\"><span class=\"big\">" + score + "</span>"
            + Html.band(score) + "</div><p class=\"muted\">Source quality score (Phase 1 assessment)</p>"
            + "<div class=\"stats\"><span class=\"muted\"><b>" + total + "</b>records mapped</span><span class=\"muted\"><b>" + mapped
            + "</b>fields used for matching</span><span class=\"muted\"><b>" + payloadCount + "</b>payload</span><span class=\"muted\"><b>"
            + ignored + "</b>ignored</span></div>"
            + "<h3>Matching features (records carrying each)</h3>" + cov + "</section>"
            + "<section class=\"card\"><h2>Attribute coverage</h2>" + attrBars
            + "<h3>Payload (kept, not used for matching)</h3>" + payBars + "</section>"
            + "<section class=\"card wide\"><h2>Field mapping summary</h2>" + table + "</section></main></body></html>";
        Path out = Path.of(args[3]);
        Files.createDirectories(out.getParent());
        Files.writeString(out, html, StandardCharsets.UTF_8);
        System.out.println("written " + out + " (" + html.length() + " chars, " + total + " records)");
    }
}
