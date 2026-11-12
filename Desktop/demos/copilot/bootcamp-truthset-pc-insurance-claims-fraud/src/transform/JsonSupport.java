import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonSupport {
    private JsonSupport() {
    }

    static Object parse(String json) {
        return new Parser(json).parse();
    }

    static Map<String, Object> object(Object value, String context) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(context + " must be a JSON object.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(context + " contains a non-string key.");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    static List<Object> array(Object value, String context) {
        if (!(value instanceof List<?> source)) {
            throw new IllegalArgumentException(context + " must be a JSON array.");
        }
        return new ArrayList<>(source);
    }

    static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        write(value, output);
        return output.toString();
    }

    private static void write(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            writeString(text, output);
        } else if (value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof BigDecimal number) {
            output.append(number.toPlainString());
        } else if (value instanceof Number) {
            output.append(value);
        } else if (value instanceof Map<?, ?> object) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be strings.");
                }
                if (!first) {
                    output.append(',');
                }
                writeString(key, output);
                output.append(':');
                write(entry.getValue(), output);
                first = false;
            }
            output.append('}');
        } else if (value instanceof List<?> array) {
            output.append('[');
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    output.append(',');
                }
                write(array.get(i), output);
            }
            output.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
        }
    }

    private static void writeString(String text, StringBuilder output) {
        output.append('"');
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (index != input.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= input.length()) {
                throw error("Expected a value");
            }
            return switch (input.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            while (true) {
                skipWhitespace();
                if (index >= input.length() || input.charAt(index) != '"') {
                    throw error("Expected an object key");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                if (result.containsKey(key)) {
                    throw error("Duplicate object key");
                }
                result.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') {
                    return result.toString();
                }
                if (character == '\\') {
                    if (index >= input.length()) {
                        throw error("Incomplete escape sequence");
                    }
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(parseUnicodeEscape());
                        default -> throw error("Invalid escape sequence");
                    }
                } else {
                    if (character < 0x20) {
                        throw error("Unescaped control character");
                    }
                    result.append(character);
                }
            }
            throw error("Unterminated string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > input.length()) {
                throw error("Incomplete Unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(input.charAt(index++), 16);
                if (digit < 0) {
                    throw error("Invalid Unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Object parseNumber() {
            int start = index;
            consume('-');
            if (!consume('0')) {
                if (index >= input.length() || !isDigit(input.charAt(index))) {
                    throw error("Invalid number");
                }
                while (index < input.length() && isDigit(input.charAt(index))) {
                    index++;
                }
            } else if (index < input.length() && isDigit(input.charAt(index))) {
                throw error("Leading zero in number");
            }
            if (consume('.')) {
                int fractionStart = index;
                while (index < input.length() && isDigit(input.charAt(index))) {
                    index++;
                }
                if (fractionStart == index) {
                    throw error("Missing fraction digits");
                }
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) {
                    consume('-');
                }
                int exponentStart = index;
                while (index < input.length() && isDigit(input.charAt(index))) {
                    index++;
                }
                if (exponentStart == index) {
                    throw error("Missing exponent digits");
                }
            }
            try {
                return new BigDecimal(input.substring(start, index));
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, index)) {
                throw error("Invalid literal");
            }
            index += literal.length();
            return value;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + index + ".");
        }
    }
}
