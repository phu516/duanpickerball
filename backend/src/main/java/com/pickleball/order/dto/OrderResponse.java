package com.pickleball.order.dto;

import com.pickleball.order.entity.*;
import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(Long id, String orderCode, String guestName, String guestPhone,
                            String fulfillmentMethod, Long bookingId, String courtName,
                            String status, BigDecimal subtotal, BigDecimal deliveryFee,
                            BigDecimal totalAmount, String currency,
                            List<Line> items, String guestAccessToken) {
    public static OrderResponse from(StoreOrder order, List<OrderItem> items, String token) {
        Long bookingId = order.getBooking() == null ? null : order.getBooking().getId();
        String courtName = order.getBooking() == null ? null : order.getBooking().getCourt().getName();
        return new OrderResponse(order.getId(), order.getOrderCode(), order.getGuestName(),
                order.getGuestPhone(), order.getFulfillmentMethod(), bookingId, courtName,
                order.getStatus(), order.getSubtotal(), order.getDeliveryFee(),
                order.getTotalAmount(), "VND", items.stream().map(item -> new Line(
                item.getProductName(), item.getSku(), item.getVariantLabel(),
                item.getUnitPrice(), item.getQuantity())).toList(), token);
    }

    public record Line(String productName, String sku, String variantLabel,
                       BigDecimal unitPrice, int quantity) {}
}
