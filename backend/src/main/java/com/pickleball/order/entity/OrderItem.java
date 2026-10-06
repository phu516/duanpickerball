package com.pickleball.order.entity;

import com.pickleball.product.entity.ProductVariant;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id") private StoreOrder order;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id") private ProductVariant variant;
    @Column(nullable = false) private String productName;
    @Column(nullable = false) private String sku;
    @Column(nullable = false) private String variantLabel;
    @Column(nullable = false) private BigDecimal unitPrice;
    @Column(nullable = false) private int quantity;

    protected OrderItem() {}

    public OrderItem(StoreOrder order, ProductVariant variant, int quantity) {
        this.order = order;
        this.variant = variant;
        this.productName = variant.getProduct().getName();
        this.sku = variant.getSku();
        this.variantLabel = variant.getLabel();
        this.unitPrice = variant.getPrice();
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public String getProductName() { return productName; }
    public String getSku() { return sku; }
    public String getVariantLabel() { return variantLabel; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
