package com.pickleball.payment.service;

import com.pickleball.payment.entity.Payment;
import com.pickleball.payment.provider.PaymentProvider;
import com.pickleball.payment.repository.PaymentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentLinkLifecycleService {
    private final PaymentRepository payments;
    private final List<PaymentProvider> providers;

    public PaymentLinkLifecycleService(PaymentRepository payments, List<PaymentProvider> providers) {
        this.payments = payments;
        this.providers = providers;
    }

    public void cancelForBooking(Long bookingId) {
        close(payments.findByBookingId(bookingId));
    }

    public void cancelForOrder(Long orderId) {
        close(payments.findByOrderId(orderId));
    }

    private void close(List<Payment> list) {
        for (Payment payment : list) {
            if (!"PENDING".equals(payment.getStatus()) || !"PAYOS".equals(payment.getProvider())) continue;
            providers.stream().filter(provider -> "PAYOS".equals(provider.code())).findFirst()
                    .ifPresent(provider -> provider.cancelCheckout(payment));
            payment.fail();
        }
    }
}
