package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class OutlineHeapTest {

    private static void offer(final OutlineHeap t, final long dur, final long seq, final long line) {
        t.offer(dur, seq, line, 0, 0, 0, 0, 0, 1, (byte) 0, 0);
    }

    @Test
    void keepsTheLongestCallsAndOnATieTheLaterOne() {
        final OutlineHeap t = new OutlineHeap(3);
        offer(t, 50, 1, 10);
        offer(t, 10, 2, 20);
        offer(t, 30, 3, 30);
        offer(t, 40, 4, 40);
        offer(t, 30, 5, 50);
        offer(t, 5, 6, 60);
        assertEquals(3, t.size());
        assertEquals(30, t.thresholdTicks(), "the shortest kept call is the outline threshold");
        final List<Long> kept = new ArrayList<>();
        for (final int i : t.byLine()) {
            kept.add(t.line(i));
        }
        assertEquals(List.of(10L, 40L, 50L), kept,
                "of the two 30-tick calls the later one, seq 5, wins, so the outline prefers recent calls on ties");
    }

    @Test
    void byLineOrdersEntriesByBodyLineNotByDuration() {
        final OutlineHeap t = new OutlineHeap(4);
        offer(t, 1, 1, 300);
        offer(t, 9, 2, 100);
        offer(t, 5, 3, 200);
        final int[] order = t.byLine();
        assertArrayEquals(new long[] { 100, 200, 300 }, new long[] { t.line(order[0]), t.line(order[1]), t.line(order[2]) });
        assertEquals(1, t.thresholdTicks(), "the heap is not full, so the smallest entry sets the threshold");
    }

    @Test
    void everyColumnMovesTogetherWhenTheHeapReorders() {
        final OutlineHeap t = new OutlineHeap(2);
        t.offer(7, 1, 11, 100, 3, 2, 42, 5, 6, (byte) 3, 9);
        t.offer(8, 2, 12, 200, 4, 1, 43, 6, 7, (byte) 0, 0);
        t.offer(9, 3, 13, 300, 5, 0, 44, 7, 8, (byte) 1, 2);
        final int[] order = t.byLine();
        final int a = order[0];
        assertEquals(12, t.line(a));
        assertEquals(8, t.dur(a));
        assertEquals(200, t.start(a));
        assertEquals(4, t.self(a));
        assertEquals(1, t.depth(a));
        assertEquals(43, t.methodId(a));
        assertEquals(6, t.children(a));
        assertEquals(7, t.subLines(a));
        assertTrue(!t.thrown(a) && !t.unclosed(a));
        final int b = order[1];
        assertEquals(13, t.line(b));
        assertEquals(44, t.methodId(b));
        assertTrue(t.thrown(b) && !t.unclosed(b));
        assertEquals(2, t.excNo(b));
    }
}
