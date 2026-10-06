package com.pickleball.payment.provider;

import com.pickleball.payment.entity.Payment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class SandboxPaymentProvider implements PaymentProvider {
    @Override public String code() { return "SANDBOX"; }
    @Override public String paymentUrl(Payment payment) {
        return "/payment/sandbox/" + payment.getId();
    }
}
