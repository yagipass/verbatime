package io.github.yagipass.verbatime.cli;

import java.util.Arrays;

abstract class SubtreeVisitor implements SessionWalker.Visitor {

    private final SessionWalker walker;

    private final long rootOrdinal;

    private final CallStack stack = new CallStack();

    private long[] ordinals = new long[64];

    private boolean inside;

    private int baseDepth;

    boolean found;

    long startTicks;

    long durTicks;

    final Throws thrown = new Throws(null);

    int[] pathMethodIds = new int[0];

    long[] pathOrdinals = new long[0];

    SubtreeVisitor(final SessionWalker walker, final long rootOrdinal) {
        this.walker = walker;
        this.rootOrdinal = rootOrdinal;
        this.inside = rootOrdinal < 0;
        this.found = rootOrdinal < 0;
    }

    int baseDepth() {
        return baseDepth;
    }

    @Override
    public final void enter(final long ordinal, final int depth, final int methodId, final long startTicks) {
        thrown.enter(depth, methodId);
        if (!inside) {
            stack.push(depth, methodId);
            if (depth == ordinals.length) {
                ordinals = Arrays.copyOf(ordinals, depth * 2);
            }
            ordinals[depth] = ordinal;
            if (ordinal != rootOrdinal) {
                return;
            }
            inside = true;
            found = true;
            baseDepth = depth;
            pathMethodIds = stack.methodIds(depth + 1);
            pathOrdinals = Arrays.copyOf(ordinals, depth + 1);
        }
        onEnter(ordinal, depth - baseDepth, depth, methodId, startTicks);
    }

    @Override
    public final void exit(final long ordinal, final int depth, final int methodId, final long startTicks,
            final long durTicks, final long selfTicks, final int exceptionId, final boolean unclosed) {
        if (!inside) {
            return;
        }
        thrown.exit(ordinal, depth, methodId, durTicks, exceptionId);
        onExit(ordinal, depth - baseDepth, depth, methodId, startTicks, durTicks, selfTicks, exceptionId, unclosed);
        if (ordinal == rootOrdinal) {
            this.startTicks = startTicks;
            this.durTicks = durTicks;
            inside = false;
            walker.stop();
        }
    }

    abstract void onEnter(long ordinal, int level, int depth, int methodId, long startTicks);

    abstract void onExit(long ordinal, int level, int depth, int methodId, long startTicks, long durTicks,
            long selfTicks, int exceptionId, boolean unclosed);
}
