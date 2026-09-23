package io.github.yagipass.verbatime.jmc.index;

import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.FrameStack;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

public final class ChunkWalker {

    private ChunkWalker() {
    }

    public interface Visitor {

        default boolean enter(final long startNs, final int methodId, final int sessionDepth, final int sp) {
            return true;
        }

        boolean exit(long startNs, long durNs, long childNs, int methodId, int sessionDepth, int sp, int exc);

        default void sessionEnd() {
        }
    }

    public static void walkRange(final TraceSnapshot data, final ThreadIndex m, final long loNs, final long hiNs,
            final Visitor ev) {
        final int c0 = firstChunkEndingAtOrAfter(m, loNs);
        final int c1 = lastChunkStartingAtOrBefore(m, c0, hiNs);
        if (c0 <= c1) {
            walk(data, m, c0, c1, ev);
        }
    }

    private static void walk(final TraceSnapshot data, final ThreadIndex m, final int c0, final int c1, final Visitor ev) {
        final ChunkCursor chunks = new ChunkCursor(data.buffer, m, c0, c1);
        try {
            final EventCursor cur = new EventCursor();
            final FrameStack stack = new FrameStack();
            int baseDepth = 0;
            while (chunks.next()) {
                baseDepth = Math.max(chunks.openDepthAtStart() - stack.depth(), 0);
                if (chunks.payloadLen() > 0) {
                    chunks.open(cur);
                    boolean more = true;
                    while (more) {
                        final EventCursor.Event e = cur.next();
                        if (e == EventCursor.Event.ENTER) {
                            stack.push(cur.ticks(), cur.methodId());
                            more = ev.enter(cur.ticks() * Vbtm.NANOS_PER_TICK, cur.methodId(),
                                    baseDepth + stack.depth() - 1, stack.depth() - 1);
                        } else if (e == EventCursor.Event.EXIT) {
                            if (stack.depth() == 0) {
                                baseDepth = Math.max(baseDepth - 1, 0);
                            } else {
                                stack.pop(cur.ticks());
                                more = ev.exit(stack.startNs(), stack.durNs(), stack.childNs(), stack.methodId(),
                                        baseDepth + stack.depth(), stack.depth(), cur.exceptionId());
                            }
                        } else {
                            break;
                        }
                    }
                    if (!more) {
                        return;
                    }
                }
                if (chunks.endsSession()) {
                    stack.clear();
                    ev.sessionEnd();
                }
            }
        } finally {
            chunks.release();
        }
    }

    static int firstChunkEndingAtOrAfter(final ThreadIndex m, final long loNs) {
        int lo = 0;
        int hi = m.chunks.count;
        while (lo < hi) {
            final int middle = (lo + hi) >>> 1;
            if (m.chunks.endTicks[middle] * Vbtm.NANOS_PER_TICK < loNs) {
                lo = middle + 1;
            } else {
                hi = middle;
            }
        }
        return lo;
    }

    private static int lastChunkStartingAtOrBefore(final ThreadIndex m, final int from, final long hiNs) {
        int best = -1;
        int lo = from;
        int hi = m.chunks.count;
        while (lo < hi) {
            final int middle = (lo + hi) >>> 1;
            if (m.chunks.baseTicks[middle] * Vbtm.NANOS_PER_TICK <= hiNs) {
                best = middle;
                lo = middle + 1;
            } else {
                hi = middle;
            }
        }
        return best;
    }

    public static long saturatingAdd(final long a, final long b) {
        final long s = a + b;
        return s < a ? Long.MAX_VALUE : s;
    }
}
