import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Shared transformation program for the five banking sources (Module 5, Phase 2).
 *
 * <p>Every source already arrives in Senzing JSON: root DATA_SOURCE and RECORD_ID, a FEATURES
 * list of feature objects, plus business columns at the root. The mapping decided by Senzing's
 * mapping_workflow (docs/mapping/{SOURCE}_mapper.md) is therefore a ROUTING, not a rewrite:
 * <ul>
 *   <li>DATA_SOURCE is the source's constant code; RECORD_ID is the source RECORD_ID verbatim.</li>
 *   <li>Each FEATURES object is copied as one object, keeping only attributes the mapping
 *       dispositioned as features (all of them, for these sources). Values are never reformatted,
 *       recoded or re-cased, so every emitted value is verbatim from the source.</li>
 *   <li>Root business columns dispositioned as payload are kept at the root as scalars.</li>
 *   <li>A payload column whose name is a reserved Senzing attribute is renamed (value unchanged).</li>
 *   <li>A feature object holding only a usage-type label (e.g. PHONE_TYPE, no number) is dropped.</li>
 *   <li>Anything else is dropped and counted, so a new column cannot slip through unnoticed.</li>
 * </ul>
 * JSON is handled by MiniJson (src/server/MiniJson.java), the bootcamp's dependency-free reader.
 *
 * <p>Usage (from a per-source entry point): {@code <input.jsonl> -o <output.jsonl> [--sample N]}
 */
public final class SenzingJsonPassThrough {
    private SenzingJsonPassThrough() { }

    /** Runs the transformation; returns the number of records written. */
    /** Usage-type labels that are not values of their feature on their own. */
    static final Set<String> QUALIFIERS = Set.of("ADDR_TYPE", "PHONE_TYPE", "NAME_TYPE");

    public static int run(String[] args, String dataSource, List<String> payloadFields,
                          Set<String> featureAttributes) throws IOException {
        return run(args, dataSource, payloadFields, featureAttributes, Map.of(), Map.of());
    }

    /**
     * As above; {@code payloadRename} renames payload keys whose source name collides with a reserved
     * Senzing attribute (Senzing would otherwise read a root ACCOUNT_NUMBER as a feature, not payload).
     */
    public static int run(String[] args, String dataSource, List<String> payloadFields,
                          Set<String> featureAttributes, Map<String, String> payloadRename) throws IOException {
        return run(args, dataSource, payloadFields, featureAttributes, payloadRename, Map.of());
    }

    /**
     * As above, plus {@code recordCorrections}: per-RECORD_ID corrections the bootcamper decided
     * (documented in the source's mapper notes). Each receives the record's copied FEATURES list and
     * returns the corrected list. Corrections may re-type a record and re-route its names, but must
     * only move source values, never rewrite them, so the verbatim check still holds.
     */
    public static int run(String[] args, String dataSource, List<String> payloadFields,
                          Set<String> featureAttributes, Map<String, String> payloadRename,
                          Map<String, java.util.function.UnaryOperator<List<Object>>> recordCorrections)
            throws IOException {
        return run(args, dataSource, payloadFields, featureAttributes, payloadRename, recordCorrections, Map.of());
    }

    /**
     * As above, plus {@code rootFeatures}: root columns the mapping dispositioned as a feature rather
     * than payload (e.g. BUSINESS_REGISTRY's INCORPORATED -> REGISTRATION_DATE). Each present value is
     * emitted, unchanged, as its own one-attribute FEATURES object.
     */
    public static int run(String[] args, String dataSource, List<String> payloadFields,
                          Set<String> featureAttributes, Map<String, String> payloadRename,
                          Map<String, java.util.function.UnaryOperator<List<Object>>> recordCorrections,
                          Map<String, String> rootFeatures)
            throws IOException {
        String in = null, out = null;
        int sample = Integer.MAX_VALUE;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o": case "--output": out = args[++i]; break;
                case "--sample": sample = Integer.parseInt(args[++i]); break;
                default: in = args[i];
            }
        }
        if (in == null || out == null) {
            System.err.println("usage: <input.jsonl> -o <output.jsonl> [--sample N]");
            System.exit(64);
        }
        Path outPath = Paths.get(out);
        if (outPath.toAbsolutePath().getParent() != null) Files.createDirectories(outPath.toAbsolutePath().getParent());

        int read = 0, written = 0, rejected = 0, corrected = 0;
        Map<String, Integer> dropped = new TreeMap<>();
        try (BufferedReader r = Files.newBufferedReader(Paths.get(in), StandardCharsets.UTF_8);
             BufferedWriter w = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null && written < sample) {
                if (line.trim().isEmpty()) continue;
                read++;
                Map<String, Object> src;
                try {
                    src = MiniJson.obj(MiniJson.parse(line));
                } catch (RuntimeException e) {
                    rejected++;
                    System.err.println("line " + read + ": not valid JSON (" + e.getMessage() + ")");
                    continue;
                }
                Object recordId = src.get("RECORD_ID");
                Object ds = src.get("DATA_SOURCE");
                if (recordId == null || String.valueOf(recordId).trim().isEmpty() || !dataSource.equals(ds)) {
                    rejected++;
                    System.err.println("line " + read + ": missing RECORD_ID or DATA_SOURCE is not " + dataSource);
                    continue;
                }
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("DATA_SOURCE", dataSource);
                rec.put("RECORD_ID", String.valueOf(recordId));
                List<Object> features = new ArrayList<>();
                for (Object fo : MiniJson.arr(src.get("FEATURES"))) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : MiniJson.obj(fo).entrySet()) {
                        if (featureAttributes.contains(e.getKey()) && e.getValue() != null) f.put(e.getKey(), e.getValue());
                        else dropped.merge("FEATURES." + e.getKey(), 1, Integer::sum);
                    }
                    // A usage-type label with no value of its own feature (e.g. a lone PHONE_TYPE)
                    // qualifies nothing: drop the object rather than emit a qualifier-only feature.
                    boolean qualifierOnly = !f.isEmpty() && f.keySet().stream().allMatch(QUALIFIERS::contains);
                    if (qualifierOnly) dropped.merge("qualifier-only " + f.keySet(), 1, Integer::sum);
                    else if (!f.isEmpty()) features.add(f);
                }
                java.util.function.UnaryOperator<List<Object>> fix = recordCorrections.get(String.valueOf(recordId));
                if (fix != null) {
                    features = fix.apply(features);
                    corrected++;
                }
                rec.put("FEATURES", features);
                for (Map.Entry<String, Object> e : src.entrySet()) {
                    String k = e.getKey();
                    if (k.equals("DATA_SOURCE") || k.equals("RECORD_ID") || k.equals("FEATURES")) continue;
                    String outKey = payloadRename.getOrDefault(k, k);
                    if (rootFeatures.containsKey(k) && e.getValue() != null && !(e.getValue() instanceof Map)
                            && !(e.getValue() instanceof List)) {
                        Map<String, Object> f = new LinkedHashMap<>();
                        f.put(rootFeatures.get(k), e.getValue());
                        features.add(f);
                    } else if (payloadFields.contains(k) && e.getValue() != null && !(e.getValue() instanceof Map)
                            && !(e.getValue() instanceof List)) rec.put(outKey, e.getValue());
                    else dropped.merge(k, 1, Integer::sum);
                }
                w.write(MiniJson.write(rec));
                w.newLine();
                written++;
                if (written % 1000 == 0) System.err.println(dataSource + ": " + written + " records written");
            }
        }
        System.out.println(dataSource + ": read " + read + ", written " + written + ", rejected " + rejected
            + ", corrected " + corrected
            + (dropped.isEmpty() ? ", nothing dropped" : ", dropped " + dropped));
        return written;
    }

    /** The feature attributes the mapping routes for these sources (Entity Specification names). */
    public static Set<String> attributes(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }
}
