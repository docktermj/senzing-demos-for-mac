// Mapper.java -- shared pieces of the transformation programs: building one feature object from
// (source field, attribute) pairs, and the common command line (input, -o output, --sample N) that
// streams the source through a mapping function into Senzing JSON Lines with progress on stderr.
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class Mapper {
    private Mapper() {}

    /** Adds one feature object built from (sourceField, attribute) pairs, if any value is present. */
    static void addFeature(List<Object> features, Map<String, String> row, String... pairs) {
        Map<String, Object> f = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String v = row.get(pairs[i]);
            if (Records.present(v)) { f.put(pairs[i + 1], v); }
        }
        if (!f.isEmpty()) { features.add(f); }
    }

    /** Runs a mapper from the command line; map returns null for a row it cannot key. */
    static void run(String name, String[] args, Function<Map<String, String>, Map<String, Object>> map) throws IOException {
        String input = null, output = null;
        long sample = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o" -> output = args[++i];
                case "--sample" -> sample = Long.parseLong(args[++i]);
                default -> input = args[i];
            }
        }
        if (input == null || output == null) {
            System.err.println("usage: " + name + " <input> -o <output.jsonl> [--sample N]");
            System.exit(2);
        }
        Path out = Path.of(output);
        if (out.getParent() != null) { Files.createDirectories(out.getParent()); }
        long[] written = {0}, skipped = {0};
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            long read = Records.forEach(Path.of(input), sample, row -> {
                Map<String, Object> rec = map.apply(row);
                if (rec == null) { skipped[0]++; return; }
                try {
                    w.write(Json.write(rec));
                    w.newLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (++written[0] % 1000 == 0) { System.err.println("  " + written[0] + " records..."); }
            });
            System.out.println("read=" + read + " written=" + written[0] + " skipped_no_id=" + skipped[0] + " -> " + out);
        }
    }
}
