package io.github.yagipass.verbatime.jmc.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.yagipass.verbatime.jmc.index.Calls;
import io.github.yagipass.verbatime.jmc.index.RandomTraces;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor.Window;

final class WindowExtractorTest {

    @Test
    void windowsMatchFullDecodeAcrossSeedsBudgetsAndZooms() throws IOException {
        for (long seed = 1; seed <= 8; seed++) {
            final byte[] bytes = RandomTraces.random(seed);
            final ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
            long minNs = Long.MAX_VALUE;
            long maxNs = Long.MIN_VALUE;
            for (final ReferenceDecoder.Call f : ref.calls) {
                minNs = Math.min(minNs, f.startNs());
                maxNs = Math.max(maxNs, f.startNs() + f.durNs());
            }
            final long span = Math.max(maxNs - minNs, 1);
            final Random rng = new Random(seed * 31);
            for (final int budget : new int[] { 1 << 30, 4000, 48 }) {
                final TraceSnapshot data = TestTraces.index(bytes, budget);
                final List<long[]> windows = new ArrayList<>();
                windows.add(new long[] { minNs, maxNs });
                windows.add(new long[] { minNs - span / 10, minNs + span / 3 });
                windows.add(new long[] { maxNs - span / 3, maxNs + span / 10 });
                windows.add(new long[] { minNs + span / 3, minNs + span / 2 });
                for (int i = 0; i < 12; i++) {
                    final long a = minNs + (long) (rng.nextDouble() * span);
                    final long b = a + Math.max((long) (rng.nextDouble() * span / 4), 200);
                    windows.add(new long[] { a, b });
                }
                for (final TraceSnapshot.ThreadIndex m : data.threads) {
                    for (int c = 0; c < m.chunks.count && windows.size() < 40; c += 3) {
                        final long t = m.chunks.baseTicks[c] * 100;
                        windows.add(new long[] { t - 700, t + 900 });
                    }
                }
                for (final long[] wdw : windows) {
                    for (final int px : new int[] { 60, 1000, 2_000_000 }) {
                        checkWindow(data, ref, wdw[0], wdw[1], px, "seed " + seed + " budget " + budget + " window ["
                                + wdw[0] + "," + wdw[1] + "] px " + px);
                    }
                }
            }
        }
    }

    private static void checkWindow(final TraceSnapshot data, final ReferenceDecoder.Result ref, final long t0,
            final long t1, final int px, final String ctx) {
        final long thr = Math.max((long) ((t1 - t0) / (double) px * WindowExtractor.PIXEL_FRACTION), 100);
        final Window res = WindowExtractor.extract(data, t0, t1, px, Integer.MAX_VALUE);
        assertEquals(thr, res.windowMinDurNs, ctx + " thr");

        final Map<Long, List<ReferenceDecoder.Call>> expected = TestTraces.byTid(ref.calls.stream()
                .filter(f -> f.startNs() <= t1 && f.startNs() + f.durNs() >= t0 && (f.unclosed() || f.durNs() >= thr))
                .toList());
        final Map<Long, Calls> actual = new java.util.HashMap<>();
        for (final Calls tf : res.callsByThread) {
            actual.put(tf.tid, tf);
        }
        assertEquals(expected.keySet(), actual.keySet(), ctx + " tids");
        for (final Map.Entry<Long, List<ReferenceDecoder.Call>> e : expected.entrySet()) {
            final List<ReferenceDecoder.Call> exp = e.getValue();
            final Calls tf = actual.get(e.getKey());
            assertEquals(exp.size(), tf.count, ctx + " tid " + e.getKey() + " frame count");
            for (int i = 0; i < exp.size(); i++) {
                final ReferenceDecoder.Call f = exp.get(i);
                final String c = ctx + " tid " + e.getKey() + " frame " + i;
                assertEquals(f.startNs(), tf.startNs[i], c + " start");
                assertEquals(f.durNs(), tf.durNs[i], c + " dur");
                assertEquals(f.methodId(), tf.methodId[i], c + " method");
                assertEquals(f.depth(), tf.depth[i], c + " depth");
                assertEquals(f.selfNs(), tf.selfNs[i], c + " self");
                assertEquals(f.unclosed(), tf.unclosed[i], c + " unclosed");
                assertEquals(f.exceptionId(), tf.exceptionId[i], c + " exc");
            }
        }
    }

    @Test
    void frameBudgetRaisesThresholdConsistently() throws IOException {
        for (long seed = 1; seed <= 5; seed++) {
            final byte[] bytes = RandomTraces.random(seed);
            final ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
            final TraceSnapshot data = TestTraces.index(bytes, 1 << 30);
            final long t0 = data.minNs;
            final long t1 = data.maxNs;
            final int budget = 40;
            final Window res = WindowExtractor.extract(data, t0, t1, 2_000_000, budget);
            final long unclosedTotal = ref.calls.stream().filter(ReferenceDecoder.Call::unclosed).count();
            assertTrue(res.totalCalls <= Math.max(budget, unclosedTotal),
                    "seed " + seed + ": " + res.totalCalls + " frames for budget " + budget);
            final long eff = res.windowMinDurNs;
            final long expected = ref.calls.stream().filter(f -> f.unclosed() || f.durNs() >= eff).count();
            assertEquals(expected, res.totalCalls, "seed " + seed + " eff " + eff);
            for (final Calls tf : res.callsByThread) {
                for (int i = 0; i < tf.count; i++) {
                    if (tf.depth[i] == 0) {
                        continue;
                    }
                    boolean found = false;
                    for (int j = 0; j < tf.count && !found; j++) {
                        found = tf.depth[j] == tf.depth[i] - 1 && tf.startNs[j] <= tf.startNs[i]
                                && tf.startNs[j] + tf.durNs[j] >= tf.startNs[i] + tf.durNs[i];
                    }
                    assertTrue(found, "seed " + seed + " frame " + i + " depth " + tf.depth[i]
                            + " has no parent in the response");
                }
            }
        }
    }
}
