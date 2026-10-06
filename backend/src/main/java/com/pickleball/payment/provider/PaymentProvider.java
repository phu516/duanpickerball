package com.pickleball.payment.provider;

import com.pickleball.payment.entity.Payment;

public interface PaymentProvider {
    String code();
    String paymentUrl(Payment payment);
}
