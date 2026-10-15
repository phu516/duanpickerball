package com.pickleball.payment.provider;

import com.pickleball.payment.entity.Payment;
import java.time.LocalDateTime;

public interface PaymentProvider {
    String code();
    PaymentCheckout createCheckout(Payment payment, LocalDateTime expiresAt, String returnUrl);
    default void cancelCheckout(Payment payment) {}
}
