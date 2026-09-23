package io.github.yagipass.verbatime.agent.test;

import java.nio.file.Path;

public final class E2EGateVerify {

    private E2EGateVerify() {
    }

    public static void main(final String[] args) throws Exception {
        final DecodedTrace d = DecodedTrace.decode(Path.of(args[0]));
        int failures = 0;
        failures += check(d.cleanEnd, "end-of-recording footer present, so the trace is complete");
        failures += check(d.sessions.size() == 1, "1 session for root, got " + d.sessions.size());
        failures += check(d.methodNames.containsValue("io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;"), "CLASS records name the root");
        final DecodedTrace.DecodedSession s1 = d.sessions.get(1);
        if (s1 != null) {
            failures += check(s1.ended, "session ended");
            failures += check(DecodedTrace.toPreorder(s1).size() > 5, "session recorded the callees of the startup code");
        } else {
            failures++;
        }
        if (failures > 0) {
            System.err.println("[e2e-gate] " + failures + (failures == 1 ? " check" : " checks") + " FAILED");
            System.exit(1);
        }
        System.err.println("[e2e-gate] verify OK");
    }

    private static int check(final boolean cond, final String msg) {
        if (!cond) {
            System.err.println("[e2e-gate]   FAIL " + msg);
            return 1;
        }
        return 0;
    }
}
