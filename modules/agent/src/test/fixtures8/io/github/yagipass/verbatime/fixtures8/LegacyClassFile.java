package io.github.yagipass.verbatime.fixtures8;

public final class LegacyClassFile {

  public String root() {
    return "a" + b(1) + c(2L, 3.0) + caught();
  }

  int b(int x) {
    return x + 1;
  }

  static double c(long a, double b) {
    return a + b;
  }

  int caught() {
    try {
      thrower();
      return 0;
    } catch (IllegalStateException e) {
      return 1;
    }
  }

  void thrower() {
    throw new IllegalStateException("old");
  }

  public synchronized int sync(int x) {
    return x * 3;
  }
}
