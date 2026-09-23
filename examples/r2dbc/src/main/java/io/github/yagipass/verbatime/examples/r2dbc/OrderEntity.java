package io.github.yagipass.verbatime.examples.r2dbc;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("orders")
public record OrderEntity(@Id Long id, String sku, int qty, long cents, @Column("tx_id") String txId, @Column("created_at") LocalDateTime createdAt) {

    static OrderEntity of(final String sku, final int qty, final long cents, final String txId) {
        return new OrderEntity(null, sku, qty, cents, txId, LocalDateTime.now());
    }
}
