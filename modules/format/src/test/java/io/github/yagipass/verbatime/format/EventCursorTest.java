package io.github.yagipass.verbatime.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.format.EventCursor.Event;
import io.github.yagipass.verbatime.format.EventCursor.Fault;

final class EventCursorTest {

    private static final long BASE = 1_000;

    private static byte[] threeEvents() {
        return new TraceBuilder.Payload(BASE).enter(BASE, 5).enter(BASE + 300, 7).exitThrow(BASE + 1_000, 3).bytes();
    }

    private static void varint(final ByteArrayOutputStream o, final long v) {
        final byte[] b = new byte[Varint.MAX_BYTES];
        o.write(b, 0, Varint.put(b, 0, v));
    }

    private static EventCursor over(final byte[] b) {
        final EventCursor c = new EventCursor();
        c.reset(b, 0, b.length, BASE);
        return c;
    }

    private static List<Event> drain(final EventCursor c) {
        final List<Event> seen = new ArrayList<>();
        while (true) {
            final Event e = c.next();
            seen.add(e);
            if (e != Event.ENTER && e != Event.EXIT) {
                return seen;
            }
        }
    }

    @Test
    void aCleanPayloadYieldsEveryEventWithAbsoluteTicksAndThenEnd() {
        final byte[] b = new TraceBuilder.Payload(BASE).enter(BASE, 5).enter(BASE + 30, 7).exitThrow(BASE + 50, 3)
                .exit(BASE + 100).bytes();
        final EventCursor c = over(b);

        assertEquals(Event.ENTER, c.next());
        assertEquals(BASE, c.ticks());
        assertEquals(5, c.methodId());
        assertEquals(0, c.eventIndex());

        assertEquals(Event.ENTER, c.next());
        assertEquals(BASE + 30, c.ticks());
        assertEquals(7, c.methodId());

        assertEquals(Event.EXIT, c.next());
        assertEquals(BASE + 50, c.ticks());
        assertEquals(3, c.exceptionId());

        assertEquals(Event.EXIT, c.next());
        assertEquals(BASE + 100, c.ticks());
        assertEquals(-1, c.exceptionId());

        assertEquals(Event.END, c.next());
        assertEquals(b.length, c.stopIndex());
        assertEquals(4, c.decodedEvents());
        assertEquals(Event.END, c.next(), "a terminal event is sticky");
    }

    @Test
    void theFirstEventNeverAddsItsDeltaBecauseTheChunkHeaderCarriesTheBase() {
        final byte[] b = { (byte) (5 << 1), 1 };
        final EventCursor c = over(b);
        assertEquals(Event.ENTER, c.next());
        assertEquals(BASE, c.ticks());
        assertEquals(1, c.methodId());
    }

    @Test
    void everyTruncatedPrefixStopsAtTheStartOfTheCutEventAndCountsOnlyWholeOnes() {
        final byte[] whole = threeEvents();
        final int[] eventEnd = { 2, 5, 8 };
        assertEquals(8, whole.length, "the fixture has known event boundaries: 2 + 3 + 3 bytes");
        for (int k = 0; k <= whole.length; k++) {
            final EventCursor c = new EventCursor();
            c.reset(whole, 0, k, BASE);
            final List<Event> seen = drain(c);
            final Event terminal = seen.get(seen.size() - 1);
            int complete = 0;
            int cutStart = k;
            for (int i = 0; i < eventEnd.length; i++) {
                if (eventEnd[i] <= k) {
                    complete++;
                } else {
                    cutStart = i == 0 ? 0 : eventEnd[i - 1];
                    break;
                }
            }
            final String at = "prefix of " + k + " bytes";
            final boolean onBoundary = cutStart == k;
            assertEquals(onBoundary ? Event.END : Event.INCOMPLETE, terminal,
                    at + ": a cut on an event boundary is indistinguishable from a clean end");
            assertEquals(complete, c.decodedEvents(), at);
            assertEquals(cutStart, c.stopIndex(), at);
            assertEquals(complete, seen.size() - 1, at);
        }
    }

    @Test
    void anEnterCutAfterItsHeaderHasAlreadyAdvancedTheTicksLikeTheReferenceDecoder() {
        final byte[] whole = threeEvents();
        final EventCursor c = new EventCursor();
        c.reset(whole, 0, 4, BASE);
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.INCOMPLETE, c.next());
        assertEquals(BASE + 300, c.ticks(), "the header delta of the cut enter is applied before its method id");
        assertEquals(2, c.stopIndex());
        assertEquals(1, c.decodedEvents());
    }

    @Test
    void anExitCutBeforeItsExceptionIdHasNotAdvancedTheTicks() {
        final byte[] whole = threeEvents();
        final EventCursor c = new EventCursor();
        c.reset(whole, 0, 7, BASE);
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.INCOMPLETE, c.next());
        assertEquals(BASE + 300, c.ticks(), "the exit delta is applied only after its exception id was read");
        assertEquals(5, c.stopIndex());
        assertEquals(2, c.decodedEvents());
    }

    @Test
    void tenContinuationBytesAreCorruptWhileNineAtTheEndAreMerelyIncomplete() {
        final byte[] tooLong = new byte[10];
        Arrays.fill(tooLong, (byte) 0x80);
        final EventCursor c = over(tooLong);
        assertEquals(Event.CORRUPT, c.next());
        assertSame(Fault.VARINT_TOO_LONG, c.fault());
        assertEquals(0, c.stopIndex());

        final byte[] cut = new byte[9];
        Arrays.fill(cut, (byte) 0x80);
        final EventCursor d = over(cut);
        assertEquals(Event.INCOMPLETE, d.next());
    }

    @Test
    void aMethodIdAtTheFormatLimitIsCorruptAndStopsAtThatEventNotAtTheChunkStart() {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new TraceBuilder.Payload(BASE).enter(BASE, 1).bytes());
        final int second = o.size();
        o.write(0);
        varint(o, Vbtm.METHOD_ID_LIMIT);
        final EventCursor c = over(o.toByteArray());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.CORRUPT, c.next());
        assertSame(Fault.METHOD_ID_LIMIT, c.fault());
        assertEquals(Vbtm.METHOD_ID_LIMIT, c.faultValue());
        assertEquals(second, c.stopIndex());
        assertEquals(second, c.eventIndex());
        assertEquals(1, c.decodedEvents());

        final byte[] ok = new TraceBuilder.Payload(BASE).enter(BASE, Vbtm.METHOD_ID_LIMIT - 1).bytes();
        assertEquals(Event.ENTER, over(ok).next());
    }

    @Test
    void anExceptionIdAtTheFormatLimitIsCorruptInsteadOfBeingClampedToUnknown() {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new TraceBuilder.Payload(BASE).enter(BASE, 1).bytes());
        final int second = o.size();
        o.write(2 | 1);
        varint(o, Vbtm.EXCEPTION_ID_LIMIT);
        final EventCursor c = over(o.toByteArray());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.CORRUPT, c.next());
        assertSame(Fault.EXCEPTION_ID_LIMIT, c.fault());
        assertEquals(Vbtm.EXCEPTION_ID_LIMIT, c.faultValue());
        assertEquals(second, c.stopIndex());
        assertEquals(Event.CORRUPT, c.next(), "a terminal event is sticky");
    }

    @Test
    void aNegativeMethodIdIsCorruptInsteadOfAnEnterThatConsumersWouldIndexArraysWith() {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new TraceBuilder.Payload(BASE).enter(BASE, 1).bytes());
        final int second = o.size();
        o.write(0);
        varint(o, -1);
        final EventCursor c = over(o.toByteArray());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.CORRUPT, c.next(), "a ten-byte varint with bit 63 set must not pass the upper-bound check");
        assertSame(Fault.METHOD_ID_LIMIT, c.fault());
        assertEquals(-1, c.faultValue());
        assertEquals(second, c.stopIndex());
        assertEquals(1, c.decodedEvents());
    }

    @Test
    void aNegativeExceptionIdIsCorruptInsteadOfPassingAsANormalReturn() {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new TraceBuilder.Payload(BASE).enter(BASE, 1).bytes());
        final int second = o.size();
        o.write(2 | 1);
        varint(o, -1);
        final EventCursor c = over(o.toByteArray());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.CORRUPT, c.next(),
                "exception() == -1 means no exception, so a thrown exit would read as a normal return");
        assertSame(Fault.EXCEPTION_ID_LIMIT, c.fault());
        assertEquals(-1, c.faultValue());
        assertEquals(second, c.stopIndex());
    }

    @Test
    void thePayloadWriterRefusesANegativeMethodIdThatTheCursorWouldRejectAsCorrupt() {
        final TraceBuilder.Payload p = new TraceBuilder.Payload(BASE);
        assertThrows(IllegalArgumentException.class, () -> p.enter(BASE, -1));
    }

    @Test
    void anEnterDeltaThatPushesTheClockPastTheFormatLimitIsCorruptWithoutAdvancingTheTicks() {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new TraceBuilder.Payload(BASE).enter(BASE, 1).bytes());
        final int second = o.size();
        final long delta = Vbtm.MAX_TICKS - BASE + 1;
        varint(o, delta << 1);
        varint(o, 2);
        final EventCursor c = over(o.toByteArray());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.CORRUPT, c.next(),
                "ticks past MAX_TICKS overflow every consumer's ticks * NANOS_PER_TICK into negative nanoseconds");
        assertSame(Fault.TICKS_LIMIT, c.fault());
        assertEquals(delta, c.faultValue());
        assertEquals(BASE, c.ticks(), "a rejected delta is not applied");
        assertEquals(second, c.stopIndex());
        assertEquals(second, c.eventIndex());
        assertEquals(1, c.decodedEvents());
    }

    @Test
    void anExitDeltaThatPushesTheClockPastTheFormatLimitIsCorruptEvenAfterItsExceptionIdWasRead() {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new TraceBuilder.Payload(BASE).enter(BASE, 1).bytes());
        final int second = o.size();
        final long delta = Vbtm.MAX_TICKS - BASE + 1;
        varint(o, (delta << 2) | 2 | 1);
        varint(o, 1);
        final EventCursor c = over(o.toByteArray());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.CORRUPT, c.next());
        assertSame(Fault.TICKS_LIMIT, c.fault());
        assertEquals(delta, c.faultValue());
        assertEquals(BASE, c.ticks());
        assertEquals(second, c.stopIndex(), "the stop is the event start, not the byte after the exception id");
        assertEquals(1, c.decodedEvents());
    }

    @Test
    void aTenByteHeaderWithBitSixtyThreeSetIsATicksLimitFaultNotAHugeForwardJump() {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(new TraceBuilder.Payload(BASE).enter(BASE, 1).bytes());
        final int second = o.size();
        varint(o, -1L << 1);
        varint(o, 2);
        final EventCursor c = over(o.toByteArray());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.CORRUPT, c.next(), "h >>> 1 of a negative header is 2^62, far past MAX_TICKS");
        assertSame(Fault.TICKS_LIMIT, c.fault());
        assertEquals((-1L << 1) >>> 1, c.faultValue());
        assertEquals(second, c.stopIndex());
    }

    @Test
    void aDeltaThatLandsExactlyOnTheFormatLimitIsStillAnEvent() {
        final byte[] b = new TraceBuilder.Payload(BASE).enter(BASE, 1).enter(Vbtm.MAX_TICKS, 2).exit(Vbtm.MAX_TICKS)
                .bytes();
        final EventCursor c = over(b);
        assertEquals(Event.ENTER, c.next());
        assertEquals(Event.ENTER, c.next());
        assertEquals(Vbtm.MAX_TICKS, c.ticks(), "MAX_TICKS itself still multiplies into a positive long");
        assertEquals(Event.EXIT, c.next());
        assertEquals(Vbtm.MAX_TICKS, c.ticks());
        assertEquals(Event.END, c.next());
    }

    @Test
    void thePayloadWriterRefusesTicksOutsideTheRangeTheCursorAccepts() {
        final TraceBuilder.Payload p = new TraceBuilder.Payload(BASE);
        assertThrows(IllegalArgumentException.class, () -> p.enter(Vbtm.MAX_TICKS + 1, 1));
        assertThrows(IllegalArgumentException.class, () -> p.enter(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> p.exit(Vbtm.MAX_TICKS + 1));
        assertThrows(IllegalArgumentException.class, () -> p.exitThrow(Vbtm.MAX_TICKS + 1, 1));
    }

    @Test
    void resetReusesTheCursorWithoutLeakingThePreviousPayload() {
        final EventCursor c = over(threeEvents());
        drain(c);
        final byte[] b = new TraceBuilder.Payload(7).enter(7, 9).bytes();
        c.reset(b, 0, b.length, 7);
        assertEquals(Event.ENTER, c.next());
        assertEquals(7, c.ticks());
        assertEquals(9, c.methodId());
        assertEquals(1, c.decodedEvents());
        assertEquals(Event.END, c.next());
    }
}
