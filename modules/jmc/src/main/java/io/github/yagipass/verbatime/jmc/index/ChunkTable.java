package io.github.yagipass.verbatime.jmc.index;

import java.util.Arrays;

public final class ChunkTable {

    private static final int INITIAL = 8;

    public int count;

    long[] payloadOffset;

    long[] payloadEnd;

    public long[] baseTicks;

    long[] endTicks;

    int[] openDepthAtStart;

    boolean[] endsSession;

    ChunkTable() {
        this(INITIAL);
    }

    private ChunkTable(final int capacity) {
        payloadOffset = new long[capacity];
        payloadEnd = new long[capacity];
        baseTicks = new long[capacity];
        endTicks = new long[capacity];
        openDepthAtStart = new int[capacity];
        endsSession = new boolean[capacity];
    }

    void append(final long payloadOffset, final long payloadEnd, final long baseTicks, final long endTicks,
            final int openDepthAtStart) {
        final int i = count;
        if (i == this.payloadOffset.length) {
            final int cap = i * 2;
            this.payloadOffset = Arrays.copyOf(this.payloadOffset, cap);
            this.payloadEnd = Arrays.copyOf(this.payloadEnd, cap);
            this.baseTicks = Arrays.copyOf(this.baseTicks, cap);
            this.endTicks = Arrays.copyOf(this.endTicks, cap);
            this.openDepthAtStart = Arrays.copyOf(this.openDepthAtStart, cap);
            this.endsSession = Arrays.copyOf(this.endsSession, cap);
        }
        this.payloadOffset[i] = payloadOffset;
        this.payloadEnd[i] = payloadEnd;
        this.baseTicks[i] = baseTicks;
        this.endTicks[i] = endTicks;
        this.openDepthAtStart[i] = openDepthAtStart;
        this.endsSession[i] = false;
        count = i + 1;
    }

    void markSessionEnd(final int i) {
        endsSession[i] = true;
    }

    public long payloadLen(final int i) {
        return payloadEnd[i] - payloadOffset[i];
    }

    ChunkTable copy() {
        final ChunkTable c = new ChunkTable(Math.max(count, INITIAL));
        c.count = count;
        System.arraycopy(payloadOffset, 0, c.payloadOffset, 0, count);
        System.arraycopy(payloadEnd, 0, c.payloadEnd, 0, count);
        System.arraycopy(baseTicks, 0, c.baseTicks, 0, count);
        System.arraycopy(endTicks, 0, c.endTicks, 0, count);
        System.arraycopy(openDepthAtStart, 0, c.openDepthAtStart, 0, count);
        System.arraycopy(endsSession, 0, c.endsSession, 0, count);
        return c;
    }
}
