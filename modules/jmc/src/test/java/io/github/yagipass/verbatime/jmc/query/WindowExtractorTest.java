package io.github.yagipass.verbatime.jmc.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.jmc.index.Calls;
import io.github.yagipass.verbatime.jmc.index.RandomTraces;
import io.github.yagipass.verbatime.jmc.index.ReferenceDecoder;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class WindowExtractorTest {

  @Test
  void windowsMatchFullDecodeAcrossSeedsBudgetsAndZooms() throws IOException {
    for (long seed = 1; seed <= 8; seed++) {
      byte[] bytes = RandomTraces.random(seed);
      ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
      @Var long minNs = Long.MAX_VALUE;
      @Var long maxNs = Long.MIN_VALUE;
      for (ReferenceDecoder.Call f : ref.calls) {
        minNs = Math.min(minNs, f.startNs());
        maxNs = Math.max(maxNs, f.startNs() + f.durNs());
      }
      long span = Math.max(maxNs - minNs, 1);
      Random rng = new Random(seed * 31);
      for (int budget : new int[] {1 << 30, 4000, 48}) {
        TraceSnapshot data = TestTraces.index(bytes, budget);
        List<long[]> windows = new ArrayList<>();
        windows.add(new long[] {minNs, maxNs});
        windows.add(new long[] {minNs - span / 10, minNs + span / 3});
        windows.add(new long[] {maxNs - span / 3, maxNs + span / 10});
        windows.add(new long[] {minNs + span / 3, minNs + span / 2});
        for (int i = 0; i < 12; i++) {
          long a = minNs + (long) (rng.nextDouble() * span);
          long b = a + Math.max((long) (rng.nextDouble() * span / 4), 200);
          windows.add(new long[] {a, b});
        }
        for (TraceSnapshot.ThreadIndex m : data.threads) {
          for (int c = 0; c < m.chunks.count && windows.size() < 40; c += 3) {
            long t = m.chunks.baseTicks[c] * 100;
            windows.add(new long[] {t - 700, t + 900});
          }
        }
        for (long[] wdw : windows) {
          for (int px : new int[] {60, 1000, 2_000_000}) {
            checkWindow(
                data,
                ref,
                wdw[0],
                wdw[1],
                px,
                "seed "
                    + seed
                    + " budget "
                    + budget
                    + " window ["
                    + wdw[0]
                    + ","
                    + wdw[1]
                    + "] px "
                    + px);
          }
        }
      }
    }
  }

  @Test
  void frameBudgetRaisesThresholdConsistently() throws IOException {
    for (long seed = 1; seed <= 5; seed++) {
      byte[] bytes = RandomTraces.random(seed);
      ReferenceDecoder.Result ref = ReferenceDecoder.decode(bytes);
      TraceSnapshot data = TestTraces.index(bytes, 1 << 30);
      long t0 = data.minNs;
      long t1 = data.maxNs;
      int budget = 40;
      Window res = WindowExtractor.extract(data, t0, t1, 2_000_000, budget);
      long unclosedTotal = ref.calls.stream().filter(ReferenceDecoder.Call::unclosed).count();
      assertTrue(
          res.totalCalls <= Math.max(budget, unclosedTotal),
          "seed " + seed + ": " + res.totalCalls + " frames for budget " + budget);
      long eff = res.windowMinDurNs;
      long expected = ref.calls.stream().filter(f -> f.unclosed() || f.durNs() >= eff).count();
      assertEquals(expected, res.totalCalls, "seed " + seed + " eff " + eff);
      for (Calls tf : res.callsByThread) {
        for (int i = 0; i < tf.count; i++) {
          if (tf.depth[i] == 0) {
            continue;
          }
          @Var boolean found = false;
          for (int j = 0; j < tf.count && !found; j++) {
            found =
                tf.depth[j] == tf.depth[i] - 1
                    && tf.startNs[j] <= tf.startNs[i]
                    && tf.startNs[j] + tf.durNs[j] >= tf.startNs[i] + tf.durNs[i];
          }
          assertTrue(
              found,
              "seed "
                  + seed
                  + " frame "
                  + i
                  + " depth "
                  + tf.depth[i]
                  + " has no parent in the response");
        }
      }
    }
  }

  private static void checkWindow(
      TraceSnapshot data, ReferenceDecoder.Result ref, long t0, long t1, int px, String ctx) {
    long thr = Math.max((long) ((t1 - t0) / (double) px * WindowExtractor.PIXEL_FRACTION), 100);
    Window res = WindowExtractor.extract(data, t0, t1, px, Integer.MAX_VALUE);
    assertEquals(thr, res.windowMinDurNs, ctx + " thr");

    Map<Long, List<ReferenceDecoder.Call>> expected =
        TestTraces.byTid(
            ref.calls.stream()
                .filter(
                    f ->
                        f.startNs() <= t1
                            && f.startNs() + f.durNs() >= t0
                            && (f.unclosed() || f.durNs() >= thr))
                .toList());
    Map<Long, Calls> actual = new java.util.HashMap<>();
    for (Calls tf : res.callsByThread) {
      actual.put(tf.tid, tf);
    }
    assertEquals(expected.keySet(), actual.keySet(), ctx + " tids");
    for (Map.Entry<Long, List<ReferenceDecoder.Call>> e : expected.entrySet()) {
      List<ReferenceDecoder.Call> exp = e.getValue();
      Calls tf = actual.get(e.getKey());
      assertEquals(exp.size(), tf.count, ctx + " tid " + e.getKey() + " frame count");
      for (int i = 0; i < exp.size(); i++) {
        ReferenceDecoder.Call f = exp.get(i);
        String c = ctx + " tid " + e.getKey() + " frame " + i;
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
}
