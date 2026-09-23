package io.github.yagipass.verbatime.agent.probe;

public final class Log {

    private Log() {
    }

    public static void info(final String msg) {
        System.err.println("[verbatime] " + msg);
    }

    public static void warn(final String msg) {
        System.err.println("[verbatime] WARN " + msg);
    }

    public static String plural(final long n, final String noun) {
        return n + " " + (n == 1 ? noun : noun + "s");
    }
}
