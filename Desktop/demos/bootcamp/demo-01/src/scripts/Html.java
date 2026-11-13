// Html.java -- shared HTML helpers for the bootcamp's static visual pages (quality_visual.java,
// mapping_quality_visual.java): context-safe escaping, the quality-band badge, and a CSS coverage bar.
final class Html {
    private Html() {}

    static String esc(Object s) {
        return String.valueOf(s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String band(double score) {
        if (score >= 80) { return "<span class=\"band ok\">&#10003; Strong (&ge;80)</span>"; }
        if (score >= 70) { return "<span class=\"band warn\">&#9888; Acceptable &mdash; some gaps (70&ndash;79)</span>"; }
        return "<span class=\"band bad\">&#9888; Recommend fixing before mapping (&lt;70)</span>";
    }

    static String bar(String label, double pct, String note) {
        String cls = pct >= 90 ? "hi" : pct >= 70 ? "mid" : "lo";
        return "<div class=\"row\"><div class=\"lbl\">" + esc(label) + "</div><div class=\"track\"><div class=\"fill "
            + cls + "\" style=\"width:" + pct + "%\"></div></div><div class=\"val\">" + pct + "%"
            + (note.isEmpty() ? "" : " <span class=\"note\">" + esc(note) + "</span>") + "</div></div>";
    }
}
