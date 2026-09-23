package io.github.yagipass.verbatime.examples.workload;

public final class PricingService {

    private final TaxCalculator tax = new TaxCalculator();

    public long price(final String sku, final int qty) {
        final long unit = 100L + (Work.cpu(sku, 2_000) & 0xFFF);
        final long net = unit * qty;
        return net + tax.tax(net);
    }
}
