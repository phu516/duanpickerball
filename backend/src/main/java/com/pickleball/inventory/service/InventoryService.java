package com.pickleball.inventory.service;

import com.pickleball.common.exception.BusinessException;
import com.pickleball.inventory.entity.*;
import com.pickleball.inventory.repository.*;
import com.pickleball.order.entity.OrderItem;
import com.pickleball.product.entity.ProductVariant;
import com.pickleball.product.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final ProductVariantRepository variants;
    private final InventoryReservationRepository reservations;
    private final InventoryMovementRepository movements;
    private final EntityManager entityManager;

    public InventoryService(ProductVariantRepository variants,
                            InventoryReservationRepository reservations,
                            InventoryMovementRepository movements, EntityManager entityManager) {
        this.variants = variants;
        this.reservations = reservations;
        this.movements = movements;
        this.entityManager = entityManager;
    }

    public ProductVariant lockVariant(Long id) {
        ProductVariant variant = variants.findLocked(id);
        if (variant == null) throw new BusinessException(HttpStatus.NOT_FOUND,
                "VARIANT_NOT_FOUND", "Không tìm thấy SKU");
        // A cart may have loaded this SKU before the lock. Refresh under the lock so its
        // reserved quantity reflects the transaction that committed immediately before us.
        entityManager.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
        return variant;
    }

    public void reserve(OrderItem item, ProductVariant variant, int quantity) {
        if (!variant.isActive() || !variant.getProduct().isActive() || quantity > variant.available()) {
            throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Không đủ tồn kho cho " + variant.getSku());
        }
        variant.reserve(quantity);
        reservations.save(new InventoryReservation(item, variant, quantity,
                LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(1)));
    }

    public void release(List<OrderItem> items) {
        for (InventoryReservation reservation : forItems(items)) {
            if (!reservation.getStatus().equals("HELD")) continue;
            ProductVariant variant = lockVariant(reservation.getVariant().getId());
            variant.release(reservation.getQuantity());
            reservation.release();
        }
    }

    public void fulfill(List<OrderItem> items) {
        for (InventoryReservation reservation : forItems(items)) {
            if (!reservation.getStatus().equals("HELD")) continue;
            ProductVariant variant = lockVariant(reservation.getVariant().getId());
            variant.fulfill(reservation.getQuantity());
            movements.save(new InventoryMovement(variant, -reservation.getQuantity(),
                    "Bàn giao đơn hàng", null));
            reservation.fulfill();
        }
    }

    @Transactional
    public InventoryView adjust(Long variantId, int delta, String reason, Long actorId) {
        ProductVariant variant = lockVariant(variantId);
        if (variant.getStockOnHand() + delta < variant.getStockReserved()) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVENTORY_BELOW_RESERVED",
                    "Không thể giảm tồn dưới lượng đã giữ");
        }
        variant.adjust(delta);
        movements.save(new InventoryMovement(variant, delta, reason, actorId));
        return map(variant);
    }

    @Transactional(readOnly = true)
    public List<InventoryView> overview() { return variants.findAll().stream().map(this::map).toList(); }

    @Transactional(readOnly = true)
    public List<InventoryMovement> movements() { return movements.findByOrderByCreatedAtDesc(); }

    private List<InventoryReservation> forItems(List<OrderItem> items) {
        return reservations.findByOrderItemIdIn(items.stream().map(OrderItem::getId).toList());
    }

    private InventoryView map(ProductVariant variant) {
        return new InventoryView(variant.getId(), variant.getSku(),
                variant.getStockOnHand(), variant.getStockReserved(), variant.available());
    }

    public record InventoryView(Long variantId, String sku, int onHand, int reserved, int available) {}
}
