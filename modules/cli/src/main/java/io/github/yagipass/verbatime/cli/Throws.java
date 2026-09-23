package io.github.yagipass.verbatime.cli;

import java.util.TreeMap;

final class Throws {

    interface Listener {

        void thrown(int exceptionId, int throwerId, long throwerOrdinal, long throwerDurTicks, int catcherId);
    }

    static final int NO_CATCHER = -1;

    long count;

    final TreeMap<Integer, Long> countByException = new TreeMap<>();

    private final Listener listener;

    private final CallStack stack = new CallStack();

    private boolean open;

    private int exceptionId;

    private int throwerId;

    private long throwerOrdinal;

    private long throwerDurTicks;

    private int lastDepth;

    Throws(final Listener listener) {
        this.listener = listener;
    }

    void enter(final int depth, final int methodId) {
        stack.push(depth, methodId);
        if (open) {
            close(stack.callerOf(lastDepth));
        }
    }

    void exit(final long ordinal, final int depth, final int methodId, final long durTicks, final int exceptionId) {
        if (exceptionId == SessionWalker.NO_EXCEPTION) {
            if (open) {
                close(methodId);
            }
            return;
        }
        if (open && this.exceptionId == exceptionId) {
            lastDepth = depth;
            return;
        }
        if (open) {
            close(methodId);
        }
        open = true;
        this.exceptionId = exceptionId;
        throwerId = methodId;
        throwerOrdinal = ordinal;
        throwerDurTicks = durTicks;
        lastDepth = depth;
        count++;
        countByException.merge(exceptionId, 1L, Long::sum);
    }

    void sessionEnded() {
        if (open) {
            close(stack.callerOf(lastDepth));
        }
    }

    private void close(final int catcherId) {
        open = false;
        if (listener != null) {
            listener.thrown(exceptionId, throwerId, throwerOrdinal, throwerDurTicks, catcherId);
        }
    }
}
