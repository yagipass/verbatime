package io.github.yagipass.verbatime.agent.test;

import java.nio.file.Path;

import io.github.yagipass.verbatime.format.Vbtm;

public final class E2EVerify {

    private E2EVerify() {
    }

    public static void main(final String[] args) throws Exception {
        final DecodedTrace d = DecodedTrace.decode(Path.of(args[0]));
        int failures = 0;
        failures += check(d.cleanEnd, "end-of-recording footer present, so the trace is complete");
        failures += check(d.sessions.size() == 3, "3 sessions for root, rootThrows, and rootAllocates, got " + d.sessions.size());
        failures += check(d.methodNames.containsValue("io.github.yagipass.verbatime.fixtures.Fixture.root()Ljava/lang/String;"), "CLASS records name the root");
        final DecodedTrace.DecodedSession s1 = d.sessions.get(1);
        if (s1 != null) {
            failures += check(s1.ended, "session 1 ended");
            failures += check(DecodedTrace.toPreorder(s1).size() > 5, "session 1 recorded the callees");
        } else {
            failures++;
        }
        final DecodedTrace.DecodedSession s2 = d.sessions.get(2);
        if (s2 != null) {
            failures += check(s2.ended, "session 2 ended");
            final DecodedTrace.Node root2 = DecodedTrace.toPreorder(s2).get(0);
            failures += check(root2.thrown(), "throw flag on rootThrows");
            failures += check("java.lang.IllegalStateException".equals(d.exceptionName(root2.exceptionId())), "rootThrows records what was thrown, got " + d.exceptionName(root2.exceptionId()));
        } else {
            failures++;
        }
        failures += check(d.danglingExceptionRefs == 0, "every EXCEPTION record precedes the chunk that references it, " + d.danglingExceptionRefs + " dangling");
        final DecodedTrace.DecodedSession s3 = d.sessions.get(3);
        if (s3 != null && !s3.events.isEmpty()) {
            failures += check(s3.ended, "session 3 ended");
            final long from = s3.events.get(0).ticks();
            final long to = s3.events.get(s3.events.size() - 1).ticks();
            final long inside = d.gcPauses.stream().filter(p -> p.startTicks() <= to && p.endTicks() >= from).count();
            failures += check(inside >= 1, "a GC pause overlaps the rootAllocates session, with " + d.gcPauses.size() + " on file and " + inside + " inside");
            failures += check(d.gcPauses.stream().anyMatch(p -> p.startTicks() <= to && p.endTicks() >= from && p.action() == Vbtm.GC_ACTION_MAJOR && p.durTicks() > 0),
                    "the explicit collection is a major pause with a measurable duration: " + d.gcPauses);
            failures += check(d.gcPauses.stream().allMatch(p -> !p.collector().isEmpty()), "every GC record names its collector");
        } else {
            failures++;
        }
        if (failures > 0) {
            System.err.println("[e2e] " + failures + (failures == 1 ? " check" : " checks") + " FAILED");
            System.exit(1);
        }
        System.err.println("[e2e] verify OK");
    }

    private static int check(final boolean cond, final String msg) {
        if (!cond) {
            System.err.println("[e2e]   FAIL " + msg);
            return 1;
        }
        return 0;
    }
}
