package com.pickleball.payment.dto;

import com.pickleball.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record PaymentResponse(Long paymentId, String purpose, String method, String provider,
                              BigDecimal amount, String currency, String status,
                              OffsetDateTime createdAt, OffsetDateTime paidAt, String paymentUrl,
                              String qrCode, OffsetDateTime expiresAt, String accountName,
                              String accountNumber, String description, String bookingCode,
                              String orderCode, String providerReference) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getPurpose(), payment.getMethod(),
                payment.getProvider(), payment.getAmount(), "VND", payment.getStatus(),
                payment.getCreatedAt().atOffset(ZoneOffset.UTC),
                payment.getPaidAt() == null ? null : payment.getPaidAt().atOffset(ZoneOffset.UTC),
                payment.getPaymentUrl(),
                payment.getQrCode(), payment.getExpiresAt() == null ? null
                        : payment.getExpiresAt().atOffset(ZoneOffset.UTC),
                payment.getAccountName(), payment.getAccountNumber(), payment.getDescription(),
                payment.getBooking() == null ? null : payment.getBooking().getBookingCode(),
                payment.getOrder() == null ? null : payment.getOrder().getOrderCode(),
                payment.getProviderReference());
    }
}
