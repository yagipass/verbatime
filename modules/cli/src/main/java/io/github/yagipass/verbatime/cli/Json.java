package io.github.yagipass.verbatime.cli;

final class Json {

    private final StringBuilder sb = new StringBuilder("{");

    Json(final String type) {
        put("type", type);
    }

    Json put(final String key, final String value) {
        key(key);
        if (value == null) {
            sb.append("null");
        } else {
            appendQuoted(sb, value);
        }
        return this;
    }

    Json put(final String key, final long value) {
        key(key);
        sb.append(value);
        return this;
    }

    Json put(final String key, final boolean value) {
        key(key);
        sb.append(value);
        return this;
    }

    Json ms(final String key, final long ticks) {
        key(key);
        sb.append(Formats.ms(ticks));
        return this;
    }

    Json raw(final String key, final String json) {
        key(key);
        sb.append(json);
        return this;
    }

    @Override
    public String toString() {
        return sb + "}";
    }

    static String quote(final String s) {
        final StringBuilder b = new StringBuilder(s.length() + 16);
        appendQuoted(b, s);
        return b.toString();
    }

    private void key(final String key) {
        if (sb.length() > 1) {
            sb.append(',');
        }
        appendQuoted(sb, key);
        sb.append(':');
    }

    private static void appendQuoted(final StringBuilder b, final String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }
}
