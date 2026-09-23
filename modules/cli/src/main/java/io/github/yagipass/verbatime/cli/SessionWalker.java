package io.github.yagipass.verbatime.cli;

import java.util.Arrays;

import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.FrameStack;
import io.github.yagipass.verbatime.format.Vbtm;

final class SessionWalker {

    interface Visitor {

        void enter(long ordinal, int depth, int methodId, long startTicks);

        void exit(long ordinal, int depth, int methodId, long startTicks, long durTicks, long selfTicks,
                int exceptionId, boolean unclosed);
    }

    static final int NO_EXCEPTION = -1;

    private final TraceFile file;

    private final EventCursor cursor = new EventCursor();

    private final FrameStack stack = new FrameStack();

    private long[] ordinals = new long[64];

    private byte[] scratch = new byte[0];

    private boolean stopped;

    private boolean halted;

    long startTicks;

    long endTicks;

    SessionWalker(final TraceFile file) {
        this.file = file;
    }

    void stop() {
        stopped = true;
        halted = true;
    }

    void walk(final TraceFile.Session s, final Visitor v) {
        stopped = false;
        halted = false;
        stack.clear();
        long ordinal = 0;
        long last = -1;
        startTicks = s.chunks.isEmpty() ? 0 : s.chunks.get(0).baseTicks();
        for (final TraceFile.Chunk c : s.chunks) {
            if (scratch.length < c.len()) {
                scratch = new byte[c.len()];
            }
            file.data.copy(c.offset(), scratch, c.len());
            final long base = Math.max(c.baseTicks(), last);
            if (last < 0) {
                startTicks = base;
            }
            cursor.reset(scratch, 0, c.len(), base);
            while (!stopped) {
                final EventCursor.Event e = cursor.next();
                if (e == EventCursor.Event.ENTER) {
                    last = cursor.ticks();
                    final int depth = stack.depth();
                    if (depth == ordinals.length) {
                        ordinals = Arrays.copyOf(ordinals, depth * 2);
                    }
                    ordinals[depth] = ordinal;
                    stack.push(last, cursor.methodId());
                    v.enter(ordinal, depth, cursor.methodId(), last);
                    ordinal++;
                } else if (e == EventCursor.Event.EXIT) {
                    last = cursor.ticks();
                    if (stack.depth() == 0) {
                        file.markCorrupt(c.offset() + cursor.eventIndex(),
                                "exit with no open frame in session " + s.number);
                        stopped = true;
                        break;
                    }
                    stack.pop(last);
                    exit(v, cursor.exceptionId(), false);
                } else {
                    if (e == EventCursor.Event.CORRUPT) {
                        file.markCorrupt(c.offset() + cursor.eventIndex(), fault(cursor.fault(), cursor.faultValue()));
                        stopped = true;
                    } else if (e == EventCursor.Event.INCOMPLETE && file.status == TraceFile.Status.COMPLETE) {
                        file.markCorrupt(c.offset() + cursor.eventIndex(), "chunk payload ends mid-event");
                        stopped = true;
                    }
                    if (cursor.decodedEvents() > 0) {
                        last = cursor.ticks();
                    }
                    break;
                }
            }
            if (stopped) {
                break;
            }
        }
        endTicks = Math.max(last, startTicks);
        if (halted) {
            return;
        }
        while (stack.depth() > 0) {
            stack.pop(endTicks);
            exit(v, NO_EXCEPTION, true);
        }
    }

    private void exit(final Visitor v, final int exceptionId, final boolean unclosed) {
        final int depth = stack.depth();
        v.exit(ordinals[depth], depth, stack.methodId(), stack.startNs() / Vbtm.NANOS_PER_TICK,
                stack.durNs() / Vbtm.NANOS_PER_TICK, stack.selfNs() / Vbtm.NANOS_PER_TICK, exceptionId, unclosed);
    }

    private static String fault(final EventCursor.Fault fault, final long value) {
        return switch (fault) {
            case VARINT_TOO_LONG -> "varint too long";
            case METHOD_ID_LIMIT -> "method id " + value + " is outside the 2^22 format range";
            case EXCEPTION_ID_LIMIT -> "exception id " + value + " is outside the 2^22 format range";
            case TICKS_LIMIT -> "tick delta " + value + " pushes the clock past the format limit";
        };
    }
}
