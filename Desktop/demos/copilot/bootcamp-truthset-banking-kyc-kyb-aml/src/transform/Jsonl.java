import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Jsonl {
    private Jsonl() {
    }

    static Map<String, Object> parseObject(String text) {
        Object value = new Parser(text).parse();
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("JSON value must be an object.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) value;
        return object;
    }

    static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        appendValue(output, value);
        return output.toString();
    }

    private static void appendValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String) {
            appendString(output, (String) value);
        } else if (value instanceof Boolean || value instanceof Number) {
            output.append(value);
        } else if (value instanceof Map<?, ?>) {
            appendObject(output, (Map<?, ?>) value);
        } else if (value instanceof List<?>) {
            appendArray(output, (List<?>) value);
        } else {
            throw new IllegalArgumentException(
                "Unsupported JSON value type: " + value.getClass().getName());
        }
    }

    private static void appendObject(StringBuilder output, Map<?, ?> object) {
        output.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalArgumentException("JSON object keys must be strings.");
            }
            if (!first) {
                output.append(',');
            }
            appendString(output, (String) entry.getKey());
            output.append(':');
            appendValue(output, entry.getValue());
            first = false;
        }
        output.append('}');
    }

    private static void appendArray(StringBuilder output, List<?> values) {
        output.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                output.append(',');
            }
            appendValue(output, values.get(i));
        }
        output.append(']');
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"':
                    output.append("\\\"");
                    break;
                case '\\':
                    output.append("\\\\");
                    break;
                case '\b':
                    output.append("\\b");
                    break;
                case '\f':
                    output.append("\\f");
                    break;
                case '\n':
                    output.append("\\n");
                    break;
                case '\r':
                    output.append("\\r");
                    break;
                case '\t':
                    output.append("\\t");
                    break;
                default:
                    if (current < 0x20) {
                        output.append("\\u00");
                        output.append(HEX[(current >> 4) & 0x0f]);
                        output.append(HEX[current & 0x0f]);
                    } else {
                        output.append(current);
                    }
            }
        }
        output.append('"');
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final class Parser {
        private final String text;
        private int position;

        private Parser(String text) {
            this.text = text;
        }

        private Object parse() {
            Object value = readValue();
            skipWhitespace();
            if (position != text.length()) {
                fail("Unexpected content after JSON value.");
            }
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (position >= text.length()) {
                fail("Expected a JSON value.");
            }
            char current = text.charAt(position);
            if (current == '{') {
                return readObject();
            }
            if (current == '[') {
                return readArray();
            }
            if (current == '"') {
                return readString();
            }
            if (current == 't') {
                return readLiteral("true", Boolean.TRUE);
            }
            if (current == 'f') {
                return readLiteral("false", Boolean.FALSE);
            }
            if (current == 'n') {
                return readLiteral("null", null);
            }
            if (current == '-' || isDigit(current)) {
                return readNumber();
            }
            fail("Unexpected character while reading JSON value.");
            return null;
        }

        private Map<String, Object> readObject() {
            position++;
            skipWhitespace();
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (position >= text.length() || text.charAt(position) != '"') {
                    fail("Expected a string object key.");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                if (object.containsKey(key)) {
                    fail("Duplicate object key: " + key);
                }
                object.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            position++;
            skipWhitespace();
            List<Object> values = new ArrayList<Object>();
            if (consume(']')) {
                return values;
            }
            while (true) {
                values.add(readValue());
                skipWhitespace();
                if (consume(']')) {
                    return values;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < text.length()) {
                char current = text.charAt(position++);
                if (current == '"') {
                    return value.toString();
                }
                if (current < 0x20) {
                    fail("Unescaped control character in string.");
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                if (position >= text.length()) {
                    fail("Unterminated escape sequence.");
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        value.append(escaped);
                        break;
                    case 'b':
                        value.append('\b');
                        break;
                    case 'f':
                        value.append('\f');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 't':
                        value.append('\t');
                        break;
                    case 'u':
                        value.append(readUnicodeEscape());
                        break;
                    default:
                        fail("Invalid string escape.");
                }
            }
            fail("Unterminated string.");
            return null;
        }

        private char readUnicodeEscape() {
            if (position + 4 > text.length()) {
                fail("Incomplete Unicode escape.");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(position++), 16);
                if (digit < 0) {
                    fail("Invalid Unicode escape.");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Object readNumber() {
            int start = position;
            consume('-');
            if (consume('0')) {
                if (position < text.length() && isDigit(text.charAt(position))) {
                    fail("Leading zero in number.");
                }
            } else {
                readDigits();
            }
            if (consume('.')) {
                readDigits();
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) {
                    consume('-');
                }
                readDigits();
            }
            try {
                return new BigDecimal(text.substring(start, position));
            } catch (NumberFormatException e) {
                fail("Invalid number.");
                return null;
            }
        }

        private void readDigits() {
            int start = position;
            while (position < text.length() && isDigit(text.charAt(position))) {
                position++;
            }
            if (start == position) {
                fail("Expected a digit.");
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, position)) {
                fail("Invalid literal.");
            }
            position += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (position < text.length()) {
                char current = text.charAt(position);
                if (current != ' ' && current != '\n' && current != '\r' && current != '\t') {
                    return;
                }
                position++;
            }
        }

        private boolean consume(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                fail("Expected '" + expected + "'.");
            }
        }

        private void fail(String message) {
            throw new IllegalArgumentException(
                message + " At character " + (position + 1) + ".");
        }

        private static boolean isDigit(char value) {
            return value >= '0' && value <= '9';
        }
    }
}
