package io.github.yagipass.verbatime.jmc.index;

import com.google.errorprone.annotations.Var;
import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.FrameStack;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;

public final class ChunkWalker {

  private ChunkWalker() {}

  public interface Visitor {

    default boolean enter(long startNs, int methodId, int sessionDepth, int sp) {
      return true;
    }

    boolean exit(
        long startNs, long durNs, long childNs, int methodId, int sessionDepth, int sp, int exc);

    default void sessionEnd() {}
  }

  public static void walkRange(
      TraceSnapshot data, ThreadIndex m, long loNs, long hiNs, Visitor ev) {
    int c0 = firstChunkEndingAtOrAfter(m, loNs);
    int c1 = lastChunkStartingAtOrBefore(m, c0, hiNs);
    if (c0 <= c1) {
      walk(data, m, c0, c1, ev);
    }
  }

  private static void walk(TraceSnapshot data, ThreadIndex m, int c0, int c1, Visitor ev) {
    ChunkCursor chunks = new ChunkCursor(data.buffer, m, c0, c1);
    try {
      EventCursor cur = new EventCursor();
      FrameStack stack = new FrameStack();
      @Var int baseDepth = 0;
      while (chunks.next()) {
        baseDepth = Math.max(chunks.openDepthAtStart() - stack.depth(), 0);
        if (chunks.payloadLen() > 0) {
          chunks.open(cur);
          @Var boolean more = true;
          while (more) {
            EventCursor.Event e = cur.next();
            if (e == EventCursor.Event.ENTER) {
              stack.push(cur.ticks(), cur.methodId());
              more =
                  ev.enter(
                      cur.ticks() * Vbtm.NANOS_PER_TICK,
                      cur.methodId(),
                      baseDepth + stack.depth() - 1,
                      stack.depth() - 1);
            } else if (e == EventCursor.Event.EXIT) {
              if (stack.depth() == 0) {
                baseDepth = Math.max(baseDepth - 1, 0);
              } else {
                stack.pop(cur.ticks());
                more =
                    ev.exit(
                        stack.startNs(),
                        stack.durNs(),
                        stack.childNs(),
                        stack.methodId(),
                        baseDepth + stack.depth(),
                        stack.depth(),
                        cur.exceptionId());
              }
            } else {
              break;
            }
          }
          if (!more) {
            return;
          }
        }
        if (chunks.endsSession()) {
          stack.clear();
          ev.sessionEnd();
        }
      }
    } finally {
      chunks.release();
    }
  }

  static int firstChunkEndingAtOrAfter(ThreadIndex m, long loNs) {
    @Var int lo = 0;
    @Var int hi = m.chunks.count;
    while (lo < hi) {
      int middle = (lo + hi) >>> 1;
      if (m.chunks.endTicks[middle] * Vbtm.NANOS_PER_TICK < loNs) {
        lo = middle + 1;
      } else {
        hi = middle;
      }
    }
    return lo;
  }

  private static int lastChunkStartingAtOrBefore(ThreadIndex m, int from, long hiNs) {
    @Var int best = -1;
    @Var int lo = from;
    @Var int hi = m.chunks.count;
    while (lo < hi) {
      int middle = (lo + hi) >>> 1;
      if (m.chunks.baseTicks[middle] * Vbtm.NANOS_PER_TICK <= hiNs) {
        best = middle;
        lo = middle + 1;
      } else {
        hi = middle;
      }
    }
    return best;
  }

  public static long saturatingAdd(long a, long b) {
    long s = a + b;
    return s < a ? Long.MAX_VALUE : s;
  }
}
