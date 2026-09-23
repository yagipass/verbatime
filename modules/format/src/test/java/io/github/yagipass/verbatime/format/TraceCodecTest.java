package io.github.yagipass.verbatime.format;

import static io.github.yagipass.verbatime.format.GoldenTrace.EPOCH_MS;
import static io.github.yagipass.verbatime.format.GoldenTrace.PAYLOAD;
import static io.github.yagipass.verbatime.format.GoldenTrace.UTC_OFFSET;
import static io.github.yagipass.verbatime.format.GoldenTrace.bytes;
import static io.github.yagipass.verbatime.format.GoldenTrace.golden;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.EventCursor.Event;

final class TraceCodecTest {

    @Test
    void theEncodersProduceTheGoldenBytesByteForByte() {
        final byte[] payload = new TraceBuilder.Payload(100).enter(100, 0).enter(105, 1).exit(108).exitThrow(110, 1).bytes();
        assertArrayEquals(bytes(PAYLOAD), payload, "EventEncoder encodes the four events as the hand-written payload");
        final byte[] trace = new TraceBuilder(EPOCH_MS, UTC_OFFSET).thread(7, "main").clazz(0, "a.B", "m()V", "n(I)V")
                .exception(1, "x.E").gc(300, 20, Vbtm.GC_ACTION_MINOR, "G1", "Alloc").chunk(7, 100, payload, false)
                .chunk(7, 200, new byte[0], true).end().bytes();
        assertArrayEquals(golden(), trace, "RecordEncoder encodes every record type as the hand-written trace");
    }

    @Test
    void theGoldenPayloadDecodesToItsFourEventsWithAbsoluteTicks() {
        final EventCursor c = new EventCursor();
        final byte[] p = bytes(PAYLOAD);
        c.reset(p, 0, p.length, 100);
        assertEquals(Event.ENTER, c.next());
        assertEquals(100, c.ticks());
        assertEquals(0, c.methodId());
        assertEquals(Event.ENTER, c.next());
        assertEquals(105, c.ticks());
        assertEquals(1, c.methodId());
        assertEquals(Event.EXIT, c.next());
        assertEquals(108, c.ticks());
        assertEquals(-1, c.exceptionId());
        assertEquals(Event.EXIT, c.next());
        assertEquals(110, c.ticks());
        assertEquals(1, c.exceptionId());
        assertEquals(Event.END, c.next());
    }
}
