package com.pickleball.payment.provider;

public record PaymentCheckout(Long orderCode, String paymentLinkId, String paymentUrl,
                              String qrCode, String accountName, String accountNumber,
                              String description) {}
