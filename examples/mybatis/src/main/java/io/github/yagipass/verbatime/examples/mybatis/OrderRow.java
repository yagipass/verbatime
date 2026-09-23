package io.github.yagipass.verbatime.examples.mybatis;

import java.time.LocalDateTime;

public final class OrderRow {

    private Long id;

    private String sku;

    private int qty;

    private long cents;

    private String txId;

    private LocalDateTime createdAt;

    public OrderRow() {
    }

    public OrderRow(final String sku, final int qty, final long cents, final String txId) {
        this.sku = sku;
        this.qty = qty;
        this.cents = cents;
        this.txId = txId;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(final String sku) {
        this.sku = sku;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(final int qty) {
        this.qty = qty;
    }

    public long getCents() {
        return cents;
    }

    public void setCents(final long cents) {
        this.cents = cents;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(final String txId) {
        this.txId = txId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
