package io.github.yagipass.verbatime.jmc.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.Calls;
import io.github.yagipass.verbatime.jmc.index.ChunkWalker;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

public final class WindowExtractor {

    public static final int DEFAULT_CALL_BUDGET = 60_000;

    static final double PIXEL_FRACTION = 0.5;

    private WindowExtractor() {
    }

    public static final class Window {

        long windowMinDurNs;

        public int totalCalls;

        public final List<Calls> callsByThread = new ArrayList<>();

        private Window() {
        }
    }

    public static Window extract(final TraceSnapshot data, long t0Ns, long t1Ns, int px, final int callBudget) {
        if (t1Ns < t0Ns) {
            final long t = t0Ns;
            t0Ns = t1Ns;
            t1Ns = t;
        }
        px = Math.max(px, 1);
        final long thr = Math.max((long) ((t1Ns - t0Ns) / (double) px * PIXEL_FRACTION), Vbtm.NANOS_PER_TICK);
        final long d = data.overviewThresholdNs;

        final Window res = new Window();

        for (final ThreadIndex m : data.threads) {
            final Calls ov = m.overview;
            final Calls tf = new Calls(m.tid, 64);
            for (int i = 0; i < ov.count; i++) {
                final long s = ov.startNs[i];
                if (s > t1Ns) {
                    break;
                }
                final long e = s + ov.durNs[i];
                if (e >= t0Ns && (ov.unclosed[i] || ov.durNs[i] >= thr)) {
                    tf.add(s, ov.durNs[i], ov.selfNs[i], ov.methodId[i], ov.depth[i], ov.unclosed[i], ov.exceptionId[i]);
                }
            }
            final int overviewCount = tf.count;
            if (thr < d) {
                addBelowOverview(data, m, t0Ns, t1Ns, thr, d, tf);
            }
            if (tf.count > 0) {
                if (tf.count > overviewCount) {
                    tf.sortByStart();
                }
                res.callsByThread.add(tf);
                res.totalCalls += tf.count;
            }
        }

        res.windowMinDurNs = res.totalCalls > callBudget ? capToBudget(res, callBudget) : thr;
        res.callsByThread.removeIf(tf -> tf.count == 0);
        return res;
    }

    private static void addBelowOverview(final TraceSnapshot data, final ThreadIndex m, final long t0Ns, final long t1Ns,
            final long thr, final long d, final Calls out) {
        ChunkWalker.walkRange(data, m, t0Ns - d, t1Ns + d, (startNs, durNs, childNs, methodId, sessionDepth, sp, exc) -> {
            if (durNs < d && durNs >= thr && startNs <= t1Ns && startNs + durNs >= t0Ns) {
                out.add(startNs, durNs, Math.max(durNs - childNs, 0), methodId, sessionDepth, false, exc);
            }
            return true;
        });
    }

    private static long capToBudget(final Window res, final int budget) {
        int closed = 0;
        int unclosedCount = 0;
        for (final Calls tf : res.callsByThread) {
            for (int i = 0; i < tf.count; i++) {
                if (tf.unclosed[i]) {
                    unclosedCount++;
                } else {
                    closed++;
                }
            }
        }
        final long effThr;
        final int allowed = budget - unclosedCount;
        if (allowed <= 0) {
            effThr = Long.MAX_VALUE;
        } else {
            final long[] durs = new long[closed];
            int k = 0;
            for (final Calls tf : res.callsByThread) {
                for (int i = 0; i < tf.count; i++) {
                    if (!tf.unclosed[i]) {
                        durs[k++] = tf.durNs[i];
                    }
                }
            }
            Arrays.sort(durs);
            effThr = durs[closed - allowed] + 1;
        }
        res.totalCalls = 0;
        for (final Calls tf : res.callsByThread) {
            res.totalCalls += tf.dropShorterThan(effThr);
        }
        return effThr;
    }
}
