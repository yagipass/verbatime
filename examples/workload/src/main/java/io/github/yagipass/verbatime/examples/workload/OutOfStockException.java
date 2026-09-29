package io.github.yagipass.verbatime.examples.workload;

public final class OutOfStockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OutOfStockException(String sku, int qty, int stock) {
        super("sku " + sku + ": requested " + qty + " but only " + stock + " in stock");
    }
}
