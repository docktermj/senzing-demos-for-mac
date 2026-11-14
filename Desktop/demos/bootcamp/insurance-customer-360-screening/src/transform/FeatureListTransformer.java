package transform;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import server.Json;

/**
 * Shared transformer for this project's sources that already arrive in Senzing record shape:
 * one JSON object per line with DATA_SOURCE, RECORD_ID, a FEATURES list of feature objects, and
 * business fields at the root.
 *
 * Each source program (src/transform/transform_<source>.java) supplies only its own settings, taken
 * from the mapping that mapping_workflow validated for it: the DATA_SOURCE code, which feature
 * attributes are mapped, which root fields are payload, and any per-record type decisions the
 * bootcamper made. The logic, applied the same way to every such source:
 *
 *   - DATA_SOURCE is the source's constant; RECORD_ID is the source RECORD_ID, verbatim.
 *   - RECORD_TYPE comes from the record's own FEATURES[].RECORD_TYPE (a type discriminator):
 *     PERSON keeps person-name attributes, ORGANIZATION keeps NAME_ORG, and a record with no
 *     RECORD_TYPE keeps the name attribute the source states.
 *   - A record-type decision overrides that: the record gets the decided type, and each of its names
 *     is emitted as that type's name attribute (NAME_ORG or NAME_FULL), value unchanged.
 *   - Every other mapped attribute is copied verbatim; each source feature object stays one object.
 *     Blank values are dropped, and so is an object holding only a *_TYPE label (it qualifies nothing).
 *   - Payload fields are copied to the record root unchanged.
 *
 * Values are routed, never rewritten (the verbatim-fidelity gate checks this). JSON handling uses the
 * project's dependency-free reader, src/server/Json.java.
 *
 * Public and named after the class because several source programs reference it (javac resolves a
 * shared class from the sourcepath by filename).
 */
public final class FeatureListTransformer {

    public static final Set<String> PERSON_NAMES = Set.of("NAME_FIRST", "NAME_LAST", "NAME_MIDDLE", "NAME_FULL");
    public static final Set<String> ORG_NAMES = Set.of("NAME_ORG");

    private final String dataSource;
    private final Set<String> featureAttrs;
    private final List<String> payload;
    private final Map<String, String> recordTypeDecisions;
    private final Map<String, String> rootFeatures;

    /**
     * @param dataSource          the DATA_SOURCE code to emit
     * @param featureAttrs        attributes mapped regardless of record type (name attributes excluded)
     * @param payload             root fields copied as payload, in output order
     * @param recordTypeDecisions RECORD_ID -> RECORD_TYPE decisions for rows the bootcamper decided
     */
    public FeatureListTransformer(String dataSource, Set<String> featureAttrs, List<String> payload,
                                  Map<String, String> recordTypeDecisions) {
        this(dataSource, featureAttrs, payload, recordTypeDecisions, Map.of());
    }

    /**
     * @param rootFeatures root field -> single-attribute feature it is emitted as (one object each,
     *                     value verbatim), for business fields the mapping sends to a feature, such as
     *                     an incorporation year emitted as REGISTRATION_DATE
     */
    public FeatureListTransformer(String dataSource, Set<String> featureAttrs, List<String> payload,
                                  Map<String, String> recordTypeDecisions, Map<String, String> rootFeatures) {
        this.dataSource = dataSource;
        this.featureAttrs = featureAttrs;
        this.payload = payload;
        this.recordTypeDecisions = recordTypeDecisions;
        this.rootFeatures = rootFeatures;
    }

    /** Counters reported at the end of a run. */
    public static final class Stats {
        public int read, written, skipped;
        public final Map<String, Integer> dropped = new TreeMap<>();
        public final Map<String, Integer> types = new TreeMap<>();

        void drop(String why) { dropped.merge(why, 1, Integer::sum); }
    }

    /** Transform one source record into one Senzing record (insertion-ordered map). */
    public Map<String, Object> transform(Map<String, Object> src, Stats stats) {
        String recordId = Json.str(src.get("RECORD_ID"));
        List<Object> features = Json.arr(src.get("FEATURES"));

        String recordType = null;
        for (Object o : features) {
            String t = Json.str(Json.obj(o).get("RECORD_TYPE"));
            if (t != null && !t.isBlank()) { recordType = t.strip(); break; }
        }
        String decided = recordTypeDecisions.get(recordId);
        if (decided != null) recordType = decided;

        Set<String> nameAttrs;
        if ("PERSON".equals(recordType)) {
            nameAttrs = PERSON_NAMES;
        } else if ("ORGANIZATION".equals(recordType)) {
            nameAttrs = ORG_NAMES;
        } else {
            nameAttrs = new HashSet<>(PERSON_NAMES); // default: keep the name the source states
            nameAttrs.addAll(ORG_NAMES);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("DATA_SOURCE", dataSource);
        out.put("RECORD_ID", recordId);
        List<Object> outFeatures = new ArrayList<>();
        if (recordType != null) {
            Map<String, Object> rt = new LinkedHashMap<>();
            rt.put("RECORD_TYPE", recordType);
            outFeatures.add(rt);
        }
        stats.types.merge(recordType == null ? "(none)" : recordType, 1, Integer::sum);

        for (Object o : features) {
            Map<String, Object> kept = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : Json.obj(o).entrySet()) {
                String attr = e.getKey();
                Object value = e.getValue();
                if (attr.equals("RECORD_TYPE")) continue;
                if (value == null || Json.str(value).isBlank()) { stats.drop("blank " + attr); continue; }
                boolean isName = PERSON_NAMES.contains(attr) || ORG_NAMES.contains(attr);
                if (isName && decided != null) {
                    kept.put("ORGANIZATION".equals(decided) ? "NAME_ORG" : "NAME_FULL", value);
                } else if (isName && nameAttrs.contains(attr)) {
                    kept.put(attr, value);
                } else if (isName) {
                    stats.drop(attr + " on " + recordType + " record");
                } else if (featureAttrs.contains(attr)) {
                    kept.put(attr, value);
                } else {
                    stats.drop("unmapped " + attr);
                }
            }
            if (kept.isEmpty()) continue;
            if (kept.keySet().stream().allMatch(k -> k.endsWith("_TYPE"))) {
                stats.drop("qualifier-only " + String.join("+", kept.keySet()));
                continue;
            }
            outFeatures.add(kept);
        }
        for (Map.Entry<String, String> rf : rootFeatures.entrySet()) {
            Object value = src.get(rf.getKey());
            if (value == null || Json.str(value).isBlank()) continue;
            Map<String, Object> f = new LinkedHashMap<>();
            f.put(rf.getValue(), value);
            outFeatures.add(f);
        }
        out.put("FEATURES", outFeatures);

        for (String p : payload) {
            if (src.get(p) != null) out.put(p, src.get(p));
        }
        return out;
    }

    /** Transform a whole JSONL file. sample <= 0 means every record. */
    public Stats run(Path input, Path output, int sample) throws IOException {
        Stats stats = new Stats();
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedReader in = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = in.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                if (sample > 0 && stats.read >= sample) break;
                stats.read++;
                Map<String, Object> src;
                try {
                    src = Json.parseObject(line);
                } catch (IllegalArgumentException e) {
                    System.err.println("line " + lineNo + ": skipped, not a JSON object (" + e.getMessage() + ")");
                    stats.skipped++;
                    continue;
                }
                if (src.get("RECORD_ID") == null) {
                    System.err.println("line " + lineNo + ": skipped, no RECORD_ID");
                    stats.skipped++;
                    continue;
                }
                out.write(Json.write(transform(src, stats)));
                out.newLine();
                stats.written++;
                if (stats.written % 10_000 == 0) System.err.println("... " + stats.written + " records written");
            }
        }
        return stats;
    }

    /** Shared command line: <input.jsonl> -o <output.jsonl> [--sample N]. */
    public void main(String programName, String[] args) throws IOException {
        String input = null, output = null;
        int sample = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o": case "--output": output = args[++i]; break;
                case "--sample": sample = Integer.parseInt(args[++i]); break;
                default: input = args[i];
            }
        }
        if (input == null || output == null) {
            System.err.println("usage: " + programName + " <input.jsonl> -o <output.jsonl> [--sample N]");
            System.exit(2);
        }
        Stats s = run(Paths.get(input), Paths.get(output), sample);
        System.out.println("READ=" + s.read);
        System.out.println("WRITTEN=" + s.written);
        System.out.println("SKIPPED=" + s.skipped);
        System.out.println("RECORD_TYPES=" + s.types);
        System.out.println("DROPPED=" + s.dropped);
        System.out.println("OUTPUT=" + output);
    }
}
