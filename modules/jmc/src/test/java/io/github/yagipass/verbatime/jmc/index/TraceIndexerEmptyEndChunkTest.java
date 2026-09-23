package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;
import io.github.yagipass.verbatime.jmc.query.SubtreeAggregate;

final class TraceIndexerEmptyEndChunkTest {

    private static TraceSnapshot agentStyleTrace() throws IOException {
        final TraceBuilder w = TestTraces.writer();
        w.thread(1, "main");
        w.clazz(0, "pkg.A", "a()V");
        final TraceBuilder.Payload first = new TraceBuilder.Payload(100);
        first.enter(100, 0).exit(120);
        w.chunk(1, 100, first.bytes(), false);
        w.chunk(1, 0, new byte[0], true);
        final TraceBuilder.Payload second = new TraceBuilder.Payload(300);
        second.enter(300, 0).exit(310);
        w.chunk(1, 300, second.bytes(), true);
        return TestTraces.index(w);
    }

    @Test
    void emptyEndChunkWithZeroBaseIsIndexedAtTheThreadsLastTick() throws IOException {
        final TraceSnapshot d = agentStyleTrace();
        final ThreadIndex m = d.thread(1);
        assertEquals(3, m.chunks.count, "the empty END chunk stays in the index because it carries the session end");
        assertArrayEquals(new boolean[] { false, true, true }, Arrays.copyOf(m.chunks.endsSession, 3), "END flags");
        for (int i = 0; i < m.chunks.count; i++) {
            assertTrue(m.chunks.baseTicks[i] <= m.chunks.endTicks[i], "chunk " + i + " base <= last");
            if (i > 0) {
                assertTrue(m.chunks.endTicks[i - 1] <= m.chunks.baseTicks[i], "chunk " + i + " keeps the arrays monotonic");
            }
        }
        assertEquals(120, m.chunks.baseTicks[1], "the empty chunk sits at the last tick the thread had seen");
        assertTrue(d.sessions.get(0).ended, "session 1 is closed by the empty END chunk");
    }

    @Test
    void rangeQueriesStillFindTheChunksBeforeTheEmptyEndChunk() throws IOException {
        final TraceSnapshot d = agentStyleTrace();
        final ThreadIndex m = d.thread(1);
        assertEquals(0, ChunkWalker.firstChunkEndingAtOrAfter(m, 100 * Vbtm.NANOS_PER_TICK),
                "a window starting inside session 1 must begin at its chunk, not skip past the 0 entry");
        final SubtreeAggregate agg = SubtreeAggregate.compute(d, 1, 100 * Vbtm.NANOS_PER_TICK, 20 * Vbtm.NANOS_PER_TICK, 0, 0);
        assertTrue(agg.found(), "the frame recorded before the empty END chunk is still reachable by a range query");
    }
}
