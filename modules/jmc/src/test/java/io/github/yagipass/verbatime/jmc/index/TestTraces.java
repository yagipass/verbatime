package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.format.TraceBuilder;

public final class TestTraces {

    private TestTraces() {
    }

    public static final long DEFAULT_START_EPOCH_MS = OffsetDateTime.parse("2026-09-02T13:38:05+09:00").toInstant()
            .toEpochMilli();

    public static final int DEFAULT_UTC_OFFSET_SECONDS = 9 * 3600;

    public static TraceBuilder writer() {
        return new TraceBuilder(DEFAULT_START_EPOCH_MS, DEFAULT_UTC_OFFSET_SECONDS);
    }

    public static TraceSnapshot index(final TraceBuilder w) throws IOException {
        return index(w.bytes(), 1 << 20);
    }

    public static TraceSnapshot index(final byte[] bytes, final int overviewBudget) throws IOException {
        final Path f = tempFile();
        Files.write(f, bytes);
        return TraceIndexer.index(f, overviewBudget, TraceIndexer.ProgressListener.NONE);
    }

    public static Path tempFile() throws IOException {
        final Path f = Files.createTempFile("vbtm-test", ".vbtm");
        f.toFile().deleteOnExit();
        return f;
    }

    public static void append(final Path f, final byte[] all, final int from, final int to) throws IOException {
        Files.write(f, Arrays.copyOfRange(all, from, to), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static void assertSameTraceData(final TraceSnapshot e, final TraceSnapshot a, final String ctx) {
        assertEquals(e.buffer.size(), a.buffer.size(), ctx + " file size");
        assertEquals(e.truncated, a.truncated, ctx + " truncated");
        assertEquals(e.corruptOffset, a.corruptOffset, ctx + " corruptOffset");
        assertEquals(e.corruptReason, a.corruptReason, ctx + " corruptReason");
        assertEquals(e.startEpochMs, a.startEpochMs, ctx + " startEpochMs");
        assertEquals(e.utcOffsetSeconds, a.utcOffsetSeconds, ctx + " utcOffsetSeconds");
        assertEquals(e.minNs, a.minNs, ctx + " minNs");
        assertEquals(e.maxNs, a.maxNs, ctx + " maxNs");
        assertEquals(e.totalCalls, a.totalCalls, ctx + " totalCalls");
        assertEquals(e.overviewThresholdNs, a.overviewThresholdNs, ctx + " overviewThresholdNs");
        assertEquals(e.totalMethods, a.totalMethods, ctx + " totalMethods");
        assertEquals(e.threadNames, a.threadNames, ctx + " threadNames");
        final int names = Math.max(e.methodNames.length, a.methodNames.length);
        for (int i = 0; i < names; i++) {
            assertEquals(e.methodName(i), a.methodName(i), ctx + " methodName " + i);
        }
        assertEquals(e.totalExceptions, a.totalExceptions, ctx + " totalExceptions");
        final int excNames = Math.max(e.exceptionNames.length, a.exceptionNames.length);
        for (int i = 0; i < excNames; i++) {
            assertEquals(e.exceptionName(i), a.exceptionName(i), ctx + " exceptionName " + i);
        }
        assertEquals(e.gc.count, a.gc.count, ctx + " gc count");
        assertEquals(e.gc.totalNs, a.gc.totalNs, ctx + " gc totalNs");
        final int gcs = e.gc.count;
        assertArrayEquals(Arrays.copyOf(e.gc.startNs, gcs), Arrays.copyOf(a.gc.startNs, gcs), ctx + " gc startNs");
        assertArrayEquals(Arrays.copyOf(e.gc.durNs, gcs), Arrays.copyOf(a.gc.durNs, gcs), ctx + " gc durNs");
        assertArrayEquals(Arrays.copyOf(e.gc.action, gcs), Arrays.copyOf(a.gc.action, gcs), ctx + " gc action");
        assertArrayEquals(Arrays.copyOf(e.gc.collector, gcs), Arrays.copyOf(a.gc.collector, gcs), ctx + " gc collector");
        assertArrayEquals(Arrays.copyOf(e.gc.cause, gcs), Arrays.copyOf(a.gc.cause, gcs), ctx + " gc cause");
        final int agg = Math.max(e.callsByMethod.length, a.callsByMethod.length);
        for (int i = 0; i < agg; i++) {
            assertEquals(at(e.callsByMethod, i), at(a.callsByMethod, i), ctx + " callsByMethod " + i);
        }
        assertEquals(e.sessions.size(), a.sessions.size(), ctx + " session count");
        for (int i = 0; i < e.sessions.size(); i++) {
            final TraceSnapshot.Session x = e.sessions.get(i);
            final TraceSnapshot.Session y = a.sessions.get(i);
            final String c = ctx + " session " + x.seq;
            assertEquals(x.seq, y.seq, c + " seq");
            assertEquals(x.tid, y.tid, c + " tid");
            assertEquals(x.rootMethodId, y.rootMethodId, c + " rootMethodId");
            assertEquals(x.startNs, y.startNs, c + " startNs");
            assertEquals(x.endNs, y.endNs, c + " endNs");
            assertEquals(x.ended, y.ended, c + " ended");
            assertEquals(x.callCount, y.callCount, c + " frames");
            assertEquals(x.firstChunk, y.firstChunk, c + " firstChunk");
            assertEquals(x.lastChunk, y.lastChunk, c + " lastChunk");
        }
        assertEquals(e.threads.size(), a.threads.size(), ctx + " thread count");
        for (int i = 0; i < e.threads.size(); i++) {
            final TraceSnapshot.ThreadIndex x = e.threads.get(i);
            final TraceSnapshot.ThreadIndex y = a.threads.get(i);
            final String c = ctx + " tid " + x.tid;
            assertEquals(x.tid, y.tid, c + " order");
            assertEquals(x.maxDepth, y.maxDepth, c + " maxDepth");
            assertEquals(x.totalCalls, y.totalCalls, c + " totalCalls");
            assertEquals(x.chunks.count, y.chunks.count, c + " chunkCount");
            final int n = x.chunks.count;
            assertArrayEquals(Arrays.copyOf(x.chunks.payloadOffset, n), Arrays.copyOf(y.chunks.payloadOffset, n), c + " chunks.payloadOffset");
            assertArrayEquals(Arrays.copyOf(x.chunks.payloadEnd, n), Arrays.copyOf(y.chunks.payloadEnd, n), c + " chunks.payloadEnd");
            assertArrayEquals(Arrays.copyOf(x.chunks.baseTicks, n), Arrays.copyOf(y.chunks.baseTicks, n), c + " chunks.baseTicks");
            assertArrayEquals(Arrays.copyOf(x.chunks.endTicks, n), Arrays.copyOf(y.chunks.endTicks, n), c + " chunks.endTicks");
            assertArrayEquals(Arrays.copyOf(x.chunks.openDepthAtStart, n), Arrays.copyOf(y.chunks.openDepthAtStart, n), c + " chunks.openDepthAtStart");
            assertArrayEquals(Arrays.copyOf(x.chunks.endsSession, n), Arrays.copyOf(y.chunks.endsSession, n), c + " chunks.endsSession");
            assertEquals(x.overview.count, y.overview.count, c + " overview.count");
            final int o = x.overview.count;
            assertArrayEquals(Arrays.copyOf(x.overview.startNs, o), Arrays.copyOf(y.overview.startNs, o), c + " overview.startNs");
            assertArrayEquals(Arrays.copyOf(x.overview.durNs, o), Arrays.copyOf(y.overview.durNs, o), c + " overview.durNs");
            assertArrayEquals(Arrays.copyOf(x.overview.selfNs, o), Arrays.copyOf(y.overview.selfNs, o), c + " overview.selfNs");
            assertArrayEquals(Arrays.copyOf(x.overview.methodId, o), Arrays.copyOf(y.overview.methodId, o), c + " overview.methodId");
            assertArrayEquals(Arrays.copyOf(x.overview.depth, o), Arrays.copyOf(y.overview.depth, o), c + " overview.depth");
            assertArrayEquals(Arrays.copyOf(x.overview.unclosed, o), Arrays.copyOf(y.overview.unclosed, o), c + " overview.unclosed");
            assertArrayEquals(Arrays.copyOf(x.overview.exceptionId, o), Arrays.copyOf(y.overview.exceptionId, o), c + " overview.exceptionId");
        }
    }

    private static long at(final long[] a, final int i) {
        return i < a.length ? a[i] : 0;
    }

    public static Map<Long, List<ReferenceDecoder.Call>> byTid(final List<ReferenceDecoder.Call> frames) {
        final Map<Long, List<ReferenceDecoder.Call>> out = new HashMap<>();
        for (final ReferenceDecoder.Call f : frames) {
            out.computeIfAbsent(f.tid(), t -> new ArrayList<>()).add(f);
        }
        final Comparator<ReferenceDecoder.Call> cmp = Comparator.comparingLong(ReferenceDecoder.Call::startNs)
                .thenComparingInt(ReferenceDecoder.Call::depth).thenComparingLong(ReferenceDecoder.Call::durNs);
        for (final List<ReferenceDecoder.Call> l : out.values()) {
            l.sort(cmp);
        }
        return out;
    }

    static long expectedThreshold(final List<ReferenceDecoder.Call> frames, final int budget) {
        final long[] hist = new long[DurationHistogram.SIZE];
        for (final ReferenceDecoder.Call f : frames) {
            hist[DurationHistogram.bucketIndex(f.durNs())]++;
        }
        return DurationHistogram.chooseThreshold(hist, frames.size(), budget);
    }
}
