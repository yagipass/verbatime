package io.github.yagipass.verbatime.agent.probe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.DecodedTrace;

public final class ProbeFailureTest {

    private ProbeFailureTest() {
    }

    public static void run() throws Exception {
        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
        Files.createDirectories(tmp);
        final int root = MethodRegistry.reserveIds("test.pf.Root", List.of("root()V"));
        final int child = MethodRegistry.reserveIds("test.pf.Child", List.of("child()V"));
        Probe.addRootId(root);
        try {
            enterFailure(tmp, root, child);
            exitFailure(tmp, root, child);
            sessionEndIsSilent(tmp, root, child);
        } finally {
            Probe.disableAndFlush();
            Probe.detach();
            Probe.replaceRootBits(new long[0]);
            threadLocal().remove();
        }
    }

    private static void enterFailure(final Path tmp, final int root, final int child) throws Exception {
        final TraceFileWriter w = start(tmp.resolve("pf-enter.vbtm"));
        final int activeBefore = Probe.liveSessions();
        final String err = Check.captureStderr(() -> {
            app(() -> {
                Probe.enter(root);
                Probe.enter(child);
                wrappedExit(child);
            }, "a healthy session records without errors");
            forceOverflowOnNextPush();
            app(() -> Probe.enter(child), "an Error inside Probe.enter must not surface from the instrumented method, because the application would fail where it did not without the agent");
            app(() -> wrappedExit(child), "exits after the failure are ignored without throwing while the error is still unwinding");
            app(() -> wrappedExit(root), "the root's exit after the failure is ignored as well");
            app(() -> {
                Probe.enter(root);
                wrappedExit(root);
            }, "the next enter ends the broken session, and the root execution it starts records normally");
        });
        Check.eq(activeBefore, Probe.liveSessions(), "the broken session is detached, so status does not count it as active until the recording stops");
        stop(w);

        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(2, d.sessions.size(), "the broken session is closed with an END chunk, so the thread's next root execution reads as its own session instead of as more children of the broken one");
        final DecodedTrace.DecodedSession broken = d.sessions.get(1);
        Check.that(broken.ended, "the broken session ends where the failure happened instead of staying open for every later call on the thread");
        Check.eq(3, broken.events.size(), "events buffered before the failure are kept");
        final List<DecodedTrace.Node> nodes = preorder(broken);
        Check.that(nodes.size() == 2 && nodes.get(0).unclosed() && !nodes.get(1).unclosed(), "the root cut by the failure reads as unclosed, while the child that returned before it stays closed");
        expectCompleteRoot(d.sessions.get(2), root, "after a failure in enter");
        Check.eq(1, Check.occurrences(err, "ended early"), "the operator is told once that a session was cut short by an error inside the agent");
    }

    private static void exitFailure(final Path tmp, final int root, final int child) throws Exception {
        final TraceFileWriter w = start(tmp.resolve("pf-exit.vbtm"));
        final int activeBefore = Probe.liveSessions();
        Check.captureStderr(() -> {
            app(() -> {
                Probe.enter(root);
                Probe.enter(child);
            }, "a healthy session records without errors");
            forceOverflowOnNextPush();
            app(() -> wrappedExit(child), "an Error inside Probe.exit must not reach the wrapper's catch-all, which would record a second exit for the frame and turn a normal return into a throw");
            app(() -> wrappedExit(root), "exits after the failure are ignored without throwing while the error is still unwinding");
            app(() -> {
                Probe.enter(root);
                wrappedExit(root);
            }, "the next root execution on the same thread records normally");
        });
        Check.eq(activeBefore, Probe.liveSessions(), "the broken session is detached after a failure in exit as well");
        stop(w);

        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(2, d.sessions.size(), "a failure in exit also closes the session before the thread's next one");
        final DecodedTrace.DecodedSession broken = d.sessions.get(1);
        Check.that(broken.ended, "the session broken in exit ends at the thread's next enter");
        Check.that(broken.events.stream().allMatch(e -> e.tag() == DecodedTrace.TAG_ENTER), "no exit is recorded for the failed exit, so a decoder never pops a frame that is still open");
        Check.that(preorder(broken).stream().allMatch(DecodedTrace.Node::unclosed), "both frames cut by the failure read as unclosed");
        expectCompleteRoot(d.sessions.get(2), root, "after a failure in exit");
    }

    private static void sessionEndIsSilent(final Path tmp, final int root, final int child) throws Exception {
        final TraceFileWriter w = start(tmp.resolve("pf-silent.vbtm"));
        final String err = Check.captureStderr(() -> {
            for (int i = 0; i < 3; i++) {
                app(() -> {
                    Probe.enter(root);
                    Probe.enter(child);
                    wrappedExit(child);
                    wrappedExit(root);
                }, "a healthy root execution records without errors");
            }
        });
        Check.eq("", err, "a root execution that ends normally writes nothing to stderr, because a line per execution floods the log of a busy root and, when stderr is a pipe that fills up, blocks the application thread inside the instrumented method's exit");
        stop(w);

        final DecodedTrace d = DecodedTrace.decode(w.path());
        Check.eq(3, d.sessions.size(), "every silent root execution is still recorded as its own session");
        for (final DecodedTrace.DecodedSession s : d.sessions.values()) {
            Check.that(s.ended && s.rootId == root && s.events.size() == 4, "a silent session keeps its END and all of its events");
        }
    }

    private static void wrappedExit(final int id) {
        try {
            Probe.exit(id);
        } catch (final Throwable t) {
            Probe.exitThrow(t, id);
            throw t;
        }
    }

    private static void forceOverflowOnNextPush() throws Exception {
        final Session r = threadLocal().get();
        r.pos = r.buf.length;
    }

    private static void expectCompleteRoot(final DecodedTrace.DecodedSession s, final int root, final String when) {
        Check.that(s != null && s.ended && s.rootId == root && s.events.size() == 2, "a complete root session " + when + " has its own ENTER and EXIT");
    }

    private static List<DecodedTrace.Node> preorder(final DecodedTrace.DecodedSession s) {
        try {
            return DecodedTrace.toPreorder(s);
        } catch (final IllegalStateException e) {
            Check.fail("the recorded events form a valid tree: " + e.getMessage());
            return List.of();
        }
    }

    private static TraceFileWriter start(final Path path) throws Exception {
        Probe.disableAndFlush();
        threadLocal().remove();
        final TraceFileWriter w = TraceFileWriter.open(path);
        Probe.attach(w);
        Probe.enable();
        return w;
    }

    private static void stop(final TraceFileWriter w) {
        Probe.disableAndFlush();
        Probe.detach();
        w.close();
    }

    private static void app(final Runnable step, final String message) {
        try {
            step.run();
        } catch (final Throwable t) {
            Check.fail(message + ": " + t);
        }
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<Session> threadLocal() throws Exception {
        final Field f = Probe.class.getDeclaredField("CURRENT");
        f.setAccessible(true);
        return (ThreadLocal<Session>) f.get(null);
    }
}
