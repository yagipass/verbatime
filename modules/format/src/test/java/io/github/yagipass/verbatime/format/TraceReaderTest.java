package io.github.yagipass.verbatime.format;

import static io.github.yagipass.verbatime.format.GoldenTrace.CHUNK_PAYLOAD_OFFSET;
import static io.github.yagipass.verbatime.format.GoldenTrace.EPOCH_MS;
import static io.github.yagipass.verbatime.format.GoldenTrace.GOLDEN;
import static io.github.yagipass.verbatime.format.GoldenTrace.PAYLOAD;
import static io.github.yagipass.verbatime.format.GoldenTrace.UTC_OFFSET;
import static io.github.yagipass.verbatime.format.GoldenTrace.bytes;
import static io.github.yagipass.verbatime.format.GoldenTrace.golden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceReader.Outcome;

final class TraceReaderTest {

    private static final class VisitLog implements TraceReader.Visitor {

        final List<String> calls = new ArrayList<>();

        @Override
        public void anchor(final long startEpochMs, final int utcOffsetSeconds) {
            calls.add("anchor " + startEpochMs + " " + utcOffsetSeconds);
        }

        @Override
        public void thread(final long tid, final String name) {
            calls.add("thread " + tid + " " + name);
        }

        @Override
        public void clazz(final long baseId, final String className, final String[] sigs) {
            calls.add("class " + baseId + " " + className + " " + Arrays.toString(sigs));
        }

        @Override
        public void exception(final long id, final String className) {
            calls.add("exception " + id + " " + className);
        }

        @Override
        public void gc(final long startTicks, final long durTicks, final int action, final String name,
                final String cause) {
            calls.add("gc " + startTicks + " " + durTicks + " " + action + " " + name + " " + cause);
        }

        @Override
        public void chunk(final long tid, final long baseTicks, final byte[] bytes, final int off, final int len,
                final boolean sessionEnd, final boolean truncated) {
            calls.add("chunk " + tid + " " + baseTicks + " " + Arrays.toString(Arrays.copyOfRange(bytes, off, off + len))
                    + (sessionEnd ? " end" : "") + (truncated ? " partial" : ""));
        }

        @Override
        public void end() {
            calls.add("end");
        }
    }

    private static List<String> goldenCalls() {
        return List.of("anchor " + EPOCH_MS + " " + UTC_OFFSET, "thread 7 main", "class 0 a.B [m()V, n(I)V]",
                "exception 1 x.E", "gc 300 20 1 G1 Alloc", "chunk 7 100 " + Arrays.toString(bytes(PAYLOAD)),
                "chunk 7 200 [] end", "end");
    }

    @Test
    void theReaderDecodesTheGoldenBytesIntoTheSameRecordsInOrder() {
        final VisitLog r = new VisitLog();
        assertEquals(Outcome.CLEAN, TraceReader.read(golden(), r));
        assertEquals(goldenCalls(), r.calls);
    }

    @Test
    void aCutInsideAChunkReportsThePartialPayloadAndTruncation() {
        final VisitLog r = new VisitLog();
        final byte[] cut = Arrays.copyOf(golden(), CHUNK_PAYLOAD_OFFSET + 3);
        assertEquals(Outcome.TRUNCATED, TraceReader.read(cut, r));
        assertEquals("chunk 7 100 " + Arrays.toString(Arrays.copyOf(bytes(PAYLOAD), 3)) + " partial",
                r.calls.get(r.calls.size() - 1));
    }

    @Test
    void aTraceWithoutTheEndRecordIsTruncatedEvenWhenEveryRecordIsWhole() {
        final VisitLog r = new VisitLog();
        assertEquals(Outcome.TRUNCATED, TraceReader.read(Arrays.copyOf(golden(), GOLDEN.length - 1), r));
        assertEquals(goldenCalls().subList(0, goldenCalls().size() - 1), r.calls);
    }

    @Test
    void theMagicAndTheVersionByteAreCheckedBeforeAnything() {
        final byte[] oldMagic = golden();
        System.arraycopy(new byte[] { 'v', 'a', 'f', '1' }, 0, oldMagic, 0, 4);
        CorruptTraceException e = assertThrows(CorruptTraceException.class,
                () -> TraceReader.read(oldMagic, new VisitLog()));
        assertEquals(0, e.offset());
        assertTrue(e.getMessage().contains("magic"), e.getMessage());

        final byte[] newer = golden();
        newer[Vbtm.VERSION_OFFSET] = (byte) (Vbtm.VERSION + 1);
        e = assertThrows(CorruptTraceException.class, () -> TraceReader.read(newer, new VisitLog()));
        assertEquals(Vbtm.VERSION_OFFSET, e.offset(), "the version is refused before the anchor is even looked at");
        assertTrue(e.getMessage().contains("version " + (Vbtm.VERSION + 1)), e.getMessage());
        assertTrue(e.getMessage().contains("version " + Vbtm.VERSION), e.getMessage());
    }

    @Test
    void aCutInsideAnyRecordHeaderIsTruncatedNotCorrupt() {
        for (int n = Vbtm.MAGIC_BYTES; n < GOLDEN.length; n++) {
            final byte[] cut = Arrays.copyOf(golden(), n);
            assertEquals(Outcome.TRUNCATED, TraceReader.read(cut, new VisitLog()), "cut at " + n);
        }
    }

    @Test
    void theReaderRejectsWhatTheViewerRejects() {
        rejects(new TraceBuilder(EPOCH_MS, Vbtm.MAX_UTC_OFFSET_SECONDS + 1), "UTC offset");
        rejects(new TraceBuilder(EPOCH_MS, Integer.MIN_VALUE), "UTC offset");
        rejects(trace().clazz(Vbtm.METHOD_ID_LIMIT - 1, "a.B", "m()V", "n()V"), "2^22");
        rejects(trace().clazz(Long.MAX_VALUE, "a.B", "m()V"), "2^22");
        rejects(trace().exception(0, "x.E"), "exception id 0");
        rejects(trace().exception(Vbtm.EXCEPTION_ID_LIMIT, "x.E"), "exception id " + Vbtm.EXCEPTION_ID_LIMIT);
        rejects(trace().gc(Vbtm.MAX_TICKS + 1, 0, Vbtm.GC_ACTION_MINOR, "G1", "Alloc"), "ticks out of range");
        rejects(trace().gc(1, Vbtm.MAX_TICKS, Vbtm.GC_ACTION_MINOR, "G1", "Alloc"), "ticks out of range");
        rejects(trace().gc(0, 0, Vbtm.GC_ACTION_MAJOR + 1, "G1", "Alloc"), "unknown GC action");
        rejects(trace().gc(0, 0, Vbtm.GC_ACTION_MINOR, "x".repeat(Vbtm.MAX_GC_LABEL_BYTES + 1), ""), "collector name");
        rejects(trace().gc(0, 0, Vbtm.GC_ACTION_MINOR, "", "x".repeat(Vbtm.MAX_GC_LABEL_BYTES + 1)), "GC cause");
        rejects(varint(trace().rawBytes(Vbtm.RECORD_CHUNK, 7, 0), Vbtm.MAX_CHUNK_PAYLOAD_BYTES + 1), "payload length");
        rejects(varint(trace().rawBytes(Vbtm.RECORD_CHUNK, 7), Vbtm.MAX_TICKS + 1).rawBytes(0), "base ticks");
        rejects(varint(trace().rawBytes(Vbtm.RECORD_CHUNK_END, 7), -1L).rawBytes(0), "base ticks");
        rejects(varint(trace().rawBytes(Vbtm.RECORD_THREAD, 7), -1L), "negative string length");
        rejects(varint(trace().rawBytes(Vbtm.RECORD_GC, 0, 0, Vbtm.GC_ACTION_MINOR), -1L), "negative string length");
        rejects(trace().rawBytes(0x7F), "unknown record type");
        rejects(trace().end().rawBytes(Vbtm.RECORD_THREAD), "after the END record");
        final int[] tenContinuations = new int[Varint.MAX_BYTES];
        Arrays.fill(tenContinuations, 0x80);
        rejects(trace().rawBytes(Vbtm.RECORD_THREAD).rawBytes(tenContinuations), "varint too long");
    }

    @Test
    void theReaderAcceptsValuesAtTheLimits() {
        final TraceBuilder w = new TraceBuilder(EPOCH_MS, Vbtm.MAX_UTC_OFFSET_SECONDS)
                .clazz(Vbtm.METHOD_ID_LIMIT - 1, "a.B", "m()V").exception(Vbtm.EXCEPTION_ID_LIMIT - 1, "x.E")
                .gc(1, Vbtm.MAX_TICKS - 1, Vbtm.GC_ACTION_MAJOR, "x".repeat(Vbtm.MAX_GC_LABEL_BYTES), "")
                .chunk(7, Vbtm.MAX_TICKS, new byte[0], true).end();
        final VisitLog r = new VisitLog();
        assertEquals(Outcome.CLEAN, TraceReader.read(w.bytes(), r));
        assertEquals(6, r.calls.size());
        final VisitLog west = new VisitLog();
        assertEquals(Outcome.CLEAN,
                TraceReader.read(new TraceBuilder(EPOCH_MS, -Vbtm.MAX_UTC_OFFSET_SECONDS).end().bytes(), west));
        assertEquals("anchor " + EPOCH_MS + " " + -Vbtm.MAX_UTC_OFFSET_SECONDS, west.calls.get(0));
    }

    private static TraceBuilder trace() {
        return new TraceBuilder(EPOCH_MS, UTC_OFFSET);
    }

    private static TraceBuilder varint(final TraceBuilder w, final long v) {
        final byte[] b = new byte[Varint.MAX_BYTES];
        final int n = Varint.put(b, 0, v);
        final int[] raw = new int[n];
        for (int i = 0; i < n; i++) {
            raw[i] = b[i] & 0xFF;
        }
        return w.rawBytes(raw);
    }

    private static void rejects(final TraceBuilder w, final String reason) {
        final CorruptTraceException e = assertThrows(CorruptTraceException.class,
                () -> TraceReader.read(w.bytes(), new VisitLog()), reason);
        assertTrue(e.getMessage().contains(reason), e.getMessage());
    }
}
