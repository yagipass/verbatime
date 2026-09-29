package io.github.yagipass.verbatime.agent.test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.errorprone.annotations.Var;

public final class Check {

    private static int passed;

    private static final List<String> FAILURES = new ArrayList<>();

    private Check() {
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void that(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            FAILURES.add(message);
            System.err.println("  FAIL " + message);
        }
    }

    public static void eq(Object expected, Object actual, String message) {
        that(Objects.equals(expected, actual), message + ": expected <" + expected + "> but was <" + actual + ">");
    }

    public static void fail(String message) {
        that(false, message);
    }

    public static <T extends Throwable> T thrown(Class<T> type, ThrowingRunnable body, String message) {
        try {
            body.run();
        } catch (Throwable t) {
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

    public static int occurrences(String haystack, String needle) {
        @Var int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    public static String captureStderr(ThrowingRunnable body) throws Exception {
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setErr(realErr);
        }
        String err = captured.toString(StandardCharsets.UTF_8);
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
