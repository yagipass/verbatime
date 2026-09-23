package io.github.yagipass.verbatime.agent.test;

import io.github.yagipass.verbatime.agent.probe.Log;

final class LogTest {

    private LogTest() {
    }

    static void run() {
        Check.eq("1 session", Log.plural(1, "session"), "a single item names the noun in the singular");
        Check.eq("3 sessions", Log.plural(3, "session"), "several items name the noun in the plural");
        Check.eq("0 unclosed sessions", Log.plural(0, "unclosed session"), "zero reads as a plural, and a qualifier stays in front");
    }
}
