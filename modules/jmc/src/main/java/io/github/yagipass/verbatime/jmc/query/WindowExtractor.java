package io.github.yagipass.verbatime.jmc.query;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.Calls;
import io.github.yagipass.verbatime.jmc.index.ChunkWalker;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class WindowExtractor {

  public static final int DEFAULT_CALL_BUDGET = 60_000;

  static final double PIXEL_FRACTION = 0.5;

  private WindowExtractor() {}

  public static final class Window {

    long windowMinDurNs;

    public int totalCalls;

    public final List<Calls> callsByThread = new ArrayList<>();

    private Window() {}
  }

  public static Window extract(
      TraceSnapshot data, @Var long t0Ns, @Var long t1Ns, @Var int px, int callBudget) {
    if (t1Ns < t0Ns) {
      long t = t0Ns;
      t0Ns = t1Ns;
      t1Ns = t;
    }
    px = Math.max(px, 1);
    long thr = Math.max((long) ((t1Ns - t0Ns) / (double) px * PIXEL_FRACTION), Vbtm.NANOS_PER_TICK);
    long d = data.overviewThresholdNs;

    Window res = new Window();

    for (ThreadIndex m : data.threads) {
      Calls ov = m.overview;
      Calls tf = new Calls(m.tid, 64);
      for (int i = 0; i < ov.count; i++) {
        long s = ov.startNs[i];
        if (s > t1Ns) {
          break;
        }
        long e = s + ov.durNs[i];
        if (e >= t0Ns && (ov.unclosed[i] || ov.durNs[i] >= thr)) {
          tf.add(
              s,
              ov.durNs[i],
              ov.selfNs[i],
              ov.methodId[i],
              ov.depth[i],
              ov.unclosed[i],
              ov.exceptionId[i]);
        }
      }
      int overviewCount = tf.count;
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

  private static void addBelowOverview(
      TraceSnapshot data, ThreadIndex m, long t0Ns, long t1Ns, long thr, long d, Calls out) {
    ChunkWalker.walkRange(
        data,
        m,
        t0Ns - d,
        t1Ns + d,
        (startNs, durNs, childNs, methodId, sessionDepth, sp, exc) -> {
          if (durNs < d && durNs >= thr && startNs <= t1Ns && startNs + durNs >= t0Ns) {
            out.add(
                startNs, durNs, Math.max(durNs - childNs, 0), methodId, sessionDepth, false, exc);
          }
          return true;
        });
  }

  private static long capToBudget(Window res, int budget) {
    @Var int closed = 0;
    @Var int unclosedCount = 0;
    for (Calls tf : res.callsByThread) {
      for (int i = 0; i < tf.count; i++) {
        if (tf.unclosed[i]) {
          unclosedCount++;
        } else {
          closed++;
        }
      }
    }
    long effThr;
    int allowed = budget - unclosedCount;
    if (allowed <= 0) {
      effThr = Long.MAX_VALUE;
    } else {
      long[] durs = new long[closed];
      @Var int k = 0;
      for (Calls tf : res.callsByThread) {
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
    for (Calls tf : res.callsByThread) {
      res.totalCalls += tf.dropShorterThan(effThr);
    }
    return effThr;
  }
}
