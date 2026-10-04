package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.errorprone.annotations.Var;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Varint;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

final class TraceIndexerTest {

    private static byte[] simpleTrace() {
        TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        w.clazz(1, "pkg.Root", "root()V");
        w.clazz(2, "pkg.A", "a()V", "a2()V");
        w.exception(1, "java.sql.SQLException");
        TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1);
        p.enter(110, 2);
        p.enter(120, 9);
        p.exit(150);
        p.exit(160);
        p.enter(160, 3);
        p.exitThrow(170, 1);
        p.exit(200);
        w.chunk(7, 100, p.bytes(), true);
        w.gc(162, 6, Vbtm.GC_ACTION_MINOR, "G1 Young Generation", "G1 Evacuation Pause");
        w.gc(90, 15, Vbtm.GC_ACTION_MAJOR, "G1 Old Generation", "System.gc()");
        w.end();
        return w.bytes();
    }

    @Test
    void gcPausesExplainSelfTime() throws IOException {
        TraceSnapshot d = TestTraces.index(simpleTrace(), 1 << 20);
        assertEquals(2, d.gc.count);
        assertEquals(21 * 100, d.gc.totalNs);
        assertEquals(162 * 100, d.gc.startNs[0], "file order is kept: the young pause was written first");
        assertEquals(6 * 100, d.gc.durNs[0]);
        assertEquals(Vbtm.GC_ACTION_MINOR, d.gc.action[0]);
        assertEquals("G1 Young Generation", d.gc.collector[0]);
        assertEquals("G1 Evacuation Pause", d.gc.cause[0]);
        assertEquals(90 * 100, d.gc.startNs[1]);
        assertEquals("System.gc()", d.gc.cause[1]);
        assertEquals(new TraceSnapshot.GcPauses.Overlap(600, 1), d.gc.overlap(160 * 100, 10 * 100),
                "a pause inside a frame is charged to that frame in full");
        assertEquals(new TraceSnapshot.GcPauses.Overlap(500 + 600, 2), d.gc.overlap(100 * 100, 100 * 100),
                "a pause straddling a frame edge counts only the intersection, so self is never over-explained");
        assertEquals(new TraceSnapshot.GcPauses.Overlap(0, 0), d.gc.overlap(110 * 100, 40 * 100),
                "a frame that no pause touched reports nothing rather than a zero-count line");
        assertEquals(4, d.totalCalls, "GC records do not disturb the frame stream");
        assertEquals(100 * 100, d.minNs, "a pause before the first frame does not widen the trace");
    }

    @Test
    void gcLabelBeyondTheLimitIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.thread(7, "main");
        long offset = w.bytes().length;
        w.gc(100, 10, Vbtm.GC_ACTION_MINOR, "X".repeat(Vbtm.MAX_GC_LABEL_BYTES + 1), "cause");
        w.end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(offset, d.corruption.offset(), "a label longer than any JVM emits marks the record, not the file end, as corrupt");
        assertEquals(0, d.gc.count);
    }

    @Test
    void negativeGcStartIsCorruptLikeInTheFormatReader() throws IOException {
        TraceBuilder w = TestTraces.writer();
        long offset = w.bytes().length;
        w.rawBytes(Vbtm.RECORD_GC);
        rawVarint(w, -1L);
        w.rawBytes(10, Vbtm.GC_ACTION_MINOR, 4, 'C', 'o', 'p', 'y', 5, 'c', 'a', 'u', 's', 'e');
        w.end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(offset, d.corruption.offset(),
                "a 10-byte varint decoding to a negative tick count must not reach the viewer as a pause before the epoch");
        assertTrue(d.corruption.reason().contains("GC pause ticks out of range"), d.corruption.reason());
        assertEquals(0, d.gc.count);
    }

    @Test
    void gcActionOutsideTheFormatIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        long offset = w.bytes().length;
        w.gc(100, 10, 3, "Copy", "Allocation Failure");
        w.end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(offset, d.corruption.offset(), "an action the format does not define is rejected like an unknown record type");
    }

    @Test
    void simpleSession() throws IOException {
        TraceSnapshot d = TestTraces.index(simpleTrace(), 1 << 20);
        assertFalse(d.truncated);
        assertNull(d.corruption);
        assertEquals(4, d.totalCalls);
        assertEquals(1, d.sessions.size());
        Session s = d.sessions.get(0);
        assertEquals(1, s.seq);
        assertEquals(7, s.tid);
        assertEquals(1, s.rootMethodId);
        assertTrue(s.ended);
        assertEquals(100 * 100, s.startNs);
        assertEquals(200 * 100, s.endNs);
        assertEquals("main", d.threadName(7));
        assertEquals("pkg.Root.root()V", d.methodName(1));
        assertEquals("pkg.A.a()V", d.methodName(2));
        assertEquals("pkg.A.a2()V", d.methodName(3));
        assertEquals("<unknown#9>", d.methodName(9));
        assertEquals(3, d.totalMethods);
        assertEquals("java.sql.SQLException", d.exceptionName(1), "the RECORD_EXCEPTION table names what a2 threw");
        assertEquals("<unknown>", d.exceptionName(0), "id 0 is reserved for a throw whose class the agent could not record");
        assertEquals("<unknown#5>", d.exceptionName(5));
        assertEquals(1, d.totalExceptions);
        ThreadIndex main = d.threads.get(0);
        int[] exc = Arrays.copyOf(main.overview.exceptionId, main.overview.count);
        assertEquals(1, Arrays.stream(exc).filter(x -> x >= 0).count(), "exactly one frame ended by throw");
        for (int i = 0; i < main.overview.count; i++) {
            if (main.overview.methodId[i] == 3) {
                assertEquals(1, main.overview.exceptionId[i], "the a2 frame carries the SQLException id, not just a throw bit");
            } else {
                assertEquals(-1, main.overview.exceptionId[i], "frames that returned normally have no exception id");
            }
        }

        assertEquals(1, d.threads.size());
        ThreadIndex m = d.threads.get(0);
        assertEquals(4, m.overview.count);
        assertEquals(0, d.overviewThresholdNs);
        assertEquals(2, m.maxDepth);
        assertArrayEquals(new long[] { 10000, 11000, 12000, 16000 }, Arrays.copyOf(m.overview.startNs, 4));
        assertArrayEquals(new long[] { 10000, 5000, 3000, 1000 }, Arrays.copyOf(m.overview.durNs, 4));
        assertArrayEquals(new int[] { 0, 1, 2, 1 }, Arrays.copyOf(m.overview.depth, 4));
        assertArrayEquals(new int[] { 1, 2, 9, 3 }, Arrays.copyOf(m.overview.methodId, 4));
        assertArrayEquals(new long[] { 4000, 2000, 3000, 1000 }, Arrays.copyOf(m.overview.selfNs, 4));
        assertEquals(1, d.callsByMethod[1]);
        assertEquals(1, d.callsByMethod[2]);
        assertEquals(1, d.callsByMethod[3]);
    }

    @Test
    void unclosedSessionDrainsAtEof() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.thread(5, "t");
        TraceBuilder.Payload p = new TraceBuilder.Payload(1000);
        p.enter(1000, 10);
        p.enter(1010, 11);
        p.exit(1020);
        p.enter(1030, 12);
        w.chunk(5, 1000, p.bytes(), false);
        w.end();
        TraceSnapshot d = TestTraces.index(w);
        assertFalse(d.truncated);
        assertEquals(3, d.totalCalls);
        Session s = d.sessions.get(0);
        assertFalse(s.ended);
        assertEquals(1030 * 100, s.endNs);
        ThreadIndex m = d.threads.get(0);
        @Var int unclosedCount = 0;
        for (int i = 0; i < m.overview.count; i++) {
            if (m.overview.unclosed[i]) {
                unclosedCount++;
                assertEquals(1030 * 100, m.overview.startNs[i] + m.overview.durNs[i]);
            }
        }
        assertEquals(2, unclosedCount);
    }

    @Test
    void threadRenameAndTwoSessionsPerTid() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.thread(5, "old-name");
        TraceBuilder.Payload p1 = new TraceBuilder.Payload(100);
        p1.enter(100, 1).exit(200);
        w.chunk(5, 100, p1.bytes(), true);
        w.thread(5, "new-name");
        TraceBuilder.Payload p2 = new TraceBuilder.Payload(300);
        p2.enter(300, 2).exit(400);
        w.chunk(5, 300, p2.bytes(), true);
        w.end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals("new-name", d.threadName(5));
        assertEquals(2, d.sessions.size());
        assertEquals(1, d.sessions.get(0).rootMethodId);
        assertEquals(2, d.sessions.get(1).rootMethodId);
        assertTrue(d.sessions.get(0).ended);
        assertTrue(d.sessions.get(1).ended);
        assertEquals(1, d.threads.size());
        assertEquals(2, d.threads.get(0).chunks.count);
    }

    @Test
    void emptyChunkEndClosesSession() throws IOException {
        TraceBuilder w = TestTraces.writer();
        TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(150);
        w.chunk(9, 100, p.bytes(), false);
        w.chunk(9, 150, new byte[0], true);
        w.end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(1, d.sessions.size());
        assertTrue(d.sessions.get(0).ended);
        assertEquals(150 * 100, d.sessions.get(0).endNs);
        assertEquals(1, d.totalCalls);
    }

    @Test
    void missingFooterIsTruncated() throws IOException {
        TraceBuilder w = TestTraces.writer();
        TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200);
        w.chunk(3, 100, p.bytes(), true);
        TraceSnapshot d = TestTraces.index(w);
        assertTrue(d.truncated);
        assertNull(d.corruption);
        assertEquals(1, d.totalCalls);
    }

    @Test
    void dataAfterFooterIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200);
        w.chunk(3, 100, p.bytes(), true);
        long offset = w.bytes().length;
        w.end().thread(5, "late");
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(offset, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("footer"));
        assertFalse(d.truncated);
        assertEquals(1, d.totalCalls);
    }

    @Test
    void badMagicRejected() {
        assertThrows(TraceIndexer.NotTraceFormatException.class,
                () -> TestTraces.index(new byte[] { 'j', 'm', 't', '1', 0 }, 1000));
        assertThrows(TraceIndexer.NotTraceFormatException.class, () -> TestTraces.index(new byte[] { 'j', 'm' }, 1000));
        TraceIndexer.NotTraceFormatException old = assertThrows(TraceIndexer.NotTraceFormatException.class,
                () -> TestTraces.index(new byte[] { 'v', 'a', 'f', '1', Vbtm.RECORD_END }, 1000),
                "the pre-rename magic is refused, naming the expected one");
        assertTrue(old.getMessage().contains("\"vbtm\""), old.getMessage());
    }

    @Test
    void unsupportedVersionRejected() throws IOException {
        byte[] full = TestTraces.writer().thread(3, "t").end().bytes();
        full[Vbtm.VERSION_OFFSET] = (byte) (Vbtm.VERSION + 1);
        TraceIndexer.NotTraceFormatException e = assertThrows(TraceIndexer.NotTraceFormatException.class,
                () -> TestTraces.index(full, 1000), "a newer version is refused up front, not reported as corrupt");
        assertTrue(e.getMessage().contains("version " + (Vbtm.VERSION + 1)), e.getMessage());
        assertTrue(e.getMessage().contains("version " + Vbtm.VERSION), e.getMessage());
        TraceSnapshot d = TestTraces.index(Arrays.copyOf(full, Vbtm.VERSION_OFFSET), 1000);
        assertTrue(d.truncated, "a file cut before the version byte is truncated, not refused");
        assertNull(d.corruption);
    }

    @Test
    void anchorIsReadIntoTraceData() throws IOException {
        long epochMs = 1_800_000_000_123L;
        TraceBuilder w = new TraceBuilder(epochMs, -5 * 3600);
        w.thread(3, "t").end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(epochMs, d.startEpochMs, "tick 0 maps to the recording's start time");
        assertEquals(-5 * 3600, d.utcOffsetSeconds, "the server's offset travels with the file, signed");
        assertNull(d.corruption);
        assertFalse(d.truncated);
        assertEquals("2027-01-15T03:00:00.123-05:00", d.wallClock(0).toString());
        assertEquals("2027-01-15T03:00:00.123000100-05:00", d.wallClock(100).toString(),
                "wall-clock conversion keeps the 100 ns tick");
    }

    @Test
    void firstRecordMustBeTheAnchor() throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(Vbtm.magic());
        o.write(Vbtm.VERSION);
        o.write(Vbtm.RECORD_THREAD);
        o.write(3);
        o.write(1);
        o.write('t');
        o.write(Vbtm.RECORD_END);
        TraceSnapshot d = TestTraces.index(o.toByteArray(), 1 << 20);
        assertEquals(Vbtm.ANCHOR_OFFSET, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("anchor"), d.corruption.reason());
        assertEquals(0, d.threadNames.size(), "nothing past the missing anchor is read");
    }

    @Test
    void aSecondAnchorIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.thread(3, "t");
        long offset = w.bytes().length;
        w.rawBytes(Vbtm.RECORD_ANCHOR, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(offset, d.corruption.offset());
        assertEquals(TestTraces.DEFAULT_START_EPOCH_MS, d.startEpochMs, "the first anchor stays authoritative");
    }

    @Test
    void anchorOffsetOutOfRangeIsCorrupt() throws IOException {
        TraceBuilder w = new TraceBuilder(TestTraces.DEFAULT_START_EPOCH_MS, 19 * 3600);
        w.thread(3, "t").end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.ANCHOR_OFFSET, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("offset"), d.corruption.reason());
    }

    @Test
    void anchorOffsetAtIntegerMinIsCorrupt() throws IOException {
        TraceBuilder w = new TraceBuilder(TestTraces.DEFAULT_START_EPOCH_MS, Integer.MIN_VALUE);
        w.thread(3, "t").end();
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.ANCHOR_OFFSET, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("offset"), d.corruption.reason());
    }

    @Test
    void aFileCutInsideTheAnchorIsTruncatedNotCorrupt() throws IOException {
        byte[] full = TestTraces.writer().thread(3, "t").end().bytes();
        for (int cut = Vbtm.MAGIC_BYTES; cut < Vbtm.HEADER_BYTES; cut++) {
            TraceSnapshot d = TestTraces.index(Arrays.copyOf(full, cut), 1 << 20);
            assertTrue(d.truncated, "cut " + cut);
            assertNull(d.corruption, "cut " + cut);
            assertEquals(0, d.startEpochMs, "cut " + cut + ": no anchor yet, so no wall clock");
        }
    }

    @Test
    void unknownRecordTypeIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200);
        w.chunk(3, 100, p.bytes(), true);
        long offset = w.bytes().length;
        w.rawBytes(0x7F, 1, 2, 3);
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(offset, d.corruption.offset());
        assertNotNull(d.corruption);
        assertEquals(1, d.totalCalls);
        assertFalse(d.truncated);
    }

    @Test
    void exitWithEmptyStackIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        TraceBuilder.Payload p = new TraceBuilder.Payload(100);
        p.enter(100, 1).exit(200).exit(210);
        w.chunk(3, 100, p.bytes(), true);
        TraceSnapshot d = TestTraces.index(w);
        assertTrue(d.corruption.offset() > 0);
        assertTrue(d.corruption.reason().contains("exit with no open frame"));
        assertEquals(1, d.totalCalls);
        assertFalse(d.sessions.get(0).ended);
    }

    @Test
    void payloadBoundaryMismatchIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.chunk(3, 100, new byte[] { (byte) 0x80 }, false);
        TraceBuilder.Payload p = new TraceBuilder.Payload(300);
        p.enter(300, 1).exit(400);
        w.chunk(3, 300, p.bytes(), true);
        TraceSnapshot d = TestTraces.index(w);
        assertTrue(d.corruption.offset() > 0);
        assertTrue(d.corruption.reason().contains("mid-event"));
        assertEquals(0, d.totalCalls);
    }

    @Test
    void classIdOverflowIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.clazz((1 << 22) - 1, "pkg.X", "a()V", "b()V");
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.HEADER_BYTES, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("2^22"));
    }

    @Test
    void classIdArithmeticOverflowIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.clazz(Long.MAX_VALUE, "pkg.X", "a()V");
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.HEADER_BYTES, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("2^22"), d.corruption.reason());
    }

    @Test
    void negativeClassIdIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer().rawBytes(Vbtm.RECORD_CLASS);
        byte[] id = new byte[Varint.MAX_BYTES];
        int n = Varint.put(id, 0, -1L);
        for (int i = 0; i < n; i++) {
            w.rawBytes(id[i] & 0xFF);
        }
        w.rawBytes(1, 5, 'p', 'k', 'g', '.', 'X', 4, 'a', '(', ')', 'V');
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.HEADER_BYTES, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("2^22"), d.corruption.reason());
    }

    @Test
    void negativeNameLengthIsCorruptNotTruncated() throws IOException {
        TraceBuilder w = TestTraces.writer().rawBytes(Vbtm.RECORD_THREAD, 7);
        rawVarint(w, -1L);
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.HEADER_BYTES, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("negative string length"), d.corruption.reason());
        assertFalse(d.truncated, "no writer produces a negative length, so it is corruption, not a recording cut short");
    }

    @Test
    void chunkBaseTicksBeyondTheLimitIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer().rawBytes(Vbtm.RECORD_CHUNK, 1);
        rawVarint(w, Vbtm.MAX_TICKS + 1);
        w.rawBytes(0);
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.HEADER_BYTES, d.corruption.offset(),
                "base ticks past MAX_TICKS would overflow the session start into negative nanoseconds");
        assertTrue(d.corruption.reason().contains("ticks"), d.corruption.reason());
    }

    @Test
    void negativeChunkBaseTicksIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer().rawBytes(Vbtm.RECORD_CHUNK_END, 1);
        rawVarint(w, -1L);
        w.rawBytes(0);
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(Vbtm.HEADER_BYTES, d.corruption.offset());
        assertTrue(d.corruption.reason().contains("ticks"), d.corruption.reason());
    }

    @Test
    void tickDeltaBeyondTheLimitIsCorruptInsteadOfASessionEndThatWrapsNegative() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.clazz(1, "pkg.Root", "root()V");
        byte[] first = new TraceBuilder.Payload(100).enter(100, 1).bytes();
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.writeBytes(first);
        byte[] exit = new byte[Varint.MAX_BYTES];
        p.write(exit, 0, Varint.put(exit, 0, ((Vbtm.MAX_TICKS + 1 - 100) << 2) | 1));
        byte[] payload = p.toByteArray();
        w.chunk(1, 100, payload, true);
        int secondEvent = w.bytes().length - payload.length + first.length;
        TraceSnapshot d = TestTraces.index(w);
        assertEquals(secondEvent, d.corruption.offset(), "the exit whose delta leaves the format range marks the file");
        assertTrue(d.corruption.reason().contains("tick delta"), d.corruption.reason());
    }

    @Test
    void chunkBaseTicksAtTheLimitStillLoad() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.chunk(1, Vbtm.MAX_TICKS, new byte[0], true);
        w.end();
        TraceSnapshot d = TestTraces.index(w);
        assertNull(d.corruption);
        assertEquals(Vbtm.MAX_TICKS * Vbtm.NANOS_PER_TICK, d.sessions.get(0).startNs);
        assertTrue(d.sessions.get(0).startNs > 0, "MAX_TICKS is the last value whose nanoseconds fit in a long");
    }

    private static void rawVarint(TraceBuilder w, long v) {
        byte[] b = new byte[Varint.MAX_BYTES];
        int n = Varint.put(b, 0, v);
        for (int i = 0; i < n; i++) {
            w.rawBytes(b[i] & 0xFF);
        }
    }

    @Test
    void varintTooLongIsCorrupt() throws IOException {
        TraceBuilder w = TestTraces.writer();
        w.rawBytes(Vbtm.RECORD_THREAD);
        for (int i = 0; i < 10; i++) {
            w.rawBytes(0x80);
        }
        w.rawBytes(0x01);
        TraceSnapshot d = TestTraces.index(w);
        assertTrue(d.corruption.offset() > 0);
        assertTrue(d.corruption.reason().contains("varint too long"));
    }

    @Test
    void everyPrefixMatchesReference() throws IOException {
        byte[] full = RandomTraces.random(42);
        for (int cut = 4; cut <= full.length; cut += (cut < 2000 ? 1 : 7)) {
            byte[] prefix = Arrays.copyOf(full, cut);
            ReferenceDecoder.Result ref = ReferenceDecoder.decode(prefix);
            TraceSnapshot d = TestTraces.index(prefix, 1 << 30);
            assertEquals(-1, ref.corruptOffset, "prefix " + cut);
            assertNull(d.corruption, "prefix " + cut);
            assertEquals(ref.truncated, d.truncated, "prefix " + cut);
            assertMatchesReference(ref, d, "prefix " + cut);
        }
    }

    @Test
    void randomTracesMatchReference() throws IOException {
        for (long seed = 1; seed <= 15; seed++) {
            byte[] bytes = RandomTraces.random(seed);
            ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
            TraceSnapshot d = TestTraces.index(bytes, 1 << 30);
            String ctx = "seed " + seed;
            assertFalse(ref.truncated, ctx);
            assertMatchesReference(ref, d, ctx);
            Map<Integer, Long> calls = new java.util.HashMap<>();
            for (ReferenceDecoder.Call f : ref.calls) {
                if (!f.unclosed()) {
                    calls.merge(f.methodId(), 1L, Long::sum);
                }
            }
            for (Map.Entry<Integer, Long> e : calls.entrySet()) {
                int methodId = e.getKey();
                assertEquals(e.getValue().longValue(), d.callsByMethod[methodId], ctx + " calls #" + methodId);
            }
        }
    }

    @Test
    void adaptiveOverviewEqualsTwoPassThreshold() throws IOException {
        for (long seed = 1; seed <= 10; seed++) {
            byte[] bytes = RandomTraces.random(seed);
            ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
            int budget = 64;
            TraceSnapshot d = TestTraces.index(bytes, budget);
            long expectedD = TestTraces.expectedThreshold(ref.calls, budget);
            assertEquals(expectedD, d.overviewThresholdNs, "seed " + seed);
            Map<Long, List<ReferenceDecoder.Call>> byTid = TestTraces
                    .byTid(ref.calls.stream().filter(f -> f.durNs() >= expectedD || f.unclosed()).toList());
            for (ThreadIndex m : d.threads) {
                List<ReferenceDecoder.Call> exp = byTid.getOrDefault(m.tid, List.of());
                assertEquals(exp.size(), m.overview.count, "seed " + seed + " tid " + m.tid);
                for (int i = 0; i < exp.size(); i++) {
                    ReferenceDecoder.Call f = exp.get(i);
                    String c = "seed " + seed + " tid " + m.tid + " frame " + i;
                    assertEquals(f.startNs(), m.overview.startNs[i], c);
                    assertEquals(f.durNs(), m.overview.durNs[i], c);
                    assertEquals(f.methodId(), m.overview.methodId[i], c);
                    assertEquals(f.depth(), m.overview.depth[i], c);
                    assertEquals(f.selfNs(), m.overview.selfNs[i], c);
                    assertEquals(f.unclosed(), m.overview.unclosed[i], c);
                }
            }
        }
    }

    static void assertMatchesReference(ReferenceDecoder.Result ref, TraceSnapshot d, String ctx) {
        assertEquals(ref.startEpochMs, d.startEpochMs, ctx + " startEpochMs");
        assertEquals(ref.utcOffsetSeconds, d.utcOffsetSeconds, ctx + " utcOffsetSeconds");
        assertEquals(ref.calls.size(), d.totalCalls, ctx + " totalCalls");
        assertEquals(ref.threadNames, d.threadNames, ctx + " threadNames");
        for (Map.Entry<Integer, String> e : ref.methodNames.entrySet()) {
            assertEquals(e.getValue(), d.methodName(e.getKey()), ctx + " name #" + e.getKey());
        }
        for (Map.Entry<Integer, String> e : ref.exceptionNames.entrySet()) {
            assertEquals(e.getValue(), d.exceptionName(e.getKey()), ctx + " exception #" + e.getKey());
        }
        assertEquals(ref.exceptionNames.size(), d.totalExceptions, ctx + " totalExceptions");
        assertEquals(ref.gc.size(), d.gc.count, ctx + " gc count");
        @Var long gcTotal = 0;
        for (int i = 0; i < ref.gc.size(); i++) {
            ReferenceDecoder.GcPause g = ref.gc.get(i);
            String c = ctx + " gc " + i;
            assertEquals(g.startNs(), d.gc.startNs[i], c + " start");
            assertEquals(g.durNs(), d.gc.durNs[i], c + " dur");
            assertEquals(g.action(), d.gc.action[i], c + " action");
            assertEquals(g.name(), d.gc.collector[i], c + " name");
            assertEquals(g.cause(), d.gc.cause[i], c + " cause");
            gcTotal += g.durNs();
        }
        assertEquals(gcTotal, d.gc.totalNs, ctx + " gc totalNs");
        assertEquals(ref.sessions.size(), d.sessions.size(), ctx + " session count");
        for (int i = 0; i < ref.sessions.size(); i++) {
            ReferenceDecoder.Session rs = ref.sessions.get(i);
            Session s = d.sessions.get(i);
            String c = ctx + " session " + (i + 1);
            assertEquals(rs.seq, s.seq, c);
            assertEquals(rs.tid, s.tid, c);
            assertEquals(rs.rootMethodId, s.rootMethodId, c);
            assertEquals(rs.startNs, s.startNs, c);
            assertEquals(rs.endNs, s.endNs, c);
            assertEquals(rs.ended, s.ended, c);
            assertEquals(rs.callCount, s.callCount, c);
        }
        Map<Long, List<ReferenceDecoder.Call>> byTid = TestTraces.byTid(ref.calls);
        assertEquals(0, d.overviewThresholdNs, ctx);
        @Var int tids = 0;
        for (ThreadIndex m : d.threads) {
            List<ReferenceDecoder.Call> exp = byTid.getOrDefault(m.tid, List.of());
            if (!exp.isEmpty()) {
                tids++;
            }
            assertEquals(exp.size(), m.overview.count, ctx + " tid " + m.tid + " frames");
            @Var int maxDepth = 0;
            for (int i = 0; i < exp.size(); i++) {
                ReferenceDecoder.Call f = exp.get(i);
                String c = ctx + " tid " + m.tid + " frame " + i;
                assertEquals(f.startNs(), m.overview.startNs[i], c + " start");
                assertEquals(f.durNs(), m.overview.durNs[i], c + " dur");
                assertEquals(f.methodId(), m.overview.methodId[i], c + " method");
                assertEquals(f.depth(), m.overview.depth[i], c + " depth");
                assertEquals(f.selfNs(), m.overview.selfNs[i], c + " self");
                assertEquals(f.unclosed(), m.overview.unclosed[i], c + " unclosed");
                assertEquals(f.exceptionId(), m.overview.exceptionId[i], c + " exc");
                maxDepth = Math.max(maxDepth, f.depth());
            }
            assertEquals(maxDepth, m.maxDepth, ctx + " tid " + m.tid + " maxDepth");
        }
        assertEquals(byTid.size(), tids, ctx + " thread count");

        Map<Long, Integer> nextChunk = new java.util.HashMap<>();
        for (Session s : d.sessions) {
            String c = ctx + " session " + s.seq + " chunks";
            assertEquals(nextChunk.getOrDefault(s.tid, 0).intValue(), s.firstChunk, c + " first");
            assertTrue(s.lastChunk >= s.firstChunk, c + " owns at least one chunk");
            nextChunk.put(s.tid, s.lastChunk + 1);
            ThreadIndex m = d.thread(s.tid);
            if (m != null) {
                assertEquals(s.startNs, m.chunks.baseTicks[s.firstChunk] * Vbtm.NANOS_PER_TICK, c + " start anchor");
                for (int i = s.firstChunk; i <= s.lastChunk; i++) {
                    assertEquals(i == s.lastChunk && s.ended, m.chunks.endsSession[i], c + " cIsEnd[" + i + "]");
                }
            }
        }
        for (ThreadIndex m : d.threads) {
            assertEquals(m.chunks.count, nextChunk.getOrDefault(m.tid, 0).intValue(), ctx + " tid " + m.tid + " chunk partition");
            for (int i = 0; i < m.chunks.count; i++) {
                assertTrue(m.chunks.baseTicks[i] <= m.chunks.endTicks[i], ctx + " tid " + m.tid + " chunk " + i + " base <= last");
                if (i > 0) {
                    assertTrue(m.chunks.endTicks[i - 1] <= m.chunks.baseTicks[i], ctx + " tid " + m.tid + " chunk " + i + " starts at or after the previous chunk's last tick");
                }
            }
        }
    }

}
