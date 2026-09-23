package io.github.yagipass.verbatime.agent.test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Check {

    private static int passed;

    private static final List<String> FAILURES = new ArrayList<>();

    private Check() {
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void that(final boolean condition, final String message) {
        if (condition) {
            passed++;
        } else {
            FAILURES.add(message);
            System.err.println("  FAIL " + message);
        }
    }

    public static void eq(final Object expected, final Object actual, final String message) {
        that(Objects.equals(expected, actual), message + ": expected <" + expected + "> but was <" + actual + ">");
    }

    public static void fail(final String message) {
        that(false, message);
    }

    public static <T extends Throwable> T thrown(final Class<T> type, final ThrowingRunnable body, final String message) {
        try {
            body.run();
        } catch (final Throwable t) {
            if (type.isInstance(t)) {
                passed++;
                return type.cast(t);
            }
            fail(message + ": expected " + type.getSimpleName() + " but got " + t);
            return null;
        }
        fail(message + ": expected " + type.getSimpleName() + " but nothing was thrown");
        return null;
    }

    public static int occurrences(final String haystack, final String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    public static String captureStderr(final ThrowingRunnable body) throws Exception {
        final PrintStream realErr = System.err;
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setErr(realErr);
        }
        final String err = captured.toString(StandardCharsets.UTF_8);
        realErr.print(err);
        return err;
    }

    public static int passed() {
        return passed;
    }

    public static List<String> failures() {
        return FAILURES;
    }
}
