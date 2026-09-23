package io.github.yagipass.verbatime.jmc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.yagipass.verbatime.jmc.export.ExportNames;

public final class Formats {

    private static final DateTimeFormatter WALL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSS xxx",
            Locale.ROOT);

    private Formats() {
    }

    public static String fmtDur(final long ns) {
        final long v = Math.max(ns, 0);
        if (v < 1_000L) {
            return v + " ns";
        }
        if (v < 1_000_000L) {
            return String.format(Locale.ROOT, "%.2f µs", v / 1e3);
        }
        if (v < 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2f ms", v / 1e6);
        }
        return String.format(Locale.ROOT, "%.3f s", v / 1e9);
    }

    public static String fmtTs(final long ns) {
        return String.format(Locale.ROOT, "%.4f s", ns / 1e9);
    }

    public static String fmtWall(final OffsetDateTime t) {
        return WALL.format(t);
    }

    public static String fmtInt(final long x) {
        return String.format(Locale.US, "%,d", x);
    }

    public static String fmtPct(final double pct) {
        final double v = pct > 0 && Double.isFinite(pct) ? pct : 0;
        final int digits = v >= 10 ? 0 : v >= 1 ? 1 : 2;
        return new BigDecimal(v).setScale(digits, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public static String fmtBytes(final long b) {
        return b < 1 << 20 ? (b + " B") : String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
    }

    public static String fmtElapsed(final long millis) {
        final long s = Math.max(0, millis / 1000);
        final long h = s / 3600;
        final long m = s % 3600 / 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", m, s % 60);
    }

    public static String fmtLag(final long agentBytes, final long transferredBytes, final double agentBytesPerSec) {
        final long behind = Math.max(agentBytes - transferredBytes, 0);
        if (behind == 0) {
            return "up to date";
        }
        final String s = fmtBytes(behind) + " behind";
        if (agentBytesPerSec <= 0) {
            return s;
        }
        return s + ", about " + Math.max(Math.round(behind / agentBytesPerSec), 1) + " s";
    }

    public static String plural(final int n, final String noun) {
        return n + " " + (n == 1 ? noun : noun + "s");
    }

    public static String shortName(final String full) {
        return ExportNames.shortName(full);
    }

    public static String signature(final String full) {
        final int p = full.indexOf('(');
        if (p < 0) {
            return shortName(full);
        }
        final int close = full.indexOf(')', p);
        if (close < 0) {
            return full;
        }
        final List<String> args = parseTypes(full.substring(p + 1, close));
        if (args == null) {
            return full;
        }
        return shortName(full) + "(" + String.join(", ", args) + ")";
    }

    private static List<String> parseTypes(final String s) {
        final List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            final StringBuilder arr = new StringBuilder();
            while (i < s.length() && s.charAt(i) == '[') {
                arr.append("[]");
                i++;
            }
            if (i >= s.length()) {
                return null;
            }
            final char c = s.charAt(i);
            if (c == 'L') {
                final int semi = s.indexOf(';', i);
                if (semi < 0) {
                    break;
                }
                final String fq = s.substring(i + 1, semi).replace('/', '.');
                out.add(fq.substring(fq.lastIndexOf('.') + 1) + arr);
                i = semi + 1;
            } else {
                final String prim = primitive(c);
                if (prim == null) {
                    return null;
                }
                out.add(prim + arr);
                i++;
            }
        }
        return out;
    }

    private static String primitive(final char c) {
        return switch (c) {
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'D' -> "double";
            case 'F' -> "float";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'S' -> "short";
            case 'Z' -> "boolean";
            case 'V' -> "void";
            default -> null;
        };
    }
}
