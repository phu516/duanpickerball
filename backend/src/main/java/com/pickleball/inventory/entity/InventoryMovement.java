package com.pickleball.inventory.entity;

import com.pickleball.product.entity.ProductVariant;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id") private ProductVariant variant;
    @Column(nullable = false) private int quantityDelta;
    @Column(nullable = false) private String reason;
    private Long actorId;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected InventoryMovement() {}

    public InventoryMovement(ProductVariant variant, int delta, String reason, Long actorId) {
        this.variant = variant;
        this.quantityDelta = delta;
        this.reason = reason;
        this.actorId = actorId;
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getVariantId() { return variant.getId(); }
    public int getQuantityDelta() { return quantityDelta; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
