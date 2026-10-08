package io.github.yagipass.verbatime.examples.workload;

import com.google.errorprone.annotations.Var;

public final class Work {

  private Work() {}

  public static long cpu(String seed, int rounds) {
    @Var long h = 1125899906842597L;
    for (int r = 0; r < rounds; r++) {
      for (int i = 0; i < seed.length(); i++) {
        h = 31 * h + seed.charAt(i);
      }
      h ^= h >>> 29;
    }
    return h;
  }

  public static void io(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
