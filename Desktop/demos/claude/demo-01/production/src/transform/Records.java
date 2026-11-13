// Records.java -- shared, dependency-free source reader for the transformation programs.
// Reads a CSV file (header row, RFC 4180 quoting) or a JSONL file into ordered field->value maps,
// with values kept exactly as they appear in the source (no trimming or reformatting), so mappers
// route values rather than rewrite them. JSONL lines are parsed with the shared Json reader
// (src/server/Json.java). Reused by every mapper here and by Data processing's loader.
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class Records {
    private Records() {}

    /** Streams each record of a .csv or .jsonl file to the consumer; stops after limit records when limit > 0. */
    static long forEach(Path path, long limit, Consumer<Map<String, String>> sink) throws IOException {
        long n = 0;
        try (BufferedReader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            boolean jsonl = path.toString().endsWith(".jsonl");
            List<String> header = jsonl ? null : csv(in.readLine());
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                if (line.isBlank()) { continue; }
                sink.accept(jsonl ? jsonRow(line) : csvRow(header, line));
                if (++n == limit) { break; }
            }
        }
        return n;
    }

    static boolean present(String v) { return v != null && !v.isBlank(); }

    private static Map<String, String> jsonRow(String line) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : Json.obj(Json.parse(line)).entrySet()) {
            m.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        return m;
    }

    private static Map<String, String> csvRow(List<String> header, String line) {
        List<String> v = csv(line);
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) { m.put(header.get(i), i < v.size() ? v.get(i) : null); }
        return m;
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
}
