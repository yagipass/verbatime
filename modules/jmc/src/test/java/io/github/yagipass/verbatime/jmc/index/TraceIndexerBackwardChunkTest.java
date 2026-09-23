package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;

final class TraceIndexerBackwardChunkTest {

    private static TraceSnapshot frameClosedInABackwardChunk() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "a()V");
        w.clazz(1, "pkg.B", "b()V");
        final TraceBuilder.Payload first = new TraceBuilder.Payload(100);
        first.enter(100, 0).enter(110, 1).exit(120);
        w.chunk(1, 100, first.bytes(), false);
        final TraceBuilder.Payload second = new TraceBuilder.Payload(90);
        second.exit(90).enter(100, 1).exit(105);
        w.chunk(1, 90, second.bytes(), true);
        return TestTraces.index(w);
    }

    private static void assertMonotonicChunkIndex(final ThreadIndex m) {
        for (int i = 0; i < m.chunks.count; i++) {
            assertTrue(m.chunks.baseTicks[i] <= m.chunks.endTicks[i], "chunk " + i + " base <= last");
            if (i > 0) {
                assertTrue(m.chunks.endTicks[i - 1] <= m.chunks.baseTicks[i],
                        "chunk " + i + " must not start before the previous chunk's last tick, or ChunkWalker's binary search skips chunks");
            }
        }
    }

    @Test
    void backwardChunkIsShiftedToTheThreadsLastTickInsteadOfYieldingNegativeDurations() throws IOException {
        final TraceSnapshot d = frameClosedInABackwardChunk();
        final ThreadIndex m = d.thread(1);
        assertEquals(2, m.chunks.count, "both chunks are indexed");
        assertEquals(120, m.chunks.baseTicks[1], "the backward chunk is moved up to the last tick the thread had seen");
        assertMonotonicChunkIndex(m);

        int outer = -1;
        int trailing = -1;
        for (int i = 0; i < m.overview.count; i++) {
            assertTrue(m.overview.durNs[i] >= 0, "frame " + i + " duration must not be negative");
            assertTrue(m.overview.selfNs[i] >= 0, "frame " + i + " self time must not be negative");
            if (m.overview.depth[i] == 0) {
                if (m.overview.methodId[i] == 0) {
                    outer = i;
                } else {
                    trailing = i;
                }
            }
        }
        assertTrue(outer >= 0, "the frame that spans the chunk boundary is in the overview");
        assertEquals(20 * Vbtm.NANOS_PER_TICK, m.overview.durNs[outer],
                "the closing exit is chunk 2's first event, so it lands on the shifted base 120 and the frame is 20 ticks, not -10");
        assertEquals(10 * Vbtm.NANOS_PER_TICK, m.overview.selfNs[outer], "self = 20 minus the 10-tick child");
        assertTrue(trailing >= 0, "the frame recorded after the closing exit is in the overview");
        assertEquals(130 * Vbtm.NANOS_PER_TICK, m.overview.startNs[trailing],
                "the shift moves the whole chunk: 10 ticks after the exit stays 10 ticks after the shifted exit");
        assertEquals(5 * Vbtm.NANOS_PER_TICK, m.overview.durNs[trailing], "durations inside the shifted chunk are preserved");
        assertTrue(d.sessions.get(0).ended, "the END chunk still closes the session");
        assertEquals(135 * Vbtm.NANOS_PER_TICK, d.sessions.get(0).endNs, "the session ends where the shifted chunk ends");
    }

    @Test
    void rangeQueriesStillFindTheFrameThatSpansTheBackwardChunk() throws IOException {
        final TraceSnapshot d = frameClosedInABackwardChunk();
        assertEquals(0, ChunkWalker.firstChunkEndingAtOrAfter(d.thread(1), 100 * Vbtm.NANOS_PER_TICK),
                "a window starting in chunk 1 must begin there, not skip to the chunk whose base was behind");
        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, 100 * Vbtm.NANOS_PER_TICK, 20 * Vbtm.NANOS_PER_TICK, 0, 0);
        assertTrue(agg.found(), "the spanning frame is reachable by a range query after the repair");
    }

    @Test
    void sessionStartingBeforeThePreviousSessionsLastTickIsShiftedToo() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "a()V");
        final TraceBuilder.Payload first = new TraceBuilder.Payload(100);
        first.enter(100, 0).exit(120);
        w.chunk(1, 100, first.bytes(), true);
        final TraceBuilder.Payload second = new TraceBuilder.Payload(50);
        second.enter(50, 0).exit(60);
        w.chunk(1, 50, second.bytes(), true);
        final TraceSnapshot d = TestTraces.index(w);
        final ThreadIndex m = d.thread(1);
        assertEquals(2, d.sessions.size(), "two sessions on the thread");
        assertEquals(120 * Vbtm.NANOS_PER_TICK, d.sessions.get(1).startNs,
                "the chunk index is one per-thread array across sessions, so a session cannot start before the previous one's last tick");
        assertEquals(120, m.chunks.baseTicks[1], "the session's first chunk carries the shifted base");
        assertMonotonicChunkIndex(m);
        assertEquals(10 * Vbtm.NANOS_PER_TICK, m.overview.durNs[1], "the shift preserves durations inside the chunk");
    }
}
