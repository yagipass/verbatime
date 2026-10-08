package io.github.yagipass.verbatime.jmc.index;

import com.google.errorprone.annotations.Var;

final class DurationHistogram {

  static final int SIZE = 1000 + 16 * 900;

  private static final long[] POW10 = new long[19];

  static {
    POW10[0] = 1;
    for (int i = 1; i < POW10.length; i++) {
      POW10[i] = POW10[i - 1] * 10;
    }
  }

  private DurationHistogram() {}

  static int bucketIndex(long durNs) {
    if (durNs < 1000) {
      return (int) durNs;
    }
    @Var int d = 4;
    while (d < 19 && durNs >= POW10[d]) {
      d++;
    }
    int m = (int) (durNs / POW10[d - 3]);
    return 1000 + (d - 4) * 900 + (m - 100);
  }

  static long bucketValue(int index) {
    if (index < 1000) {
      return index;
    }
    int r = index - 1000;
    return (r % 900 + 100) * POW10[r / 900 + 1];
  }

  static long chooseThreshold(long[] hist, long total, long budget) {
    if (total <= budget) {
      return 0;
    }
    @Var long kept = 0;
    for (int i = SIZE - 1; i >= 0; i--) {
      kept += hist[i];
      if (kept >= budget) {
        return bucketValue(i);
      }
    }
    return 0;
  }
}
