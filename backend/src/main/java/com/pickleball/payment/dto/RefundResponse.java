package com.pickleball.payment.dto;

import com.pickleball.payment.entity.Refund;
import java.math.BigDecimal;

public record RefundResponse(Long id, Long paymentId, BigDecimal amount, String status, String reason) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getPayment().getId(), refund.getAmount(),
                refund.getStatus(), refund.getReason());
    }
}
