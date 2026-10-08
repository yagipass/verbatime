package io.github.yagipass.verbatime.examples.workload;

public final class TaxCalculator {

  public long tax(long net) {
    Work.cpu("tax", 500);
    return net / 10;
  }
}
