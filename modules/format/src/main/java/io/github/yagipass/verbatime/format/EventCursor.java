package io.github.yagipass.verbatime.format;

public final class EventCursor {

    public enum Event {
        ENTER, EXIT, END, INCOMPLETE, CORRUPT
    }

    public enum Fault {
        VARINT_TOO_LONG, METHOD_ID_LIMIT, EXCEPTION_ID_LIMIT, TICKS_LIMIT
    }

    private static final int OK = 0;

    private static final int OUT_OF_BYTES = 1;

    private static final int TOO_LONG = 2;

    private byte[] bytes = new byte[0];

    private int limit;

    private int pos;

    private long ticks;

    private int varintStatus;

    private int methodId;

    private int exceptionId;

    private int eventIndex;

    private int stopIndex;

    private int decodedEvents;

    private Event terminal;

    private Fault fault;

    private long faultValue;

    public void reset(final byte[] bytes, final int off, final int len, final long baseTicks) {
        this.bytes = bytes;
        this.pos = off;
        this.limit = off + len;
        this.ticks = baseTicks;
        this.varintStatus = OK;
        this.methodId = -1;
        this.exceptionId = -1;
        this.eventIndex = off;
        this.stopIndex = off;
        this.decodedEvents = 0;
        this.terminal = null;
        this.fault = null;
        this.faultValue = 0;
    }

    public Event next() {
        if (terminal != null) {
            return terminal;
        }
        if (pos >= limit) {
            eventIndex = limit;
            return stop(Event.END);
        }
        eventIndex = pos;
        final long h = varint();
        if (varintStatus != OK) {
            return fail();
        }
        if ((h & EventEncoder.EXIT_BIT) == 0) {
            final long delta = h >>> 1;
            if (decodedEvents > 0) {
                if (delta > Vbtm.MAX_TICKS - ticks) {
                    return corrupt(Fault.TICKS_LIMIT, delta);
                }
                ticks += delta;
            }
            final long id = varint();
            if (varintStatus != OK) {
                return fail();
            }
            if (id < 0 || id >= Vbtm.METHOD_ID_LIMIT) {
                return corrupt(Fault.METHOD_ID_LIMIT, id);
            }
            methodId = (int) id;
            decodedEvents++;
            return Event.ENTER;
        }
        int exc = -1;
        if ((h & EventEncoder.THROW_BIT) != 0) {
            final long e = varint();
            if (varintStatus != OK) {
                return fail();
            }
            if (e < 0 || e >= Vbtm.EXCEPTION_ID_LIMIT) {
                return corrupt(Fault.EXCEPTION_ID_LIMIT, e);
            }
            exc = (int) e;
        }
        final long delta = h >>> 2;
        if (decodedEvents > 0) {
            if (delta > Vbtm.MAX_TICKS - ticks) {
                return corrupt(Fault.TICKS_LIMIT, delta);
            }
            ticks += delta;
        }
        exceptionId = exc;
        decodedEvents++;
        return Event.EXIT;
    }

    public long ticks() {
        return ticks;
    }

    public int methodId() {
        return methodId;
    }

    public int exceptionId() {
        return exceptionId;
    }

    public int eventIndex() {
        return eventIndex;
    }

    public int stopIndex() {
        return stopIndex;
    }

    public int decodedEvents() {
        return decodedEvents;
    }

    public Fault fault() {
        return fault;
    }

    public long faultValue() {
        return faultValue;
    }

    private Event fail() {
        return varintStatus == OUT_OF_BYTES ? stop(Event.INCOMPLETE) : corrupt(Fault.VARINT_TOO_LONG, 0);
    }

    private Event stop(final Event e) {
        stopIndex = eventIndex;
        terminal = e;
        return e;
    }

    private Event corrupt(final Fault f, final long value) {
        fault = f;
        faultValue = value;
        return stop(Event.CORRUPT);
    }

    private long varint() {
        long r = 0;
        int shift = 0;
        while (true) {
            if (pos >= limit) {
                varintStatus = OUT_OF_BYTES;
                return 0;
            }
            final int b = bytes[pos++] & 0xFF;
            r |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return r;
            }
            shift += 7;
            if (shift > 63) {
                varintStatus = TOO_LONG;
                return 0;
            }
        }
    }
}
