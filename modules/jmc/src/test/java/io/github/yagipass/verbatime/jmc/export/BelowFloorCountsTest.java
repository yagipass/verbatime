package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class BelowFloorCountsTest {

    @Test
    void callsOfOneParentAreCountedPerMethodNotPerCall() {
        final BelowFloorCounts c = new BelowFloorCounts();
        c.increment(7);
        c.increment(3);
        c.increment(7);
        c.increment(7);
        assertEquals(2, c.size(), "two distinct methods were called below the floor");
        assertEquals(7, c.methodId(0), "first seen first: the accounting line lists methods in first-call order");
        assertEquals(3, c.count(0));
        assertEquals(3, c.methodId(1));
        assertEquals(1, c.count(1));
    }

    @Test
    void clearEmptiesTheTableSoTheNextParentStartsFromZero() {
        final BelowFloorCounts c = new BelowFloorCounts();
        c.increment(1);
        c.increment(2);
        c.clear();
        assertEquals(0, c.size());
        c.increment(2);
        assertEquals(1, c.size());
        assertEquals(2, c.methodId(0));
        assertEquals(1, c.count(0), "a count from the previous parent must not carry over");
    }

    @Test
    void manyDistinctMethodsGrowTheTableAndKeepEveryCount() {
        final BelowFloorCounts c = new BelowFloorCounts();
        for (int round = 1; round <= 3; round++) {
            for (int id = 0; id < 100; id++) {
                c.increment(id * 31);
            }
        }
        assertEquals(100, c.size());
        for (int i = 0; i < 100; i++) {
            assertEquals(i * 31, c.methodId(i), "insertion order survives a rehash");
            assertEquals(3, c.count(i));
        }
        assertEquals(0, c.methodId(0), "method id 0 is a valid key, distinct from the empty slot");
    }
}
