package com.pickleball.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id") private Product product;
    @Column(nullable = false, unique = true) private String sku;
    @Column(nullable = false) private String label;
    @Column(nullable = false) private BigDecimal price;
    @Column(nullable = false) private int stockOnHand;
    @Column(nullable = false) private int stockReserved;
    @Column(nullable = false) private boolean active;

    protected ProductVariant() {}

    public ProductVariant(Product product, String sku, String label, BigDecimal price, int stockOnHand) {
        this.product = product;
        this.sku = sku;
        this.label = label;
        this.price = price;
        this.stockOnHand = stockOnHand;
        this.stockReserved = 0;
        this.active = true;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getSku() { return sku; }
    public String getLabel() { return label; }
    public BigDecimal getPrice() { return price; }
    public int getStockOnHand() { return stockOnHand; }
    public int getStockReserved() { return stockReserved; }
    public int available() { return stockOnHand - stockReserved; }
    public boolean isActive() { return active; }

    public void reserve(int quantity) {
        if (quantity <= 0 || quantity > available()) throw new IllegalArgumentException("Insufficient stock");
        stockReserved += quantity;
    }

    public void release(int quantity) { stockReserved -= quantity; }
    public void fulfill(int quantity) { stockReserved -= quantity; stockOnHand -= quantity; }
    public void adjust(int delta) { stockOnHand += delta; }
    public void update(String label, BigDecimal price, boolean active) {
        this.label = label;
        this.price = price;
        this.active = active;
    }
}
