package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.export.SessionExporter.Result;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;

final class SessionExporterFixtureTest {

    @TempDir
    Path dir;

    private static final long FLOOR_NS = 10_000;

    private static byte[] trace() {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "run()V");
        w.clazz(2, "pkg.A", "a()V", "getChar()I", "getChar(ZZ)I", "tiny()V");
        w.clazz(6, "pkg.other.A", "a()V");
        w.exception(1, "java.lang.IllegalStateException");
        w.exception(2, "pkg.AppException");
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1);
        p.enter(110, 2);
        p.enter(120, 3).exit(130);
        p.enter(130, 4).exit(300);
        p.enter(300, 5).exitThrow(305, 1);
        p.enter(310, 5).enter(311, 5).exit(312).exit(315);
        p.exitThrow(500, 1);
        p.enter(600, 6).exitThrow(900, 2);
        p.exit(150_100);
        w.chunk(7, 100, p.bytes(), true);
        w.gc(120, 10, Vbtm.GC_ACTION_MINOR, "Copy", "Allocation Failure");
        w.gc(140_000, 20_000, Vbtm.GC_ACTION_MAJOR, "MarkSweepCompact", "System.gc()");
        w.end();
        return w.bytes();
    }

    private Result export(final TraceSnapshot d, final Path out) throws IOException {
        return SessionExporter.export(d, d.sessions.get(0), FLOOR_NS, out, TraceIndexer.ProgressListener.NONE,
                SessionExporter.OUTLINE_LIMIT, SessionExporter.WRITE_BUFFER_BYTES);
    }

    @Test
    void theWholeFileIsPinned() throws IOException {
        final TraceSnapshot d = TestTraces.index(trace(), 1 << 20);

        final Path out = dir.resolve("s1.txt");
        final Result r = export(d, out);

        final String expected = String.join("\n", List.of(
                "# verbatime session export v1",
                "",
                "file: " + d.path.getFileName() + "  format: vbtm v1  status: complete",
                "session: #1  thread: main  root: Root.run",
                "duration: 15.0000 ms  calls: 8  max depth: 3  methods used: 6",
                "recorded: 2026-09-02T13:38:05.000+09:00",
                "gc: 2 pauses in this session, 1.0110 ms stop-the-world across every thread, listed under ## gc pauses and not subtracted from self",
                "floor: 10 µs  calls >= floor: 4, one line each  calls < floor: 4, kept as per-parent counts",
                "units: ms, where 0.0001 ms = 1 tick of 100 ns. body line = \"start dur depth Name [self S] [!eN] [~]\"",
                "  \"!eN\" = ended by throw, where eN is the exception class listed under ## exceptions. A bare \"!\" = class unknown",
                "  \"·N calls <10µs, M incl. nested [!K]: a×3, b×2, c\" = calls below the floor made directly",
                "  by the previous line's method. M also counts their descendants, K ended by throw, and the line is the last child",
                "how to read: start from the outline and find every section's line range in the contents below. \"[N lines]\" is the",
                "  subtree size in the body. The ancestors of a body line are the nearest lines above it with a smaller depth, and depth 0 is the root",
                "",
                "## contents",
                "",
                "L26-L31  outline: the 4 longest calls as an index into the body, where L numbers are file lines",
                "L33-L41  hot methods by self: 6 methods, self / total / calls over closed calls only",
                "L43-L51  hot methods by calls: 6 methods",
                "L53-L60  methods: 6 short names -> full signatures, where #2 and #3 mark colliding short names",
                "L62-L65  exceptions: 2 classes thrown in this session, eN -> class name",
                "L67-L70  gc pauses: 2 stop-the-world pauses in this session, start dur kind collector: cause",
                "L72-L78  body: 5 lines, one call per line in time order",
                "",
                "## outline: nodes >= 0.0170 ms, 4 of 4 body nodes",
                "",
                "L74  0.0000 15.0000 100.0%  Root.run self 14.9310  [5 lines]",
                "L75  0.0010  0.0390   0.3%   A.a self 0.0200 hidden 3 calls 0.0020 !e1  [3 lines]",
                "L76  0.0030  0.0170   0.1%    A.getChar  [1 lines]",
                "L78  0.0500  0.0300   0.2%   A.a#2 !e2  [1 lines]",
                "",
                "## hot methods by self, top 40",
                "",
                " self_ms total_ms calls  method",
                " 14.9310  15.0000     1  Root.run",
                "  0.0300   0.0300     1  A.a#2",
                "  0.0200   0.0390     1  A.a",
                "  0.0170   0.0170     1  A.getChar",
                "  0.0010   0.0011     3  A.tiny",
                "  0.0010   0.0010     1  A.getChar#2",
                "",
                "## hot methods by calls, top 25",
                "",
                " self_ms total_ms calls  method",
                "  0.0010   0.0011     3  A.tiny",
                " 14.9310  15.0000     1  Root.run",
                "  0.0300   0.0300     1  A.a#2",
                "  0.0200   0.0390     1  A.a",
                "  0.0170   0.0170     1  A.getChar",
                "  0.0010   0.0010     1  A.getChar#2",
                "",
                "## methods: 6 used in this session",
                "",
                "Root.run = pkg.Root.run()V",
                "A.a = pkg.A.a()V",
                "A.getChar = pkg.A.getChar(ZZ)I",
                "A.tiny = pkg.A.tiny()V",
                "A.getChar#2 = pkg.A.getChar()I",
                "A.a#2 = pkg.other.A.a()V",
                "",
                "## exceptions: 2 classes thrown in this session",
                "",
                "e1 = java.lang.IllegalStateException",
                "e2 = pkg.AppException",
                "",
                "## gc pauses: 2 in this session, clipped to it",
                "",
                "0.0020 0.0010 minor Copy: Allocation Failure",
                "13.9900 1.0100 major MarkSweepCompact: System.gc()",
                "",
                "## body: 5 lines",
                "",

                "0.0000 15.0000 0 Root.run self 14.9310" + "    ",
                "0.0010  0.0390 1 A.a self  0.0200" + " !e1",
                "0.0030 0.0170 2 A.getChar",
                "0.0010 0.0020 2 ·3 calls <10µs, 4 incl. nested [!1]: A.tiny×2, A.getChar#2",
                "0.0500 0.0300 1 A.a#2 !e2")) + "\n";
        assertEquals(expected, Files.readString(out));

        assertEquals(out, r.file());
        assertEquals(Files.size(out), r.bytes());
        assertEquals(78, r.lines());
        assertEquals(5, r.bodyLines());
        assertEquals(8, r.calls());
        assertEquals(4, r.listedCalls());
        assertEquals(4, r.belowFloorCalls());
        assertEquals(3, r.maxDepth());
        assertEquals(17_000, r.outlineThresholdNs());
        assertFalse(Files.exists(dir.resolve("s1.txt.part")), "the staging file is gone");

        final ParsedExport t = ParsedExport.read(out);
        t.checkInvariants("fixture");
        for (final ParsedExport.OutlineRow o : t.outline) {
            final ParsedExport.BodyLine b = t.bodyAt(o.anchor());
            assertEquals(o.start(), b.start(), "anchor resolves to the same call: " + o.raw());
            assertEquals(o.dur(), b.dur(), o.raw());
            assertEquals(o.depth(), b.depth(), o.raw());
            assertEquals(o.name(), b.name(), o.raw());
        }
    }

    @Test
    void sameInputSameBytesSoExportsCanBeDiffed() throws IOException {
        final TraceSnapshot d = TestTraces.index(trace(), 1 << 20);
        export(d, dir.resolve("a.txt"));
        export(d, dir.resolve("b.txt"));
        assertEquals(Files.readString(dir.resolve("a.txt")), Files.readString(dir.resolve("b.txt")));
    }

    @Test
    void floorZeroListsEveryCallAndNeedsNoAccountingLines() throws IOException {
        final TraceSnapshot d = TestTraces.index(trace(), 1 << 20);
        final Path out = dir.resolve("all.txt");
        final Result r = SessionExporter.export(d, d.sessions.get(0), 0, out, TraceIndexer.ProgressListener.NONE);
        assertEquals(8, r.listedCalls());
        assertEquals(0, r.belowFloorCalls());
        final ParsedExport t = ParsedExport.read(out);
        assertEquals(8, t.body.size());
        assertTrue(t.body.stream().noneMatch(ParsedExport.BodyLine::accounting));
        assertEquals("0.0211 0.0001 3 A.tiny", t.body.get(6).raw(), "the 100 ns nested call is one tick");
        assertEquals("0.0200 0.0005 2 A.tiny !e1", t.body.get(4).raw(),
                "a throw is flagged on the call itself, with the class it threw");
        assertEquals("java.lang.IllegalStateException", t.exceptionClass(t.body.get(4).excNo()));
        assertEquals("0.0020 0.0010 2 A.getChar", t.body.get(2).raw(),
                "names are numbered in first-use order, so getChar()I is bare here and getChar(ZZ)I gets #2");
        assertTrue(t.line(ParsedExport.LINE_FLOOR).startsWith(
                "floor: none  calls >= floor: 8, one line each  calls < floor: 0"), t.line(ParsedExport.LINE_FLOOR));
        t.checkInvariants("floor 0");
    }

    @Test
    void theFlagSlotIsSizedForTheRecordingsExceptionCountAndAnUnknownClassKeepsABareMark() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "run()V", "step()V", "leaf()V");
        for (int id = 1; id <= 10; id++) {
            w.exception(id, "pkg.Ex" + id);
        }
        final TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1);
        p.enter(110, 2);
        p.enter(120, 3).exitThrow(200_120, 0);
        p.exitThrow(300_110, 10);
        p.exit(400_100);
        w.chunk(7, 100, p.bytes(), true);
        w.end();
        final TraceSnapshot d = TestTraces.index(w);
        final Path out = dir.resolve("wide.txt");
        export(d, out);

        final ParsedExport t = ParsedExport.read(out);
        assertEquals(List.of("0.0000 40.0000 0 Root.run self 10.0000     ", "0.0010 30.0000 1 Root.step self 10.0000 !e1 ",
                "0.0020 20.0000 2 Root.leaf !"), t.body.stream().map(ParsedExport.BodyLine::raw).toList(),
                "a deferred line's flag slot has room for the widest number the recording can need, 2 digits for 10 classes, so"
                        + " patching \"!e1\" in place never overruns the line, and a class the agent could not name is a bare \"!\"");
        assertEquals(List.of("e1 = pkg.Ex10"), t.exceptions.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue()).toList(),
                "numbers follow first use in this export, not the file's ids, and unknown classes get no row");
        assertTrue(t.body.get(2).thrown());
        assertEquals(null, t.exceptionClass(t.body.get(2).excNo()));
        t.checkInvariants("wide");
    }

    @Test
    void cancellationLeavesNoFileBehind() throws IOException {
        final TraceSnapshot d = TestTraces.index(trace(), 1 << 20);
        final Path out = dir.resolve("cancelled.txt");
        assertThrows(TraceIndexer.CancelledException.class,
                () -> SessionExporter.export(d, d.sessions.get(0), FLOOR_NS, out, (done, total) -> true));
        assertFalse(Files.exists(out));
        assertFalse(Files.exists(dir.resolve("cancelled.txt.part")));
    }

    @Test
    void aSessionWithoutFramesStillGetsAWellFormedFile() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(9, "idle");
        w.chunk(9, 150, new byte[0], true);
        w.end();
        final TraceSnapshot d = TestTraces.index(w);
        final Session s = d.sessions.get(0);
        assertEquals(0, s.callCount);
        assertTrue(d.threads.isEmpty(), "the index keeps no model for a thread without frames");
        final Path out = dir.resolve("empty.txt");
        final Result r = SessionExporter.export(d, s, FLOOR_NS, out, TraceIndexer.ProgressListener.NONE);
        assertEquals(0, r.calls());
        assertEquals(0, r.bodyLines());
        final ParsedExport t = ParsedExport.read(out);
        assertEquals("session: #1  thread: idle  root: <no enter>", t.line(ParsedExport.LINE_SESSION));
        assertEquals("duration: 0.0000 ms  calls: 0  max depth: 0  methods used: 0", t.line(ParsedExport.LINE_DURATION));
        assertEquals("## body: 0 lines", t.line(t.bodyStart), "an empty body is a heading with no blank line to trail");
        assertTrue(t.body.isEmpty());
        assertTrue(t.outline.isEmpty());
    }
}
