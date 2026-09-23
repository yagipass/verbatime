package io.github.yagipass.verbatime.examples.workload;

public record Receipt(String sku, int qty, long cents, String txId) {

    public String toText() {
        return "sku=" + sku + " qty=" + qty + " cents=" + cents + " tx=" + txId;
    }
}
