package io.github.yagipass.verbatime.examples.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String sku;

  private int qty;

  private long cents;

  private String txId;

  private Instant createdAt;

  protected OrderEntity() {}

  public OrderEntity(String sku, int qty, long cents, String txId) {
    this.sku = sku;
    this.qty = qty;
    this.cents = cents;
    this.txId = txId;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public int getQty() {
    return qty;
  }

  public long getCents() {
    return cents;
  }

  public String getTxId() {
    return txId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
