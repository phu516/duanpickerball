package com.pickleball.payment.provider;

import com.pickleball.payment.entity.Payment;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class SandboxPaymentProvider implements PaymentProvider {
    @Override public String code() { return "SANDBOX"; }
    @Override public PaymentCheckout createCheckout(Payment payment, LocalDateTime expiresAt, String returnUrl) {
        return new PaymentCheckout(null, null, "/payment/sandbox/" + payment.getId(), null, null, null, null);
    }
}
