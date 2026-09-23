package io.github.yagipass.verbatime.jmc.export;

import java.util.Arrays;

final class OutlineHeap {

    private final long[] dur;

    private final long[] seq;

    private final long[] line;

    private final long[] start;

    private final long[] self;

    private final int[] depth;

    private final int[] methodId;

    private final long[] children;

    private final long[] subLines;

    private final byte[] flags;

    private final int[] excNo;

    private int n;

    OutlineHeap(final int capacity) {
        dur = new long[capacity];
        seq = new long[capacity];
        line = new long[capacity];
        start = new long[capacity];
        self = new long[capacity];
        depth = new int[capacity];
        methodId = new int[capacity];
        children = new long[capacity];
        subLines = new long[capacity];
        flags = new byte[capacity];
        excNo = new int[capacity];
    }

    void offer(final long durTicks, final long sequence, final long lineNo, final long startTicks,
            final long selfTicks, final int frameDepth, final int method, final long directChildren,
            final long subtreeLines, final byte flagBits, final int exceptionNo) {
        final int i;
        if (n < dur.length) {
            i = n++;
        } else if (durTicks > dur[0] || (durTicks == dur[0] && sequence > seq[0])) {
            i = 0;
        } else {
            return;
        }
        dur[i] = durTicks;
        seq[i] = sequence;
        line[i] = lineNo;
        start[i] = startTicks;
        self[i] = selfTicks;
        depth[i] = frameDepth;
        methodId[i] = method;
        children[i] = directChildren;
        subLines[i] = subtreeLines;
        flags[i] = flagBits;
        excNo[i] = exceptionNo;
        if (i == 0) {
            siftDown(0);
        } else {
            siftUp(i);
        }
    }

    int size() {
        return n;
    }

    long thresholdTicks() {
        return n > 0 ? dur[0] : 0;
    }

    int[] byLine() {
        final Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Long.compare(line[a], line[b]));
        final int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = idx[i];
        }
        return out;
    }

    long dur(final int i) {
        return dur[i];
    }

    long line(final int i) {
        return line[i];
    }

    long start(final int i) {
        return start[i];
    }

    long self(final int i) {
        return self[i];
    }

    int depth(final int i) {
        return depth[i];
    }

    int methodId(final int i) {
        return methodId[i];
    }

    long children(final int i) {
        return children[i];
    }

    long subLines(final int i) {
        return subLines[i];
    }

    boolean thrown(final int i) {
        return (flags[i] & 1) != 0;
    }

    boolean unclosed(final int i) {
        return (flags[i] & 2) != 0;
    }

    int excNo(final int i) {
        return excNo[i];
    }

    private boolean less(final int a, final int b) {
        return dur[a] < dur[b] || (dur[a] == dur[b] && seq[a] < seq[b]);
    }

    private void siftUp(int i) {
        while (i > 0) {
            final int parent = (i - 1) >>> 1;
            if (!less(i, parent)) {
                return;
            }
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        while (true) {
            final int l = 2 * i + 1;
            if (l >= n) {
                return;
            }
            int m = l;
            if (l + 1 < n && less(l + 1, l)) {
                m = l + 1;
            }
            if (!less(m, i)) {
                return;
            }
            swap(i, m);
            i = m;
        }
    }

    private void swap(final int a, final int b) {
        swap(dur, a, b);
        swap(seq, a, b);
        swap(line, a, b);
        swap(start, a, b);
        swap(self, a, b);
        swap(children, a, b);
        swap(subLines, a, b);
        swap(depth, a, b);
        swap(methodId, a, b);
        swap(excNo, a, b);
        final byte f = flags[a];
        flags[a] = flags[b];
        flags[b] = f;
    }

    private static void swap(final long[] v, final int a, final int b) {
        final long t = v[a];
        v[a] = v[b];
        v[b] = t;
    }

    private static void swap(final int[] v, final int a, final int b) {
        final int t = v[a];
        v[a] = v[b];
        v[b] = t;
    }
}
