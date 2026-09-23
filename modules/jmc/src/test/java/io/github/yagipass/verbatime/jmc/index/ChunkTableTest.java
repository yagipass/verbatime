package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChunkTableTest {

    @Test
    void copySharesNoArrayWithItsSourceSoASnapshotStaysFrozen() {
        final ChunkTable t = new ChunkTable();
        for (int i = 0; i < 10; i++) {
            t.append(100L * i, 100L * i + 50, 10L * i, 10L * i + 9, i % 3);
        }
        t.markSessionEnd(9);
        final ChunkTable c = t.copy();
        assertEquals(10, c.count);
        assertEquals(900, c.payloadOffset[9]);
        assertEquals(50, c.payloadLen(9));
        assertTrue(c.endsSession[9]);
        assertNotSame(t.payloadOffset, c.payloadOffset);
        assertNotSame(t.endsSession, c.endsSession);

        t.append(9_999, 10_000, 1, 2, 0);
        t.markSessionEnd(0);
        assertEquals(10, c.count, "chunks appended to the live indexer do not appear in the snapshot");
        assertFalse(c.endsSession[0], "flags set later on the live table do not leak into the snapshot");
    }

    @Test
    void appendGrowsPastTheInitialCapacityAndKeepsEveryColumn() {
        final ChunkTable t = new ChunkTable();
        for (int i = 0; i < 100; i++) {
            t.append(i, i + 1, 2L * i, 2L * i + 1, i);
        }
        assertEquals(100, t.count);
        for (int i = 0; i < 100; i++) {
            assertEquals(i, t.payloadOffset[i]);
            assertEquals(1, t.payloadLen(i));
            assertEquals(2L * i, t.baseTicks[i]);
            assertEquals(2L * i + 1, t.endTicks[i]);
            assertEquals(i, t.openDepthAtStart[i]);
            assertFalse(t.endsSession[i]);
        }
    }
}
