package io.github.yagipass.verbatime.jmc.index;

import java.util.Arrays;

public final class Calls {

    private static final int INITIAL = 16;

    public final long tid;

    public int count;

    public long[] startNs;

    public long[] durNs;

    public long[] selfNs;

    public int[] methodId;

    public int[] depth;

    public boolean[] unclosed;

    public int[] exceptionId;

    public Calls(final long tid) {
        this(tid, INITIAL);
    }

    public Calls(final long tid, final int capacity) {
        this(tid, new long[capacity], new long[capacity], new long[capacity], new int[capacity], new int[capacity],
                new boolean[capacity], new int[capacity]);
    }

    private Calls(final long tid, final long[] startNs, final long[] durNs, final long[] selfNs, final int[] methodId,
            final int[] depth, final boolean[] unclosed, final int[] exceptionId) {
        this.tid = tid;
        this.startNs = startNs;
        this.durNs = durNs;
        this.selfNs = selfNs;
        this.methodId = methodId;
        this.depth = depth;
        this.unclosed = unclosed;
        this.exceptionId = exceptionId;
    }

    public void add(final long startNs, final long durNs, final long selfNs, final int methodId, final int depth,
            final boolean unclosed, final int exceptionId) {
        if (count == this.startNs.length) {
            final int cap = count * 2;
            this.startNs = Arrays.copyOf(this.startNs, cap);
            this.durNs = Arrays.copyOf(this.durNs, cap);
            this.selfNs = Arrays.copyOf(this.selfNs, cap);
            this.methodId = Arrays.copyOf(this.methodId, cap);
            this.depth = Arrays.copyOf(this.depth, cap);
            this.unclosed = Arrays.copyOf(this.unclosed, cap);
            this.exceptionId = Arrays.copyOf(this.exceptionId, cap);
        }
        this.startNs[count] = startNs;
        this.durNs[count] = durNs;
        this.selfNs[count] = selfNs;
        this.methodId[count] = methodId;
        this.depth[count] = depth;
        this.unclosed[count] = unclosed;
        this.exceptionId[count] = exceptionId;
        count++;
    }

    public void sortByStart() {
        final Integer[] idx = new Integer[count];
        for (int i = 0; i < count; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (x, y) -> {
            int c = Long.compare(startNs[x], startNs[y]);
            if (c == 0) {
                c = Integer.compare(depth[x], depth[y]);
            }
            return c != 0 ? c : Long.compare(durNs[x], durNs[y]);
        });
        final long[] s = new long[count];
        final long[] d = new long[count];
        final long[] sf = new long[count];
        final int[] mi = new int[count];
        final int[] dp = new int[count];
        final boolean[] un = new boolean[count];
        final int[] ex = new int[count];
        for (int i = 0; i < count; i++) {
            final int j = idx[i];
            s[i] = startNs[j];
            d[i] = durNs[j];
            sf[i] = selfNs[j];
            mi[i] = methodId[j];
            dp[i] = depth[j];
            un[i] = unclosed[j];
            ex[i] = exceptionId[j];
        }
        startNs = s;
        durNs = d;
        selfNs = sf;
        methodId = mi;
        depth = dp;
        unclosed = un;
        exceptionId = ex;
    }

    public int dropShorterThan(final long d) {
        int w = 0;
        for (int r = 0; r < count; r++) {
            if (unclosed[r] || durNs[r] >= d) {
                startNs[w] = startNs[r];
                durNs[w] = durNs[r];
                selfNs[w] = selfNs[r];
                methodId[w] = methodId[r];
                depth[w] = depth[r];
                unclosed[w] = unclosed[r];
                exceptionId[w] = exceptionId[r];
                w++;
            }
        }
        count = w;
        return w;
    }

    Calls copy() {
        final int cap = Math.max(count, INITIAL);
        final Calls c = new Calls(tid, Arrays.copyOf(startNs, cap), Arrays.copyOf(durNs, cap),
                Arrays.copyOf(selfNs, cap), Arrays.copyOf(methodId, cap), Arrays.copyOf(depth, cap),
                Arrays.copyOf(unclosed, cap), Arrays.copyOf(exceptionId, cap));
        c.count = count;
        return c;
    }
}
