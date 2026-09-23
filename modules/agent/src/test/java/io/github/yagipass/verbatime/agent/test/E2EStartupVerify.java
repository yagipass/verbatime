package io.github.yagipass.verbatime.agent.test;

import java.nio.file.Path;

public final class E2EStartupVerify {

    private E2EStartupVerify() {
    }

    public static void main(final String[] args) throws Exception {
        final DecodedTrace d = DecodedTrace.decode(Path.of(args[0]));
        int failures = 0;
        failures += check(d.cleanEnd, "end-of-recording footer present, so the shutdown hook closed the recording");
        failures += check(d.sessions.size() == 2, "2 sessions, one per root call, got " + d.sessions.size());
        failures += check(d.methodNames.containsValue("io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;"), "CLASS records name the first root");
        failures += check(d.methodNames.containsValue("io.github.yagipass.verbatime.fixtures.Fixture.rootThrows()V"), "CLASS records name the second root");

        final DecodedTrace.DecodedSession first = d.sessions.get(1);
        final DecodedTrace.DecodedSession second = d.sessions.get(2);
        if (first == null || second == null) {
            failed(failures + 1);
            return;
        }
        failures += check(first.ended, "the root() session ended");
        failures += check(DecodedTrace.toPreorder(first).size() > 5, "the root() session recorded the callees of the root");
        failures += check(second.ended, "the rootThrows() session ended");
        failures += check(second.events.stream().anyMatch(e -> e.tag() == DecodedTrace.TAG_EXIT_THROW), "the rootThrows() session recorded a throwing exit");
        failures += check(!d.exceptionNames.isEmpty(), "the throwing exit names its exception");
        if (failures > 0) {
            failed(failures);
            return;
        }
        System.err.println("[e2e-startup] verify OK");
    }

    private static void failed(final int failures) {
        System.err.println("[e2e-startup] " + failures + (failures == 1 ? " check" : " checks") + " FAILED");
        System.exit(1);
    }

    private static int check(final boolean cond, final String msg) {
        if (!cond) {
            System.err.println("[e2e-startup]   FAIL " + msg);
            return 1;
        }
        return 0;
    }
}
