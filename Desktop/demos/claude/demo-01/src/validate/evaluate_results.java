// evaluate_results.java -- Data processing, Phase D: statistics, accuracy against ground truth, and
// the match-key audit, computed in one pass over data/results/entity_export.jsonl (written by
// ExportAndAudit). No Senzing call and no SQL: the export is the only reader of the repository.
//
// Statistics follow reporting_guide(topic='evaluation'): per-source record/entity counts and
// compression, cross-source entities per source pair, entity-size buckets, match-principle frequency
// (ERRULE_CODE + MATCH_KEY), and relationship categories deduplicated by (min_id, max_id).
// Accuracy uses data/ground_truth/record_to_customer.csv (which invented customer each record
// describes): pairwise precision / recall / F1 over record pairs, plus over-merged entities (2+
// customers) and split customers (2+ entities).
// Match-key audit: '-' features in MATCH_KEY counted in three buckets with their own denominators --
// single-source per-record, cross-source per-record, and relationship (deduplicated). Keys are split
// after removing parenthesized roles, honoring backslash escapes.
//
// Writes data/results/evaluation.json. Usage: src/scripts/run-java.sh EvaluateResults
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class EvaluateResults {
    /** Signed features of a match key: roles removed, '\' escapes honored, split on unescaped +/-. */
    static List<String> signedFeatures(String key) {
        String k = key.replaceAll("\\([^)]*\\)", "");
        List<String> out = new ArrayList<>();
        StringBuilder cur = null;
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c == '\\' && i + 1 < k.length()) { if (cur != null) { cur.append(k.charAt(++i)); } continue; }
            if (c == '+' || c == '-') { if (cur != null && cur.length() > 1) { out.add(cur.toString()); } cur = new StringBuilder().append(c); continue; }
            if (cur != null) { cur.append(c); }
        }
        if (cur != null && cur.length() > 1) { out.add(cur.toString()); }
        return out;
    }

    static <K> void inc(Map<K, Long> m, K k) { m.merge(k, 1L, Long::sum); }

    static Map<String, Object> top(Map<String, Long> m, int n) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(n)
            .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    static long pairs(long n) { return n * (n - 1) / 2; }

    public static void main(String[] args) throws Exception {
        Map<String, String> truth = new HashMap<>();
        for (String line : Files.readAllLines(Path.of("data/ground_truth/record_to_customer.csv"))) {
            String[] p = line.split(",");
            if (p[0].equals("source")) { continue; }
            truth.put(p[0] + "|" + p[1], p[2]);
        }
        Map<String, Long> recordsPerSource = new TreeMap<>(), entitiesPerSource = new TreeMap<>();
        Map<String, Long> crossPairs = new TreeMap<>(), sizes = new LinkedHashMap<>(), principles = new HashMap<>();
        Map<String, Long> crossPrinciples = new HashMap<>(), relCategories = new TreeMap<>();
        Map<String, Long> supSingle = new TreeMap<>(), supCross = new TreeMap<>(), supRel = new TreeMap<>();
        long perRecSingle = 0, perRecCross = 0, relCompared = 0;
        Set<String> seenRel = new HashSet<>();
        long entities = 0, crossEntities = 0, records = 0, unknownRecords = 0;
        // accuracy
        Map<String, Map<String, Long>> customerEntities = new HashMap<>();   // customer -> entity -> count
        long predictedPairs = 0, truePositivePairs = 0, overMerged = 0;
        List<Object> overMergedSamples = new ArrayList<>(), crossSamples = new ArrayList<>();
        for (String b : List.of("1", "2", "3-5", "6-10", "11+")) { sizes.put(b, 0L); }

        for (String line : Files.readAllLines(Path.of("data/results/entity_export.jsonl"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) { continue; }
            Map<String, Object> row = Json.obj(Json.parse(line));
            Map<String, Object> re = Json.obj(row.get("RESOLVED_ENTITY"));
            long eid = Json.lng(re.get("ENTITY_ID"));
            List<Object> recs = Json.arr(re.get("RECORDS"));
            entities++;
            int n = recs.size();
            records += n;
            inc(sizes, n == 1 ? "1" : n == 2 ? "2" : n <= 5 ? "3-5" : n <= 10 ? "6-10" : "11+");
            Set<String> srcs = new TreeSet<>();
            Map<String, Long> custCount = new HashMap<>();
            for (Object o : recs) {
                Map<String, Object> r = Json.obj(o);
                String ds = Json.str(r.get("DATA_SOURCE"), ""), rid = Json.str(r.get("RECORD_ID"), "");
                srcs.add(ds);
                inc(recordsPerSource, ds);
                String cust = truth.get(ds + "|" + rid);
                if (cust == null) { unknownRecords++; } else {
                    inc(custCount, cust);
                    customerEntities.computeIfAbsent(cust, x -> new HashMap<>()).merge(String.valueOf(eid), 1L, Long::sum);
                }
            }
            for (String s : srcs) { inc(entitiesPerSource, s); }
            boolean cross = srcs.size() > 1;
            if (cross) {
                crossEntities++;
                List<String> l = new ArrayList<>(srcs);
                for (int i = 0; i < l.size(); i++) for (int j = i + 1; j < l.size(); j++) { inc(crossPairs, l.get(i) + " x " + l.get(j)); }
            }
            predictedPairs += pairs(n);
            for (long c : custCount.values()) { truePositivePairs += pairs(c); }
            if (custCount.size() > 1) {
                overMerged++;
                if (overMergedSamples.size() < 10) { overMergedSamples.add(Map.of("entity_id", eid, "records", (long) n, "customers", (long) custCount.size())); }
            }
            if (cross && crossSamples.size() < 25 && eid % 7 == 0) {   // deterministic spread of samples
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("entity_id", eid);
                s.put("records", recs);
                s.put("customers", new ArrayList<>(custCount.keySet()));
                crossSamples.add(s);
            }
            // per-record match keys (the first record of an entity carries an empty key)
            for (Object o : recs) {
                Map<String, Object> r = Json.obj(o);
                String mk = Json.str(r.get("MATCH_KEY"), "");
                if (mk.isEmpty()) { continue; }
                String principle = Json.str(r.get("ERRULE_CODE"), "") + " " + mk;
                inc(principles, principle);
                if (cross) { inc(crossPrinciples, principle); perRecCross++; } else { perRecSingle++; }
                for (String f : signedFeatures(mk)) {
                    if (f.startsWith("-")) { inc(cross ? supCross : supSingle, f.substring(1)); }
                }
            }
            // relationships, deduplicated by (min_id, max_id)
            for (Object o : Json.arr(row.get("RELATED_ENTITIES"))) {
                Map<String, Object> rel = Json.obj(o);
                long other = Json.lng(rel.get("ENTITY_ID"));
                String pair = Math.min(eid, other) + "-" + Math.max(eid, other);
                if (!seenRel.add(pair)) { continue; }
                inc(relCategories, Json.str(rel.get("MATCH_LEVEL_CODE"), ""));
                relCompared++;
                for (String f : signedFeatures(Json.str(rel.get("MATCH_KEY"), ""))) {
                    if (f.startsWith("-")) { inc(supRel, f.substring(1)); }
                }
            }
        }
        // ground-truth pairs and split customers
        long truePairs = 0, split = 0;
        Map<String, Long> custRecords = new HashMap<>();
        for (String v : truth.values()) { inc(custRecords, v); }
        for (long c : custRecords.values()) { truePairs += pairs(c); }
        for (Map<String, Long> m : customerEntities.values()) { if (m.size() > 1) { split++; } }
        double precision = predictedPairs == 0 ? 1 : (double) truePositivePairs / predictedPairs;
        double recall = truePairs == 0 ? 1 : (double) truePositivePairs / truePairs;
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        // cross-source recall: true cross-source customer pairs found together
        long multiSourceCustomers = 0, multiSourceTogether = 0;
        Map<String, Set<String>> custSources = new HashMap<>();
        for (Map.Entry<String, String> e : truth.entrySet()) { custSources.computeIfAbsent(e.getValue(), x -> new TreeSet<>()).add(e.getKey().split("\\|")[0]); }
        for (Map.Entry<String, Set<String>> e : custSources.entrySet()) {
            if (e.getValue().size() > 1) {
                multiSourceCustomers++;
                if (customerEntities.getOrDefault(e.getKey(), Map.of()).size() == 1) { multiSourceTogether++; }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records);
        out.put("records_without_ground_truth", unknownRecords);
        out.put("entities", entities);
        out.put("ground_truth_customers_in_data", (long) custRecords.size());
        Map<String, Object> perSource = new LinkedHashMap<>();
        for (String s : recordsPerSource.keySet()) {
            long r = recordsPerSource.get(s), e = entitiesPerSource.get(s);
            perSource.put(s, Map.of("records", r, "entities", e, "compression_pct", Math.round(1000.0 * (1 - (double) e / r)) / 10.0));
        }
        out.put("per_source", perSource);
        out.put("cross_source_entities", crossEntities);
        out.put("cross_source_pairs", crossPairs);
        out.put("entity_size_buckets", sizes);
        out.put("top_match_principles", top(principles, 15));
        out.put("top_cross_source_principles", top(crossPrinciples, 10));
        out.put("relationships_dedup", relCategories);
        Map<String, Object> acc = new LinkedHashMap<>();
        acc.put("predicted_pairs", predictedPairs);
        acc.put("true_pairs", truePairs);
        acc.put("true_positive_pairs", truePositivePairs);
        acc.put("pairwise_precision_pct", Math.round(precision * 1000) / 10.0);
        acc.put("pairwise_recall_pct", Math.round(recall * 1000) / 10.0);
        acc.put("pairwise_f1_pct", Math.round(f1 * 1000) / 10.0);
        acc.put("over_merged_entities", overMerged);
        acc.put("split_customers", split);
        acc.put("multi_source_customers", multiSourceCustomers);
        acc.put("multi_source_customers_fully_resolved", multiSourceTogether);
        acc.put("multi_source_customer_resolution_pct", multiSourceCustomers == 0 ? 0.0 : Math.round(1000.0 * multiSourceTogether / multiSourceCustomers) / 10.0);
        acc.put("over_merged_samples", overMergedSamples);
        out.put("accuracy_vs_ground_truth", acc);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("single_source_per_record", Map.of("comparisons", perRecSingle, "suppressors", supSingle));
        audit.put("cross_source_per_record", Map.of("comparisons", perRecCross, "suppressors", supCross));
        audit.put("relationship", Map.of("comparisons", relCompared, "suppressors", supRel));
        out.put("match_key_audit", audit);
        out.put("cross_source_samples", crossSamples);
        Files.writeString(Path.of("data/results/evaluation.json"), Json.write(out), StandardCharsets.UTF_8);
        out.remove("cross_source_samples");
        acc.remove("over_merged_samples");
        System.out.println(Json.write(out));
    }
}
