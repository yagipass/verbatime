package io.github.yagipass.verbatime.examples.workload;

public final class PricingService {

  private final TaxCalculator tax = new TaxCalculator();

  public long price(String sku, int qty) {
    long unit = 100L + (Work.cpu(sku, 2_000) & 0xFFF);
    long net = unit * qty;
    return net + tax.tax(net);
  }
}
