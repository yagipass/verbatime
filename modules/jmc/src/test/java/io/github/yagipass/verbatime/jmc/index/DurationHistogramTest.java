package io.github.yagipass.verbatime.jmc.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

final class DurationHistogramTest {

    private static long pyBucket(final long durNs) {
        if (durNs < 1000) {
            return durNs;
        }
        final int digits = Long.toString(durNs).length();
        long k = 1;
        for (int i = 0; i < digits - 3; i++) {
            k *= 10;
        }
        return durNs / k * k;
    }

    @Test
    void bucketIndexMatchesPythonBucketing() {
        for (long v = 0; v < 5000; v++) {
            assertEquals(pyBucket(v), DurationHistogram.bucketValue(DurationHistogram.bucketIndex(v)), "v=" + v);
        }
        final Random rng = new Random(7);
        for (int i = 0; i < 200_000; i++) {
            final long v = Math.floorMod(rng.nextLong(), 4_000_000_000_000L);
            assertEquals(pyBucket(v), DurationHistogram.bucketValue(DurationHistogram.bucketIndex(v)), "v=" + v);
        }
        for (final long v : new long[] { 999, 1000, 1001, 9_999, 10_000, 999_999_999_999L, 1_000_000_000_000L,
                Long.MAX_VALUE / 2 }) {
            assertEquals(pyBucket(v), DurationHistogram.bucketValue(DurationHistogram.bucketIndex(v)), "v=" + v);
        }
    }

    @Test
    void chooseThresholdMatchesPythonSemantics() {
        long[] hist = new long[DurationHistogram.SIZE];
        hist[DurationHistogram.bucketIndex(500)] = 10;
        assertEquals(0, DurationHistogram.chooseThreshold(hist, 10, 10));
        hist = new long[DurationHistogram.SIZE];
        hist[DurationHistogram.bucketIndex(100)] = 100;
        hist[DurationHistogram.bucketIndex(5000)] = 5;
        hist[DurationHistogram.bucketIndex(90_000)] = 3;
        assertEquals(5000, DurationHistogram.chooseThreshold(hist, 108, 7));
        assertEquals(90_000, DurationHistogram.chooseThreshold(hist, 108, 3));
        assertEquals(100, DurationHistogram.chooseThreshold(hist, 108, 50));
    }
}
