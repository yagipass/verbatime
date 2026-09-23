package io.github.yagipass.verbatime.examples.workload;

public final class InventoryRepository {

    public static final int STOCK = 100;

    public void reserve(final String sku, final int qty) {
        Work.io(3);
        if (qty > STOCK) {
            throw new OutOfStockException(sku, qty, STOCK);
        }
    }
}
