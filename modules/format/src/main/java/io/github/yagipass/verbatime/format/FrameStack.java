package io.github.yagipass.verbatime.format;

import java.util.Arrays;

public final class FrameStack {

    private static final int INITIAL_CAPACITY = 64;

    private long[] startTicks;

    private long[] childNs;

    private int[] methodIds;

    private int sp;

    private long poppedStartTicks;

    private long poppedChildNs;

    private int poppedMethodId;

    private long poppedEndTicks;

    public FrameStack() {
        this(new long[INITIAL_CAPACITY], new long[INITIAL_CAPACITY], new int[INITIAL_CAPACITY]);
    }

    private FrameStack(final long[] startTicks, final long[] childNs, final int[] methodIds) {
        this.startTicks = startTicks;
        this.childNs = childNs;
        this.methodIds = methodIds;
    }

    public int depth() {
        return sp;
    }

    public void push(final long startTicks, final int methodId) {
        if (sp == this.startTicks.length) {
            final int cap = sp * 2;
            this.startTicks = Arrays.copyOf(this.startTicks, cap);
            childNs = Arrays.copyOf(childNs, cap);
            methodIds = Arrays.copyOf(methodIds, cap);
        }
        this.startTicks[sp] = startTicks;
        childNs[sp] = 0;
        methodIds[sp] = methodId;
        sp++;
    }

    public void pop(final long endTicks) {
        sp--;
        poppedStartTicks = startTicks[sp];
        poppedChildNs = childNs[sp];
        poppedMethodId = methodIds[sp];
        poppedEndTicks = endTicks;
        if (sp > 0) {
            childNs[sp - 1] += (endTicks - poppedStartTicks) * Vbtm.NANOS_PER_TICK;
        }
    }

    public long startNs() {
        return poppedStartTicks * Vbtm.NANOS_PER_TICK;
    }

    public long durNs() {
        return (poppedEndTicks - poppedStartTicks) * Vbtm.NANOS_PER_TICK;
    }

    public long childNs() {
        return poppedChildNs;
    }

    public long selfNs() {
        return Math.max(durNs() - poppedChildNs, 0);
    }

    public int methodId() {
        return poppedMethodId;
    }

    public void clear() {
        sp = 0;
    }

    public FrameStack copy() {
        final int cap = Math.max(sp, INITIAL_CAPACITY);
        final FrameStack c = new FrameStack(Arrays.copyOf(startTicks, cap), Arrays.copyOf(childNs, cap),
                Arrays.copyOf(methodIds, cap));
        c.sp = sp;
        c.poppedStartTicks = poppedStartTicks;
        c.poppedChildNs = poppedChildNs;
        c.poppedMethodId = poppedMethodId;
        c.poppedEndTicks = poppedEndTicks;
        return c;
    }
}
