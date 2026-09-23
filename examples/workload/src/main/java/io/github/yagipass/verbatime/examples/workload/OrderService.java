package io.github.yagipass.verbatime.examples.workload;

public final class OrderService {

    private final PricingService pricing = new PricingService();

    private final InventoryRepository inventory = new InventoryRepository();

    private final PaymentGateway payment = new PaymentGateway();

    private final AuditLog audit = new AuditLog();

    public Receipt placeOrder(final String sku, final int qty) {
        final long cents = pricing.price(sku, qty);
        inventory.reserve(sku, qty);
        final String txId = payment.charge(cents);
        audit.append("order sku=" + sku + " qty=" + qty + " cents=" + cents + " tx=" + txId);
        return new Receipt(sku, qty, cents, txId);
    }
}
