package io.github.yagipass.verbatime.jmc.query;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.jmc.index.ChunkWalker;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;
import java.util.BitSet;
import org.jspecify.annotations.Nullable;

public final class MatchSearch {

  public static final class Match {

    public final long tid;

    public final long startNs;

    public final long durNs;

    public final int depth;

    public final int methodId;

    private Match(long tid, long startNs, long durNs, int depth, int methodId) {
      this.tid = tid;
      this.startNs = startNs;
      this.durNs = durNs;
      this.depth = depth;
      this.methodId = methodId;
    }
  }

  private MatchSearch() {}

  public static @Nullable Match nextMatch(TraceSnapshot data, BitSet methods, long afterNs) {
    @Var Match best = null;
    for (ThreadIndex m : data.threads) {
      @Var Match ovm = null;
      for (int i = 0; i < m.overview.count; i++) {
        long s = m.overview.startNs[i];
        if (best != null && s >= best.startNs) {
          break;
        }
        if (s > afterNs && methods.get(m.overview.methodId[i])) {
          ovm =
              new Match(m.tid, s, m.overview.durNs[i], m.overview.depth[i], m.overview.methodId[i]);
          break;
        }
      }
      Match fine =
          fineNext(
              data,
              m,
              methods,
              afterNs,
              ovm != null ? ovm.startNs : (best != null ? best.startNs : Long.MAX_VALUE));
      Match cand = fine != null ? fine : ovm;
      if (cand != null && (best == null || cand.startNs < best.startNs)) {
        best = cand;
      }
    }
    return best;
  }

  public static @Nullable Match prevMatch(TraceSnapshot data, BitSet methods, long beforeNs) {
    @Var Match best = null;
    for (ThreadIndex m : data.threads) {
      @Var Match ovm = null;
      for (int i = m.overview.count - 1; i >= 0; i--) {
        long s = m.overview.startNs[i];
        if (best != null && s <= best.startNs) {
          break;
        }
        if (s < beforeNs && methods.get(m.overview.methodId[i])) {
          ovm =
              new Match(m.tid, s, m.overview.durNs[i], m.overview.depth[i], m.overview.methodId[i]);
          break;
        }
      }
      Match fine =
          finePrev(
              data,
              m,
              methods,
              beforeNs,
              ovm != null ? ovm.startNs : (best != null ? best.startNs : Long.MIN_VALUE));
      Match cand = fine != null ? fine : ovm;
      if (cand != null && (best == null || cand.startNs > best.startNs)) {
        best = cand;
      }
    }
    return best;
  }

  private static @Nullable Match fineNext(
      TraceSnapshot data, ThreadIndex m, BitSet methods, long afterNs, long upperBoundNs) {
    long d = data.overviewThresholdNs;
    if (d <= 0) {
      return null;
    }
    Match[] out = {null};
    long[] candStart = {Long.MAX_VALUE};
    int[] candSp = {-1};
    ChunkWalker.walkRange(
        data,
        m,
        afterNs,
        ChunkWalker.saturatingAdd(upperBoundNs, d),
        new ChunkWalker.Visitor() {
          @Override
          public boolean enter(long startNs, int methodId, int sessionDepth, int sp) {
            if (candSp[0] < 0
                && startNs > afterNs
                && startNs < upperBoundNs
                && methods.get(methodId)) {
              candStart[0] = startNs;
              candSp[0] = sp;
            }
            return candSp[0] < 0 || startNs <= candStart[0] + d;
          }

          @Override
          public boolean exit(
              long startNs,
              long durNs,
              long childNs,
              int methodId,
              int sessionDepth,
              int sp,
              int exc) {
            if (sp == candSp[0]) {
              if (startNs == candStart[0] && durNs < d) {
                out[0] = new Match(m.tid, startNs, durNs, sessionDepth, methodId);
                return false;
              }
              candSp[0] = -1;
              candStart[0] = Long.MAX_VALUE;
            }
            return true;
          }

          @Override
          public void sessionEnd() {
            candSp[0] = -1;
            candStart[0] = Long.MAX_VALUE;
          }
        });
    return out[0];
  }

  private static @Nullable Match finePrev(
      TraceSnapshot data, ThreadIndex m, BitSet methods, long beforeNs, long lowerBoundNs) {
    long d = data.overviewThresholdNs;
    if (d <= 0) {
      return null;
    }
    Match[] out = {null};
    ChunkWalker.walkRange(
        data,
        m,
        lowerBoundNs,
        ChunkWalker.saturatingAdd(beforeNs, d),
        (startNs, durNs, childNs, methodId, sessionDepth, sp, exc) -> {
          if (durNs < d
              && startNs < beforeNs
              && startNs > lowerBoundNs
              && methods.get(methodId)
              && (out[0] == null || startNs > out[0].startNs)) {
            out[0] = new Match(m.tid, startNs, durNs, sessionDepth, methodId);
          }
          return true;
        });
    return out[0];
  }
}
