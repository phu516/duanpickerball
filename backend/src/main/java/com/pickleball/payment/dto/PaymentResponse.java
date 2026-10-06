package com.pickleball.payment.dto;

import com.pickleball.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long paymentId, String purpose, String method, String provider,
                              BigDecimal amount, String currency, String status,
                              LocalDateTime createdAt, LocalDateTime paidAt, String paymentUrl) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getPurpose(), payment.getMethod(),
                payment.getProvider(), payment.getAmount(), "VND", payment.getStatus(),
                payment.getCreatedAt(), payment.getPaidAt(), null);
    }
}
