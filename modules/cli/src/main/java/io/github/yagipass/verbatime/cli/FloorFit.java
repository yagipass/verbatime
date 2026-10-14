package io.github.yagipass.verbatime.cli;

import com.google.errorprone.annotations.Var;
import java.util.Arrays;

final class FloorFit {

  private final int limit;

  private long[] durTicks = new long[64];

  private long[] foldAboveTicks = new long[64];

  private int size;

  private boolean dropped;

  private long longestTicks;

  FloorFit(int limit) {
    this.limit = limit;
  }

  void offer(long durTicks, long foldAboveTicks) {
    if (durTicks != Long.MAX_VALUE) {
      longestTicks = Math.max(longestTicks, durTicks);
    }
    if (size < limit) {
      if (size == this.durTicks.length) {
        int capacity = (int) Math.min((long) size * 2, limit);
        this.durTicks = Arrays.copyOf(this.durTicks, capacity);
        this.foldAboveTicks = Arrays.copyOf(this.foldAboveTicks, capacity);
      }
      this.durTicks[size] = durTicks;
      this.foldAboveTicks[size] = foldAboveTicks;
      siftUp(size++);
      return;
    }
    dropped = true;
    if (durTicks > this.durTicks[0]) {
      this.durTicks[0] = durTicks;
      this.foldAboveTicks[0] = foldAboveTicks;
      siftDown(0);
    }
  }

  long floor() {
    if (!dropped && lines(0) <= limit) {
      return 0;
    }
    long[] candidates = candidates();
    @Var int lo = 0;
    @Var int hi = candidates.length;
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (lines(candidates[mid]) <= limit) {
        hi = mid;
      } else {
        lo = mid + 1;
      }
    }
    return candidates[Math.min(lo, candidates.length - 1)];
  }

  private long[] candidates() {
    long[] sorted = Arrays.copyOf(durTicks, size);
    Arrays.sort(sorted);
    long above = dropped ? sorted[0] : Long.MIN_VALUE;
    long[] candidates = new long[size + 1];
    @Var int n = 0;
    for (long d : sorted) {
      if (d > above && d <= longestTicks && (n == 0 || candidates[n - 1] != d)) {
        candidates[n++] = d;
      }
    }
    candidates[n++] = longestTicks + 1;
    return Arrays.copyOf(candidates, n);
  }

  private long lines(long floorTicks) {
    @Var long lines = 0;
    for (int i = 0; i < size; i++) {
      if (durTicks[i] >= floorTicks) {
        lines += foldAboveTicks[i] < floorTicks ? 2 : 1;
      }
    }
    return lines;
  }

  private void siftUp(@Var int i) {
    while (i > 0) {
      int parent = (i - 1) >>> 1;
      if (durTicks[parent] <= durTicks[i]) {
        return;
      }
      swap(i, parent);
      i = parent;
    }
  }

  private void siftDown(@Var int i) {
    while (true) {
      int l = 2 * i + 1;
      int r = l + 1;
      @Var int m = i;
      if (l < size && durTicks[l] < durTicks[m]) {
        m = l;
      }
      if (r < size && durTicks[r] < durTicks[m]) {
        m = r;
      }
      if (m == i) {
        return;
      }
      swap(i, m);
      i = m;
    }
  }

  private void swap(int a, int b) {
    long d = durTicks[a];
    durTicks[a] = durTicks[b];
    durTicks[b] = d;
    long f = foldAboveTicks[a];
    foldAboveTicks[a] = foldAboveTicks[b];
    foldAboveTicks[b] = f;
  }
}
