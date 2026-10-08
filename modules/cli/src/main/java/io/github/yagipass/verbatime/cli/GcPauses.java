package io.github.yagipass.verbatime.cli;

import com.google.errorprone.annotations.Var;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class GcPauses {

  private final List<TraceFile.GcPause> byStart;

  private final long longestTicks;

  GcPauses(TraceFile file) {
    byStart = new ArrayList<>(file.gcPauses);
    byStart.sort(Comparator.comparingLong(TraceFile.GcPause::startTicks));
    @Var long longest = 0;
    for (TraceFile.GcPause g : byStart) {
      longest = Math.max(longest, g.durTicks());
    }
    longestTicks = longest;
  }

  int count() {
    return byStart.size();
  }

  long totalTicks() {
    @Var long t = 0;
    for (TraceFile.GcPause g : byStart) {
      t += g.durTicks();
    }
    return t;
  }

  List<TraceFile.GcPause> overlapping(long startTicks, long endTicks) {
    List<TraceFile.GcPause> r = new ArrayList<>();
    for (int i = firstCandidate(startTicks); i < byStart.size(); i++) {
      TraceFile.GcPause g = byStart.get(i);
      if (g.startTicks() >= endTicks) {
        break;
      }
      if (g.endTicks() > startTicks) {
        r.add(g);
      }
    }
    return r;
  }

  long overlapTicks(long startTicks, long endTicks) {
    @Var long t = 0;
    for (TraceFile.GcPause g : overlapping(startTicks, endTicks)) {
      t += Math.min(endTicks, g.endTicks()) - Math.max(startTicks, g.startTicks());
    }
    return t;
  }

  private int firstCandidate(long startTicks) {
    long from = startTicks - longestTicks;
    @Var int lo = 0;
    @Var int hi = byStart.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (byStart.get(mid).startTicks() < from) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }
}
