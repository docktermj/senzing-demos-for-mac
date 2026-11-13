// quality_visual.java -- Data Quality, Mapping, and Transformation, Phase 1 Step 6 visual.
//
// Renders data/mapping/quality_assessment.json (written by assess_quality.java) as a
// self-contained HTML page: per-source score cards, coverage bars per field, and format
// consistency per coded field. Plain HTML/CSS bars, no scripts and no network fetches, so it
// renders offline. Colors and fonts come from the Senzing brand tokens generated into
// src/server/assets/brand.json (taken from the bootcamp's brand_tokens). Every data-sourced
// string is HTML-escaped (shared helpers in Html.java).
//
// Usage: java -cp src/build/classes QualityVisual   (after javac; run from the project root)
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class QualityVisual {
    public static void main(String[] args) throws Exception {
        Map<String, Object> qa = Json.obj(Json.parse(Files.readString(Path.of("data/mapping/quality_assessment.json"))));
        Map<String, Object> brand = Json.obj(Json.parse(Files.readString(Path.of("src/server/assets/brand.json"))));
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Object> src : qa.entrySet()) {
            if (src.getKey().equals("cross_source_overlap")) { continue; }
            Map<String, Object> s = Json.obj(src.getValue());
            double score = ((Number) s.get("quality_score")).doubleValue();
            body.append("<section class=\"card\"><h2>").append(Html.esc(src.getKey())).append("</h2>")
                .append("<div class=\"score\"><span class=\"big\">").append(score).append("</span>").append(Html.band(score)).append("</div>")
                .append("<p class=\"muted\">").append(Html.esc(s.get("records"))).append(" records &middot; completeness ")
                .append(Html.esc(s.get("completeness"))).append("% &middot; format consistency ").append(Html.esc(s.get("format_consistency")))
                .append("% &middot; duplicate rate ").append(Html.esc(s.get("duplicate_rate"))).append("%</p>")
                .append("<h3>Field coverage (applicable fields)</h3>");
            for (Map.Entry<String, Object> f : Json.obj(s.get("field_presence")).entrySet()) {
                body.append(Html.bar(f.getKey(), ((Number) f.getValue()).doubleValue(), ""));
            }
            body.append("<h3>Format consistency (coded fields)</h3>");
            for (Map.Entry<String, Object> f : Json.obj(s.get("format_by_field")).entrySet()) {
                Map<String, Object> x = Json.obj(f.getValue());
                long off = Json.lng(x.get("off_pattern"));
                body.append(Html.bar(f.getKey(), ((Number) x.get("consistency")).doubleValue(),
                    off == 0 ? "" : off + " off-pattern"));
            }
            body.append("</section>");
        }
        body.append("<section class=\"card\"><h2>Shared values between sources</h2><table><tr><th>Pair</th><th>Shared emails</th><th>Shared phones</th></tr>");
        for (Map.Entry<String, Object> o : Json.obj(qa.get("cross_source_overlap")).entrySet()) {
            Map<String, Object> x = Json.obj(o.getValue());
            body.append("<tr><td>").append(Html.esc(o.getKey())).append("</td><td>").append(Html.esc(x.get("shared_email_values")))
                .append("</td><td>").append(Html.esc(x.get("shared_phone_digits"))).append("</td></tr>");
        }
        body.append("</table><p class=\"muted\">Counted as distinct values present in both sources (measured, not inferred).</p></section>");

        String css = ":root{" + Json.str(brand.get("root_vars"), "") + "}"
            + "body{margin:0;background:var(--bg);color:var(--ink);font-family:" + Json.str(brand.get("font"), "sans-serif") + "}"
            + "header{background:var(--navy);color:#fff;padding:18px 28px;border-bottom:4px solid var(--blue)}"
            + "header h1{margin:0;font-size:22px}header p{margin:4px 0 0;opacity:.8;font-size:13px}"
            + "main{display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:18px;padding:22px 28px}"
            + ".card{background:#fff;border:1px solid var(--line);border-radius:10px;padding:16px 20px}"
            + "h2{margin:0 0 6px;font-size:17px}h3{margin:16px 0 8px;font-size:13px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}"
            + ".score{display:flex;align-items:center;gap:12px}.big{font-size:34px;font-weight:700;color:var(--blue)}"
            + ".band{font-size:12px;padding:3px 9px;border-radius:12px;border:1px solid var(--line)}"
            + ".band.ok{color:var(--green);border-color:var(--green)}.band.warn{color:var(--gold);border-color:var(--gold)}.band.bad{color:#B3261E;border-color:#B3261E}"
            + ".muted{color:var(--muted);font-size:13px}"
            + ".row{display:grid;grid-template-columns:130px 1fr 150px;align-items:center;gap:10px;margin:5px 0;font-size:13px}"
            + ".lbl{font-family:" + Json.str(brand.get("code_font"), "monospace") + ";font-size:12px}"
            + ".track{background:var(--accent-soft);border-radius:5px;height:14px;overflow:hidden}"
            + ".fill{height:100%}.fill.hi{background:var(--blue)}.fill.mid{background:var(--gold)}.fill.lo{background:#B3261E}"
            + ".note{color:var(--muted);font-size:11px}"
            + "table{border-collapse:collapse;font-size:13px;width:100%}td,th{border-bottom:1px solid var(--line);padding:6px 8px;text-align:left}";
        String html = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>Data Quality Assessment</title><style>" + css + "</style></head><body>"
            + "<header><h1>Data Quality Assessment</h1><p>quality = 0.70 &times; completeness + 0.25 &times; format consistency + 0.05 &times; (100 &minus; duplicate rate)</p></header>"
            + "<main>" + body + "</main></body></html>";
        Path out = Path.of("docs/visualizations/data_quality_assessment.html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, html, StandardCharsets.UTF_8);
        System.out.println("written " + out + " (" + html.length() + " chars)");
    }
}
