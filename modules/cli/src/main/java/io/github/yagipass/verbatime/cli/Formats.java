package io.github.yagipass.verbatime.cli;

import java.util.Locale;

import io.github.yagipass.verbatime.format.Vbtm;

final class Formats {

    private Formats() {
    }

    static String ms(final long ticks) {
        final long t = Math.max(ticks, 0);
        return t / Vbtm.TICKS_PER_MS + "." + String.format(Locale.ROOT, "%04d", t % Vbtm.TICKS_PER_MS);
    }

    static String grouped(final long v) {
        return String.format(Locale.US, "%,d", v);
    }

    static String percent(final long part, final long whole) {
        if (whole <= 0) {
            return "-";
        }
        final long tenths = Math.round(part * 1000.0 / whole);
        return tenths / 10 + "." + tenths % 10 + "%";
    }

    static String duration(final long ticks) {
        if (ticks <= 0) {
            return "0";
        }
        if (ticks % (Vbtm.TICKS_PER_MS * 1000) == 0) {
            return ticks / (Vbtm.TICKS_PER_MS * 1000) + "s";
        }
        if (ticks >= Vbtm.TICKS_PER_MS) {
            return trimZeros(ms(ticks)) + "ms";
        }
        return ticks % 10 == 0 ? ticks / 10 + "us" : ticks / 10 + "." + ticks % 10 + "us";
    }

    static String plural(final long n, final String noun) {
        return plural(n, noun, noun + "s");
    }

    static String plural(final long n, final String one, final String many) {
        return grouped(n) + " " + (n == 1 ? one : many);
    }

    static long roundUpTicks(final long ticks) {
        if (ticks <= 1) {
            return Math.max(ticks, 0);
        }
        long p = 1;
        while (p * 10 <= ticks) {
            p *= 10;
        }
        for (final long m : new long[] { 1, 2, 5, 10 }) {
            if (p * m >= ticks) {
                return p * m;
            }
        }
        return p * 10;
    }

    private static String trimZeros(final String s) {
        int end = s.length();
        while (s.charAt(end - 1) == '0') {
            end--;
        }
        if (s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }
}
