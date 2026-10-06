package com.pickleball.inventory.entity;

import com.pickleball.order.entity.OrderItem;
import com.pickleball.product.entity.ProductVariant;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id") private OrderItem orderItem;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id") private ProductVariant variant;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private String status;
    private LocalDateTime expiresAt;

    protected InventoryReservation() {}

    public InventoryReservation(OrderItem item, ProductVariant variant, int quantity, LocalDateTime expiresAt) {
        this.orderItem = item;
        this.variant = variant;
        this.quantity = quantity;
        this.status = "HELD";
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public ProductVariant getVariant() { return variant; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public void release() { status = "RELEASED"; }
    public void fulfill() { status = "FULFILLED"; }
}
