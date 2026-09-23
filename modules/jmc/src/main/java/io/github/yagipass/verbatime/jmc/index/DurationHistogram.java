package io.github.yagipass.verbatime.jmc.index;

final class DurationHistogram {

    static final int SIZE = 1000 + 16 * 900;

    private static final long[] POW10 = new long[19];
    static {
        POW10[0] = 1;
        for (int i = 1; i < POW10.length; i++) {
            POW10[i] = POW10[i - 1] * 10;
        }
    }

    private DurationHistogram() {
    }

    static int bucketIndex(final long durNs) {
        if (durNs < 1000) {
            return (int) durNs;
        }
        int d = 4;
        while (d < 19 && durNs >= POW10[d]) {
            d++;
        }
        final int m = (int) (durNs / POW10[d - 3]);
        return 1000 + (d - 4) * 900 + (m - 100);
    }

    static long bucketValue(final int index) {
        if (index < 1000) {
            return index;
        }
        final int r = index - 1000;
        return (r % 900 + 100) * POW10[r / 900 + 1];
    }

    static long chooseThreshold(final long[] hist, final long total, final long budget) {
        if (total <= budget) {
            return 0;
        }
        long kept = 0;
        for (int i = SIZE - 1; i >= 0; i--) {
            kept += hist[i];
            if (kept >= budget) {
                return bucketValue(i);
            }
        }
        return 0;
    }
}
