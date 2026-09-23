package io.github.yagipass.verbatime.jmc.export;

import java.util.Arrays;

final class OpenCalls {

    private static final int INITIAL = 256;

    long[] startTicks = new long[INITIAL];

    int[] methodId = new int[INITIAL];

    long[] childTicks = new long[INITIAL];

    long[] directChildren = new long[INITIAL];

    long[] descendants = new long[INITIAL];

    long[] thrownDescendants = new long[INITIAL];

    long[] belowFloorCalls = new long[INITIAL];

    long[] belowFloorDescendants = new long[INITIAL];

    long[] belowFloorThrown = new long[INITIAL];

    long[] belowFloorTicks = new long[INITIAL];

    long[] lineNo = new long[INITIAL];

    long[] durPatchOffset = new long[INITIAL];

    long[] selfPatchOffset = new long[INITIAL];

    private BelowFloorCounts[] belowFloorAt = new BelowFloorCounts[INITIAL];

    private int size;

    private int maxSize;

    int size() {
        return size;
    }

    int maxSize() {
        return maxSize;
    }

    int push(final int method, final long ticks) {
        if (size == startTicks.length) {
            allocate();
        }
        final int k = size;
        startTicks[k] = ticks;
        methodId[k] = method;
        childTicks[k] = 0;
        directChildren[k] = 0;
        descendants[k] = 0;
        thrownDescendants[k] = 0;
        belowFloorCalls[k] = 0;
        belowFloorDescendants[k] = 0;
        belowFloorThrown[k] = 0;
        belowFloorTicks[k] = 0;
        size++;
        if (size > maxSize) {
            maxSize = size;
        }
        return k;
    }

    int pop() {
        return --size;
    }

    BelowFloorCounts belowFloorAt(final int k) {
        BelowFloorCounts t = belowFloorAt[k];
        if (t == null) {
            t = belowFloorAt[k] = new BelowFloorCounts();
        }
        return t;
    }

    BelowFloorCounts belowFloorOrNull(final int k) {
        return belowFloorAt[k];
    }

    private void allocate() {
        final int n = startTicks.length * 2;
        startTicks = Arrays.copyOf(startTicks, n);
        methodId = Arrays.copyOf(methodId, n);
        childTicks = Arrays.copyOf(childTicks, n);
        directChildren = Arrays.copyOf(directChildren, n);
        descendants = Arrays.copyOf(descendants, n);
        thrownDescendants = Arrays.copyOf(thrownDescendants, n);
        belowFloorCalls = Arrays.copyOf(belowFloorCalls, n);
        belowFloorDescendants = Arrays.copyOf(belowFloorDescendants, n);
        belowFloorThrown = Arrays.copyOf(belowFloorThrown, n);
        belowFloorTicks = Arrays.copyOf(belowFloorTicks, n);
        lineNo = Arrays.copyOf(lineNo, n);
        durPatchOffset = Arrays.copyOf(durPatchOffset, n);
        selfPatchOffset = Arrays.copyOf(selfPatchOffset, n);
        belowFloorAt = Arrays.copyOf(belowFloorAt, n);
    }
}
