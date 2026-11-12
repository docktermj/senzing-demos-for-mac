import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.json.Json;
import javax.json.JsonObject;

final class SourceColorEncoder {
    private static final String WHITE = "#FFFFFF";
    private static final String DARK_INK = "#18160F";
    private static final List<String> FALLBACK_COLORS = List.of(
            "#8b5cf6", "#ec4899", "#0ea5e9", "#a3a34a", "#ef4444", "#14b8a6");
    private static final List<String> STROKES = List.of("#FFFFFF", "#18160F", "#FAF8F3");
    private static final List<Double> STROKE_WIDTHS = List.of(1.5, 3.0);
    private static final List<Double> FILL_SHADES = List.of(0.0, 0.30, -0.30, 0.55, -0.55);
    private static final int STROKE_STATES = 1 + STROKES.size() * STROKE_WIDTHS.size();
    static final int CAPACITY = FALLBACK_COLORS.size() * STROKE_STATES * FILL_SHADES.size();

    private SourceColorEncoder() {}

    static Map<String, Paint> encode(Collection<String> sourceSetKeys) {
        List<String> keys = new TreeSet<>(sourceSetKeys).stream().toList();
        Map<String, String> preferred = new LinkedHashMap<>();
        for (String key : keys) {
            String color = preferredColor(key);
            if (color != null) {
                preferred.put(key, color);
            }
        }
        Set<String> claimed = new HashSet<>(preferred.values());
        List<String> available = FALLBACK_COLORS.stream()
                .filter(color -> !claimed.contains(color))
                .toList();
        if (available.isEmpty()) {
            available = FALLBACK_COLORS;
        }

        Map<String, Paint> assigned = new LinkedHashMap<>();
        int fallbackIndex = 0;
        for (String key : keys) {
            String fill;
            int cycle;
            if (preferred.containsKey(key)) {
                fill = preferred.get(key);
                cycle = 0;
            } else {
                fill = available.get(fallbackIndex % available.size());
                cycle = fallbackIndex / available.size();
                double shade = FILL_SHADES.get((cycle / STROKE_STATES) % FILL_SHADES.size());
                fill = shade(fill, shade);
                fallbackIndex++;
            }
            int slot = cycle % STROKE_STATES;
            if (slot == 0) {
                assigned.put(key, new Paint(fill, STROKES.get(0), null, cycle));
            } else {
                int index = slot - 1;
                String stroke = STROKES.get((index + 1) % STROKES.size());
                double width = STROKE_WIDTHS.get(index / STROKES.size());
                assigned.put(key, new Paint(fill, stroke, width, cycle));
            }
        }
        return assigned;
    }

    static Check checkEncoding(Collection<String> sourceSetKeys, Map<String, Paint> colors) {
        Set<String> actual = new HashSet<>();
        int actualCollisions = 0;
        for (Paint paint : colors.values()) {
            if (!actual.add(paint.renderKey())) {
                actualCollisions++;
            }
        }

        List<String> syntheticKeys = new ArrayList<>();
        for (int i = 0; i < CAPACITY; i++) {
            syntheticKeys.add(String.format("SYNTHETIC_SOURCE_SET_%03d", i));
        }
        Map<String, Paint> syntheticColors = encode(syntheticKeys);
        Set<String> synthetic = new HashSet<>();
        int syntheticCollisions = 0;
        for (Paint paint : syntheticColors.values()) {
            if (!synthetic.add(paint.renderKey())) {
                syntheticCollisions++;
            }
        }

        long fallbackCount = sourceSetKeys.stream().filter(key -> preferredColor(key) == null).count();
        String warning = fallbackCount > CAPACITY
                ? "There are " + fallbackCount + " source-set keys but only " + CAPACITY
                        + " distinct fallback encodings; some colors repeat."
                : null;
        boolean hasCombination = sourceSetKeys.stream().anyMatch(key -> key.contains("|"));
        String status = !hasCombination ? "not_exercised"
                : actualCollisions == 0 && syntheticCollisions == 0 ? "ok" : "failed";
        return new Check(status, colors.size() - actualCollisions, actualCollisions,
                synthetic.size(), syntheticCollisions, warning);
    }

    private static String preferredColor(String key) {
        return switch (key) {
            case "CUSTOMERS" -> "#F57826";
            case "REFERENCE" -> "#3B6EA5";
            case "WATCHLIST" -> "#C8922A";
            default -> null;
        };
    }

    private static String shade(String fill, double factor) {
        if (factor == 0) {
            return fill;
        }
        String target = factor > 0 ? WHITE : DARK_INK;
        double weight = Math.abs(factor);
        int[] source = rgb(fill);
        int[] destination = rgb(target);
        return String.format("#%02X%02X%02X",
                Math.round(source[0] + (destination[0] - source[0]) * weight),
                Math.round(source[1] + (destination[1] - source[1]) * weight),
                Math.round(source[2] + (destination[2] - source[2]) * weight));
    }

    private static int[] rgb(String hex) {
        return new int[] {
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    static final class Paint {
        final String fill;
        final String stroke;
        final Double strokeWidth;
        final int cycle;

        Paint(String fill, String stroke, Double strokeWidth, int cycle) {
            this.fill = fill;
            this.stroke = stroke;
            this.strokeWidth = strokeWidth;
            this.cycle = cycle;
        }

        String renderKey() {
            return fill + "|" + (strokeWidth == null ? "none" : stroke + "|" + strokeWidth);
        }

        JsonObject toJson() {
            return Json.createObjectBuilder()
                    .add("fill", fill)
                    .add("stroke", stroke)
                    .add("stroke_width", strokeWidth == null ? JsonValueNull.VALUE : Json.createValue(strokeWidth))
                    .add("cycle", cycle)
                    .build();
        }
    }

    static final class Check {
        final String status;
        final int actualUnique;
        final int actualCollisions;
        final int syntheticUnique;
        final int syntheticCollisions;
        final String warning;

        Check(String status, int actualUnique, int actualCollisions,
              int syntheticUnique, int syntheticCollisions, String warning) {
            this.status = status;
            this.actualUnique = actualUnique;
            this.actualCollisions = actualCollisions;
            this.syntheticUnique = syntheticUnique;
            this.syntheticCollisions = syntheticCollisions;
            this.warning = warning;
        }
    }

    private static final class JsonValueNull {
        static final javax.json.JsonValue VALUE = javax.json.JsonValue.NULL;
    }
}
