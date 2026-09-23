package io.github.yagipass.verbatime.agent;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.github.yagipass.verbatime.agent.jmx.VerbatimeControl;
import io.github.yagipass.verbatime.agent.probe.MethodRegistry;
import io.github.yagipass.verbatime.agent.probe.Probe;
import io.github.yagipass.verbatime.agent.test.Check;
import io.github.yagipass.verbatime.agent.test.DecodedTrace;

public final class VerbatimeControlTest {

    private VerbatimeControlTest() {
    }

    public static void run() throws Exception {
        final Path tmp = Path.of(System.getProperty("io.github.yagipass.verbatime.agent.test.tmp"));
        Files.createDirectories(tmp);

        final int base = MethodRegistry.reserveIds("test.ctl.C", List.of("m()V", "n()V"));
        MethodRegistry.commitClass(base, "test.ctl.C", List.of("m()V", "n()V"));

        spoolMode(base);
        outMode(tmp, base);
        concurrentDrain(tmp, base);
        rootValidation();
        presetRoots();
        searchMethods();
        uncommittedIds();
    }

    private static void concurrentDrain(final Path tmp, final int base) throws Exception {
        final Path dir = tmp.resolve("ctl-concurrent");
        Files.createDirectories(dir);
        final Path outFile = dir.resolve("trace.vbtm");
        final Config cfg = Config.parse("out=" + outFile);
        final Roots roots = new Roots();
        final VerbatimeControl ctl = new VerbatimeControl(cfg, new Transformer(cfg, roots, null), roots, new Recorder());
        ctl.replaceRoots(new String[] { "test.ctl.C::m" });
        final long id = ctl.startRecording("concurrent");

        final AtomicReference<byte[]> drained = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread puller = new Thread(() -> {
            try {
                drained.set(drainClosed(ctl, id, 0));
            } catch (final Throwable t) {
                failure.set(t);
            }
        }, "ctl-test-puller");
        puller.start();

        for (int s = 0; s < 16; s++) {
            Probe.enter(base);
            for (int i = 0; i < 100_000; i++) {
                Probe.enter(base + 1);
                Probe.exit(base + 1);
            }
            Probe.exit(base);
            Check.eq("recording", statusMap(ctl).get("state"), "status answers while the pull is running");
        }
        ctl.stopRecording();
        puller.join(30_000);
        Check.that(!puller.isAlive(), "the pull thread finishes once the recording is closed");
        Check.that(failure.get() == null, "pull thread failed: " + failure.get());
        final byte[] file = Files.readAllBytes(outFile);
        Check.that(drained.get() != null && Arrays.equals(file, drained.get()), "a drain interleaved with control calls delivers the exact file, footer included");
        ctl.shutdown();
    }

    private static void spoolMode(final int base) throws Exception {
        final Config cfg = Config.parse(null);
        final Roots roots = new Roots();
        final Transformer tr = new Transformer(cfg, roots, null);
        final VerbatimeControl ctl = new VerbatimeControl(cfg, tr, roots, new Recorder());

        Map<String, String> st = statusMap(ctl);
        Check.eq("idle", st.get("state"), "starts idle");
        Check.eq("*", st.get("include"), "include defaults to everything");
        Check.eq("0", st.get("roots"), "no roots until they are applied over JMX");
        Check.that(!st.containsKey("spoolDir"), "spool dir is not created before the first recording");
        Check.that(!st.containsKey("out"), "no out key without out=");

        expectIllegalState(() -> ctl.startRecording("early"), "start with no roots");
        expectIllegalArgument(() -> ctl.replaceRoots(new String[] { "no-separator" }), "unparseable root spec");
        expectIllegalArgument(() -> ctl.replaceRoots(new String[] { "java.util.ArrayList::size" }), "root on a never-instrumented class");
        ctl.replaceRoots(new String[] { "test.ctl.C::m" });
        st = statusMap(ctl);
        Check.eq("1", st.get("roots"), "replaceRoots replaces the whole root set");
        Check.eq("ok test.ctl.C::m", st.get("root.0"), "replaceRoots resolves against loaded classes");

        Probe.enter(base);
        Check.eq(0, Probe.liveSessions(), "root enter before a recording starts no session");

        final int completedBefore = Probe.endedSessions();
        final long id1 = ctl.startRecording("first");
        st = statusMap(ctl);
        Check.eq("recording", st.get("state"), "state after start");
        Check.eq(String.valueOf(id1), st.get("recording.id"), "recording id in status");
        Check.eq("first", st.get("recording.name"), "recording name in status");
        Check.that(!st.containsKey("recording.truncated"), "healthy writer has no truncated flag");
        final Path spool = Path.of(st.get("spoolDir"));
        Check.that(Files.isDirectory(spool), "spool dir created lazily at the first start");
        final Path file1 = spool.resolve(st.get("recording.file"));
        Check.that(Files.exists(file1), "start opened the spool file");
        Check.that(file1.getFileName().toString().startsWith("rec-" + id1 + "-") && file1.getFileName().toString().endsWith(".vbtm"), "spool file naming");

        Probe.enter(base);
        Probe.enter(base + 1);
        Probe.exit(base + 1);
        Probe.exit(base);
        Check.eq(completedBefore + 1, Probe.endedSessions(), "session completed while recording");

        final byte[] live = drainAvailable(ctl, id1);
        Check.eq(Files.size(file1), (long) live.length, "live stream drains exactly the committed bytes");
        final DecodedTrace d1 = DecodedTrace.decode(live);
        Check.that(!d1.cleanEnd, "a live drain has no footer yet");
        Check.eq(st.get("recording.startEpochMs"), String.valueOf(d1.startEpochMs), "status and the file's anchor report the same start time");
        Check.eq("test.ctl.C.m()V", d1.methodNames.get(base), "method table replayed into the new file");
        Check.eq(1, d1.sessions.size(), "sessions are numbered per file");
        Check.eq(base, d1.sessions.get(1).rootId, "streamed session root");
        Check.eq(4, d1.sessions.get(1).events.size(), "streamed session events");

        final String startEpochMs1 = st.get("recording.startEpochMs");
        ctl.stopRecording();
        st = statusMap(ctl);
        Check.eq("idle", st.get("state"), "state after stop");
        Check.eq(String.valueOf(id1), st.get("lastRecording.id"), "lastRecording after stop");
        Check.eq("first", st.get("lastRecording.name"), "lastRecording keeps the name so a client can find its local copy");
        Check.eq(startEpochMs1, st.get("lastRecording.startEpochMs"), "lastRecording keeps the start time so a client can find its local copy");
        Check.that(!st.containsKey("lastRecording.truncated"), "clean stop has no truncated flag");

        Probe.enter(base);
        Check.eq(0, Probe.liveSessions(), "stop disables new sessions");

        final byte[] file1Bytes = Files.readAllBytes(file1);
        final DecodedTrace fin = DecodedTrace.decode(file1Bytes);
        Check.that(fin.cleanEnd, "stopped file carries the end-of-recording footer");
        final byte[] all = drainClosed(ctl, id1, 0);
        Check.that(Arrays.equals(file1Bytes, all), "closed stream == file content, footer included");
        Check.that(!Files.exists(file1), "spool file removed after full delivery");
        expectIllegalArgument(() -> ctl.openStream(id1, 0), "discarded recording id is unknown");

        final long id2 = ctl.startRecording("second");
        Check.eq(id1 + 1, id2, "recording ids increment");
        final Path file2 = spool.resolve(statusMap(ctl).get("recording.file"));
        Probe.enter(base);
        Probe.exit(base);
        ctl.stopRecording();
        final byte[] file2Bytes = Files.readAllBytes(file2);
        final byte[] tail = drainClosed(ctl, id2, file2Bytes.length / 2);
        Check.that(Arrays.equals(Arrays.copyOfRange(file2Bytes, file2Bytes.length / 2, file2Bytes.length), tail), "fromOffset resumes mid-file");
        Check.that(!Files.exists(file2), "tail delivery discards the spool file too");
        final DecodedTrace d2 = DecodedTrace.decode(file2Bytes);
        Check.eq("test.ctl.C.m()V", d2.methodNames.get(base), "method table replayed into the second file");
        Check.eq(1, d2.sessions.size(), "second file restarts session numbering");
        Check.that(d2.sessions.get(1).ended, "second session ended");
        Check.that(!statusMap(ctl).containsKey("recording.id"), "no recording.* keys while idle");

        ctl.startRecording("open-session");
        Probe.enter(base);
        Probe.enter(base + 1);
        ctl.stopRecording();
        Check.eq(0, Probe.liveSessions(), "stop reclaims live sessions");
        final Path fileOpen = spool.resolve(statusMap(ctl).get("lastRecording.file"));
        final DecodedTrace dOpen = DecodedTrace.decode(fileOpen);
        Check.that(dOpen.cleanEnd, "stop with a live session still writes the footer");
        Check.that(!dOpen.sessions.get(1).ended, "the live session is flushed unclosed");
        final int completedAfterStop = Probe.endedSessions();
        ctl.startRecording("after-open");
        Probe.enter(base);
        Probe.exit(base);
        Check.eq(completedAfterStop + 1, Probe.endedSessions(), "the thread sheds its stale session and rejoins the next recording");
        ctl.stopRecording();

        final long id3 = ctl.startRecording("third");
        final Path file3 = spool.resolve(statusMap(ctl).get("recording.file"));
        Probe.enter(base);
        Probe.exit(base);
        ctl.stopRecording();
        final long sid3 = ctl.openStream(id3, 0);
        final long id4 = ctl.startRecording("fourth");
        expectIllegalArgument(() -> ctl.readStream(sid3), "streams of the previous recording are closed by a new start");
        expectIllegalArgument(() -> ctl.openStream(id3, 0), "previous recording id is invalidated by a new start");
        Check.that(!Files.exists(file3), "undelivered spool file removed when a new recording starts");
        ctl.stopRecording();

        expectIllegalState(() -> ctl.stopRecording(), "stop while idle");
        expectIllegalArgument(() -> ctl.openStream(9999, 0), "unknown recording id");
        final long id5 = ctl.startRecording("fifth");
        Check.eq(id4 + 1, id5, "ids keep incrementing");
        expectIllegalState(() -> ctl.startRecording("sixth"), "double start");
        expectIllegalState(() -> ctl.replaceRoots(new String[] { "test.ctl.C::n" }), "replaceRoots while recording");
        Check.that(ctl.searchMethods("test.ctl.C", 10).length >= 1, "searchMethods stays available while recording, unlike replaceRoots");
        expectIllegalArgument(() -> ctl.openStream(id5, Long.MAX_VALUE), "offset beyond committed");
        ctl.stopRecording();

        ctl.registerMBean();
        final Path fileJmx;
        final var server = java.lang.management.ManagementFactory.getPlatformMBeanServer();
        final var name = new javax.management.ObjectName(VerbatimeControl.OBJECT_NAME);
        try {
            final String[] lines = (String[]) server.invoke(name, "status", new Object[0], new String[0]);
            Check.eq("4", parse(lines).get("v"), "protocol v4 over the MBeanServer");
            final String[] found = (String[]) server.invoke(name, "searchMethods", new Object[] { "test.ctl.C", 10 }, new String[] { "java.lang.String", "int" });
            Check.that(List.of(found).contains("test.ctl.C::m"), "searchMethods over the MBeanServer with the primitive int signature the client sends");
            server.invoke(name, "replaceRoots", new Object[] { new String[] { "test.ctl.C::n" } }, new String[] { "[Ljava.lang.String;" });
            Check.eq("ok test.ctl.C::n", statusMap(ctl).get("root.0"), "replaceRoots over the MBeanServer");
            final long rid = (Long) server.invoke(name, "startRecording", new Object[] { "jmx" }, new String[] { "java.lang.String" });
            Check.eq(id5 + 1, rid, "startRecording over the MBeanServer");
            fileJmx = spool.resolve(statusMap(ctl).get("recording.file"));
            server.invoke(name, "stopRecording", new Object[0], new String[0]);
            Check.eq("idle", statusMap(ctl).get("state"), "stopRecording over the MBeanServer");
        } finally {
            server.unregisterMBean(name);
        }

        Check.that(Files.exists(fileJmx), "undelivered spool file stays until shutdown");
        ctl.shutdown();
        Check.that(!Files.exists(fileJmx), "shutdown removes the undelivered spool file");
        Check.that(!Files.exists(spool), "shutdown removes the temp spool directory");
    }

    private static void outMode(final Path tmp, final int base) throws Exception {
        final Path dir = tmp.resolve("ctl-out");
        Files.createDirectories(dir);
        final Path outFile = dir.resolve("trace.vbtm");
        final Config cfg = Config.parse("out=" + outFile);
        final Roots roots = new Roots();
        final VerbatimeControl ctl = new VerbatimeControl(cfg, new Transformer(cfg, roots, null), roots, new Recorder());
        ctl.replaceRoots(new String[] { "test.ctl.C::m" });
        Check.eq(outFile.toString(), statusMap(ctl).get("out"), "out path in status");

        final long id1 = ctl.startRecording("one");
        Probe.enter(base);
        Probe.exit(base);
        ctl.stopRecording();
        Check.that(Files.exists(outFile), "out mode writes to the exact path");
        final byte[] first = Files.readAllBytes(outFile);
        final DecodedTrace d1 = DecodedTrace.decode(first);
        Check.that(d1.cleanEnd, "out file carries the footer");
        Check.eq(1, d1.sessions.size(), "one session in the out file");

        final byte[] delivered = drainClosed(ctl, id1, 0);
        Check.that(Arrays.equals(first, delivered), "out mode streams the file too");
        Check.that(Files.exists(outFile), "out mode keeps the file after full delivery");
        Check.that(Arrays.equals(first, drainClosed(ctl, id1, 0)), "delivered out recording stays streamable until the next start");

        final long id2 = ctl.startRecording("two");
        expectIllegalArgument(() -> ctl.openStream(id1, 0), "previous out recording id is invalidated by a restart");
        ctl.stopRecording();
        final DecodedTrace d2 = DecodedTrace.decode(outFile);
        Check.that(d2.cleanEnd, "second out file carries the footer");
        Check.eq(0, d2.sessions.size(), "a restart truncates the out file in place");
        Check.eq(id1 + 1, id2, "ids increment in out mode too");
        ctl.shutdown();
        Check.that(Files.exists(outFile), "out file survives shutdown");

        final Config bad = Config.parse("out=" + dir.resolve("missing-dir").resolve("t.vbtm"));
        final Roots badRoots = new Roots();
        final VerbatimeControl badCtl = new VerbatimeControl(bad, new Transformer(bad, badRoots, null), badRoots, new Recorder());
        badCtl.replaceRoots(new String[] { "test.ctl.C::m" });
        Check.thrown(UncheckedIOException.class, () -> badCtl.startRecording("x"), "missing out parent directory rejected at start");
    }

    private static void rootValidation() {
        final Config cfg = Config.parse("exclude=test.ctl");
        final Roots roots = new Roots();
        final VerbatimeControl ctl = new VerbatimeControl(cfg, new Transformer(cfg, roots, null), roots, new Recorder());
        expectIllegalArgument(() -> ctl.replaceRoots(new String[] { "test.ctl.C::m" }), "root on an excluded class");
    }

    private static void presetRoots() {
        final Config cfg = Config.parse("roots=test.ctl.C::m");
        final Roots roots = new Roots();
        roots.presetRoots(cfg.roots());
        final VerbatimeControl ctl = new VerbatimeControl(cfg, new Transformer(cfg, roots, null), roots, new Recorder());
        Check.eq("ok test.ctl.C::m", statusMap(ctl).get("root.0"), "roots= from the agent arguments resolve against loaded classes");
        Check.eq(List.of(), roots.unresolved(), "a root that matched is not reported as unresolved");

        final Config missing = Config.parse("roots=test.ctl.Absent::gone");
        final Roots missingRoots = new Roots();
        missingRoots.presetRoots(missing.roots());
        Check.eq(missing.roots(), missingRoots.unresolved(), "a root no class ever matched stays unresolved");
    }

    private static void searchMethods() {
        final Config cfg = Config.parse(null);
        final Roots roots = new Roots();
        final VerbatimeControl ctl = new VerbatimeControl(cfg, new Transformer(cfg, roots, null), roots, new Recorder());

        final int base = MethodRegistry.reserveIds("test.ctl.D", List.of("p(I)V", "p(J)V"));
        MethodRegistry.commitClass(base, "test.ctl.D", List.of("p(I)V", "p(J)V"));

        final String[] dedup = ctl.searchMethods("test.ctl.d", 10);
        Check.eq(1, dedup.length, "overloads collapse into one candidate, matched case-insensitively");
        Check.eq("test.ctl.D::p", dedup[0], "candidates are RootSpec-parseable class::method lines");

        final List<String> c = List.of(ctl.searchMethods("test.ctl.C", 10));
        Check.that(c.contains("test.ctl.C::m") && c.contains("test.ctl.C::n"), "every matching method of a class is offered");
        Check.that(c.indexOf("test.ctl.C::m") < c.indexOf("test.ctl.C::n"), "candidates come back sorted for a stable popup");

        Check.eq(1, ctl.searchMethods("test.ctl", 1).length, "max caps the result count");
        Check.eq(0, ctl.searchMethods(null, 10).length, "null query yields no candidates");
        Check.eq(0, ctl.searchMethods("   ", 10).length, "blank query yields no candidates");
        Check.eq(0, ctl.searchMethods("test.ctl", 0).length, "non-positive max yields no candidates");
    }

    private static void uncommittedIds() {
        final Config cfg = Config.parse(null);
        final Roots roots = new Roots();
        final VerbatimeControl ctl = new VerbatimeControl(cfg, new Transformer(cfg, roots, null), roots, new Recorder());

        final List<String> sigs = List.of("q()V");
        final int phantom = MethodRegistry.reserveIds("test.ctl.Phantom", sigs);

        Check.eq(0, ctl.searchMethods("test.ctl.Phantom", 10).length, "a class registered but never committed, whose transform failed so that it loaded unchanged, is not offered as a root, since nothing in it calls Probe.enter");
        ctl.replaceRoots(new String[] { "test.ctl.Phantom::q" });
        Check.eq("pending test.ctl.Phantom::q", statusMap(ctl).get("root.0"), "status does not say ok for a root whose bytecode can never start a session");

        MethodRegistry.commitClass(phantom, "test.ctl.Phantom", sigs);
        Check.eq(1, ctl.searchMethods("test.ctl.Phantom", 10).length, "the same class is offered once committed, so the check above is about the commit, not the name");
        ctl.replaceRoots(new String[] { "test.ctl.Phantom::q" });
        Check.eq("ok test.ctl.Phantom::q", statusMap(ctl).get("root.0"), "the same root resolves once committed");
    }

    private static void expectIllegalState(final Runnable r, final String what) {
        Check.thrown(IllegalStateException.class, r::run, what + " rejected");
    }

    private static void expectIllegalArgument(final Runnable r, final String what) {
        Check.thrown(IllegalArgumentException.class, r::run, what + " rejected");
    }

    private static byte[] drainAvailable(final VerbatimeControl ctl, final long recordingId) {
        final long sid = ctl.openStream(recordingId, 0);
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while (true) {
                final byte[] b = ctl.readStream(sid);
                if (b == null || b.length == 0) {
                    return bos.toByteArray();
                }
                bos.writeBytes(b);
            }
        } finally {
            ctl.closeStream(sid);
        }
    }

    private static byte[] drainClosed(final VerbatimeControl ctl, final long recordingId, final long fromOffset) {
        final long sid = ctl.openStream(recordingId, fromOffset);
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while (true) {
                final byte[] b = ctl.readStream(sid);
                if (b == null) {
                    return bos.toByteArray();
                }
                bos.writeBytes(b);
            }
        } finally {
            ctl.closeStream(sid);
        }
    }

    private static Map<String, String> statusMap(final VerbatimeControl ctl) {
        return parse(ctl.status());
    }

    private static Map<String, String> parse(final String[] lines) {
        final Map<String, String> m = new HashMap<>();
        for (final String l : lines) {
            final int eq = l.indexOf('=');
            m.put(l.substring(0, eq), l.substring(eq + 1));
        }
        return m;
    }
}
